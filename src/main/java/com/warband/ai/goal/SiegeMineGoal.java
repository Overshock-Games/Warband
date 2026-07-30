package com.warband.ai.goal;

import com.warband.ai.Squad;
import com.warband.ai.TemporaryTacticBlocks;
import com.warband.WarbandDebug;
import com.warband.config.WarbandConfig;
import com.warband.entity.MobData;
import com.warband.entity.Tactic;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Breaks through a wall when the target cannot be reached any other way.
 *
 * <p>This is the answer to the oldest cheese in the game: stand somewhere with no
 * valid path and the world has nothing to say. Warband already had squads, morale
 * and a shared blackboard, but a fence post ended every one of those systems, and
 * illager crusades that muster at a player's base were arriving only to mill about
 * outside.
 *
 * <p>Deliberately narrow. It only engages when
 * <ol>
 *   <li>pathfinding has actually failed — never as a shortcut past open ground,</li>
 *   <li>the obstructing block is listed in the configured block tag, so packs decide
 *       what is soft, and reinforcing with obsidian or metal remains real
 *       counterplay rather than a removed option,</li>
 *   <li>the {@code mobGriefing} gamerule allows it,</li>
 *   <li>the target is close enough that this reads as a breach and not as tunnelling
 *       in from the horizon.</li>
 * </ol>
 *
 * <p>Damage is reverted by {@link TemporaryTacticBlocks} unless
 * {@code siegeMiningPermanent} is set. Digging is telegraphed with real block-break
 * progress, so a player hears and sees the wall going before it goes.
 */
public final class SiegeMineGoal extends SquadGoal {

    /** Below this the mob is not considered smart enough to dig. */
    private static final double MIN_DIFFICULTY = 0.55;
    /** Beyond this the target is too far away for digging to read as a breach. */
    private static final double MAX_TARGET_DISTANCE = 24.0;
    /** Re-evaluate (and re-path) at most this often; path creation is not free. */
    private static final int DECISION_INTERVAL = 30;
    /** Ticks to chew through one block at minimum difficulty. */
    private static final int BASE_DIG_TICKS = 60;
    /** Ticks to chew through one block at difficulty 1.0. */
    private static final int FAST_DIG_TICKS = 24;
    /** Blocks removed per activation, so a breach is a hole and not a tunnel. */
    private static final int MAX_BLOCKS_PER_BREACH = 4;

    private BlockPos digTarget;
    private int digTicks;
    private int digTicksNeeded;
    private int blocksBroken;
    private int lastProgressStage = -1;

    public SiegeMineGoal(Mob mob, Squad squad) {
        super(mob, squad, 1.0);
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!WarbandConfig.siegeMiningEnabled) return false;
        if (frightened()) return false;
        if (!(mob.level() instanceof ServerLevel level)) return false;
        if (!Boolean.TRUE.equals(level.getGameRules().get(GameRules.MOB_GRIEFING))) return false;
        if (MobData.get(mob).difficulty() < MIN_DIFFICULTY) return false;
        if (blocksBroken >= MAX_BLOCKS_PER_BREACH) return false;
        if (!decisionReady(DECISION_INTERVAL)) return false;

        BlockPos goal = targetPosition();
        if (goal == null) return false;
        if (mob.distanceToSqr(Vec3.atCenterOf(goal)) > MAX_TARGET_DISTANCE * MAX_TARGET_DISTANCE) return false;
        // Only dig when walking there genuinely does not work.
        if (canPathTo(goal)) return false;

