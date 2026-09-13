"""Prepare the centered Blockbench Bedrock export and four sliding animations.

Run after exporting afl_chest_freezer.bbmodel through Blockbench's bedrock codec
to build/chest_freezer_latest_bedrock.json.
The source model remains authoritative; this only prepares runtime resources.
"""

import json
from collections import Counter
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "src/main/blockbench/afl_chest_freezer.bbmodel"
EXPORTED_GEO = ROOT / "build/chest_freezer_latest_bedrock.json"
ASSETS = ROOT / "src/main/resources/assets/apocalypse_firstlight"
GEO = ASSETS / "geo/chest_freezer.geo.json"
ANIM = ASSETS / "animations/chest_freezer.animation.json"

source = json.loads(SOURCE.read_text(encoding="utf-8"))
geo = json.loads(EXPORTED_GEO.read_text(encoding="utf-8"))
geometry = geo["minecraft:geometry"][0]
assert geometry["bones"][0]["pivot"] == [0, 0, 0], "Export the latest centered bbmodel first"
source_cubes = Counter(
    tuple(round(value, 3) for value in (
        -cube["to"][0], cube["from"][1], cube["from"][2],
        *[cube["to"][axis] - cube["from"][axis] for axis in range(3)]
    ))
    for cube in source["elements"] if cube.get("type") == "cube"
)
export_cubes = Counter(
    tuple(round(value, 3) for value in (*cube["origin"], *cube["size"]))
    for bone in geometry["bones"] for cube in bone.get("cubes", [])
)
assert source_cubes == export_cubes, "The Bedrock export does not match the saved bbmodel"
cube_bounds = [
    (
        [cube["origin"][axis] for axis in range(3)],
        [cube["origin"][axis] + cube["size"][axis] for axis in range(3)],
    )
    for bone in geometry["bones"] for cube in bone.get("cubes", [])
]
minimum = [min(bounds[0][axis] for bounds in cube_bounds) for axis in range(3)]
maximum = [max(bounds[1][axis] for bounds in cube_bounds) for axis in range(3)]
assert all(abs(actual - expected) < 0.001 for actual, expected in zip(minimum, [-16, 0, -8])) \
    and all(abs(actual - expected) < 0.001 for actual, expected in zip(maximum, [16, 15.8, 8])), (
    f"Unexpected geometry bounds: {minimum}..{maximum}"
)
geometry["description"]["identifier"] = "geometry.chest_freezer"
geometry["description"]["visible_bounds_width"] = 4
geometry["description"]["visible_bounds_height"] = 2
geometry["description"]["visible_bounds_offset"] = [0, 0.5, 0]

assert {"left_lid", "right_lid", "left_lid_glass", "right_lid_glass"} <= {
    bone["name"] for bone in geometry["bones"]
}
GEO.write_text(json.dumps(geo, indent=2) + "\n", encoding="utf-8")

animations = {}
expected = ("left_lid_open", "left_lid_close", "right_lid_open", "right_lid_close")
for animation in source["animations"]:
    if animation["name"] not in expected:
        continue
    name = animation["name"]
    lid = name.split("_lid_")[0] + "_lid"
    animator = next(iter(animation["animators"].values()))
    assert animator["name"] == lid
    positions = {}
    for keyframe in animator["keyframes"]:
        assert keyframe["channel"] == "position"
        point = keyframe["data_points"][0]
        # Bedrock exporter mirrors Blockbench's X geometry axis.
        positions[f"{keyframe['time']:g}"] = {
            "vector": [-float(point["x"]), float(point["y"]), float(point["z"])]
        }
    animations[name] = {
        "loop": False,
        "animation_length": animation["length"],
        "bones": {lid: {"position": positions}},
    }

assert set(animations) == set(expected)
animations["left_lid_open_pose"] = {
    "loop": True,
    "animation_length": 0.05,
    "bones": {"left_lid": {"position": [-14.15, 0, 0]}},
}
animations["right_lid_open_pose"] = {
    "loop": True,
    "animation_length": 0.05,
    "bones": {"right_lid": {"position": [14.15, 0, 0]}},
}
for lid in ("left", "right"):
    animations[f"{lid}_lid_closed_pose"] = {
        "loop": True,
        "animation_length": 0.05,
        "bones": {f"{lid}_lid": {"position": [0, 0, 0]}},
    }
ANIM.write_text(json.dumps({"format_version": "1.8.0", "animations": animations}, indent=2) + "\n", encoding="utf-8")
print(f"Wrote {GEO} ({len(geometry['bones'])} bones) and {ANIM} ({len(animations)} animations)")
