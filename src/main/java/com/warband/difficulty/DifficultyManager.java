package com.warband.difficulty;

import com.warband.config.WarbandConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * The single source of truth for local difficulty.
 *
 * <p>Everything else, stat buffs, AI tier, squad size, spawn pacing, reads one
 * normalized scalar from here: {@code 0.0} is vanilla-calm, {@code 1.0} is
 * maximum. Keep it that way; one scalar in, many systems out. The intent is also
 * to stamp this value onto each mob (see {@link com.warband.entity.MobData}) at
 * spawn, so a mob carries its own difficulty.
 *
 * <p>Vanilla difficulty does not scale Warband. Peaceful is the only special
 * case, because hostile AI should not run when the world is peaceful.
 */
public final class DifficultyManager {

    private DifficultyManager() {
    }

    /** Local difficulty at a position, normalized {@code 0.0 .. 1.0}. */
    public static double getDifficulty(ServerLevel level, BlockPos pos) {
        return getDifficulty(level, pos, null);
    }

    /**
     * Local difficulty at a position. The {@code player} argument is accepted
     * for API symmetry but no current mode uses it. Normalized {@code 0.0 .. 1.0}.
     */
    public static double getDifficulty(ServerLevel level, BlockPos pos, @Nullable Player player) {
        if (level.getLevelData().getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL) {
            return 0.0;
        }

        double value = rawDifficulty(level, pos, player);

        // Spawn-safe scaling for REGIONAL mode (DISTANCE already bakes a long
        // world-distance ramp in): the immediate spawn area stays calm, then
        // regional memory takes over quickly. Using maxDifficultyRadius here
        // made learned regional pressure look broken near spawn.
        if (WarbandConfig.difficultyMode == DifficultyMode.REGIONAL) {
            value *= regionalSpawnScale(level, pos);
        }
        // Apply environmental bonuses after the regional learner so newly-entered
        // caves/dimensions can still feel dangerous, while the immediate spawn
        // safe radius remains fully vanilla in the Overworld.
        if (!insideOverworldSafeRadius(level, pos)) {
            value += dimensionBonus(level);
            value += overworldDepthBonus(level, pos);
        }
        return clamp01(value);
    }

    /**
     * Regional spawn grace: calm inside safeRadius, full strength after
     * regionalSpawnRampBlocks.
     *
     * <p>Eased rather than linear. A linear ramp starts climbing at full rate the
     * moment you leave the safe radius and stops dead on arrival at the top, which
     * players read as a step — the reported "staircase rather than an upwards ramp".
     * Smoothstep flattens both ends so pressure creeps in and settles in.
     */
    public static double regionalSpawnScale(ServerLevel level, BlockPos pos) {
        if (!level.dimension().equals(Level.OVERWORLD)) return 1.0;
        if (level.getServer() == null) return 1.0;
        double safe = WarbandConfig.safeRadius;
        if (safe <= 0.0) return 1.0;
        BlockPos spawn = level.getServer().overworld().getRespawnData().pos();
        double dx = pos.getX() - spawn.getX();
        double dz = pos.getZ() - spawn.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        return smoothstep(clamp01((dist - safe) / WarbandConfig.regionalSpawnRampBlocks));
    }

    /** Classic 3t²-2t³ ease. Zero slope at both ends, so no visible seam. */
    private static double smoothstep(double t) {
        return t * t * (3.0 - 2.0 * t);
    }

    /** True only in the fully protected Overworld spawn-safe circle. */
    public static boolean insideOverworldSafeRadius(ServerLevel level, BlockPos pos) {
        if (!level.dimension().equals(Level.OVERWORLD)) return false;
        if (level.getServer() == null) return false;
        double safe = WarbandConfig.safeRadius;
        if (safe <= 0.0) return false;
        BlockPos spawn = level.getServer().overworld().getRespawnData().pos();
        double dx = pos.getX() - spawn.getX();
        double dz = pos.getZ() - spawn.getZ();
        return Math.sqrt(dx * dx + dz * dz) <= safe;
    }

    /** Additive Overworld depth pressure, independent of distance/regional mode. */
    public static double overworldDepthBonus(ServerLevel level, BlockPos pos) {
        if (!WarbandConfig.overworldDepthDifficultyEnabled) return 0.0;
        if (!level.dimension().equals(Level.OVERWORLD)) return 0.0;
        int start = WarbandConfig.overworldDepthStartY;
        int max = WarbandConfig.overworldDepthMaxY;
        if (start == max || pos.getY() >= start) return 0.0;
        double t = (start - pos.getY()) / (double) Math.max(1, start - max);
        // Eased for the same reason as the spawn ramp: descending into a cave is
        // fast, so a linear onset arrives as a jolt right at the threshold depth.
        return smoothstep(clamp01(t)) * WarbandConfig.overworldDepthBonusMax;
    }

    /** Per-dimension additive pressure, the Nether and End are inherently harsher. */
    private static double dimensionBonus(ServerLevel level) {
        if (level.dimension().equals(Level.NETHER)) {
            return WarbandConfig.netherDifficultyBonus;
        }
        if (level.dimension().equals(Level.END)) {
            return WarbandConfig.endDifficultyBonus;
        }
        return 0.0;
    }

    /** The raw scalar from the configured mode, before spawn/depth/dimension adjustment. */
    private static double rawDifficulty(ServerLevel level, BlockPos pos, @Nullable Player player) {
        return switch (WarbandConfig.difficultyMode) {
            case DISTANCE -> distanceDifficulty(level, pos);
            case REGIONAL -> RegionalDifficulty.difficultyAt(level, pos);
        };
    }

    private static double distanceDifficulty(ServerLevel level, BlockPos pos) {
        // Always anchor distance to overworld spawn; Nether/End "respawn data" is
        // not meaningful as a world origin, and the player's mental map is
        // overworld-rooted regardless of where they are.
        ServerLevel overworld = level.getServer() != null ? level.getServer().overworld() : level;
        BlockPos spawn = overworld.getRespawnData().pos();
        double dx = pos.getX() - spawn.getX();
        double dz = pos.getZ() - spawn.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);

        double safe = WarbandConfig.safeRadius;
        double max = Math.max(safe + 1.0, WarbandConfig.maxDifficultyRadius);
        return clamp01((dist - safe) / (max - safe));
    }

    private static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }
}
