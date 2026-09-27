"""Frame pre-rendered native-gun GUI sprites on one transparent square canvas.

The input PNGs are already projections of the Blockbench models in their
inventory poses. Their alpha bounds are therefore the visible screen-space
bounds; no world-model scale or item-in-hand transform enters this calculation.
Requires Pillow. Run normally to generate, or pass --check to verify outputs.
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path

from PIL import Image, ImageChops


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "src/main/blockbench/inventory_icons"
TEXTURES = ROOT / "src/main/resources/assets/apocalypse_firstlight/textures/item"
MODELS = ROOT / "src/main/resources/assets/apocalypse_firstlight/models/item"
CANVAS = 256

# Longest-axis occupancy is a class policy, never a world-size multiplier.
CLASS_FILL = {
    "PISTOL": 0.82,
    "LARGE_PISTOL": 0.88,
    "LONG_GUN": 0.88,
    "EXTRA_LONG_GUN": 0.89,
}
WEAPONS = {
    "p9_01": "PISTOL",
    "blackridge_50": "LARGE_PISTOL",
    "br51_01": "LONG_GUN",
    "hr55": "LONG_GUN",
    "silverwood_12": "EXTRA_LONG_GUN",
}


def frame(source: Image.Image, weapon_class: str) -> Image.Image:
    source = source.convert("RGBA")
    bounds = source.getchannel("A").getbbox()
    if bounds is None:
        raise ValueError("Source has no visible pixels")
    projected = source.crop(bounds)
    width, height = projected.size
    target = CANVAS * CLASS_FILL[weapon_class]
    scale = min(target / width, target / height)
    fitted = projected.resize(
        (max(1, round(width * scale)), max(1, round(height * scale))),
        Image.Resampling.LANCZOS,
    )
    result = Image.new("RGBA", (CANVAS, CANVAS), (0, 0, 0, 0))
    result.alpha_composite(fitted, ((CANVAS - fitted.width) // 2, (CANVAS - fitted.height) // 2))
    return result


def audit(image: Image.Image) -> dict:
    left, top, right, bottom = image.getchannel("A").getbbox()
    width, height = image.size
    return {
        "canvas": [width, height],
        "alpha_bounds": [left, top, right - 1, bottom - 1],
        "fill_x": round((right - left) / width, 4),
        "fill_y": round((bottom - top) / height, 4),
        "longest_axis_fill": round(max((right - left) / width, (bottom - top) / height), 4),
        "center_offset_px": [round((left + right - width) / 2, 2), round((top + bottom - height) / 2, 2)],
    }


def check_model(weapon: str) -> None:
    model = json.loads((MODELS / f"{weapon}.json").read_text(encoding="utf-8"))
    if model.get("loader") != "forge:separate_transforms":
        raise ValueError(f"{weapon}: expected separate GUI/in-hand transforms")
    gui = model["perspectives"]["gui"]
    if gui.get("textures", {}).get("layer0") != f"apocalypse_firstlight:item/{weapon}_inventory":
        raise ValueError(f"{weapon}: GUI texture route differs from output")
    if gui.get("display", {}).get("gui", {}).get("scale", [1, 1, 1]) != [1, 1, 1]:
        raise ValueError(f"{weapon}: legacy per-weapon GUI scale remains")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="verify checked-in icons without writing")
    args = parser.parse_args()
    results = {}
    for weapon, weapon_class in WEAPONS.items():
        check_model(weapon)
        source_path = SOURCE / f"{weapon}_inventory.png"
        output_path = TEXTURES / f"{weapon}_inventory.png"
        with Image.open(source_path) as original:
            output = frame(original, weapon_class)
        if args.check:
            with Image.open(output_path) as saved:
                saved = saved.convert("RGBA")
                if saved.size != output.size or ImageChops.difference(saved, output).getbbox():
                    raise ValueError(f"{weapon}: output differs from normalized source")
        else:
            output.save(output_path, format="PNG", optimize=True)
        results[weapon] = {"class": weapon_class, **audit(output)}
    print(json.dumps({"canvas_power_of_two": CANVAS > 0 and CANVAS & (CANVAS - 1) == 0,
                      "weapons": results}, indent=2))


if __name__ == "__main__":
    main()
