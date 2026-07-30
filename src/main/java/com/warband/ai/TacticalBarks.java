package com.warband.ai;

import com.warband.config.WarbandConfig;
import com.warband.entity.Tactic;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import org.jetbrains.annotations.Nullable;

import java.util.WeakHashMap;

/**
 * Audible intent. When a mob commits to a tactic, it makes a noise that says which
 * <i>kind</i> of thing it is about to do.
 *
 * <p>Borrowed from F.E.A.R. and Halo, whose enemies are not much smarter than their
 * contemporaries — they narrate. Players credit AI with intelligence in proportion to
 * how much of its reasoning they can perceive, and Warband ships thirty tactics that
 * are almost entirely invisible. The mod was already smarter than it sounded.
 *
 * <p>Grouped into a handful of <b>families</b> rather than one cue per tactic. Thirty
 * distinct noises is not a vocabulary, it is noise; six that a player can actually
 * learn to read is. The families answer the only questions that matter in the moment:
 * are they closing, circling, pulling back, calling friends, about to lunge, or
 * setting a trap?
 *
 * <p>Restraint is the whole design:
 * <ul>
 *   <li>only while the mob has a target — idle mobs do not chatter,</li>
 *   <li>one bark per mob per {@value #COOLDOWN_TICKS} ticks,</li>
 *   <li>quiet, so vanilla's own audio still leads,</li>
 *   <li>nothing for tactics whose consequence the player cannot see, and nothing for
 *       tactics that already telegraph themselves (siege digging has real block-break
 *       cracks; web throws already play a cobweb cue).</li>
 * </ul>
 */
public final class TacticalBarks {

    /** Minimum ticks between one mob's barks. */
    private static final int COOLDOWN_TICKS = 70;
    /** Deliberately below vanilla mob ambience so this layers under, not over. */
    private static final float VOLUME = 0.55f;

    /** Per-mob throttle. Weak so it never keeps a dead mob alive. */
    private static final WeakHashMap<Mob, Integer> LAST_BARK = new WeakHashMap<>();

    private TacticalBarks() {
    }

    /**
     * What a bark communicates.
     *
     * <p>The <b>voice</b> is always the mob's own sound (see {@link MobVoice}) so a
     * creeper never groans like a zombie. Meaning is carried by <b>pitch</b> plus an
     * optional layered <b>accent</b> — same speaker, different tone. Pitch alone was
     * not enough to keep six families apart, and swapping the voice per family threw
     * away the species identity players rely on.
     */
    private enum Bark {
        /** Committing toward you: low voice, heavy gear. */
        ADVANCE(0.8f, SoundEvents.ARMOR_EQUIP_CHAIN.value(), 0.35f),
        /** Moving around you rather than at you: raised voice, light gear. */
        CIRCLE(1.3f, SoundEvents.ARMOR_EQUIP_LEATHER.value(), 0.3f),
        /** Giving ground: falling voice, no accent. */
        WITHDRAW(0.65f, null, 0.0f),
        /** Bringing others. The horn is the universal reinforcement signifier — keep it. */
        RALLY(1.15f, SoundEvents.RAID_HORN.value(), 0.45f),
        /** About to close the distance violently: sharp voice plus a whoosh. */
        LUNGE(1.5f, SoundEvents.PLAYER_ATTACK_SWEEP, 0.4f),
        /** Looking for you rather than at you: quiet voice, sniffing. */
        SEARCH(1.05f, SoundEvents.FOX_SNIFF, 0.3f);

        private final float pitch;
        private final @Nullable SoundEvent accent;
        private final float accentVolume;

        Bark(float pitch, @Nullable SoundEvent accent, float accentVolume) {
            this.pitch = pitch;
            this.accent = accent;
            this.accentVolume = accentVolume;
        }
    }

