#!/usr/bin/env python3
"""Warband codebase audit.

Mechanical checks for the specific mistakes this codebase actually makes, rather than a
generic linter. Every rule here exists because it shipped a real bug or a real piece of
sloppiness at least once.

    python scripts/audit.py            # report
    python scripts/audit.py --quiet    # errors only

Exit code 1 if any ERROR is found. WARNINGs are judgement calls and never fail the run.
"""
from __future__ import annotations

import argparse
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
MAIN = ROOT / "src" / "main" / "java"
GOALS = MAIN / "com" / "warband" / "ai" / "goal"
MIXINS = MAIN / "com" / "warband" / "mixin"
COORDINATOR = MAIN / "com" / "warband" / "ai" / "SquadCoordinator.java"
CONFIG = MAIN / "com" / "warband" / "config" / "WarbandConfig.java"

errors: list[str] = []
warnings: list[str] = []


def strip_comments_and_strings(src: str) -> str:
    """Crude but adequate: removes block/line comments and string literals."""
    src = re.sub(r"/\*.*?\*/", "", src, flags=re.S)
    src = re.sub(r"//[^\n]*", "", src)
    src = re.sub(r'"(?:\\.|[^"\\])*"', '""', src)
    return src


def java_files() -> list[pathlib.Path]:
    return sorted(MAIN.rglob("*.java"))


# ---------------------------------------------------------------- unused imports
def check_unused_imports() -> None:
    """Shipped three of these in 1.4.0, all from edits that deleted the last usage."""
    for f in java_files():
        src = f.read_text(encoding="utf-8")
        body = strip_comments_and_strings(re.sub(r"^import .*$", "", src, flags=re.M))
        for m in re.finditer(r"^import (?:static )?[\w.]+\.(\w+);$", src, re.M):
            name = m.group(1)
            if name == "*":
                continue
            if not re.search(r"\b" + re.escape(name) + r"\b", body):
                errors.append(f"unused import: {f.relative_to(ROOT)} -> {name}")


# ------------------------------------------------------------ unused private members
def check_unused_private_members() -> None:
    """Dead private helpers and constants. MOB_STACK_CLIMB's helpers lingered this way."""
    for f in java_files():
        src = f.read_text(encoding="utf-8")
        body = strip_comments_and_strings(src)

        for m in re.finditer(r"private (?:static )?(?:final )?[\w<>\[\],. ?]+ (\w+)\s*\(", body):
            name = m.group(1)
            calls = len(re.findall(r"\b" + re.escape(name) + r"\s*\(", body))
            # A method reference is a usage too. Counting only "name(" call sites flagged
            # every command handler and every event callback in the codebase as dead.
            refs = len(re.findall(r"::\s*" + re.escape(name) + r"\b", body))
            if calls + refs <= 1:
                warnings.append(f"possibly unused private method: {f.relative_to(ROOT)} -> {name}()")

        for m in re.finditer(r"private static final [\w<>\[\],. ]+ ([A-Z][A-Z0-9_]*)\s*=", body):
            name = m.group(1)
            if len(re.findall(r"\b" + re.escape(name) + r"\b", body)) <= 1:
                errors.append(f"unused private constant: {f.relative_to(ROOT)} -> {name}")


# ------------------------------------------------------------------- mixin hygiene
def check_mixin_unique() -> None:
    """A warband$ prefix is convention; @Unique makes the compiler enforce it.

    Only non-injector helpers need it — mixin already owns @Inject/@ModifyArg/@Redirect
    handler methods.
    """
    injector = re.compile(r"@(Inject|ModifyArg|ModifyArgs|ModifyVariable|ModifyReturnValue|Redirect|WrapOperation|WrapWithCondition|Overwrite)")
    for f in sorted(MIXINS.rglob("*.java")):
        src = f.read_text(encoding="utf-8")
        for m in re.finditer(r"(?:@[\w()\"$., =\-]+\s+)*private [\w<>\[\],. ?]+ (\w+)\s*\(", src):
            name = m.group(1)
            preceding = src[max(0, m.start() - 400):m.start()]
            # Look only at the annotation block immediately above this declaration.
            block = preceding.rsplit("}", 1)[-1]
            if injector.search(block):
                continue
            if "@Unique" in block:
                continue
            errors.append(
                f"mixin helper without @Unique: {f.relative_to(ROOT)} -> {name}() "
                f"(prefix alone is not enforced)"
            )


def check_mixin_hardcoded_indexes() -> None:
    """@ModifyArg index= is a hand-written descriptor assumption, fatal at load if wrong."""
    for f in sorted(MIXINS.rglob("*.java")):
        src = f.read_text(encoding="utf-8")
        for m in re.finditer(r"index\s*=\s*(\d+)", src):
            warnings.append(
                f"hardcoded mixin arg index: {f.relative_to(ROOT)} -> index={m.group(1)} "
                f"(re-verify against the target descriptor on every MC update)"
            )


# ----------------------------------------------------------- fully-qualified names
def check_inline_qualified_names() -> None:
    """`com.warband.x.Y.z()` inline instead of an import. Reads as unfinished."""
    for f in java_files():
        src = strip_comments_and_strings(f.read_text(encoding="utf-8"))
        src = re.sub(r"^import .*$", "", src, flags=re.M)
        hits = re.findall(r"\bcom\.warband\.[\w.]+\.[A-Z]\w+", src)
        if hits:
            distinct = sorted(set(hits))
            shown = ", ".join(t.rsplit(".", 1)[-1] for t in distinct[:4])
            more = f" +{len(distinct) - 4} more" if len(distinct) > 4 else ""
            # One line per file, not per occurrence — 40 near-identical warnings buried
            # the findings that actually mattered.
            warnings.append(
                f"inline qualified names: {f.relative_to(ROOT)} "
                f"({len(hits)} refs: {shown}{more}) — import instead"
            )


