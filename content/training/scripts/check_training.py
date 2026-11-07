#!/usr/bin/env python3
"""Validate the training content (content/training/SCHEMA.md, A40).

Library mode (default):
    python3 content/training/scripts/check_training.py

Candidate mode (a session composed by Claude or by the engine for one user, A40 §4.4):
    python3 content/training/scripts/check_training.py --candidate session.yaml --request request.yaml

Library checks:
  * closed vocabularies from rules.yaml (level, category, pattern, equipment, place, risk tags, ...);
  * id format and uniqueness; required fields; references (regressions, progressions, swaps,
    template items and alternatives, mode swaps, fitness tests) resolve and are not retired;
  * profile codes: every contraindication is a code from rules.yaml profile_codes;
  * rule M-ALL «mobility everywhere»: every template starts with a warm-up that has a mobility
    item and ends with a cool-down that has a stretch item and ends with stretch or breathing;
    `lighter` never drops the warm-up or the cool-down;
  * exercise level ≤ template level (template level `all` takes classic mobility, stretch,
    breathing and cardio only); exercise equipment ⊆ template equipment;
  * regimes: every template defines BOOST, STEADY and GENTLE; methods not allowed in a regime
    must swap; template RPE fits the level cap; advanced/extreme templates carry the screening
    and consent codes; at least 3 templates per level and 3 mobility templates;
  * block minutes add up to duration_min (±10 %, at least ±3 min);
  * red-line words in every *_ru text (rules.yaml stopwords_ru);
  * TitanPose keyframes (poses.yaml, A40 §6.3): every exercise id exists, frame keys equal
    `animation.poses[].key`, 12 joint angles per frame, knee and elbow within anatomical limits,
    no joint below the floor; coverage of the most used Classic exercises is reported.

Candidate checks: everything above for the one session, plus the request — level, regime cap,
place and equipment, profile codes (contraindications and forbidden risk tags), minutes.

Exit code 0 = no errors (warnings allowed), 1 = errors, 2 = bad input.
"""
from __future__ import annotations

import argparse
import re
import sys
from collections import Counter
from pathlib import Path

import math

import yaml

HERE = Path(__file__).resolve().parent.parent
LEVEL_ORDER = {"classic": 0, "advanced": 1, "extreme": 2}
EX_ID = re.compile(r"^ex-[a-z0-9]+(-[a-z0-9]+)*$")
TPL_ID = re.compile(r"^tpl-(cl|ad|ex|mob)-[a-z0-9]+(-[a-z0-9]+)*$")
GEN_ID = re.compile(r"^gen-[0-9a-f]{8}$")
TPL_PREFIX = {"classic": "cl", "advanced": "ad", "extreme": "ex", "all": "mob"}
EX_REQUIRED = ["id", "name_ru", "name_en", "level", "category", "pattern", "muscles", "equipment", "place",
               "measure", "impact", "risk_tags", "default", "technique_ru", "mistakes_ru", "cues_ru",
               "breathing_ru", "contraindications", "regressions", "progressions", "swaps", "animation"]
TPL_REQUIRED = ["id", "version", "name_ru", "level", "method", "goal", "place", "equipment", "duration_min",
                "intensity", "modes", "lighter", "blocks", "why_ru", "contraindications"]
ALL_LEVEL_CATEGORIES = {"mobility", "stretch", "breathing", "cardio"}
ITEM_KEYS = {"ex", "sets", "reps", "time_s", "distance_m", "per_side", "rest_s", "tempo", "rpe", "zone",
             "note_ru", "alt", "pair"}


class Report:
    def __init__(self):
        self.errors: list[str] = []
        self.warnings: list[str] = []

    def err(self, where: str, msg: str):
        self.errors.append(f"{where}: {msg}")

    def warn(self, where: str, msg: str):
        self.warnings.append(f"{where}: {msg}")


def load(path: Path):
    try:
        return yaml.safe_load(path.read_text(encoding="utf-8"))
    except (OSError, yaml.YAMLError) as e:
        print(f"cannot read {path}: {e}", file=sys.stderr)
        sys.exit(2)


def rpe_max(text) -> float | None:
    nums = [float(x) for x in re.findall(r"\d+(?:\.\d+)?", str(text))]
    return max(nums) if nums else None


