"""Element IDs and transparent colour variants per catalog part, from Rebrickable's downloads.

Download elements.csv.gz and colors.csv.gz from https://rebrickable.com/downloads/ into %TEMP%/rb, then run
from the project root. Writes src/main/resources/lego_elements.json and the "trans_colors" of every entry in
lego_catalog.json (copied to src/main/resources).
"""
import collections
import csv
import gzip
import json
import os
import re
import shutil

RB = os.path.join(os.environ["TEMP"], "rb")
COLORS = {
    "red": [4], "blue": [1], "yellow": [14], "green": [2], "white": [15], "black": [0],
    "orange": [25], "lime": [27], "cyan": [3], "magenta": [26], "pink": [29, 13],
    "light_gray": [71, 7], "gray": [72, 8], "brown": [70, 6], "purple": [30, 22],
    "light_blue": [322], "tan": [19], "dark_tan": [28], "dark_red": [320], "dark_blue": [272],
    "dark_green": [288], "sand_green": [378], "light_orange": [191], "dark_orange": [484], "lavender": [31],
    "dark_brown": [308],
    "trans_clear": [47], "trans_black": [1095], "trans_brown": [40], "trans_red": [36], "trans_dark_pink": [45],
    "trans_pink": [230], "trans_purple": [52], "trans_dark_blue": [33], "trans_medium_blue": [143],
    "trans_light_blue": [41], "trans_green": [34], "trans_bright_green": [35], "trans_neon_green": [42],
    "trans_yellow": [46], "trans_orange": [182], "trans_neon_orange": [57], "opal_trans_clear": [1055],
    "opal_trans_light_blue": [1053], "opal_trans_purple": [1059],
}
TRANS_ORDER = [c for c in COLORS if c.startswith(("trans_", "opal_"))]
# Rebrickable part number -> LEGO design id, where they differ.
ALIASES = {"6141": "4073"}

by_rb = {}
for ours, ids in COLORS.items():
    for rank, i in enumerate(ids):
        by_rb[str(i)] = (ours, rank)
catalog = json.load(open("lego_catalog.json", encoding="utf-8"))
designs = {str(e["design_id"]) for e in catalog}
for e in catalog:
    for a in e.get("aliases", []):
        ALIASES.setdefault(str(a), str(e["design_id"]))
found = collections.defaultdict(lambda: collections.defaultdict(list))
by_design = collections.defaultdict(list)
for e in csv.DictReader(gzip.open(os.path.join(RB, "elements.csv.gz"), "rt", encoding="utf-8")):
    if e["color_id"] not in by_rb:
        continue
    entry = (*by_rb[e["color_id"]], int(e["element_id"]))
    by_id = ALIASES.get(e["design_id"], e["design_id"])
    if by_id in designs:
        by_design[by_id].append(entry)
    m = re.fullmatch(r"(\d+)[a-z]?", e["part_num"])
    design = m and ALIASES.get(m.group(1), m.group(1))
    if design in designs:
        ours, rank, element = entry
        found[design][ours].append((rank, -element, element))
# Rebrickable numbers some parts differently; fall back to the LEGO design id column.
for design, entries in by_design.items():
    if design not in found:
        for ours, rank, element in entries:
            found[design][ours].append((rank, -element, element))

out = {}
for design in sorted(found, key=int):
    out[design] = {c: [x[2] for x in sorted(set(v))][:3] for c, v in sorted(found[design].items())}
with open("src/main/resources/lego_elements.json", "w", encoding="utf-8") as f:
    json.dump(out, f, separators=(",", ":"))

trans_parts = 0
for entry in catalog:
    trans = [c for c in TRANS_ORDER if c in out.get(str(entry["design_id"]), {})]
    if trans:
        entry["trans_colors"] = trans
        trans_parts += 1
    else:
        entry.pop("trans_colors", None)
with open("lego_catalog.json", "w", encoding="utf-8") as f:
    json.dump(catalog, f, ensure_ascii=False, indent=2)
    f.write("\n")
shutil.copyfile("lego_catalog.json", "src/main/resources/lego_catalog.json")

missing = sorted(designs - set(out), key=int)
print("designs with ids", len(out), "of", len(designs), "missing", missing)
print("parts with transparent colours", trans_parts, "variants", sum(len(e.get("trans_colors", [])) for e in catalog))
