package com.warband.config;

import com.warband.difficulty.DifficultyMode;
import com.warband.entity.Tactic;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Properties;

/**
 * File-backed config at {@code config/warband.properties}.
 *
 * <p>Written with {@link Files#writeString} from a text block, never
 * {@code Properties.store()} (timestamp + encoding noise on Windows).
 */
public final class WarbandConfig {

    // ── Difficulty ──────────────────────────────────────────────────────────
    public static ConfigProfile configProfile = ConfigProfile.CUSTOM;
    public static DifficultyMode difficultyMode = DifficultyMode.REGIONAL;
    /** Blocks from world spawn that stay fully vanilla (difficulty 0). */
    public static int safeRadius = 96;
    /**
     * REGIONAL mode: blocks after safeRadius until learned pressure reaches full
     * strength. A short ramp reads as a difficulty cliff — at 32 the world went
     * from fully calm to fully hostile over one screen's worth of travel.
     */
    public static int regionalSpawnRampBlocks = 256;
    /** Distance from spawn at which difficulty caps (DISTANCE mode). */
    public static int maxDifficultyRadius = 4096;
    /** Optional mercy window after death. 0 disables relief and keeps regional pressure honest. */
    public static int deathReliefSeconds = 0;
    /** How much relief eases difficulty when enabled: 0.0 none .. 1.0 full calm. */
    public static double deathReliefStrength = 0.5;
    /** Fraction of the gap a player's capability score decays toward gear each second. */
    public static double scoreDecayRate = 0.005;
    /** REGIONAL mode: chunks around players sampled each interval. */
    public static int regionalSampleRadiusChunks = 2;
    public static double regionalBlendRate = 0.20;
    public static double regionalAccelerationPerSample = 0.03;
    public static double regionalAccelerationMax = 0.25;
    public static double regionalDecayRate = 0.002;
    /** REGIONAL mode: pressure must persist this long before a cell rises. */
    public static int regionalIncreaseDelaySeconds = 0;
    /** REGIONAL mode: cell decay waits this long after last pressure. */
    public static int regionalDecayDelaySeconds = 120;
    /** REGIONAL mode: extra difficulty added per additional nearby player. */
    public static double regionalPlayerBonus = 0.15;
    /** If true, Overworld caves add difficulty below the configured Y range. */
    public static boolean overworldDepthDifficultyEnabled = true;
    /** Y where Overworld depth difficulty starts adding pressure. */
    public static int overworldDepthStartY = 48;
    /** Y where Overworld depth difficulty reaches its maximum bonus. */
    public static int overworldDepthMaxY = -48;
    /** Maximum additive Overworld depth difficulty bonus. */
    public static double overworldDepthBonusMax = 0.25;
    /** Extra difficulty added in the Nether, regardless of mode. */
    public static double netherDifficultyBonus = 0.25;
    /** Extra difficulty added in the End, regardless of mode. */
    public static double endDifficultyBonus = 0.35;

    // ── Squads & spawning ───────────────────────────────────────────────────
    public static boolean squadsEnabled = true;
    /** Base squad-size cap for a solo player; raised by {@link #squadPlayerBonus}. */
    public static int maxSquadSize = 6;
    /** Extra squad slots allowed per additional player sharing the region. */
    public static int squadPlayerBonus = 2;
    public static double naturalSquadChanceMin = 0.35;
    public static double naturalSquadChanceMax = 0.60;
    /** Performance cap, most "smart AI" mobs ticked per player at once. */
    public static int maxSmartMobsPerPlayer = 24;
    /** If true, spawned hostile mobs get difficulty-scaled stat buffs. */
    public static boolean statBuffsEnabled = true;
    public static double statHealthBonusMax = 0.35;
    public static double statDamageBonusMax = 0.20;
    public static double statSpeedBonusMax = 0.08;
    public static double statKnockbackResistanceMax = 0.15;
    /** If true, situational tactics may place short-lived blocks like cobwebs/ice. */
    public static boolean temporaryTacticBlocks = true;
    /** If true, Warband enhancement intensity cycles through build-up, peak, and relax windows. */
    public static boolean encounterDirectorEnabled = true;
    /** Extra enhancement chance per additional player sharing the region. */
    public static double encounterPlayerBonus = 0.15;
    public static int directorBuildUpSeconds = 240;
    public static int directorPeakSeconds = 120;
    public static int directorRelaxSeconds = 180;
    public static double directorBuildUpEnhancementChance = 0.70;
    public static double directorRelaxEnhancementChance = 0.15;
    public static boolean roleVisualsEnabled = true;
    public static boolean roleCuesEnabled = true;
    public static boolean antiFarmEnabled = true;
    public static int antiFarmCrowdThreshold = 10;
    public static int antiFarmScanSeconds = 5;
    public static int antiFarmTier1Crowd = 8;
    public static int antiFarmTier2Crowd = 14;
    public static int antiFarmTier3Crowd = 22;
    public static boolean experienceScalingEnabled = true;
    public static double experienceDifficultyBonusMax = 0.35;
    public static double experienceLeaderBonus = 0.15;
    public static boolean witherAbilitiesEnabled = true;
    public static boolean enderDragonAbilitiesEnabled = true;
    public static boolean extendedMobTacticsEnabled = true;
    /** Comma-separated tactic names to suppress while keeping other smart AI active. */
    public static String disabledTactics = "";
    /**
     * Opts modded mobs into Warband behaviour pools. See
     * {@link com.warband.entity.MobPools} for the format.
     */
    public static String customMobPools = "";
    private static EnumSet<Tactic> disabledTacticSet = EnumSet.noneOf(Tactic.class);
    /** If true, log each Warband tactic execution for debugging and balance passes. */
    public static boolean debugTacticLogs = false;
    /** Sun-sensitive undead path to shade at dawn instead of waiting to burn. */
    public static boolean seekShelterEnabled = true;
    /** Endermen shot by a player teleport next to the shooter and aggro, instead of just dodging. */
    public static boolean endermanProvokeEnabled = true;
    /** Minimum difficulty for mobs spawned by mob spawners, trial spawners, and summons. Encounter setpieces shouldn't be trivial. */
    public static double spawnerDifficultyFloor = 0.45;
    /** Smart skeletons and baby zombies will opportunistically mount nearby spiders/chickens out of combat. */
    public static boolean naturalJockeysEnabled = true;
    /** Difficulty required before a mob is "smart enough" to form a jockey on its own. */
    public static double naturalJockeyMinDifficulty = 0.35;
    /** Active raiders pillage every animal in range (leashed, named, tamed, baby — the lot) once no village defenders are left. */
    public static boolean raidPredationEnabled = true;
    /** High-ominous raids summon a faction bounty hunter during the final wave to finish the player personally. */
    public static boolean raidFinaleBountyEnabled = true;
    /** Medium-or-higher ominous raids can attract a rival faction patrol that attacks the raiders. */
    public static boolean raidRivalInterceptEnabled = true;

