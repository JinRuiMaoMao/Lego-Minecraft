"""Generate LEGO-accurate color textures (BrickLink / LEGO palette)."""
import os
import struct
import zlib

# Official LEGO color approximations (BrickLink color IDs)
COLORS = {
    "red": (201, 26, 9),           # Bright Red
    "blue": (0, 85, 191),          # Bright Blue
    "yellow": (242, 205, 55),      # Bright Yellow
    "green": (0, 133, 43),         # Green
    "white": (244, 244, 244),      # White
    "black": (5, 19, 29),          # Black
    "orange": (254, 138, 24),      # Bright Orange
    "lime": (166, 206, 57),        # Lime
    "cyan": (0, 183, 183),         # Dark Turquoise / Cyan
    "magenta": (144, 31, 118),     # Bright Purple
    "pink": (252, 151, 172),       # Bright Pink
    "light_gray": (160, 160, 160), # Light Bluish Gray
    "gray": (99, 99, 99),          # Dark Bluish Gray
    "brown": (106, 57, 9),         # Reddish Brown
    "purple": (172, 120, 186),     # Medium Lavender
    "light_blue": (54, 174, 191),  # Medium Azure
    "tan": (228, 205, 158),        # Tan / Brick Yellow
    "dark_tan": (149, 138, 115),   # Dark Tan
    "dark_red": (114, 14, 15),     # Dark Red
    "dark_blue": (10, 52, 99),     # Dark Blue
    "dark_green": (24, 70, 50),    # Dark Green
    "sand_green": (160, 188, 172), # Sand Green
    "light_orange": (248, 187, 61),# Bright Light Orange
    "dark_orange": (169, 85, 0),   # Dark Orange
    "lavender": (225, 213, 237),   # Lavender
    "dark_brown": (53, 33, 0),     # Dark Brown
}

# Transparent colours (Rebrickable RGB, alpha).
TRANS_COLORS = {
    "trans_clear": (252, 252, 252, 128),
    "trans_black": (99, 95, 82, 160),
    "trans_brown": (99, 95, 82, 160),
    "trans_red": (201, 26, 9, 160),
    "trans_dark_pink": (223, 102, 149, 160),
    "trans_pink": (228, 173, 200, 160),
    "trans_purple": (165, 165, 203, 160),
    "trans_dark_blue": (0, 32, 160, 160),
    "trans_medium_blue": (207, 226, 247, 160),
    "trans_light_blue": (174, 239, 236, 160),
    "trans_green": (132, 182, 141, 160),
    "trans_bright_green": (217, 228, 167, 160),
    "trans_neon_green": (248, 241, 132, 160),
    "trans_yellow": (245, 205, 47, 160),
    "trans_orange": (240, 143, 28, 160),
    "trans_neon_orange": (255, 128, 13, 160),
    "opal_trans_clear": (252, 252, 252, 176),
    "opal_trans_light_blue": (104, 188, 197, 176),
    "opal_trans_purple": (131, 32, 183, 176),
}


def write_png(path, width, height, rgba_rows):
    def chunk(tag, data):
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    raw = b"".join(b"\x00" + row for row in rgba_rows)
    ihdr = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
    png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr) + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(png)


def make_brick_texture(rgb, alpha=255):
    size = 16
    rows = []
    r, g, b = rgb
    for y in range(size):
        row = bytearray()
        for x in range(size):
            hr, hg, hb = min(r + 28, 255), min(g + 28, 255), min(b + 28, 255)
            sr, sg, sb = max(r - 35, 0), max(g - 35, 0), max(b - 35, 0)
            if y < 2 or x < 2:
                cr, cg, cb = hr, hg, hb
            elif y >= size - 2 or x >= size - 2:
                cr, cg, cb = sr, sg, sb
            else:
                cr, cg, cb = r, g, b
            row.extend((cr, cg, cb, alpha))
        rows.append(bytes(row))
    return rows


def make_flat_texture(rgb, alpha=255):
    return [bytes(bytearray((*rgb, alpha) * 16)) for _ in range(16)]


def make_hole_texture():
    size = 16
    rows = []
    cx, cy, r = 7.5, 7.5, 4.5
    for y in range(size):
        row = bytearray()
        for x in range(size):
            dist = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
            if dist <= r - 1:
                row.extend((20, 20, 20, 255))
            elif dist <= r:
                row.extend((50, 50, 50, 255))
            else:
                row.extend((120, 120, 120, 255))
        rows.append(bytes(row))
    return rows


def make_icon():
    size = 128
    rows = []
    for y in range(size):
        row = bytearray()
        for x in range(size):
            in_stud = (size * 0.35 <= x <= size * 0.65) and (size * 0.15 <= y <= size * 0.45)
            in_body = (size * 0.12 <= x <= size * 0.88) and (size * 0.45 <= y <= size * 0.92)
            if in_stud:
                row.extend((220, 40, 40, 255))
            elif in_body:
                row.extend((201, 26, 9, 255))
            else:
                row.extend((0, 0, 0, 0))
        rows.append(bytes(row))
    return rows


MOVER_ICON = [
    ".......WW.......",
    "......WWWW......",
    ".....WWWWWW.....",
    ".......WW.......",
    ".......WW.......",
    "....hh....hh....",
    "....OO....OO....",
    "..hhhhhhhhhhhh..",
    ".WhOOOOOOOOOOdW.",
    "WWhOOOOOOOOOOdWW",
    "WWhOOOOOOOOOOdWW",
    ".WhOOOOOOOOOOdW.",
    "..hOOOOOOOOOOd..",
    "..dddddddddddd..",
    "................",
    "................",
]
MOVER_PALETTE = {
    "W": (245, 245, 245, 255),
    "O": (254, 138, 24, 255),
    "h": (255, 178, 90, 255),
    "d": (190, 96, 10, 255),
    ".": (0, 0, 0, 0),
}


def make_mover_icon():
    return [bytes(bytearray(c for ch in line for c in MOVER_PALETTE[ch])) for line in MOVER_ICON]


def main():
    root = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "legominecraft", "textures")
    block_dir = os.path.join(root, "block")
    for name, rgb in COLORS.items():
        write_png(os.path.join(block_dir, f"lego_{name}.png"), 16, 16, make_brick_texture(rgb))
        write_png(os.path.join(block_dir, f"lego_flat_{name}.png"), 16, 16, make_flat_texture(rgb))
    for name, (r, g, b, a) in TRANS_COLORS.items():
        write_png(os.path.join(block_dir, f"lego_{name}.png"), 16, 16, make_brick_texture((r, g, b), a))
        write_png(os.path.join(block_dir, f"lego_flat_{name}.png"), 16, 16, make_flat_texture((r, g, b), a))
    write_png(os.path.join(block_dir, "lego_flat_hole.png"), 16, 16, make_flat_texture((22, 22, 24)))
    write_png(os.path.join(block_dir, "lego_flat_hole_light.png"), 16, 16, make_flat_texture((58, 60, 64)))
    write_png(os.path.join(block_dir, "lego_axle_hole.png"), 16, 16, make_hole_texture())
    write_png(os.path.join(block_dir, "lego_glass.png"), 16, 16, make_brick_texture((180, 220, 255)))
    write_png(os.path.join(root, "entity", "lego_minifig.png"), 32, 32, make_brick_texture(COLORS["yellow"]))
    write_png(os.path.join(root, "item", "lego_mover.png"), 16, 16, make_mover_icon())
    write_png(os.path.join(root, "..", "icon.png"), 128, 128, make_icon())
    print(f"Generated {len(COLORS)} official-color textures")


if __name__ == "__main__":
    main()
