package com.warband.illager;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Persistent player-scoped memory for illager revenge patrols.
 *
 * <p>{@code originPos}/{@code originDimension} record where the original fight
 * happened, so revenge can muster from that place rather than teleporting near
 * the player. They are plain coordinates, no structure lookup, so this works
 * with any pillager structure, vanilla or modded.
 *
 * <p>{@code scar} is what the survivor learned from the fight — see
 * {@link IllagerScar}. Optional in the codec, so grudges saved before scars
 * existed load as {@link IllagerScar#NONE} rather than breaking the save.
 *
 * <p>{@code personalName} is the survivor's bare name — "Arvek" — and nothing else. It
 * used to be the mob's whole rendered display name ("Sergeant Arvek of the Pale Axe"),
 * which meant the save file held a sentence of English that had to be taken apart again
 * with {@code indexOf(" of the ")} every time the survivor came back. That was already
 * fragile if a server renamed a mob, and it cannot survive display text becoming
 * translatable at all. The faction is a field in its own right, so the display name can
 * always be rebuilt — it never needed storing.
 */
public record IllagerGrudge(String personalName, IllagerFaction faction, float difficulty,
                            int anger, long readyAt, int attempts,
                            long originPos, String originDimension, IllagerScar scar) {

    public static final Codec<IllagerGrudge> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("personalName", "").forGetter(IllagerGrudge::personalName),
            // Legacy display name, read-only. The getter always returns the field default so
            // optionalFieldOf drops it, and nothing new is ever written under this key.
            Codec.STRING.optionalFieldOf("survivorName", "").forGetter(grudge -> ""),
            IllagerFaction.CODEC.optionalFieldOf("faction", IllagerFaction.BLACK_HORN).forGetter(IllagerGrudge::faction),
            Codec.FLOAT.fieldOf("difficulty").forGetter(IllagerGrudge::difficulty),
            Codec.INT.fieldOf("anger").forGetter(IllagerGrudge::anger),
            Codec.LONG.fieldOf("readyAt").forGetter(IllagerGrudge::readyAt),
            Codec.INT.optionalFieldOf("attempts", 0).forGetter(IllagerGrudge::attempts),
            Codec.LONG.optionalFieldOf("originPos", 0L).forGetter(IllagerGrudge::originPos),
            Codec.STRING.optionalFieldOf("originDimension", "").forGetter(IllagerGrudge::originDimension),
            IllagerScar.CODEC.optionalFieldOf("scar", IllagerScar.NONE).forGetter(IllagerGrudge::scar)
    ).apply(instance, IllagerGrudge::fromSaved));

    /**
     * Codec entry point that accepts either shape of save.
     *
     * <p>A grudge written before the split only has the old display-name field, so its
     * personal name is recovered from it once here, at load, instead of on every display.
     */
    private static IllagerGrudge fromSaved(String personalName, String legacyDisplayName,
                                           IllagerFaction faction, float difficulty, int anger,
                                           long readyAt, int attempts, long originPos,
                                           String originDimension, IllagerScar scar) {
        String name = personalName.isEmpty() ? personalNameFrom(legacyDisplayName) : personalName;
        return new IllagerGrudge(name, faction, difficulty, anger, readyAt, attempts,
                originPos, originDimension, scar);
    }

    /**
     * Pulls the bare name out of a stored English display name, for old saves only.
     *
     * <p>"Sergeant Arvek of the Pale Axe" becomes "Arvek". This is the parsing that should
     * never have been load-bearing, kept only so existing worlds do not lose their nemeses
     * on upgrade. Nothing saved from now on goes through it.
     */
    private static String personalNameFrom(String displayName) {
        String clean = displayName;
        int factionIndex = clean.indexOf(" of the ");
        if (factionIndex >= 0) {
            clean = clean.substring(0, factionIndex);
        }
        String[] parts = clean.trim().split("\\s+");
        String last = parts.length == 0 ? "" : parts[parts.length - 1];
        return last.isEmpty() ? "Survivor" : last;
    }

    public IllagerGrudge addAnger(int amount, long newReadyAt) {
        return new IllagerGrudge(personalName, faction, difficulty, Math.min(100, anger + amount),
                newReadyAt, attempts, originPos, originDimension, scar);
    }

    public IllagerGrudge attempted(long retryAt) {
        return new IllagerGrudge(personalName, faction, difficulty, Math.max(0, anger - 20),
                retryAt, attempts + 1, originPos, originDimension, scar);
    }

    /**
     * Records a fresh lesson. A survivor keeps the first scar it earned unless the new
     * fight taught it something different, so a nemesis reads as accumulating history
     * rather than resetting every encounter.
     */
    public IllagerGrudge withScar(IllagerScar newScar) {
        if (newScar == null || newScar == IllagerScar.NONE || newScar == scar) return this;
        return new IllagerGrudge(personalName, faction, difficulty, anger,
                readyAt, attempts, originPos, originDimension, newScar);
    }

    /** True if a fight location was recorded for this grudge. */
    public boolean hasOrigin() {
        return !originDimension.isEmpty();
    }
}
