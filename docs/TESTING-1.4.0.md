# Warband 1.4.0 test plan

Executable verification for the 1.4.0 behaviour changes. Each case lists the exact
commands, the log lines that prove it worked, and the pass criterion.

**Split of labour:** cases marked **[LOG]** are machine-checkable — run them, then
hand over `logs/latest.log` and they can be verified without you describing
anything. Cases marked **[EYES]** cannot be read from a log; they need your
judgement, and the questions are listed at the bottom.

---

## Setup

1. Set these in `config/warband.properties`, then `/warband reload`:

```properties
debugTacticLogs=true
```

That flag is what emits every `EVENT=` line below. Everything else is already on by
default in 1.4.0.

2. Confirm the config path the game is actually using — 1.4.0 changed this:

```
/warband reload
```

Expect `[Warband] config reloaded from <absolute path>`. **If that path is not the
one you edited, stop and report it** — that was a real bug and this is its check.

3. Play as a **player in-game**, not from the server console. The console's position
   is the world origin, which is inside terrain; mobs spawned there suffocate
   instantly and every test silently reports "no mobs". (Learned the hard way while
   validating this plan. If you must drive it from console, prefix with
   `forceload add -32 -32 32 32` and `execute positioned <x> <y> <z> run ...`.)

4. Grab a flat area away from your base. Recommended: creative, superflat, night.

### The one command that makes all of this possible

```
/warband debug stamp <0.0-1.0>
```

Re-stamps every hostile within 24 blocks at that difficulty and rebinds its goals.
Needed because `/summon` stamps mobs at the *local* difficulty, which near spawn is
`0.00` — no tactics at all. So: summon what you want, then stamp it.

Verify tactics landed with `/warband mobdebug`. Confirmed working output:

```
[Warband] zombie id=26 difficulty=0.90 role=NONE squad=-1
  tactics=WATER_COMMIT,PRESSURE_UNREACHABLE,ZOMBIE_HORDE,LEAP_UNREACHABLE,MOB_STACK_CLIMB,SIEGE_MINE
```

---

## A. Regression tests (the originally reported bugs)

### A1 — Zombies no longer pin themselves under cover **[LOG] [EYES]**

The headline bug. Reported as zombies "rocking back and forth" under a floating
block and not following.

```
/time set day
/warband debug squad 0.9
```

Pillar up 4, break the bottom 2, let them gather beneath, then jump down and run
120+ blocks.

- **PASS (log):** **zero** `EVENT=SEEK_SHELTER` lines for those mobs while they have
  a target. `SEEK_SHELTER` appearing *only* when nothing is targeting you is correct.
- **PASS (log):** **zero** `EVENT=REGROUP` lines while they can see you.
- **FAIL:** any `SEEK_SHELTER` or `REGROUP` interleaved with combat tactic events for
  the same mob id.
- **[EYES]** Do they follow you the whole way, or stall under the leftover pillar?

### A2 — Config actually persists and reloads **[LOG]**

```
/warband reload
```

Then edit `maxSquadSize` in the file, and **reload the world** (do not restart the
game).

- **PASS:** `[Warband] Config loaded` appears again on world load, and
  `/warband mobs` behaviour reflects the edited value.
- Two `Config loaded` lines per launch is correct: mod init, then server start.

### A3 — Creepers fear cats again **[LOG] [EYES]**

```
/summon minecraft:cat ~ ~ ~ {Owner:{Name:"<yourname>"}}
/summon minecraft:creeper ~5 ~ ~
/warband debug stamp 0.9
```

- **PASS (log):** **no** `EVENT=CREEPER_STALK` or `EVENT=CREEPER_BREACH` lines while
  the cat is within ~6 blocks of the creeper.
- **[EYES]** Does the creeper visibly back off?

### A4 — Spider webs are survivable **[LOG] [EYES]**

```
/summon minecraft:spider ~3 ~ ~
/warband debug stamp 0.9
```

Let it web you, get stuck, and stay still.

- **PASS (log):** `EVENT=SPIDER_WEB` lines are **≥140 ticks (7s) apart** per mob id,
  and **stop entirely** while you are standing in a web.
- **FAIL:** repeated `SPIDER_WEB` from the same id while you are already trapped.
- **[EYES]** Can you fight back, or is it still a death sentence?

