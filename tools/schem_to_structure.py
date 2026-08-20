#!/usr/bin/env python3
"""Convert Sponge .schem files to Minecraft 1.20.1 Structure Template NBT.

This tool intentionally has no third-party dependency.  It reads/writes the
gzip NBT used by Sponge schematics and vanilla structure templates directly.
"""
from __future__ import annotations

import argparse
import gzip
import io
import os
import struct
import sys
import tempfile
from collections import Counter, deque
from pathlib import Path

TAG_END, TAG_BYTE, TAG_SHORT, TAG_INT, TAG_LONG = 0, 1, 2, 3, 4
TAG_FLOAT, TAG_DOUBLE, TAG_BYTE_ARRAY, TAG_STRING = 5, 6, 7, 8
TAG_LIST, TAG_COMPOUND, TAG_INT_ARRAY, TAG_LONG_ARRAY = 9, 10, 11, 12
MC_1201_DATA_VERSION = 3465
AIR_LIKE = {"minecraft:air", "minecraft:cave_air", "minecraft:void_air"}


class NbtError(ValueError):
    pass


def _read_exact(stream, size):
    data = stream.read(size)
    if len(data) != size:
        raise NbtError("unexpected end of NBT data")
    return data


def _read_string(stream):
    size = struct.unpack(">H", _read_exact(stream, 2))[0]
    return _read_exact(stream, size).decode("utf-8")


def _read_payload(stream, tag_type):
    if tag_type == TAG_END:
        return None
    if tag_type == TAG_BYTE:
        return struct.unpack(">b", _read_exact(stream, 1))[0]
    if tag_type == TAG_SHORT:
        return struct.unpack(">h", _read_exact(stream, 2))[0]
    if tag_type == TAG_INT:
        return struct.unpack(">i", _read_exact(stream, 4))[0]
    if tag_type == TAG_LONG:
        return struct.unpack(">q", _read_exact(stream, 8))[0]
    if tag_type == TAG_FLOAT:
        return struct.unpack(">f", _read_exact(stream, 4))[0]
    if tag_type == TAG_DOUBLE:
        return struct.unpack(">d", _read_exact(stream, 8))[0]
    if tag_type == TAG_BYTE_ARRAY:
        size = struct.unpack(">i", _read_exact(stream, 4))[0]
        if size < 0:
            raise NbtError("negative byte array length")
        return _read_exact(stream, size)
    if tag_type == TAG_STRING:
        return _read_string(stream)
    if tag_type == TAG_LIST:
        element_type = struct.unpack(">b", _read_exact(stream, 1))[0]
        size = struct.unpack(">i", _read_exact(stream, 4))[0]
        if size < 0:
            raise NbtError("negative list length")
        return (element_type, [_read_payload(stream, element_type) for _ in range(size)])
    if tag_type == TAG_COMPOUND:
        result = {}
        while True:
            child_type = struct.unpack(">b", _read_exact(stream, 1))[0]
            if child_type == TAG_END:
                return result
            name = _read_string(stream)
            result[name] = (child_type, _read_payload(stream, child_type))
    if tag_type == TAG_INT_ARRAY or tag_type == TAG_LONG_ARRAY:
        item_size = 4 if tag_type == TAG_INT_ARRAY else 8
        size = struct.unpack(">i", _read_exact(stream, 4))[0]
        if size < 0:
            raise NbtError("negative array length")
        return [struct.unpack(">i" if item_size == 4 else ">q", _read_exact(stream, item_size))[0]
                for _ in range(size)]
    raise NbtError(f"unsupported NBT tag type {tag_type}")


def read_nbt(path):
    with gzip.open(path, "rb") as source:
        tag_type = struct.unpack(">b", _read_exact(source, 1))[0]
        if tag_type != TAG_COMPOUND:
            raise NbtError("root tag is not a Compound")
        root_name = _read_string(source)
        return root_name, _read_payload(source, TAG_COMPOUND)


def _write_string(stream, value):
    data = value.encode("utf-8")
    if len(data) > 65535:
        raise NbtError("NBT string is too long")
    stream.write(struct.pack(">H", len(data)) + data)


