// One-step deploy of the wiki to Cloudflare Pages (Direct Upload with wrangler). Main workspace on master only.
//
//   npm run deploy                  checks -> build -> post-build checks -> wrangler pages deploy (production)
//   npm run deploy -- --dry-run     everything except the upload (any workspace may run this; public images not
//                                   uploaded yet are only warnings here, a real deploy refuses them)
//   npm run deploy:probe            deploy a one-page site with no content to production and to a preview branch,
//                                   to test Cloudflare Access before any real content goes up
//
// Nothing is uploaded unless every step passes, so a failed run leaves the live site as it was. The full wiki is
// refused until deploy.config.json has accessVerified: true (set by the user after the probe check).
// wrangler must be logged in (npx wrangler login, done once by the user) or CLOUDFLARE_API_TOKEN set.
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import { execFileSync, spawnSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'
import { requireMain, git } from './lib/main-guard.mjs'

const WIKI = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const REPO = path.resolve(WIKI, '..')
const args = process.argv.slice(2)
const dryRun = args.includes('--dry-run')
const probe = args.includes('--probe')
const cfg = JSON.parse(fs.readFileSync(path.join(WIKI, 'deploy.config.json'), 'utf8'))
const lock = JSON.parse(fs.readFileSync(path.join(WIKI, 'images.lock.json'), 'utf8'))
const images = JSON.parse(fs.readFileSync(path.join(WIKI, 'images.json'), 'utf8'))
// Tools run as node + their JS entry, never through a shell (paths and commit messages contain spaces).
const TOOL = { vitepress: 'node_modules/vitepress/bin/vitepress.js', wrangler: 'node_modules/wrangler/bin/wrangler.js' }
const tool = (name, toolArgs) => [process.execPath, [path.join(WIKI, TOOL[name]), ...toolArgs]]

function step(title) { console.log(`\n== ${title}`) }
function fail(msg) { console.log(`\nDEPLOY STOPPED: ${msg}\nNothing was uploaded; the live site is unchanged.`); process.exit(1) }
function run(cmd, cmdArgs) {
  const r = spawnSync(cmd, cmdArgs, { cwd: WIKI, stdio: 'inherit' })
  return r.status === 0
}

// ---- 1. who and what -------------------------------------------------------------------------------------------
step('workspace')
if (!dryRun) { try { requireMain(REPO) } catch (e) { fail(e.message) } }
const commit = git(REPO, ['rev-parse', 'HEAD'])
const subject = git(REPO, ['log', '-1', '--format=%s']).slice(0, 300)
const branch = git(REPO, ['rev-parse', '--abbrev-ref', 'HEAD'])
console.log(`${branch} @ ${commit.slice(0, 8)}  ${subject.slice(0, 80)}`)
let dirtyWiki = 0
if (!probe) {
  // The game numbers must be the committed main line: no uncommitted data, code or version changes. Uncommitted
  // wiki pages are allowed (the user commits on their own schedule); the site then says so next to the commit.
  const dirtyGame = git(REPO, ['status', '--porcelain', '--', 'src/main/resources', 'src/main/java', 'gradle.properties']).split('\n').filter(Boolean)
  if (dirtyGame.length && !dryRun) fail(`uncommitted changes in the game files the wiki reads:\n${dirtyGame.slice(0, 15).join('\n')}`)
  if (dirtyGame.length) console.log(`(dry run: ${dirtyGame.length} uncommitted game file(s) ignored)`)
  dirtyWiki = git(REPO, ['status', '--porcelain', '--', 'wiki']).split('\n').filter(Boolean).length
  if (dirtyWiki) console.log(`${dirtyWiki} uncommitted wiki file(s): the site shows them after the commit`)
  if (!cfg.accessVerified && !dryRun) fail('Cloudflare Access is not verified yet (deploy.config.json accessVerified: false). Run npm run deploy:probe and check the probe pages first.')
}

const out = fs.mkdtempSync(path.join(os.tmpdir(), probe ? 'afl-wiki-probe-' : 'afl-wiki-dist-'))

if (probe) {
  // ---- probe: a page and an asset with no content, to see that Access guards every address --------------------
  const stamp = new Date().toISOString()
  fs.mkdirSync(path.join(out, 'assets'), { recursive: true })
  fs.writeFileSync(path.join(out, 'index.html'), `<!doctype html><meta charset="utf-8"><meta name="robots" content="noindex"><title>Access probe</title><p>AFL wiki access probe ${stamp}</p><p><a href="/assets/probe.txt">asset</a></p>`)
  fs.writeFileSync(path.join(out, 'assets/probe.txt'), `probe asset ${stamp}\n`)
} else {
  // ---- 2. checks before building ----------------------------------------------------------------------------
  step(dryRun ? 'wiki check' : 'wiki check (strict: public images uploaded and reachable)')
  if (!run(process.execPath, ['scripts/check-wiki.mjs', ...(dryRun ? [] : ['--strict'])])) fail('wiki check failed')

  // ---- 3. build ---------------------------------------------------------------------------------------------
  step('build')
  if (!run(...tool('vitepress', ['build', '.', '--outDir', out]))) fail('vitepress build failed (dead internal links fail the build)')

  // ---- 4. after the build ------------------------------------------------------------------------------------
  step('post-build checks')
  const problems = []
  const stagedRefs = []
  for (const f of ['index.html', 'en/index.html', '404.html', 'about/index.html', 'changelog/updates.html']) if (!fs.existsSync(path.join(out, f))) problems.push(`missing ${f}`)
  const publicUrls = new Set(Object.values(lock).map((e) => e.url))
  const internalKeys = Object.entries(images).filter(([k, v]) => !k.startsWith('_') && v.visibility === 'internal').map(([k]) => k)
  let files = 0
  const walk = (dir) => {
    for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
      const p = path.join(dir, e.name)
      if (e.isDirectory()) { walk(p); continue }
      if (!/\.(html|js|css|json)$/.test(e.name)) continue
      files++
      const text = fs.readFileSync(p, 'utf8')
      for (const m of text.matchAll(/https:\/\/img\.staticgrove\.net\/[A-Za-z0-9._\-/]+/g)) {
        if (!publicUrls.has(m[0])) problems.push(`${path.relative(out, p)} references ${m[0]}, which is not in images.lock.json`)
        if (internalKeys.some((k) => m[0].includes(`/${k}.`))) problems.push(`${path.relative(out, p)}: internal image served from the public domain: ${m[0]}`)
      }
      // a real staged file name (the theme code itself contains the bare /img/staged/ fallback prefix)
      if (/\/img\/staged\/[a-z0-9_]+\.(webp|png|jpe?g)/.test(text)) (dryRun ? stagedRefs : problems).push(`${path.relative(out, p)} references a staged image (not uploaded)`)
    }
  }
  walk(out)
  if (fs.existsSync(path.join(out, 'img/staged'))) fs.rmSync(path.join(out, 'img/staged'), { recursive: true })
  if (problems.length) fail(`post-build checks:\n${[...new Set(problems)].slice(0, 30).join('\n')}`)
  if (stagedRefs.length) console.log(`  warn  ${stagedRefs.length} file(s) use staged images; a real deploy needs them uploaded first`)
  console.log(`${files} files scanned, ${publicUrls.size} public image URL(s), ${internalKeys.length} internal image(s) bundled`)

  // External links are reported, not fatal (sites go down for reasons that are not ours).
  step('external links')
  const ext = new Set()
  const md = (dir) => { for (const e of fs.readdirSync(dir, { withFileTypes: true })) { if (['node_modules', '.vitepress', 'public'].includes(e.name)) continue; const p = path.join(dir, e.name); if (e.isDirectory()) md(p); else if (p.endsWith('.md')) for (const m of fs.readFileSync(p, 'utf8').matchAll(/\]\((https?:\/\/[^)\s]+)\)/g)) ext.add(m[1]) } }
  md(WIKI)
  for (const u of ext) {
    try {
      const r = await fetch(u, { method: 'HEAD', redirect: 'follow', signal: AbortSignal.timeout(15000) })
      if (r.status >= 400) console.log(`  warn  ${u} answered HTTP ${r.status}`)
    } catch (e) { console.log(`  warn  ${u} unreachable (${e.message})`) }
  }
  console.log(`${ext.size} external link(s) checked`)
}

