package com.warband.compat;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Presence check for Illager Invasion.
 *
 * <p>Everything this class used to do — deciding which mobs count as illagers, which are
 * support casters, which can hold a seat of power — moved to entity-type tags in
 * {@link IllagerKinds}, because none of it was ever really about <i>this</i> mod. What is
 * left is the one genuinely mod-specific question: is Illager Invasion installed, and so
 * should Warband stand aside and let its own stronger illagers fill a role Warband would
 * otherwise fill itself?
 */
public final class IllagerInvasionCompat {

    private static final String MOD_ID = "illagerinvasion";

    private IllagerInvasionCompat() {
    }

    public static boolean isLoaded() {
        return FabricLoader.getInstance().isModLoaded(MOD_ID);
    }
}
