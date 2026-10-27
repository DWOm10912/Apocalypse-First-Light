// Quick look at Firefly takes before cutting: length, max 100 / 400 ms loudness, peak, stereo correlation, SHA-256
// prefix, and a 50 ms loudness envelope (one character per block) to see how many events a take holds.
//   node tools/analyze-sound-takes.mjs <file or dir> [more ...]
import fs from 'node:fs';
import path from 'node:path';
import {createHash} from 'node:crypto';
import {execFileSync} from 'node:child_process';
import {SR, decode, maxLoudness, peak, kweight} from './sound-mix-lib.mjs';

function correlation(file) {
  const ch = +execFileSync('ffprobe', ['-v', 'error', '-select_streams', 'a:0', '-show_entries', 'stream=channels', '-of', 'csv=p=0', file]).toString().trim();
  if (ch !== 2) return null;
  const buf = execFileSync('ffmpeg', ['-v', 'error', '-i', file, '-ar', String(SR), '-f', 'f32le', '-'], {maxBuffer: 1 << 28});
  const s = new Float32Array(buf.buffer, buf.byteOffset, buf.length / 4);
  let ll = 0, rr = 0, lr = 0;
  for (let i = 0; i + 1 < s.length; i += 2) { ll += s[i] * s[i]; rr += s[i + 1] * s[i + 1]; lr += s[i] * s[i + 1]; }
  return +(lr / Math.sqrt(ll * rr + 1e-20)).toFixed(3);
}

function envelope(x) {   // K-weighted loudness per 50 ms block: ' ' < -50, '.' < -40, ':' < -30, '+' < -20, '#' above
  const k = kweight(x), B = Math.round(0.05 * SR);
  let s = '';
  for (let b = 0; b < k.length; b += B) {
    let e = 0; const n = Math.min(B, k.length - b);
    for (let i = b; i < b + n; i++) e += k[i] * k[i];
    const l = -0.691 + 10 * Math.log10(e / n + 1e-20);
    s += l < -50 ? ' ' : l < -40 ? '.' : l < -30 ? ':' : l < -20 ? '+' : '#';
  }
  return s;
}

const files = process.argv.slice(2).flatMap(a => fs.statSync(a).isDirectory()
  ? fs.readdirSync(a).filter(f => /\.(wav|ogg)$/i.test(f)).map(f => path.join(a, f)) : [a]);
for (const f of files) {
  const x = decode(f);
  const sha = createHash('sha256').update(fs.readFileSync(f)).digest('hex').slice(0, 16);
  console.log(`${path.basename(f)}  sha ${sha}  ${(x.length / SR).toFixed(2)} s  max100 ${maxLoudness(x, 0.1).toFixed(1)}  max400 ${maxLoudness(x, 0.4).toFixed(1)} LUFS  peak ${(20 * Math.log10(peak(x))).toFixed(1)} dBFS  corr ${correlation(f)}`);
  console.log(`  |${envelope(x)}|`);
}
