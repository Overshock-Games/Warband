package com.warband;

import com.warband.config.WarbandConfig;
import net.minecraft.world.entity.Entity;

/**
 * Structured debug output for behaviour verification, gated on
 * {@code debugTacticLogs}.
 *
 * <p>One fixed shape so a log can be machine-checked rather than eyeballed:
 *
 * <pre>
 * [Warband] EVENT=SIEGE_MINE mob=zombie id=482 diff=0.90 pos=12 64 -30 block=minecraft:dirt
 * </pre>
 *
 * <p>Every field is {@code key=value}, so {@code grep EVENT=} yields the full trace
 * of what Warband decided during a play session and each line is parseable on its
 * own. Costs nothing when the flag is off — the guard is checked before any string
 * is built.
 */
public final class WarbandDebug {

    private WarbandDebug() {
    }

    public static boolean enabled() {
        return WarbandConfig.debugTacticLogs;
    }

    /**
     * @param event  stable uppercase token, greppable as {@code EVENT=<token>}
     * @param source the acting entity, or null for world-level events
     * @param detail extra {@code key=value} pairs, already formatted
     */
    public static void event(String event, Entity source, String detail) {
        if (!WarbandConfig.debugTacticLogs) return;

        StringBuilder line = new StringBuilder(96);
        line.append("EVENT=").append(event);
        if (source != null) {
            line.append(" mob=").append(source.getType().toShortString())
                    .append(" id=").append(source.getId())
                    .append(" pos=").append(source.blockPosition().getX())
                    .append(' ').append(source.blockPosition().getY())
                    .append(' ').append(source.blockPosition().getZ());
        }
        if (detail != null && !detail.isEmpty()) {
            line.append(' ').append(detail);
        }
        WarbandMod.LOGGER.info("[Warband] {}", line);
    }

    public static void event(String event, Entity source) {
        event(event, source, null);
    }

    /** World-level event with no acting entity (block restores, config actions). */
    public static void event(String event, String detail) {
        event(event, null, detail);
    }
}