### A5 — Spiders attack instead of stalling **[EYES]**

Summon a stamped spider, stand under a 2-block ceiling and let it reach you.

- **[EYES]** Does it bite when adjacent, or freeze in place / hover without
  attacking?

---

## B. Siege mining — the highest-risk feature

### B1 — A wall gets breached **[LOG]**

Wall yourself into a 1-thick **dirt or cobblestone** box, fully sealed.

```
/warband debug squad 0.9
```

- **PASS:** in order —
  ```
  EVENT=SIEGE_DIG_START mob=zombie id=… diff=0.90 block=minecraft:dirt target=… digTicks=…
  EVENT=SIEGE_MINE mob=zombie id=… diff=0.90 target=player
  EVENT=SIEGE_BLOCK_REMOVED pos=… block=minecraft:dirt permanent=false restoreIn=1800t pending=1
  ```
- **FAIL:** `SIEGE_DIG_START` with no `SIEGE_BLOCK_REMOVED` following (dig never
  completes), or digging while you are standing in the open.

### B2 — Breaches heal (the default promise) **[LOG]**

After B1, walk away from the hole and wait 90s.

- **PASS:** `EVENT=SIEGE_RESTORE pos=… block=minecraft:dirt` and the wall is visibly
  whole again.

### B3 — Restore never overwrites you **[LOG]**

Repeat B1, then **stand in the hole** and wait past 90s.

- **PASS:** repeated `EVENT=SIEGE_RESTORE_DEFERRED pos=… reason=entity_inside` while
  you stand there, then `SIEGE_RESTORE` once you step out.
- **FAIL:** you take suffocation damage, or the block appears inside you.

### B4 — Restore never overwrites your rebuild **[LOG]**

Repeat B1, then place **any different block** in the hole yourself.

- **PASS:** `EVENT=SIEGE_RESTORE_SKIPPED pos=… reason=occupied_by_block`, and your
  block survives.

### B5 — Reinforced walls hold (the counterplay) **[LOG]**

Seal yourself in **obsidian** (also try deepslate, iron blocks).

```
/warband debug squad 0.9
```

- **PASS:** **zero** `EVENT=SIEGE_DIG_START`. The mobs mill outside.
- This is the intended counterplay: the `warband:siege_breakable` tag excludes
  obsidian, deepslate, metals, containers and beds.

### B6 — The gamerule is respected **[LOG]**

```
/gamerule mobGriefing false
```
Repeat B1.

- **PASS:** **zero** `SIEGE_DIG_START` / `SIEGE_BLOCK_REMOVED`.

### B7 — Permanent mode **[LOG]**

Set `siegeMiningPermanent=true`, `/warband reload`, repeat B1.

- **PASS:** `SIEGE_BLOCK_REMOVED … permanent=true restoreIn=never`, and **no**
  `SIEGE_RESTORE` ever follows.

### B8 — Difficulty gate **[LOG]**

```
/warband debug stamp 0.40
```
(then repeat B1 with a sealed dirt box)

- **PASS:** no siege events at 0.40; `/warband mobdebug` shows no `SIEGE_MINE` in the
  tactic list. Stamp the same mobs at `0.60` and it should appear.

---

## C. Other new behaviours

### C1 — Creeper breaching **[LOG] [EYES]**

Seal yourself in a 1-thick dirt box.

```
/summon minecraft:creeper ~4 ~ ~
/warband debug stamp 0.9
```

- **PASS (log):** `EVENT=CREEPER_BREACH mob=creeper id=… diff=0.90`
- **[EYES]** Does it walk to the wall and detonate against it, or wander off?
- **⚠ Note:** this is the **one exception to reversible damage**. The blast is an
  ordinary vanilla explosion, so its crater is permanent no matter what
  `siegeMiningPermanent` is set to, and it can remove blocks the siege tag excludes —
  including an iron door. Turn `creeperBreachEnabled` off if a world must take no
  lasting damage at all. Worth deciding explicitly before release.

### C2 — Ladder climbing **[LOG] [EYES]**

Build a 6-block pillar with a ladder up one side; stand on top.

```
/warband debug squad 0.9
```

- **PASS (log):** `EVENT=CLIMB mob=zombie id=… rise=…`
- **[EYES]** Do they actually reach the top, or slide back down repeatedly?