def _write_payload(stream, tag_type, value):
    if tag_type == TAG_BYTE:
        stream.write(struct.pack(">b", int(value)))
    elif tag_type == TAG_SHORT:
        stream.write(struct.pack(">h", int(value)))
    elif tag_type == TAG_INT:
        stream.write(struct.pack(">i", int(value)))
    elif tag_type == TAG_LONG:
        stream.write(struct.pack(">q", int(value)))
    elif tag_type == TAG_FLOAT:
        stream.write(struct.pack(">f", float(value)))
    elif tag_type == TAG_DOUBLE:
        stream.write(struct.pack(">d", float(value)))
    elif tag_type == TAG_BYTE_ARRAY:
        stream.write(struct.pack(">i", len(value)) + bytes(value))
    elif tag_type == TAG_STRING:
        _write_string(stream, value)
    elif tag_type == TAG_LIST:
        element_type, values = value
        stream.write(struct.pack(">bi", element_type, len(values)))
        for item in values:
            _write_payload(stream, element_type, item)
    elif tag_type == TAG_COMPOUND:
        for name, (child_type, child_value) in value.items():
            stream.write(struct.pack(">b", child_type))
            _write_string(stream, name)
            _write_payload(stream, child_type, child_value)
        stream.write(b"\0")
    elif tag_type in (TAG_INT_ARRAY, TAG_LONG_ARRAY):
        item_format = ">i" if tag_type == TAG_INT_ARRAY else ">q"
        stream.write(struct.pack(">i", len(value)))
        for item in value:
            stream.write(struct.pack(item_format, int(item)))
    else:
        raise NbtError(f"cannot write tag type {tag_type}")


def write_nbt(path, root):
    raw = io.BytesIO()
    raw.write(struct.pack(">b", TAG_COMPOUND))
    _write_string(raw, "")
    _write_payload(raw, TAG_COMPOUND, root)
    with gzip.open(path, "wb") as target:
        target.write(raw.getvalue())


def scalar(compound, name, default=None):
    item = compound.get(name)
    return default if item is None else item[1]


def require_scalar(compound, name):
    if name not in compound or compound[name][0] not in (TAG_BYTE, TAG_SHORT, TAG_INT, TAG_LONG):
        raise NbtError(f"missing integer field {name}")
    return int(compound[name][1])


def list_value(compound, name, expected_type=None):
    item = compound.get(name)
    if item is None or item[0] != TAG_LIST:
        raise NbtError(f"missing ListTag field {name}")
    element_type, values = item[1]
    if expected_type is not None and element_type != expected_type:
        raise NbtError(f"{name} has element type {element_type}, expected {expected_type}")
    return values


def parse_state(text):
    text = text.strip()
    if not text or ":" not in text.split("[", 1)[0]:
        raise NbtError(f"unresolved block state: {text!r}")
    name, _, properties = text.partition("[")
    if properties and not properties.endswith("]"):
        raise NbtError(f"malformed block state: {text!r}")
    props = {}
    if properties:
        for pair in properties[:-1].split(","):
            if "=" not in pair:
                raise NbtError(f"malformed block property in {text!r}")
            key, value = pair.split("=", 1)
            if not key or not value or key in props:
                raise NbtError(f"malformed block property in {text!r}")
            props[key] = value
    result = {"Name": (TAG_STRING, name)}
    if props:
        result["Properties"] = (TAG_COMPOUND,
                                  {key: (TAG_STRING, value) for key, value in sorted(props.items())})
    return result, name


def decode_varints(data, count):
    values, index = [], 0
    for _ in range(count):
        value, shift = 0, 0
        while True:
            if index >= len(data) or shift > 35:
                raise NbtError("truncated or oversized Sponge BlockData varint")
            byte = data[index]
            index += 1
            value |= (byte & 0x7f) << shift
            if not byte & 0x80:
                break
            shift += 7
        values.append(value)
    if index != len(data):
        raise NbtError(f"BlockData has {len(data) - index} trailing bytes")
    return values


def position_from_value(value):
    if isinstance(value, list) and len(value) == 3:
        return tuple(int(x) for x in value)
    return None


def get_be_position(be):
    pos = position_from_value(scalar(be, "Pos"))
    if pos is not None:
        return pos
    values = [scalar(be, key) for key in ("x", "y", "z")]
    if all(value is not None for value in values):
        return tuple(int(value) for value in values)
    raise NbtError("BlockEntity has no Pos or x/y/z coordinates")


