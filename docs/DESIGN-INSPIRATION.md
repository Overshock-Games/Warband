# Borrowing from other games

Cross-domain design notes for post-1.4.0. Sources are games, not Minecraft mods —
mod-space comparison lives in `docs/FEATURE-GAP.md`.

## The constraint that filters everything

Warband is **server-side with no client mod**. That eliminates most borrowed
mechanics outright: no custom HUD, no new models, no shaders, no input capture, no
UI panels. Anything imported has to be expressible through vanilla channels.

The actual toolkit, and how much of it is currently spent:

| Channel | Status in 1.4.0 |
|---|---|
| Vanilla sounds | 43 call sites, but ad-hoc — no systematic per-tactic vocabulary |
| Particles | Used (`TacticalEffects`) |
| Chat / action bar | Used, mostly by the illager systems |
| Name tags (+ always-visible) | Used for named illagers and bounty hunters |
| Glowing (outline through walls) | Used for the bounty mark |
| Equipment / entity scale | Used for role visuals |
| Potion effects | Used |
| **Boss bar (`ServerBossEvent`)** | **Completely unused** — a real server-side HUD, free |
| Movement itself | The main channel, and the most expensive to read |

## The through-line: Warband is smarter than it looks

30 tactics ship in 1.4.0. A player cannot perceive most of them. The feedback that
started this whole pass said it plainly about the deepest system in the mod:

> "I really don't get what the changes were … I still don't know what we were
> supposed to do or think about all this illager wars thing, which I don't even know
> if we got to see or if we didn't even trigger it."

That is not a capability gap, it is a **legibility** gap, and it is exactly what
combat-AI games solved decades ago. So the highest-value borrows are not new
behaviours — they are ways to make existing behaviour readable.

## The table

| Source | Mechanic | Plugs into | Cost | Verdict |
|---|---|---|---|---|
| **F.E.A.R. / Halo** | Squad *barks*: enemies announce intent out loud ("flanking left") | `TacticalEffects.roleCue`, existing 30-tactic mask | Low | **SHIPPED** — `TacticalBarks` |
| **Thief / Splinter Cell** | Three-state awareness — unaware → suspicious → alert, each with a tell | `VisibilityRules`, `Squad.lastKnownPos` | Low | **SHIPPED** — `PerceptionCues` |
| **Shadow of Mordor** | Nemesis *adaptation*: a survivor returns changed by how it nearly died | `IllagerGrudge` (already persists named survivors) | Low | **SHIPPED** — `IllagerScar` |
| **Shadow of Mordor** | Reputation → pre-emptive fear; grunts flee a player who slaughtered their army | `FactionReputation` heat, `Squad.morale` | Low | **Take** |
| **Left 4 Dead** | Boomer bile: a mark that *summons* the horde onto you | `Squad.alertTo`, `broadcastDistress`, `WitchSupportGoal` | Low | Strong |
| **XCOM** | Overwatch: a shooter holds its shot for the moment you break cover | `KiteGoal`, marksman role, ranged tuning | Medium | Strong |
| **XCOM** | Flanking actually *rewards* the flanker | `FlankGoal` (exists but is currently cosmetic) | Low | Strong |
| **Left 4 Dead** | Hunter pin: you are held until a teammate frees you | `MultiplayerDirector` | Medium | Careful |
| **Monster Hunter** | Wounded monster flees and leaves a trackable trail | `RetreatWhenLowGoal`, bounty hunters | Medium | Nice-to-have |
| **Alien: Isolation** | Director knows where you are; the creature must legitimately find you | — | — | **Already built** |
| **Left 4 Dead** | AI Director pacing (build-up / peak / relax) | — | — | **Already built** |
| **Shadow of Mordor** | Named ranks, grudges, promotion on death | — | — | **Already built** |
| Darkest Dungeon | Stress / afflictions | — | High | Reject: too RPG for vanilla+ |
| Souls games | Stamina, poise, i-frames | — | High | Reject: needs client-side combat feel |
| Total War | Formation facing / morale shock | — | High | Reject: Minecraft crowds are too small and terrain too vertical |

---

## 1. Tactical barks — F.E.A.R., Halo *(shipped)*

F.E.A.R.'s replicas are not much smarter than their contemporaries. They *sound*
smarter, because they narrate: "he's flanking!", "reloading, cover me!". Halo's
grunts panic audibly when an elite dies. Players credit AI with intelligence
proportional to how much of its reasoning they can hear.

