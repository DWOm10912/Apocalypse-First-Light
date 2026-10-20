<script setup lang="ts">
// Gun growth levels: reload tier, aiming sway and recoil recovery per level (SurvivorGrowth.java formulas + perks).
import { data } from '../../data/survivor.data.mjs'
import { useLoc, fmt } from '../i18n'
const { loc, t } = useLoc()
const perks = (data.growth.find((g: any) => g.group === 'firearms')?.perks ?? []) as any[]
const tierAt = (lv: number) => [...perks].reverse().find((p) => lv >= p.level)
</script>

<template>
  <div class="scroll">
    <table>
      <thead><tr><th>Lv</th><th>{{ t('换弹档', 'Reload tier') }}</th><th>{{ t('瞄准晃动', 'Aiming sway') }}</th><th>{{ t('后坐力回复', 'Recoil recovery') }}</th></tr></thead>
      <tbody>
        <tr v-for="(sway, lv) in data.gunLevels.adsSway" :key="lv">
          <td class="n">{{ lv }}</td>
          <td>{{ tierAt(lv)?.name[loc] ?? '—' }}</td>
          <td class="n">×{{ fmt(sway, 2) }}</td>
          <td class="n">×{{ fmt(data.gunLevels.recoilRecovery[lv], 2) }}</td>
        </tr>
      </tbody>
    </table>
  </div>
  <div class="afl-src">{{ t('来源', 'Source') }}: <code>SurvivorGrowth.adsSway / recoilRecovery</code> · <code>survivor_growth/firearms_*.json</code> perks</div>
</template>

<style scoped>
.scroll { overflow-x: auto; }
.n { font-family: var(--vp-font-family-mono); color: var(--afl-amber2); white-space: nowrap; }
</style>
