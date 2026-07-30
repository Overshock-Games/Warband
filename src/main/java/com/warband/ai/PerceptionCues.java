package com.warband.ai;

import com.warband.WarbandDebug;
import com.warband.config.WarbandConfig;
import com.warband.entity.MobData;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Makes being hunted audible: unaware → suspicious → alert, and crucially the way
 * back down again.
 *
 * <p>{@link VisibilityRules} already shortens detection range for crouching, darkened
 * and invisible targets. That did real work in total silence, so nobody knew stealth
 * existed, let alone played around it. This turns the state changes into cues.
 *
 * <p><b>Built around the "lost you" cue first.</b> Most implementations of this idea
 * spend all their effort on the alarm and forget the all-clear, which is the half that
 * makes stealth <i>playable</i>: without it a player never learns they got away, so
 * hiding has tension but no resolution and the whole loop reads as broken rather than
 * tense. Thief and Metal Gear both spend a signature sound here.
 *
 * <p>Sound leads because it is the only channel that survives the situation. A hiding
 * player is behind cover, in the dark, at range, facing the wrong way — every visual
 * tell is unreliable exactly when it matters most. A head-turn and a particle ride
 * along for players who can see.
 *
 * <p>Throttled <b>per player</b>, not per mob. These cues inform one person about their
 * own situation, so a squad of twenty must not produce twenty of them. Reusing the
 * per-mob throttle from {@link TacticalBarks} here would be a bug.
 */
public final class PerceptionCues {

    /** How often the sweep runs. Perception is not a per-tick concern. */
    private static final int SCAN_INTERVAL = 10;
    /** Only mobs this close to a player are considered. */
    private static final double SCAN_RADIUS = 24.0;
    /** Minimum ticks between cues delivered to one player. */
    private static final int PLAYER_COOLDOWN_TICKS = 30;
    /** Below this difficulty a mob is not attentive enough to react at all. */
    private static final double MIN_DIFFICULTY = 0.20;
    /**
     * Quiet time required before declaring an all-clear.
     *
     * <p>Hysteresis, not politeness. A mob bobbing in and out of line of sight behind a
     * doorway flips ALERT/UNAWARE every couple of seconds, and without this the player
     * gets an alarm-then-all-clear pair each time — observed in testing as
     * ALERTED and LOST_TRACK two seconds apart, repeatedly.
     */
    private static final int ALL_CLEAR_DELAY_TICKS = 60;

    private static final WeakHashMap<Mob, Awareness> STATE = new WeakHashMap<>();
    /** Per-player: last cue tick, and whether an all-clear is still owed. */
    private static final Map<UUID, PlayerCueState> PLAYERS = new HashMap<>();

    private static int tickCounter;

    private PerceptionCues() {
    }

    private enum Awareness {
        UNAWARE,
        /** Something is there — in sight and in range, but concealment is holding. */
        SUSPICIOUS,
        ALERT
    }

    private static final class PlayerCueState {
        int lastCueTick;
        /** True once anything has gone ALERT, so exactly one all-clear is owed. */
        boolean alarmRaised;
        /** Last tick anything was hunting, for the all-clear hysteresis. */
        int lastAlertTick;
        /** Who was hunting, so the all-clear can be spoken in that mob's own voice. */
        java.lang.ref.WeakReference<Mob> lastAlertMob = new java.lang.ref.WeakReference<>(null);
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!WarbandConfig.perceptionCuesEnabled) return;
            if (++tickCounter < SCAN_INTERVAL) return;
            tickCounter = 0;

