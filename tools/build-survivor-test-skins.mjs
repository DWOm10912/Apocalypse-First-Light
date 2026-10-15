// Survivor Death and Succession V0: the two test skins of the runtime look switch
// (docs/gameplay/survivor_death_succession_v0.md 13). Plain 64x64 player skins, base layer only (the overlay layer
// stays transparent): test_classic (4 px arms) and test_slim (3 px arms). Placeholders only; the formal AFL survivor
// skin library replaces them.
//
//   node tools/build-survivor-test-skins.mjs                 writes the PNGs
//   node tools/build-survivor-test-skins.mjs --check         compares only (exit 1 when an output differs)
//   node tools/build-survivor-test-skins.mjs --preview <dir> also writes 8x enlarged copies there
import fs from 'node:fs';
import path from 'node:path';
import zlib from 'node:zlib';
import {fileURLToPath} from 'node:url';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const OUT = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight/textures/entity/survivor');
const SIZE = 64;

const crcTable = Array.from({length: 256}, (_, n) => {
  let c = n;
  for (let k = 0; k < 8; k++) c = c & 1 ? 0xEDB88320 ^ (c >>> 1) : c >>> 1;
  return c >>> 0;
});
const crc32 = buf => {
  let c = 0xFFFFFFFF;
  for (const b of buf) c = crcTable[(c ^ b) & 0xFF] ^ (c >>> 8);
  return (c ^ 0xFFFFFFFF) >>> 0;
};
function png(width, height, rgba) {
  const raw = Buffer.alloc((width * 4 + 1) * height);
  for (let y = 0; y < height; y++) {
    raw[y * (width * 4 + 1)] = 0;
    Buffer.from(rgba.buffer, y * width * 4, width * 4).copy(raw, y * (width * 4 + 1) + 1);
  }
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(width, 0);
  ihdr.writeUInt32BE(height, 4);
  ihdr[8] = 8;
  ihdr[9] = 6;
  const chunk = (type, data) => {
    const len = Buffer.alloc(4);
    len.writeUInt32BE(data.length);
    const td = Buffer.concat([Buffer.from(type), data]), crc = Buffer.alloc(4);
    crc.writeUInt32BE(crc32(td));
    return Buffer.concat([len, td, crc]);
  };
  return Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk('IHDR', ihdr),
    chunk('IDAT', zlib.deflateSync(raw, {level: 9})), chunk('IEND', Buffer.alloc(0))]);
}

const rgb = hex => [parseInt(hex.slice(1, 3), 16), parseInt(hex.slice(3, 5), 16), parseInt(hex.slice(5, 7), 16)];

// A box of the player model: the six faces in the standard skin net, painted by paint(face, i, j, faceW, faceH).
function box(px, u, v, w, h, d, paint) {
  const faces = [['top', u + d, v, w, d], ['bottom', u + d + w, v, w, d], ['right', u, v + d, d, h],
    ['front', u + d, v + d, w, h], ['left', u + d + w, v + d, d, h], ['back', u + d + w + d, v + d, w, h]];
  for (const [face, x0, y0, fw, fh] of faces) {
    for (let j = 0; j < fh; j++) {
      for (let i = 0; i < fw; i++) {
        const [r, g, b] = rgb(paint(face, i, j, fw, fh));
        const o = ((y0 + j) * SIZE + x0 + i) * 4;
        px[o] = r; px[o + 1] = g; px[o + 2] = b; px[o + 3] = 255;
      }
    }
  }
}

