import gzip
import importlib.util
import json
from pathlib import Path
import struct
import tempfile
import unittest

spec=importlib.util.spec_from_file_location('importer',Path(__file__).resolve().parents[1]/'import_authored_building.py')
importer=importlib.util.module_from_spec(spec);spec.loader.exec_module(importer)

class ImportTests(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory();self.addCleanup(self.tmp.cleanup)
        self.root=Path(self.tmp.name);self.project=self.root/'project';self.project.mkdir()
        (self.project/'build.gradle').touch();self.source=self.root/'authoring_test_box.nbt'
        def name(s): return struct.pack('>H',len(s))+s.encode()
        def lst(s,kind,n):return b'\x09'+name(s)+bytes([kind])+struct.pack('>i',n)
        nbt=b'\x0a\x00\x00'+lst('size',3,3)+struct.pack('>iii',16,12,20)+lst('entities',10,0)+lst('blocks',10,0)+lst('palette',10,0)+b'\x00'
        with gzip.open(self.source,'wb') as f:f.write(nbt)
        self.meta=dict(id='authoring_test_box',structure='apocalypse_firstlight:authoring_test_box',category='UTILITY',
            footprint=dict(width=16,depth=20),height=12,front='SOUTH',surface_offset_y=1,
            allowed_rotations=['NONE'],city_zones=['EDGE'],road_facing=True,damage_compatible=True,loot_ready=False,authoring_version=1)
        self.save()
    def save(self):self.source.with_suffix('.json').write_text(json.dumps(self.meta),encoding='utf-8')
    def test_import_and_overwrite(self):
        paths=importer.import_pair(self.source,self.project)
        self.assertEqual(paths[0].read_bytes(),self.source.read_bytes())
        with self.assertRaises(FileExistsError):importer.import_pair(self.source,self.project)
        importer.import_pair(self.source,self.project,True)
        self.assertFalse((self.project/'src/main/resources/data/apocalypse_firstlight/small_city/pools').exists())
    def test_missing_pair(self):
        self.source.with_suffix('.json').unlink()
        with self.assertRaises(OSError):importer.import_pair(self.source,self.project)
    def test_bad_id(self):
        self.meta['id']='../escape';self.save()
        with self.assertRaises(ValueError):importer.import_pair(self.source,self.project)
    def test_dimension_mismatch(self):
        self.meta['height']=13;self.save()
        with self.assertRaises(ValueError):importer.import_pair(self.source,self.project)
    def test_invalid_metadata(self):
        for key,value in [('category','OTHER'),('loot_ready',True),('front','NORTH'),('authoring_version',True)]:
            old=self.meta[key];self.meta[key]=value;self.save()
            with self.assertRaises(ValueError):importer.import_pair(self.source,self.project)
            self.meta[key]=old
    def test_corrupt_nbt(self):
        self.source.write_bytes(b'not nbt')
        with self.assertRaises(OSError):importer.import_pair(self.source,self.project)

if __name__=='__main__':unittest.main()