            // Drop state for players who have left, or the map grows for the life of
            // the server on a busy instance.
            if (PLAYERS.size() > server.getPlayerList().getPlayerCount()) {
                PLAYERS.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
            }

            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                // Spectators only. Creative players are still targetable in vanilla, and
                // excluding them would also make this untestable in the mode people
                // actually test in.
                if (player.isSpectator()) continue;
                sweep(player);
            }
        });
    }

    private static void sweep(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) return;

        AABB box = player.getBoundingBox().inflate(SCAN_RADIUS, SCAN_RADIUS * 0.5, SCAN_RADIUS);
        List<Mob> nearby = level.getEntitiesOfClass(Mob.class, box,
                mob -> mob instanceof Enemy && mob.isAlive() && MobData.isStamped(mob)
                        && MobData.get(mob).difficulty() >= MIN_DIFFICULTY);
        if (nearby.isEmpty()) return;

        // Collect transitions first, then speak once. Deciding per mob inside the loop
        // is how twenty mobs end up shouting over each other.
        Mob roused = null;
        Mob alerted = null;
        boolean anyAlert = false;

        for (Mob mob : nearby) {
            Awareness previous = STATE.getOrDefault(mob, Awareness.UNAWARE);
            Awareness current = evaluate(mob, player);
            if (current == Awareness.ALERT) anyAlert = true;
            if (current == previous) continue;
            STATE.put(mob, current);

            if (current == Awareness.SUSPICIOUS && previous == Awareness.UNAWARE) {
                roused = nearer(player, roused, mob);
            } else if (current == Awareness.ALERT) {
                alerted = nearer(player, alerted, mob);
            }
        }

        PlayerCueState state = PLAYERS.computeIfAbsent(player.getUUID(), id -> new PlayerCueState());

        // Escalation outranks first contact: if something just locked on, that is the
        // more urgent thing for the player to hear.
        if (anyAlert) {
            state.lastAlertTick = player.tickCount;
        }
        if (alerted != null) {
            state.alarmRaised = true;
            state.lastAlertMob = new java.lang.ref.WeakReference<>(alerted);
            if (ready(player, state)) {
                cueAlert(level, alerted, player);
            }
            return;
        }
        if (roused != null && ready(player, state)) {
            cueSuspicious(level, roused, player);
            return;
        }
        // Nothing is hunting any more, and something was. One all-clear, once — but
        // only after the hunt has actually stayed quiet, or a mob flickering behind a
        // doorway produces an alarm/all-clear pair every few seconds.
        if (!anyAlert && state.alarmRaised
                && player.tickCount - state.lastAlertTick >= ALL_CLEAR_DELAY_TICKS) {
            state.alarmRaised = false;
            cueLostYou(level, player, state.lastAlertMob.get());
        }
    }

    /** Current awareness of this mob with respect to one player. */
    private static Awareness evaluate(Mob mob, ServerPlayer player) {
        if (mob.getTarget() != null) return Awareness.ALERT;
        if (VisibilityRules.concealedNearMiss(mob, player)) return Awareness.SUSPICIOUS;
        // The squad shared a last-known position and this mob is acting on it.
        Squad squad = SquadCoordinator.getSquad(MobData.get(mob).squadId());
        if (squad != null && squad.lastKnownPos() != null) return Awareness.SUSPICIOUS;
        return Awareness.UNAWARE;
    }

    private static @Nullable Mob nearer(ServerPlayer player, @Nullable Mob current, Mob candidate) {
        if (current == null) return candidate;
        return candidate.distanceToSqr(player) < current.distanceToSqr(player) ? candidate : current;
    }

    private static boolean ready(ServerPlayer player, PlayerCueState state) {
        if (player.tickCount - state.lastCueTick < PLAYER_COOLDOWN_TICKS) return false;
        state.lastCueTick = player.tickCount;
        return true;
    }

    /** "Something's there." Rising pitch reads as a question. */
    private static void cueSuspicious(ServerLevel level, Mob mob, ServerPlayer player) {
        mob.getLookControl().setLookAt(player, 30.0F, 30.0F);
        // The mob's own voice, raised — a questioning version of its normal sound.
        level.playSound(null, mob.getX(), mob.getY(), mob.getZ(),
                MobVoice.of(mob), SoundSource.HOSTILE, 0.6f, 1.45f);
        level.playSound(null, mob.getX(), mob.getY(), mob.getZ(),
                SoundEvents.FOX_SNIFF, SoundSource.HOSTILE, 0.35f, 1.4f);
        level.sendParticles(ParticleTypes.SMOKE, mob.getX(), mob.getEyeY() + 0.6, mob.getZ(),
                3, 0.08, 0.05, 0.08, 0.0);
        WarbandDebug.event("SUSPICIOUS", mob, "player=" + player.getName().getString());
    }

    /** "Found you." Deliberately harsher than the suspicion cue. */
    private static void cueAlert(ServerLevel level, Mob mob, ServerPlayer player) {
        // Its own voice, sharp and loud. Was a fixed ravager grunt for every species,
        // which is why a creeper spotting you sounded like something else entirely.
        level.playSound(null, mob.getX(), mob.getY(), mob.getZ(),
                MobVoice.of(mob), SoundSource.HOSTILE, 0.85f, 1.6f);
        level.sendParticles(ParticleTypes.ANGRY_VILLAGER, mob.getX(), mob.getEyeY() + 0.6, mob.getZ(),
                2, 0.1, 0.05, 0.1, 0.0);
        WarbandDebug.event("ALERTED", mob, "player=" + player.getName().getString());
    }

    /**
     * The all-clear. Played at the player rather than at any mob, because by
     * definition the thing that lost them may be out of earshot — and because this is
     * information about the player's own situation, not about a location.
     */
    private static void cueLostYou(ServerLevel level, ServerPlayer player, @Nullable Mob gaveUp) {
        // Spoken by whatever gave up, in its own voice, dropped low — "it loses
        // interest". Falls back to a neutral sound at the player if that mob is gone,
        // because the all-clear still has to land even when nothing is left to say it.
        if (gaveUp != null && gaveUp.isAlive()) {
            level.playSound(null, gaveUp.getX(), gaveUp.getY(), gaveUp.getZ(),
                    MobVoice.of(gaveUp), SoundSource.HOSTILE, 0.7f, 0.55f);
        } else {
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.HOSTILE, 0.4f, 0.6f);
        }
        WarbandDebug.event("LOST_TRACK", player, "player=" + player.getName().getString());
    }

    /** True while this mob is holding a suspicious pause, for {@code SuspicionPauseGoal}. */
    public static boolean isSuspicious(Mob mob) {
        return STATE.get(mob) == Awareness.SUSPICIOUS;
    }
}
