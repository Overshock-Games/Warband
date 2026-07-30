---
name: warband-audit
description: Run the Warband codebase audit and interpret its findings. Use before any release, before committing a batch of behaviour changes, after adding or reprioritising a mob goal, after touching a mixin, and after adding a config key. Also use when asked to check code quality, look for dead code, or find goal-priority conflicts.
---

# Warband audit

```bash
python scripts/audit.py            # full report
python scripts/audit.py --quiet    # errors only, for a pre-commit gate
```

Exit code 1 on any ERROR. WARNINGs never fail the run — they are judgement calls.

**Run it before every release, and after any change to goals, mixins, or config.** It is
not a general linter; every rule exists because that exact mistake shipped at least once.

## ERRORS — fix before shipping

| Rule | Why it exists |
|---|---|
| Unused imports | Three shipped in 1.4.0, all left by edits that deleted the last usage. "Looks like the code was never opened in an IDE" was public feedback. |
| Unused private constants | Dead tuning knobs read as live config and mislead the next reader. |
| Mixin helper without `@Unique` | A `warband$` prefix is convention only; `@Unique` makes the compiler enforce it. Shipped without it in 1.4.0. |
| Config key/arg misalignment | `WarbandConfig.toPropertiesString()` interpolates ~100 args positionally. A mismatch throws `MissingFormatArgumentException` **at runtime**, so the config silently stops saving — a bug users actually reported. |
| Hardcoded display text | `Component.literal("some English")` cannot be translated. Public feedback on 1.4.0: *"You should never hardcode displayed text ... otherwise your mod becomes pain in the ass to translate."* |
| Concatenated display text | `"Warmarshal " + name` bakes English word order in even when both halves are translatable. |
| Lang key missing from `en_us.json` | The fallback hides it in English, so it only ever shows up as untranslatable text in someone else's language. |

## Displayed text

**Always `Component.translatableWithFallback(key, english, args...)`.** Never
`Component.literal` for anything a player reads, and never `Component.translatable` alone.

The fallback is not optional politeness here — Warband is **server-side with no client
mod**, so the player is normally on a vanilla client that has never heard of this mod.
`Component.translatable` alone would show them `warband.rank.captain`. Sending both means a
vanilla client renders the English and a client with a Warband language file renders the
translation, with no branching on either side.

Three rules that follow from it:

- **The join is a key too.** "Bounty Hunter of the Pale Axe" is three pieces of text plus a
  piece of English grammar. Use a pattern key with positional args —
  `warband.name.with_faction` is `"%1$s %2$s of the %3$s"` — so a language that orders those
  differently has something to hook. `WarbandText` holds the shared shapes.
- **Never persist display text, and never parse it back.** `IllagerGrudge` used to store
  `getCustomName().getString()` and recover the mob's name with
  `indexOf(" of the ")`. That was already fragile if a server renamed a mob, and it cannot
  survive translation at all. Store the datum (`personalName`, plus the faction as its own
  field) and compose the display name at render time.
- **Proper nouns stay literal.** "Arvek" is a name, not a string to translate.

Exempt: `com.warband.command` — operator diagnostics whose labels deliberately match the
`EVENT=` names in the logs. `/warband intel` is the exception inside the exception; a normal
player reads it, so it returns components. The audit skips the whole package, so a new
player-facing message added there will not be caught — put it elsewhere.

## Recognising other mods' content

**Use entity-type tags in `data/warband/tags/entity_type/`, not id matching in Java.** Public
feedback on 1.4.0, on a namespace+path `switch` over ten Illager Invasion mobs: *"Tags would
probably be useful here."*

Matching ids in Java recognises exactly the one mod whose ids somebody typed in, and adding
a second one needs a code change and a release. A tag is extensible by any datapack or any
other mod without Warband knowing it exists. Every shipped entry needs
`{ "id": "...", "required": false }` — that is what lets a tag name an entity type from a mod
that is not installed without failing tag load.

`IllagerInvasionCompat` is what is left over when this is done properly: a bare
`isModLoaded()` check, which is a legitimate question. `IllagerKinds` holds the tag-driven
predicates.

Two things to keep in mind:

- **Keep vanilla working by class as well.** `isIllagerLike` still tests
  `instanceof AbstractIllager` before the tag, so a broken or `"replace": true` datapack
  cannot switch the mod off by accident.
- **Widening a tag changes gameplay.** Vanilla evokers are deliberately *not* in
  `illager_support`, and the seat-boss check is still gated on `isModLoaded()`. Adding
  vanilla types to those tags would silently reassign roles. A tag refactor should be
  behaviour-neutral unless the change is the point.

## WARNINGS — each needs a deliberate answer, not a reflex

### Goal priority ties (the important one)

**This is the recurring bug class in this codebase.** The goal selector only lets a
*strictly* higher-priority goal take a flag from a running one, so two goals at the same
priority holding `MOVE` cannot preempt each other — whichever started first keeps control
indefinitely.

Every one of these was that same fault:

- creepers ignoring cats — `RegroupGoal` tied vanilla's `AvoidEntityGoal` at 3
- spiders never biting — `CeilingCrawlGoal` tied vanilla's `SpiderAttackGoal` at 4
- a mob frozen mid-stare unable to flee a creeper — `SuspicionPauseGoal` tied
  `DreadAvoidGoal` at 1

A tie is **acceptable** when the goals are mutually exclusive in practice — different mob
families, or one requires a target and the other requires none. It is **a bug** when both
can be eligible for the same mob at the same moment.

When adding or moving a goal, check vanilla's priorities for that mob family too, not just
Warband's. Read them from the real class rather than memory:

```bash
javap -p -c -classpath <deobf-mc-jar> net.minecraft.world.entity.monster.Creeper \
  | sed -n '/registerGoals/,/^  [a-z]/p' | grep -E "iconst_|class net/minecraft"
```

### Flagless goal moving the mob

A goal with no flags never competes for `MOVE`, which is usually why it was written that
way — but it also gets no arbitration, so it must yield on its own terms.
`ClimbToTargetGoal` shipped exactly this bug: it overrode a spider's movement every tick
while the vanilla attack goal was trying to use it. If a flagless goal writes
`setDeltaMovement` or calls `moveTo`, it needs an explicit proximity or state yield.

### Hardcoded mixin arg index

`@ModifyArg(index = N)` encodes a hand-written assumption about a target descriptor, and
`mixins.json` uses `defaultRequire: 1`, so a changed signature is **fatal at load** rather
than a warning. Re-verify against the real descriptor on every Minecraft update —
the local porting notes track these (`docs/` is untracked — working notes, not shipped).

### Inline fully-qualified names

`com.warband.x.Y.z()` written inline instead of imported. Cosmetic, but it reads as
unfinished and drew public comment. Pre-existing instances are fine to leave; do not add
new ones.

### Possibly unused private method

Heuristic — it counts `name(` calls and `::name` references, so it can still miss reflective
or mixin-injected use. Confirm before deleting.

Note it only looks at **private** members. `IllagerInvasionCompat.isSkirmisher` and
`isBruiser` were public with zero callers and no check caught them; both are gone.

### Per-mod id matching

See "Recognising other mods' content" above. Warns rather than fails, because a genuine
`isModLoaded()` presence check is fine — what it is looking for is a foreign namespace being
matched together with `getPath()`.

### Unused lang key

Reported as a warning, not an error: a key can be legitimately staged ahead of the code that
uses it. Keys built at runtime from an id or an enum name (`"warband.rank." + id`) are
resolved by prefix, so they are not reported.

## What the audit cannot catch

It is static analysis on a behaviour mod. It says nothing about whether a tactic *fires*,
whether the AI reads as intelligent, or whether tuning feels right. For those:

- `debugTacticLogs=true` emits a machine-readable `EVENT=` trace; `grep "EVENT="` on the log
- the local test plan under `docs/` lists the cases, split into `[LOG]` (verifiable from a
  log) and `[EYES]` (needs a human). That directory is untracked, so it may be absent in a
  fresh clone
- ratios matter: 26 `SIEGE_DIG_START` against 3 `SIEGE_BLOCK_REMOVED` is how the
  reachability bug was found, and no static check would have surfaced it

A clean audit means the code is tidy, not that the mod works.