    // ── Multiplayer ────────────────────────────────────────────────────────
    public static boolean multiplayerFeaturesEnabled = true;
    public static int multiplayerSmartMobsPerExtraPlayer = 8;
    public static double multiplayerEncounterBonusPerExtraPlayer = 0.12;
    public static int multiplayerDeathMercySeconds = 45;
    public static int multiplayerDeathMercyRadius = 48;
    public static double multiplayerDeathMercyStrength = 0.65;
    public static double multiplayerDogpilePenalty = 18.0;

    // ── Illagers ────────────────────────────────────────────────────────────
    public static boolean illagerFactionsEnabled = true;
    public static boolean illagerDoctrineEnabled = true;
    public static boolean illagerGrudgesEnabled = true;
    public static boolean illagerRivalriesEnabled = true;
    public static boolean illagerRaidDoctrineEnabled = true;
    public static boolean illagerFactionBannersEnabled = true;
    public static boolean illagerRoleGearEnabled = true;
    public static boolean illagerBountyHuntersEnabled = true;
    /** If true, mansions and outposts become elevated faction strongholds. */
    public static boolean illagerStrongholdsEnabled = true;
    /** Minimum difficulty for the garrison of a mansion-tier faction seat. */
    public static double mansionGarrisonFloor = 1.0;
    /** Minimum difficulty for the garrison of an outpost-tier faction camp. */
    public static double outpostGarrisonFloor = 0.5;
    /** Boss bonus health for a mansion Warmarshal (base multiplier, on top of stat buffs). */
    public static double warmarshalHealthBonus = 1.5;
    /** Boss bonus attack damage for a mansion Warmarshal (base multiplier). */
    public static double warmarshalDamageBonus = 0.5;

    // ── Items ──────────────────────────────────────────────────────────────
    public static boolean goatHornCommandEnabled = true;

    private static Path configPath;

    private WarbandConfig() {
    }

    /**
     * Absolute path to {@code config/warband.properties}, resolved through the
     * loader rather than the process working directory.
     *
     * <p>A bare relative path resolves against wherever the JVM was launched
     * from, which is only the game directory by luck. Dedicated servers started
     * from a parent directory (or via a wrapper script) read and wrote a
     * <i>different</i> file than the one in the pack's {@code config/} folder,
     * so edits looked like they were silently discarded.
     *
     * <p>Falls back to the old relative path when no loader is present, so this
     * class stays usable from plain unit tests.
     */
    private static Path configPath() {
        if (configPath == null) {
            try {
                configPath = net.fabricmc.loader.api.FabricLoader.getInstance()
                        .getConfigDir().resolve("warband.properties");
            } catch (Throwable ignored) {
                configPath = Path.of("config", "warband.properties");
            }
        }
        return configPath;
    }

    /** Where the config actually lives, for command feedback. */
    public static String configLocation() {
        return configPath().toString();
    }

