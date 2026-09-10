"""Bounded Java NBT and modern (1.18+) Anvil reader. No world loading/downgrade."""
import gzip
import io
import math
from pathlib import Path
import re
import struct
import zlib

MAX_NBT = 16 * 1024 * 1024

def inflate(data, compression):
    if compression == 3:
        if len(data) > MAX_NBT: raise ValueError('CHUNK_DECODE_FAILED: uncompressed size limit')
        return data
    if compression not in (1, 2):
        raise ValueError(f'CHUNK_DECODE_FAILED: unsupported compression type {compression}')
    d = zlib.decompressobj(31 if compression == 1 else 15)
    out = d.decompress(data, MAX_NBT + 1)
    if len(out) > MAX_NBT or d.unconsumed_tail or not d.eof:
        raise ValueError('CHUNK_DECODE_FAILED: size limit/truncated compression')
    return out

def nbt(data):
    """Decode with byte/count/depth budgets, including malformed untrusted arrays."""
    if len(data) > MAX_NBT: raise ValueError('NBT_SIZE_LIMIT')
    stream = io.BytesIO(data)
    def take(n):
        if n < 0 or n > len(data) - stream.tell(): raise ValueError('NBT_TRUNCATED')
        return stream.read(n)
    def number(fmt): return struct.unpack('>' + fmt, take(struct.calcsize('>' + fmt)))[0]
    def string(): return take(number('H')).decode('utf-8', errors='strict')
    def count():
        n = number('i')
        if n < 0 or n > 2_000_000: raise ValueError('NBT_ARRAY_LIMIT')
        return n
    def value(t, depth=0):
        if depth > 64: raise ValueError('NBT_DEPTH_LIMIT')
        if t in (1,2,3,4,5,6): return number({1:'b',2:'h',3:'i',4:'q',5:'f',6:'d'}[t])
        if t == 7: return take(count())
        if t == 8: return string()
        if t == 9:
            item = number('B'); n = count()
            if item == 0 and n: raise ValueError('NBT_INVALID_LIST')
            return [value(item, depth+1) for _ in range(n)]
        if t == 10:
            result = {}
            while True:
                kind = number('B')
                if kind == 0: return result
                name = string()
                if name in result: raise ValueError('NBT_DUPLICATE_KEY')
                result[name] = value(kind, depth+1)
        if t in (11,12):
            n=count();size=4 if t==11 else 8
            return list(struct.unpack('>' + ('i' if t==11 else 'q')*n, take(size*n)))
        raise ValueError(f'NBT_UNSUPPORTED_TAG: {t}')
    if number('B') != 10: raise ValueError('NBT_ROOT_NOT_COMPOUND')
    string();result=value(10)
    if stream.tell()!=len(data): raise ValueError('NBT_TRAILING_DATA')
    return result

def level_metadata(world):
    path=Path(world)/'level.dat'
    if not path.is_file(): raise ValueError('LEVEL_DAT_MISSING')
    if path.stat().st_size>MAX_NBT: raise ValueError('LEVEL_DAT_SIZE_LIMIT')
    d=nbt(inflate(path.read_bytes(),1))['Data']
    dims=['overworld']
    if (Path(world)/'DIM-1/region').is_dir(): dims.append('the_nether')
    if (Path(world)/'DIM1/region').is_dir(): dims.append('the_end')
    return {'world_name':d.get('LevelName'), 'data_version':d.get('DataVersion'),
            'source_version':d.get('Version',{}).get('Name'),
            'spawn':[d.get('SpawnX',0),d.get('SpawnY',0),d.get('SpawnZ',0)],
            'last_played':d.get('LastPlayed'),'dimensions':dims,
            'world_gen_settings':d.get('WorldGenSettings',{})}

