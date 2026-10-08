"""Generate blockstates, models, recipes, loot tables, tags, lang, and spawn items."""
import glob
import json
import math
import os

CATALOG_PATH = os.path.join(os.path.dirname(__file__), "..", "lego_catalog.json")

COLORS = [
    "red", "blue", "yellow", "green", "white", "black", "orange", "lime",
    "cyan", "magenta", "pink", "light_gray", "gray", "brown",
    "purple", "light_blue", "tan", "dark_tan", "dark_red", "dark_blue",
    "dark_green", "sand_green", "light_orange", "dark_orange", "lavender", "dark_brown",
]

# Colouring ingredient per colour: vanilla dyes where they exist, a fitting material otherwise.
DYE_MAP = {c: f"minecraft:{c}_dye" for c in COLORS}
DYE_MAP.update({
    "tan": "minecraft:sand",
    "dark_tan": "minecraft:wheat",
    "dark_red": "minecraft:nether_wart",
    "dark_blue": "minecraft:lapis_lazuli",
    "dark_green": "minecraft:kelp",
    "sand_green": "minecraft:fern",
    "light_orange": "minecraft:honeycomb",
    "dark_orange": "minecraft:raw_copper",
    "lavender": "minecraft:amethyst_shard",
    "dark_brown": "minecraft:dark_oak_log",
})

# Plate heights per catalog kind (3 plates = 1 brick = 1 block); entries may override with "height".
KIND_HEIGHT = {
    "brick": 3, "slope": 3, "inverted_slope": 3, "round_brick": 3, "technic_brick": 3,
    "plate": 1, "tile": 1, "round_plate": 1, "round_tile": 1, "baseplate": 1, "technic_plate": 1,
    "cheese_slope": 2, "curved_slope": 2, "bracket": 1, "gem": 2, "bar": 1, "wall_panel": 3,
}
STUDLESS_KINDS = {"tile", "round_tile", "cheese_slope", "curved_slope", "gem", "bar"}
# Fittings (bars, clips, boxes) are given in units: 1/6 stud = half a plate.
UNIT = 16 / 6
BAR_R = 3.0
CLIP_OUTER = 5.2

# Decorative (non-catalog) block kinds: template model, English name, Chinese name.
DECOR_TYPES = {
    "minifig_head": ("lego_minifig_head_template", "Lego Minifig Head", "乐高人仔头"),
    "minifig_torso": ("lego_minifig_torso_template", "Lego Minifig Torso", "乐高人仔上身"),
    "minifig_legs": ("lego_minifig_legs_template", "Lego Minifig Legs", "乐高人仔腿"),
    "minifig_statue": ("lego_minifig_statue_template", "Lego Minifig Statue", "乐高人仔雕像"),
    "animal": ("lego_animal_template", "Lego Animal", "乐高动物"),
    "plant": ("lego_plant_template", "Lego Plant", "乐高植物"),
    "leaf": ("lego_leaf_template", "Lego Leaf", "乐高叶子"),
    "flower": ("lego_flower_template", "Lego Flower", "乐高花"),
    "chair": ("lego_chair_template", "Lego Chair", "乐高椅子"),
    "table": ("lego_table_template", "Lego Table", "乐高桌子"),
    "window": ("lego_window_template", "Lego Window", "乐高窗户"),
    "door": ("lego_door_template", "Lego Door", "乐高门"),
    "lamp": ("lego_lamp_template", "Lego Lamp", "乐高灯"),
    "cable": ("lego_cable_template", "Lego Cable", "乐高缆线"),
    "fabric": ("lego_fabric_template", "Lego Fabric", "乐高织物"),
    "panel": ("lego_panel_template", "Lego Panel", "乐高装饰板"),
    "sign": ("lego_sign_template", "Lego Sign", "乐高标牌"),
}

COLOR_EN = {
    "red": "Red", "blue": "Blue", "yellow": "Yellow", "green": "Green",
    "white": "White", "black": "Black", "orange": "Orange", "lime": "Lime",
    "cyan": "Cyan", "magenta": "Magenta", "pink": "Pink",
    "light_gray": "Light Gray", "gray": "Gray", "brown": "Brown",
    "purple": "Purple", "light_blue": "Medium Azure", "tan": "Tan", "dark_tan": "Dark Tan",
    "dark_red": "Dark Red", "dark_blue": "Dark Blue", "dark_green": "Dark Green",
    "sand_green": "Sand Green", "light_orange": "Bright Light Orange", "dark_orange": "Dark Orange",
    "lavender": "Lavender", "dark_brown": "Dark Brown",
}
COLOR_ZH = {
    "red": "红色", "blue": "蓝色", "yellow": "黄色", "green": "绿色",
    "white": "白色", "black": "黑色", "orange": "橙色", "lime": "黄绿色",
    "cyan": "青色", "magenta": "品红色", "pink": "粉红色",
    "light_gray": "淡灰色", "gray": "灰色", "brown": "棕色",
    "purple": "紫色", "light_blue": "中天蓝色", "tan": "沙黄色", "dark_tan": "深沙黄色",
    "dark_red": "深红色", "dark_blue": "深蓝色", "dark_green": "深绿色",
    "sand_green": "沙绿色", "light_orange": "亮橙黄色", "dark_orange": "深橙色",
    "lavender": "薰衣草紫", "dark_brown": "深棕色",
}