def walk_ru(obj, path=""):
    """Yield (path, text) for every string under a key ending in _ru."""
    if isinstance(obj, dict):
        for k, v in obj.items():
            p = f"{path}.{k}" if path else str(k)
            if str(k).endswith("_ru") or k == "ru":
                if isinstance(v, str):
                    yield p, v
                elif isinstance(v, list):
                    for i, s in enumerate(v):
                        if isinstance(s, str):
                            yield f"{p}[{i}]", s
            else:
                yield from walk_ru(v, p)
    elif isinstance(obj, list):
        for i, v in enumerate(obj):
            yield from walk_ru(v, f"{path}[{i}]")


class Checker:
    def __init__(self, rules, exercises, templates, rep: Report):
        self.r = rules
        self.v = {k: set(v) for k, v in rules["vocab"].items()}
        self.tags = set(rules["risk_tags"])
        self.codes = set(rules["profile_codes"])
        self.methods = {m["id"]: m for m in rules["methods"]}
        self.ex = {}
        self.tpl = {}
        self.rep = rep
        self.exercises = exercises
        self.templates = templates
        stems = [re.escape(s) for s in rules.get("stopwords_ru", [])]
        self.stop = re.compile(rf"(?<![а-яёa-z])({'|'.join(stems)})", re.IGNORECASE) if stems else None

    # ------------------------------------------------------------------ helpers
    def in_vocab(self, where, field, values, vocab):
        values = values if isinstance(values, list) else [values]
        for x in values:
            if x not in self.v[vocab]:
                self.rep.err(where, f"{field} «{x}» not in vocab.{vocab}")

    def check_words(self, where, obj):
        if not self.stop:
            return
        for p, text in walk_ru(obj):
            m = self.stop.search(text.lower())
            if m:
                self.rep.err(where, f"red-line word «{m.group(1)}» in {p}: {text!r}")

    # ------------------------------------------------------------------ exercises
    def check_exercises(self):
        ids = Counter(e.get("id") for e in self.exercises)
        for i, dup in ((i, n) for i, n in ids.items() if n > 1):
            self.rep.err(i, f"duplicate exercise id ×{dup}")
        names = Counter(e.get("name_ru") for e in self.exercises)
        for n, c in names.items():
            if c > 1:
                self.rep.err(n, f"duplicate name_ru ×{c}")
        self.ex = {e["id"]: e for e in self.exercises if "id" in e}
        for e in self.exercises:
            w = e.get("id", "?")
            missing = [k for k in EX_REQUIRED if k not in e]
            if missing:
                self.rep.err(w, f"missing fields {missing}")
                continue
            if not EX_ID.match(w):
                self.rep.err(w, "id must match ex-<kebab>")
            self.in_vocab(w, "level", e["level"], "level")
            self.in_vocab(w, "category", e["category"], "category")
            self.in_vocab(w, "pattern", e["pattern"], "pattern")
            self.in_vocab(w, "measure", e["measure"], "measure")
            self.in_vocab(w, "impact", e["impact"], "impact")
            self.in_vocab(w, "equipment", e["equipment"], "equipment")
            self.in_vocab(w, "place", e["place"], "place")
            mus = e["muscles"]
            if not isinstance(mus, dict) or not mus.get("primary"):
                self.rep.err(w, "muscles.primary is required")
            else:
                self.in_vocab(w, "muscles", mus.get("primary", []) + mus.get("secondary", []), "muscles")
            for t in e["risk_tags"]:
                if t not in self.tags:
                    self.rep.err(w, f"risk tag «{t}» not in rules.risk_tags")
            for c in e["contraindications"]:
                if c not in self.codes:
                    self.rep.err(w, f"contraindication «{c}» is not a profile code (rules.profile_codes)")
            if e["impact"] == "high" and "high_impact" not in e["risk_tags"] and "max_speed" not in e["risk_tags"]:
                self.rep.err(w, "impact high needs risk tag high_impact or max_speed")
            if "inversion" in e["risk_tags"] and not {"hypertension", "eye_pressure"} <= set(e["contraindications"]):
                self.rep.err(w, "inversion needs contraindications hypertension and eye_pressure")
            if e["level"] == "extreme" and "screening_not_cleared" not in e["contraindications"]:
                self.rep.err(w, "extreme exercise must list screening_not_cleared")
            if not (2 <= len(e["technique_ru"]) <= 5):
                self.rep.err(w, "technique_ru: 2–5 steps")
            if not e["mistakes_ru"] or not e["cues_ru"]:
                self.rep.err(w, "mistakes_ru and cues_ru must not be empty")
            if not e["name_ru"].strip() or not e["breathing_ru"].strip():
                self.rep.err(w, "name_ru and breathing_ru must not be empty")
            d = e["default"]
            if "sets" not in d:
                self.rep.err(w, "default.sets is required")
            need = {"reps": "reps", "reps_per_side": "reps", "time": "time_s", "time_per_side": "time_s",
                    "distance": "distance_m"}[e["measure"]] if e["measure"] in self.v["measure"] else None
            if need and need not in d:
                self.rep.err(w, f"measure {e['measure']} needs default.{need}")
            if "rpe" in d and rpe_max(d["rpe"]) and rpe_max(d["rpe"]) > 10:
                self.rep.err(w, "default.rpe > 10")
            a = e["animation"]
            for k in ("view", "loop", "props", "poses"):
                if k not in a:
                    self.rep.err(w, f"animation.{k} is required")
            if "view" in a:
                self.in_vocab(w, "animation.view", a["view"], "view")
            self.in_vocab(w, "animation.props", a.get("props", []), "props")
            poses = a.get("poses", [])
            if len(poses) < 2:
                self.rep.err(w, "animation needs ≥ 2 key poses")
            pk = [p.get("key") for p in poses]
            if len(set(pk)) != len(pk) or not all(re.match(r"^[a-z][a-z0-9_]*$", k or "") for k in pk):
                self.rep.err(w, "animation pose keys must be unique snake_case")
            if any(not p.get("ru") for p in poses):
                self.rep.err(w, "every pose needs ru")
            for kind in ("regressions", "progressions", "swaps"):
                for ref in e[kind]:
                    if ref == w:
                        self.rep.err(w, f"{kind} refers to itself")
                    elif ref not in self.ex:
                        self.rep.err(w, f"{kind} → unknown {ref}")
                    elif self.ex[ref].get("retired"):
                        self.rep.err(w, f"{kind} → retired {ref}")
            for ref in e["regressions"]:
                if ref in self.ex and LEVEL_ORDER.get(self.ex[ref]["level"], 0) > LEVEL_ORDER.get(e["level"], 0):
                    self.rep.err(w, f"regression {ref} has a higher level")
            if e["contraindications"] and e["category"] not in ("mobility", "stretch", "breathing") \
                    and not (e["swaps"] or e["regressions"]):
                self.rep.warn(w, "has contraindications but no swaps or regressions")
            self.check_words(w, e)

    # ------------------------------------------------------------------ templates
    def check_session(self, t, where, *, library=True, request=None):
        """Checks shared by library templates and generated candidates."""
        rep = self.rep
        lvl = t["level"]
        self.in_vocab(where, "level", lvl, "template_level")
        self.in_vocab(where, "goal", t["goal"], "goal")
        self.in_vocab(where, "place", t["place"], "place")
        self.in_vocab(where, "equipment", t["equipment"], "equipment")
        m = self.methods.get(t["method"])
        if not m:
            rep.err(where, f"method «{t['method']}» not in rules.methods")
        elif m["level"] != lvl:
            rep.err(where, f"method {t['method']} is level {m['level']}, template is {lvl}")
        for c in t["contraindications"]:
            if c not in self.codes:
                rep.err(where, f"contraindication «{c}» is not a profile code")
        if lvl in ("advanced", "extreme") and "screening_not_cleared" not in t["contraindications"]:
            rep.err(where, f"{lvl} template must list screening_not_cleared")
        if lvl == "extreme" and "extreme_not_consented" not in t["contraindications"]:
            rep.err(where, "extreme template must list extreme_not_consented")
        rpe = rpe_max(t["intensity"].get("rpe"))
        caps = self.r["regime_caps"]["BOOST"]["rpe_max"]
        if rpe is None:
            rep.err(where, "intensity.rpe is required")
        elif lvl in caps and rpe > caps[lvl]:
            rep.err(where, f"intensity rpe {rpe} above level cap {caps[lvl]}")

        blocks = t["blocks"]
        if not blocks:
            rep.err(where, "no blocks")
            return
        types = [b.get("type") for b in blocks]
        for b in blocks:
            self.in_vocab(where, "block.type", b.get("type"), "block_type")
            self.in_vocab(where, "block.format", b.get("format"), "block_format")
        me = self.r["mobility_everywhere"]
        # --- M-ALL: warm-up with mobility first, cool-down with stretch last
        if types[0] != "warmup":
            rep.err(where, "M-ALL: first block must be warmup")
        if types[-1] != "cooldown":
            rep.err(where, "M-ALL: last block must be cooldown")
        if types.count("warmup") != 1 or types.count("cooldown") != 1:
            rep.err(where, "M-ALL: exactly one warmup and one cooldown")
        cats = lambda b: [self.ex[i["ex"]]["category"] for i in b.get("items", []) if i.get("ex") in self.ex]
        for b in blocks:
            if b.get("type") == "warmup":
                if b.get("min", 0) < me["warmup"]["min_minutes"]:
                    rep.err(where, f"M-ALL: warmup shorter than {me['warmup']['min_minutes']} min")
                for c, n in me["warmup"]["min_items_category"].items():
                    if cats(b).count(c) < n:
                        rep.err(where, f"M-ALL: warmup needs ≥ {n} {c} item")
            if b.get("type") == "cooldown":
                if b.get("min", 0) < me["cooldown"]["min_minutes"]:
                    rep.err(where, f"M-ALL: cooldown shorter than {me['cooldown']['min_minutes']} min")
                for c, n in me["cooldown"]["min_items_category"].items():
                    if cats(b).count(c) < n:
                        rep.err(where, f"M-ALL: cooldown needs ≥ {n} {c} item")
                cc = cats(b)
                if cc and cc[-1] not in me["cooldown"]["end_with"]:
                    rep.err(where, "M-ALL: cooldown must end with stretch or breathing")
        lighter = t.get("lighter") or {}
        for blk in lighter.get("drop_blocks", []):
            if blk in me["lighter_may_not_drop"]:
                rep.err(where, f"M-ALL: lighter may not drop {blk}")
            elif blk not in types:
                rep.err(where, f"lighter drops absent block {blk}")
        for exd in lighter.get("drop_exercises", []):
            if exd not in self.ex:
                rep.err(where, f"lighter.drop_exercises → unknown {exd}")
        if lighter.get("rpe_max") and rpe and lighter["rpe_max"] > rpe:
            rep.err(where, "lighter.rpe_max is heavier than the template")
        for k in ("sets_delta", "rounds_delta", "duration_pct", "volume_pct"):
            if k in lighter and lighter[k] > 0:
                rep.err(where, f"lighter.{k} must be ≤ 0")

        # --- minutes
        total = sum(b.get("min", 0) for b in blocks)
        tol = max(3, round(t["duration_min"] * 0.10))
        if abs(total - t["duration_min"]) > tol:
            rep.err(where, f"blocks sum to {total} min, duration_min is {t['duration_min']} (±{tol})")

        # --- items
        tpl_eq = set(t["equipment"]) | {"none"}
        tpl_places = set(t["place"])
        for b in blocks:
            if b.get("format") in ("superset",):
                pairs = Counter(i.get("pair") for i in b.get("items", []))
                if None in pairs or any(n < 2 for n in pairs.values()):
                    rep.err(where, f"superset block items need a shared pair letter (≥ 2 each)")
            if b.get("format") in ("circuit", "amrap") and not (b.get("rounds") or b.get("minutes")):
                rep.err(where, f"{b.get('format')} block needs rounds or minutes")
            if b.get("format") == "emom" and not b.get("minutes"):
                rep.err(where, "emom block needs minutes")
            for it in b.get("items", []):
                unknown = set(it) - ITEM_KEYS
                if unknown:
                    rep.err(where, f"unknown item keys {sorted(unknown)}")
                if "zone" in it:
                    self.in_vocab(where, "zone", it["zone"], "zone")
                for ref in [it.get("ex")] + it.get("alt", []):
                    e = self.ex.get(ref)
                    if not e:
                        rep.err(where, f"item → unknown exercise {ref}")
                        continue
                    if e.get("retired"):
                        rep.err(where, f"item → retired exercise {ref}")
                    if lvl == "all":
                        if e["level"] != "classic" or e["category"] not in ALL_LEVEL_CATEGORIES:
                            rep.err(where, f"{ref} ({e['level']}/{e['category']}) not allowed in an all-level template")
                    elif LEVEL_ORDER[e["level"]] > LEVEL_ORDER.get(lvl, 0):
                        rep.err(where, f"{ref} is {e['level']}, above template level {lvl}")
                    # exercise equipment is any-of: one listed option is enough («none» = no kit needed)
                    if not set(e["equipment"]) & tpl_eq:
                        rep.err(where, f"{ref} needs one of {sorted(e['equipment'])}, template has none of them")
                    if ref == it.get("ex") and not (set(e["place"]) & tpl_places):
                        rep.err(where, f"{ref} fits none of the template places")
                    if ref == it.get("ex") and not set(e["place"]) >= tpl_places and not it.get("alt"):
                        rep.warn(where, f"{ref} misses places {sorted(tpl_places - set(e['place']))} and has no alt")
                e = self.ex.get(it.get("ex"))
                if e:
                    has_amount = any(k in it for k in ("reps", "time_s", "distance_m"))
                    if not has_amount and b.get("format") != "intervals":
                        rep.err(where, f"{it['ex']}: item needs reps, time_s or distance_m")
                    if it.get("per_side") and e["measure"] not in ("reps_per_side", "time_per_side"):
                        rep.warn(where, f"{it['ex']}: per_side on a two-sided exercise")
                    if "rpe" in it and rpe and rpe_max(it["rpe"]) > (caps.get(lvl, 10)):
                        rep.err(where, f"{it['ex']}: item rpe above level cap")
        self.check_words(where, t)

    def check_modes(self, t, where):
        modes = t["modes"]
        for reg in self.v["regime"]:
            if reg not in modes:
                self.rep.err(where, f"modes.{reg} missing")
                continue
            mode = modes[reg]
            if "swap" in mode:
                tgt = self.tpl.get(mode["swap"])
                if not tgt:
                    self.rep.err(where, f"modes.{reg}.swap → unknown {mode['swap']}")
                    continue
                if tgt["id"] == t["id"]:
                    self.rep.err(where, f"modes.{reg} swaps to itself")
                if self.method_allowed(tgt["method"], reg) is False:
                    self.rep.err(where, f"modes.{reg}.swap → {tgt['id']} whose method is not allowed in {reg}")
                tl, ol = tgt["level"], t["level"]
                if tl != "all" and LEVEL_ORDER[tl] > LEVEL_ORDER.get(ol, 0):
                    self.rep.err(where, f"modes.{reg}.swap → higher level {tl}")
            elif mode.get("use") in ("full", "lighter"):
                if self.method_allowed(t["method"], reg) is False:
                    self.rep.err(where, f"method {t['method']} not allowed in {reg}: must swap")
                if reg == "GENTLE" and t["level"] != "all" and t["method"] != "zone2":
                    self.rep.err(where, "GENTLE: only mobility/zone2 templates may stay; others must swap")
                if reg == "GENTLE" and t["method"] == "zone2" and mode["use"] != "lighter":
                    self.rep.err(where, "GENTLE: zone2 must be lighter")
                if reg != "BOOST" and mode["use"] == "full" and t["level"] != "all" and t["method"] != "zone2":
                    rpe = rpe_max(t["intensity"].get("rpe"))
                    cap = self.r["regime_caps"][reg]["rpe_max"].get(t["level"], 10)
                    if rpe and rpe > cap:
                        self.rep.err(where, f"{reg}: full version rpe {rpe} above cap {cap}; use lighter or swap")
                if mode["use"] == "lighter" and reg != "BOOST":
                    cap = self.r["regime_caps"][reg]["rpe_max"].get(t["level"], 10) if t["level"] != "all" else 10
                    lr = (t.get("lighter") or {}).get("rpe_max")
                    if lr and lr > cap:
                        self.rep.err(where, f"{reg}: lighter rpe_max {lr} above cap {cap}")
            else:
                self.rep.err(where, f"modes.{reg} needs use: full|lighter or swap: <template>")

    def method_allowed(self, method, regime):
        allow = self.r["regime_caps"][regime]["allow_methods"]
        if allow == "all_of_level":
            return True
        return method in allow

    def check_templates(self):
        ids = Counter(t.get("id") for t in self.templates)
        for i, n in ids.items():
            if n > 1:
                self.rep.err(i, f"duplicate template id ×{n}")
        self.tpl = {t["id"]: t for t in self.templates if "id" in t}
        per_level = Counter()
        for t in self.templates:
            w = t.get("id", "?")
            missing = [k for k in TPL_REQUIRED if k not in t]
            if missing:
                self.rep.err(w, f"missing fields {missing}")
                continue
            if not TPL_ID.match(w):
                self.rep.err(w, "id must match tpl-<cl|ad|ex|mob>-<kebab>")
            elif w.split("-")[1] != TPL_PREFIX.get(t["level"]):
                self.rep.err(w, f"id prefix does not match level {t['level']}")
            per_level[t["level"]] += 1
            self.check_session(t, w)
            self.check_modes(t, w)
        for lvl in ("classic", "advanced", "extreme", "all"):
            if per_level[lvl] < 3:
                self.rep.err("templates", f"level {lvl} has {per_level[lvl]} templates, need ≥ 3")
        for s in self.r["mobility_everywhere"]["standalone_templates"]:
            if s not in self.tpl:
                self.rep.err("rules.mobility_everywhere", f"unknown template {s}")
        used = {i["ex"] for t in self.templates for b in t["blocks"] for i in b["items"]}
        used |= {a for t in self.templates for b in t["blocks"] for i in b["items"] for a in i.get("alt", [])}
        for e in self.ex:
            if e not in used:
                self.rep.warn(e, "not used in any template (reachable only via swaps or generation)")

    # ------------------------------------------------------------------ rules
    def check_rules(self):
        r = self.r
        for lv in r["levels"]:
            for m in lv["methods"]:
                if m not in self.methods:
                    self.rep.err(f"levels.{lv['id']}", f"unknown method {m}")
                elif self.methods[m]["level"] != lv["id"]:
                    self.rep.err(f"levels.{lv['id']}", f"method {m} belongs to {self.methods[m]['level']}")
        tests = {ft["id"] for ft in r["fitness_tests"]}
        for ft in r["fitness_tests"]:
            if ft["exercise"] not in self.ex:
                self.rep.err(ft["id"], f"unknown exercise {ft['exercise']}")
            for c in ft.get("contraindications", []):
                if c not in self.codes:
                    self.rep.err(ft["id"], f"unknown profile code {c}")
        for lv in r["levels"]:
            t = lv["entry"].get("tests") or {}
            for tid in t.get("all_of", []) if isinstance(t, dict) else []:
                if tid not in tests:
                    self.rep.err(f"levels.{lv['id']}", f"unknown test {tid}")
        for code, spec in r["profile_codes"].items():
            if not re.match(r"^[a-z][a-z0-9_]*$", code):
                self.rep.err(code, "profile code must be snake_case")
            for t in spec.get("forbid_tags", []):
                if t not in self.tags:
                    self.rep.err(code, f"unknown risk tag {t}")
            for p in spec.get("forbid_patterns", []):
                if p not in self.v["pattern"]:
                    self.rep.err(code, f"unknown pattern {p}")
            for m in spec.get("forbid_methods", []):
                if m not in self.methods:
                    self.rep.err(code, f"unknown method {m}")
        for reg, cap in r["regime_caps"].items():
            if reg not in self.v["regime"]:
                self.rep.err("regime_caps", f"unknown regime {reg}")
            for t in cap.get("forbid_tags", []):
                if t not in self.tags:
                    self.rep.err(f"regime_caps.{reg}", f"unknown risk tag {t}")
            if isinstance(cap["allow_methods"], list):
                for m in cap["allow_methods"]:
                    if m not in self.methods:
                        self.rep.err(f"regime_caps.{reg}", f"unknown method {m}")
        for g in r["gates"] + r["modifiers"]:
            for tid in g["then"].get("only_templates", []) + g["then"].get("suggest", []) + \
                    [x for x in g["then"].get("ladder", []) if str(x).startswith("tpl-")] + \
                    ([g["then"]["intervals_to"]] if "intervals_to" in g["then"] else []):
                if tid not in self.tpl:
                    self.rep.err(g["id"], f"unknown template {tid}")
        self.check_words("rules", {k: v for k, v in r.items() if k != "stopwords_ru"})

    # ------------------------------------------------------------------ candidate
    def check_candidate(self, c, req):
        w = c.get("id", "candidate")
        if not GEN_ID.match(str(c.get("id", ""))) and c.get("id") not in self.tpl:
            self.rep.err(w, "candidate id must be gen-<8 hex> or a template id")
        missing = [k for k in TPL_REQUIRED if k not in c and k not in ("version", "modes", "lighter")]
        if missing:
            self.rep.err(w, f"missing fields {missing}")
            return
        self.check_session(c, w, library=False)
        lvl, reg = req["level"], req["regime"]
        if c["level"] != "all" and LEVEL_ORDER[c["level"]] > LEVEL_ORDER[lvl]:
            self.rep.err(w, f"session level {c['level']} above user level {lvl}")
        if not self.method_allowed(c["method"], reg):
            self.rep.err(w, f"method {c['method']} not allowed in {reg}")
        cap = self.r["regime_caps"][reg]
        rpe = rpe_max(c["intensity"].get("rpe"))
        if rpe and rpe > cap["rpe_max"][lvl]:
            self.rep.err(w, f"rpe {rpe} above {reg} cap {cap['rpe_max'][lvl]}")
        codes = set(req.get("profile", []))
        forbid_tags = set(cap.get("forbid_tags", []))
        forbid_patterns, forbid_methods, rpe_caps = set(), set(), []
        for code in codes:
            spec = self.r["profile_codes"].get(code)
            if spec is None:
                self.rep.err(w, f"request profile code {code} unknown")
                continue
            if spec.get("hide_training") or spec.get("program") == "none":
                self.rep.err(w, f"profile {code}: no training session may be offered")
            forbid_tags |= set(spec.get("forbid_tags", []))
            forbid_patterns |= set(spec.get("forbid_patterns", []))
            forbid_methods |= set(spec.get("forbid_methods", []))
            if spec.get("rpe_max"):
                rpe_caps.append(spec["rpe_max"])
            if code in c["contraindications"]:
                self.rep.err(w, f"session lists {code} as a contraindication")
        if c["method"] in forbid_methods:
            self.rep.err(w, f"method {c['method']} forbidden by profile")
        if rpe_caps and rpe and rpe > min(rpe_caps):
            self.rep.err(w, f"rpe {rpe} above profile cap {min(rpe_caps)}")
        if req.get("place") and req["place"] not in c["place"]:
            self.rep.err(w, f"session does not fit place {req['place']}")
        have = set(req.get("equipment", [])) | {"none"}
        if req.get("minutes") and c["duration_min"] > req["minutes"]:
            self.rep.err(w, f"session {c['duration_min']} min longer than {req['minutes']}")
        for b in c["blocks"]:
            for it in b["items"]:
                e = self.ex.get(it["ex"])
                if not e:
                    continue
                if not set(e["equipment"]) & have:
                    self.rep.err(w, f"{e['id']} needs one of {sorted(e['equipment'])}")
                if req.get("place") and req["place"] not in e["place"]:
                    self.rep.err(w, f"{e['id']} not for place {req['place']}")
                bad = set(e["risk_tags"]) & forbid_tags
                if bad:
                    self.rep.err(w, f"{e['id']} has forbidden risk tags {sorted(bad)}")
                if e["pattern"] in forbid_patterns:
                    self.rep.err(w, f"{e['id']} pattern {e['pattern']} forbidden by profile")
                hit = set(e["contraindications"]) & codes
                if hit:
                    self.rep.err(w, f"{e['id']} contraindicated for {sorted(hit)}")

