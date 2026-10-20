<script setup lang="ts">
// Survivor backgrounds from survivor_backgrounds/*.json: fixed traits, point budget, successor pool.
import { data } from '../../data/survivor.data.mjs'
import { useLoc } from '../i18n'
const { loc, t } = useLoc()
</script>

<template>
  <table>
    <thead>
      <tr><th>{{ t('背景', 'Background') }}</th><th>{{ t('固定特质', 'Fixed traits') }}</th><th>{{ t('点数预算', 'Budget') }}</th><th>{{ t('继任者会抽到', 'Successors') }}</th></tr>
    </thead>
    <tbody>
      <tr v-for="b in data.backgrounds" :key="b.id">
        <td class="nm">{{ b.name[loc] }}<div v-if="b.desc" class="sub">{{ b.desc[loc] }}</div></td>
        <td>{{ b.traitNames.length ? b.traitNames.map((n: any) => n[loc]).join(t('、', ', ')) : '—' }}</td>
        <td class="n">{{ b.budget }}</td>
        <td>{{ b.successor ? t('会', 'Yes') : t('不会', 'No') }}</td>
      </tr>
    </tbody>
  </table>
  <div class="afl-src">{{ t('来源', 'Source') }}: <code>survivor_backgrounds/*.json</code></div>
</template>

<style scoped>
.nm { min-width: 7em; }
.sub { font-size: 12px; color: var(--afl-dim); }
.n { font-family: var(--vp-font-family-mono); color: var(--afl-amber2); }
</style>