# Transparent colours: (English, Chinese, ingredients added to the white part to tint it).
TRANS_COLORS = {
    "trans_clear": ("Trans-Clear", "透明", ["minecraft:glass_pane"]),
    "trans_black": ("Trans-Black", "透明黑", ["minecraft:black_stained_glass_pane"]),
    "trans_brown": ("Trans-Brown", "透明棕", ["minecraft:brown_stained_glass_pane"]),
    "trans_red": ("Trans-Red", "透明红", ["minecraft:red_stained_glass_pane"]),
    "trans_dark_pink": ("Trans-Dark Pink", "透明深粉", ["minecraft:magenta_stained_glass_pane"]),
    "trans_pink": ("Trans-Pink", "透明粉", ["minecraft:pink_stained_glass_pane"]),
    "trans_purple": ("Trans-Purple", "透明紫", ["minecraft:purple_stained_glass_pane"]),
    "trans_dark_blue": ("Trans-Dark Blue", "透明深蓝", ["minecraft:blue_stained_glass_pane"]),
    "trans_medium_blue": ("Trans-Medium Blue", "透明中蓝", ["minecraft:blue_stained_glass_pane", "minecraft:white_dye"]),
    "trans_light_blue": ("Trans-Light Blue", "透明浅蓝", ["minecraft:light_blue_stained_glass_pane"]),
    "trans_green": ("Trans-Green", "透明绿", ["minecraft:green_stained_glass_pane"]),
    "trans_bright_green": ("Trans-Bright Green", "透明亮绿", ["minecraft:lime_stained_glass_pane"]),
    "trans_neon_green": ("Trans-Neon Green", "透明荧光绿", ["minecraft:lime_stained_glass_pane", "minecraft:glowstone_dust"]),
    "trans_yellow": ("Trans-Yellow", "透明黄", ["minecraft:yellow_stained_glass_pane"]),
    "trans_orange": ("Trans-Orange", "透明橙", ["minecraft:orange_stained_glass_pane"]),
    "trans_neon_orange": ("Trans-Neon Orange", "透明荧光橙", ["minecraft:orange_stained_glass_pane", "minecraft:glowstone_dust"]),
    "opal_trans_clear": ("Opal Trans-Clear", "蛋白石透明", ["minecraft:glass_pane", "minecraft:quartz"]),
    "opal_trans_light_blue": ("Opal Trans-Light Blue", "蛋白石透明浅蓝", ["minecraft:light_blue_stained_glass_pane", "minecraft:quartz"]),
    "opal_trans_purple": ("Opal Trans-Purple", "蛋白石透明紫", ["minecraft:purple_stained_glass_pane", "minecraft:quartz"]),
}
for _c, (_en, _zh, _) in TRANS_COLORS.items():
    COLOR_EN[_c] = _en
    COLOR_ZH[_c] = _zh

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources")
TEMPLATE_FILES = {f"{t[0]}.json" for t in DECOR_TYPES.values()}

PX = 16
STUD_R = 4.8
STUD_H = 2.8
MAX_PREVIEW_STUDS = 64


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write("\n")


def bid(part, color):
    return f"lego_{part}_{color}"


def bid_catalog(design_id, color):
    return f"lego_{design_id}_{color}"


def item(part, color):
    return f"legominecraft:{bid(part, color)}"


def cat(design_id, color):
    return f"legominecraft:{bid_catalog(design_id, color)}"


def load_catalog():
    with open(CATALOG_PATH, encoding="utf-8") as f:
        return json.load(f)


def ing(item_id):
    return {"item": item_id}


def res(block_id, count=1):
    return {"id": f"legominecraft:{block_id}", "count": count}


def simple_loot(block_id):
    return {
        "type": "minecraft:block",
        "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"legominecraft:{block_id}"}],
                   "conditions": [{"condition": "minecraft:survives_explosion"}]}],
    }


# ---------------------------------------------------------------- catalog recipes

def spec_ingredient(value, color):
    """Ints are same-colour catalog design IDs; "dye" is this colour's dye; other strings are item IDs."""
    if isinstance(value, int):
        return ing(cat(value, color))
    if value == "dye":
        return ing(DYE_MAP[color])
    return ing(value)


def make_catalog_recipe(entry, color):
    spec = entry["recipe"]
    result_id = bid_catalog(entry["design_id"], color)
    if color in TRANS_COLORS:
        tint = TRANS_COLORS[color][2]
        return {"type": "minecraft:crafting_shapeless",
                "ingredients": [ing(cat(entry["design_id"], "white"))] + [ing(t) for t in tint],
                "result": res(result_id, 1)}
    count = spec.get("count", 1)
    if "clay" in spec:
        return {"type": "minecraft:crafting_shaped", "pattern": [" C ", "CDC", " C "],
                "key": {"C": ing("minecraft:clay_ball"), "D": ing(DYE_MAP[color])},
                "result": res(result_id, spec["clay"])}
    if "shaped" in spec:
        return {"type": "minecraft:crafting_shaped", "pattern": spec["shaped"],
                "key": {k: spec_ingredient(v, color) for k, v in spec["key"].items()},
                "result": res(result_id, count)}
    if "shapeless" in spec:
        return {"type": "minecraft:crafting_shapeless",
                "ingredients": [spec_ingredient(v, color) for v in spec["shapeless"]],
                "result": res(result_id, count)}
    raise ValueError(f"Bad recipe spec for #{entry['design_id']}: {spec}")


# ---------------------------------------------------------------- catalog preview models

def face_all(texture="#all"):
    return {k: {"texture": texture} for k in ("north", "east", "south", "west", "up", "down")}


def element(x0, y0, z0, x1, y1, z1, texture="#all"):
    return {"from": [round(x0, 4), round(y0, 4), round(z0, 4)],
            "to": [round(x1, 4), round(y1, 4), round(z1, 4)],
            "faces": face_all(texture)}


TECHNIC_STUDLESS = {"beam", "pin", "axle_pin", "axle", "bush", "half_bush", "gear", "double_gear"}


def rotated(el, axis, angle):
    if angle:
        el["rotation"] = {"origin": [8, 8, 8], "axis": axis, "angle": angle}
    return el