    public static void load(Logger logger) {
        Path path = configPath();
        Properties props = new Properties();
        if (Files.exists(path)) {
            try (Reader r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                props.load(r);
            } catch (IOException e) {
                logger.error("[Warband] Failed to read config, using defaults", e);
            }
        }

        configProfile = parseProfile(props, configProfile, logger);
        difficultyMode = parseMode(props, difficultyMode, logger);
        safeRadius = parseInt(props, "safeRadius", safeRadius, 0, 100_000, logger);
        maxDifficultyRadius = parseInt(props, "maxDifficultyRadius", maxDifficultyRadius, 1, 1_000_000, logger);
        deathReliefSeconds = parseInt(props, "deathReliefSeconds", deathReliefSeconds, 0, 100_000, logger);
        deathReliefStrength = parseDouble(props, "deathReliefStrength", deathReliefStrength, 0.0, 1.0, logger);
        scoreDecayRate = parseDouble(props, "scoreDecayRate", scoreDecayRate, 0.0, 1.0, logger);
        regionalSampleRadiusChunks = parseInt(props, "regionalSampleRadiusChunks", regionalSampleRadiusChunks, 0, 16, logger);
        regionalBlendRate = parseDouble(props, "regionalBlendRate", regionalBlendRate, 0.0, 1.0, logger);
        regionalAccelerationPerSample = parseDouble(props, "regionalAccelerationPerSample", regionalAccelerationPerSample, 0.0, 1.0, logger);
        regionalAccelerationMax = parseDouble(props, "regionalAccelerationMax", regionalAccelerationMax, 0.0, 1.0, logger);
        regionalDecayRate = parseDouble(props, "regionalDecayRate", regionalDecayRate, 0.0, 1.0, logger);
        regionalIncreaseDelaySeconds = parseInt(props, "regionalIncreaseDelaySeconds", regionalIncreaseDelaySeconds, 0, 3600, logger);
        regionalDecayDelaySeconds = parseInt(props, "regionalDecayDelaySeconds", regionalDecayDelaySeconds, 0, 100_000, logger);
        regionalPlayerBonus = parseDouble(props, "regionalPlayerBonus", regionalPlayerBonus, 0.0, 1.0, logger);
        regionalSpawnRampBlocks = parseInt(props, "regionalSpawnRampBlocks", regionalSpawnRampBlocks, 1, 100_000, logger);
        overworldDepthDifficultyEnabled = parseBoolean(props, "overworldDepthDifficultyEnabled", overworldDepthDifficultyEnabled, logger);
        overworldDepthStartY = parseInt(props, "overworldDepthStartY", overworldDepthStartY, -2048, 2048, logger);
        overworldDepthMaxY = parseInt(props, "overworldDepthMaxY", overworldDepthMaxY, -2048, 2048, logger);
        overworldDepthBonusMax = parseDouble(props, "overworldDepthBonusMax", overworldDepthBonusMax, 0.0, 1.0, logger);
        netherDifficultyBonus = parseDouble(props, "netherDifficultyBonus", netherDifficultyBonus, 0.0, 1.0, logger);
        endDifficultyBonus = parseDouble(props, "endDifficultyBonus", endDifficultyBonus, 0.0, 1.0, logger);

        squadsEnabled = parseBoolean(props, "squadsEnabled", squadsEnabled, logger);
        maxSquadSize = parseInt(props, "maxSquadSize", maxSquadSize, 1, 64, logger);
        squadPlayerBonus = parseInt(props, "squadPlayerBonus", squadPlayerBonus, 0, 32, logger);
        naturalSquadChanceMin = parseDouble(props, "naturalSquadChanceMin", naturalSquadChanceMin, 0.0, 1.0, logger);
        naturalSquadChanceMax = parseDouble(props, "naturalSquadChanceMax", naturalSquadChanceMax, 0.0, 1.0, logger);
        maxSmartMobsPerPlayer = parseInt(props, "maxSmartMobsPerPlayer", maxSmartMobsPerPlayer, 1, 512, logger);
        statBuffsEnabled = parseBoolean(props, "statBuffsEnabled", statBuffsEnabled, logger);
        statHealthBonusMax = parseDouble(props, "statHealthBonusMax", statHealthBonusMax, 0.0, 10.0, logger);
        statDamageBonusMax = parseDouble(props, "statDamageBonusMax", statDamageBonusMax, 0.0, 10.0, logger);
        statSpeedBonusMax = parseDouble(props, "statSpeedBonusMax", statSpeedBonusMax, 0.0, 10.0, logger);
        statKnockbackResistanceMax = parseDouble(props, "statKnockbackResistanceMax", statKnockbackResistanceMax, 0.0, 1.0, logger);
        temporaryTacticBlocks = parseBoolean(props, "temporaryTacticBlocks", temporaryTacticBlocks, logger);
        encounterDirectorEnabled = parseBoolean(props, "encounterDirectorEnabled", encounterDirectorEnabled, logger);
        encounterPlayerBonus = parseDouble(props, "encounterPlayerBonus", encounterPlayerBonus, 0.0, 1.0, logger);
        directorBuildUpSeconds = parseInt(props, "directorBuildUpSeconds", directorBuildUpSeconds, 1, 100_000, logger);
        directorPeakSeconds = parseInt(props, "directorPeakSeconds", directorPeakSeconds, 1, 100_000, logger);
        directorRelaxSeconds = parseInt(props, "directorRelaxSeconds", directorRelaxSeconds, 1, 100_000, logger);
        directorBuildUpEnhancementChance = parseDouble(props, "directorBuildUpEnhancementChance", directorBuildUpEnhancementChance, 0.0, 1.0, logger);
        directorRelaxEnhancementChance = parseDouble(props, "directorRelaxEnhancementChance", directorRelaxEnhancementChance, 0.0, 1.0, logger);
        roleVisualsEnabled = parseBoolean(props, "roleVisualsEnabled", roleVisualsEnabled, logger);
        roleCuesEnabled = parseBoolean(props, "roleCuesEnabled", roleCuesEnabled, logger);
        antiFarmEnabled = parseBoolean(props, "antiFarmEnabled", antiFarmEnabled, logger);
        antiFarmCrowdThreshold = parseInt(props, "antiFarmCrowdThreshold", antiFarmCrowdThreshold, 3, 128, logger);
        antiFarmScanSeconds = parseInt(props, "antiFarmScanSeconds", antiFarmScanSeconds, 1, 600, logger);
        antiFarmTier1Crowd = parseInt(props, "antiFarmTier1Crowd", antiFarmTier1Crowd, 3, 128, logger);
        antiFarmTier2Crowd = parseInt(props, "antiFarmTier2Crowd", antiFarmTier2Crowd, 3, 128, logger);
        antiFarmTier3Crowd = parseInt(props, "antiFarmTier3Crowd", antiFarmTier3Crowd, 3, 128, logger);
        experienceScalingEnabled = parseBoolean(props, "experienceScalingEnabled", experienceScalingEnabled, logger);
        experienceDifficultyBonusMax = parseDouble(props, "experienceDifficultyBonusMax", experienceDifficultyBonusMax, 0.0, 5.0, logger);
        experienceLeaderBonus = parseDouble(props, "experienceLeaderBonus", experienceLeaderBonus, 0.0, 5.0, logger);
        boolean legacyBossAbilities = parseBoolean(props, "bossAbilitiesEnabled", true, logger);
        witherAbilitiesEnabled = parseBoolean(props, "witherAbilitiesEnabled", legacyBossAbilities, logger);
        enderDragonAbilitiesEnabled = parseBoolean(props, "enderDragonAbilitiesEnabled", legacyBossAbilities, logger);
        extendedMobTacticsEnabled = parseBoolean(props, "extendedMobTacticsEnabled", extendedMobTacticsEnabled, logger);
        disabledTactics = props.getProperty("disabledTactics", disabledTactics).trim();
        disabledTacticSet = parseTacticSet(disabledTactics, logger);
        customMobPools = props.getProperty("customMobPools", customMobPools).trim();
        com.warband.entity.MobPools.load(customMobPools, logger);
        debugTacticLogs = parseBoolean(props, "debugTacticLogs", debugTacticLogs, logger);
        seekShelterEnabled = parseBoolean(props, "seekShelterEnabled", seekShelterEnabled, logger);
        endermanProvokeEnabled = parseBoolean(props, "endermanProvokeEnabled", endermanProvokeEnabled, logger);
        spawnerDifficultyFloor = parseDouble(props, "spawnerDifficultyFloor", spawnerDifficultyFloor, 0.0, 1.0, logger);
        naturalJockeysEnabled = parseBoolean(props, "naturalJockeysEnabled", naturalJockeysEnabled, logger);
        naturalJockeyMinDifficulty = parseDouble(props, "naturalJockeyMinDifficulty", naturalJockeyMinDifficulty, 0.0, 1.0, logger);
        raidPredationEnabled = parseBoolean(props, "raidPredationEnabled", raidPredationEnabled, logger);
        raidFinaleBountyEnabled = parseBoolean(props, "raidFinaleBountyEnabled", raidFinaleBountyEnabled, logger);
        raidRivalInterceptEnabled = parseBoolean(props, "raidRivalInterceptEnabled", raidRivalInterceptEnabled, logger);

        multiplayerFeaturesEnabled = parseBoolean(props, "multiplayerFeaturesEnabled", multiplayerFeaturesEnabled, logger);
        multiplayerSmartMobsPerExtraPlayer = parseInt(props, "multiplayerSmartMobsPerExtraPlayer", multiplayerSmartMobsPerExtraPlayer, 0, 128, logger);
        multiplayerEncounterBonusPerExtraPlayer = parseDouble(props, "multiplayerEncounterBonusPerExtraPlayer", multiplayerEncounterBonusPerExtraPlayer, 0.0, 2.0, logger);
        multiplayerDeathMercySeconds = parseInt(props, "multiplayerDeathMercySeconds", multiplayerDeathMercySeconds, 0, 3600, logger);
        multiplayerDeathMercyRadius = parseInt(props, "multiplayerDeathMercyRadius", multiplayerDeathMercyRadius, 1, 512, logger);
        multiplayerDeathMercyStrength = parseDouble(props, "multiplayerDeathMercyStrength", multiplayerDeathMercyStrength, 0.0, 1.0, logger);
        multiplayerDogpilePenalty = parseDouble(props, "multiplayerDogpilePenalty", multiplayerDogpilePenalty, 0.0, 100.0, logger);

        illagerFactionsEnabled = parseBoolean(props, "illagerFactionsEnabled", illagerFactionsEnabled, logger);
        illagerDoctrineEnabled = parseBoolean(props, "illagerDoctrineEnabled", illagerDoctrineEnabled, logger);
        illagerGrudgesEnabled = parseBoolean(props, "illagerGrudgesEnabled", illagerGrudgesEnabled, logger);
        illagerRivalriesEnabled = parseBoolean(props, "illagerRivalriesEnabled", illagerRivalriesEnabled, logger);
        illagerRaidDoctrineEnabled = parseBoolean(props, "illagerRaidDoctrineEnabled", illagerRaidDoctrineEnabled, logger);
        illagerFactionBannersEnabled = parseBoolean(props, "illagerFactionBannersEnabled", illagerFactionBannersEnabled, logger);
        illagerRoleGearEnabled = parseBoolean(props, "illagerRoleGearEnabled", illagerRoleGearEnabled, logger);
        illagerBountyHuntersEnabled = parseBoolean(props, "illagerBountyHuntersEnabled", illagerBountyHuntersEnabled, logger);
        illagerStrongholdsEnabled = parseBoolean(props, "illagerStrongholdsEnabled", illagerStrongholdsEnabled, logger);
        mansionGarrisonFloor = parseDouble(props, "mansionGarrisonFloor", mansionGarrisonFloor, 0.0, 1.0, logger);
        outpostGarrisonFloor = parseDouble(props, "outpostGarrisonFloor", outpostGarrisonFloor, 0.0, 1.0, logger);
        warmarshalHealthBonus = parseDouble(props, "warmarshalHealthBonus", warmarshalHealthBonus, 0.0, 10.0, logger);
        warmarshalDamageBonus = parseDouble(props, "warmarshalDamageBonus", warmarshalDamageBonus, 0.0, 10.0, logger);
        goatHornCommandEnabled = parseBoolean(props, "goatHornCommandEnabled", goatHornCommandEnabled, logger);

        save(logger);
        applyProfile();
        logger.info("[Warband] Config loaded");
    }