### C2b — Door opening **[LOG] [EYES]**

Build a room with a **fully sealed** wooden-door wall between the mob and its target,
then put an illager, piglin or drowned on the far side.

```
/summon minecraft:vindicator ~-6 ~ ~
/warband debug stamp 0.9
```

- **PASS (log):** `EVENT=DOOR_GOAL_BOUND mob=vindicator id=…` then
  `EVENT=DOOR_OPEN mob=vindicator id=… door=<x y z>`
- **Confirmed working output from a dev-server run:**
  ```
  EVENT=DOOR_GOAL_BOUND mob=vindicator id=25 pos=13 101 -1
  EVENT=DOOR_OPEN       mob=vindicator id=25 pos=15 101 0 door=16 102 0
  ```
- **`DOOR_GOAL_BOUND` but no `DOOR_OPEN`** means the mob never actually pathed
  through the door. **Seal the wall completely** — the first attempt at this test
  failed purely because the wall had a gap and the mob walked around it, which looks
  identical to the feature being broken.
- **No `DOOR_GOAL_BOUND` at all** means the mob is not in the eligible set. Zombies
  are excluded on purpose; they break doors instead.
- **[EYES]** Do they shut the door behind them? Does a squad filing through look
  deliberate, or do they jam up fighting over it?

### C2c — Tactical barks **[EYES]**

```
/warband debug squad 0.9
```

Let them engage you, then move so they have to reposition.

- **[EYES]** Can you tell *by ear alone* whether they are closing on you, circling,
  pulling back, or calling for reinforcements? That is the whole point — six cues,
  meant to be learnable.
- **[EYES]** Is it too chatty with 6+ mobs? Throttle is one bark per mob per 3.5s.
- **[EYES]** Do the cues sound like Minecraft, or like a different game leaked in?
- Cross-check against the log: every bark corresponds to an `EVENT=` line for the
  same mob, since both hang off the same call. A tactic firing in the log with no
  audible cue is either intentionally silent (webs, siege digging, lit creepers) or a
  gap worth reporting.
- Set `tacticalBarksEnabled=false` and confirm silence returns.

### C2d — Nemesis scars **[EYES]**

Kill a *notable* factioned illager (outpost captain, banner-carrier) in front of
witnesses, using a **specific** damage type — set one on fire, or shoot it. Let a
survivor escape, then wait for the revenge patrol (or force it with
`/warband debug revenge 0.9`, which spawns a deliberately blade-scarred captain).

- **PASS:** `/warband intel` lists the survivor with a scar suffix, e.g.
  `Yorn of the Ash Banner / Ash Banner anger 40 attempts 0 fire-scarred`
- **PASS:** when the patrol arrives, a grey chat line names what changed —
  *"Yorn the Returned walks through flame now."*
- **PASS:** the adaptation is real, not cosmetic. A fire-scarred survivor should
  actually ignore fire; an arrow-scarred one should visibly wear a helmet.
- **[EYES]** Does the causal link land — can you tell the adaptation came from *your*
  earlier tactics rather than a random buff?
- **[EYES]** Is escalation fair? Killing them with the same weapon every time should
  progressively stop working, which is the intended pressure to vary tactics.
- **Save compatibility is covered by unit tests** (`IllagerScarTest`), including
  grudges saved before scars existed, so no in-game check is needed for that.

### C2e — Perception cues and stealth **[LOG] [EYES]**

Needs a real player, so it cannot be driven from the console. Stand in the dark or
crouch at the edge of a stamped mob's range.

```
/warband debug squad 0.9
/time set night
```

Crouch and back away until they lose you, then approach again.

- **PASS (log):** `EVENT=SUSPICIOUS mob=… player=…` when concealment is what stopped
  them seeing you, `EVENT=ALERTED` when they lock on, and exactly one
  `EVENT=LOST_TRACK` per encounter once nothing is hunting you.
- **FAIL:** `SUSPICIOUS` firing constantly while you stand in the open — it should only
  fire when a stealth modifier (crouch, darkness, invisibility) is the reason you are
  unseen.
- **FAIL:** repeated `LOST_TRACK` spam. It is owed once per alarm, not per mob.
- **[EYES]** Does the mob visibly **stop and turn to look**? That is the tell for when
  you can see it; the sound is the tell for when you cannot.