def technic_preview_elements(entry):
    """Studless Technic parts in part space (1 module = 16 px) scaled into one block, centred."""
    kind = entry["kind"]
    n = max(1, entry["studs_z"])
    last = 16.0 * (n - 1)
    els = []

    def fit(span):
        return min(1.0, 15.5 / span)

    if kind == "beam":
        # length along x, hole axis along z
        r = 7.2
        s = fit(last + 2 * r)
        x0 = 8 - (last / 2) * s

        def bx(a0, a1, b0, b1, c0, c1, tex="#all"):
            return element(x0 + a0 * s, 8 + b0 * s, 8 + c0 * s, x0 + a1 * s, 8 + b1 * s, 8 + c1 * s, tex)

        els.append(bx(0, last, -r, r, -r, r))
        els.append(bx(-r * 0.75, last + r * 0.75, -r * 0.75, r * 0.75, -r, r))
        els.append(bx(-r, last + r, -r * 0.4, r * 0.4, -r, r))
        for k in range(n):
            c = 16.0 * k
            els.append(bx(c - 4.4, c + 4.4, -4.4, 4.4, -r - 0.1, r + 0.1, "#hole"))
        return els

    if kind in ("pin", "axle_pin", "axle"):
        # axis along x
        span = last + 15.2
        s = fit(span)
        x0 = 8 - (last / 2) * s

        def bx(a0, a1, rr, tex="#all"):
            return element(x0 + a0 * s, 8 - rr * s, 8 - rr * s, x0 + a1 * s, 8 + rr * s, 8 + rr * s, tex)

        def cross(a0, a1):
            els.append(element(x0 + a0 * s, 8 - 4.6 * s, 8 - 1.7 * s, x0 + a1 * s, 8 + 4.6 * s, 8 + 1.7 * s))
            els.append(element(x0 + a0 * s, 8 - 1.7 * s, 8 - 4.6 * s, x0 + a1 * s, 8 + 1.7 * s, 8 + 4.6 * s))

        if kind == "pin":
            els.append(bx(-7.6, last + 7.6, 4.7))
            els.append(bx(7.3, 8.7, 5.8))
        elif kind == "axle_pin":
            els.append(bx(-7.6, 7.3, 4.7))
            els.append(bx(7.3, 8.7, 5.8))
            cross(8.7, 23.6)
        else:
            cross(-7.6, last + 7.6)
        return els

    # round parts: axis along z, facing the viewer
    if kind in ("bush", "half_bush"):
        outer, root = 6.2, None
        z0, z1 = (-7.6, 7.6) if kind == "bush" else (-7.6, -0.4)
    else:
        teeth = entry.get("teeth", 8)
        root = max(teeth - 2.4, 5.0)
        outer = teeth + 2.0
        z0, z1 = (-7.6, -0.4) if kind == "gear" else (-7.6, 7.6)
    s = fit(2 * outer)
    zc = (z0 + z1) / 2
    za, zb = 8 + (z0 - zc) * s, 8 + (z1 - zc) * s
    body = root if root is not None else outer
    for angle in (0, 22.5, 45, -22.5):
        els.append(rotated(element(8 - body * s, 8 - body * s, za, 8 + body * s, 8 + body * s, zb), "z", angle))
    if root is not None:
        w = max(0.6, 1.6 * s)
        for angle in (0, 22.5, 45, -22.5):
            els.append(rotated(element(8 - outer * s, 8 - w, za + 0.2 * s, 8 + outer * s, 8 + w, zb - 0.2 * s), "z", angle))
            els.append(rotated(element(8 - w, 8 - outer * s, za + 0.2 * s, 8 + w, 8 + outer * s, zb - 0.2 * s), "z", angle))
    arm, half = 4.6 * s, 1.7 * s
    els.append(element(8 - arm, 8 - half, za - 0.1, 8 + arm, 8 + half, zb + 0.1, "#hole"))
    els.append(element(8 - half, 8 - arm, za - 0.1, 8 + half, 8 + arm, zb + 0.1, "#hole"))
    return els


def shape_px(shape):
    return [[(x * PX, z * PX) for x, z in poly] for poly in shape]


def x_range(poly, z):
    xs = []
    for i, (ax, az) in enumerate(poly):
        bx, bz = poly[(i + 1) % len(poly)]
        if z < min(az, bz) - 1e-9 or z > max(az, bz) + 1e-9:
            continue
        if abs(bz - az) < 1e-9:
            xs += [ax, bx]
        else:
            xs.append(ax + (bx - ax) * (z - az) / (bz - az))
    return (min(xs), max(xs)) if xs else (0, 0)


def outline_strips(shape, step):
    """Boxes (x0, z0, x1, z1) in px tracing each outline polygon in strips along z (mid-strip width)."""
    out = []
    for poly in shape_px(shape):
        z_min = min(z for _, z in poly)
        z_max = max(z for _, z in poly)
        z = z_min
        while z < z_max - 1e-6:
            z1 = min(z + step, z_max)
            x0, x1 = x_range(poly, (z + z1) / 2)
            if x1 - x0 > 1e-6:
                out.append((x0, z, x1, z1))
            z = z1
    return out


def stud_fits(shape, cx, cz):
    """Same rule as LegoPiece: the stud disc must lie wholly inside one outline polygon."""
    for poly in shape_px(shape):
        area = sum(a[0] * b[1] - b[0] * a[1] for a, b in zip(poly, poly[1:] + poly[:1]))
        sign = 1 if area > 0 else -1
        inside = True
        for (ax, az), (bx, bz) in zip(poly, poly[1:] + poly[:1]):
            ex, ez = bx - ax, bz - az
            length = math.hypot(ex, ez)
            if length < 1e-9:
                continue
            if sign * (ex * (cz - az) - ez * (cx - ax)) / length < STUD_R - 1e-3:
                inside = False
                break
        if inside:
            return True
    return False