    public static void save(Logger logger) {
        Path path = configPath();
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, toPropertiesString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.error("[Warband] Failed to save config to {}", path, e);
        }
    }

    private static String toPropertiesString() {
        return """
                # Warband configuration
                # Changes take effect on world reload or server restart.

                # ── Difficulty ────────────────────────────────────────────────────
                # Preset override: CUSTOM, VANILLA_PLUS (or vanilla+), FANTASY. CUSTOM respects every value below.
                configProfile=%s
                # How local difficulty is derived: DISTANCE or REGIONAL.
                difficultyMode=%s
                # Blocks from world spawn that stay fully vanilla (difficulty 0).
                safeRadius=%d
                # REGIONAL mode: blocks after safeRadius until learned pressure reaches full strength.
                regionalSpawnRampBlocks=%d
                # Distance from spawn at which difficulty caps (DISTANCE mode).
                maxDifficultyRadius=%d
                # Optional mercy window after death. 0 disables relief and keeps regional pressure honest.
                deathReliefSeconds=%d
                # How much a death eases difficulty when enabled: 0.0 none .. 1.0 full calm.
                deathReliefStrength=%s
                # Fraction of the gap a capability score decays toward gear each second.
                scoreDecayRate=%s
                # REGIONAL mode: nearby chunks learn the running average player score.
                regionalSampleRadiusChunks=%d
                regionalBlendRate=%s
                # REGIONAL mode: additional blend speed gained for repeated samples in the same area.
                regionalAccelerationPerSample=%s
                regionalAccelerationMax=%s
                regionalDecayRate=%s
                # REGIONAL mode: timers for sustained-pressure increase and post-pressure decay grace.
                regionalIncreaseDelaySeconds=%d
                regionalDecayDelaySeconds=%d
                # REGIONAL mode: extra difficulty added per additional nearby player.
                regionalPlayerBonus=%s
                # Overworld depth difficulty: optional additive pressure below overworldDepthStartY,
                # reaching overworldDepthBonusMax at overworldDepthMaxY.
                overworldDepthDifficultyEnabled=%s
                overworldDepthStartY=%d
                overworldDepthMaxY=%d
                overworldDepthBonusMax=%s
                # Extra difficulty added in the Nether / End, regardless of mode.
                netherDifficultyBonus=%s
                endDifficultyBonus=%s
                # ── Squads & spawning ─────────────────────────────────────────────
                # If true, mobs may spawn as role-based squads at higher difficulty.
                squadsEnabled=%s
                # Base squad-size cap for a solo player.
                maxSquadSize=%d
                # Extra squad slots allowed per additional player sharing the region.
                squadPlayerBonus=%d
                # Chance eligible natural mobs become squad actors, scaled by local difficulty.
                naturalSquadChanceMin=%s
                naturalSquadChanceMax=%s
                # Performance cap: most tactical-AI mobs ticked per player at once.
                maxSmartMobsPerPlayer=%d
                # If true, spawned hostile mobs get difficulty-scaled stat buffs.
                statBuffsEnabled=%s
                # Maximum stat bonuses at Warband difficulty 1.0. Health/damage/speed are base multipliers.
                statHealthBonusMax=%s
                statDamageBonusMax=%s
                statSpeedBonusMax=%s
                # Maximum flat knockback resistance added at Warband difficulty 1.0.
                statKnockbackResistanceMax=%s
                # If true, smarter mobs may place short-lived tactical blocks like cobwebs or ice.
                temporaryTacticBlocks=%s
                # If true, Warband enhancement intensity cycles through build-up, peak, and relax windows.
                encounterDirectorEnabled=%s
                # Extra enhancement chance per additional player sharing the region.
                encounterPlayerBonus=%s
                # Encounter director phase lengths.
                directorBuildUpSeconds=%d
                directorPeakSeconds=%d
                directorRelaxSeconds=%d
                # Chance that an otherwise eligible mob receives Warband enhancements during build-up / relax.
                directorBuildUpEnhancementChance=%s
                directorRelaxEnhancementChance=%s
                # If true, squadded mobs get visible role silhouettes/equipment.
                roleVisualsEnabled=%s
                # If true, role assignment plays a restrained vanilla mob cue.
                roleCuesEnabled=%s
                # If true, trapped/crowded farm mobs suppress drops and try to escape.
                antiFarmEnabled=%s
                antiFarmCrowdThreshold=%d
                antiFarmScanSeconds=%d
                # Anti-farm escalation thresholds by nearby same-type crowd.
                antiFarmTier1Crowd=%d
                antiFarmTier2Crowd=%d
                antiFarmTier3Crowd=%d
                # If true, legitimate Warband-enhanced mobs grant modest extra XP.
                # Farm-suppressed mobs still grant 0 XP.
                experienceScalingEnabled=%s
                experienceDifficultyBonusMax=%s
                experienceLeaderBonus=%s
                # If true, the Wither gains Warband phase abilities (nova, dash, skull barrage, last stand).
                witherAbilitiesEnabled=%s
                # If true, the Ender Dragon gains Warband phase abilities (blink, charge, ender gale, breath line).
                # Disable this for compatibility with mods that replace the dragon fight, such as True Ending.
                enderDragonAbilitiesEnabled=%s
                # If true, guardians, shulkers, ghasts, cave spiders, ravagers and wardens get Warband tactics.
                extendedMobTacticsEnabled=%s
                # Comma-separated tactic names to disable, e.g. SPIDER_WEB,CEILING_CRAWL,RANGED_REPOSITION,ENDERMAN_DISRUPT.
                disabledTactics=%s
                # Opt modded mobs into Warband behaviour pools, so they gain the matching
                # tactics/roles and squad up with their vanilla counterparts. Pools are
                # separated by ';' and entity ids by ','; '>' splits a pool from its ids
                # (entity ids already contain ':'). Example:
                #   customMobPools=ZOMBIE_FAMILY>examplemod:armored_zombie;SPIDER>examplemod:giant_spider
                # Pools: SPIDER, CAVE_SPIDER, ABSTRACT_SKELETON, STRAY, BOGGED, RANGED_ATTACK,
                # ZOMBIE_FAMILY, CREEPER, ENDERMAN, ABSTRACT_PIGLIN, BLAZE, WITCH, SLIME_FAMILY,
                # HOGLIN_FAMILY, ILLAGER_LIKE, PHANTOM, GUARDIAN, SHULKER, GHAST, RAVAGER, WARDEN.
                # Find an entity id with: /data get entity @e[limit=1,sort=nearest] id
                customMobPools=%s
                # If true, logs each Warband tactic execution for debugging.
                debugTacticLogs=%s
                # If true, sun-sensitive undead path to shade at dawn instead of waiting to burn.
                seekShelterEnabled=%s
                # If true, endermen shot by a player teleport next to the shooter and aggro.
                endermanProvokeEnabled=%s
                # Minimum difficulty for spawner/trial-spawner/summoned mobs (0.0-1.0). Encounter setpieces shouldn't be trivial.
                spawnerDifficultyFloor=%s
                # If true, smart skeletons mount nearby spiders and baby zombies mount nearby chickens when out of combat.
                naturalJockeysEnabled=%s
                # Difficulty required (0.0-1.0) before a mob is smart enough to form a jockey.
                naturalJockeyMinDifficulty=%s
                # If true, active raiders pillage every animal in range (including leashed/named/tamed/babies) when no villager or iron golem is left to fight.
                raidPredationEnabled=%s
                # If true, the final wave of a high-ominous raid summons a faction bounty hunter to finish the player personally.
                raidFinaleBountyEnabled=%s
                # If true, medium-or-higher ominous raids can attract a rival faction patrol that attacks the raiders.
                raidRivalInterceptEnabled=%s

                # ── Multiplayer ─────────────────────────────────────────────────
                # If true, enables party-aware budgets, threat targets, shared intel, and death mercy.
                multiplayerFeaturesEnabled=%s
                # Extra smart-mob budget per additional nearby active player.
                multiplayerSmartMobsPerExtraPlayer=%d
                # Extra encounter enhancement chance multiplier per additional nearby player.
                multiplayerEncounterBonusPerExtraPlayer=%s
                # Temporary local pressure reduction after a nearby player death.
                multiplayerDeathMercySeconds=%d
                multiplayerDeathMercyRadius=%d
                multiplayerDeathMercyStrength=%s
                # Threat-score penalty per squadmate already targeting the same player.
                multiplayerDogpilePenalty=%s

                # ── Illagers ─────────────────────────────────────────────────────
                # If true, illagers receive regional faction identity in names and grudge records.
                illagerFactionsEnabled=%s
                # If true, illager squads use role/faction doctrine movement.
                illagerDoctrineEnabled=%s
                # If true, non-raid illager witnesses can remember players and return later.
                illagerGrudgesEnabled=%s
                # If true, rival factions can shadow or intercept revenge attacks.
                illagerRivalriesEnabled=%s
                # If true, active raid illagers use settlement assault doctrine.
                illagerRaidDoctrineEnabled=%s
                # If true, factioned illagers carry colored faction banners.
                illagerFactionBannersEnabled=%s
                # If true, Warband illagers receive role-appropriate equipment.
                illagerRoleGearEnabled=%s
                # If true, high faction heat can trigger elite bounty hunters.
                illagerBountyHuntersEnabled=%s
                # If true, mansions and outposts become elevated faction strongholds.
                illagerStrongholdsEnabled=%s
                # Minimum difficulty for a mansion / outpost garrison.
                mansionGarrisonFloor=%s
                outpostGarrisonFloor=%s
                # Mansion Warmarshal boss bonuses (base multipliers, on top of stat buffs).
                warmarshalHealthBonus=%s
                warmarshalDamageBonus=%s

                # ── Items ────────────────────────────────────────────────────────
                # If true, goat horns also rally nearby golems and disrupt Warband illagers.
                goatHornCommandEnabled=%s

                """.formatted(
                    configProfile,
                    difficultyMode,
                    safeRadius,
                    regionalSpawnRampBlocks,
                    maxDifficultyRadius,
                    deathReliefSeconds,
                    deathReliefStrength,
                    scoreDecayRate,
                    regionalSampleRadiusChunks,
                    regionalBlendRate,
                    regionalAccelerationPerSample,
                    regionalAccelerationMax,
                    regionalDecayRate,
                    regionalIncreaseDelaySeconds,
                    regionalDecayDelaySeconds,
                    regionalPlayerBonus,
                    overworldDepthDifficultyEnabled,
                    overworldDepthStartY,
                    overworldDepthMaxY,
                    overworldDepthBonusMax,
                    netherDifficultyBonus,
                    endDifficultyBonus,
                    squadsEnabled,
                    maxSquadSize,
                    squadPlayerBonus,
                    naturalSquadChanceMin,
                    naturalSquadChanceMax,
                    maxSmartMobsPerPlayer,
                    statBuffsEnabled,
                    statHealthBonusMax,
                    statDamageBonusMax,
                    statSpeedBonusMax,
                    statKnockbackResistanceMax,
                    temporaryTacticBlocks,
                    encounterDirectorEnabled,
                    encounterPlayerBonus,
                    directorBuildUpSeconds,
                    directorPeakSeconds,
                    directorRelaxSeconds,
                    directorBuildUpEnhancementChance,
                    directorRelaxEnhancementChance,
                    roleVisualsEnabled,
                    roleCuesEnabled,
                    antiFarmEnabled,
                    antiFarmCrowdThreshold,
                    antiFarmScanSeconds,
                    antiFarmTier1Crowd,
                    antiFarmTier2Crowd,
                    antiFarmTier3Crowd,
                    experienceScalingEnabled,
                    experienceDifficultyBonusMax,
                    experienceLeaderBonus,
                    witherAbilitiesEnabled,
                    enderDragonAbilitiesEnabled,
                    extendedMobTacticsEnabled,
                    disabledTactics,
                    customMobPools,
                    debugTacticLogs,
                    seekShelterEnabled,
                    endermanProvokeEnabled,
                    spawnerDifficultyFloor,
                    naturalJockeysEnabled,
                    naturalJockeyMinDifficulty,
                    raidPredationEnabled,
                    raidFinaleBountyEnabled,
                    raidRivalInterceptEnabled,
                    multiplayerFeaturesEnabled,
                    multiplayerSmartMobsPerExtraPlayer,
                    multiplayerEncounterBonusPerExtraPlayer,
                    multiplayerDeathMercySeconds,
                    multiplayerDeathMercyRadius,
                    multiplayerDeathMercyStrength,
                    multiplayerDogpilePenalty,
                    illagerFactionsEnabled,
                    illagerDoctrineEnabled,
                    illagerGrudgesEnabled,
                    illagerRivalriesEnabled,
                    illagerRaidDoctrineEnabled,
                    illagerFactionBannersEnabled,
                    illagerRoleGearEnabled,
                    illagerBountyHuntersEnabled,
                    illagerStrongholdsEnabled,
                    mansionGarrisonFloor,
                    outpostGarrisonFloor,
                    warmarshalHealthBonus,
                    warmarshalDamageBonus,
                    goatHornCommandEnabled
                );
    }

    public static boolean tacticEnabled(Tactic tactic) {
        return !disabledTacticSet.contains(tactic);
    }

    private static boolean parseBoolean(Properties props, String key, boolean def, Logger logger) {
        String raw = props.getProperty(key);
        if (raw == null) return def;
        String s = raw.trim().toLowerCase();
        if (s.equals("true")) return true;
        if (s.equals("false")) return false;
        logger.warn("[Warband] '{}' is not a valid boolean ('{}'), using default {}", key, raw, def);
        return def;
    }

    private static ConfigProfile parseProfile(Properties props, ConfigProfile def, Logger logger) {
        String raw = props.getProperty("configProfile");
        ConfigProfile parsed = ConfigProfile.fromString(raw, null);
        if (raw != null && parsed == null) {
            logger.warn("[Warband] 'configProfile' is not a valid profile ('{}'), using default {}", raw, def);
            return def;
        }
        return parsed != null ? parsed : def;
    }

    private static EnumSet<Tactic> parseTacticSet(String raw, Logger logger) {
        EnumSet<Tactic> set = EnumSet.noneOf(Tactic.class);
        if (raw == null || raw.isBlank()) return set;
        for (String token : raw.split(",")) {
            String name = token.trim();
            if (name.isEmpty()) continue;
            try {
                set.add(Tactic.valueOf(name.toUpperCase()));
            } catch (IllegalArgumentException e) {
                logger.warn("[Warband] '{}' is not a valid tactic in disabledTactics", name);
            }
        }
        return set;
    }

    private static void applyProfile() {
        switch (configProfile) {
            case CUSTOM -> {
            }
            case VANILLA_PLUS -> {
                naturalSquadChanceMax = 0.45;
                statHealthBonusMax = 0.25;
                statDamageBonusMax = 0.15;
                maxSmartMobsPerPlayer = 20;
                temporaryTacticBlocks = false;
                witherAbilitiesEnabled = false;
                enderDragonAbilitiesEnabled = false;
                multiplayerSmartMobsPerExtraPlayer = 6;
                multiplayerEncounterBonusPerExtraPlayer = 0.08;
                multiplayerDeathMercySeconds = 60;
                multiplayerDogpilePenalty = 24.0;
                illagerGrudgesEnabled = false;
                illagerRivalriesEnabled = false;
                illagerFactionBannersEnabled = false;
                illagerBountyHuntersEnabled = false;
                warmarshalHealthBonus = 0.75;
                warmarshalDamageBonus = 0.25;
                goatHornCommandEnabled = false;
                antiFarmTier1Crowd = 10;
                antiFarmTier2Crowd = 18;
                antiFarmTier3Crowd = 28;
            }
            case FANTASY -> {
                naturalSquadChanceMax = 0.60;
                statHealthBonusMax = 0.35;
                statDamageBonusMax = 0.20;
                maxSmartMobsPerPlayer = 24;
                multiplayerSmartMobsPerExtraPlayer = 8;
                multiplayerEncounterBonusPerExtraPlayer = 0.12;
                multiplayerDeathMercySeconds = 45;
                multiplayerDogpilePenalty = 18.0;
                antiFarmTier1Crowd = 8;
                antiFarmTier2Crowd = 14;
                antiFarmTier3Crowd = 22;
            }
        }
    }

    private static DifficultyMode parseMode(Properties props, DifficultyMode def, Logger logger) {
        String raw = props.getProperty("difficultyMode");
        if (raw == null) return def;
        DifficultyMode parsed = DifficultyMode.fromString(raw, null);
        if (parsed == null) {
            logger.warn("[Warband] 'difficultyMode' is not a valid mode ('{}'), using default {}", raw, def);
            return def;
        }
        return parsed;
    }

    private static double parseDouble(Properties props, String key, double def, double min, double max, Logger logger) {
        String raw = props.getProperty(key);
        if (raw == null) return def;
        try {
            double val = Double.parseDouble(raw.trim());
            if (val < min || val > max) {
                logger.warn("[Warband] '{}' value {} out of range [{}, {}], clamping", key, val, min, max);
                return Math.max(min, Math.min(max, val));
            }
            return val;
        } catch (NumberFormatException e) {
            logger.warn("[Warband] '{}' is not a valid number ('{}'), using default {}", key, raw, def);
            return def;
        }
    }

    private static int parseInt(Properties props, String key, int def, int min, int max, Logger logger) {
        String raw = props.getProperty(key);
        if (raw == null) return def;
        try {
            int val = Integer.parseInt(raw.trim());
            if (val < min || val > max) {
                logger.warn("[Warband] '{}' value {} out of range [{}, {}], clamping", key, val, min, max);
                return Math.max(min, Math.min(max, val));
            }
            return val;
        } catch (NumberFormatException e) {
            logger.warn("[Warband] '{}' is not a valid integer ('{}'), using default {}", key, raw, def);
            return def;
        }
    }
}
