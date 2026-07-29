package com.warband.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Finds the nearest sky-covered tile a mob can actually stand in.
 *
 * <p>Shared by {@link com.warband.ai.goal.SeekShelterGoal} (undead at dawn) and
 * {@link com.warband.ai.goal.SpiderRainShelterGoal} (spiders in rain).
 *
 * <p>Searched outward ring by ring so the first hit is the nearest and the common
 * case — shade a block or two away — exits almost immediately. A full-volume scan
 * costs {@code (2r+1)² × (2v+1)} block lookups per call (over 2000 at the default
 * radius) and ran on every undead every recheck; that showed up as tick time in
 * crowded areas.
 */
public final class ShelterScan {

    private ShelterScan() {
    }

    /**
     * @param needsHeadroom require a second air block above, so a two-block-tall
     *                      mob is not sent to a one-block gap it cannot occupy.
     *                      Unreachable targets make navigation fail and read to
     *                      players as a mob stuck twitching in place.
     */
    public static @Nullable BlockPos nearestCovered(Level level, BlockPos origin,
                                                    int radius, int verticalRadius,
                                                    boolean needsHeadroom) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int r = 0; r <= radius; r++) {
            BlockPos found = scanRing(level, origin, cursor, r, verticalRadius, needsHeadroom);
            if (found != null) return found;
        }
        return null;
    }

    /** Perimeter of the square at Chebyshev distance {@code r}, or the origin column when r is 0. */
    private static @Nullable BlockPos scanRing(Level level, BlockPos origin, BlockPos.MutableBlockPos cursor,
                                               int r, int verticalRadius, boolean needsHeadroom) {
        if (r == 0) {
            return scanColumn(level, origin, cursor, 0, 0, verticalRadius, needsHeadroom);
        }
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                // Interior was already covered by a smaller ring.
                if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                BlockPos found = scanColumn(level, origin, cursor, dx, dz, verticalRadius, needsHeadroom);
                if (found != null) return found;
            }
        }
        return null;
    }

    /** Vertical offsets nearest the mob's own feet first, so it prefers a level walk to a climb. */
    private static @Nullable BlockPos scanColumn(Level level, BlockPos origin, BlockPos.MutableBlockPos cursor,
                                                 int dx, int dz, int verticalRadius, boolean needsHeadroom) {
        for (int step = 0; step <= verticalRadius * 2; step++) {
            int dy = (step + 1) / 2;
            if (step % 2 == 1) dy = -dy;
            cursor.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
            if (isCovered(level, cursor, needsHeadroom)) return cursor.immutable();
        }
        return null;
    }

    private static boolean isCovered(Level level, BlockPos pos, boolean needsHeadroom) {
        if (level.canSeeSky(pos)) return false;
        if (!level.getBlockState(pos).isAir()) return false;
        if (level.getBlockState(pos.below()).isAir()) return false;
        return !needsHeadroom || level.getBlockState(pos.above()).isAir();
    }
}
