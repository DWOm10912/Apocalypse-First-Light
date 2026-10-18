<script setup lang="ts">
import { computed } from 'vue'
import { data } from '../../data/game.data.mjs'
import { useLoc, MODE_NAMES, SLOT_NAMES, damageAt, fmt } from '../i18n'

const props = defineProps<{ id: string; part: 'compare' | 'table' | 'falloff' | 'attachments' }>()
const { loc, t, link } = useLoc()
const all = data.guns
const g = computed(() => all.find((x: any) => x.id === props.id))

// ---- comparison bars against every gun in the game --------------------------
const perShot = (x: any) => x.damage.base * x.pellets
const avg = (f: (x: any) => number) => all.reduce((s: number, x: any) => s + f(x), 0) / all.length
const meters = computed(() => {
  const x = g.value
  const maxDmg = Math.max(...all.map(perShot))
  const maxCap = Math.max(...all.map((y: any) => y.capacity))
  const maxRange = Math.max(...all.map((y: any) => y.damage.maxRange))
  const minReload = Math.min(...all.map((y: any) => y.reload.tactical))
  const maxNoise = Math.max(...all.map((y: any) => y.noise.radius))
  const maxRpm = Math.max(...all.map((y: any) => Math.max(...y.modes.map((m: any) => m.rpm))))
  const rpm = (y: any) => Math.max(...y.modes.map((m: any) => m.rpm))
  return [
    { lb: t('单发伤害', 'Damage per shot'), v: perShot(x) / maxDmg, a: avg(perShot) / maxDmg, txt: `${fmt(perShot(x))}`, sub: t(`最高 ${fmt(maxDmg)}`, `max ${fmt(maxDmg)}`) },
    { lb: t('射速', 'Fire rate'), v: rpm(x) / maxRpm, a: avg(rpm) / maxRpm, txt: `${rpm(x)}`, sub: t('发/分', 'rpm') },
    { lb: t('弹匣容量', 'Magazine'), v: x.capacity / maxCap, a: avg((y: any) => y.capacity) / maxCap, txt: `${x.capacity}`, sub: t(`最高 ${maxCap}`, `max ${maxCap}`) },
    { lb: t('最大射程', 'Max range'), v: x.damage.maxRange / maxRange, a: avg((y: any) => y.damage.maxRange) / maxRange, txt: `${x.damage.maxRange}`, sub: t('格', 'blocks') },
    { lb: t('换弹速度', 'Reload speed'), v: minReload / x.reload.tactical, a: avg((y: any) => minReload / y.reload.tactical), txt: `${fmt(x.reload.tactical)}`, sub: t('秒', 's') },
    { lb: t('安静程度', 'Quietness'), v: 1 - x.noise.radius / maxNoise, a: avg((y: any) => 1 - y.noise.radius / maxNoise), txt: `${x.noise.radius}`, sub: t('格枪声', 'block noise') }
  ]
})