def preview_elements(entry):
    """Piece geometry (same proportions as the in-world mesh) scaled to fit one block."""
    kind = entry["kind"]
    if kind in TECHNIC_STUDLESS:
        return technic_preview_elements(entry)
    sx, sz = entry["studs_x"], entry["studs_z"]
    w, d = sx * PX, sz * PX
    h = entry.get("height", KIND_HEIGHT[kind]) * PX / 3
    if kind == "gem":
        h = 1.5 * PX / 3  # body is half a brick (LegoPartDefinition.bodyPlates)
    panel = entry.get("panel", {}) if kind == "wall_panel" else None
    has_studs = "studs" in entry or (kind not in STUDLESS_KINDS and sx * sz <= MAX_PREVIEW_STUDS
                                     and (panel is None or panel.get("rim", False)))
    raw = []

    def box(x0, y0, z0, x1, y1, z1, texture="#all"):
        raw.append((x0, y0, z0, x1, y1, z1, texture))

    if kind in ("slope", "cheese_slope", "inverted_slope"):
        slope_end = d if kind == "cheese_slope" else d - PX
        steps = 4
        for i in range(steps):
            z0 = slope_end * i / steps
            z1 = slope_end * (i + 1) / steps
            step_h = h * (i + 1) / (steps + 1) + 1
            if kind == "inverted_slope":
                box(0, h - step_h, z0, w, h, z1)
            else:
                box(0, 0, z0, w, step_h, z1)
        if slope_end < d:
            box(0, 0, slope_end, w, h, d)
    elif kind == "curved_slope":
        # Same curve as LegoPiece.curvePlates (LDraw 15068's sheared 1/8 cylinder over a half-plate lip);
        # each row's underside raised by row_bottoms plates (LegoPartDefinition.rowBottoms).
        curve = []
        for i in range(64 + 1):
            a = math.pi / 4 * i / 64
            curve.append((min(1, max(0, 1 + math.sin(a) - math.cos(a))),
                          min(1, max(0, (28.9719 * (math.cos(a) + math.sin(a)) - 24.972) / 16))))

        profile = entry.get("profile", "")

        def curve_top(depth):
            # Profiles as LegoPiece.curvePlates(def, depth): "round" quarter circle, "double*" mirrored arch.
            hp = h * 3 / PX
            mirrored = profile.startswith("double")
            t = 1 - abs(2 * depth - 1) if mirrored else depth
            if profile.endswith("round"):
                lip = max(0, hp - sz * (0.5 if mirrored else 1) * 2.5)
                return (lip + (hp - lip) * math.sqrt(max(0, 1 - (1 - t) ** 2))) * PX / 3
            f = 1.0
            for (da, fa), (db, fb) in zip(curve, curve[1:]):
                if t <= db:
                    f = fb if db - da < 1e-9 else fa + (fb - fa) * (t - da) / (db - da)
                    break
            return (0.5 + (hp - 0.5) * (f - 0.25) / 0.75) * PX / 3

        bottoms = entry.get("row_bottoms", [])
        for iz in range(sz):
            bottom = (bottoms[iz] if iz < len(bottoms) else 0) * PX / 3
            for half in range(2):
                z_a, z_b = (iz + half / 2) * PX, (iz + (half + 1) / 2) * PX
                box(0, bottom, z_a, w, curve_top((z_a + z_b) / 2 / d), z_b)
    elif kind in ("round_brick", "round_plate", "round_tile"):
        box(1, 0, 1, w - 1, h, d - 1)
        box(0.2, 0, 3, w - 0.2, h, d - 3)
        box(3, 0, 0.2, w - 3, h, d - 0.2)
    elif "shape" in entry:
        for x0, z0, x1, z1 in outline_strips(entry["shape"], max(w, d) / 16):
            box(x0, 0, z0, x1, h, z1)
    elif panel is not None:
        # Same boxes as LegoPiece.panelBoxes: wall thickness in LDU (20 per stud).
        t = panel.get("wall", 4) * PX / 20
        plate = PX / 3
        inner = h - plate / 2 if panel.get("rim") else h
        if "shape" in entry:
            for poly in entry["shape"]:
                xs = [p[0] * PX for p in poly]
                zs = [p[1] * PX for p in poly]
                box(min(xs), 0, min(zs), max(xs), plate, max(zs))
        else:
            box(0, 0, 0, w, plate, d)
        box(0, plate, d - t, w, h, d)
        if panel.get("left"):
            box(0, plate, 0, t, inner, d - t)
        if panel.get("right"):
            box(w - t, plate, 0, w, inner, d - t)
        if panel.get("divider"):
            half = 3 * PX / 40
            box(w / 2 - half, plate, 0, w / 2 + half, inner, d - t)
        if panel.get("rim"):
            box(0, inner, 0, w, h, d - t)
    elif entry.get("profile") == "log":
        for ix in range(sx):
            cx = ix * PX + 8
            box(cx - 8, 0, d * 0.15, cx + 8, h, d * 0.85)
            box(cx - 5, 0, 0, cx + 5, h, d)
    elif kind != "bar":
        box(0, 0, 0, w, h, d)

    bars = entry.get("bars")
    if bars is None and kind == "bar" and "clips" not in entry:
        bars = [{"from": [3, 1, 0], "to": [3, 1, sz * 6]}]
    for bar in bars or []:
        a = [v * UNIT for v in bar["from"]]
        b = [v * UNIT for v in bar["to"]]
        r = BAR_R * bar.get("r", 1.125) / 1.125
        lo = [min(a[k], b[k]) for k in range(3)]
        hi = [max(a[k], b[k]) for k in range(3)]
        box(*[lo[k] if hi[k] - lo[k] > 1e-6 else lo[k] - r for k in range(3)],
            *[hi[k] if hi[k] - lo[k] > 1e-6 else hi[k] + r for k in range(3)])
    for clip in entry.get("clips", []):
        c = [v * UNIT for v in clip["at"]]
        axis = "xyz".index(clip["axis"])
        half = clip.get("half", 1) * UNIT
        outer = BAR_R * clip["outer"] / 1.125 if "outer" in clip else CLIP_OUTER
        lo = [c[k] - (half if k == axis else outer) for k in range(3)]
        hi = [c[k] + (half if k == axis else outer) for k in range(3)]
        box(*lo, *hi)
    for b in entry.get("boxes", []):
        box(*[v * UNIT for v in b])

    if has_studs:
        if "studs" in entry:
            centres = [(s[0] * PX, s[1] * PX, s[2] * PX / 3 if len(s) > 2 else h, s[3] if len(s) > 3 else 1)
                       for s in entry["studs"]]
        else:
            centres = [(ix * PX + 8, iz * PX + 8, h, 1) for ix in range(sx) for iz in range(sz)
                       if not (kind == "slope" and iz != sz - 1)
                       and not ("shape" in entry and not stud_fits(entry["shape"], ix * PX + 8, iz * PX + 8))]
        for cx, cz, top, up in centres:
            tip = top + STUD_H if up > 0 else top - STUD_H
            box(cx - STUD_R, min(top, tip), cz - STUD_R, cx + STUD_R, max(top, tip), cz + STUD_R)
            spots = entry.get("open_studs")
            if spots is True or isinstance(spots, list) and any(
                    abs(s[0] * PX - cx) < 1e-3 and abs(s[1] * PX - cz) < 1e-3 for s in spots):
                face = tip if up > 0 else tip - 0.15
                box(cx - 3, face, cz - 3, cx + 3, face + 0.15, cz + 3, "#hole")

    if entry.get("grille") == "top":
        n = sz * 2 + 1
        for i in range(1, n + 1):
            z = d * i / (n + 1)
            box(2, h, z - 0.8, w - 2, h + 0.15, z + 0.8, "#hole")
    elif entry.get("grille") == "side":
        n = sx * 2 + 1
        for i in range(1, n + 1):
            x = w * i / (n + 1)
            box(x - 0.8, 2, -0.15, x + 0.8, h - 2, 0, "#hole")

    if kind == "technic_brick":
        holes = [8] if sz == 1 else [i * PX for i in range(1, sz)]
        for z in holes:
            for x in (-0.15, w - 0.05):
                box(x, 9.6 - 3.6, z - 4.2, x + 0.2, 9.6 + 3.6, z + 4.2, "#hole")
    elif kind == "technic_plate":
        holes = [i * PX for i in range(1, sz)]
        for z in holes:
            box(w / 2 - 3.2, h, z - 3.2, w / 2 + 3.2, h + 0.15, z + 3.2, "#hole")

    bracket = entry.get("bracket")
    if bracket:
        bw, bh, bx0 = bracket["width"], bracket["height"], bracket.get("x0", 0)
        plate = PX / 3
        length = bracket.get("plates", bh * 3) * plate
        thick = bracket.get("thickness", 1) * plate
        first = bracket.get("first_stud", 1.25) * plate
        inverted = bracket.get("inverted")
        y0 = 0 if inverted else h - length
        box(bx0 * PX, y0, -thick, (bx0 + bw) * PX, y0 + length, 0)
        for i in range(bw):
            for j in range(bh):
                cx = (bx0 + i) * PX + 8
                cy = first + j * 2.5 * plate if inverted else h - first - j * 2.5 * plate
                box(cx - STUD_R, cy - STUD_R, -thick - STUD_H, cx + STUD_R, cy + STUD_R, -thick)
    if entry.get("side_studs"):
        # Same spots as LegoPartDefinition.sideStudCentres: explicit [x, y] spots or row heights, else one
        # row per 2.5 plates, centred, 2.5 plates apart.
        hp = entry.get("height", KIND_HEIGHT[kind])
        n = max(1, int(hp / 2.5 + 1e-9))
        spec = entry["side_studs"] if isinstance(entry["side_studs"], list) else \
            [hp / 2 + (i - (n - 1) / 2) * 2.5 for i in range(n)]
        for face in entry.get("side_stud_faces", ["north"]):
            columns = sz if face in ("west", "east") else sx
            spots = []
            for s in spec:
                if isinstance(s, list):
                    spots.append((s[0], s[1]))
                else:
                    spots.extend((i + 0.5, s) for i in range(columns))
            for a, y in spots:
                ca, cy = a * PX, y * PX / 3
                if face == "north":
                    box(ca - STUD_R, cy - STUD_R, -STUD_H, ca + STUD_R, cy + STUD_R, 0)
                elif face == "south":
                    box(ca - STUD_R, cy - STUD_R, d, ca + STUD_R, cy + STUD_R, d + STUD_H)
                elif face == "west":
                    box(-STUD_H, cy - STUD_R, ca - STUD_R, 0, cy + STUD_R, ca + STUD_R)
                else:
                    box(w, cy - STUD_R, ca - STUD_R, w + STUD_H, cy + STUD_R, ca + STUD_R)

    lo = [min(b[k] for b in raw) for k in range(3)]
    hi = [max(b[k + 3] for b in raw) for k in range(3)]
    s = PX / max(hi[k] - lo[k] for k in range(3))
    off = [(PX - (hi[k] - lo[k]) * s) / 2 - lo[k] * s for k in range(3)]
    return [element(off[0] + x0 * s, off[1] + y0 * s, off[2] + z0 * s,
                    off[0] + x1 * s, off[1] + y1 * s, off[2] + z1 * s, tex)
            for x0, y0, z0, x1, y1, z1, tex in raw]