Warband has 30 tactics and no cue vocabulary. Assign each tactic family one
recognisable vanilla sound, played on the acting mob at low volume:

| Tactic family | Candidate cue |
|---|---|
| Flank / encircle | `ILLAGER_*` / mob ambient, doubled — a "moving" tell |
| Call backup / distress | `RAID_HORN` (already the faction arrival cue) |
| Retreat / rout | mob hurt sound at low pitch |
| Siege dig | already covered by real block-break cracks |
| Overwatch / hold | bow-draw sound |
| Regroup | short horn |

Rules that keep it from becoming noise: one cue per mob per few seconds, radius-
limited, and **never** on a tactic the player cannot see the consequence of. Pair
with `debugTacticLogs` so cue coverage is auditable.

Cost is genuinely low — the goals already call `logTactic(...)` at exactly the right
moments, so the hook points exist. This is the single biggest
perceived-intelligence-per-line change available.

## 2. Three-state awareness — Thief *(shipped)*

`VisibilityRules` already reduces detection for crouching, invisible and
darkness-affected targets. **Players have no way to know that exists**, so stealth is
an invisible mechanic and nobody plays around it.

### The constraint: the player is hiding

Every other cue in the mod can assume the player is looking at the fight. This one
cannot. A hiding player is potentially behind cover, in the dark, at range, and facing
the wrong way. That single fact eliminates most of the toolkit before design starts —
**anything that requires seeing the mob is unreliable exactly when it matters most.**

### Two different questions, often conflated

| | Question | Shape | Genre solution |
|---|---|---|---|
| **A** | "Has *that* mob noticed me?" | per-mob, positional | mob animation + barks |
| **B** | "Am I hidden *right now*?" | player-global, continuous | Thief's light gem, Splinter Cell's meter |

These need different channels. Conflating them is why stealth feedback usually ends up
as a HUD element — but B is also the one that costs the most vanilla feel.

### Every channel, and whether it survives the constraint

| Channel | Through walls? | In darkness? | At range? | Verdict for awareness |
|---|---|---|---|---|
| Positional sound (`playSound`) | **yes** | **yes** | ~16 blocks, volume-scaled | **Primary.** The only channel that works when the player cannot see |
| Sound *pitch* as a scalar | yes | yes | yes | **Use.** Free way to encode escalation without new sounds |
| Mob stops moving | no | no | yes | **Primary visual.** Unmistakable, diegetic, costs nothing |
| Mob head-turn toward stimulus (`getLookControl`) | no | no | yes | **Use.** The classic "it heard something" |
| `setAggressive(boolean)` | no | no | yes | Marginal — drives some vanilla anim/AI; worth testing |
| Particles above the mob | no | **yes** (self-lit) | yes | **Use sparingly.** Good escalation accent |
| Action-bar text | n/a | n/a | n/a | For **B** only, and only on transition. Spammy per-tick |
| Boss bar (`ServerBossEvent`) | n/a | n/a | n/a | **Reject** — see below |
| Glowing on the mob | yes | yes | yes | **Reject** — too gamey, and already the bounty-mark language |
| Glowing on the **player** | — | — | — | **Actively broken:** `VisibilityRules:46` treats GLOWING as bypassing every detection penalty, so this would delete stealth while trying to explain it |
| Name tag / `?` icon | yes | yes | yes | **Reject** — `IllagerIdentity` deliberately sets `setCustomNameVisible(false)`, and floating text through walls is the exact complaint already received about unreadable illager names |
| Equipment / scale change | no | no | yes | Too slow and heavy for a transient state |
| Title / subtitle | n/a | n/a | n/a | Far too intrusive for a repeating state |

### The recommendation

**Suspicious** — the mob *stops*, *turns toward* the stimulus, plays a short
questioning sound (pitch-shifted ambient), and after ~1s either escalates or
de-escalates. The stop-and-turn is the tell for a player who can see; the sound is the
tell for one who cannot. Both, always, because either may be unavailable.

**Alert** — normal targeting, a sharper/louder cue, plus the existing
`Squad.alertTo` share so the alarm spreads.

**Lost you** — *the state most implementations forget, and the one that makes stealth
playable.* A distinct "gave up" sound on the alert → unaware transition. Without it a
player never learns they got away, so hiding has tension but no **resolution**, and
the whole loop feels broken rather than tense. Thief and Metal Gear both spend a
signature sound here. This should be the first thing built, not the last.