// ---- 5. files every deploy carries: no indexing, no framing, build stamp ----------------------------------------
fs.writeFileSync(path.join(out, 'robots.txt'), 'User-agent: *\nDisallow: /\n')
fs.writeFileSync(path.join(out, '_headers'), [
  '/*',
  '  X-Robots-Tag: noindex, nofollow',
  '  X-Frame-Options: DENY',
  '  Referrer-Policy: same-origin',
  '  X-Content-Type-Options: nosniff',
  '/assets/*',
  '  Cache-Control: public, max-age=31536000, immutable',
  ''
].join('\n'))
fs.writeFileSync(path.join(out, 'build.json'), JSON.stringify({ probe, commit, branch, subject, uncommittedWikiFiles: dirtyWiki, built: new Date().toISOString() }, null, 2))

if (dryRun) {
  console.log(`\nDry run passed. Output: ${out}\nNothing was uploaded.`)
  process.exit(0)
}

// ---- 6. upload -------------------------------------------------------------------------------------------------
step(`deploy to Cloudflare Pages project ${cfg.project}`)
let projects = ''
try { const [n, a] = tool('wrangler', ['pages', 'project', 'list', '--json']); projects = execFileSync(n, a, { cwd: WIKI, encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] }) } catch (e) {
  fail(`wrangler cannot reach Cloudflare (not logged in? run: npx wrangler login in wiki/)\n${String(e.stderr ?? e.message).slice(0, 400)}`)
}
if (!projects.includes(`"${cfg.project}"`)) {
  if (!probe) fail(`Pages project ${cfg.project} does not exist yet; the first deploy must be npm run deploy:probe`)
  console.log(`creating Pages project ${cfg.project} (production branch ${cfg.productionBranch})`)
  if (!run(...tool('wrangler', ['pages', 'project', 'create', cfg.project, '--production-branch', cfg.productionBranch]))) fail('could not create the Pages project')
}
const deploy = (br) => run(...tool('wrangler', ['pages', 'deploy', out, '--project-name', cfg.project, '--branch', br, '--commit-hash', commit, '--commit-message', probe ? 'access probe' : subject, `--commit-dirty=${dirtyWiki ? 'true' : 'false'}`]))
if (!deploy(cfg.productionBranch)) fail('wrangler pages deploy failed (the previous deployment stays live)')
if (probe && !deploy('probe')) fail('preview (probe branch) deploy failed')

console.log(probe ? `
Probe deployed. Now, in a private window or on a phone that is NOT logged in to Cloudflare Access, open:
  https://${cfg.project}.pages.dev/                 production
  https://probe.${cfg.project}.pages.dev/           preview branch
  https://${cfg.project}.pages.dev/assets/probe.txt static asset
  https://${cfg.customDomain}/                       custom domain (once bound)
Every one must show the Cloudflare Access login, never the probe text. Then set accessVerified in deploy.config.json.`
  : `\nDeployed ${commit.slice(0, 8)} to https://${cfg.customDomain}/ (and https://${cfg.project}.pages.dev/).`)