def strip_be_coordinates(be):
    return {key: value for key, value in be.items() if key not in {"Pos", "x", "y", "z"}}


def make_int_list(values):
    return (TAG_LIST, (TAG_INT, [(int(value)) for value in values]))


def make_string_list(values):
    return (TAG_LIST, (TAG_STRING, list(values)))


def state_name(palette_state):
    return scalar(palette_state, "Name", "")


def key(x, y, z, sx, sz):
    return x + sx * (z + sz * y)


def decode_schematic(root):
    # Sponge v2/v3 schematics are normally rooted directly; accepting a
    # wrapped Schematic compound costs little and helps older exporters.
    if "Schematic" in root and root["Schematic"][0] == TAG_COMPOUND:
        root = root["Schematic"][1]
    width, height, length = (require_scalar(root, name) for name in ("Width", "Height", "Length"))
    if min(width, height, length) <= 0:
        raise NbtError("Width, Height and Length must be positive")
    palette = root.get("Palette")
    if palette is None or palette[0] != TAG_COMPOUND:
        raise NbtError("Sponge schematic is missing Compound Palette")
    palette_entries = []
    palette_by_index = {}
    for state_string, item in palette[1].items():
        if item[0] not in (TAG_BYTE, TAG_SHORT, TAG_INT, TAG_LONG):
            raise NbtError(f"Palette index for {state_string!r} is not an integer")
        parsed, _ = parse_state(state_string)
        index = int(item[1])
        if index in palette_by_index:
            raise NbtError(f"duplicate Sponge palette index {index}")
        palette_by_index[index] = parsed
        palette_entries.append((index, parsed, state_string))
    palette_entries.sort(key=lambda item: item[0])
    if not palette_entries or [index for index, _, _ in palette_entries] != list(range(len(palette_entries))):
        raise NbtError("Sponge Palette indices must be contiguous from zero")
    block_data_item = root.get("BlockData")
    if block_data_item is None or block_data_item[0] != TAG_BYTE_ARRAY:
        raise NbtError("Sponge schematic is missing ByteArray BlockData")
    cells = width * height * length
    block_indices = decode_varints(block_data_item[1], cells)
    if any(index not in palette_by_index for index in block_indices):
        raise NbtError("BlockData references an unresolved palette index")
    offset_item = root.get("Offset")
    source_offset = scalar(root, "Offset", None)
    if offset_item is not None and offset_item[0] == TAG_INT_ARRAY:
        source_offset = list(offset_item[1])
    elif offset_item is not None and offset_item[0] == TAG_LIST:
        source_offset = list(offset_item[1][1])
    metadata = root.get("Metadata")
    block_entities = []
    if root.get("BlockEntities") is not None:
        block_entities = list_value(root, "BlockEntities", TAG_COMPOUND)
    entities = []
    if root.get("Entities") is not None:
        entities = list_value(root, "Entities", TAG_COMPOUND)
    return root, (width, height, length), palette_by_index, block_indices, source_offset, metadata, block_entities, entities


