package com.warband.illager;

import net.minecraft.network.chat.Component;

import java.util.Locale;

/** Faction-wide posture toward a player, derived from accumulated heat. */
public enum VengeanceState {
    QUIET("quiet"),
    NOTICED("noticed"),
    WARY("watching"),
    HOSTILE("hostile"),
    WAR("at war"),
    CRUSADE("crusade");

    private final String fallback;

    VengeanceState(String fallback) {
        this.fallback = fallback;
    }

    /**
     * The posture as shown to a player.
     *
     * <p>Translatable with the English text as the fallback, so a vanilla client with no
     * Warband resources still reads it while a translated one does not have to.
     */
    public Component displayName() {
        return Component.translatableWithFallback(
                "warband.vengeance." + name().toLowerCase(Locale.ROOT), fallback);
    }

    public static VengeanceState fromHeat(int heat) {
        if (heat >= 150) return CRUSADE;
        if (heat >= 80) return WAR;
        if (heat >= 40) return HOSTILE;
        if (heat >= 20) return WARY;
        if (heat >= 1) return NOTICED;
        return QUIET;
    }
}
