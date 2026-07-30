# Feature gap analysis: Warband vs Enhanced AI

Written for the 1.4.x → 1.5 planning pass, from user feedback naming Enhanced AI
as the comparison point. Every "Warband status" line below was checked against the
1.4.0 source, not recalled.

> **Status: Tier 1 and most of Tier 2 shipped in 1.4.0.** The gap table below
> describes the state this analysis was written against; see "What shipped" at the
> bottom for what is now closed and what deliberately was not.

## Baseline: there is no competitor on this platform

| | Enhanced AI | Warband 1.4.0 |
|---|---|---|
| Loaders | Forge, NeoForge | Fabric |
| Max MC version | **1.21.1** | 26.1.2 |
| 26.x support | **none** | yes |
| Downloads | ~549k | — |

Enhanced AI has never shipped for Fabric and has never shipped for 26.x. A user
went looking for it on 26.1.2, could not find it, and landed here instead — that is
the whole origin of this feedback. **Warband is currently the only mod of its kind
on Fabric 26.x.** That is a real moat, and it argues for closing the obvious
functional gaps quickly rather than polishing systems nobody else has.

It also means the comparison is asymmetric: users arrive expecting Enhanced AI's
*anti-cheese* and get Warband's *tactics*. The gaps below are almost entirely in
the first category.

## Where Warband already leads

Not gaps — worth knowing so they don't get "fixed" into parity:

- **Role squads with a shared blackboard.** Leader/Bruiser/Marksman/Support, morale,
  rout on leader death, shared last-known position, backup calls, distress
  broadcast between squads. Enhanced AI has no squad concept at all.
- **Difficulty that learns.** Regional pressure derived from demonstrated player
  capability, not a global config number.
- **The entire illager layer.** Five factions, doctrines, territories, heat that
  decays and shifts with rival kills, grudges, revenge parties, bounty hunters,
  Warmarshals, stronghold garrisons, advancements.
- **Multiplayer awareness.** Threat memory, anti-dogpile targeting, death mercy
  zones, per-region smart-mob budgets.
- **Anti-farm director.** Drop/XP suppression and escape for trapped or crowded mobs.
- **Boss phase abilities**, encounter pacing (build-up/peak/relax), proactive
  out-of-combat AI (dusk perches, idle horde clustering, sun/rain shelter, jockeys).
- **Perception rules that subtract.** Crouching, invisibility and darkness *reduce*
  detection range. Enhanced AI moves the opposite way, granting wall-penetrating
  target detection. Warband's direction is the defensible one and should not change.

## The gap table

