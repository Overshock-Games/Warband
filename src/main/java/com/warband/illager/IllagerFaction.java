package com.warband.illager;

import com.mojang.serialization.Codec;
import net.minecraft.network.chat.Component;

import java.util.List;

public enum IllagerFaction {
    BLACK_HORN("black_horn", "Black Horn", FactionDoctrine.HUNT),
    RED_LEDGER("red_ledger", "Red Ledger", FactionDoctrine.COMMAND),
    PALE_AXE("pale_axe", "Pale Axe", FactionDoctrine.SIEGE),
    ASH_BANNER("ash_banner", "Ash Banner", FactionDoctrine.BURN),
    IRON_CHOIR("iron_choir", "Iron Choir", FactionDoctrine.AMBUSH);

    public static final Codec<IllagerFaction> CODEC = Codec.STRING.xmap(IllagerFaction::byId, IllagerFaction::id);
    private static final List<IllagerFaction> VALUES = List.of(values());

    private final String id;
    private final String fallbackName;
    private final FactionDoctrine doctrine;

    IllagerFaction(String id, String fallbackName, FactionDoctrine doctrine) {
        this.id = id;
        this.fallbackName = fallbackName;
        this.doctrine = doctrine;
    }

    public String id() {
        return id;
    }

    /**
     * The faction's name as shown to a player.
     *
     * <p>Faction names are invented proper nouns, so most translators will leave them
     * alone — but they appear inside sentences ("A crusade of the Pale Axe has come for
     * you"), and a language that inflects those needs to be able to reach them. The
     * English name is the fallback, so nothing breaks on a client without Warband
     * resources.
     */
    public Component displayName() {
        return Component.translatableWithFallback("warband.faction." + id, fallbackName);
    }

    public FactionDoctrine doctrine() {
        return doctrine;
    }

    /**
     * Mutual rival pairings: Black Horn ↔ Pale Axe, Red Ledger ↔ Ash Banner.
     * Iron Choir is a zealot order with no peer, so they rival themselves and
     * never participate in rival-intercept patrols (callers must guard for self).
     */
    public IllagerFaction rival() {
        return switch (this) {
            case BLACK_HORN -> PALE_AXE;
            case PALE_AXE -> BLACK_HORN;
            case RED_LEDGER -> ASH_BANNER;
            case ASH_BANNER -> RED_LEDGER;
            case IRON_CHOIR -> IRON_CHOIR;
        };
    }

    public static IllagerFaction pick(int seed) {
        return VALUES.get(Math.floorMod(seed, VALUES.size()));
    }

    private static IllagerFaction byId(String id) {
        for (IllagerFaction faction : values()) {
            if (faction.id.equals(id)) return faction;
        }
        return BLACK_HORN;
    }
}