function skin(c) {
  const px = new Uint8Array(SIZE * SIZE * 4);
  const plaid = (i, j) => (c.plaid && (j % 4 === 2 || i % 4 === 1)) ? c.topLine : c.top;

  // head 8x8x8 at (0,0)
  box(px, 0, 0, 8, 8, 8, (face, i, j) => {
    if (face === 'top') return c.hair;
    if (face === 'bottom') return c.skin;
    if (face === 'back') return j < c.hairBack ? (c.tie && j === 5 && (i === 3 || i === 4) ? c.tie : c.hair) : c.skin;
    if (face === 'right' || face === 'left') {
      const toBack = face === 'right' ? 7 - i : i;  // 0 at the front edge, 7 at the back edge
      if (j < 2 || (toBack >= 5 && j < c.hairBack)) return c.hair;
      if (toBack === 3 && (j === 4 || j === 5)) return c.skinShade;  // ear
      return c.skin;
    }
    // front: fringe, sideburns, brows, eyes, nose, mouth
    if (j < 2) return c.hair;
    if ((i === 0 || i === 7) && j < c.sideHair) return c.hair;
    if (j === 3 && (i === 1 || i === 2 || i === 5 || i === 6)) return c.hairShade;
    if (j === 4 && (i === 1 || i === 6)) return c.eyeWhite;
    if (j === 4 && (i === 2 || i === 5)) return c.iris;
    if (j === 5 && (i === 3 || i === 4)) return c.skinShade;
    if (j === 6 && (i === 3 || i === 4)) return c.lip;
    return c.skin;
  });

  // body 8x12x4 at (16,16)
  box(px, 16, 16, 8, 12, 4, (face, i, j) => {
    if (face === 'top') return c.top;
    if (face === 'bottom') return c.pants;
    if (j === 11) return face === 'front' && (i === 3 || i === 4) ? c.buckle : c.belt;
    if (face === 'front' && c.openJacket && (i === 3 || i === 4)) return c.undershirt;
    if (face === 'front' && j === 0 && (i === 3 || i === 4)) return c.skin;
    return plaid(i, j);
  });

  // arms: right at (40,16), left at (32,48); 4 or 3 px wide
  const w = c.slim ? 3 : 4;
  const arm = (face, i, j) => {
    if (face === 'top') return c.top;
    if (face === 'bottom') return c.skin;
    if (j < c.sleeve) return plaid(i, j);
    if (j === c.sleeve) return c.cuff;
    return c.skin;
  };
  box(px, 40, 16, w, 12, 4, arm);
  box(px, 32, 48, w, 12, 4, arm);

  // legs: right at (0,16), left at (16,48)
  const leg = (face, i, j) => {
    if (face === 'top') return c.pants;
    if (face === 'bottom') return c.sole;
    if (j < 8) return c.pants;
    if (j === 8) return c.pantsShade;
    return c.boots;
  };
  box(px, 0, 16, 4, 12, 4, leg);
  box(px, 16, 48, 4, 12, 4, leg);
  return px;
}

const SKINS = {
  // olive work jacket open over a grey shirt, blue jeans, brown boots, short brown hair
  test_classic: {
    slim: false, skin: '#C68E63', skinShade: '#B07B54', lip: '#9A5E45',
    hair: '#3B2A1E', hairShade: '#2A1D14', hairBack: 6, sideHair: 4, tie: null,
    eyeWhite: '#EDEAE4', iris: '#3E5A73',
    top: '#5B6141', topLine: '#5B6141', plaid: false, openJacket: true, undershirt: '#8D8C86',
    sleeve: 9, cuff: '#4A5034', belt: '#2B2622', buckle: '#8C8A80',
    pants: '#3A4A63', pantsShade: '#2F3D52', boots: '#4A3526', sole: '#231A13',
  },
  // red flannel with rolled sleeves, charcoal trousers, dark boots, black hair tied back
  test_slim: {
    slim: true, skin: '#7A5038', skinShade: '#68432E', lip: '#5A3424',
    hair: '#1E1A18', hairShade: '#121010', hairBack: 8, sideHair: 6, tie: '#6E2A26',
    eyeWhite: '#E6E1DA', iris: '#3A2A20',
    top: '#8C2F2A', topLine: '#5E1E1B', plaid: true, openJacket: false, undershirt: '#8C2F2A',
    sleeve: 7, cuff: '#5E1E1B', belt: '#1F1B19', buckle: '#1F1B19',
    pants: '#3A3B3E', pantsShade: '#303134', boots: '#2C2724', sole: '#161311',
  },
};

const check = process.argv.includes('--check');
const previewAt = process.argv.indexOf('--preview');
const previewDir = previewAt >= 0 ? process.argv[previewAt + 1] : null;
let differs = 0;
if (!check) fs.mkdirSync(OUT, {recursive: true});
for (const [id, spec] of Object.entries(SKINS)) {
  const px = skin(spec);
  const bytes = png(SIZE, SIZE, px);
  const file = path.join(OUT, id + '.png');
  if (check) {
    const same = fs.existsSync(file) && fs.readFileSync(file).equals(bytes);
    if (!same) differs++;
    console.log((same ? 'same    ' : 'DIFFERS ') + path.relative(ROOT, file));
  } else {
    fs.writeFileSync(file, bytes);
    console.log('wrote ' + path.relative(ROOT, file));
  }
  if (previewDir) {
    const k = 8, big = new Uint8Array(SIZE * k * SIZE * k * 4);
    for (let y = 0; y < SIZE * k; y++) {
      for (let x = 0; x < SIZE * k; x++) {
        const s = ((Math.floor(y / k) * SIZE) + Math.floor(x / k)) * 4, o = (y * SIZE * k + x) * 4;
        const a = px[s + 3];
        const checker = ((Math.floor(x / 16) + Math.floor(y / 16)) % 2) ? 200 : 230;
        for (let ch = 0; ch < 3; ch++) big[o + ch] = a ? px[s + ch] : checker;
        big[o + 3] = 255;
      }
    }
    fs.mkdirSync(previewDir, {recursive: true});
    fs.writeFileSync(path.join(previewDir, id + '_preview.png'), png(SIZE * k, SIZE * k, big));
  }
}
if (check && differs) process.exit(1);