// ---- full parameter table ----------------------------------------------------
const rows = computed(() => {
  const x = g.value
  const u = (zh: string, en: string) => t(zh, en)
  const r: [string, string][] = [
    [u('类别', 'Class'), ''],
    [u('弹药', 'Ammo'), x.ammo.name[loc.value]],
    [u('弹匣容量', 'Magazine'), `${x.capacity} ${u('发', 'rds')}`],
    [u('射击模式', 'Fire modes'), x.modes.map((m: any) => MODE_NAMES[m.mode]?.[loc.value] ?? m.mode).join(' / ')],
    [u('默认模式', 'Default mode'), MODE_NAMES[x.defaultMode]?.[loc.value] ?? x.defaultMode]
  ]
  r.shift()
  if (x.pellets > 1) r.push([u('每发弹丸', 'Pellets per shot'), `${x.pellets}`])
  r.push([u('基础伤害（生命值）', 'Base damage (health)'), x.pellets > 1 ? `${fmt(x.damage.base)} × ${x.pellets}` : fmt(x.damage.base)])
  r.push([u('爆头倍率', 'Headshot multiplier'), x.damage.headshot ? `×${fmt(x.damage.headshot)}` : u('×1.5（服务器默认）', '×1.5 (server default)')])
  r.push([u('衰减起点', 'Falloff start'), `${x.damage.falloffStart} ${u('格', 'blocks')}`])
  r.push([u('标称射程（物品说明）', 'Listed range (tooltip)'), `${x.damage.effectiveRange} ${u('格', 'blocks')}`])
  r.push([u('最大射程', 'Max range'), `${x.damage.maxRange} ${u('格', 'blocks')}`])
  r.push([u('最大射程处伤害', 'Damage at max range'), `${fmt(x.damage.base * x.damage.minMultiplier)}（${Math.round(x.damage.minMultiplier * 100)}%）`])
  for (const m of x.modes) {
    const name = MODE_NAMES[m.mode]?.[loc.value] ?? m.mode
    r.push([`${u('射速', 'Fire rate')} · ${name}`, `${m.rpm} ${u('发/分', 'rpm')}（${m.intervalTicks} tick）`])
    if (m.bloom) r.push([`${u('连射散布', 'Bloom')} · ${name}`, u(`每发 +${fmt(m.bloom.per_shot_degrees)}°，最多 ${fmt(m.bloom.max_degrees)}°`, `+${fmt(m.bloom.per_shot_degrees)}° per shot, max ${fmt(m.bloom.max_degrees)}°`)])
  }
  r.push([u('腰射散布', 'Hip spread'), `${fmt(x.spread.hip)}°`])
  if (x.spread.ads != null) r.push([u('瞄准散布', 'ADS spread'), `${fmt(x.spread.ads)}°`])
  if (x.ads.time != null) r.push([u('开镜时间', 'ADS time'), `${fmt(x.ads.time)} ${u('秒', 's')}`])
  r.push([u('战术换弹', 'Tactical reload'), `${fmt(x.reload.tactical)} ${u('秒', 's')}`])
  r.push([u('空仓换弹', 'Empty reload'), `${fmt(x.reload.empty)} ${u('秒', 's')}`])
  r.push([u('枪声半径', 'Noise radius'), `${x.noise.radius} ${u('格', 'blocks')}${x.noise.tinnitus ? u('，会耳鸣', ', causes tinnitus') : ''}`])
  const sup = x.attachments.find((a: any) => a.slot === 'muzzle')
  if (sup) r.push([u('装消音器后', 'Suppressed'), `${Math.max(1, Math.round(x.noise.radius * sup.noiseMultiplier))} ${u('格', 'blocks')}`])
  if (x.massKg != null) r.push([u('重量', 'Weight'), `${fmt(x.massKg, 3)} kg${u('（含空弹匣）', ' (with empty magazine)')}`])
  if (x.ammo.massKg != null) r.push([u('单发弹药重量', 'Round weight'), `${fmt(x.ammo.massKg * 1000, 1)} g`])
  if (x.grid) r.push([u('背包占格', 'Inventory size'), x.grid.replace('x', ' × ')])
  r.push([u('物品 ID', 'Item ID'), `apocalypse_firstlight:${x.id}`])
  return r
})

// ---- falloff chart -----------------------------------------------------------
const W = 560, H = 190, L = 44, R = 16, T = 18, B = 30
const chart = computed(() => {
  const d = g.value.damage
  const xMax = Math.ceil((d.maxRange * 1.12) / 8) * 8
  const yMax = d.base
  const X = (v: number) => L + (v / xMax) * (W - L - R)
  const Y = (v: number) => T + (1 - v / yMax) * (H - T - B)
  const pts = [[0, d.base], [d.falloffStart, d.base], [d.maxRange, d.base * d.minMultiplier]]
  const line = pts.map(([a, b]) => `${X(a).toFixed(1)},${Y(b).toFixed(1)}`).join(' ')
  const ticks = [0, d.falloffStart, d.maxRange]
  return { X, Y, line, ticks, xMax, d, end: { x: X(d.maxRange), y: Y(d.base * d.minMultiplier) }, floor: Y(0) }
})
const samples = computed(() => {
  const d = g.value.damage
  return [10, 25, 50, 75, 100].map((m) => ({ m, v: damageAt(d, m) }))
})
</script>

