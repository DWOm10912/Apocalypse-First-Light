// Cabin Exterior V1 preview server (design use only, not a game asset): serves the repository read-only and the preview
// page, saves PNGs posted to /save?name=x.png into ../m1_sheets.
//   node design/buildings/forest_start_home_v1/m1_source/serve.cjs   then open http://127.0.0.1:8766/cabin_preview.html
const http = require('http'), fs = require('fs'), path = require('path');
const here = __dirname, repo = path.resolve(here, '../../../..'), out = path.join(here, '..', 'm1_sheets');
const types = {'.html': 'text/html; charset=utf-8', '.png': 'image/png', '.js': 'text/javascript', '.json': 'application/json', '.mjs': 'text/javascript'};
fs.mkdirSync(out, {recursive: true});
http.createServer((req, res) => {
  const url = new URL(req.url, 'http://x');
  if (req.method === 'POST' && url.pathname === '/save') {
    const name = path.basename(url.searchParams.get('name') || 'out.png'), chunks = [];
    req.on('data', c => chunks.push(c));
    req.on('end', () => { fs.writeFileSync(path.join(out, name), Buffer.concat(chunks)); res.writeHead(200); res.end('ok'); });
    return;
  }
  let p = decodeURIComponent(url.pathname);
  p = p.startsWith('/repo/') ? path.join(repo, p.slice(6)) : path.join(here, p);
  if (!(p.startsWith(repo)) || !fs.existsSync(p) || fs.statSync(p).isDirectory()) { res.writeHead(404); return res.end(); }
  res.writeHead(200, {'Content-Type': types[path.extname(p).toLowerCase()] || 'application/octet-stream', 'Cache-Control': 'no-store'});
  fs.createReadStream(p).pipe(res);
}).listen(8766, '127.0.0.1');
console.log('http://127.0.0.1:8766/cabin_preview.html');
