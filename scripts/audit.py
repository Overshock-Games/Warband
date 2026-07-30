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
import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
MAIN = ROOT / "src" / "main" / "java"
GOALS = MAIN / "com" / "warband" / "ai" / "goal"
MIXINS = MAIN / "com" / "warband" / "mixin"
COORDINATOR = MAIN / "com" / "warband" / "ai" / "SquadCoordinator.java"
CONFIG = MAIN / "com" / "warband" / "config" / "WarbandConfig.java"
LANG = ROOT / "src" / "main" / "resources" / "assets" / "warband" / "lang" / "en_us.json"

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


# --------------------------------------------------------------- displayed text
# Operator diagnostics stay literal on purpose — see WarbandCommand's class javadoc.
LITERAL_TEXT_EXEMPT = {"command"}
# Two real words next to each other. A command name, a key fragment or a separator is not prose.
_PROSE = re.compile(r"[A-Za-z]{2,}\s+[A-Za-z]{2,}")
# Strings shaped like a key but that name a file, not a translation.
NOT_KEY_SUFFIXES = {"properties", "json", "txt", "log", "jar", "accesswidener", "toml"}


def _literal_args(src: str):
    """Yields (argument_source, line) for every Component.literal(...) call."""
    for m in re.finditer(r"Component\.literal\(", src):
        i, depth = m.end(), 1
        while i < len(src) and depth:
            if src[i] == '"':  # skip over string literals, including escapes
                i += 1
                while i < len(src) and src[i] != '"':
                    i += 2 if src[i] == "\\" else 1
            elif src[i] == "(":
                depth += 1
            elif src[i] == ")":
                depth -= 1
                if not depth:
                    break
            i += 1
        yield src[m.end():i], src[: m.start()].count("\n") + 1


def check_hardcoded_display_text() -> None:
    """Component.literal("some English") shipped to a player cannot be translated.

    Warband is server-side, so a player is normally on a vanilla client that has never
    heard of this mod: Component.translatable alone would show them the raw key. The fix
    is always translatableWithFallback(key, english) — the client renders whichever it
    can. Public review of 1.4.0 put it plainly: "You should never hardcode displayed
    text ... otherwise your mod becomes pain in the ass to translate."

    Only Component.literal is checked, because that is the sink. A bare English string
    passed around as a String is not yet a bug; it becomes one here.
    """
    for f in java_files():
        if f.parent.name in LITERAL_TEXT_EXEMPT:
            continue
        src = f.read_text(encoding="utf-8")
        for arg, line in _literal_args(src):
            texts = re.findall(r'"((?:\\.|[^"\\])*)"', arg)
            # A command name is an identifier, not prose — "/warband intel" stays literal.
            if any(t.startswith("/") for t in texts):
                continue
            prose = [t for t in texts if _PROSE.search(t)]
            # Gluing a literal onto a value bakes English word order in, even when neither
            # half is prose on its own: "Warmarshal " + name.
            glued = "+" in arg and any(t.strip() for t in texts)
            if not prose and not glued:
                continue
            shown = (prose[0] if prose else next(t for t in texts if t.strip()))[:48]
            detail = "concatenated display text" if glued and not prose else "hardcoded display text"
            errors.append(
                f"{detail}: {f.relative_to(ROOT)}:{line} -> "
                f'"{shown}" (use Component.translatableWithFallback with %1$s args)'
            )


def check_lang_keys_resolve() -> None:
    """Every warband.* key used in code should exist in en_us.json, and vice versa.

    The fallback means a missing key is invisible in English, so this would otherwise only
    surface as an untranslatable string in somebody else's language.
    """
    if not LANG.exists():
        errors.append("lang: assets/warband/lang/en_us.json is missing")
        return
    try:
        keys = set(json.loads(LANG.read_text(encoding="utf-8")))
    except json.JSONDecodeError as exc:
        errors.append(f"lang: en_us.json does not parse ({exc})")
        return

    used: set[str] = set()
    prefixes: set[str] = set()
    for f in java_files():
        src = f.read_text(encoding="utf-8")
        for key in re.findall(r'"(warband\.[\w.]+)"', src):
            # "warband.properties" and friends are filenames that happen to match the shape.
            if key.rsplit(".", 1)[-1] in NOT_KEY_SUFFIXES:
                continue
            # A trailing dot means the key is completed at runtime from an id or enum name.
            (prefixes if key.endswith(".") else used).add(key)

    for key in sorted(used - keys):
        errors.append(f"lang: '{key}' is used in code but missing from en_us.json")
    for prefix in sorted(prefixes):
        if not any(k.startswith(prefix) for k in keys):
            errors.append(f"lang: no key starts with '{prefix}'")
    for key in sorted(keys - used):
        if any(key.startswith(p) for p in prefixes):
            continue
        warnings.append(f"lang: '{key}' is defined but never used")


# ----------------------------------------------------------- per-mod id hardcoding
def check_hardcoded_mod_ids() -> None:
    """Matching another mod's registry ids in Java only ever recognises that one mod.

    Tags are the vanilla answer, and `"required": false` lets a shipped tag name entity
    types from mods that are not installed. Public review of 1.4.0 said exactly this:
    "Tags would probably be useful here."
    """
    known = {"minecraft", "warband", "c", "fabric"}
    for f in java_files():
        src = f.read_text(encoding="utf-8")
        namespaces = {
            m.group(1)
            for m in re.finditer(r'"([a-z][a-z0-9_]{2,})"\s*\.equals\(\s*\w+\.getNamespace\(\)', src)
        }
        namespaces |= {m.group(1) for m in re.finditer(r'MOD_ID\s*=\s*"([a-z][a-z0-9_]{2,})"', src)}
        foreign = namespaces - known
        # A plain isModLoaded() presence check is fine; keying off ids is what tags replace.
        if not foreign or "getPath()" not in src:
            continue
        warnings.append(
            f"per-mod id matching: {f.relative_to(ROOT)} keys off "
            f"{', '.join(sorted(foreign))} registry paths — prefer an entity_type tag so "
            f"other mods work without a code change"
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
        check_hardcoded_display_text,
        check_lang_keys_resolve,
        check_hardcoded_mod_ids,
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