<template>
  <div v-if="g">
    <div v-if="part === 'compare'" class="afl-card meters">
      <div v-for="m in meters" :key="m.lb" class="m">
        <span class="lb">{{ m.lb }}</span>
        <span class="tr"><i :style="{ width: Math.max(2, m.v * 100) + '%' }"></i><u :style="{ left: m.a * 100 + '%' }"></u></span>
        <span class="v afl-mono">{{ m.txt }} <small>{{ m.sub }}</small></span>
      </div>
      <div class="mnote"><span>{{ g.name[loc] }}</span><span class="g">{{ t(`全部 ${all.length} 把枪的平均值`, `Average of all ${all.length} guns`) }}</span></div>
    </div>

    <div v-else-if="part === 'table'" class="afl-card tbl">
      <table>
        <tbody>
          <tr v-for="[k, v] in rows" :key="k"><th>{{ k }}</th><td>{{ v }}</td></tr>
        </tbody>
      </table>
      <div class="afl-src foot">{{ t('数值来自', 'From') }} <code>{{ g.source }}</code> · {{ data.version.mod }} · {{ data.version.commit }}</div>
    </div>

    <div v-else-if="part === 'falloff'" class="afl-card fall">
      <svg :viewBox="`0 0 ${W} ${H}`" role="img" :aria-label="t('伤害随距离变化', 'Damage over distance')">
        <g stroke="#262b33">
          <line v-for="f in [0, .25, .5, .75, 1]" :key="f" :x1="L" :x2="W - R" :y1="chart.Y(chart.d.base * f)" :y2="chart.Y(chart.d.base * f)" />
        </g>
        <rect :x="chart.X(chart.d.maxRange)" :y="T" :width="W - R - chart.X(chart.d.maxRange)" :height="H - T - B" fill="#ef7f6c" opacity=".06" />
        <line :x1="chart.X(chart.d.effectiveRange)" :x2="chart.X(chart.d.effectiveRange)" :y1="T" :y2="H - B" stroke="#7fb6ea" stroke-dasharray="2 4" />
        <text :x="chart.X(chart.d.effectiveRange) + 5" :y="T + 11" fill="#7fb6ea" font-size="11">{{ t('标称射程', 'Listed range') }} {{ chart.d.effectiveRange }}</text>
        <polyline :points="chart.line" fill="none" stroke="#f0a53a" stroke-width="2.5" stroke-linejoin="round" />
        <line :x1="chart.end.x" :y1="chart.end.y" :x2="chart.end.x" :y2="chart.floor" stroke="#f0a53a" stroke-width="1.5" stroke-dasharray="3 3" />
        <g fill="#8d939b" font-size="11" font-family="JetBrains Mono, monospace">
          <text :x="L - 8" :y="chart.Y(chart.d.base) + 4" text-anchor="end">{{ fmt(chart.d.base) }}</text>
          <text :x="L - 8" :y="chart.Y(chart.d.base * chart.d.minMultiplier) + 4" text-anchor="end">{{ fmt(chart.d.base * chart.d.minMultiplier, 1) }}</text>
          <text v-for="v in chart.ticks" :key="v" :x="chart.X(v)" :y="H - 10" text-anchor="middle">{{ v }}</text>
        </g>
      </svg>
      <div class="cap">
        {{ t(`${chart.d.falloffStart} 格以内满伤害，之后一路下降，到最大射程 ${chart.d.maxRange} 格时剩 ${Math.round(chart.d.minMultiplier * 100)}%，再远子弹就没了。物品说明里的“射程”是标称射程，不影响伤害`, `Full damage up to ${chart.d.falloffStart} blocks, then it drops steadily to ${Math.round(chart.d.minMultiplier * 100)}% at the ${chart.d.maxRange}-block max range; the bullet goes no further. The tooltip's Range line is a listed value and does not change damage`) }}
        <span v-if="g.pellets > 1">{{ t(`（每颗弹丸，共 ${g.pellets} 颗）`, ` (per pellet, ${g.pellets} pellets)`) }}</span>
      </div>
      <div class="samples">
        <span v-for="s in samples" :key="s.m"><small>{{ s.m }} {{ t('格', 'm') }}</small><b class="afl-mono">{{ s.v ? fmt(s.v, 1) : '—' }}</b></span>
      </div>
    </div>

    <div v-else-if="part === 'attachments'">
      <div v-if="g.attachments.length" class="atts">
        <div v-for="a in g.attachments" :key="a.id" class="afl-card">
          <span class="afl-pill">{{ SLOT_NAMES[a.slot]?.[loc] ?? a.slot }}</span>
          <b>{{ a.name[loc] }}</b>
          <span class="eff">
            <template v-if="a.slot === 'muzzle'">{{ t(`枪声 ${g.noise.radius} → ${Math.max(1, Math.round(g.noise.radius * a.noiseMultiplier))} 格`, `Noise ${g.noise.radius} → ${Math.max(1, Math.round(g.noise.radius * a.noiseMultiplier))} blocks`) }}</template>
            <template v-else-if="a.slot === 'magazine'">{{ t(`${g.capacity} → ${a.rounds} 发`, `${g.capacity} → ${a.rounds} rounds`) }}<template v-if="a.emptyReload">{{ t(`，空仓换弹 ${fmt(a.emptyReload)} 秒`, `, empty reload ${fmt(a.emptyReload)} s`) }}</template></template>
            <template v-else>{{ t('瞄准点移到红点上', 'Aims through the dot') }}</template>
          </span>
          <code>{{ a.id }}</code>
        </div>
      </div>
      <p v-else class="none">{{ t('这把枪不能装配件', 'This gun takes no attachments') }}</p>
      <p class="hint">{{ t('配件可以原地换装，也可以在维护工作台上换', 'Swap them in the field or at a maintenance bench') }} · <a :href="link('/arms/attachments')">{{ t('全部配件', 'All attachments') }}</a></p>
    </div>
  </div>
