<script setup lang="ts">
// Every ammo type the guns use, from native_guns, with the per-round mass and the guns that fire it.
import { computed } from 'vue'
import { data } from '../../data/game.data.mjs'
import { useLoc, fmt } from '../i18n'
const { loc, t, link } = useLoc()
const rows = computed(() => {
  const m = new Map<string, any>()
  for (const g of data.guns as any[]) {
    const a = g.ammo
    if (!m.has(a.id)) m.set(a.id, { ...a, guns: [] })
    m.get(a.id).guns.push(g)
  }
  return [...m.values()]
})
</script>

<template>
  <table>
    <thead>
      <tr><th>{{ t('弹药', 'Ammo') }}</th><th>{{ t('每发重量', 'Per round') }}</th><th>{{ t('用这种弹的枪', 'Used by') }}</th></tr>
    </thead>
    <tbody>
      <tr v-for="a in rows" :key="a.id">
        <td>{{ a.name[loc] }}<div class="sub">{{ a.id }}</div></td>
        <td class="n">{{ a.massKg == null ? '—' : `${fmt(a.massKg * 1000)} g` }}</td>
        <td><a v-for="(g, i) in a.guns" :key="g.id" :href="link(`/arms/guns/${g.id}`)">{{ g.name[loc] }}<span v-if="i < a.guns.length - 1">、</span></a></td>
      </tr>
    </tbody>
  </table>
  <div class="afl-src">{{ t('来源', 'Source') }}: native_guns/*.json · item_mass</div>
</template>

<style scoped>
.sub { font-size: 12px; color: var(--afl-dim); font-family: var(--vp-font-family-mono); }
.n { font-family: var(--vp-font-family-mono); color: var(--afl-amber2); white-space: nowrap; }
</style>
