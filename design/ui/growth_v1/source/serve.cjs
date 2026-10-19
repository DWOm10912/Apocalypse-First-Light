// Growth page concept server (design use only, not a game asset).
// node design/ui/growth_v1/source/serve.cjs, then open http://127.0.0.1:8782/growth_concept.html?s=A (A B).
// /assets/... serves the mod's assets (the AFL Sans font), /design/... the design folder (backgrounds, the inventory shot).
const http = require('http'), fs = require('fs'), path = require('path');
const root = __dirname;
const assets = path.resolve(__dirname, '../../../../src/main/resources/assets/apocalypse_firstlight');
const design = path.resolve(__dirname, '../../..');
const types = {'.html': 'text/html; charset=utf-8', '.png': 'image/png', '.js': 'text/javascript', '.ttf': 'font/ttf'};
http.createServer((req, res) => {
  const rel = decodeURIComponent(new URL(req.url, 'http://x').pathname);
  const base = rel.startsWith('/assets/') ? assets : rel.startsWith('/design/') ? design : root;
  const p = path.join(base, base === assets ? rel.slice('/assets/'.length) : base === design ? rel.slice('/design/'.length) : rel);
  if (!p.startsWith(path.normalize(base)) || !fs.existsSync(p) || fs.statSync(p).isDirectory()) { res.writeHead(404); return res.end(); }
  res.writeHead(200, {'Content-Type': types[path.extname(p).toLowerCase()] || 'application/octet-stream', 'Cache-Control': 'no-store'});
  fs.createReadStream(p).pipe(res);
}).listen(8782, '127.0.0.1');
console.log('http://127.0.0.1:8782/growth_concept.html?s=A');