</template>

<style scoped>
.meters { padding: 16px 20px; }
.m { display: grid; grid-template-columns: 110px 1fr 120px; align-items: center; gap: 14px; padding: 7px 0; font-size: 13.5px; }
.lb { color: var(--afl-dim); }
.tr { height: 8px; border-radius: 4px; background: rgba(255,255,255,.06); position: relative; }
.tr i { position: absolute; left: 0; top: 0; bottom: 0; border-radius: 4px; background: linear-gradient(90deg, #c67d1f, var(--afl-amber2)); }
.tr u { position: absolute; top: -4px; width: 2px; height: 16px; background: #6c727b; }
.v { text-align: right; color: var(--afl-text); }
.v small { color: var(--afl-dim); font-family: var(--vp-font-family-base); font-size: 12px; }
.mnote { font-size: 12px; color: var(--afl-dim); margin-top: 8px; display: flex; gap: 16px; flex-wrap: wrap; }
.mnote span::before { content: ''; display: inline-block; width: 10px; height: 4px; border-radius: 2px; background: var(--afl-amber); margin-right: 6px; vertical-align: middle; }
.mnote span.g::before { width: 2px; height: 10px; background: #6c727b; }
.tbl { padding: 4px 0 0; overflow: hidden; }
.tbl table { display: table; width: 100%; margin: 0; border-collapse: collapse; }
.tbl th, .tbl td { border: 0; border-bottom: 1px solid var(--afl-line); padding: 9px 18px; font-size: 14px; text-align: left; }
.tbl th { background: transparent; color: var(--afl-dim); font-weight: 400; width: 40%; }
.tbl td { color: var(--afl-text); }
.foot { padding: 10px 18px 12px; }
.fall { padding: 16px 18px 14px; }
.fall svg { width: 100%; height: auto; display: block; }
.cap { font-size: 13.5px; color: #cbc5ba; margin-top: 6px; }
.samples { display: flex; gap: 8px; flex-wrap: wrap; margin-top: 10px; }
.samples span { background: var(--afl-card2); border: 1px solid var(--afl-line); border-radius: 10px; padding: 6px 12px; }
.samples small { display: block; font-size: 11px; color: var(--afl-dim); }
.samples b { font-size: 16px; color: var(--afl-text); }
.atts { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; }
.atts > div { padding: 14px 16px; display: flex; flex-direction: column; gap: 6px; align-items: flex-start; }
.atts b { font-size: 14.5px; color: var(--afl-text); }
.atts .eff { font-size: 13px; color: var(--afl-blue); }
.atts code { font-size: 11px; color: var(--afl-dim); background: none; padding: 0; }
.hint, .none { font-size: 13px; color: var(--afl-dim); }
@media (max-width: 760px) {
  .m { grid-template-columns: 80px 1fr 86px; gap: 8px; font-size: 12.5px; }
  .atts { grid-template-columns: 1fr; }
  .tbl th, .tbl td { padding: 8px 12px; font-size: 13px; }
}
</style>