def write_catalog_assets(assets, entry, color, data, lang_en, lang_zh):
    design_id = entry["design_id"]
    block_id = bid_catalog(design_id, color)
    hole = "lego_flat_hole_light" if color == "black" else "lego_flat_hole"
    model = {
        "parent": "minecraft:block/block",
        "textures": {
            "all": f"legominecraft:block/lego_{color}",
            "hole": f"legominecraft:block/{hole}",
            "particle": f"legominecraft:block/lego_flat_{color}",
        },
        "elements": preview_elements(entry),
    }
    write_json(os.path.join(assets, "models/block", f"{block_id}.json"), model)
    write_json(os.path.join(assets, "blockstates", f"{block_id}.json"),
               {"variants": {"": {"model": f"legominecraft:block/{block_id}"}}})
    write_json(os.path.join(assets, "models/item", f"{block_id}.json"),
               {"parent": f"legominecraft:block/{block_id}"})
    write_json(os.path.join(data, "recipe", f"{block_id}.json"), make_catalog_recipe(entry, color))

    en_name = f"{COLOR_EN[color]} {entry['name_en']}"
    zh_name = f"{COLOR_ZH[color]}{entry['name_zh']}"
    for key in (f"block.legominecraft.{block_id}", f"item.legominecraft.{block_id}"):
        lang_en[key] = en_name
        lang_zh[key] = zh_name


