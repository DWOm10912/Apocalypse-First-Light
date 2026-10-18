<script setup lang="ts">
// Every AFL biome from worldgen/biome, warmest first, with the temperature system's air and water values.
import { data } from '../../data/game.data.mjs'
import { useLoc } from '../i18n'
const { t } = useLoc()
const deg = (v: number | null) => (v == null ? '—' : `${v} °C`)
</script>

<template>
  <table class="biomes">
    <thead>
      <tr>
        <th>{{ t('生物群系', 'Biome') }}</th>
        <th>{{ t('平均气温', 'Mean air') }}</th>
        <th>{{ t('昼夜温差 ±', 'Day swing ±') }}</th>
        <th>{{ t('水温', 'Water') }}</th>
      </tr>
    </thead>
    <tbody>
      <tr v-for="b in data.biomes" :key="b.id">
        <td>{{ t(b.zh, b.en) }}<div class="sub">{{ t(b.en, b.zh) }}</div></td>
        <td class="n">{{ deg(b.mean) }}</td>
        <td class="n">{{ b.amp == null ? '—' : `${b.amp} °C` }}</td>
        <td class="n">{{ deg(b.water) }}</td>
      </tr>
    </tbody>
  </table>
  <div class="afl-src">{{ t('来源', 'Source') }}: worldgen/biome · temperature/temperature_v1.json</div>
</template>

<style scoped>
.sub { font-size: 12px; color: var(--afl-dim); }
.n { font-family: var(--vp-font-family-mono); color: var(--afl-amber2); white-space: nowrap; }
</style>
