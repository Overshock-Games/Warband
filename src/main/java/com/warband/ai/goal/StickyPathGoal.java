package com.warband.ai.goal;

import com.warband.ai.Squad;
import com.warband.ai.TemporaryTacticBlocks;
import com.warband.entity.Tactic;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;

/**
 * Leaves short-lived webbing behind an active spider.
 *
 * <p>Skips placement when webbing is already adjacent. Several spiders sharing a
 * target used to lay overlapping trails on a 2-second cycle, carpeting the fight
 * floor rather than marking a path through it.
 */
public final class StickyPathGoal extends SquadGoal {

    private static final int COOLDOWN_TICKS = 20 * 5;
    private static final int WEB_LIFETIME_TICKS = 20 * 6;

    private BlockPos webPos;

    public StickyPathGoal(Mob mob, Squad squad) {
        super(mob, squad, 1.0);
    }

    @Override
    public boolean canUse() {
        if (visibleTarget() == null || !cooldownReady()) return false;
        webPos = mob.blockPosition();
        if (!mob.level().getBlockState(webPos).isAir()) return false;
        return !webbingAdjacent(webPos);
    }

    /** Cheap 6-neighbour check — a trail should be dotted, not a solid mat. */
    private boolean webbingAdjacent(BlockPos pos) {
        if (mob.level().getBlockState(pos).is(Blocks.COBWEB)) return true;
        for (Direction direction : Direction.values()) {
            if (mob.level().getBlockState(pos.relative(direction)).is(Blocks.COBWEB)) return true;
        }
        return false;
    }

    @Override
    public void start() {
        resetCooldown(COOLDOWN_TICKS);
        if (webPos != null) {
            if (TemporaryTacticBlocks.place((ServerLevel) mob.level(), webPos, Blocks.COBWEB, WEB_LIFETIME_TICKS)) {
                announceTactic(Tactic.STICKY_PATH);
            }
        }
    }
}
