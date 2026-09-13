"""Double the four chest-freezer animation timelines without touching model geometry.

The bbmodel is a compact JSON file. Re-serialize only its animations array so
the editable geometry, texture references, groups, and other project metadata
remain byte-for-byte unchanged.
"""

import json
from pathlib import Path


SOURCE = Path(__file__).resolve().parents[1] / "src/main/blockbench/afl_chest_freezer.bbmodel"
EXPECTED = {
    "left_lid_open", "left_lid_close", "right_lid_open", "right_lid_close"
}


def main() -> None:
    source = SOURCE.read_text(encoding="utf-8")
    marker = '"animations":'
    start = source.index(marker) + len(marker)
    while source[start].isspace():
        start += 1
    animations, length = json.JSONDecoder().raw_decode(source[start:])
    assert len(animations) == 4 and {animation["name"] for animation in animations} == EXPECTED

    old_lengths = {animation["length"] for animation in animations}
    if old_lengths == {0.7}:
        print("Chest freezer animations are already 0.70 seconds; no source change")
        return
    assert old_lengths == {0.35}, f"Unexpected animation lengths: {old_lengths}"

    for animation in animations:
        animator = next(iter(animation["animators"].values()))
        assert animator["name"] == animation["name"].split("_lid_")[0] + "_lid"
        keyframes = animator["keyframes"]
        assert len(keyframes) == 15
        assert all(keyframe["channel"] == "position" and
                   abs(keyframe["time"] - index * 0.025) < 0.000001
                   for index, keyframe in enumerate(keyframes))
        for keyframe in keyframes:
            keyframe["time"] = round(keyframe["time"] * 2, 6)
        animation["length"] = 0.7

    updated = source[:start] + json.dumps(animations, ensure_ascii=False, separators=(",", ":")) + source[start + length:]
    json.loads(updated)
    SOURCE.write_text(updated, encoding="utf-8")
    print("Retimed four bbmodel animations to 0.70 seconds (15 keys each)")


if __name__ == "__main__":
    main()
