// Render benchmark (2026-10-08, docs/dev/render_performance_v1.md): fixed camera views in the Fuel Stop A1 store, timed
// by the development-only AflRenderProfiler lines in run/logs/latest.log ("[AFL RENDER PROFILE] pack=...; switches=...;
// N frames, X ms/frame ... | renderer: main+shadow calls, main+shadow ms, max ms | ..."). Same save, same views, same
// shader pack before and after a change: run it with the game open (Creative, first person), the A1 authoring session
// resumed (/afl_author resume gas_station_02_store -96 -53 448 31 20 10 2) and the shader pack under test active. A fixed
// time of day (doDaylightCycle off) keeps the shadow light still between runs.
//   node tools/afl_minecraft_mcp/render_benchmark.mjs <label> [hold seconds, default 16] [modes, default "current"]
// modes: comma-separated switch sets of AflRenderDev, each run at every view before the camera moves on (so all of them
// see the same sun): current (leave the switches as they are), off (both off: the old path), cull (shadow_cull only),
// mesh (static_mesh only), both. A mode other than current is written to run/afl_render_dev.properties, which the
// development client reads once a second; the profiler line names the switches in effect, and only windows whose
// switches match the mode count. After the run (or on Ctrl+C) the control file is removed, which restores both switches.
// It moves the camera through VIEWS (camera_move: no blocks change), holds each, then restores the player and writes
// build/render_benchmarks/<label>.json plus a table on stdout. Only profiler windows that lie wholly inside a hold (after
// 1.5 s of settling) count; a view with none is reported as such.
import {BridgeClient} from './bridge_client.mjs';
import {mkdir, readFile, rm, writeFile} from 'node:fs/promises';
import path from 'node:path';

const ID = 'gas_station_02_store', ORIGIN = [-96, -53, 448];
// paper frame of fuel_stop_a1_store.mjs (X east, Z south, k up) -> world; paper north = world south (yaw 0), paper east = yaw 90
const world = (X, k, Z) => [ORIGIN[0] + 28 - X, ORIGIN[1] + k + 2, ORIGIN[2] + 18 - Z];
export const VIEWS = [
  {name: 'stock_room', at: world(19.5, 0, 2.5), yaw: -90, pitch: 5, note: 'stock room toward the walk-in steel door, racks to the left'},
  {name: 'walk_in', at: world(12.5, 0, 3.5), yaw: 180, pitch: 5, note: 'walk-in cooler toward the beverage coolers\' backs'},
  {name: 'back_exterior', at: world(14, 0, -4), yaw: 180, pitch: 0, note: 'outside the back wall, toward the doors and the meter box'},
  {name: 'sales_coolers', at: world(13.5, 0, 7.5), yaw: 0, pitch: 5, note: 'sales floor, in front of the three beverage coolers'},
  {name: 'front_doors', at: world(3, 0, 16.5), yaw: 0, pitch: 0, note: 'outside the west entrance, toward the glass doors'},
  {name: 'sky', at: world(14, 0, 20), yaw: 0, pitch: -75, note: 'in front of the store, looking up (control)'},
];
export const MODES = {
  off: {shadow_cull: 'off', static_mesh: 'off'},
  cull: {shadow_cull: 'on', static_mesh: 'off'},
  mesh: {shadow_cull: 'off', static_mesh: 'on'},
  both: {shadow_cull: 'on', static_mesh: 'on'},
};
const CONTROL = path.resolve('run/afl_render_dev.properties');

const LINE = /^\[(\d{2})(\S*) (\d{2}):(\d{2}):(\d{2})\.(\d{3})\].*\[AFL RENDER PROFILE\] pack=([^;]*); (?:switches=([^;]*); )?(\d+) frames, ([\d.]+) ms\/frame \((\d+) fps\); AFL BERs ([\d.]+) ms\/frame \|(.*)$/;
function parseLog(text) {
  const out = [];
  for (const raw of text.split(/\r?\n/)) {
    const m = raw.match(LINE);
    if (!m) continue;
    const [, , , hh, mm, ss, ms, pack, switches, frames, frameMs, fps, berMs, rest] = m;
    const renderers = {};
    for (const cell of rest.split('|')) {
      const r = cell.trim().match(/^(\S+): ([\d.]+)\+([\d.]+) calls, ([\d.]+)\+([\d.]+) ms, max ([\d.]+) ms$/);
      if (r) renderers[r[1]] = {mainCalls: +r[2], shadowCalls: +r[3], mainMs: +r[4], shadowMs: +r[5], maxMs: +r[6]};
    }
    out.push({clock: ((+hh * 60 + +mm) * 60 + +ss) * 1000 + +ms, pack: pack.trim(), switches: (switches || '').trim(),
      frames: +frames, frameMs: +frameMs, fps: +fps, berMs: +berMs, renderers});
  }
  return out;
}
const switchLabel = m => `shadow_cull=${m.shadow_cull},static_mesh=${m.static_mesh}`;
const clockNow = () => { const d = new Date(); return ((d.getHours() * 60 + d.getMinutes()) * 60 + d.getSeconds()) * 1000 + d.getMilliseconds(); };
const sleep = ms => new Promise(res => setTimeout(res, ms));
const writeControl = m => writeFile(CONTROL, `# written by tools/afl_minecraft_mcp/render_benchmark.mjs\nshadow_cull=${m.shadow_cull}\nstatic_mesh=${m.static_mesh}\n`);