        digTarget = chooseBlock(level, goal);
        if (digTarget == null) return false;
        digTicks = 0;
        digTicksNeeded = digTicksFor(MobData.get(mob).difficulty());
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (digTarget == null) return false;
        if (frightened()) return false;
        if (!(mob.level() instanceof ServerLevel level)) return false;
        if (!isBreachable(level, digTarget)) return false;
        // Stay in reach of what we are digging; being knocked away cancels it.
        return mob.distanceToSqr(Vec3.atCenterOf(digTarget)) <= 9.0;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        mob.getNavigation().stop();
        WarbandDebug.event("SIEGE_DIG_START", mob, String.format(
                "diff=%.2f block=%s target=%s digTicks=%d broken=%d",
                MobData.get(mob).difficulty(),
                BuiltInRegistries.BLOCK.getKey(mob.level().getBlockState(digTarget).getBlock()),
                digTarget.getX() + " " + digTarget.getY() + " " + digTarget.getZ(),
                digTicksNeeded, blocksBroken));
    }

    @Override
    public void tick() {
        if (digTarget == null || !(mob.level() instanceof ServerLevel level)) return;

        mob.getLookControl().setLookAt(Vec3.atCenterOf(digTarget));
        digTicks++;

        // Vanilla block-break cracks: the telegraph. A player should always get the
        // chance to hear a wall being worked on and respond to it.
        int stage = Math.min(9, (digTicks * 10) / Math.max(1, digTicksNeeded));
        if (stage != lastProgressStage) {
            lastProgressStage = stage;
            level.destroyBlockProgress(mob.getId(), digTarget, stage);
        }

        if (digTicks < digTicksNeeded) return;

        if (TemporaryTacticBlocks.mine(level, digTarget, WarbandConfig.siegeMiningRestoreSeconds * 20)) {
            blocksBroken++;
            logTactic(Tactic.SIEGE_MINE);
        }
        level.destroyBlockProgress(mob.getId(), digTarget, -1);
        digTarget = null;
        lastProgressStage = -1;
        resetCooldown(DECISION_INTERVAL);
    }

    @Override
    public void stop() {
        if (digTarget != null && mob.level() instanceof ServerLevel level) {
            level.destroyBlockProgress(mob.getId(), digTarget, -1);
        }
        digTarget = null;
        lastProgressStage = -1;
        blocksBroken = 0;
    }

    private int digTicksFor(double difficulty) {
        double t = Math.max(0.0, Math.min(1.0, (difficulty - MIN_DIFFICULTY) / (1.0 - MIN_DIFFICULTY)));
        return (int) Math.round(BASE_DIG_TICKS + (FAST_DIG_TICKS - BASE_DIG_TICKS) * t);
    }

    /** Where the squad is trying to get to: a seen target, else the shared last-known position. */
    private @Nullable BlockPos targetPosition() {
        LivingEntity target = visibleTarget();
        if (target != null) return target.blockPosition();
        return squad.lastKnownPos();
    }

    private boolean canPathTo(BlockPos goal) {
        Path path = mob.getNavigation().createPath(goal, 0);
        return path != null && path.canReach();
    }

    /**
     * The obstruction to remove: the block directly in the way, preferring head
     * height so the mob opens a gap it can walk through. Falls back to digging up or
     * down when the target is mostly above or below.
     */
    private @Nullable BlockPos chooseBlock(ServerLevel level, BlockPos goal) {
        Vec3 toGoal = Vec3.atCenterOf(goal).subtract(mob.position());
        Vec3 flat = new Vec3(toGoal.x, 0.0, toGoal.z);
        BlockPos feet = mob.blockPosition();

        List<BlockPos> candidates = new ArrayList<>(4);
        if (flat.lengthSqr() > 0.01) {
            Vec3 step = flat.normalize();
            BlockPos ahead = BlockPos.containing(
                    mob.getX() + step.x, mob.getY(), mob.getZ() + step.z);
            candidates.add(ahead.above());
            candidates.add(ahead);
        }
        double rise = goal.getY() - mob.getY();
        if (rise > 2.0) {
            candidates.add(feet.above(2));
        } else if (rise < -2.0) {
            candidates.add(feet.below());
        }

        for (BlockPos candidate : candidates) {
            if (isBreachable(level, candidate)) return candidate.immutable();
        }
        return null;
    }

    private boolean isBreachable(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return false;
        // Never chew on the unbreakable; also skips bedrock/barriers generically.
        if (state.getDestroySpeed(level, pos) < 0.0F) return false;
        if (state.is(BlockTags.WITHER_IMMUNE)) return false;
        if (state.hasBlockEntity()) return false;
        return state.is(breakableTag());
    }

    /** Resolved per call so {@code /warband reload} can repoint the tag without a restart. */
    private static TagKey<Block> breakableTag() {
        Identifier id = Identifier.tryParse(WarbandConfig.siegeMiningBlockTag);
        return TagKey.create(net.minecraft.core.registries.Registries.BLOCK,
                id == null ? Identifier.fromNamespaceAndPath("warband", "siege_breakable") : id);
    }
}
