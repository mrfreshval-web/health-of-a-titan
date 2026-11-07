#!/usr/bin/env python3
"""Validate morning haiku (content/haiku/STYLE.md, GENERATION.md).

Library mode (default):
    python3 scripts/check_haiku.py
    python3 scripts/check_haiku.py --library content/haiku/library.yaml

Candidate mode (haiku written by Claude when the library runs out for a user):
    python3 scripts/check_haiku.py --candidates out.json --request request.json

Checks:
  * 5-7-5 syllables, counted by Russian vowels (а е ё и о у ы э ю я);
  * three lines, no digits, Latin letters, "!" or "%"; "Титан" is capitalised;
  * id format and uniqueness, mode/tags/image from the closed lists, tag logic;
  * normalised text and every single line unique across the library;
  * stop dictionary (content/haiku/stopwords.yaml): errors reject, warnings are listed;
  * candidate mode also: mode matches the request, tags fit the day, the text and
    lines are new for the library and for this user, the image is not one of the
    last 7 days' images.

Exit code 0 = no errors (warnings allowed), 1 = errors, 2 = bad input.
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from collections import Counter
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parent.parent
DEFAULT_LIBRARY = ROOT / "content/haiku/library.yaml"
DEFAULT_STOPWORDS = ROOT / "content/haiku/stopwords.yaml"

VOWELS = set("аеёиоуыэюя")
PATTERN = (5, 7, 5)
MODES = ("razgon", "ravno", "berezhno")  # DayState.regime: BOOST, STEADY, GENTLE (A09 §2)

CONDITION_TAGS = {"sleep_short", "sleep_good", "recovery_low", "recovery_high", "strain_high"}
DAY_TAGS = {"weekend", "workday"}
SEASON_TAGS = {"season_winter", "season_spring", "season_summer", "season_autumn"}
TAGS = CONDITION_TAGS | DAY_TAGS | SEASON_TAGS | {"any"}
EXCLUSIVE = [
    {"sleep_short", "sleep_good"},
    {"recovery_low", "recovery_high"},
    {"weekend", "workday"},
    SEASON_TAGS,
]
# A09 §2: «Разгон» needs sleep >= 7 h and the night signal at or above the personal baseline.
FORBIDDEN_FOR_MODE = {
    "razgon": {"sleep_short", "recovery_low"},
    "berezhno": {"recovery_high"},
    "ravno": set(),
}

LIB_ID = re.compile(r"^hk-(razgon|ravno|berezhno)-(\d{3})$")
GEN_ID = re.compile(r"^hkg-(razgon|ravno|berezhno)-([0-9a-f]{8})$")
RU = "а-яё"


def normalize(text: str) -> str:
    t = text.lower().replace("ё", "е")
    t = re.sub(rf"[^{RU}\s]", " ", t)
    return " ".join(t.split())


def syllables(line: str) -> int:
    return sum(1 for ch in line.lower() if ch in VOWELS)


def compile_words(items):
    return [(p, re.compile(rf"(?<![{RU}])(?:{p})(?![{RU}])")) for p in items]


def load_stopwords(path: Path):
    data = yaml.safe_load(path.read_text(encoding="utf-8"))
    errors = []
    for category, items in (data.get("errors") or {}).items():
        errors += [(category, p, rx) for p, rx in compile_words(items)]
    warnings = [("warning", p, rx) for p, rx in compile_words(data.get("warnings") or [])]
    return errors, warnings


class Report:
    def __init__(self):
        self.errors: list[str] = []
        self.warnings: list[str] = []

    def err(self, hid, msg):
        self.errors.append(f"ERROR   {hid}: {msg}")

    def warn(self, hid, msg):
        self.warnings.append(f"WARNING {hid}: {msg}")


def check_one(h: dict, rep: Report, images: set, stop_err, stop_warn, id_rx) -> list[str] | None:
    """Checks a single haiku. Returns its three lines or None."""
    hid = str(h.get("id", "<no id>"))
    for key in ("id", "mode", "tags", "image", "text"):
        if key not in h:
            rep.err(hid, f"missing field '{key}'")
    m = id_rx.match(hid)
    if not m:
        rep.err(hid, f"bad id format (expected {id_rx.pattern})")
    mode = h.get("mode")
    if mode not in MODES:
        rep.err(hid, f"unknown mode '{mode}'")
    elif m and m.group(1) != mode:
        rep.err(hid, f"id says '{m.group(1)}' but mode is '{mode}'")

    tags = h.get("tags") or []
    if not isinstance(tags, list) or not tags:
        rep.err(hid, "tags must be a non-empty list")
        tags = []
    for t in tags:
        if t not in TAGS:
            rep.err(hid, f"unknown tag '{t}'")
    if len(set(tags)) != len(tags):
        rep.err(hid, "duplicate tags")
    if "any" in tags and len(tags) > 1:
        rep.err(hid, "'any' must be the only tag")
    for group in EXCLUSIVE:
        if len(group & set(tags)) > 1:
            rep.err(hid, f"mutually exclusive tags: {sorted(group & set(tags))}")
    if mode in FORBIDDEN_FOR_MODE and FORBIDDEN_FOR_MODE[mode] & set(tags):
        rep.err(hid, f"tags {sorted(FORBIDDEN_FOR_MODE[mode] & set(tags))} contradict mode '{mode}' (A09 §2)")

    if h.get("image") not in images:
        rep.err(hid, f"unknown image '{h.get('image')}' (add it to images: first)")

    text = h.get("text")
    if not isinstance(text, str):
        rep.err(hid, "text must be a string")
        return None
    lines = text.split("\n")
    if len(lines) != 3:
        rep.err(hid, f"expected 3 lines, got {len(lines)}")
        return None
    counts = tuple(syllables(x) for x in lines)
    if counts != PATTERN:
        rep.err(hid, f"syllables {counts}, expected {PATTERN}: {text!r}")
    for x in lines:
        if x != x.strip() or not x.strip():
            rep.err(hid, f"empty line or stray spaces: {x!r}")
    if re.search(r"[0-9]", text):
        rep.err(hid, "digits are not allowed (syllables become ambiguous)")
    if re.search(r"[A-Za-z]", text):
        rep.err(hid, "Latin letters are not allowed")
    if "!" in text or "%" in text:
        rep.err(hid, "'!' and '%' are not allowed in haiku")
    if re.search(rf"(?<![{RU}])титан", text):
        rep.err(hid, "write 'Титан' with a capital letter")
    # Capitals follow the sentence: a line starts with a capital only after ". ? …" (or as line 1).
    for i, x in enumerate(lines):
        first = next((ch for ch in x if ch.isalpha()), "")
        sentence_start = i == 0 or lines[i - 1].rstrip().endswith((".", "?", "…"))
        if sentence_start and first and not first.isupper():
            rep.err(hid, f"line {i + 1} starts a sentence but is lower case: {x!r}")
        if not sentence_start and first.isupper() and not x.lstrip().startswith("Титан"):
            rep.err(hid, f"line {i + 1} continues a sentence but starts with a capital: {x!r}")

    norm = normalize(text)
    for category, pattern, rx in stop_err:
        if rx.search(norm):
            rep.err(hid, f"stop word [{category}] /{pattern}/")
    for _, pattern, rx in stop_warn:
        if rx.search(norm):
            rep.warn(hid, f"read by a human: /{pattern}/")
    return lines


def check_library(lib_path: Path, stop_path: Path) -> int:
    lib = yaml.safe_load(lib_path.read_text(encoding="utf-8"))
    stop_err, stop_warn = load_stopwords(stop_path)
    rep = Report()
    for key in ("version", "reviewedBy", "images", "haiku"):
        if key not in lib:
            rep.err("<file>", f"missing top-level key '{key}'")
    images = set((lib.get("images") or {}).keys())
    items = lib.get("haiku") or []

    seen_ids, seen_text, seen_line = {}, {}, {}
    for h in items:
        hid = str(h.get("id"))
        if hid in seen_ids:
            rep.err(hid, "duplicate id")
        seen_ids[hid] = True
        lines = check_one(h, rep, images, stop_err, stop_warn, LIB_ID)
        if not lines:
            continue
        norm = normalize(h["text"])
        if norm in seen_text:
            rep.err(hid, f"same text as {seen_text[norm]}")
        seen_text.setdefault(norm, hid)
        for ln in lines:
            nl = normalize(ln)
            if nl in seen_line and seen_line[nl] != hid:
                rep.err(hid, f"line {ln!r} already used in {seen_line[nl]}")
            seen_line.setdefault(nl, hid)

    print_report(rep)
    by_mode = Counter(h.get("mode") for h in items)
    print(f"\nHaiku: {len(items)} total; " + ", ".join(f"{m} {by_mode.get(m, 0)}" for m in MODES))
    for m in MODES:
        imgs = Counter(h.get("image") for h in items if h.get("mode") == m)
        tags = Counter(t for h in items if h.get("mode") == m for t in h.get("tags") or [])
        print(f"  {m}: {len(imgs)} images (max share {max(imgs.values()) if imgs else 0}); "
              f"tags {dict(sorted(tags.items()))}")
    return finish(rep)


def check_candidates(cand_path: Path, req_path: Path, lib_path: Path, stop_path: Path) -> int:
    lib = yaml.safe_load(lib_path.read_text(encoding="utf-8"))
    stop_err, stop_warn = load_stopwords(stop_path)
    req = json.loads(req_path.read_text(encoding="utf-8"))
    out = json.loads(cand_path.read_text(encoding="utf-8"))
    rep = Report()
    images = set((lib.get("images") or {}).keys())
    lib_texts = {normalize(h["text"]) for h in lib.get("haiku") or []}
    lib_lines = {normalize(x) for h in lib.get("haiku") or [] for x in h["text"].split("\n")}
    shown_texts = {normalize(t) for t in req.get("shown_texts", [])}
    shown_lines = {normalize(x) for t in req.get("shown_texts", []) for x in t.split("\n")}
    recent = set(req.get("recent_images", []))
    day_tags = set(req.get("day_tags", [])) | {"any"}
    shown_ids = set(req.get("shown_ids", []))

    items = out.get("haiku") if isinstance(out, dict) else None
    if not isinstance(items, list) or not items:
        print("ERROR   output must be {\"haiku\": [ ... ]} with at least one item")
        return 1
    batch_texts = set()
    for h in items:
        hid = str(h.get("id", "<no id>"))
        lines = check_one(h, rep, images, stop_err, stop_warn, GEN_ID)
        if h.get("mode") != req.get("mode"):
            rep.err(hid, f"mode '{h.get('mode')}' != requested '{req.get('mode')}'")
        extra = set(h.get("tags") or []) - day_tags
        if extra:
            rep.err(hid, f"tags {sorted(extra)} are not true for this day")
        if h.get("image") in recent:
            rep.err(hid, f"image '{h.get('image')}' was shown in the last 7 days")
        if hid in shown_ids:
            rep.err(hid, "id already shown to this user")
        if not lines:
            continue
        norm = normalize(h["text"])
        if norm in lib_texts or norm in shown_texts or norm in batch_texts:
            rep.err(hid, "text is not new")
        batch_texts.add(norm)
        for ln in lines:
            if normalize(ln) in lib_lines or normalize(ln) in shown_lines:
                rep.err(hid, f"line {ln!r} repeats the library or this user's history")
    print_report(rep)
    print(f"\nCandidates: {len(items)}")
    return finish(rep)


def print_report(rep: Report):
    for line in rep.errors + rep.warnings:
        print(line)


def finish(rep: Report) -> int:
    print(f"Errors: {len(rep.errors)}; warnings: {len(rep.warnings)}")
    return 1 if rep.errors else 0


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--library", type=Path, default=DEFAULT_LIBRARY)
    ap.add_argument("--stopwords", type=Path, default=DEFAULT_STOPWORDS)
    ap.add_argument("--candidates", type=Path, help="JSON output of the generator")
    ap.add_argument("--request", type=Path, help="JSON request that produced the candidates")
    a = ap.parse_args(argv)
    try:
        if a.candidates:
            if not a.request:
                ap.error("--candidates needs --request")
            return check_candidates(a.candidates, a.request, a.library, a.stopwords)
        return check_library(a.library, a.stopwords)
    except (OSError, yaml.YAMLError, json.JSONDecodeError) as e:
        print(f"bad input: {e}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