# ---------------------------------------------------------------- decorative recipes

def make_decor_recipe(part, color):
    b = bid(part, color)
    brick, plate, tile = cat(3005, color), cat(3024, color), cat(3070, color)
    head, torso, legs = item("minifig_head", color), item("minifig_torso", color), item("minifig_legs", color)
    plant = item("plant", color)
    dye = DYE_MAP[color]
    recipes = {
        "minifig_head": {"type": "minecraft:crafting_shaped", "pattern": ["D", "C", "C"],
            "key": {"C": ing("minecraft:clay_ball"), "D": ing(dye)}, "result": res(b, 1)},
        "minifig_torso": {"type": "minecraft:crafting_shaped", "pattern": ["D", "B", "B"],
            "key": {"B": ing(brick), "D": ing(dye)}, "result": res(b, 1)},
        "minifig_legs": {"type": "minecraft:crafting_shaped", "pattern": ["D", "P", "P"],
            "key": {"P": ing(plate), "D": ing(dye)}, "result": res(b, 1)},
        "minifig_statue": {"type": "minecraft:crafting_shaped", "pattern": ["H", "T", "L"],
            "key": {"H": ing(head), "T": ing(torso), "L": ing(legs)}, "result": res(b, 1)},
        "animal": {"type": "minecraft:crafting_shaped", "pattern": ["D ", "CB"],
            "key": {"C": ing("minecraft:clay_ball"), "B": ing("minecraft:bone_meal"), "D": ing(dye)}, "result": res(b, 1)},
        "plant": {"type": "minecraft:crafting_shaped", "pattern": ["D ", "CS"],
            "key": {"C": ing("minecraft:clay_ball"), "S": ing("minecraft:wheat_seeds"), "D": ing(dye)}, "result": res(b, 2)},
        "leaf": {"type": "minecraft:crafting_shapeless", "ingredients": [ing(plant)], "result": res(b, 4)},
        "flower": {"type": "minecraft:crafting_shapeless", "ingredients": [ing(plant), ing(dye)], "result": res(b, 1)},
        "chair": {"type": "minecraft:crafting_shaped", "pattern": ["P ", "PB", "PP"],
            "key": {"P": ing(plate), "B": ing(brick)}, "result": res(b, 1)},
        "table": {"type": "minecraft:crafting_shaped", "pattern": ["PPP", " B ", " B "],
            "key": {"P": ing(plate), "B": ing(brick)}, "result": res(b, 1)},
        "window": {"type": "minecraft:crafting_shaped", "pattern": ["PPP", "PGP", "PPP"],
            "key": {"P": ing(plate), "G": ing("minecraft:glass_pane")}, "result": res(b, 1)},
        "door": {"type": "minecraft:crafting_shaped", "pattern": ["PP", "PP", "PP"], "key": {"P": ing(plate)}, "result": res(b, 1)},
        "lamp": {"type": "minecraft:crafting_shaped", "pattern": ["G", "P", "T"],
            "key": {"G": ing("minecraft:glowstone_dust"), "P": ing(plate), "T": ing("minecraft:torch")}, "result": res(b, 1)},
        "cable": {"type": "minecraft:crafting_shaped", "pattern": ["S", "G", "D"],
            "key": {"S": ing("minecraft:string"), "G": ing("minecraft:slime_ball"), "D": ing(dye)},
            "result": res(b, 2)},
        "fabric": {"type": "minecraft:crafting_shaped", "pattern": ["SS", "SD"],
            "key": {"S": ing("minecraft:string"), "D": ing(dye)}, "result": res(b, 1)},
        "panel": {"type": "minecraft:crafting_shaped", "pattern": ["TT", "TT"], "key": {"T": ing(tile)}, "result": res(b, 1)},
        "sign": {"type": "minecraft:crafting_shaped", "pattern": ["PPP", " S ", " S "],
            "key": {"P": ing(plate), "S": ing("minecraft:stick")}, "result": res(b, 1)},
    }
    return recipes[part]


def cleanup():
    patterns = [
        os.path.join(ROOT, "assets/legominecraft/blockstates/lego_*.json"),
        os.path.join(ROOT, "assets/legominecraft/models/block/lego_*.json"),
        os.path.join(ROOT, "assets/legominecraft/models/item/lego_*.json"),
        os.path.join(ROOT, "data/legominecraft/recipe/lego_*.json"),
        os.path.join(ROOT, "data/legominecraft/loot_table/blocks/lego_*.json"),
    ]
    for pattern in patterns:
        for path in glob.glob(pattern):
            if os.path.basename(path) in TEMPLATE_FILES:
                continue
            os.remove(path)
    # Templates for removed generic part kinds.
    for name in ("brick", "plate", "tile", "slope", "round_brick", "baseplate", "technic_beam", "connector",
                 "wheel", "gear", "cross_axle", "pin", "chain", "motor"):
        path = os.path.join(ROOT, f"assets/legominecraft/models/block/lego_{name}_template.json")
        if os.path.exists(path):
            os.remove(path)


