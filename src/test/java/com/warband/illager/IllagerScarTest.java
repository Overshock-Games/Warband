package com.warband.illager;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Persistence and semantics for nemesis scars.
 *
 * <p>The codec cases matter most: grudges are stored on player data, so a schema
 * change that fails to load would silently wipe every named survivor a player has
 * earned. The pre-scar JSON case is the actual regression guard.
 */
class IllagerScarTest {

    private static final String PRE_SCAR_JSON = """
            {
              "survivorName": "Yorn of the Ash Banner",
              "faction": "ash_banner",
              "difficulty": 0.8,
              "anger": 40,
              "readyAt": 1234,
              "attempts": 1,
              "originPos": 99,
              "originDimension": "minecraft:overworld"
            }
            """;

    @Test
    void grudgesSavedBeforeScarsExistedStillLoad() {
        IllagerGrudge grudge = IllagerGrudge.CODEC
                .parse(JsonOps.INSTANCE, JsonParser.parseString(PRE_SCAR_JSON))
                .getOrThrow();

        assertEquals("Yorn of the Ash Banner", grudge.survivorName());
        assertEquals(40, grudge.anger());
        // The whole point: absent field must default, not fail.
        assertEquals(IllagerScar.NONE, grudge.scar());
        assertFalse(grudge.scar().marked());
    }

    @Test
    void scarSurvivesACodecRoundTrip() {
        IllagerGrudge original = new IllagerGrudge("Grix", IllagerFaction.BLACK_HORN, 0.7f,
                55, 20L, 2, 42L, "minecraft:overworld", IllagerScar.FIRE);

        var encoded = IllagerGrudge.CODEC.encodeStart(JsonOps.INSTANCE, original).getOrThrow();
        IllagerGrudge decoded = IllagerGrudge.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();

        assertEquals(IllagerScar.FIRE, decoded.scar());
        assertEquals(original, decoded);
    }

    @Test
    void angerAndRetriesPreserveTheScar() {
        IllagerGrudge grudge = new IllagerGrudge("Grix", IllagerFaction.BLACK_HORN, 0.7f,
                10, 0L, 0, 0L, "", IllagerScar.BLAST);

        assertEquals(IllagerScar.BLAST, grudge.addAnger(20, 5L).scar());
        assertEquals(IllagerScar.BLAST, grudge.attempted(9L).scar());
    }

    @Test
    void aNewLessonReplacesTheOldOneButNothingUnlearnsIt() {
        IllagerGrudge scarred = new IllagerGrudge("Grix", IllagerFaction.BLACK_HORN, 0.7f,
                10, 0L, 0, 0L, "", IllagerScar.FIRE);

        // A different lesson overwrites.
        assertEquals(IllagerScar.ARROWS, scarred.withScar(IllagerScar.ARROWS).scar());
        // NONE must never erase an earned scar — otherwise a later scarless fight
        // would quietly disarm an established nemesis.
        assertEquals(IllagerScar.FIRE, scarred.withScar(IllagerScar.NONE).scar());
        // Same lesson is a no-op, and returns the identical record.
        assertTrue(scarred == scarred.withScar(IllagerScar.FIRE));
    }

    @Test
    void unknownScarNamesDegradeToNone() {
        assertEquals(IllagerScar.NONE, IllagerScar.fromString("not_a_scar"));
        assertEquals(IllagerScar.NONE, IllagerScar.fromString(""));
        assertEquals(IllagerScar.NONE, IllagerScar.fromString(null));
        // Case-insensitive, so hand-edited data still works.
        assertEquals(IllagerScar.FIRE, IllagerScar.fromString("fire"));
    }

    @Test
    void everyMarkedScarHasSomethingToSay() {
        for (IllagerScar scar : IllagerScar.values()) {
            if (!scar.marked()) continue;
            assertFalse(scar.label().isBlank(), scar + " needs a label for intel listings");
            assertFalse(scar.boast().isBlank(), scar + " needs a boast for the arrival message");
        }
    }
}
