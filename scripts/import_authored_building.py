"""Import an approved export pair, not a city-pool registration. Standard library only."""
import argparse
import gzip
import json
from pathlib import Path
import re
import shutil
import struct
import tempfile

CATEGORIES = set('HIGHRISE_OFFICE HIGHRISE_APARTMENT MIDRISE_OFFICE MIDRISE_APARTMENT COMMERCIAL RESIDENTIAL INDUSTRIAL WAREHOUSE UTILITY FILLER SPECIAL_POI'.split())
ZONES = set('CORE MIXED COMMERCIAL RESIDENTIAL INDUSTRIAL EDGE'.split())
ROTATIONS = set('NONE CLOCKWISE_90 CLOCKWISE_180 COUNTERCLOCKWISE_90'.split())

def check_nbt(path, expected):
    # Read/skip standard binary NBT without a private structure format or external library.
    with gzip.open(path, 'rb') as stream:
        data = memoryview(stream.read(512 * 1024 * 1024 + 1))
    if len(data) > 512 * 1024 * 1024:
        raise ValueError('Uncompressed NBT exceeds 512 MiB safety limit')
    pos = 0
    def take(n):
        nonlocal pos
        if n < 0 or pos + n > len(data):
            raise ValueError('Truncated/invalid NBT')
        result = data[pos:pos+n]; pos += n
        return result
    def integer(): return struct.unpack('>i', take(4))[0]
    def string(): return bytes(take(struct.unpack('>H', take(2))[0])).decode('utf-8', errors='replace')
    def payload(kind, depth=0):
        if depth > 128: raise ValueError('NBT nesting limit')
        if kind in (1, 2, 3, 4, 5, 6): take({1:1, 2:2, 3:4, 4:8, 5:4, 6:8}[kind])
        elif kind in (7, 11, 12): take(integer() * {7:1, 11:4, 12:8}[kind])
        elif kind == 8: string()
        elif kind == 9:
            child = take(1)[0]; count = integer()
            if count < 0 or count > len(data): raise ValueError('Invalid NBT list length')
            for _ in range(count): payload(child, depth+1)
        elif kind == 10:
            while True:
                child = take(1)[0]
                if child == 0: break
                string(); payload(child, depth+1)
        else: raise ValueError(f'Invalid NBT tag {kind}')
    if take(1)[0] != 10: raise ValueError('Expected compound Structure NBT')
    string(); size = None; entities = None; fields = set()
    while True:
        kind = take(1)[0]
        if kind == 0: break
        name = string(); fields.add(name)
        if name == 'size':
            if kind != 9 or take(1)[0] != 3 or integer() != 3: raise ValueError('Invalid structure size tag')
            size = [integer(), integer(), integer()]
        elif name == 'entities':
            if kind != 9: raise ValueError('Invalid entities tag')
            take(1); entities = integer()
            if entities != 0: raise ValueError('Entities must not be exported')
        else: payload(kind)
    if pos != len(data) or size != expected or entities != 0 or 'blocks' not in fields or not ({'palette', 'palettes'} & fields):
        raise ValueError('Not a matching standard Minecraft Structure NBT')

def import_pair(source, project, overwrite=False):
    source = Path(source).resolve(strict=True)
    if source.suffix != '.nbt': raise ValueError('Input must be .nbt')
    meta_path = source.with_suffix('.json')
    meta = json.loads(meta_path.read_text(encoding='utf-8'))
    ident = meta.get('id', '')
    if not isinstance(ident, str) or not re.fullmatch('[a-z][a-z0-9_]{0,63}', ident) or source.stem != ident:
        raise ValueError('Invalid/mismatched building id')
    fp = meta['footprint']; w, d, h = fp['width'], fp['depth'], meta['height']; offset = meta['surface_offset_y']
    if any(type(x) is not int for x in (w,d,h,offset)) or not (1<=w<=128 and 1<=d<=128 and 2<=h<=192 and 0<=offset<h):
        raise ValueError('Invalid dimensions/surface offset')
    if (meta['structure'] != 'apocalypse_firstlight:'+ident or meta['front'] != 'SOUTH'
            or meta['category'] not in CATEGORIES or not meta['city_zones'] or not set(meta['city_zones']) <= ZONES
            or not meta['allowed_rotations'] or not set(meta['allowed_rotations']) <= ROTATIONS
            or type(meta['authoring_version']) is not int or meta['authoring_version'] != 1 or meta['loot_ready'] is not False
            or type(meta['road_facing']) is not bool or type(meta['damage_compatible']) is not bool):
        raise ValueError('Invalid/incomplete V1 metadata')
    check_nbt(source, [w,h,d])
    project = Path(project).resolve(strict=True)
    if not (project/'build.gradle').is_file(): raise ValueError('Not an AFL project root (build.gradle missing)')
    root = project/'src/main/resources/data/apocalypse_firstlight'
    targets = [root/'structures'/f'{ident}.nbt', root/'small_city/buildings'/f'{ident}.json']
    for target in targets:
        if not target.resolve().is_relative_to(project): raise ValueError('Destination escapes project')
        if target.is_symlink(): raise ValueError('Refusing symlink destination')
        if target.exists() and not overwrite: raise FileExistsError(f'{target} exists; explicit --overwrite required')
    # Stage both inputs and back up replacements before modifying either destination.
    with tempfile.TemporaryDirectory(prefix='afl-author-import-') as tmp:
        tmp = Path(tmp); backups = {}; written = []
        try:
            for i, (src, dst) in enumerate(zip((source, meta_path), targets)):
                shutil.copyfile(src, tmp/f'new{i}')
                if dst.exists(): shutil.copyfile(dst, tmp/f'old{i}'); backups[i] = tmp/f'old{i}'
            for i, dst in enumerate(targets):
                dst.parent.mkdir(parents=True, exist_ok=True)
                with dst.open('wb' if overwrite else 'xb') as out:
                    written.append(i)
                    with (tmp/f'new{i}').open('rb') as inp: shutil.copyfileobj(inp, out)
        except Exception:
            for i in written:
                if i in backups: shutil.copyfile(backups[i], targets[i])
                else: targets[i].unlink(missing_ok=True)
            raise
    for dst in targets: print(f'IMPORTED {dst}')
    print('SMALL_CITY_POOL_AUTO_INTEGRATION = NO')
    return targets

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('nbt', type=Path)
    parser.add_argument('--project', type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument('--overwrite', action='store_true')
    args = parser.parse_args()
    try: import_pair(args.nbt, args.project, args.overwrite)
    except (OSError, ValueError, KeyError, TypeError) as exc: parser.exit(1, f'IMPORT FAILED: {exc}\n')
