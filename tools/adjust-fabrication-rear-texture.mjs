import fs from 'node:fs';
import zlib from 'node:zlib';
import assert from 'node:assert/strict';

// Deterministic pixel relocation, not a repaint of the shared power socket.
const base='src/main/resources/assets/apocalypse_firstlight/';
const sourcePath='src/main/blockbench/precision_fabrication_station.bbmodel';
const model=JSON.parse(fs.readFileSync(sourcePath,'utf8'));
const validator=fs.readFileSync('tools/sync-precision-fabrication-station.mjs','utf8');
const decode=new Function('zlib','assert',validator.slice(validator.indexOf('function pixels('),validator.indexOf('const atlas=Buffer'))+'return pixels;')(zlib,assert);
const atlas=decode(Buffer.from(model.textures[0].source.split(',')[1],'base64'));
const back=decode(fs.readFileSync(base+'textures/block/machine_back.png'));
const before=Buffer.from(atlas.rgba);
const copy=(src,sx,sy,w,h,dx,dy)=>{for(let y=0;y<h;y++)for(let x=0;x<w;x++){
 const a=((sy+y)*src.width+sx+x)*4,b=((dy+y)*256+dx+x)*4;
 src.rgba.copy(atlas.rgba,b,a,a+4);
}};
copy(atlas,96,0,32,32,224,224);
// Recenter the entire 8x8 panel around the already aligned socket, X16/Y7.9.
for(let y=0;y<32;y++)for(let x=0;x<32;x++)if(x<2||x>=30||y<2||y>=30)
 atlas.rgba.set([98,102,106,255],((224+y)*256+224+x)*4);
copy(back,11,11,10,10,235,235);
// Rear-only vents: shorten openings from 8 to 6 pixels; keep panel and frame.
const rear=model.elements.find(e=>e.name==='rear_access_cover').faces.south.uv;
const [u,v]=rear;
for(let y=0;y<32;y++)for(let x=0;x<32;x++) {
 const i=((v+y)*256+u+x)*4;
 // Restore smooth rear steel, matching original broad highlights and edge shading.
 let rgb=[54,58,59];
 const blend=(c,a)=>{rgb=rgb.map(n=>Math.round(n*(1-a)+c*a));};
 if(x>=2&&x<30&&y>=2&&y<10)blend(255,5/255);
 if(x>=2&&x<30&&y>=23&&y<30)blend(0,4/255);
 if(y===0||x===0)blend(255,16/255);
 if(x===31||y===31)blend(0,24/255);
 atlas.rgba.set([...rgb,255],i);
}
for(const y of [7,13,19,25])for(const x of [6,20])for(let dy=0;dy<2;dy++)for(let dx=0;dx<6;dx++)
 atlas.rgba.set([23,28,29,255],((v+y+dy)*256+u+x+dx)*4);
for(let y=0;y<256;y++)for(let x=0;x<256;x++)if(!(x>=224&&y>=224)&&!(x>=u&&x<u+32&&y>=v&&y<v+32)) {
 const i=(y*256+x)*4;assert(atlas.rgba.subarray(i,i+4).equals(before.subarray(i,i+4)));
}
const crc=buf=>{let c=0xffffffff;for(const b of buf){c^=b;for(let k=0;k<8;k++)c=c&1?(c>>>1)^0xedb88320:c>>>1;}return (c^0xffffffff)>>>0;};
const chunk=(tag,data)=>{const b=Buffer.alloc(data.length+12);b.writeUInt32BE(data.length);b.write(tag,4);data.copy(b,8);b.writeUInt32BE(crc(b.subarray(4,-4)),b.length-4);return b;};
const header=Buffer.alloc(13);header.writeUInt32BE(256);header.writeUInt32BE(256,4);header[8]=8;header[9]=6;
const rows=Buffer.alloc(256*1025);for(let y=0;y<256;y++)atlas.rgba.copy(rows,y*1025+1,y*1024,(y+1)*1024);
const png=Buffer.concat([Buffer.from([137,80,78,71,13,10,26,10]),chunk('IHDR',header),chunk('IDAT',zlib.deflateSync(rows)),chunk('IEND',Buffer.alloc(0))]);
model.textures[0].source='data:image/png;base64,'+png.toString('base64');
fs.writeFileSync(sourcePath,JSON.stringify(model));
fs.writeFileSync(base+'textures/block/precision_fabrication_station.png',png);
console.log({socketCentre:[16,7.9,16],rearVentWidth:'8 -> 6 pixels',geometryChanged:false});
