# Changelog

## 1.4.0

Last release for 26.1.2. Subsequent updates target 26.2, which is not
source-compatible (see `docs/PORTING-26.2.md`).

### Fixed

- Fixed undead abandoning a chase to stand under nearby cover, then pacing in and
  out of it. Sun-shelter ran at a higher goal priority than the vanilla attack
  goal and never checked for a target, so pillaring up and breaking the base left
  every zombie underneath pinned to the leftover blocks. Undead now only seek
  shade out of combat, and burn while chasing, as in vanilla.
- Fixed squad members being dragged back to the group while actively chasing. A
  horde that bunched up would reel its own pursuers in, so mobs never followed a
  player any real distance. Regrouping now only applies when nobody can see the
  target.
- Fixed `config/warband.properties` resolving against the process working
  directory. Servers launched from anywhere but the game directory read and wrote
  a different file than the one in the pack, so edits appeared to be discarded.
- Fixed config edits not applying until a full game restart. The file is now
  re-read when a world loads and on `/reload`.
- Fixed creepers ignoring cats and ocelots. Vanilla's avoidance goal shares a
  priority with Warband's regroup goal, and the goal selector cannot break a tie,
  so whichever started first kept control of movement. Warband tactics now yield
  entirely while a cat is within the vanilla fear radius.
- Fixed spiders climbing to a player and then never attacking, or stopping dead
  in front of them. Ceiling-crawl shared a priority with the vanilla spider
  attack goal and held on to movement as long as the spider touched any wall.
- Fixed spider webs being inescapable. Webs landed on the target's own block
  every 3 seconds with no stacking check, so they hit the face with nowhere to
  dodge and were reapplied the moment a player broke free.
- Fixed squad formations spawning inside terrain and on top of each other, which
  made hordes pile into one spot instead of reaching the player. Members now take
  distinct bearings on a ring, and each slot is checked for real standing room.
- Fixed zombie encirclement giving up when its preferred approach was blocked,
  which put the whole squad back on one path.

### Changed

- Difficulty progression is a ramp rather than a staircase. Role gear was a hard
  cutoff — nothing below difficulty 0.35, a full iron kit at it — and squad
  formations and leaders unlocked in the same narrow band. Gear pieces now roll
  independently on a curve from 0.25 to 0.80 and climb leather → chainmail →
  iron, formations fade in from 0.35, and both the spawn-distance ramp and the
  cave depth bonus are eased.
- `regionalSpawnRampBlocks` default 32 → 256. At 32 the world went from fully
  calm to fully hostile across one screen of travel. **Existing configs keep
  their own value; raise it by hand to get the smoother curve.**
- Spider webs are a zoning tool rather than a stun: they lead a moving target, so
  movement is the counterplay, and spiders bite instead of webbing at point-blank
  range.
- Reduced early-game mob volume as a side effect of fading formations in, which
  was also reported as a lag source.

### Added — siege and anti-cheese

The answer to a player with no valid path to them. Every system Warband already had
— squads, morale, the shared blackboard, illager crusades that muster at your base —
quietly ended at a fence post.

- **Siege mining.** Zombies (0.55), illagers (0.60) and ravagers (0.50) break through
  walls to reach a target they cannot path to. **Breaches heal by default**: the
  original block is restored after `siegeMiningRestoreSeconds` (90s), because Warband
  is a drop-in for existing worlds and a breach exists to force the fight, not to eat
  your base. Set `siegeMiningPermanent=true` to keep the damage.
  - Only engages when pathfinding has genuinely failed, never as a shortcut past open
    ground.
  - Only breaks blocks in the `siegeMiningBlockTag` (default `warband:siege_breakable`
    — dirt, wood, cobble, glass, wool). **Obsidian, deepslate, metal blocks,
    containers and beds are absent, so reinforcing a base is real counterplay.**
  - Respects the `mobGriefing` gamerule, caps blocks per breach, and shows real
    block-break cracks so a wall is always heard going before it goes.
  - Restores refuse to overwrite anything you rebuilt, and wait rather than
    materialise a block inside you.
- **Creeper breaching** (0.60). A creeper that cannot reach its target closes on the
  wall between you and detonates. Vanilla only swells within ~3 blocks of a
  *reachable* target, so one layer of dirt made you immune to the one mob whose whole
  identity is removing walls.
- **Ladder and vine climbing** (0.30). Unlocks early on purpose: using a ladder is not
  clever, it is the baseline expectation that a mob understands what it is touching.
- **Explosion and warden avoidance** (0.35). Mobs scatter from swelling creepers, lit
  TNT and wardens. A formation peeling away from one creeper reads as a group
  reaction, not scenery.
- **Vehicle anti-cheese.** Mobs parked in a boat or minecart break out. Not
  difficulty-gated — that is an exploit at every difficulty.
- **Ranged tuning.** Skeleton shot cadence and accuracy now scale with Warband
  difficulty instead of only the world difficulty setting, bounded by
  `rangedCadenceBonusMax` / `rangedAccuracyBonusMax`. Pillager crossbows are
  unchanged; they have no equivalent hook.
- **Ghast volleys and blaze fireball variation.** Both mobs were entirely predictable
  — a fixed cadence on a fixed line. Volleys spread wide at low difficulty and
  tighten as it climbs, layered on top of vanilla's attack goals rather than
  replacing them.

Not included: door opening (the 26.1.2 door-pathing flag moved to the node evaluator
and needs accesswidener surgery for little gain — vanilla already breaks and opens
doors) and silverfish reinforcements (vanilla already ships
`SilverfishWakeUpFriendsGoal`; the config key is currently inert).

### Added

- Added `customMobPools`, opting modded mobs into Warband behaviour pools so they
  gain the matching tactics and roles and squad up with their vanilla
  counterparts. Mobs that already extend a vanilla class (most custom zombies) are
  picked up automatically and need no entry.
- Added a one-time explainer the first time a player earns faction heat. Every
  other faction message announced a consequence — a revenge party, a bounty
  hunter — without ever establishing that the faction system exists, so players
  could not tell what they had triggered, or whether they had triggered anything.

### Internal

- Gradle 9.6.1, Loom 1.17.17, Fabric loader 0.19.3, Fabric API 0.155.2+26.1.2.
- Shelter searches scan outward with an early exit instead of testing every block
  in a ~2200-block volume on every recheck for every undead.

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
