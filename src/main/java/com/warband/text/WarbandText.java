package com.warband.text;

import com.warband.illager.IllagerFaction;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * Shared shapes for Warband's player-facing text.
 *
 * <p>Everything the player reads is a translatable component with the English text as its
 * fallback. That combination matters more here than in most mods: Warband is server-side
 * with no client half, so a player is normally on a vanilla client that has never heard of
 * this mod. A bare {@code Component.translatable} would show them {@code warband.rank.captain}.
 * {@code translatableWithFallback} sends both, so a vanilla client renders the English and a
 * client that does have a Warband language file renders the translation, with no branching
 * on either side.
 *
 * <p>The patterns live here rather than being inlined because the <i>joins</i> need
 * translating too. "Bounty Hunter of the Pale Axe" is three separate pieces of text plus a
 * piece of English grammar, and a language that orders those differently can only express
 * that if the join is a key of its own.
 *
 * <p>Not covered: {@code /warband} diagnostic output, which stays literal on purpose. See
 * {@code WarbandCommand}.
 */
public final class WarbandText {

    private WarbandText() {
    }

    /** A spoken line, greyed and in quotes — the mod's convention for a mob talking. */
    public static Component quoted(String key, String fallback) {
        return Component.translatableWithFallback("warband.quoted", "\"%s\"",
                Component.translatableWithFallback(key, fallback)).withStyle(ChatFormatting.GRAY);
    }

    /** A rank or role bound to a faction: "Bounty Hunter of the Pale Axe". */
    public static Component titleOfFaction(String key, String fallback, IllagerFaction faction) {
        return Component.translatableWithFallback("warband.name.title_of_faction", "%1$s of the %2$s",
                Component.translatableWithFallback(key, fallback), faction.displayName());
    }
}