def convert(input_path, output_path, force=False, dry_run=False, drop_entities=False):
    _, source = read_nbt(input_path)
    root, size, palette_by_index, indices, source_offset, metadata, source_bes, entities = decode_schematic(source)
    warnings, errors = [], []
    if entities and not drop_entities:
        types = Counter(str(scalar(entity, "id", "<missing>")) for entity in entities)
        errors.append("Entities found (default policy refuses conversion): " + ", ".join(f"{k} x{v}" for k, v in types.items()))
    if entities and drop_entities:
        warnings.append(f"Dropped {len(entities)} source entities by explicit --drop-entities")

    sx, sy, sz = size
    states = [palette_by_index[index] for index in indices]
    names = [state_name(state) for state in states]
    total_cells = len(states)
    exterior = set()
    queue = deque()
    def add_if_exterior(x, y, z):
        position = key(x, y, z, sx, sz)
        if names[position] in AIR_LIKE and position not in exterior:
            exterior.add(position); queue.append(position)
    for y in range(sy):
        for z in range(sz):
            add_if_exterior(0, y, z); add_if_exterior(sx - 1, y, z)
    for y in range(sy):
        for x in range(sx):
            add_if_exterior(x, y, 0); add_if_exterior(x, y, sz - 1)
    for z in range(sz):
        for x in range(sx):
            add_if_exterior(x, 0, z); add_if_exterior(x, sy - 1, z)
    directions = ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1))
    while queue:
        position = queue.popleft()
        x = position % sx; y = position // (sx * sz); z = (position // sx) % sz
        for dx, dy, dz in directions:
            nx, ny, nz = x + dx, y + dy, z + dz
            if 0 <= nx < sx and 0 <= ny < sy and 0 <= nz < sz:
                add_if_exterior(nx, ny, nz)
    interior = {position for position, name in enumerate(names) if name in AIR_LIKE and position not in exterior}

    be_by_position = {}
    for index, be in enumerate(source_bes):
        position = get_be_position(be)
        if not (0 <= position[0] < sx and 0 <= position[1] < sy and 0 <= position[2] < sz):
            errors.append(f"orphan BlockEntity[{index}] outside bounds at {position}")
        position_key = key(*position, sx, sz)
        if position_key in be_by_position:
            errors.append(f"duplicate BlockEntity position at {position}")
        else:
            be_by_position[position_key] = strip_be_coordinates(be)
        if position_key < len(names) and names[position_key] in AIR_LIKE:
            errors.append(f"BlockEntity[{index}] is attached to AIR at {position}")

    output_palette, palette_indices = [], {}
    blocks = []
    custom_counts = Counter()
    namespace_counts = Counter()
    for position, state in enumerate(states):
        if position in exterior:
            continue
        state_key = repr(state)
        if state_key not in palette_indices:
            palette_indices[state_key] = len(output_palette); output_palette.append(state)
        x = position % sx; y = position // (sx * sz); z = (position // sx) % sz
        block = {"pos": make_int_list((x, y, z)), "state": (TAG_INT, palette_indices[state_key])}
        if position in be_by_position:
            block["nbt"] = (TAG_COMPOUND, be_by_position[position])
        blocks.append((TAG_COMPOUND, block))
        namespace = state_name(state).split(":", 1)[0]
        namespace_counts[namespace] += 1
        if namespace == "apocalypse_firstlight":
            custom_counts[state_name(state)] += 1
    root_out = {
        "DataVersion": (TAG_INT, int(scalar(root, "DataVersion", MC_1201_DATA_VERSION))),
        "size": make_int_list(size),
        "palette": (TAG_LIST, (TAG_COMPOUND, output_palette)),
        "blocks": (TAG_LIST, (TAG_COMPOUND, [value for _, value in blocks])),
        "entities": (TAG_LIST, (TAG_COMPOUND, [])),
    }
    report = {
        "Input": str(input_path), "Output": str(output_path), "DataVersion": root_out["DataVersion"][1],
        "Size": f"{sx} x {sy} x {sz}", "Source Offset": source_offset if source_offset is not None else "<absent>",
        "Source palette entries": len(palette_by_index), "Output palette entries": len(output_palette),
        "Total cells": total_cells, "Non-air cells": sum(name not in AIR_LIKE for name in names),
        "Exterior AIR omitted": len(exterior), "Interior AIR preserved": len(interior),
        "Interior AIR written": len(interior), "Blocks written": len(blocks),
        "BlockEntities preserved": len(be_by_position),
        "Entities found": len(entities), "Namespaces used": dict(namespace_counts),
        "Custom blocks": dict(custom_counts), "Warnings": warnings, "Errors": errors,
    }
    if errors:
        return report, None
    if not dry_run and output_path.exists() and not force:
        report["Errors"].append("Output exists; pass --force to overwrite")
        return report, None

    # Both normal conversion and --dry-run use the exact same serialization /
    # gzip / read-back path.  Dry-run writes only an isolated temporary file.
    temporary = None
    try:
        if dry_run:
            handle = tempfile.NamedTemporaryFile(prefix="afl_schem_", suffix=".nbt", delete=False)
            temporary = Path(handle.name)
            handle.close()
        else:
            output_path.parent.mkdir(parents=True, exist_ok=True)
            temporary = output_path.with_name(output_path.name + ".tmp")
        write_nbt(temporary, root_out)
        readback = validate_output(temporary, root_out, size, len(blocks), len(output_palette), exterior, interior,
                                   len(names) - len(exterior) - len(interior), len(be_by_position))
        report["Interior AIR read-back"] = readback["interior_air"]
        report["Non-air read-back"] = readback["non_air"]
        report["BlockEntities read-back"] = readback["block_entities"]
        if not dry_run:
            os.replace(temporary, output_path)
            temporary = None
    finally:
        if temporary is not None:
            try: temporary.unlink()
            except FileNotFoundError: pass
    return report, root_out


