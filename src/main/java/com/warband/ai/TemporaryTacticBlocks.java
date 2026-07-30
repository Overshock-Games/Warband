package com.warband.ai;

import com.warband.WarbandDebug;
import com.warband.config.WarbandConfig;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

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
    /**
     * How long a resealed block is immune to being mined again.
     *
     * <p>Without this, siege mining and the reseal form an endless loop: dig the block,
     * wait out the restore, dig the identical block again, forever, with block-break
     * audio every cycle. A short immunity turns a walled-in standoff into "the breach
     * stays open while they are working on it" instead of Sisyphus with a pickaxe.
     */
    private static final int REBREAK_IMMUNITY_TICKS = 20 * 45;

    private static final List<Entry> ENTRIES = new ArrayList<>();
    /** Recently resealed positions, keyed by packed BlockPos, valued by expiry tick. */
    private static final Map<Long, Long> RECENTLY_RESTORED = new HashMap<>();

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
        if (!current.isAir()) {
            WarbandDebug.event("SIEGE_RESTORE_SKIPPED", posDetail(entry.pos) + " reason=occupied_by_block");
            return true;
        }
        if (isOccupied(entry.level, entry.pos, entry.restoreState)) {
            WarbandDebug.event("SIEGE_RESTORE_DEFERRED", posDetail(entry.pos) + " reason=entity_inside");
            return false;
        }
        if (!stillPartOfSomething(entry.level, entry.pos)) {
            WarbandDebug.event("SIEGE_RESTORE_SKIPPED", posDetail(entry.pos) + " reason=structure_gone");
            return true;
        }
        entry.level.setBlock(entry.pos, entry.restoreState, BLOCK_UPDATE);
        RECENTLY_RESTORED.put(entry.pos.asLong(), entry.level.getGameTime() + REBREAK_IMMUNITY_TICKS);
        // A block silently popping back reads as a glitch. Give it a small, quiet cue
        // so a reseal is legible as something the world did on purpose.
        entry.level.playSound(null, entry.pos, entry.restoreState.getSoundType().getPlaceSound(),
                SoundSource.BLOCKS, 0.45f, 0.85f);
        entry.level.sendParticles(ParticleTypes.CRIT,
                entry.pos.getX() + 0.5, entry.pos.getY() + 0.5, entry.pos.getZ() + 0.5,
                4, 0.25, 0.25, 0.25, 0.0);
        WarbandDebug.event("SIEGE_RESTORE", posDetail(entry.pos)
                + " block=" + BuiltInRegistries.BLOCK.getKey(entry.restoreState.getBlock()));
        return true;
    }

    /**
     * Whether restoring here would repair a hole rather than conjure a floating block.
     *
     * <p>Restoring was originally context-free: it only asked whether the exact spot was
     * empty. That is fine for a breach in a wall that is still standing, but absurd when
     * the structure has since gone — a mob mines the base of a pillar, the player takes
     * the pillar down, and ninety seconds later a single block reappears in mid-air.
     *
     * <p>Requiring two solid orthogonal neighbours is a cheap proxy for "this was part of
     * something that still exists". A wall breach keeps three or four of its neighbours,
     * a hole in a floor keeps several, while an isolated block or the remains of a
     * dismantled pillar keeps one or none and is left alone.
     */
    private static boolean stillPartOfSomething(ServerLevel level, BlockPos pos) {
        int solidNeighbours = 0;
        for (Direction direction : Direction.values()) {
            if (!level.getBlockState(pos.relative(direction)).isAir()) {
                solidNeighbours++;
                if (solidNeighbours >= 2) return true;
            }
        }
        return false;
    }

    /**
     * True while this position must not be mined again, because Warband only just put
     * it back. Also prunes its own expired entries, so the map cannot grow unbounded.
     */
    public static boolean recentlyRestored(ServerLevel level, BlockPos pos) {
        if (RECENTLY_RESTORED.isEmpty()) return false;
        long now = level.getGameTime();
        RECENTLY_RESTORED.values().removeIf(expiry -> expiry <= now);
        Long expiry = RECENTLY_RESTORED.get(pos.asLong());
        return expiry != null && expiry > now;
    }

    private static String posDetail(BlockPos pos) {
        return "pos=" + pos.getX() + " " + pos.getY() + " " + pos.getZ();
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
        WarbandDebug.event("SIEGE_BLOCK_REMOVED", posDetail(immutable)
                + " block=" + BuiltInRegistries.BLOCK.getKey(original.getBlock())
                + " permanent=" + WarbandConfig.siegeMiningPermanent
                + " restoreIn=" + (WarbandConfig.siegeMiningPermanent ? "never" : ttlTicks + "t")
                + " pending=" + ENTRIES.size());
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