    /**
     * Plays the bark for a tactic, if that tactic has one.
     *
     * <p>Called from the same place tactics are logged, so cue coverage and debug
     * coverage stay in step.
     */
    public static void play(Mob mob, Tactic tactic) {
        if (!WarbandConfig.tacticalBarksEnabled) return;
        Bark bark = barkFor(tactic);
        if (bark == null) return;
        // Intent only means something when there is someone to intend it against.
        if (mob.getTarget() == null) return;
        if (!(mob.level() instanceof ServerLevel level)) return;
        if (!ready(mob)) return;

        speak(level, mob, bark);
    }

    /**
     * The rally cue, for behaviours that have no {@link Tactic} of their own.
     *
     * <p>Calling for reinforcements is the most consequential thing a squad does and
     * the one a player most needs to hear coming, but it runs through
     * {@code CallBackupGoal} rather than the tactic mask, so it would otherwise be
     * the one silent case that mattered.
     */
    public static void rally(Mob mob) {
        if (!WarbandConfig.tacticalBarksEnabled) return;
        if (mob.getTarget() == null) return;
        if (!(mob.level() instanceof ServerLevel level)) return;
        if (!ready(mob)) return;

        speak(level, mob, Bark.RALLY);
    }

    /** The mob's own voice at the bark's pitch, with the family accent layered over it. */
    private static void speak(ServerLevel level, Mob mob, Bark bark) {
        level.playSound(null, mob.getX(), mob.getY(), mob.getZ(),
                MobVoice.of(mob), SoundSource.HOSTILE, VOLUME, bark.pitch);
        if (bark.accent != null) {
            level.playSound(null, mob.getX(), mob.getY(), mob.getZ(),
                    bark.accent, SoundSource.HOSTILE, bark.accentVolume, bark.pitch);
        }
    }

    private static boolean ready(Mob mob) {
        Integer last = LAST_BARK.get(mob);
        if (last != null && mob.tickCount - last < COOLDOWN_TICKS) return false;
        LAST_BARK.put(mob, mob.tickCount);
        return true;
    }

    /**
     * Tactic to family. A {@code null} return means "stays silent", which is a real
     * design choice rather than an omission — see the class docs.
     */
    private static @Nullable Bark barkFor(Tactic tactic) {
        return switch (tactic) {
            // Closing on you.
            case ZOMBIE_HORDE, HOGLIN_STAMPEDE, GUARDIAN_SURGE, WARDEN_PRESSURE,
                 RAVAGER_BREAKER, ILLAGER_COMMAND, WATER_COMMIT -> Bark.ADVANCE;

            // Repositioning around you.
            case CREEPER_STALK, RANGED_REPOSITION, GHAST_REPOSITION, CEILING_CRAWL,
                 BLAZE_HOVER, PHANTOM_HARASS, CAVE_SPIDER_AMBUSH -> Bark.CIRCLE;

            // Giving ground.
            case BOGGED_BACKDASH, SKELETON_SMOKE -> Bark.WITHDRAW;

            // Bringing friends. Loud on purpose: this one has consequences.
            case PIGLIN_SOCIAL, WITCH_SUPPORT -> Bark.RALLY;

            // About to cover ground fast.
            case LEAP_UNREACHABLE, SLIME_SURGE, STRAY_JUMP_SHOT,
                 ENDERMAN_DISRUPT -> Bark.LUNGE;

            // Hunting a position rather than a target.
            case PRESSURE_UNREACHABLE -> Bark.SEARCH;

            // Silent by design: these already announce themselves in-world.
            // SPIDER_WEB and STICKY_PATH play cobweb cues; SIEGE_MINE shows real
            // block-break cracks; CREEPER_BREACH is a lit creeper; FROST_WALKER and
            // SHULKER_LOCKDOWN are visible on the ground and on the player.
            case SPIDER_WEB, STICKY_PATH, SIEGE_MINE, CREEPER_BREACH, FROST_WALKER,
                 SHULKER_LOCKDOWN -> null;
        };
    }
}
