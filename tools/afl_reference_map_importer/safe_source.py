import hashlib
from pathlib import Path, PurePosixPath
import shutil
import stat
import uuid
import zipfile

def open_source(source,output):
    source=Path(source).resolve(strict=True)
    if source.is_dir():
        if source.is_symlink() or any(p.is_symlink() for p in source.rglob('*')): raise ValueError('WORLD_FOLDER_SYMLINK_REJECTED')
        world=source;fingerprint=None
    else:
        if source.suffix.lower()!='.zip' or source.stat().st_size>512*1024*1024: raise ValueError('ZIP_INVALID: type/size')
        digest=hashlib.sha256()
        with source.open('rb') as f:
            for chunk in iter(lambda:f.read(1024*1024),b''): digest.update(chunk)
        fingerprint=digest.hexdigest()
        world=None;dest=Path(output)/'sources'/str(uuid.uuid4())
        with zipfile.ZipFile(source) as archive:
            total=0;names=set()
            for info in archive.infolist():
                name=info.orig_filename;part=PurePosixPath(name)
                if '\\' in name or ':' in name or part.is_absolute() or '..' in part.parts or any(s.endswith((' ','.')) for s in part.parts) or stat.S_ISLNK(info.external_attr>>16) or info.flag_bits&1: raise ValueError(f'ZIP_INVALID: unsafe member {name}')
                # Case-insensitive duplicate protection for Windows targets.
                if name.lower() in names: raise ValueError('ZIP_INVALID: duplicate path')
                names.add(name.lower());total+=info.file_size
                if len(names)>20000 or total>1024*1024*1024 or info.file_size>128*1024*1024 or (info.file_size>1024*1024 and info.file_size/max(info.compress_size,1)>1000): raise ValueError('ZIP_INVALID: extraction limits')
            # Extract only data needed for offline inspection. Never scripts, playerdata or datapacks.
            for info in archive.infolist():
                part=PurePosixPath(info.filename)
                if info.is_dir() or not (part.name=='level.dat' or (part.suffix=='.mca' and part.parent.name=='region')): continue
                target=dest.joinpath(*part.parts);target.parent.mkdir(parents=True,exist_ok=True)
                with archive.open(info) as src,target.open('xb') as dst: shutil.copyfileobj(src,dst,1024*1024)
        candidates=list(dest.rglob('level.dat'))
        if len(candidates)!=1: raise ValueError('LEVEL_DAT_MISSING_OR_MULTIPLE_WORLDS')
        world=candidates[0].parent
    if not (world/'level.dat').is_file(): raise ValueError('LEVEL_DAT_MISSING')
    return world,{'source_path':str(source),'source_archive_filename':source.name,'sha256':fingerprint}
