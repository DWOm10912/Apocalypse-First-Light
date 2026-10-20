<script setup lang="ts">
// The 16 Federation towns: English name (as on the signs), Chinese name for docs, state, rank, site.
import { data } from '../../data/world.data.mjs'
import { useLoc } from '../i18n'
const { loc, t } = useLoc()
</script>

<template>
  <div class="scroll">
    <table>
      <thead>
        <tr><th>{{ t('名字', 'Name') }}</th><th v-if="loc === 'zh'">中文对照</th><th>{{ t('州', 'State') }}</th><th>{{ t('规模', 'Size') }}</th><th>{{ t('规划点', 'Planned site') }}</th></tr>
      </thead>
      <tbody>
        <tr v-for="c in data.towns" :key="c.id">
          <td>{{ c.name }}</td>
          <td v-if="loc === 'zh'" class="sub">{{ c.zh }}</td>
          <td>{{ c.state }}</td>
          <td>{{ c.rankName[loc] }}</td>
          <td class="n">x {{ c.x }} · z {{ c.z }}</td>
        </tr>
      </tbody>
    </table>
  </div>
  <div class="afl-src">{{ t('来源', 'Source') }}: <code>RoadNames.TOWNS</code> · <code>FederalTemplate</code><template v-if="loc === 'zh'"> · 中文名 <code>docs/worldgen/federal_towns_v1.md</code></template></div>
</template>

<style scoped>
.scroll { overflow-x: auto; }
.sub { color: var(--afl-dim); }
.n { font-family: var(--vp-font-family-mono); color: var(--afl-amber2); white-space: nowrap; }
</style>
