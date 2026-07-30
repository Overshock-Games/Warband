package com.warband.compat;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Soft detection for Zombie Break &amp; Build.
 *
 * <p>That mod's entire purpose is mob block manipulation — breaking, building, bridging,
 * height adjustment, block damage accumulation, build protection — and it does the job
 * more thoroughly than Warband's single siege behaviour can. So when it is present,
 * Warband hands the domain over rather than digging alongside it, the same way it defers
 * ceiling crawling to Stormie's Spiders and the dragon fight to True Ending.
 *
 * <p>Two mods independently deciding to break the same wall is worse than either alone:
 * duplicated holes, competing movement goals, doubled block-break audio, and Warband
 * would happily mine straight through blocks the other mod's build protection is trying
 * to keep. Clean ownership beats partial overlap.
 *
 * <p>Everything else Warband does is untouched — squads, roles, tactics, difficulty,
 * factions — and creeper breaching stays ours, because that is an explosion rather than
 * mining and the other mod does not do it.
 *
 * <p>Override with {@code siegeMiningDeferToOtherMods=false} to run both, which is
 * supported but not recommended.
 *
 * <p>No compile-time dependency — checked once at static-init time.
 */
public final class ZombieBreakAndBuildCompat {

    private static final String MOD_ID = "zbb";
    private static final boolean LOADED = FabricLoader.getInstance().isModLoaded(MOD_ID);

    private ZombieBreakAndBuildCompat() {
    }

    public static boolean isLoaded() {
        return LOADED;
    }
}