- **[EYES]** Does the all-clear land as *relief*? That cue is the whole reason the
  feature exists — without it hiding has tension but no resolution.
- **[EYES]** Can you now feel that crouching does something? That was previously a real
  but completely invisible mechanic.
- Cue throttling is **per player** (one per 1.5s), not per mob, so a large squad should
  not produce a chorus. Report it if it does.

### C3 — Explosion / warden avoidance **[LOG]**

```
/warband debug squad 0.9
/summon minecraft:tnt ~3 ~ ~
```
Then separately try an ignited creeper, and `/summon minecraft:warden ~10 ~ ~`.

- **PASS:** `EVENT=DREAD_AVOID mob=… threat=tnt fled=true` (and `threat=creeper`,
  `threat=warden`).
- **FAIL:** `fled=false` repeatedly — means the flee path is failing and the feature
  is inert.

### C4 — Vehicle anti-cheese **[LOG]**

Put a stamped hostile in a boat and leave it ~3s.

```
/summon minecraft:zombie ~2 ~ ~
/warband debug stamp 0.9
```
(place a boat, push the zombie in)

- **PASS:** `EVENT=VEHICLE_ESCAPE mob=zombie id=… vehicle=boat`, boat destroyed.

### C5 — Custom mob pools **[LOG]**

Add to config, then `/warband reload`:

```properties
customMobPools=ZOMBIE_FAMILY>minecraft:husk
```

- **PASS:** `[Warband] custom mob pools: 1 entity type(s) mapped`
- Then `/summon minecraft:husk`, `/warband debug stamp 0.9`, `/warband mobdebug` —
  expect zombie-family tactics on it.
- Also try a deliberate typo (`ZOMBIE_FAMILY>not:a:real:mob`) — **PASS:** a warning
  line, and the mod still loads.

### C6 — Faction first-contact explainer **[LOG] [EYES]**

Find or spawn a pillager outpost illager and kill one.

- **PASS (log):** the chat lines are broadcast, mentioning the faction and
  `/warband intel`.
- **[EYES]** Does it read as informative or as spam? It fires **once ever** per
  player.

### C7 — Difficulty ramp smoothness **[LOG]**

From world spawn, run `/warband difficulty` at roughly 0, 100, 150, 200, 300, 400,
600 blocks out and paste the numbers.

- **PASS:** a gradual curve. **⚠ If your config predates 1.4.0 it still has
  `regionalSpawnRampBlocks=32`** and will show a cliff — set it to `256` first, or
  this test just re-measures the old bug.

---

## D. Questions only you can answer **[EYES]**

Numbers I picked blind; all are first-pass guesses.

1. **Dig speed.** 60 ticks/block at difficulty 0.55 → 24 at 1.0. Does a breach feel
   like a threat you can respond to, or too fast/too slow to matter?
2. **Breach telegraph.** Block-break cracks plus sound are the only warning. Is that
   noticeable enough that being dug out never feels cheap?
3. **Blocks per breach.** Capped at 4 per activation. Does that make a usable hole,
   or do they give up half-way through a 2-thick wall?
4. **90-second restore.** Long enough for the fight to matter, short enough not to
   feel like lasting damage?
5. **Reversible as the default** — right call for your audience, or do they want
   permanent?
6. **Should `siegeMiningEnabled` ship `false`** for one release so reports come in
   before it is on for everyone? It is currently **on by default**.
7. **Web frequency** at 7s with lead-targeting — still threatening, or defanged too
   far?
8. **Skeleton archery.** Cadence up to 35% faster and 50% tighter spread at max
   difficulty. Fair, or oppressive? (No log event — you have to feel this one.)
9. **Ghast/blaze volleys.** +2 fireballs for ghasts, +3 for blazes at max. Chaos or
   noise?
10. **Overall difficulty curve** after the ramp fixes — does progression now feel
    like a ramp rather than a staircase?

---

## Handing results back

```
run/logs/latest.log        (dev server)
logs/latest.log            (normal install)
```

The whole trace is one grep:

```bash
grep "EVENT=" logs/latest.log
```

Every line is `key=value`, so counts, ordering and per-mob-id sequences can all be
checked mechanically. Paste that output plus answers to section D.
