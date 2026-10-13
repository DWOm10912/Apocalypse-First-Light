// Re-render server for the forest start home concept (design use only, not a game asset).
// node serve.cjs, open http://127.0.0.1:8765/forest_home_concept.html, then in the page console:
//   await renderAll()     renders the 14 views in memory
//   await renderSheets()  composes the six labelled sheets
//   await renderStep('B_hero')  renders one view and saves it as fh_B_hero.png
// POST /save?name=x.png writes into ../sheets, so the sheets there are overwritten.
const http = require('http'), fs = require('fs'), path = require('path');
const root = __dirname, out = path.join(__dirname, '..', 'sheets');
const types = {'.html': 'text/html; charset=utf-8', '.png': 'image/png', '.js': 'text/javascript'};
fs.mkdirSync(out, {recursive: true});
http.createServer((req, res) => {
  const url = new URL(req.url, 'http://x');
  if (req.method === 'POST' && url.pathname === '/save') {
    const name = path.basename(url.searchParams.get('name') || 'out.png');
    const chunks = [];
    req.on('data', c => chunks.push(c));
    req.on('end', () => { fs.writeFileSync(path.join(out, name), Buffer.concat(chunks)); res.writeHead(200); res.end('ok'); });
    return;
  }
  const p = path.join(root, decodeURIComponent(url.pathname));
  if (!p.startsWith(path.normalize(root)) || !fs.existsSync(p) || fs.statSync(p).isDirectory()) { res.writeHead(404); return res.end(); }
  res.writeHead(200, {'Content-Type': types[path.extname(p).toLowerCase()] || 'application/octet-stream'});
  fs.createReadStream(p).pipe(res);
}).listen(8765, '127.0.0.1');
console.log('http://127.0.0.1:8765/forest_home_concept.html');