def validate_output(path, expected, size, block_count, palette_count, exterior, interior,
                    expected_non_air, expected_block_entities):
    _, root = read_nbt(path)
    if require_scalar(root, "DataVersion") != require_scalar(expected, "DataVersion"):
        raise NbtError("read-back DataVersion mismatch")
    if tuple(list_value(root, "size", TAG_INT)) != tuple(size):
        raise NbtError("read-back size mismatch")
    palette = list_value(root, "palette", TAG_COMPOUND); blocks = list_value(root, "blocks", TAG_COMPOUND)
    if len(palette) != palette_count or len(blocks) != block_count:
        raise NbtError("read-back palette/block count mismatch")
    seen = set()
    interior_air = 0
    non_air = 0
    block_entities = 0
    for index, block in enumerate(blocks):
        pos = tuple(list_value(block, "pos", TAG_INT))
        if len(pos) != 3 or not (0 <= pos[0] < size[0] and 0 <= pos[1] < size[1] and 0 <= pos[2] < size[2]):
            raise NbtError(f"read-back blocks[{index}] position out of bounds")
        if pos in seen: raise NbtError(f"read-back duplicate block position {pos}")
        seen.add(pos)
        state = require_scalar(block, "state")
        if not 0 <= state < len(palette): raise NbtError(f"read-back invalid palette index {state}")
        name = state_name(palette[state])
        if name in AIR_LIKE:
            interior_air += 1
        else:
            non_air += 1
        if "nbt" in block:
            block_entities += 1
        if "nbt" in block and name in AIR_LIKE:
            raise NbtError(f"read-back BlockEntity attached to AIR at {pos}")
    expected_exterior = {(
        position % size[0],
        position // (size[0] * size[2]),
        (position // size[0]) % size[2],
    ) for position in exterior}
    expected_interior = {(
        position % size[0],
        position // (size[0] * size[2]),
        (position // size[0]) % size[2],
    ) for position in interior}
    if any(position in seen for position in expected_exterior):
        raise NbtError("read-back exterior AIR was not omitted")
    missing_interior = expected_interior - seen
    if missing_interior:
        raise NbtError(f"read-back interior AIR was not preserved; missing {len(missing_interior)} positions")
    for position in expected_interior:
        block = next(block for block in blocks if tuple(list_value(block, "pos", TAG_INT)) == position)
        state = require_scalar(block, "state")
        if state_name(palette[state]) not in AIR_LIKE:
            raise NbtError(f"read-back interior position {position} is not air-like")
    if non_air != expected_non_air:
        raise NbtError(f"read-back non-air count mismatch: expected {expected_non_air}, got {non_air}")
    if interior_air != len(expected_interior):
        raise NbtError(f"read-back interior AIR count mismatch: expected {len(expected_interior)}, got {interior_air}")
    if block_entities != expected_block_entities:
        raise NbtError(f"read-back BlockEntity count mismatch: expected {expected_block_entities}, got {block_entities}")
    return {"interior_air": interior_air, "non_air": non_air, "block_entities": block_entities}


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("input", type=Path)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--force", action="store_true", help="allow replacing an existing output")
    parser.add_argument("--dry-run", action="store_true", help="parse and validate without writing output")
    parser.add_argument("--drop-entities", action="store_true", help="explicitly discard source entities")
    args = parser.parse_args(argv)
    output = args.output or args.input.with_suffix(".nbt")
    try:
        report, _ = convert(args.input, output, args.force, args.dry_run, args.drop_entities)
        for name, value in report.items(): print(f"{name}: {value}")
        if report["Errors"]:
            return 1
        print("Result: DRY-RUN VALID" if args.dry_run else "Result: CONVERTED AND VALIDATED")
        return 0
    except Exception as error:
        print(f"ERROR: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
