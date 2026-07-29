# Porting Warband to Minecraft 26.2

Status: **not started.** 1.4.0 targets 26.1.2 and is the last 26.1.2 release.

26.2 is **not source-compatible** with 26.1.2. A single jar cannot serve both:
`Slime`/`MagmaCube` changed package, so the break is at import level, not just
method level. Serving both would require source preprocessing (Stonecutter) or
reflection — not worth it for a two-version window.

Verified by cross-compiling:

```
gradlew compileJava -Pminecraft_version=26.2 -Pfabric_api_version=0.156.0+26.2
```

against Loom 1.17.17 / Gradle 9.6.1 / loader 0.19.3. Result: **71 errors**. Every
one is mechanical. Taxonomy below, with the confirmed 26.2 replacement.

## 1. `BlockPos.getCenter()` removed — 32 sites

`getCenter()` is gone from both `BlockPos` and `Vec3i`. Use the static factory,
which exists in both 26.1.2 and 26.2:

```java
pos.getCenter()          // 26.1.2 only
Vec3.atCenterOf(pos)     // both
```

`Vec3.atBottomCenterOf(pos)` and `Vec3.atLowerCornerOf(pos)` are also present if
a call site wants feet-level or corner semantics.

**This one is safe to do now, ahead of the port** — it compiles on 26.1.2 too and
shrinks the diff by nearly half. Affected files include `PressureUnreachableGoal`,
`IllagerTerritory`, `IllagerGrudgeSystem`, `EncounterDirector`, `SquadCoordinator`,
`MultiplayerDirector`, `BossDirector`.

## 2. `Slime` / `MagmaCube` moved package — 40 references

```java
net.minecraft.world.entity.monster.Slime            // 26.1.2
net.minecraft.world.entity.monster.cubemob.Slime    // 26.2
```

Same for `MagmaCube`. Import-only change; no member changes observed. Touches
`SquadCoordinator`, `SlimeSurgeGoal`, `Tactic`, `SpawnDirector`.

Note the precedent: 26.1 already did this to `Zombie` (`monster.zombie.Zombie`)
and `Spider` (`monster.spider.Spider`). Mojang is progressively foldering the
`monster` package, so expect more of this each release.

## 3. Advancement criterion package split — 1 file

```java
net.minecraft.advancements.criterion.*        // 26.1.2
net.minecraft.advancements.predicates.*       // 26.2 — EntityPredicate
net.minecraft.advancements.predicates.entity.*// 26.2
net.minecraft.advancements.triggers.*         // 26.2 — SimpleCriterionTrigger
```

`ContextAwarePredicate`, `EntityPredicate`, `SimpleCriterionTrigger` and
`CriteriaTriggers` all relocate. `WarbandEventTrigger` also reports
`codec() does not override` and `String is not a functional interface` at
lines 19/25 — the `SimpleCriterionTrigger` base contract changed shape, so this
file needs a real read, not a find-and-replace. **Budget the most time here.**

Affected: `WarbandEventTrigger`, `WarbandCriteria`.

## 4. Registry constants relocated

Missing from their old owners:

| Constant | Old owner | Count |
|---|---|---|
| `PILLAGER`, `VINDICATOR`, `EVOKER`, `ILLUSIONER`, `ZOMBIE`, `WITHER_SKELETON`, `LIGHTNING_BOLT` | `EntityType` | 22 |
| `WHITE_BANNER`, `BLACK_BANNER`, `GRAY_BANNER`, `RED_BANNER`, `ORANGE_BANNER` | `Items` | 20 |
| `WITHER_SKULL` | `EntityType` | 2 |

Affected: `FactionBanner`, `IllagerGrudgeSystem`, `RaidEvolutionHandler`,
`BossDirector`. Resolve each against the 26.2 registry classes when porting —
likely the same foldering pattern as (2).

## 5. `knockback` signature changed — 2 sites

`GolemSpinGoal.java:60` — `knockback(double, double, double)` no longer resolves.

## Recommended order

1. Do item (1) on the 26.1.2 tree now — it is version-neutral and halves the diff.
2. Branch `port/26.2`. Do (2) and (4): pure import/qualifier churn.
3. Do (3) properly — it is the only item with real semantic change.
4. Do (5).
5. Bump `depends.minecraft` in `fabric.mod.json` from `~26.1` to `~26.2`.
6. Re-verify the mixin targets in `warband.mixins.json` still bind — the compile
   check does **not** catch mixin refactor breakage, and `MobFinalizeSpawnMixin`
   / `LivingEntityFarmDropMixin` / `AbstractArrowMixin` all target methods in
   classes Mojang is actively reorganizing. Launch a dev server, not just a build.

## Handy commands

```bash
# cross-compile check
gradlew compileJava -Pminecraft_version=26.2 -Pfabric_api_version=0.156.0+26.2

# group remaining errors by kind
gradlew compileJava -q -Pminecraft_version=26.2 -Pfabric_api_version=0.156.0+26.2 2>&1 \
  | grep -E "^\s+symbol:" | sed -E 's/^\s*symbol:\s*//' | sort | uniq -c | sort -rn

# inspect a relocated class in the mapped jar
unzip -l ~/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/\
minecraft-merged-deobf/26.2/minecraft-merged-deobf-26.2.jar | grep -i slime
```
