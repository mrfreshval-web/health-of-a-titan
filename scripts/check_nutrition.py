#!/usr/bin/env python3
"""Validate the nutrition system content (A41, content/nutrition/SCHEMA.md) and the shared
user-specifics profile (content/profile/SCHEMA.md).

Library mode (default):
    python3 scripts/check_nutrition.py

Candidate mode (meals proposed by Claude when the library runs out, SCHEMA.md §8):
    python3 scripts/check_nutrition.py --candidates out.json --profile P-vegan
    python3 scripts/check_nutrition.py --candidates out.json --profile my_profile.yaml

Checks:
  * profile: closed lists, 14 EU + 9 US allergens, wheat implies gluten, every reference
    (methods, hints, levels, diets, injury presets) resolves;
  * components: unique ids, fields, closed lists, wheat -> gluten, count >= 80;
  * meals: ids, fields, closed lists, components and swaps exist, swaps keep the plate role,
    declared allergens / may_contain / diets equal the ones derived from components,
    count >= 40 and every level has meals for every main slot;
  * templates: plates sum to 1.0, every level x load class covered exactly once,
    methods exist, are available at the template level and are not blocked;
  * methods: fields, evidence, blocked methods name the decision that blocks them;
  * rules: effects from the closed list, overlays / hints / tags / levels resolve;
  * stop dictionary over every user-facing text (errors reject, warnings are listed);
  * filter tests (_tests/profiles.yaml): no allowed meal (after swaps) carries anything the
    profile excludes; enough options remain; mode, level cap, hidden numbers and forbidden
    methods are as expected; `must_reject_raw` meals are rejected without swaps;
  * candidate tests (_tests/candidates.yaml): Claude-style candidates are accepted or
    rejected as expected.

Exit code 0 = no errors (warnings allowed), 1 = errors, 2 = bad input.
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parent.parent
NUT = ROOT / "content/nutrition"
PROFILE = ROOT / "content/profile/constraints.yaml"

LEVELS = ("base", "structured", "precise")
LEVEL_RANK = {lv: i for i, lv in enumerate(LEVELS)}
LACTOSE_RANK = {"none": 0, "low": 1, "high": 2}
MAIN_SLOTS = ("breakfast", "lunch", "dinner")
MEAT = {"meat_pork", "meat_beef", "meat_poultry", "meat_other"}
TEXT_KEYS = {"ru", "what_ru", "why_ru", "note_ru", "meat_note_ru", "includes_ru", "disclaimer_ru"}
MIN_COMPONENTS, MIN_MEALS, MIN_PER_LEVEL = 80, 40, 12
MODE_ORDER = ("normal", "filter_only", "safe", "ed_care")

errors: list[str] = []
warnings: list[str] = []


def err(where: str, msg: str) -> None:
    errors.append(f"{where}: {msg}")


def warn(where: str, msg: str) -> None:
    warnings.append(f"{where}: {msg}")


def load(path: Path):
    try:
        with open(path, encoding="utf-8") as fh:
            if path.suffix == ".json":
                return json.load(fh)
            return yaml.safe_load(fh)
    except (OSError, yaml.YAMLError, json.JSONDecodeError) as exc:
        print(f"cannot read {path}: {exc}", file=sys.stderr)
        sys.exit(2)


def as_set(v) -> set:
    return set(v or [])


# ----------------------------------------------------------------------------- stop dictionary
class Stopwords:
    def __init__(self, data: dict):
        def compile_(pat: str):
            body = pat.replace(".*", "[а-яa-z0-9]*")
            return re.compile(rf"(?<![а-яa-z0-9]){body}(?![а-яa-z0-9])")

        self.errors = [(grp, p, compile_(p)) for grp, pats in (data.get("errors") or {}).items() for p in pats]
        self.warnings = [(p, compile_(p)) for p in data.get("warnings") or []]

    @staticmethod
    def norm(text: str) -> str:
        return text.lower().replace("ё", "е")

    def check(self, where: str, text: str, sink_err=err, sink_warn=warn) -> bool:
        t = self.norm(text)
        ok = True
        for grp, pat, rx in self.errors:
            m = rx.search(t)
            if m:
                sink_err(where, f"stop word [{grp}] «{m.group(0)}» (pattern {pat!r}) in «{text}»")
                ok = False
        for pat, rx in self.warnings:
            m = rx.search(t)
            if m:
                sink_warn(where, f"review word «{m.group(0)}» in «{text}»")
        return ok


def walk_texts(node, path: str, skip: set[str] = frozenset()):
    """Yield (path, text) for user-facing strings."""
    if isinstance(node, dict):
        for k, v in node.items():
            p = f"{path}.{k}"
            if k in skip:
                continue
            if k in TEXT_KEYS and isinstance(v, str):
                yield p, v
            elif isinstance(v, (dict, list)):
                yield from walk_texts(v, p, skip)
    elif isinstance(node, list):
        for i, v in enumerate(node):
            ident = v.get("id") if isinstance(v, dict) else None
            yield from walk_texts(v, f"{path}[{ident or i}]", skip)


# ----------------------------------------------------------------------------- content model
class Content:
    def __init__(self):
        self.profile = load(PROFILE)
        self.components_doc = load(NUT / "components.yaml")
        self.meals_doc = load(NUT / "meals.yaml")
        self.templates_doc = load(NUT / "templates.yaml")
        self.methods_doc = load(NUT / "methods.yaml")
        self.rules_doc = load(NUT / "rules.yaml")
        self.stop = Stopwords(load(NUT / "stopwords.yaml"))

        pr = self.profile
        self.allergens = {a["id"]: a for a in pr["allergens"]}
        self.origins = set(pr["origins"])
        self.diets = {d["id"]: d for d in pr["diets"]}
        self.intolerances = {d["id"]: d for d in pr["intolerances"]}
        self.medical = {d["id"]: d for d in pr["medical_flags"]}
        self.life_stage = {d["id"]: d for d in pr["life_stage"]}
        self.severity = {d["id"]: d for d in pr["allergy_severity"]}

        self.components = {c["id"]: c for c in self.components_doc["components"]}
        self.meals = [m for m in self.meals_doc["meals"] if not m.get("retired")]
        self.methods = {m["id"]: m for m in self.methods_doc["methods"]}
        self.slots = {s["id"] for s in self.templates_doc["slots"]}
        self.plates = self.templates_doc["plates"]
        self.overlays = {o["id"]: o for o in self.templates_doc["overlays"]}
        self.hints = self.rules_doc.get("hints") or {}
        self.contexts = set(self.meals_doc["contexts"])
        self.meal_tags = set(self.meals_doc["meal_tags"])
        self.component_tags = {t for c in self.components.values() for t in c.get("tags") or []}

    # ----- derivations from components (the engine never trusts declared values)
    def derive(self, comp_ids: list[str]):
        comps = [self.components[c] for c in comp_ids if c in self.components]
        allergens = set().union(*(as_set(c["allergens"]) for c in comps)) if comps else set()
        may = set().union(*(as_set(c.get("may_contain")) for c in comps)) if comps else set()
        may -= allergens
        origins = set().union(*(as_set(c["origin"]) for c in comps)) if comps else set()
        lactose = max((LACTOSE_RANK[c["lactose"]] for c in comps), default=0)
        tags = set().union(*(as_set(c.get("tags")) for c in comps)) if comps else set()
        diets = []
        for did, d in self.diets.items():
            if origins & as_set(d.get("exclude_origins")):
                continue
            if allergens & as_set(d.get("exclude_allergens")):
                continue
            if d.get("may_contain") == "excluded" and may & as_set(d.get("exclude_allergens")):
                continue
            if d.get("meal_rule") == "no_meat_with_dairy" and origins & MEAT and "dairy" in origins:
                continue
            diets.append(did)
        return {"allergens": allergens, "may_contain": may, "origins": origins, "lactose": lactose,
                "tags": tags, "diets": set(diets)}


# ----------------------------------------------------------------------------- profile -> effective filter
def effective(c: Content, prof: dict) -> dict:
    ex_all, strict_may, ex_orig, ex_tags = set(), set(), set(), set()
    lactose_max = 2
    meal_rules = set()
    forbid, hints = set(), set()
    hide = False
    modes = ["normal"]
    cap = prof.get("level", "precise")
    no_generation = False

    def lower_cap(lv):
        nonlocal cap
        if lv and LEVEL_RANK[lv] < LEVEL_RANK[cap]:
            cap = lv

    for a in prof.get("allergies") or []:
        ex_all.add(a["id"])
        sev = c.severity[a.get("severity", "allergy")]
        if sev["may_contain"] == "excluded":
            strict_may.add(a["id"])
        if "no_unlabeled_generation" in (sev.get("extra") or []):
            no_generation = True
    for iid in prof.get("intolerances") or []:
        it = c.intolerances[iid]
        f = it.get("filter") or {}
        if "component_lactose_max" in f:
            lactose_max = min(lactose_max, LACTOSE_RANK[f["component_lactose_max"]])
        ex_all |= as_set(f.get("exclude_allergens"))
        ex_tags |= as_set(f.get("exclude_tags"))
    diets = list(prof.get("diets") or [])
    for mid in prof.get("medical") or []:
        fl = c.medical[mid]
        diets += fl.get("implies_diet") or []
        n = fl.get("nutrition") or {}
        if n.get("may_contain_gluten") == "excluded":
            strict_may.add("gluten")
        lower_cap(n.get("max_level"))
        forbid |= as_set(n.get("forbid_methods"))
        hints |= as_set(n.get("add_hints"))
        hide = hide or bool(n.get("hide_numbers"))
        mode = fl.get("mode", "normal")
        modes.append("ed_care" if mode.startswith("ed_care") else mode)
    for lid in prof.get("life_stage") or []:
        ls = c.life_stage[lid]
        n = ls.get("nutrition") or {}
        lower_cap(n.get("max_level"))
        forbid |= as_set(n.get("forbid_methods"))
        hints |= as_set(n.get("add_hints"))
        modes.append(ls.get("mode", "safe"))
    for did in diets:
        d = c.diets[did]
        ex_orig |= as_set(d.get("exclude_origins"))
        ex_all |= as_set(d.get("exclude_allergens"))
        if d.get("may_contain") == "excluded":
            strict_may |= as_set(d.get("exclude_allergens"))
        if d.get("meal_rule"):
            meal_rules.add(d["meal_rule"])
    mode = max(modes, key=MODE_ORDER.index)
    if mode == "ed_care":
        lower_cap("base")
        hide = True
    # an allergen implied by another (wheat -> gluten) is excluded via the component data rule
    return {"ex_all": ex_all, "strict_may": strict_may, "ex_orig": ex_orig, "ex_tags": ex_tags,
            "lactose_max": lactose_max, "meal_rules": meal_rules, "forbid": forbid, "hints": hints,
            "hide_numbers": hide, "mode": mode, "level": cap, "no_generation": no_generation}


def component_violations(c: Content, cid: str, eff: dict) -> list[str]:
    comp = c.components[cid]
    out = []
    if as_set(comp["allergens"]) & eff["ex_all"]:
        out.append(f"allergen {sorted(as_set(comp['allergens']) & eff['ex_all'])}")
    if as_set(comp.get("may_contain")) & eff["strict_may"]:
        out.append(f"may contain {sorted(as_set(comp.get('may_contain')) & eff['strict_may'])}")
    if as_set(comp["origin"]) & eff["ex_orig"]:
        out.append(f"origin {sorted(as_set(comp['origin']) & eff['ex_orig'])}")
    if LACTOSE_RANK[comp["lactose"]] > eff["lactose_max"]:
        out.append(f"lactose {comp['lactose']}")
    if as_set(comp.get("tags")) & eff["ex_tags"]:
        out.append(f"tag {sorted(as_set(comp.get('tags')) & eff['ex_tags'])}")
    return out


def meal_rule_violations(c: Content, comp_ids: list[str], eff: dict) -> list[str]:
    out = []
    if "no_meat_with_dairy" in eff["meal_rules"]:
        origins = set().union(*(as_set(c.components[x]["origin"]) for x in comp_ids))
        if origins & MEAT and "dairy" in origins:
            out.append("meat with dairy (kosher)")
    return out


def resolve_meal(c: Content, meal: dict, eff: dict, use_swaps: bool = True):
    """Return (final component ids, None) if the meal fits the profile, else (None, reason)."""
    final = []
    for item in meal["components"]:
        cid = item["c"]
        if not component_violations(c, cid, eff):
            final.append(cid)
            continue
        alts = (meal.get("swaps") or {}).get(cid, []) if use_swaps else []
        for alt in alts:
            if alt in c.components and not component_violations(c, alt, eff):
                final.append(alt)
                break
        else:
            return None, f"{cid}: {', '.join(component_violations(c, cid, eff))}"
    mr = meal_rule_violations(c, final, eff)
    if mr:
        return None, "; ".join(mr)
    return final, None


# ----------------------------------------------------------------------------- checks
def check_profile(c: Content) -> None:
    pr = c.profile
    w = "profile"
    ids = [a["id"] for a in pr["allergens"]]
    if len(ids) != len(set(ids)):
        err(w, "duplicate allergen id")
    eu = [a["id"] for a in pr["allergens"] if a.get("eu14")]
    us = [a["id"] for a in pr["allergens"] if a.get("us9")]
    if len(eu) != 14:
        err(w, f"EU Annex II needs 14 allergens, found {len(eu)}: {eu}")
    if len(us) != 9:
        err(w, f"US FALCPA + FASTER needs 9 allergens, found {len(us)}: {us}")
    for a in pr["allergens"]:
        for imp in a.get("implies") or []:
            if imp not in c.allergens:
                err(w, f"allergen {a['id']} implies unknown {imp}")
    for sid in ("avoid", "allergy", "anaphylaxis"):
        if sid not in c.severity:
            err(w, f"severity {sid} missing")
    for d in pr["diets"]:
        if as_set(d.get("exclude_origins")) - c.origins:
            err(w, f"diet {d['id']} unknown origins {as_set(d.get('exclude_origins')) - c.origins}")
        if as_set(d.get("exclude_allergens")) - set(c.allergens):
            err(w, f"diet {d['id']} unknown allergens")
    for i in pr["intolerances"]:
        f = i.get("filter") or {}
        if as_set(f.get("exclude_allergens")) - set(c.allergens):
            err(w, f"intolerance {i['id']} unknown allergens")
        if "component_lactose_max" in f and f["component_lactose_max"] not in LACTOSE_RANK:
            err(w, f"intolerance {i['id']} bad lactose level")
    for group, items in (("medical_flags", pr["medical_flags"]), ("life_stage", pr["life_stage"])):
        for fl in items:
            n = fl.get("nutrition") or {}
            where = f"{w}.{group}.{fl['id']}"
            if n.get("max_level") and n["max_level"] not in LEVEL_RANK:
                err(where, f"bad max_level {n['max_level']}")
            for m in n.get("forbid_methods") or []:
                if m not in c.methods:
                    err(where, f"forbids unknown method {m}")
            for h in n.get("add_hints") or []:
                if h not in c.hints:
                    err(where, f"unknown hint {h}")
            for d in fl.get("implies_diet") or []:
                if d not in c.diets:
                    err(where, f"implies unknown diet {d}")
            if fl.get("mode") not in ("safe", "ed_care", "ed_care_light", "filter_only"):
                err(where, f"bad mode {fl.get('mode')}")
    regions, statuses = set(pr["injury_regions"]), set(pr["injury_status"])
    limits = {m["id"] for m in pr["movement_limits"]}
    seen = set()
    for ip in pr.get("injury_presets") or []:
        where = f"{w}.injury_presets.{ip['id']}"
        if not re.fullmatch(r"[a-z][a-z0-9_]*", ip["id"]) or ip["id"] in seen:
            err(where, "id must be unique snake_case")
        seen.add(ip["id"])
        if "region" in ip and (ip["region"] not in regions or ip.get("status") not in statuses):
            err(where, "unknown region or status")
        if "life_stage" in ip and ip["life_stage"] not in c.life_stage:
            err(where, "unknown life_stage")
        if "medical_flag" in ip and ip["medical_flag"] not in c.medical:
            err(where, "unknown medical_flag")
        if as_set(ip.get("limits")) - limits:
            err(where, f"unknown limits {as_set(ip.get('limits')) - limits}")
    for need in ("knee_injury", "lower_back_pain", "pregnancy", "hypertension"):
        if need not in seen:
            err(w, f"injury preset {need} missing")
    st = pr.get("storage") or {}
    if st.get("to_llm") != "codes_only" or st.get("consent") != "separate_explicit":
        err(w, "storage must send codes only to the LLM and ask separate explicit consent (Agent 7)")


def check_components(c: Content) -> None:
    doc = c.components_doc
    groups, roles = set(doc["groups"]), set(doc["roles"])
    seen = set()
    for comp in doc["components"]:
        cid = comp.get("id", "?")
        w = f"component {cid}"
        if not re.fullmatch(r"[a-z][a-z0-9_]*", cid):
            err(w, "id must be snake_case")
        if cid in seen:
            err(w, "duplicate id")
        seen.add(cid)
        for f in ("ru", "group", "role", "allergens", "origin", "lactose", "tags", "per"):
            if f not in comp:
                err(w, f"missing field {f}")
        if comp.get("group") not in groups:
            err(w, f"unknown group {comp.get('group')}")
        if comp.get("role") not in roles:
            err(w, f"unknown role {comp.get('role')}")
        for f in ("allergens", "may_contain"):
            unknown = as_set(comp.get(f)) - set(c.allergens)
            if unknown:
                err(w, f"unknown {f} {sorted(unknown)}")
        if as_set(comp.get("allergens")) & as_set(comp.get("may_contain")):
            err(w, "allergen listed both as contains and may_contain")
        for f in ("allergens", "may_contain"):
            if "wheat" in as_set(comp.get(f)) and "gluten" not in as_set(comp.get("allergens")) | as_set(comp.get(f)):
                err(w, f"wheat in {f} requires gluten")
        if as_set(comp.get("origin")) - c.origins:
            err(w, f"unknown origin {as_set(comp.get('origin')) - c.origins}")
        if comp.get("lactose") not in LACTOSE_RANK:
            err(w, f"bad lactose {comp.get('lactose')}")
        if "milk" in as_set(comp.get("allergens")) and "dairy" not in as_set(comp.get("origin")):
            err(w, "milk allergen requires dairy origin")
        if "egg" in as_set(comp.get("allergens")) and "egg" not in as_set(comp.get("origin")):
            err(w, "egg allergen requires egg origin")
        if as_set(comp.get("allergens")) & {"fish"} and "fish" not in as_set(comp.get("origin")):
            err(w, "fish allergen requires fish origin")
        if as_set(comp.get("allergens")) & {"crustacean", "mollusc"} and "seafood" not in as_set(comp.get("origin")):
            err(w, "shellfish allergen requires seafood origin")
        per = comp.get("per") or {}
        if set(per) != {"p", "c", "f", "fib"} or any(not isinstance(v, (int, float)) or v < 0 for v in per.values()):
            err(w, "per must be {p, c, f, fib} with non-negative numbers")
        if "kcal" in comp or "kcal" in per:
            err(w, "no kcal field by design (A19 veto, D-010)")
    if len(seen) < MIN_COMPONENTS:
        err("components", f"need >= {MIN_COMPONENTS}, found {len(seen)}")


def check_meals(c: Content) -> None:
    seen = set()
    per_level_slot = {(lv, s): 0 for lv in LEVELS for s in MAIN_SLOTS}
    per_level = {lv: 0 for lv in LEVELS}
    for m in c.meals_doc["meals"]:
        mid = m.get("id", "?")
        w = f"meal {mid}"
        if not re.fullmatch(r"M\d{3}", mid):
            err(w, "id must be M + 3 digits")
        if mid in seen:
            err(w, "duplicate id")
        seen.add(mid)
        if m.get("retired"):
            continue
        for f in ("ru", "levels", "slots", "plate", "components", "context", "prep_min", "swaps", "tags",
                  "allergens", "may_contain", "diets"):
            if f not in m:
                err(w, f"missing field {f}")
        check_meal_shape(c, m, w)
        comp_ids = [x["c"] for x in m.get("components") or [] if x.get("c") in c.components]
        d = c.derive(comp_ids)
        if as_set(m.get("allergens")) != d["allergens"]:
            err(w, f"declared allergens {sorted(as_set(m.get('allergens')))} != derived {sorted(d['allergens'])}")
        if as_set(m.get("may_contain")) != d["may_contain"]:
            err(w, f"declared may_contain {sorted(as_set(m.get('may_contain')))} != derived {sorted(d['may_contain'])}")
        if as_set(m.get("diets")) != d["diets"]:
            missing, extra = d["diets"] - as_set(m.get("diets")), as_set(m.get("diets")) - d["diets"]
            err(w, f"declared diets differ from derived: missing {sorted(missing)}, wrongly claimed {sorted(extra)}")
        for cid, alts in (m.get("swaps") or {}).items():
            if cid not in comp_ids:
                err(w, f"swap for {cid}, which is not in the meal")
                continue
            for a in alts:
                if a not in c.components:
                    err(w, f"swap {cid} -> unknown {a}")
                elif not same_role(c.components[a]["role"], c.components[cid]["role"]):
                    err(w, f"swap {cid} ({c.components[cid]['role']}) -> {a} ({c.components[a]['role']}) changes plate role")
        for lv in m.get("levels") or []:
            per_level[lv] = per_level.get(lv, 0) + 1
            for s in m.get("slots") or []:
                if (lv, s) in per_level_slot:
                    per_level_slot[(lv, s)] += 1
    active = len([m for m in c.meals_doc["meals"] if not m.get("retired")])
    if active < MIN_MEALS:
        err("meals", f"need >= {MIN_MEALS}, found {active}")
    for lv, n in per_level.items():
        if n < MIN_PER_LEVEL:
            err("meals", f"level {lv} has {n} meals, need >= {MIN_PER_LEVEL}")
    for (lv, s), n in per_level_slot.items():
        if n < 3:
            err("meals", f"level {lv} slot {s} has {n} meals, need >= 3")


def same_role(a: str, b: str) -> bool:
    """A swap keeps the plate role; sauces may be a fat or an extra."""
    return a == b or {a, b} == {"fat", "extra"}


def check_meal_shape(c: Content, m: dict, w: str, candidate: bool = False) -> None:
    if not isinstance(m.get("ru"), str) or not m.get("ru"):
        err(w, "ru must be a non-empty string")
    if not m.get("levels") or as_set(m.get("levels")) - set(LEVELS):
        err(w, f"bad levels {m.get('levels')}")
    if not m.get("slots") or as_set(m.get("slots")) - c.slots:
        err(w, f"bad slots {m.get('slots')}")
    if m.get("plate") not in c.plates:
        err(w, f"unknown plate {m.get('plate')}")
    if as_set(m.get("context")) - c.contexts or not m.get("context"):
        err(w, f"bad context {m.get('context')}")
    if as_set(m.get("tags")) - c.meal_tags:
        err(w, f"unknown tags {sorted(as_set(m.get('tags')) - c.meal_tags)}")
    if not isinstance(m.get("prep_min"), int) or not 0 <= m["prep_min"] <= 120:
        err(w, "prep_min must be 0..120")
    comps = m.get("components") or []
    if not comps:
        err(w, "no components")
    for x in comps:
        if x.get("c") not in c.components:
            err(w, f"unknown component {x.get('c')}")
        if not isinstance(x.get("n"), (int, float)) or not 0 < x["n"] <= 3:
            err(w, f"portion n for {x.get('c')} must be in (0, 3]")
    if len({x.get("c") for x in comps}) != len(comps):
        err(w, "component repeated")


def check_templates(c: Content) -> None:
    t = c.templates_doc
    for pid, p in t["plates"].items():
        s = sum(p["share"].values())
        if abs(s - 1.0) > 1e-6:
            err(f"plate {pid}", f"shares sum to {s}, need 1.0")
    loads = set(t["load_classes"])
    cover = {(lv, ld): [] for lv in LEVELS for ld in loads}
    ids = set()
    for tp in t["templates"]:
        w = f"template {tp['id']}"
        if tp["id"] in ids:
            err(w, "duplicate id")
        ids.add(tp["id"])
        if tp["level"] not in LEVEL_RANK:
            err(w, f"bad level {tp['level']}")
            continue
        for ld in tp["load"]:
            if ld not in loads:
                err(w, f"unknown load {ld}")
            else:
                cover[(tp["level"], ld)].append(tp["id"])
        slots_here = [s["slot"] for s in tp["slots"]]
        for s in tp["slots"]:
            if s["slot"] not in c.slots:
                err(w, f"unknown slot {s['slot']}")
            if s["plate"] not in c.plates:
                err(w, f"unknown plate {s['plate']}")
        for need in MAIN_SLOTS:
            if need not in slots_here and not (need == "lunch" and "post_training" in slots_here):
                err(w, f"main slot {need} missing")
        for mid in tp["methods"]:
            meth = c.methods.get(mid)
            if not meth:
                err(w, f"unknown method {mid}")
                continue
            if LEVEL_RANK[meth["from_level"]] > LEVEL_RANK[tp["level"]]:
                err(w, f"method {mid} is from level {meth['from_level']}")
            if meth.get("status") == "blocked":
                err(w, f"method {mid} is blocked ({meth.get('blocked_by')})")
        if "carbs_g_per_kg" in tp and tp["level"] != "precise":
            err(w, "carb ranges only on «Точность»")
    for (lv, ld), tids in cover.items():
        if len(tids) != 1:
            err("templates", f"level {lv} load {ld} covered by {tids or 'nothing'}, need exactly one")
    tagspace = c.meal_tags | c.component_tags
    for o in t["overlays"]:
        e = o["effect"]
        if as_set(e.get("prefer_tags")) - tagspace:
            err(f"overlay {o['id']}", f"unknown tags {as_set(e.get('prefer_tags')) - tagspace}")
        if as_set(e.get("prefer_context")) - c.contexts:
            err(f"overlay {o['id']}", f"unknown context {as_set(e.get('prefer_context')) - c.contexts}")
        for k in ("plate", "plate_cap"):
            if k in e and e[k] not in c.plates:
                err(f"overlay {o['id']}", f"unknown plate {e[k]}")
        h = e.get("hint")
        if h and not (h in c.hints or re.fullmatch(r"N\d\d", h)):
            err(f"overlay {o['id']}", f"unknown hint {h}")


def check_methods(c: Content) -> None:
    ids = set()
    for lv in c.methods_doc["levels"]:
        if lv["id"] not in LEVEL_RANK:
            err("methods.levels", f"unknown level {lv['id']}")
    for g in c.methods_doc.get("goals") or []:
        if as_set(g.get("levels")) - set(LEVELS):
            err(f"goal {g['id']}", "bad levels")
        if re.search(r"weight|вес|похуд", g["id"] + g["ru"]):
            err(f"goal {g['id']}", "no weight goals (A19 veto №2)")
    for m in c.methods_doc["methods"]:
        w = f"method {m['id']}"
        if m["id"] in ids:
            err(w, "duplicate id")
        ids.add(m["id"])
        for f in ("ru", "from_level", "default", "evidence", "source", "what_ru", "numbers"):
            if f not in m:
                err(w, f"missing field {f}")
        if m.get("from_level") not in LEVEL_RANK:
            err(w, "bad from_level")
        if m.get("default") not in ("on", "off", "optional"):
            err(w, "default must be on/off/optional")
        if not re.match(r"[ABCD]", str(m.get("evidence", ""))):
            err(w, "evidence must start with A/B/C/D (A04 scale)")
        if m.get("numbers") not in ("none", "time_only", "grams_range", "kcal_range"):
            err(w, "bad numbers")
        if m.get("numbers") in ("grams_range", "kcal_range") and m.get("from_level") == "base":
            err(w, "numbers are not allowed on «Основа»")
        if m.get("numbers") == "kcal_range" and (m.get("status") != "blocked" or m.get("default") != "off"):
            err(w, "energy numbers must stay blocked and off until the founder decides (A41 §3)")
        if m.get("status") == "blocked" and not m.get("blocked_by"):
            err(w, "blocked method must name blocked_by")
        for r in m.get("requires") or []:
            if r not in {p["id"] for p in c.profile["physiology"]}:
                err(w, f"requires unknown profile field {r}")


def check_rules(c: Content) -> None:
    doc = c.rules_doc
    effects = set(doc["effects"])
    ids = set()
    tagspace = c.meal_tags | c.component_tags
    for r in doc["rules"]:
        w = f"rule {r['id']}"
        if r["id"] in ids:
            err(w, "duplicate id")
        ids.add(r["id"])
        for f in ("when", "then", "why_ru", "evidence"):
            if f not in r:
                err(w, f"missing field {f}")
        then = r.get("then") or {}
        for k, v in then.items():
            if k not in effects:
                err(w, f"unknown effect {k}")
            if k == "overlay" and v not in c.overlays:
                err(w, f"unknown overlay {v}")
            if k in ("cap_level", "suggest_level") and v not in LEVEL_RANK:
                err(w, f"bad level {v}")
            if k == "hint" and not (v in c.hints or re.fullmatch(r"N\d\d", v)):
                err(w, f"unknown hint {v}")
            if k == "forbid_method" and v not in c.methods:
                err(w, f"unknown method {v}")
            if k == "prefer_meal_tags":
                for slot, tags in v.items():
                    if slot not in c.slots:
                        err(w, f"unknown slot {slot}")
                    if as_set(tags) - tagspace:
                        err(w, f"unknown tags {as_set(tags) - tagspace}")
            if k in ("set_plate", "starch_hands_delta"):
                for slot, val in v.items():
                    if slot not in c.slots:
                        err(w, f"unknown slot {slot}")
                    if k == "set_plate" and val not in c.plates:
                        err(w, f"unknown plate {val}")
            if k == "add_slot" and v not in c.slots:
                err(w, f"unknown slot {v}")
        if r.get("a19") and not re.fullmatch(r"N\d\d", r["a19"]):
            err(w, "a19 must be N01..N20")
    # safety rules must come first
    first = [r["id"] for r in doc["rules"][:2]]
    if first != ["NR-01", "NR-02"]:
        err("rules", "safety rules NR-01 (ED care) and NR-02 (safe mode) must be first")


def check_texts(c: Content) -> None:
    sources = [
        ("profile", c.profile, {"storage"}),
        ("components", c.components_doc, set()),
        ("meals", c.meals_doc, set()),
        ("templates", c.templates_doc, set()),
        ("methods", c.methods_doc, {"excluded"}),
        ("rules", c.rules_doc, set()),
    ]
    for name, doc, skip in sources:
        for where, text in walk_texts(doc, name, skip):
            c.stop.check(where, text)
    c.stop.check("profile.storage.disclaimer_ru", c.profile["storage"]["disclaimer_ru"])
    for hid, text in c.hints.items():
        c.stop.check(f"rules.hints.{hid}", text)


def check_profiles_tests(c: Content) -> None:
    tests = load(NUT / "_tests/profiles.yaml")
    meal_by_id = {m["id"]: m for m in c.meals}
    must_cover = {"tree_nut", "gluten", "lactose", "vegan"}
    covered = set()
    for prof in tests["profiles"]:
        w = f"test {prof['id']}"
        covered |= {a["id"] for a in prof.get("allergies") or []} | as_set(prof.get("intolerances")) | as_set(prof.get("diets"))
        if "celiac" in as_set(prof.get("medical")) or "gluten_free" in as_set(prof.get("diets")) or "gluten_sensitive" in as_set(prof.get("intolerances")):
            covered.add("gluten")
        eff = effective(c, prof)
        exp = prof.get("expect") or {}
        if exp.get("mode") and eff["mode"] != exp["mode"]:
            err(w, f"mode {eff['mode']} != expected {exp['mode']}")
        if exp.get("max_level") and eff["level"] != exp["max_level"]:
            err(w, f"level cap {eff['level']} != expected {exp['max_level']}")
        if "hide_numbers" in exp and eff["hide_numbers"] != exp["hide_numbers"]:
            err(w, f"hide_numbers {eff['hide_numbers']} != expected {exp['hide_numbers']}")
        missing = as_set(exp.get("forbidden_methods")) - eff["forbid"]
        if missing:
            err(w, f"methods should be forbidden: {sorted(missing)}")
        # the methods shown at the effective level never include forbidden ones
        for tp in c.templates_doc["templates"]:
            if tp["level"] == eff["level"]:
                shown = [m for m in tp["methods"] if m not in eff["forbid"]]
                if eff["hide_numbers"]:
                    shown = [m for m in shown if c.methods[m]["numbers"] in ("none", "time_only")]
                if set(shown) & eff["forbid"]:
                    err(w, f"template {tp['id']} would show forbidden methods")
        # the core property: nothing excluded survives the filter, even after swaps
        options = {s: 0 for s in MAIN_SLOTS}
        for m in c.meals:
            final, _ = resolve_meal(c, m, eff)
            if final is None:
                continue
            for cid in final:
                v = component_violations(c, cid, eff)
                if v:
                    err(w, f"LEAK: {m['id']} keeps {cid} ({', '.join(v)})")
            if meal_rule_violations(c, final, eff):
                err(w, f"LEAK: {m['id']} breaks a meal rule")
            if eff["level"] in m["levels"]:
                for s in MAIN_SLOTS:
                    if s in m["slots"]:
                        options[s] += 1
        need = exp.get("min_options", 3)
        for s, n in options.items():
            if n < need:
                err(w, f"only {n} options for {s} at level {eff['level']}, need >= {need}")
        for mid in exp.get("must_reject_raw") or []:
            if mid not in meal_by_id:
                err(w, f"must_reject_raw names unknown meal {mid}")
                continue
            final, _ = resolve_meal(c, meal_by_id[mid], eff, use_swaps=False)
            if final is not None:
                err(w, f"filter is a no-op: {mid} passes without swaps")
        print(f"  {prof['id']:<18} mode={eff['mode']:<11} level={eff['level']:<10} "
              f"options B/L/D={options['breakfast']}/{options['lunch']}/{options['dinner']}")
    if must_cover - covered:
        err("tests", f"profiles must cover {sorted(must_cover - covered)}")


def check_candidate(c: Content, cand: dict, eff: dict, where: str) -> list[str]:
    """Validate one Claude-generated meal against schema, stop words and the profile. Returns reasons."""
    reasons: list[str] = []
    sink = lambda w, msg: reasons.append(msg)  # noqa: E731
    global errors
    saved, errors = errors, []
    m = dict(cand)
    m.setdefault("swaps", {})
    check_meal_shape(c, m, where, candidate=True)
    reasons += errors
    errors = saved
    if reasons:
        return reasons
    c.stop.check(where, m["ru"], sink_err=sink, sink_warn=lambda *a: None)
    comp_ids = [x["c"] for x in m["components"]]
    d = c.derive(comp_ids)
    if as_set(m.get("allergens")) != d["allergens"]:
        reasons.append(f"declared allergens {sorted(as_set(m.get('allergens')))} != derived {sorted(d['allergens'])}")
    final, why = resolve_meal(c, m, eff, use_swaps=False)
    if final is None:
        reasons.append(f"profile filter: {why}")
    if eff["level"] not in m["levels"]:
        reasons.append(f"level {eff['level']} not in {m['levels']}")
    if eff["hide_numbers"] and re.search(r"\d+\s*(г|гр|ккал|%)", m["ru"]):
        reasons.append("numbers in text while numbers are hidden")
    if eff["no_generation"]:
        reasons.append("generation is off for anaphylaxis profiles (library only)")
    return reasons


def check_candidate_tests(c: Content) -> None:
    path = NUT / "_tests/candidates.yaml"
    if not path.exists():
        err("tests", "missing _tests/candidates.yaml")
        return
    tests = load(path)
    profiles = {p["id"]: p for p in load(NUT / "_tests/profiles.yaml")["profiles"]}
    for t in tests["cases"]:
        eff = effective(c, profiles[t["profile"]])
        reasons = check_candidate(c, t["meal"], eff, f"candidate {t['id']}")
        got = "reject" if reasons else "accept"
        if got != t["expect"]:
            err(f"candidate test {t['id']}", f"expected {t['expect']}, got {got}: {reasons}")
        else:
            print(f"  {t['id']:<6} {got:<7} {('— ' + reasons[0]) if reasons else ''}")


def run_candidates(c: Content, path: Path, profile_ref: str) -> int:
    cands = load(path)
    if isinstance(cands, dict):
        cands = cands.get("meals") or cands.get("candidates") or []
    tests = {p["id"]: p for p in load(NUT / "_tests/profiles.yaml")["profiles"]}
    prof = tests.get(profile_ref) or load(Path(profile_ref))
    eff = effective(c, prof)
    bad = 0
    for i, cand in enumerate(cands, 1):
        reasons = check_candidate(c, cand, eff, f"G-{i:03d}")
        if reasons:
            bad += 1
            print(f"REJECT G-{i:03d} «{cand.get('ru')}»: " + "; ".join(reasons))
        else:
            print(f"ACCEPT G-{i:03d} «{cand.get('ru')}»")
    return 1 if bad else 0


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--candidates", type=Path, help="JSON/YAML list of meals proposed by Claude")
    ap.add_argument("--profile", help="test profile id from _tests/profiles.yaml or a YAML file")
    args = ap.parse_args()
    c = Content()
    if args.candidates:
        if not args.profile:
            print("--candidates needs --profile", file=sys.stderr)
            return 2
        return run_candidates(c, args.candidates, args.profile)

    check_profile(c)
    check_components(c)
    check_meals(c)
    check_templates(c)
    check_methods(c)
    check_rules(c)
    check_texts(c)
    print("Filter tests:")
    check_profiles_tests(c)
    print("Candidate tests:")
    check_candidate_tests(c)

    print(f"\ncomponents {len(c.components)} · meals {len(c.meals)} · methods {len(c.methods)} · "
          f"templates {len(c.templates_doc['templates'])} · rules {len(c.rules_doc['rules'])}")
    for w in warnings:
        print("WARN ", w)
    for e in errors:
        print("ERROR", e)
    print(f"\n{len(errors)} errors, {len(warnings)} warnings")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
