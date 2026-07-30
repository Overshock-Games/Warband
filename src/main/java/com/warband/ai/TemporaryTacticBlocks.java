package com.warband.ai;

import com.warband.config.WarbandConfig;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Tracks temporary block changes made by Warband tactics and undoes them later.
 *
 * <p>Two directions, one ledger:
 * <ul>
 *   <li><b>Placed</b> — a cobweb or frosted ice put down by a tactic, cleared on
 *       expiry.</li>
 *   <li><b>Mined</b> — a block a siege broke through, restored on expiry. This is
 *       what makes Warband's block-breaking non-destructive by default: a squad
 *       breaches a wall, fights the player through the hole, and the wall seals
 *       behind them. Warband is a server-side drop-in that people add to existing
 *       worlds, so permanently eating someone's base is not a defensible default.
 *       Set {@code siegeMiningPermanent=true} to keep the damage.</li>
 * </ul>
 */
public final class TemporaryTacticBlocks {

    private static final int BLOCK_UPDATE = 3;
    private static final List<Entry> ENTRIES = new ArrayList<>();

    private TemporaryTacticBlocks() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (ENTRIES.isEmpty()) return;

            Iterator<Entry> iterator = ENTRIES.iterator();
            while (iterator.hasNext()) {
                Entry entry = iterator.next();
                if (entry.level.getGameTime() < entry.expiresAt) continue;
                if (!entry.level.hasChunk(entry.pos.getX() >> 4, entry.pos.getZ() >> 4)) {
                    // Unloaded: drop the record rather than force-loading a chunk.
                    iterator.remove();
                    continue;
                }
                if (entry.restoreState == null) {
                    clearPlaced(entry);
                    iterator.remove();
                } else if (restoreMined(entry)) {
                    iterator.remove();
                }
                // A blocked restore keeps its entry and is retried next tick.
            }
        });
    }

    private static void clearPlaced(Entry entry) {
        if (entry.level.getBlockState(entry.pos).is(entry.block)) {
            entry.level.setBlock(entry.pos, Blocks.AIR.defaultBlockState(), BLOCK_UPDATE);
        }
    }

    /**
     * Puts a mined block back, but only when doing so is harmless.
     *
     * @return true when the entry is finished with (restored, or the spot was taken
     *         by something else so the record is moot). False asks for a retry,
     *         which is how a player standing in the hole delays the repair instead
     *         of being suffocated by it.
     */
    private static boolean restoreMined(Entry entry) {
        BlockState current = entry.level.getBlockState(entry.pos);
        // Someone rebuilt here, or another system claimed the space. Never overwrite.
        if (!current.isAir()) return true;
        if (isOccupied(entry.level, entry.pos, entry.restoreState)) return false;
        entry.level.setBlock(entry.pos, entry.restoreState, BLOCK_UPDATE);
        return true;
    }

    /** True when restoring this state would materialise a block inside something. */
    private static boolean isOccupied(ServerLevel level, BlockPos pos, BlockState state) {
        var shape = state.getCollisionShape(level, pos, CollisionContext.empty());
        if (shape.isEmpty()) return false;
        var box = shape.bounds().move(pos);
        for (Entity entity : level.getEntities((Entity) null, box, e -> !e.isRemoved() && !e.isSpectator())) {
            if (entity != null) return true;
        }
        return false;
    }

    public static boolean place(ServerLevel level, BlockPos pos, Block block, int ttlTicks) {
        return place(level, pos, block.defaultBlockState(), ttlTicks);
    }

    public static boolean place(ServerLevel level, BlockPos pos, BlockState state, int ttlTicks) {
        if (!WarbandConfig.temporaryTacticBlocks) return false;
        if (!level.getBlockState(pos).isAir()) return false;

        BlockPos immutable = pos.immutable();
        if (!level.setBlock(immutable, state, BLOCK_UPDATE)) return false;
        ENTRIES.add(new Entry(level, immutable, state.getBlock(), null, level.getGameTime() + ttlTicks));
        if (state.is(Blocks.COBWEB)) {
            TacticalEffects.web(level, immutable);
        }
        return true;
    }

    /**
     * Removes a block as siege damage. Restored after {@code ttlTicks} unless
     * {@code siegeMiningPermanent} is set, in which case it is simply gone and no
     * ledger entry is kept.
     *
     * @return true when the block was removed.
     */
    public static boolean mine(ServerLevel level, BlockPos pos, int ttlTicks) {
        BlockState original = level.getBlockState(pos);
        if (original.isAir()) return false;

        BlockPos immutable = pos.immutable();
        if (!level.destroyBlock(immutable, false)) return false;
        if (!WarbandConfig.siegeMiningPermanent) {
            ENTRIES.add(new Entry(level, immutable, null, original, level.getGameTime() + ttlTicks));
        }
        return true;
    }

    public static boolean freezeWater(ServerLevel level, BlockPos waterPos, int ttlTicks) {
        if (!WarbandConfig.temporaryTacticBlocks) return false;
        if (!level.getBlockState(waterPos).is(Blocks.WATER)) return false;

        BlockPos immutable = waterPos.immutable();
        if (!level.setBlock(immutable, Blocks.FROSTED_ICE.defaultBlockState(), BLOCK_UPDATE)) return false;
        ENTRIES.add(new Entry(level, immutable, Blocks.FROSTED_ICE, null, level.getGameTime() + ttlTicks));
        TacticalEffects.frost(level, immutable);
        return true;
    }

    /**
     * @param block        the block Warband placed, for a placement entry
     * @param restoreState the state to put back, for a mining entry; null means this
     *                     is a placement entry
     */
    private record Entry(ServerLevel level, BlockPos pos, @Nullable Block block,
                         @Nullable BlockState restoreState, long expiresAt) {
    }
}