Question **B** ("am I hidden?") should then be *inferable* rather than displayed: if
you can hear mobs going suspicious instead of alert when you crouch, you learn the
rule by playing. That teaches `VisibilityRules` without a tooltip and without a HUD.

### Why not a detection meter

A boss bar is the only true HUD available server-side, and a Thief-style light gem is
the textbook solution to question B. It is still the wrong call here: it would be the
single most un-vanilla element in the mod, it permanently occupies the boss-bar slot
that a siege or a nemesis fight has a better claim to, and it replaces a skill
(reading the world) with a readout. Worth a config flag at most, off by default.

### Spam control

Awareness cues inform *one player*, so they must be throttled **per player**, not per
mob — otherwise 24 smart mobs produce 24 simultaneous "?" noises. Rules:

- only the **nearest** mob in a transition barks,
- only on state **transitions**, never while in a state,
- one cue per player per ~1.5s,
- and the "lost you" cue fires once per encounter, not per mob.

This differs from `TacticalBarks`, which throttles per mob because it is describing
what that specific mob is doing. Reusing that throttle here would be a bug.

## 3. Nemesis adaptation — Shadow of Mordor *(shipped)*

The nemesis system's core loop is not naming enemies — Warband already names them.
It is **enemies that return changed by the specific way they beat or nearly lost to
you**. Warband already persists a named survivor in `IllagerGrudge` when one escapes.
Add one field: what nearly killed it.

- Nearly died to fire → returns with Fire Resistance, or wearing a helmet
- Nearly died to bow → returns with a shield
- Nearly died in melee → returns with knockback resistance and reach
- Escaped by fleeing → returns faster, and with allies

Then say so when it arrives: *"Yorn of the Ash Banner returns, scarred and shielded."*
Warband already has the arrival message, the name, and the equipment plumbing
(`IllagerLoadout`). This is a handful of fields and one switch, and it produces the
strongest story beats in the genre.

## 4. Reputation fear — Shadow of Mordor

Faction heat currently only escalates: more patrols, bounty hunters, crusades. In
Shadow of Mordor, notoriety cuts *both* ways — weak enemies flee the player who
butchered their army.

`FactionReputation.heat` and `Squad.morale` already exist, as does `RetreatWhenLowGoal`
and the rout state. At high heat, low-difficulty members of that faction should break
and run on sight. It costs almost nothing, it makes heat legible in a *second* way,
and it hands the player a power fantasy the mod currently only ever takes away.

---

## Deliberate rejects

- **Stamina / poise / i-frames** (Souls). Combat feel needs client-side timing
  feedback. Server-side approximations feel like lag, not weight.
- **Stress and afflictions** (Darkest Dungeon). Interesting, but it is an RPG layer
  on a mod whose pitch is vanilla+.
- **Formation facing and morale shock** (Total War). Minecraft engagements are 6–14
  mobs in vertical, cluttered terrain. Formation facing needs open ground and scale.
- **Anything with a custom HUD.** Boss bars are the only real HUD available, and they
  should be spent on something worth a permanent bar — a siege in progress, or a
  named nemesis fight — not on a threat meter.

## Suggested order

1. ~~**Barks**~~ — **shipped.** Six families (advance, circle, withdraw, rally, lunge,
   search) hung off the existing tactic-announcement call, so cue coverage and debug
   coverage cannot drift apart. The `Tactic` switch is exhaustive with no default, so
   any future tactic must make an explicit bark-or-silent decision at compile time.
2. ~~**Nemesis adaptation**~~ — **shipped** as `IllagerScar`. The scar is read from how
   the survivor watched its *ally* die, which was the data already in hand at witness
   time and the better fiction besides. Every adaptation is functional rather than
   cosmetic, and the arrival message names what changed so the player can connect it
   to their own past tactics. Codec field is optional, so pre-1.4.0 saves keep their
   survivors.
3. ~~**Awareness states**~~ — **shipped** as `PerceptionCues` + `SuspicionPauseGoal`.
   Built lost-you-first as argued below. The suspicion trigger is
   `VisibilityRules.concealedNearMiss`: in sight and inside the unmodified follow range,
   but outside the range crouching or darkness cut it to — so the cue fires precisely
   when concealment saved the player, which is what teaches the rule.
4. **Reputation fear** — nearly free once heat is already tracked.
5. Overwatch and flank rewards — give two existing tactics actual teeth.

Everything above rides on systems already shipped. That is the filter: borrow what
plugs into the squad blackboard, the tactic mask, the difficulty scalar, or faction
heat. Anything needing a new subsystem is a different project.
