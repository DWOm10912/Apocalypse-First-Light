// Geiger counter V2 concept server (design use only, not a game asset).
// node design/items/geiger_counter_v2/source/serve.cjs, open http://127.0.0.1:8768/geiger_concept.html, then in the page console:
//   await sheets()         renders and saves every concept sheet
// POST /save?name=x.png writes into ../sheets, so the sheets there are overwritten.
const http = require('http'), fs = require('fs'), path = require('path');
const root = __dirname, out = path.join(__dirname, '..', 'sheets');
const assets = path.resolve(__dirname, '../../../../src/main/resources/assets/apocalypse_firstlight');
const types = {'.html': 'text/html; charset=utf-8', '.png': 'image/png', '.js': 'text/javascript', '.json': 'application/json'};
fs.mkdirSync(out, {recursive: true});
http.createServer((req, res) => {
  const url = new URL(req.url, 'http://x');
  if (req.method === 'POST' && url.pathname === '/save') {
    const name = path.basename(url.searchParams.get('name') || 'out.png'), chunks = [];
    req.on('data', c => chunks.push(c));
    req.on('end', () => { fs.writeFileSync(path.join(out, name), Buffer.concat(chunks)); res.writeHead(200); res.end('ok'); });
    return;
  }
  const rel = decodeURIComponent(url.pathname), base = rel.startsWith('/assets/') ? assets : root;
  const p = path.join(base, base === assets ? rel.slice('/assets/'.length) : rel);
  if (!p.startsWith(path.normalize(base)) || !fs.existsSync(p) || fs.statSync(p).isDirectory()) { res.writeHead(404); return res.end(); }
  res.writeHead(200, {'Content-Type': types[path.extname(p).toLowerCase()] || 'application/octet-stream', 'Cache-Control': 'no-store'});
  fs.createReadStream(p).pipe(res);
}).listen(8768, '127.0.0.1');
console.log('http://127.0.0.1:8768/geiger_concept.html');
