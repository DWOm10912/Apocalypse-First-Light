import gzip
import json
from pathlib import Path
import struct
import tempfile
import unittest
import zipfile
import zlib
from tools.afl_reference_map_importer.anvil_reader import nbt,chunks,section_states,blocks,level_metadata
from tools.afl_reference_map_importer.safe_source import open_source
from tools.afl_reference_map_importer.block_compat import audit
from tools.afl_reference_map_importer.importer import extract

def string(s):
    b=s.encode();return struct.pack('>H',len(b))+b
def payload(t,v):
    if t==1:return struct.pack('>b',v)
    if t==3:return struct.pack('>i',v)
    if t==8:return string(v)
    if t==10:return b''.join(bytes([kind])+string(k)+payload(kind,value) for k,(kind,value) in v.items())+b'\0'
    if t==9:
        kind,values=v;return bytes([kind])+struct.pack('>i',len(values))+b''.join(payload(kind,item) for item in values)
    raise AssertionError(t)
def root(v):return b'\x0a\0\0'+payload(10,v)
def fixture(folder):
    (folder/'region').mkdir(parents=True)
    (folder/'level.dat').write_bytes(gzip.compress(root({'Data':(10,{'LevelName':(8,'Test'),'DataVersion':(3,4189),'SpawnX':(3,0),'SpawnY':(3,0),'SpawnZ':(3,0),'Version':(10,{'Name':(8,'1.21.4')})})})))
    section={'Y':(1,-1),'block_states':(10,{'palette':(9,(10,[{'Name':(8,'minecraft:chest'),'Properties':(10,{'facing':(8,'north')})}]))})}
    chunk=root({'xPos':(3,0),'zPos':(3,0),'DataVersion':(3,4189),'sections':(9,(10,[section])),'block_entities':(9,(10,[{'id':(8,'minecraft:chest'),'x':(3,0),'y':(3,-16),'z':(3,0),'Items':(9,(10,[{'id':(8,'minecraft:diamond')}]))}])),'entities':(9,(10,[{'id':(8,'minecraft:pig')}]))})
    packed=zlib.compress(chunk);record=struct.pack('>I',len(packed)+1)+b'\x02'+packed;sectors=(len(record)+4095)//4096
    header=struct.pack('>I',(2<<8)|sectors)+b'\0'*(8192-4)
    (folder/'region/r.0.0.mca').write_bytes(header+record+b'\0'*(4096*sectors-len(record)))

class ImporterTests(unittest.TestCase):
    def test_world_chunk_extract_sanitizes(self):
        with tempfile.TemporaryDirectory() as d:
            folder=Path(d)/'world';fixture(folder);m=level_metadata(folder);self.assertEqual(m['source_version'],'1.21.4')
            cx,cz,c=next(chunks(folder));values=list(blocks(cx,cz,c,[0,-16,0,15,-1,15]));self.assertEqual(len(values),4096);self.assertEqual(values[0][1],-16)
            r=extract(folder,m,[0,-16,0,15,-1,15],'overworld','FULL_SELECTION');self.assertEqual(r['entities'],[]);self.assertEqual(r['block_entities'],[]);self.assertEqual(r['metadata']['source_block_entities_stripped'],1);self.assertEqual(r['palette'][0]['Properties'],{'facing':'north'});self.assertEqual(r['blocks'][0][:3],[0,0,0])
    def test_zip(self):
        with tempfile.TemporaryDirectory() as d:
            base=Path(d);fixture(base/'world')
            with zipfile.ZipFile(base/'source.zip','w') as z:
                for f in (base/'world').rglob('*'):
                    if f.is_file():z.write(f,'wrapped/'+str(f.relative_to(base/'world')).replace('\\','/'))
                z.writestr('wrapped/evil.exe','not executed')
            world,meta=open_source(base/'source.zip',base/'out');self.assertTrue((world/'level.dat').exists());self.assertFalse((world/'evil.exe').exists());self.assertTrue(meta['sha256'])
    def test_zip_slip(self):
        for member in ('../level.dat','/level.dat','C:/level.dat','a\\level.dat'):
            with self.subTest(member=member),tempfile.TemporaryDirectory() as d:
                base=Path(d)
                encoded=member.replace('\\','/')
                with zipfile.ZipFile(base/'bad.zip','w') as z:z.writestr(encoded,'x')
                if '\\' in member:(base/'bad.zip').write_bytes((base/'bad.zip').read_bytes().replace(encoded.encode(),member.encode()))
                with self.assertRaises(ValueError):open_source(base/'bad.zip',base/'out')
    def test_packed_padding_high_y(self):
        palette=[{'Name':f'minecraft:test{i}'} for i in range(17)];bits=5;per=12;data=[0]*((4096+per-1)//per)
        for i in range(4096):data[i//per]|=(i%17)<<((i%per)*bits)
        p,indices=section_states({'Y':19,'block_states':{'palette':palette,'data':data}})
        self.assertEqual(indices,[i%17 for i in range(4096)])
        self.assertEqual(next(blocks(0,0,{'sections':[{'Y':19,'block_states':{'palette':[{'Name':'minecraft:stone'}]}}]},[0,304,0,15,319,15]))[1],304)
    def test_invalid_packing_no_skip(self):
        with self.assertRaises(ValueError):section_states({'Y':0,'block_states':{'palette':[{'Name':'a'},{'Name':'b'}],'data':[0]}})
    def test_registry_unknown_mapping(self):
        r={'minecraft_version':'1.20.1','data_version':3465,'blocks':{'minecraft:grass':{'properties':{},'has_block_entity':False}}};s={'palette':[{'Name':'minecraft:short_grass','Properties':{}}],'blocks':[[0,0,0,0]]}
        self.assertFalse(audit(s,r)['ready']);self.assertEqual(audit(s,r)['entries'][0]['target']['Name'],'minecraft:short_grass')
        approved={'minecraft:short_grass':{'target':'minecraft:grass','approved':True,'reason':'reviewed replacement'}};self.assertTrue(audit(s,r,approved)['ready'])
        approved['minecraft:short_grass']['approved']=False
        with self.assertRaises(ValueError):audit(s,r,approved)
    def test_property_requires_review(self):
        r={'minecraft_version':'1.20.1','data_version':3465,'blocks':{'minecraft:stone':{'properties':{}}}};s={'palette':[{'Name':'minecraft:stone','Properties':{'new':'true'}}],'blocks':[[0,0,0,0]]}
        self.assertFalse(audit(s,r)['ready']);self.assertTrue(audit(s,r,{'minecraft:stone':{'target':'minecraft:stone','drop_properties':['new'],'reason':'reviewed','approved':True}})['ready'])
    def test_nbt_bounds(self):
        with self.assertRaises(ValueError):nbt(b'\x0a\0\0\x07\0\0\x7f\xff\xff\xff')

if __name__=='__main__':unittest.main()
