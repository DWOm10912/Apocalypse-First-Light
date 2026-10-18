<script setup lang="ts">
import { ref, computed } from 'vue'
import { data } from '../../data/game.data.mjs'
import { useLoc, CLASS_NAMES, fmt } from '../i18n'

const { loc, t, link } = useLoc()
const cols = computed(() => [
  { k: 'name', lb: t('名称', 'Name'), v: (g: any) => g.name[loc.value] },
  { k: 'cls', lb: t('类别', 'Class'), v: (g: any) => CLASS_NAMES[g.weaponClass]?.[loc.value] ?? g.weaponClass },
  { k: 'dmg', lb: t('单发伤害', 'Damage'), v: (g: any) => g.damage.base * g.pellets, show: (g: any) => (g.pellets > 1 ? `${fmt(g.damage.base)}×${g.pellets}` : fmt(g.damage.base)), bar: true },
  { k: 'rpm', lb: t('射速', 'RPM'), v: (g: any) => Math.max(...g.modes.map((m: any) => m.rpm)) },
  { k: 'cap', lb: t('弹匣', 'Mag'), v: (g: any) => g.capacity },
  { k: 'range', lb: t('最大射程', 'Max range'), v: (g: any) => g.damage.maxRange },
  { k: 'reload', lb: t('换弹', 'Reload'), v: (g: any) => g.reload.tactical, show: (g: any) => `${fmt(g.reload.tactical)} s` },
  { k: 'noise', lb: t('枪声', 'Noise'), v: (g: any) => g.noise.radius },
  { k: 'mass', lb: t('重量', 'Weight'), v: (g: any) => g.massKg ?? 0, show: (g: any) => (g.massKg == null ? '—' : `${fmt(g.massKg)} kg`) }
])
const sortKey = ref('dmg')
const desc = ref(true)
const maxDmg = Math.max(...data.guns.map((g: any) => g.damage.base * g.pellets))
const rows = computed(() => {
  const c = cols.value.find((c) => c.k === sortKey.value)!
  return [...data.guns].sort((a: any, b: any) => {
    const x = c.v(a), y = c.v(b)
    const r = typeof x === 'string' ? x.localeCompare(y) : x - y
    return desc.value ? -r : r
  })
})
function sortBy(k: string) {
  if (sortKey.value === k) desc.value = !desc.value
  else { sortKey.value = k; desc.value = k !== 'name' && k !== 'cls' && k !== 'reload' }
}
</script>

<template>
  <div class="afl-card wrap">
    <table>
      <thead>
        <tr>
          <th v-for="c in cols" :key="c.k" :class="{ on: sortKey === c.k }" @click="sortBy(c.k)">
            {{ c.lb }}<span v-if="sortKey === c.k">{{ desc ? ' ↓' : ' ↑' }}</span>
          </th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="g in rows" :key="g.id">
          <td v-for="c in cols" :key="c.k" :class="{ n: c.k !== 'name' && c.k !== 'cls' }">
            <a v-if="c.k === 'name'" :href="link('/arms/guns/' + g.id)">{{ c.v(g) }}</a>
            <template v-else-if="c.bar"><span class="bar" :style="{ width: (c.v(g) / maxDmg) * 70 + 'px' }"></span>{{ c.show!(g) }}</template>
            <template v-else>{{ c.show ? c.show(g) : c.v(g) }}</template>
          </td>
        </tr>
      </tbody>
    </table>
    <div class="afl-src foot">{{ t('点击表头排序 · 数值来自', 'Click a header to sort · From') }} <code>native_guns/*.json</code> · {{ data.version.mod }} · {{ data.version.commit }}</div>
  </div>
</template>

<style scoped>
.wrap { overflow-x: auto; padding: 6px 0 0; }
table { display: table; width: 100%; min-width: 760px; margin: 0; border-collapse: collapse; }
th { cursor: pointer; user-select: none; white-space: nowrap; background: transparent !important; border: 0 !important; border-bottom: 1px solid var(--afl-line) !important; padding: 10px 12px !important; font-size: 12px !important; }
th.on { color: var(--afl-amber2) !important; }
td { border: 0 !important; border-bottom: 1px solid rgba(255,255,255,.04) !important; padding: 9px 12px !important; font-size: 13.5px; white-space: nowrap; }
td.n { font-family: var(--vp-font-family-mono); }
td a { color: var(--afl-text); font-weight: 500; }
.bar { display: inline-block; height: 6px; border-radius: 3px; background: var(--afl-amber); vertical-align: middle; margin-right: 8px; }
.foot { padding: 10px 12px 12px; }
</style>
