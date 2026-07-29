package com.warband.entity;

import com.mojang.serialization.Codec;
import com.warband.WarbandMod;
import com.warband.illager.FactionReputation;
import com.warband.illager.IllagerFactionData;
import com.warband.illager.IllagerGrudge;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.resources.Identifier;

/**
 * Warband's Fabric data attachments.
 *
 * <p>This is the foundation every other system reads from: difficulty stamping
 * (Phase 2), squad assignment (Phase 3), and the difficulty-lens HUD (Phase 4)
 * all go through {@link #MOB_DATA}.
 */
public final class WarbandAttachments {

    /**
     * Per-mob {@link MobData}, stamped at spawn. Persistent, survives save/load.
     * Not copied on death: mobs do not respawn.
     */
    public static final AttachmentType<MobData> MOB_DATA = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath(WarbandMod.MOD_ID, "mob_data"),
            builder -> builder.persistent(MobData.CODEC)
    );

    /** Per-illager faction/doctrine identity. Persistent, separate from tactical squad state. */
    public static final AttachmentType<IllagerFactionData> ILLAGER_FACTION = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath(WarbandMod.MOD_ID, "illager_faction"),
            builder -> builder.persistent(IllagerFactionData.CODEC)
    );

    /** Per-mob marker: revenge/bounty attackers should not create fresh grudges. */
    public static final AttachmentType<Boolean> ILLAGER_GRUDGE_SPAWNED = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath(WarbandMod.MOD_ID, "illager_grudge_spawned"),
            builder -> builder.persistent(Codec.BOOL)
    );

    /** Per-mob marker: trapped farm mobs stop paying out loot/XP and try to escape. */
    public static final AttachmentType<Boolean> FARM_SUPPRESSED = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath(WarbandMod.MOD_ID, "farm_suppressed"),
            builder -> builder.persistent(Codec.BOOL)
    );

    /** Per-mob anti-farm pressure tier: 0 none, 1 escape, 2 suppress loot, 3 enraged breakout. */
    public static final AttachmentType<Integer> FARM_TIER = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath(WarbandMod.MOD_ID, "farm_tier"),
            builder -> builder.persistent(Codec.INT)
    );

    /** Per-mob consecutive anti-farm detections before harsh suppression is allowed. */
    public static final AttachmentType<Integer> FARM_SUSPICION = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath(WarbandMod.MOD_ID, "farm_suspicion"),
            builder -> builder.persistent(Codec.INT)
    );

    /** Per-boss marker: one-shot Warband phase transition has fired. */
    public static final AttachmentType<Boolean> BOSS_PHASE_TRIGGERED = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath(WarbandMod.MOD_ID, "boss_phase_triggered"),
            builder -> builder.persistent(Codec.BOOL)
    );

    /** Per-boss marker: low-health last stand has fired. */
    public static final AttachmentType<Boolean> BOSS_LAST_STAND_TRIGGERED = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath(WarbandMod.MOD_ID, "boss_last_stand_triggered"),
            builder -> builder.persistent(Codec.BOOL)
    );

    /** Per-mob marker: this is a bounty hunter (separate from generic grudge spawns). Drives revive + scary AI. */
    public static final AttachmentType<Boolean> BOUNTY_HUNTER = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath(WarbandMod.MOD_ID, "bounty_hunter"),
            builder -> builder.persistent(Codec.BOOL)
    );

    /** Per-mob marker: bounty hunter has used its one-shot revive. */
    public static final AttachmentType<Boolean> BOUNTY_REVIVED = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath(WarbandMod.MOD_ID, "bounty_revived"),
            builder -> builder.persistent(Codec.BOOL)
    );

    /** Per-mob marker: this illager is a mansion's Warmarshal. Killing it breaks the faction. */
    public static final AttachmentType<Boolean> WARMARSHAL = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath(WarbandMod.MOD_ID, "warmarshal"),
            builder -> builder.persistent(Codec.BOOL)
    );

    /** Per-mob marker: faction seat key for illagers that belong to a mansion-tier stronghold. */
    public static final AttachmentType<String> STRONGHOLD_SEAT_KEY = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath(WarbandMod.MOD_ID, "stronghold_seat_key"),
            builder -> builder.persistent(Codec.STRING)
    );

    /** Transient marker: this mob already has its Warband AI goals attached. */
    public static final AttachmentType<Boolean> WARBAND_GOALS_BOUND = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath(WarbandMod.MOD_ID, "warband_goals_bound")
    );

    /** Transient marker: this golem already received its difficulty enhancements + goals. */
    public static final AttachmentType<Boolean> GOLEM_ENHANCED = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath(WarbandMod.MOD_ID, "golem_enhanced")
    );

    /**
     * On a player: their capability score ({@code 0.0..1.0}), the per-player
     * input to REGIONAL difficulty. Ratchets up instantly, decays down slowly; see
     * {@code com.warband.difficulty.PlayerScore}. Persistent and copied across
     * death so difficulty stays consistent session to session.
     */
    public static final AttachmentType<Float> PLAYER_SCORE = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath(WarbandMod.MOD_ID, "player_score"),
            builder -> builder.persistent(Codec.FLOAT).copyOnDeath()
    );

    /**
     * On a player: the game-time tick until which post-death difficulty relief
     * applies. Absent when no relief is active. Not persistent, relief lapsing
     * on logout is acceptable.
     */
    public static final AttachmentType<Long> DEATH_RELIEF = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath(WarbandMod.MOD_ID, "death_relief")
    );

    /** On a player: named illager survivors that may return later with a patrol. */
    public static final AttachmentType<java.util.List<IllagerGrudge>> ILLAGER_GRUDGES = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath(WarbandMod.MOD_ID, "illager_grudges"),
            builder -> builder.persistent(IllagerGrudge.CODEC.listOf()).copyOnDeath()
    );

    /** On a player: set once after the first factioned-illager kill so the grace grudge only fires once. */
    public static final AttachmentType<Boolean> FIRST_KILL_GRACE_USED = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath(WarbandMod.MOD_ID, "first_kill_grace_used"),
            builder -> builder.persistent(Codec.BOOL).copyOnDeath()
    );

    /** On a player: faction heat used to trigger elite bounty hunters. */
    public static final AttachmentType<java.util.List<FactionReputation>> ILLAGER_REPUTATION = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath(WarbandMod.MOD_ID, "illager_reputation"),
            builder -> builder.persistent(FactionReputation.CODEC.listOf()).copyOnDeath()
    );

    /**
     * On a player: set once the faction system has introduced itself, so the
     * one-time explainer never repeats.
     */
    public static final AttachmentType<Boolean> FACTION_INTRODUCED = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath(WarbandMod.MOD_ID, "faction_introduced"),
            builder -> builder.persistent(Codec.BOOL).copyOnDeath()
    );

    private WarbandAttachments() {
    }

    /** Touch this class so its attachments register. Called from {@code onInitialize}. */
    public static void init() {
        // Intentionally empty, referencing the class triggers static init above.
    }
}