# ----------------------------------------------------------------- config integrity
def check_config_alignment() -> None:
    """The properties template and its .formatted() args must line up by name.

    A mismatch throws MissingFormatArgumentException at runtime, not compile time, so the
    config silently fails to save — which is exactly a bug users reported.
    """
    src = CONFIG.read_text(encoding="utf-8")
    try:
        start = src.index('return """')
        mid = src.index('""".formatted(', start)
        end = src.index("\n    }", mid)
    except ValueError:
        errors.append("config: could not locate the properties template")
        return

    body, blob = src[start:mid], src[mid + len('""".formatted('):end]
    keys = [
        m.group(1)
        for line in body.splitlines()
        if not line.strip().startswith("#")
        and (m := re.match(r"([A-Za-z0-9_]+)=(%[sd])\s*$", line.strip()))
    ]

    depth, args, cur = 0, [], ""
    for ch in blob:
        if ch in "([":
            depth += 1
        elif ch in ")]":
            if depth == 0:
                break
            depth -= 1
        if ch == "," and depth == 0:
            args.append(cur.strip())
            cur = ""
        else:
            cur += ch
    if cur.strip().rstrip(");").strip():
        args.append(cur.strip().rstrip(");").strip())
    args = [a for a in args if a]

    if len(keys) != len(args):
        errors.append(f"config: {len(keys)} template keys but {len(args)} format args")
    for key, arg in zip(keys, args):
        if key != arg:
            errors.append(f"config: key '{key}' is fed by field '{arg}'")


# -------------------------------------------------------------- goal flag conflicts
def goal_flags() -> dict[str, set[str]]:
    """Which goal classes hold which goal-selector flags."""
    flags: dict[str, set[str]] = {}
    for f in sorted(GOALS.rglob("*.java")):
        src = f.read_text(encoding="utf-8")
        name = f.stem
        m = re.search(r"setFlags\(EnumSet\.(\w+)\(([^)]*)\)\)", src)
        if m:
            if m.group(1) == "noneOf":
                flags[name] = set()
            else:
                flags[name] = set(re.findall(r"Flag\.(\w+)", m.group(2)))
        elif "extends SquadGoal" in src:
            flags[name] = {"MOVE"}  # SquadGoal's default
        else:
            flags[name] = set()
    return flags


def check_goal_priority_ties() -> None:
    """Two goals at the same priority holding the same flag cannot preempt each other.

    This is *the* recurring bug class in this codebase: creepers ignoring cats, spiders
    never biting, and a mob frozen mid-stare unable to flee a creeper were all this.
    Ties are sometimes fine when the goals are mutually exclusive by mob type, so this
    warns rather than fails — but every tie deserves a deliberate answer.
    """
    src = strip_comments_and_strings(COORDINATOR.read_text(encoding="utf-8"))
    flags = goal_flags()
    by_priority: dict[int, list[str]] = {}
    for m in re.finditer(r"addGoal\((\d+),\s*new (\w+)\(", src):
        by_priority.setdefault(int(m.group(1)), []).append(m.group(2))

    for priority, names in sorted(by_priority.items()):
        movers = sorted({n for n in names if "MOVE" in flags.get(n, set())})
        if len(movers) > 1:
            warnings.append(
                f"goal priority {priority}: {len(movers)} MOVE-holding goals tie "
                f"({', '.join(movers)}) — equal priorities cannot preempt each other"
            )


def check_flagless_goals_moving_mobs() -> None:
    """A flagless goal writing delta movement has no arbitration and must yield itself.

    ClimbToTargetGoal shipped exactly this bug: no flags, so it fought the vanilla attack
    goal for control of a spider every tick.
    """
    flags = goal_flags()
    for f in sorted(GOALS.rglob("*.java")):
        name = f.stem
        if flags.get(name):
            continue
        src = strip_comments_and_strings(f.read_text(encoding="utf-8"))
        if "setDeltaMovement" not in src and "getNavigation().moveTo" not in src:
            continue
        if "HANDOFF" in src or "distanceToSqr" in src:
            continue  # has some explicit proximity yield
        warnings.append(
            f"flagless goal moves the mob without an obvious yield: {f.relative_to(ROOT)} "
            f"({name}) — no goal flag means no arbitration"
        )


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--quiet", action="store_true", help="errors only")
    args = parser.parse_args()

    for check in (
        check_unused_imports,
        check_unused_private_members,
        check_mixin_unique,
        check_mixin_hardcoded_indexes,
        check_inline_qualified_names,
        check_config_alignment,
        check_goal_priority_ties,
        check_flagless_goals_moving_mobs,
    ):
        check()

    if errors:
        print(f"ERRORS ({len(errors)}) — fix before shipping")
        for e in errors:
            print("  " + e)
    if warnings and not args.quiet:
        print(f"\nWARNINGS ({len(warnings)}) — review, may be intentional")
        for w in warnings:
            print("  " + w)
    if not errors and (args.quiet or not warnings):
        print("clean")
    elif not errors:
        print(f"\nno errors ({len(warnings)} warnings)")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