| Enhanced AI feature | Warband 1.4.0 status | Verdict |
|---|---|---|
| Zombies mine toward target (stone pickaxe) | **Absent.** No `destroyBlock`/`mobGriefing` call anywhere in the codebase | **Take** |
| Ravagers break tagged blocks | Absent | **Take** (same system) |
| Creeper swell variants: breach walls, walk while exploding, self-throw | **Absent.** Only `CreeperStalkGoal` + charged spawning | **Take** |
| Mobs climb ladders/vines | **Absent.** No `onClimbable` handling. Only `LEAP_UNREACHABLE` / `MOB_STACK_CLIMB`, gated at difficulty 0.65–0.80 | **Take** |
| Break boats/minecarts they are stuck in | Absent | **Take** |
| Mobs flee detonating creepers and ignited TNT | Absent. `FriendlyFireHandler` exists but is reactive only | **Take** |
| Door opening | Absent | **Take** |
| Skeleton/pillager shooting range, cooldown, accuracy knobs | Partial — `RangedRepositionGoal`, `SkeletonSmokeGoal`, no cadence/accuracy tuning | Consider |
| Ghast fireball volleys; blaze fireball randomisation | Partial — `BlazeHoverGoal`, `GHAST_REPOSITION` reposition only | Consider |
| Mobs flee Wardens | Absent (`WARDEN_PRESSURE` is the Warden's own tactic) | Consider |
| Silverfish call reinforcements | Absent (`CallBackupGoal` exists — trivial to extend) | Consider |
| Spider web throw | **Have** — `SpiderWebGoal`, retuned in 1.4.0 | — |
| Spider fall-damage reduction | Absent | Low |
| Zombie/spider mounting | **Have** — `MountJockeyGoal` | — |
| Drowned swim/aquatic pathing | **Have** — `WaterCommitGoal` | — |
| Enderman disarm / unreachable teleport | **Have** — `EndermanDisruptGoal`, `EndermanProvokeHandler` | — |
| Witch potion depth, Dark Arts villager conversion | Partial — `WitchSupportGoal` throws real splash potions; no conversion | Low |
| Slime larger variants, jump speed | Partial — `SlimeSurgeGoal` | Low |
| Wall-penetrating target detection; +15% flat speed, +250% swim | Absent **by design** — see below | **Decline** |
| Zombie ender pearls, fishing rods | Absent | **Decline** |
| Iron/snow golem, wolf, villager, passive-animal overhauls | `GolemDirector` only | **Decline** |

## Recommended roadmap

Ordered by player-visible impact per unit of risk. Each entry names the difficulty
band it should unlock in, because that scalar is Warband's core mechanism and the
1.4.0 pass just finished removing step functions from it — new features must fade
in, not switch on.

### Tier 1 — the "why can't they reach me" cluster

This is one feature wearing four hats. Every item in it is a variation on *the
player made themselves unreachable and the game had no answer*. It is also exactly
what the feedback asked for by name.

1. **Siege mining** (unlock ~0.55). Zombies and other diggers path to the nearest
   block between them and a known target position and mine it. Needs an allowlist
   block tag, a `mobGriefing` check, and per-mob rate limiting. *(Shipped without a
   tool requirement: block-break cracks and sound are already an unmistakable tell,
   and requiring a pickaxe would mean reworking role loadouts for no added clarity.)*
2. **Climbable blocks** (unlock ~0.30). Ladders and vines. Cheapest item on this
   list and it fixes a large slice of "mob stands at the bottom doing nothing".
3. **Creeper breaching** (unlock ~0.60). A creeper that cannot path to the player
   but is within a few blocks of the wall between them commits to detonating
   against it. This is the marquee ask.
4. **Vehicle anti-cheese** (any difficulty). Break boats and minecarts used to park
   a mob. Belongs to the existing anti-farm philosophy, not to difficulty.
5. **Explosion avoidance** (unlock ~0.35). Squadmates scatter from a swelling
   creeper or lit TNT. Cheapest possible increase in *perceived* intelligence, and
   it composes with squads in a way Enhanced AI cannot match.

### Tier 2 — combat texture

6. Ranged cadence/accuracy config for skeletons and pillagers.
7. Ghast volleys, blaze fireball variation.
8. Warden fear; silverfish reinforcements (reuse `CallBackupGoal`).
9. Door opening for non-zombie humanoids. *(Shipped: reserved for the
   intelligent-humanoid set — illagers, piglins, drowned. Zombies excluded because
   breaking the door down is their signature and vanilla already grants it on Hard.)*

## The guardrail that makes mining shippable

Mob block-breaking is the most-requested and most-hated feature in this genre. The
objection is never "mobs shouldn't dig", it is **"I logged off and my base had
holes in it"**.

Warband already has the infrastructure to answer that. `TemporaryTacticBlocks`
tracks mutated blocks with a TTL and a server-tick restore loop — it currently only
*places* blocks (cobwebs, frosted ice) and clears them on expiry. Extending an
`Entry` to record the **original** `BlockState` and restore it makes siege damage
*reversible*: a squad breaches your wall, fights you through the hole, and the wall
seals a minute later.

That is a genuine differentiator, not parity. Enhanced AI cannot offer it, because
it has no concept of temporary tactical terrain. It also collapses the usual config
matrix down to one honest choice:

- `siegeMiningEnabled` — off by default for the first release
- `siegeMiningPermanent` — `false` by default (reversible), `true` for players who
  want real consequences
- `siegeMiningBlockTag` — allowlist, so packs decide what is diggable
- respect `mobGriefing`, and skip blocks that fail a claim/protection check

Non-negotiables regardless: never mine bedrock/obsidian-tier by default, never mine
below a rate limit, never touch containers or beds, and log every breach when
`debugTacticLogs` is on so griefing reports are diagnosable.

## Explicit declines, with reasons

- **Wall-penetrating target detection.** Warband's `VisibilityRules` deliberately
  *reduces* detection for crouching, invisible and darkness-affected targets.
  Adding x-ray aggro would contradict the mod's central perception rule and delete
  stealth as a strategy. The squad blackboard already shares a *legitimately seen*
  target, which is the honest version of the same idea.
- **Flat +15% speed / +250% swim for all mobs.** Warband scales speed through
  difficulty-driven attribute modifiers (`statSpeedBonusMax`). A flat global buff
  would double-dip and is exactly the "spongier, not smarter" failure the README
  argues against.
- **Zombie ender pearls and fishing rods.** Fun, but they read as a gimmick mod
  rather than vanilla+. They would also invalidate terrain as counterplay right
  after Tier 1 spends effort making terrain matter.
- **Passive animals, villagers, wolves, snow golems.** Scope creep away from a
  hostile-AI mod, and well covered by dedicated mods.

## Beyond parity: the compounding play

Tier 1 plus the existing squad system produces something neither mod has today.
Enhanced AI's zombies mine *individually*. Warband's mobs already have roles, a
leader, a shared blackboard and backup calls. Combine them and you get **coordinated
siege**:

- A `Bruiser` breaches while `Marksman` members hold overwatch on the hole.
- A `Leader` issues the breach point, reusing the `ILLAGER_COMMAND` pattern that
  already exists for illager doctrine.
- Mining noise seeds the squad blackboard's last-known position, so a breach draws
  neighbouring squads via the existing `broadcastDistress`.
- The illager grudge system **already musters crusades near a player's respawn
  point** for the "they came for my base" beat. It currently arrives and mills
  around outside. With breaching, that existing feature finally lands.

That last point is the strongest argument for Tier 1: several systems Warband has
already built are underdelivering purely because mobs cannot get through a wall.

Further ideas worth prototyping after Tier 1, in rough order of fit:

- **Trail scent.** Squads follow a decaying player trail rather than a single
  last-known point — extends the blackboard rather than adding a new system.
- **Suppression fire.** Marksmen shoot at cover edges to force a player out of it.
- **Staged assault.** Grudge crusades pick a muster point, breach at dawn, retreat
  at a morale break — the pieces (morale, rout, crusades) all exist.
- **Trap awareness.** Squads route around pressure plates and tripwires they have
  already been hurt by.

## What shipped in 1.4.0

**Closed:**

| Gap | Implementation | Unlock |
|---|---|---|
| Zombies/illagers/ravagers mine toward target | `SiegeMineGoal`, `Tactic.SIEGE_MINE` | 0.55 / 0.60 / 0.50 |
| Creeper breaching | `CreeperBreachGoal`, `Tactic.CREEPER_BREACH` | 0.60 |
| Climb ladders/vines | `ClimbToTargetGoal` | 0.30 |
| Flee creepers, TNT **and wardens** | `DreadAvoidGoal` | 0.35 |
| Break out of boats/minecarts | `VehicleEscape` | ungated |
| Ranged cadence + accuracy | `AbstractSkeletonRangedMixin` | scales from 0 |
| Ghast volleys, blaze variation | `FireballVolley` | scales from 0 |
| Door opening | `WarbandDoorGoal` | 0.40 |

Reversible damage landed as designed: `TemporaryTacticBlocks` records the original
`BlockState` and restores it, refusing to overwrite player rebuilds and retrying
rather than materialising a block inside an entity.

**Note on door opening.** An earlier revision of this doc claimed it needed
accesswidener surgery because `GroundPathNavigation` exposes no door methods. That
was wrong — the check stopped one call short. `PathNavigation.getNodeEvaluator()` is
public, and `NodeEvaluator.setCanPassDoors` / `setCanOpenDoors` are public on top of
it, so enabling door pathing is two lines and no widener. Shipped as
`WarbandDoorGoal`.

Two things worth keeping in mind for anyone extending it:

- `OpenDoorGoal` only fires when the mob's **current path already routes onto a door
  block**, and the node evaluator treats a closed door as impassable by default. So
  the flag must be set or the goal is silently inert forever.
- The goal is wrapped rather than used bare because Warband rebinds goals on every
  world load and clears them with `goal instanceof WarbandGoal`. A raw
  `OpenDoorGoal` would not match that filter and would accumulate one duplicate per
  load.

**Deliberately not done, with reasons:**

- **Silverfish reinforcements.** Not a real gap — vanilla already ships
  `Silverfish$SilverfishWakeUpFriendsGoal`, which wakes silverfish in nearby
  infested blocks on hurt. The config key `silverfishReinforcementsEnabled` exists
  but is currently unused; either wire it to an acceleration of the vanilla goal or
  drop the key.
- **Pillager ranged tuning.** `AbstractSkeleton` exposes `getAttackInterval()` /
  `getHardAttackInterval()` as clean seams. Pillagers build
  `RangedCrossbowAttackGoal` inline with a fixed interval and no equivalent hook, so
  they are untouched rather than half-reworked.

**Verification standard.** All of the above compiles, and a dev server was launched
to confirm the mixin actually binds — `defaultRequire: 1` makes a wrong descriptor
fatal at load, which a compile check cannot catch. Startup was clean with zero stack
traces. None of it is playtested for *feel*: dig rates, breach cadence and volley
density are first-pass numbers.

## Sources

- [Enhanced AI (Modrinth)](https://modrinth.com/mod/enhanced-ai) — feature set and
  version/loader support
- Warband 1.4.0 source, verified by direct search for `destroyBlock`, `mobGriefing`,
  `onClimbable`, `Boat`, `Minecart`, `Door`
