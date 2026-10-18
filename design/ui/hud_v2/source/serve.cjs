// HUD V2 concept server (design use only, not a game asset).
// node design/ui/hud_v2/source/serve.cjs, open http://127.0.0.1:8774/hud_v2_concept.html, then in the page console:
//   await sheets()         renders and saves every concept sheet
// POST /save?name=x.png writes into ../sheets, so the sheets there are overwritten.
// /assets/... serves the mod's assets (the AFL Sans font), /design/... the design folder (the dashboard concept code this page reuses).
const http = require('http'), fs = require('fs'), path = require('path');
const root = __dirname, out = path.join(__dirname, '..', 'sheets');
const assets = path.resolve(__dirname, '../../../../src/main/resources/assets/apocalypse_firstlight');
const design = path.resolve(__dirname, '../../..');
const types = {'.html': 'text/html; charset=utf-8', '.png': 'image/png', '.js': 'text/javascript', '.json': 'application/json', '.ttf': 'font/ttf'};
fs.mkdirSync(out, {recursive: true});
http.createServer((req, res) => {
  const url = new URL(req.url, 'http://x');
  if (req.method === 'POST' && url.pathname === '/save') {
    const name = path.basename(url.searchParams.get('name') || 'out.png'), chunks = [];
    req.on('data', c => chunks.push(c));
    req.on('end', () => { fs.writeFileSync(path.join(out, name), Buffer.concat(chunks)); res.writeHead(200); res.end('ok'); });
    return;
  }
  const rel = decodeURIComponent(url.pathname);
  const base = rel.startsWith('/assets/') ? assets : rel.startsWith('/design/') ? design : root;
  const p = path.join(base, base === assets ? rel.slice('/assets/'.length) : base === design ? rel.slice('/design/'.length) : rel);
  if (!p.startsWith(path.normalize(base)) || !fs.existsSync(p) || fs.statSync(p).isDirectory()) { res.writeHead(404); return res.end(); }
  res.writeHead(200, {'Content-Type': types[path.extname(p).toLowerCase()] || 'application/octet-stream', 'Cache-Control': 'no-store'});
  fs.createReadStream(p).pipe(res);
}).listen(8774, '127.0.0.1');
console.log('http://127.0.0.1:8774/hud_v2_concept.html');
