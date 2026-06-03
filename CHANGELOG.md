# Changelog

## 1.3.2

- Added `/warband intel <player>` for ops to inspect another player's faction state.
- Added `/warband clear <player> [faction]` for ops to wipe grudges and heat from a player, optionally scoped to one faction.
- Added spider rain shelter: out-of-combat spiders path to the nearest covered tile when caught in the rain.

## 1.3.1

- Fixed a server crash during raid scans caused by an overly large entity-lookup box.

## 1.3.0

- Added proactive AI: skeletons perch on high ground at dusk, spiders pre-web approach paths, idle zombies cluster into hordes.
- Added spider leap-strike and ceiling-crawl drop attack.
- Added bounty hunter ambush hold when closing without line of sight.
- Added natural jockey acquisition: smart skeletons mount nearby spiders, smart baby zombies mount nearby chickens.
- Added vex bond: vexes die when their summoning evoker dies.
- Added zombie stacking to reach perched players.
- Added raid predation: raiders pillage every animal in range once no village defenders remain.
- Added raid finale bounty hunter on high-ominous raids.
- Added rival faction interception during raids.
- Added faction-colored trophy banner drops on Warmarshal and patrol-captain kills.
- Added passive faction heat decay over time.
- Added rival-kill heat reduction: killing a faction helps their rival forgive you.
- Added faction territories: kills inside a faction's territory hit harder; kills in their rival's territory hit softer.
- Added difficulty floor for spawner, trial spawner, and summoned mobs.
- Added Stormie's Spiders compatibility.
- Changed sun-shelter: undead seek shade at dawn predictively instead of waiting to burn.
- Changed witches to throw real splash potions for buffs.
- Changed zombie encirclement to surround from all angles instead of stacking on one flank.
- Changed retreat behavior to apply only to illagers, piglins, and drowned. Other monsters fight to the death.
- Changed retreat trigger: smart mobs pull back earlier when no allies are nearby.
- Changed backup calls to alert nearby idle squads.
- Fixed jockeys steering their mounts away from the player instead of engaging.
- Fixed spider webs not firing.
- Fixed sun-shelter not triggering.

## 1.2.1

- Tuned bounty hunters down: less bonus health, less speed, no enchanted armor, and no long Speed effect.
- Improved bounty hunter pursuit: smaller hitbox scale for two-block gaps and wind-charge jumps for vertical targets.
- Improved bounty hunter rewards with stronger vanilla loot: more emeralds, XP bottles, and occasional supplies.
- Fixed faction-seat state: Warmarshal crowns/broken seats persist, broken seats stop creating new heat/grudges, and Illager Invasion Invokers are prioritized as Warmarshals when present.
- Removed Warband's vanilla-difficulty scaling hooks; Easy/Normal/Hard no longer scale Warband difficulty or boss ability damage.

## 1.2.0

- Added Illager War advancements, including mansion entry, faction milestones, bounty, crusade, and Warmarshal kill progress.
- Added modded stronghold support: Illager Invasion forts/labyrinths are faction camps, and The Lost Castle is a faction seat.
- Added new hostile tactics: spider ceiling crawl, elevated-target hops, ranged repositioning, stealth/status-aware detection, bogged poison back-dash, stray jump shots, and more reliable Enderman disrupt/provoke behavior.
- Added optional Overworld depth difficulty for harder deepslate caves, with `/warband difficulty` showing raw/applied depth bonus.
- Added the bounty hunter overhaul: diamond gear, melee/ranged weapon swapping, parkour pursuit, taunts, glowing mark, stalk teleport, one revive, ominous summon cue, and `/warband debug bounty`.
- Added faction-war escalation and feedback: heat states, war/crusade patrols, in-world event cues, better witness rules, and clearer `/warband intel`.
- Changed config presets to `CUSTOM`, `VANILLA_PLUS` (`vanilla+` accepted), and `FANTASY`; Vanilla+ disables the more RPG-facing systems by default.
- Changed REGIONAL spawn protection: `safeRadius` now applies in REGIONAL mode, with a separate `regionalSpawnRampBlocks` ramp and tighter defaults.
- Fixed Warmarshal Illusioner conversion reliability in worlds without Illager Invasion.
- Fixed revenge and bounty patrols not immediately pathing toward the target player.
- Fixed revenge and bounty leader nametags rendering through walls.
- Fixed rare false-death states where normal Warband-stamped mobs could play death animation/audio but keep fighting.
- Fixed faction heat not tracking when bounty hunters were disabled.
- Fixed revenge grudges expiring from missed random spawn rolls instead of real failed spawn attempts.

## 1.1.0

- Split `bossAbilitiesEnabled` into `witherAbilitiesEnabled` and `enderDragonAbilitiesEnabled` so each boss can be toggled independently. Existing `bossAbilitiesEnabled` values are read once as the default for both new keys.
- Auto-disable Warband Ender Dragon abilities when [True Ending](https://modrinth.com/mod/true-ending) is loaded — detects both the Fabric mod (`mr_true_ending`) and the datapack distribution (via the `true_ending` data namespace), and re-checks on `/reload`.
- Added `/warband reload` (op-only) to re-read `config/warband.properties` without restarting the world.
- Reduced vanilla difficulty double-dipping by disabling the vanilla regional floor by default.
- Regional difficulty ramps faster by default:
  - `regionalIncreaseDelaySeconds` 10 → 0
  - `regionalBlendRate` 0.08 → 0.20
  - `regionalAccelerationPerSample` 0.01 → 0.03