def main():
    cleanup()
    assets = os.path.join(ROOT, "assets", "legominecraft")
    data = os.path.join(ROOT, "data", "legominecraft")
    all_ids = []
    lang_en = {
        "itemGroup.legominecraft.lego_structure": "Lego Structure",
        "itemGroup.legominecraft.lego_technic": "Lego Technic",
        "itemGroup.legominecraft.lego_minifig": "Lego Minifigures",
        "itemGroup.legominecraft.lego_decor": "Lego Decor",
        "itemGroup.legominecraft.lego_misc": "Lego Misc",
        "message.legominecraft.no_stud": "Nothing to connect to here (no stud / no anti-stud)",
        "message.legominecraft.blocked": "Not enough room for this piece",
        "message.legominecraft.vanilla_blocked": "Not enough room for this block here",
        "message.legominecraft.vanilla_offgrid": "This block only fits studs that line up with the block grid",
        "message.legominecraft.no_fit": "This part doesn't fit there (hole/axle taken or wrong shape)",
        "tooltip.legominecraft.rotate": "Sneak while placing to rotate 90°",
        "tooltip.legominecraft.element_ids": "Element ID: %s",
        "tooltip.legominecraft.pick_color": "Click to choose from all %s colors",
        "message.legominecraft.picked_up": "Picked up the build (%s pieces) as one LEGO model",
        "item.legominecraft.lego_model": "LEGO Model",
        "tooltip.legominecraft.model_pieces": "%s pieces",
        "tooltip.legominecraft.model_size": "Size %s×%s×%s blocks",
        "tooltip.legominecraft.model_place": "Right-click to place the whole build: a corner goes under the crosshair, turning to another side if blocked",
        "tooltip.legominecraft.model_rotate": "Sneak + right-click: turn it 90° (also when standing it on side studs)",
        "message.legominecraft.model_turned": "Build turned 90°",
        "item.legominecraft.lego_mover": "LEGO Mover",
        "tooltip.legominecraft.mover_target": "Moves what you placed last; a placed LEGO model moves as a whole",
        "tooltip.legominecraft.mover_keys": "While held: arrow keys move left / right / forward / back, Page Up / Down move up / down",
        "tooltip.legominecraft.mover_side": "Right-click: one stud to your right; sneak: to your left",
        "tooltip.legominecraft.mover_vertical": "Looking up / down: one plate up / down (sneak reverses)",
        "tooltip.legominecraft.mover_select": "Right-click another LEGO piece to select it instead",
        "message.legominecraft.moved": "Moved %s",
        "message.legominecraft.move_right": "right",
        "message.legominecraft.move_left": "left",
        "message.legominecraft.move_up": "up",
        "message.legominecraft.move_down": "down",
        "message.legominecraft.move_forward": "forward",
        "message.legominecraft.move_back": "back",
        "key.categories.legominecraft": "LEGO",
        "key.legominecraft.move_left": "Mover: move left",
        "key.legominecraft.move_right": "Mover: move right",
        "key.legominecraft.move_forward": "Mover: move forward",
        "key.legominecraft.move_back": "Mover: move back",
        "key.legominecraft.move_up": "Mover: move up",
        "key.legominecraft.move_down": "Mover: move down",
        "message.legominecraft.move_selected": "Piece selected: use the mover again to move it",
        "message.legominecraft.move_nothing": "Nothing to move: place a piece or click one first",
        "message.legominecraft.move_blocked": "Can't move there: something is in the way",
        "message.legominecraft.rotate_locked": "Can't turn: both sides of this clip are also joined another way",
        "message.legominecraft.rotate_blocked": "Can't turn: something is in the way",
        "message.legominecraft.rotate_technic": "Can't turn: that side has Technic parts or would leave the grid",
        "gui.legominecraft.pick_color": "Choose a color",
        "gui.legominecraft.pick_color_id": "Choose a color · #%s",
        "jade.legominecraft.part": "LEGO: %s",
        "config.jade.plugin_legominecraft.lego_info": "LEGO part info",
    }
    lang_zh = {
        "itemGroup.legominecraft.lego_structure": "乐高结构件",
        "itemGroup.legominecraft.lego_technic": "乐高机械组",
        "itemGroup.legominecraft.lego_minifig": "乐高人仔",
        "itemGroup.legominecraft.lego_decor": "乐高装饰",
        "itemGroup.legominecraft.lego_misc": "乐高杂项",
        "message.legominecraft.no_stud": "这里无法拼接（没有凸点或底部插孔）",
        "message.legominecraft.blocked": "空间不足，放不下这个零件",
        "message.legominecraft.vanilla_blocked": "空间不足，这个方块放不下",
        "message.legominecraft.vanilla_offgrid": "这种方块只能放在与方块网格对齐的凸点上",
        "message.legominecraft.no_fit": "装不上（孔/轴已被占用，或形状不匹配）",
        "tooltip.legominecraft.rotate": "放置时按住潜行可旋转 90°",
        "tooltip.legominecraft.element_ids": "元素编号：%s",
        "tooltip.legominecraft.pick_color": "点击可从全部 %s 种颜色中选择",
        "message.legominecraft.picked_up": "已把整个作品（%s 个零件）收成一个乐高模型",
        "item.legominecraft.lego_model": "乐高模型",
        "tooltip.legominecraft.model_pieces": "包含 %s 个零件",
        "tooltip.legominecraft.model_size": "尺寸 %s×%s×%s 格",
        "tooltip.legominecraft.model_place": "右键放下整个作品：角落对准准星，放不下时自动换个方向",
        "tooltip.legominecraft.model_rotate": "潜行+右键：旋转 90°（侧装到侧面凸点上时也可以）",
        "message.legominecraft.model_turned": "作品已旋转 90°",
        "item.legominecraft.lego_mover": "乐高移动工具",
        "tooltip.legominecraft.mover_target": "移动你刚放下的零件；刚放下的是乐高模型时整体移动",
        "tooltip.legominecraft.mover_keys": "手持时：方向键 ←→↑↓ 向左/右/前/后移，PageUp/PageDown 向上/下移",
        "tooltip.legominecraft.mover_side": "右键：向右移一个凸点；潜行+右键：向左",
        "tooltip.legominecraft.mover_vertical": "抬头/低头时：向上/向下移一层板（潜行反向）",
        "tooltip.legominecraft.mover_select": "右键点别的乐高零件可改为移动它",
        "message.legominecraft.moved": "已%s移动",
        "message.legominecraft.move_right": "向右",
        "message.legominecraft.move_left": "向左",
        "message.legominecraft.move_up": "向上",
        "message.legominecraft.move_down": "向下",
        "message.legominecraft.move_forward": "向前",
        "message.legominecraft.move_back": "向后",
        "key.categories.legominecraft": "乐高",
        "key.legominecraft.move_left": "移动工具：向左",
        "key.legominecraft.move_right": "移动工具：向右",
        "key.legominecraft.move_forward": "移动工具：向前",
        "key.legominecraft.move_back": "移动工具：向后",
        "key.legominecraft.move_up": "移动工具：向上",
        "key.legominecraft.move_down": "移动工具：向下",
        "message.legominecraft.move_selected": "已选中这个零件，再用一次即可移动",
        "message.legominecraft.move_nothing": "没有可移动的：先放一个零件或点选一个",
        "message.legominecraft.move_blocked": "移不过去：有东西挡着",
        "message.legominecraft.rotate_locked": "转不了：这个夹子两边还通过别的方式连在一起",
        "message.legominecraft.rotate_blocked": "转不过去：有东西挡着",
        "message.legominecraft.rotate_technic": "转不了：那一侧有机械组零件，或转后会偏离网格",
        "gui.legominecraft.pick_color": "选择颜色",
        "gui.legominecraft.pick_color_id": "选择颜色 · #%s",
        "jade.legominecraft.part": "乐高：%s",
        "config.jade.plugin_legominecraft.lego_info": "乐高零件信息",
    }

    variants = 0
    for entry in load_catalog():
        for color in COLORS + entry.get("trans_colors", []):
            write_catalog_assets(assets, entry, color, data, lang_en, lang_zh)
            variants += 1

    for part, (template, en, zh) in DECOR_TYPES.items():
        for color in COLORS:
            block_id = bid(part, color)
            all_ids.append(block_id)
            write_json(os.path.join(assets, "blockstates", f"{block_id}.json"),
                       {"variants": {"": {"model": f"legominecraft:block/{block_id}"}}})
            write_json(os.path.join(assets, "models/block", f"{block_id}.json"),
                       {"parent": f"legominecraft:block/{template}",
                        "textures": {"all": f"legominecraft:block/lego_{color}"}})
            write_json(os.path.join(assets, "models/item", f"{block_id}.json"),
                       {"parent": f"legominecraft:block/{block_id}"})
            write_json(os.path.join(data, "loot_table/blocks", f"{block_id}.json"), simple_loot(block_id))
            write_json(os.path.join(data, "recipe", f"{block_id}.json"), make_decor_recipe(part, color))
            lang_en[f"block.legominecraft.{block_id}"] = f"{COLOR_EN[color]} {en}"
            lang_zh[f"block.legominecraft.{block_id}"] = f"{COLOR_ZH[color]}{zh}"

    for color in COLORS:
        spawn_id = f"lego_minifig_spawn_{color}"
        write_json(os.path.join(data, "recipe", f"{spawn_id}.json"), {
            "type": "minecraft:crafting_shapeless",
            "ingredients": [ing(item("minifig_statue", color))],
            "result": {"id": f"legominecraft:{spawn_id}", "count": 1},
        })
        write_json(os.path.join(assets, "models/item", f"{spawn_id}.json"),
                   {"parent": f"legominecraft:block/{bid('minifig_statue', color)}"})
        lang_en[f"item.legominecraft.{spawn_id}"] = f"{COLOR_EN[color]} Lego Minifig Spawner"
        lang_zh[f"item.legominecraft.{spawn_id}"] = f"{COLOR_ZH[color]}乐高人仔生成器"

    write_json(os.path.join(assets, "models/item", "lego_model.json"),
               {"parent": f"legominecraft:block/{bid_catalog(3001, 'yellow')}"})
    write_json(os.path.join(assets, "models/item", "lego_mover.json"),
               {"parent": "minecraft:item/handheld", "textures": {"layer0": "legominecraft:item/lego_mover"}})
    write_json(os.path.join(data, "recipe", "lego_mover.json"), {
        "type": "minecraft:crafting_shaped",
        "pattern": ["IR", "S "],
        "key": {"I": ing("minecraft:iron_ingot"), "R": ing("minecraft:redstone"), "S": ing("minecraft:stick")},
        "result": res("lego_mover"),
    })

    write_json(os.path.join(ROOT, "data/minecraft/tags/block/mineable/pickaxe.json"),
               {"replace": False, "values": [f"legominecraft:{i}" for i in all_ids]})
    write_json(os.path.join(assets, "lang/en_us.json"), lang_en)
    write_json(os.path.join(assets, "lang/zh_cn.json"), lang_zh)
    print(f"Generated {len(load_catalog())} catalog parts ({variants} colour variants) + {len(all_ids)} decor blocks")


if __name__ == "__main__":
    main()