# --------------------------------------------------------------------------- TitanPose (A40 §6.3)
# Same forward kinematics as TitanPose.kt and docs/design/training/src/pose.js.
POSE_LEN = dict(torso=26, neck=3.6, head=5.4, ua=14, fa=13, th=21, sh=21, ft=6)
POSE_FLOOR = 92.0
KNEE_LIMITS = (-10.0, 165.0)
ELBOW_LIMITS = (-10.0, 160.0)
FRONT_VIEWS = {"front", "top"}


def _shortest(a: float, b: float) -> float:
    d = (b - a) % 360.0
    if d > 180.0:
        d -= 360.0
    return d


def _step(o, a, length):
    r = math.radians(a)
    return (o[0] + math.sin(r) * length, o[1] - math.cos(r) * length)


def pose_points(p: dict, front: bool):
    hip = tuple(p["root"])
    sh = _step(hip, p["torso"], POSE_LEN["torso"])
    head_a = p.get("head", p["torso"])
    head = _step(_step(sh, head_a, POSE_LEN["neck"]), head_a, POSE_LEN["head"])
    sw, hw = (6.5, 4.0) if front else (0.0, 0.0)
    pts = [hip, sh, head]
    for key, s in (("arm_n", sw), ("arm_f", -sw)):
        a = p.get(key, p["arm_n"])
        e = _step((sh[0] + s, sh[1]), a[0], POSE_LEN["ua"])
        pts += [e, _step(e, a[1], POSE_LEN["fa"])]
    for key, s in (("leg_n", hw), ("leg_f", -hw)):
        leg = p.get(key, p["leg_n"])
        k = _step((hip[0] + s, hip[1]), leg[0], POSE_LEN["th"])
        an = _step(k, leg[1], POSE_LEN["sh"])
        pts += [k, an, _step(an, leg[2], POSE_LEN["ft"])]
    return pts


