package com.warband.entity;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Parser-level coverage for {@code customMobPools}. The lookup side needs a live
 * entity registry, so only parsing is exercised here — that is where a user's
 * hand-written config actually goes wrong.
 */
class MobPoolsTest {

    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger("WarbandTest");

    @Test
    void parsesMultiplePoolsAndIds() {
        MobPools.load("ZOMBIE_FAMILY>examplemod:armored_zombie,examplemod:fast_zombie"
                + ";SPIDER>examplemod:giant_spider", LOGGER);

        assertEquals(EnumSet.of(Tactic.Subject.ZOMBIE_FAMILY), pools("examplemod:armored_zombie"));
        assertEquals(EnumSet.of(Tactic.Subject.ZOMBIE_FAMILY), pools("examplemod:fast_zombie"));
        assertEquals(EnumSet.of(Tactic.Subject.SPIDER), pools("examplemod:giant_spider"));
    }

    @Test
    void oneMobCanJoinSeveralPools() {
        MobPools.load("ZOMBIE_FAMILY>examplemod:brute;ABSTRACT_SKELETON>examplemod:brute", LOGGER);

        assertEquals(EnumSet.of(Tactic.Subject.ZOMBIE_FAMILY, Tactic.Subject.ABSTRACT_SKELETON),
                pools("examplemod:brute"));
    }

    @Test
    void poolNamesAreCaseInsensitiveAndWhitespaceTolerant() {
        MobPools.load("  zombie_family >  examplemod:zed , examplemod:zed_two  ", LOGGER);

        assertEquals(EnumSet.of(Tactic.Subject.ZOMBIE_FAMILY), pools("examplemod:zed"));
        assertEquals(EnumSet.of(Tactic.Subject.ZOMBIE_FAMILY), pools("examplemod:zed_two"));
    }

    @Test
    void badEntriesAreSkippedWithoutLosingGoodOnes() {
        // Missing '>', unknown pool name, and an unparseable id.
        MobPools.load("NOT_A_POOL>examplemod:a;examplemod:b;ZOMBIE_FAMILY>examplemod:good,IN VALID", LOGGER);

        assertEquals(EnumSet.of(Tactic.Subject.ZOMBIE_FAMILY), pools("examplemod:good"));
        assertTrue(pools("examplemod:a").isEmpty());
        assertTrue(pools("examplemod:b").isEmpty());
    }

    @Test
    void blankConfigClearsPools() {
        MobPools.load("ZOMBIE_FAMILY>examplemod:zed", LOGGER);
        MobPools.load("", LOGGER);

        assertTrue(pools("examplemod:zed").isEmpty());
    }

    private static EnumSet<Tactic.Subject> pools(String id) {
        return MobPools.subjectsForId(net.minecraft.resources.Identifier.parse(id));
    }
}
