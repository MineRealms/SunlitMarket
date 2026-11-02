#!/usr/bin/env python3
"""Audit Sunlit Valley trade prices against FTBQ chapters and recipes.

This is deliberately offline: it reads the pack's SNBT chapters and recipe JSON
from mod jars / kubejs/data, and never starts Minecraft.
"""
from __future__ import annotations

import argparse
import collections
import csv
import json
import re
import zipfile
from pathlib import Path

GROUP_ORDER = {
    "457DCF55318282CA": 0,  # main / welcome
    "106AD9AA3ADDE3AE": 1,  # building
    "439B0423947443D3": 2,  # equipment
    "15230846E14B6926": 3,  # life / farming / mining
    "4A46A5E1358A80A6": 4,  # GregTech
    "04716B2F19F75F08": 5,  # magic
    "5E4D3C2B1A090807": 6,  # Middle-earth
}
RESULT_KEYS = ("result", "results", "output", "outputs")
INPUT_KEYS = ("ingredients", "ingredient", "inputs", "input", "key")
ID_KEYS = ("item", "id")

def ids_in(value):
    found = set()
    if isinstance(value, str):
        if ":" in value and not value.startswith("#"):
            found.add(value.split()[0])
    elif isinstance(value, list):
        for child in value:
            found.update(ids_in(child))
    elif isinstance(value, dict):
        for key in ID_KEYS:
            child = value.get(key)
            if isinstance(child, str) and ":" in child and not child.startswith("#"):
                found.add(child)
        for child in value.values():
            if isinstance(child, (dict, list)):
                found.update(ids_in(child))
    return found

def recipe_outputs(obj):
    out = set()
    if isinstance(obj, dict):
        for key in RESULT_KEYS:
            if key in obj:
                out.update(ids_in(obj[key]))
    return out

def recipe_inputs(obj):
    out = set()
    if isinstance(obj, dict):
        for key in RESULT_KEYS:
            obj = obj
        for key in INPUT_KEYS:
            if key in obj:
                out.update(ids_in(obj[key]))
    return out

def load_json(raw):
    try:
        return json.loads(raw)
    except Exception:
        return None

def scan_recipes(pack: Path):
    recipes = collections.defaultdict(list)
    files = 0
    sources = []
    data_root = pack / "kubejs" / "data"
    if data_root.is_dir():
        sources.extend(sorted(data_root.rglob("recipes/**/*.json")))
    for path in sources:
        obj = load_json(path.read_text(encoding="utf-8", errors="replace"))
        if obj is None:
            continue
        files += 1
        outs = recipe_outputs(obj)
        if outs:
            ins = recipe_inputs(obj)
            for out in outs:
                recipes[out].append(ins)
    jars = sorted((pack / "mods").glob("*.jar"))
    for jar_path in jars:
        try:
            with zipfile.ZipFile(jar_path) as z:
                names = [n for n in z.namelist() if "/recipes/" in n and n.endswith(".json")]
                for name in names:
                    try:
                        raw = z.read(name).decode("utf-8", errors="replace")
                    except Exception:
                        continue
                    obj = load_json(raw)
                    if obj is None:
                        continue
                    files += 1
                    outs = recipe_outputs(obj)
                    if outs:
                        ins = recipe_inputs(obj)
                        for out in outs:
                            recipes[out].append(ins)
        except (zipfile.BadZipFile, OSError):
            continue
    return recipes, files

def recipe_depth(item, recipes, memo, visiting, limit=8):
    if item in memo:
        return memo[item]
    if item in visiting or limit <= 0 or item not in recipes:
        return 0
    visiting.add(item)
    best = 0
    for ins in recipes[item]:
        child = 0
        for ing in ins:
            if ing != item:
                child = max(child, recipe_depth(ing, recipes, memo, visiting, limit - 1))
        best = max(best, 1 + child)
    visiting.discard(item)
    memo[item] = best
    return best

def parse_chapters(pack: Path):
    rows = []
    items = collections.defaultdict(set)
    chapter_dir = pack / "config" / "ftbquests" / "quests" / "chapters"
    for path in sorted(chapter_dir.glob("*.snbt")):
        text = path.read_text(encoding="utf-8", errors="replace")
        group = re.search(r"group:\s*\"([^\"]+)\"", text)
        order = re.search(r"order_index:\s*(-?\d+)", text)
        group_id = group.group(1) if group else ""
        order_index = int(order.group(1)) if order else 0
        stage = GROUP_ORDER.get(group_id, 0) * 100 + order_index
        task_items = set()
        for block in re.findall(r"tasks:\s*\[(.*?)\]", text, re.S):
            if 'type: "item"' in block or 'type:"item"' in block:
                for item in re.findall(r"item:\s*\"([^\"]+)\"", block):
                    if ":" in item and not item.startswith("#"):
                        task_items.add(item.split()[0])
        for item in task_items:
            items[item].add(path.stem)
        rows.append((path.stem, group_id, order_index, stage, len(task_items)))
    return rows, items

def shipping_values(pack: Path):
    values = {}
    registry = pack / "kubejs" / "startup_scripts" / "globalRegistry.js"
    if registry.is_file():
        text = registry.read_text(encoding="utf-8", errors="replace")
        for item, value in re.findall(r"\{ item: \"([^\"]+)\", value: (\d+)\s*\}", text):
            values[item] = int(value)
    me = pack / "kubejs" / "startup_scripts" / "me" / "meTrades.js"
    if me.is_file():
        text = me.read_text(encoding="utf-8", errors="replace")
        for item, value in re.findall(r"\[\"([^\"]+)\", (\d+)\]", text):
            values.setdefault("me:" + item, int(value))
    return values

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("pack", type=Path)
    ap.add_argument("--out", type=Path, default=Path("build/sv_price_audit.csv"))
    args = ap.parse_args()
    chapters, item_chapters = parse_chapters(args.pack)
    values = shipping_values(args.pack)
    recipes, recipe_files = scan_recipes(args.pack)
    memo = {}
    rows = []
    for item in sorted(item_chapters):
        chapter_names = sorted(item_chapters[item])
        stage = max((next((r[3] for r in chapters if r[0] == name), 0) for name in chapter_names), default=0)
        anchor = values.get(item)
        depth = recipe_depth(item, recipes, memo, set())
        recipe_count = len(recipes.get(item, []))
        rows.append({
            "item": item,
            "chapters": ";".join(chapter_names),
            "stage": stage,
            "shipping_anchor": anchor if anchor is not None else "",
            "recipe_count": recipe_count,
            "recipe_depth": depth,
        })
    args.out.parent.mkdir(parents=True, exist_ok=True)
    with args.out.open("w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=list(rows[0]))
        writer.writeheader()
        writer.writerows(rows)
    anchored = sum(1 for r in rows if r["shipping_anchor"] != "")
    print(f"chapters={len(chapters)} ftbq_items={len(rows)} anchored={anchored} unanchored={len(rows)-anchored} recipe_files={recipe_files}")
    print(f"with_recipe={sum(1 for r in rows if r['recipe_count'])} with_depth={sum(1 for r in rows if r['recipe_depth'])}")
    print(f"wrote {args.out}")

if __name__ == "__main__":
    main()
