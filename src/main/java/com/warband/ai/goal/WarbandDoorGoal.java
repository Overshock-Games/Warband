package com.warband.ai.goal;

import com.warband.WarbandDebug;
import com.warband.config.WarbandConfig;
import com.warband.entity.MobData;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.NodeEvaluator;

/**
 * Intelligent humanoids work a door handle instead of being stopped by it, and shut
 * it behind them.
 *
 * <p>Wraps vanilla's {@link OpenDoorGoal} rather than reimplementing it, adding the
 * config gate, a difficulty gate and a debug trace. The wrapper also exists for a
 * mechanical reason: Warband rebinds goals whenever a mob loads, clearing them with
 * {@code goal instanceof WarbandGoal}. A bare {@code OpenDoorGoal} would not match
 * that filter and would accumulate a duplicate on every world load.
 *
 * <p>Reserved for the mobs Warband already treats as intelligent enough to retreat —
 * illagers, piglins and drowned. Zombies are deliberately excluded: breaking a door
 * down is their signature, vanilla already grants it on Hard, and handing them a
 * quieter way through would both dilute that and buff them on Easy and Normal.
 *
 * <p>Unlocks at {@value #MIN_DIFFICULTY}, above ladder climbing (0.30) and below
 * siege mining (0.55). A ladder is usually incidental scenery, but a door is a
 * barrier a player chose to build, so getting past one should cost a little more
 * progression — and the reading is legible: they learn to open doors before they
 * learn to remove walls.
 */
public final class WarbandDoorGoal extends OpenDoorGoal implements WarbandGoal {

    private static final double MIN_DIFFICULTY = 0.40;

    private final Mob doorMob;

    public WarbandDoorGoal(Mob mob) {
        // true: close it behind us. Vanilla villagers do the same, and a squad filing
        // through and shutting up after itself reads as deliberate.
        super(mob, true);
        this.doorMob = mob;
    }

    /**
     * Lets this mob's pathfinder route through doors at all.
     *
     * <p>Without it {@link OpenDoorGoal} can never fire: it only triggers when the
     * <i>current path</i> already steps onto a door block, and by default the node
     * evaluator treats a closed door as impassable, so no such path is ever produced.
     *
     * @return true when door pathing is now enabled for this mob.
     */
    public static boolean enableDoorPathing(Mob mob) {
        PathNavigation navigation = mob.getNavigation();
        NodeEvaluator evaluator = navigation.getNodeEvaluator();
        if (evaluator == null) return false;
        evaluator.setCanPassDoors(true);
        evaluator.setCanOpenDoors(true);
        return true;
    }

    @Override
    public boolean canUse() {
        if (!WarbandConfig.doorOpeningEnabled) return false;
        if (MobData.get(doorMob).difficulty() < MIN_DIFFICULTY) return false;
        return super.canUse();
    }

    @Override
    public boolean canContinueToUse() {
        return WarbandConfig.doorOpeningEnabled && super.canContinueToUse();
    }

    @Override
    public void start() {
        super.start();
        if (WarbandDebug.enabled() && doorPos != null) {
            WarbandDebug.event("DOOR_OPEN", doorMob, "door=" + doorPos.getX() + " "
                    + doorPos.getY() + " " + doorPos.getZ());
        }
    }
}
