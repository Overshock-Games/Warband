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
`docs/PORTING-26.2.md` tracks these.

### Inline fully-qualified names

`com.warband.x.Y.z()` written inline instead of imported. Cosmetic, but it reads as
unfinished and drew public comment. Pre-existing instances are fine to leave; do not add
new ones.

### Possibly unused private method

Heuristic — it counts `name(` calls and `::name` references, so it can still miss reflective
or mixin-injected use. Confirm before deleting.

## What the audit cannot catch

It is static analysis on a behaviour mod. It says nothing about whether a tactic *fires*,
whether the AI reads as intelligent, or whether tuning feels right. For those:

- `debugTacticLogs=true` emits a machine-readable `EVENT=` trace; `grep "EVENT="` on the log
- `docs/TESTING-1.4.0.md` lists the cases, split into `[LOG]` (verifiable from a log) and
  `[EYES]` (needs a human)
- ratios matter: 26 `SIEGE_DIG_START` against 3 `SIEGE_BLOCK_REMOVED` is how the
  reachability bug was found, and no static check would have surfaced it

A clean audit means the code is tidy, not that the mod works.