async function main() {
  const label = process.argv[2];
  if (!label || !/^[\w.-]+$/.test(label)) throw Error('usage: render_benchmark.mjs <label> [hold seconds] [modes]');
  const hold = (+process.argv[3] || 16) * 1000;
  const modes = (process.argv[4] || 'current').split(',').map(s => s.trim()).filter(Boolean);
  for (const m of modes) if (m !== 'current' && !MODES[m]) throw Error('unknown mode ' + m + ' (current, ' + Object.keys(MODES).join(', ') + ')');
  const c = new BridgeClient('./run');
  const status = await c.call('minecraft_status');
  const info = await c.call('authoring_info');
  if (info.id !== ID) throw Error('PLOT_MISMATCH: resume ' + ID + ' first');
  const spans = [];
  let wroteControl = false, lastMesh = null, cleaned = false;
  // restore the camera and the switches however the run ends (Ctrl+C included); a second signal exits at once
  const cleanup = async () => {
    if (cleaned) return;
    cleaned = true;
    await c.call('camera_restore').catch(e => console.error('camera_restore failed: ' + e.message));
    if (wroteControl) await rm(CONTROL, {force: true}).catch(() => {});   // removing it restores both switches
  };
  for (const signal of ['SIGINT', 'SIGTERM']) process.once(signal, () => { cleanup().finally(() => process.exit(130)); });
  try {
    for (const v of VIEWS) {
      // whole-block x / z go to the cell centre; an obstructed spot tries small horizontal offsets
      const base = v.at.map((x, i) => i !== 1 && x % 1 === 0 ? x + 0.5 : x);
      let moved = null;
      for (const [dx, dz] of [[0, 0], [0.4, 0], [-0.4, 0], [0, 0.4], [0, -0.4]]) {
        try { await c.call('camera_move', {position: [base[0] + dx, base[1], base[2] + dz], yaw: v.yaw, pitch: v.pitch}); moved = [dx, dz]; break; }
        catch (e) { if (!/OBSTRUCTED|ENTITY_AT_DESTINATION/.test(e.message)) throw e; }
      }
      if (!moved) { console.error('view ' + v.name + ': no free spot, skipped'); continue; }
      for (let i = 0; i < 40; i++) { if ((await c.call('camera_status')).client_frame_ready) break; await sleep(100); }
      for (const mode of modes) {
        if (mode !== 'current') {
          await writeControl(MODES[mode]);
          wroteControl = true;
          // read within a second; a static_mesh change also rebuilds every chunk
          const rebuild = lastMesh === null || lastMesh !== MODES[mode].static_mesh;
          lastMesh = MODES[mode].static_mesh;
          await sleep(rebuild ? 6000 : 2000);
        }
        const start = clockNow();
        await sleep(hold);
        spans.push({view: v, mode, start, end: clockNow(), offset: moved});
      }
    }
  } finally {
    await cleanup();
  }
  await sleep(5500);   // let the last window be written
  const lines = parseLog(await readFile(path.resolve('run/logs/latest.log'), 'utf8'));
  const report = {label, when: new Date().toISOString(), hold_ms: hold, modes, world: status.world, views: []};
  for (const {view, mode, start, end, offset} of spans) {
    // a profiler line is written at its window's end; a window lasts about 5 s
    const expected = mode === 'current' ? null : switchLabel(MODES[mode]);
    const window = lines.filter(l => l.clock - 5200 >= start + 1500 && l.clock <= end + 200);
    const inside = window.filter(l => expected === null || l.switches === expected);
    const avg = k => inside.length ? inside.reduce((s, l) => s + l[k], 0) / inside.length : null;
    const renderers = {};
    for (const l of inside) for (const [name, r] of Object.entries(l.renderers)) {
      const acc = renderers[name] ||= {mainCalls: 0, shadowCalls: 0, mainMs: 0, shadowMs: 0, maxMs: 0};
      for (const k of ['mainCalls', 'shadowCalls', 'mainMs', 'shadowMs']) acc[k] += r[k] / inside.length;
      acc.maxMs = Math.max(acc.maxMs, r.maxMs);
    }
    report.views.push({name: view.name, mode, note: view.note, offset, windows: inside.length, rejected: window.length - inside.length,
      packs: [...new Set(inside.map(l => l.pack))], switches: [...new Set(inside.map(l => l.switches))],
      fps: avg('fps'), frameMs: avg('frameMs'), berMs: avg('berMs'), renderers});
  }
  const dir = path.resolve('build/render_benchmarks');
  await mkdir(dir, {recursive: true});
  await writeFile(path.join(dir, label + '.json'), JSON.stringify(report, null, 1));
  const f = (x, d = 2) => x == null ? '-' : x.toFixed(d);
  console.log(`label ${label}  hold ${hold / 1000}s  modes ${modes.join(',')}`);
  console.log('view            mode     windows  pack                 fps    ms/frame  AFL BER ms  top renderers (main+shadow ms)');
  for (const v of report.views) {
    const top = Object.entries(v.renderers).filter(([n]) => !n.includes('.')).sort((a, b) => (b[1].mainMs + b[1].shadowMs) - (a[1].mainMs + a[1].shadowMs)).slice(0, 4)
      .map(([n, r]) => `${n} ${f(r.mainMs)}+${f(r.shadowMs)}`).join(', ');
    const windows = String(v.windows) + (v.rejected ? `(-${v.rejected})` : '');
    console.log(`${v.name.padEnd(16)}${v.mode.padEnd(9)}${windows.padEnd(9)}${(v.packs.join(',') || '-').slice(0, 20).padEnd(21)}${f(v.fps, 0).padEnd(7)}${f(v.frameMs).padEnd(10)}${f(v.berMs).padEnd(12)}${top}`);
  }
  console.log('wrote ' + path.relative(process.cwd(), path.join(dir, label + '.json')));
}
main().catch(e => { console.error(e.stack || e.message); process.exit(1); });
