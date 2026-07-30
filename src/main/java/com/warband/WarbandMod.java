package com.warband;

import com.warband.ally.GolemDirector;
import com.warband.ai.EndermanProvokeHandler;
import com.warband.ai.FriendlyFireHandler;
import com.warband.ai.MultiplayerDirector;
import com.warband.command.WarbandCommand;
import com.warband.ai.SquadCoordinator;
import com.warband.ai.TemporaryTacticBlocks;
import com.warband.config.WarbandConfig;
import com.warband.difficulty.PlayerScore;
import com.warband.difficulty.RegionalDifficulty;
import com.warband.entity.WarbandAttachments;
import com.warband.illager.IllagerGrudgeSystem;
import com.warband.illager.StrongholdGarrison;
import com.warband.item.GoatHornCommand;
import com.warband.spawn.AntiFarmDirector;
import com.warband.spawn.BossDirector;
import com.warband.spawn.EncounterDirector;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common entrypoint, runs on dedicated servers and integrated servers.
 *
 * <p>Warband is a vanilla mob AI and spawning overhaul. The world is calm near
 * spawn and grows deadlier the further out you go (distance mode), or as nearby
 * players gear up and stake out territory (regional mode, see
 * {@link com.warband.difficulty.DifficultyMode}). Difficulty buffs not just mob
 * stats but their AI: squad spawns with combat roles, tactical retreat, calling
 * for backup. Applies to standard overworld, Nether, and End mobs.
 */
public final class WarbandMod implements ModInitializer {

    public static final String MOD_ID = "warband";
    public static final Logger LOGGER = LoggerFactory.getLogger("Warband");

    @Override
    public void onInitialize() {
        WarbandConfig.load(LOGGER);
        registerConfigReloads();
        WarbandAttachments.init();
        com.warband.advancement.WarbandCriteria.init();
        PlayerScore.register();
        RegionalDifficulty.register();
        WarbandCommand.register();
        TemporaryTacticBlocks.register();
        com.warband.ai.VehicleEscape.register();
        com.warband.ai.PerceptionCues.register();
        EncounterDirector.register();
        AntiFarmDirector.register();
        BossDirector.register();
        MultiplayerDirector.register();
        FriendlyFireHandler.register();
        EndermanProvokeHandler.register();
        com.warband.ai.VexBondHandler.register();
        GoatHornCommand.register();
        SquadCoordinator.register();
        GolemDirector.register();
        IllagerGrudgeSystem.register();
        StrongholdGarrison.register();
        com.warband.illager.RaidEvolutionHandler.register();

        LOGGER.info("[Warband] initialized");
    }

    /**
     * Re-read {@code config/warband.properties} whenever a world comes up, and on
     * {@code /reload}.
     *
     * <p>{@code onInitialize} runs once per process launch. On a client that is
     * before the title screen, so editing the config and then loading a world left
     * the old values in memory — indistinguishable from "the config file doesn't
     * save". Reloading on server start makes the edit-then-play loop work on
     * single-player and dedicated servers alike, without needing
     * {@code /warband reload}.
     */
    private static void registerConfigReloads() {
        ServerLifecycleEvents.SERVER_STARTING.register(server -> WarbandConfig.load(LOGGER));
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> WarbandConfig.load(LOGGER));
    }
}