def check_poses(path: Path, exercises: list, templates: list, rep: "Report"):
    if not path.exists():
        rep.warn("poses.yaml", "missing: every exercise shows the standing Titan")
        return 0
    data = load(path) or {}
    for key in ("version", "reviewedBy"):
        if key not in data:
            rep.err("poses.yaml", f"missing {key}")
    poses = data.get("poses") or {}
    ex = {e["id"]: e for e in exercises if isinstance(e, dict) and "id" in e}
    for ex_id, frames in poses.items():
        w = f"poses {ex_id}"
        e = ex.get(ex_id)
        if not e:
            rep.err(w, "unknown exercise")
            continue
        if e.get("retired"):
            rep.warn(w, "exercise is retired")
        want = [p.get("key") for p in (e.get("animation") or {}).get("poses") or []]
        if not isinstance(frames, dict):
            rep.err(w, "frames must be a mapping key → pose")
            continue
        missing = [k for k in want if k not in frames]
        extra = [k for k in frames if k not in want]
        if missing:
            rep.err(w, f"no frame for key(s) {missing} (animation.poses)")
        if extra:
            rep.err(w, f"frame key(s) {extra} not in animation.poses")
        front = (e.get("animation") or {}).get("view") in FRONT_VIEWS
        for k, p in frames.items():
            wk = f"{w}.{k}"
            if not isinstance(p, dict):
                rep.err(wk, "pose must be a mapping")
                continue
            shape = {"root": 2, "arm_n": 2, "leg_n": 3, "arm_f": 2, "leg_f": 3}
            bad = [f for f, n in shape.items() if (f in p or f in ("root", "arm_n", "leg_n"))
                   and not (isinstance(p.get(f), list) and len(p[f]) == n and all(isinstance(x, (int, float)) for x in p[f]))]
            if bad or not isinstance(p.get("torso"), (int, float)):
                rep.err(wk, f"needs root[2], torso, arm_n[2], leg_n[3] (optional head, arm_f[2], leg_f[3], spine); bad: {bad or ['torso']}")
                continue
            for leg_key in ("leg_n", "leg_f"):
                leg = p.get(leg_key, p["leg_n"])
                knee = _shortest(leg[0], leg[1])
                if not KNEE_LIMITS[0] <= knee <= KNEE_LIMITS[1]:
                    rep.err(wk, f"{leg_key} knee flexion {knee:.0f}° outside {KNEE_LIMITS}")
            for arm_key in ("arm_n", "arm_f"):
                arm = p.get(arm_key, p["arm_n"])
                elbow = -_shortest(arm[0], arm[1])
                if not ELBOW_LIMITS[0] <= elbow <= ELBOW_LIMITS[1]:
                    rep.err(wk, f"{arm_key} elbow flexion {elbow:.0f}° outside {ELBOW_LIMITS}")
            if abs(p.get("spine", 0)) > 0.5:
                rep.err(wk, "spine bend must be within ±0.5")
            lowest = max(pt[1] for pt in pose_points(p, front))
            if lowest > POSE_FLOOR + 2.5:
                rep.err(wk, f"a joint is {lowest - POSE_FLOOR:.1f} below the floor")
    # Coverage: the most used exercises of Classic and mobility templates should move first.
    use = Counter(it["ex"] for t in templates if t.get("level") in ("classic", "all")
                  for b in t.get("blocks") or [] for it in b.get("items") or [])
    top = [x for x, _ in use.most_common(20)]
    without = [x for x in top if x not in poses]
    if without:
        rep.warn("poses.yaml", f"top-20 Classic exercises without keyframes: {without}")
    return len(poses)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--dir", default=str(HERE), help="content/training directory")
    ap.add_argument("--candidate", help="generated session (YAML or JSON)")
    ap.add_argument("--request", help="request the session answers (YAML or JSON)")
    ap.add_argument("-q", "--quiet", action="store_true", help="hide warnings")
    a = ap.parse_args()
    d = Path(a.dir)
    rules = load(d / "rules.yaml")
    exercises = (load(d / "exercises.yaml") or {}).get("exercises") or []
    templates = (load(d / "templates.yaml") or {}).get("templates") or []
    rep = Report()
    ch = Checker(rules, exercises, templates, rep)
    ch.check_exercises()
    ch.check_templates()
    ch.check_rules()
    n_poses = check_poses(d / "poses.yaml", exercises, templates, rep)
    if a.candidate:
        if not a.request:
            print("--candidate needs --request", file=sys.stderr)
            sys.exit(2)
        lib_errors = len(rep.errors)
        cand = load(Path(a.candidate))
        req = load(Path(a.request))
        ch.check_candidate(cand, req)
        if lib_errors:
            print(f"note: library itself has {lib_errors} errors", file=sys.stderr)
    if not a.quiet:
        for x in rep.warnings:
            print("WARN ", x)
    for x in rep.errors:
        print("ERROR", x)
    n_ex = len(exercises)
    n_tpl = len(templates)
    by = Counter(e.get("level") for e in exercises)
    print(f"\n{n_ex} exercises ({', '.join(f'{k} {v}' for k, v in sorted(by.items()))}), {n_tpl} templates; "
          f"{n_poses} with TitanPose keyframes; {len(rep.errors)} errors, {len(rep.warnings)} warnings")
    sys.exit(1 if rep.errors else 0)


if __name__ == "__main__":
    main()
