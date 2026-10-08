"""Generate block model templates for all Lego part types."""
import json
import os

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources",
                    "assets", "legominecraft", "models", "block")


def box(from_, to, texture="#all"):
    f = {"north": {"texture": texture}, "east": {"texture": texture},
         "south": {"texture": texture}, "west": {"texture": texture},
         "up": {"texture": texture}, "down": {"texture": texture}}
    return {"from": list(from_), "to": list(to), "faces": f}


def write_template(name, elements, textures=None):
    data = {"textures": textures or {"all": "legominecraft:block/lego_red"}, "elements": elements}
    path = os.path.join(ROOT, f"{name}.json")
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def main():
    write_template("lego_animal_template", [
        box((4, 4, 4), (12, 10, 12)),
        box((10, 6, 3), (14, 9, 6)),
        box((3, 0, 5), (6, 4, 8)),
        box((10, 0, 5), (13, 4, 8)),
    ])

    write_template("lego_plant_template", [
        box((7, 0, 7), (9, 8, 9)),
        box((4, 8, 4), (12, 12, 12)),
    ])

    write_template("lego_leaf_template", [
        box((2, 7, 4), (14, 9, 12)),
    ])

    write_template("lego_flower_template", [
        box((7, 0, 7), (9, 6, 9)),
        box((4, 6, 4), (12, 10, 12)),
        box((6, 10, 6), (10, 12, 10)),
    ])

    write_template("lego_chair_template", [
        box((3, 0, 3), (13, 3, 13)),
        box((3, 3, 10), (13, 14, 13)),
        box((3, 3, 3), (5, 6, 13)),
        box((11, 3, 3), (13, 6, 13)),
    ])

    write_template("lego_table_template", [
        box((2, 8, 2), (14, 10, 14)),
        box((3, 0, 3), (6, 8, 6)),
        box((10, 0, 3), (13, 8, 6)),
        box((3, 0, 10), (6, 8, 13)),
        box((10, 0, 10), (13, 8, 13)),
    ])

    write_template("lego_window_template", [
        box((2, 2, 2), (14, 14, 14), "#frame"),
        box((4, 4, 3), (12, 12, 13), "#glass"),
    ], {"frame": "legominecraft:block/lego_white", "glass": "legominecraft:block/lego_glass"})

    write_template("lego_door_template", [
        box((3, 0, 3), (13, 14, 5)),
        box((4, 12, 2), (6, 14, 4)),
    ])

    write_template("lego_lamp_template", [
        box((6, 0, 6), (10, 2, 10)),
        box((7, 2, 7), (9, 8, 9)),
        box((4, 8, 4), (12, 12, 12)),
    ])

    write_template("lego_cable_template", [
        box((2, 7, 2), (14, 9, 6)),
        box((10, 6, 6), (14, 10, 10)),
        box((6, 7, 10), (12, 9, 14)),
    ])

    write_template("lego_fabric_template", [
        box((2, 2, 4), (14, 4, 12)),
        box((3, 4, 5), (13, 6, 11)),
    ])

    write_template("lego_panel_template", [
        box((2, 2, 4), (14, 14, 12)),
        box((4, 4, 3), (12, 12, 5)),
    ])

    write_template("lego_sign_template", [
        box((7, 0, 7), (9, 8, 9)),
        box((3, 8, 4), (13, 14, 12)),
    ])

    print("Generated 17 new model templates")


if __name__ == "__main__":
    main()