def chunks(world, dimension='overworld', bounds=None):
    sub={'overworld':'region','the_nether':'DIM-1/region','the_end':'DIM1/region'}.get(dimension)
    if sub is None: raise ValueError('UNSUPPORTED_DIMENSION')
    files=sorted((Path(world)/sub).glob('r.*.*.mca'))
    if not files: raise ValueError('REGION_MISSING')
    for file in files:
        match=re.fullmatch(r'r\.(-?\d+)\.(-?\d+)\.mca',file.name)
        if not match: raise ValueError(f'REGION_INVALID_NAME: {file}')
        rx,rz=map(int,match.groups())
        if bounds and (rx*512>bounds[3] or rx*512+511<bounds[0] or rz*512>bounds[5] or rz*512+511<bounds[2]): continue
        with file.open('rb') as f:
            header=f.read(8192)
            if len(header)!=8192: raise ValueError(f'CHUNK_DECODE_FAILED: short header {file}')
            used=set();size=file.stat().st_size
            for index in range(1024):
                location=int.from_bytes(header[index*4:index*4+4],'big');sector=location>>8;sectors=location&255
                if location==0: continue
                cx=rx*32+index%32;cz=rz*32+index//32
                if sector<2 or not sectors or (sector+sectors)*4096>size or any(s in used for s in range(sector,sector+sectors)): raise ValueError(f'CHUNK_DECODE_FAILED: invalid sectors {file} chunk {cx},{cz}')
                used.update(range(sector,sector+sectors))
                if bounds and (cx*16>bounds[3] or cx*16+15<bounds[0] or cz*16>bounds[5] or cz*16+15<bounds[2]): continue
                f.seek(sector*4096);length=int.from_bytes(f.read(4),'big');compression=f.read(1)[0]
                if length<1 or length>sectors*4096-4: raise ValueError(f'CHUNK_DECODE_FAILED: invalid length {file}')
                if compression&128: raise ValueError(f'CHUNK_DECODE_FAILED: external .mcc chunks unsupported: {file} {cx},{cz}')
                try: root=nbt(inflate(f.read(length-1),compression))
                except Exception as e: raise ValueError(f'CHUNK_DECODE_FAILED: {file} {cx},{cz}: {e}') from e
                if 'sections' not in root or root.get('xPos')!=cx or root.get('zPos')!=cz: raise ValueError(f'UNSUPPORTED_CHUNK_SCHEMA: {file} {cx},{cz} keys={list(root)}')
                yield cx,cz,root

def section_states(section):
    if 'Y' not in section: raise ValueError('UNSUPPORTED_CHUNK_SCHEMA: missing section Y')
    if 'block_states' not in section:
        # Light-only sections outside terrain exist in modern Vanilla saves.
        if set(section)<= {'Y','SkyLight','BlockLight'}: return [{'Name':'minecraft:air'}],[0]*4096
        raise ValueError(f'UNSUPPORTED_CHUNK_SCHEMA: section {section["Y"]}: {list(section)}')
    states=section['block_states'];palette=states.get('palette')
    if not isinstance(palette,list) or not palette or len(palette)>4096: raise ValueError('UNSUPPORTED_CHUNK_SCHEMA: invalid palette')
    for state in palette:
        if not isinstance(state,dict) or not isinstance(state.get('Name'),str): raise ValueError('UNSUPPORTED_CHUNK_SCHEMA: palette entry')
    if len(palette)==1: return palette,[0]*4096
    bits=max(4,(len(palette)-1).bit_length());per_long=64//bits;packed=states.get('data',[])
    if len(packed)!=math.ceil(4096/per_long): raise ValueError('UNSUPPORTED_CHUNK_SCHEMA: packed blockstate long count')
    mask=(1<<bits)-1;indices=[((packed[i//per_long]&((1<<64)-1))>>((i%per_long)*bits))&mask for i in range(4096)]
    if max(indices)>=len(palette): raise ValueError('CHUNK_DECODE_FAILED: palette index out of range')
    return palette,indices

def blocks(cx,cz,root,bounds):
    seen=set()
    for section in root['sections']:
        sy=section.get('Y')
        if not isinstance(sy,int) or sy in seen: raise ValueError('UNSUPPORTED_CHUNK_SCHEMA: duplicate/invalid section Y')
        seen.add(sy)
        if sy*16>bounds[4] or sy*16+15<bounds[1]: continue
        palette,indices=section_states(section)
        for i,index in enumerate(indices):
            x=cx*16+i%16;y=sy*16+i//256;z=cz*16+(i//16)%16
            if bounds[0]<=x<=bounds[3] and bounds[1]<=y<=bounds[4] and bounds[2]<=z<=bounds[5]: yield x,y,z,palette[index]
