<script setup lang="ts">
// The HUD's status badges (client/StatusBadgeHud.java), names and effect lines from the lang file; each row starts with
// the badge itself, drawn offline with the HUD's code (gradlew wikiStatusIcons -> public/img/status/<id>_<stage>.png).
import { withBase } from 'vitepress'
import { data } from '../../data/survivor.data.mjs'
import { useLoc } from '../i18n'
const props = defineProps<{ kind: 'good' | 'bad' }>()
const { loc, t } = useLoc()
const rows = data.statuses.filter((s: any) => s.kind === props.kind)
</script>

<template>
  <table>
    <thead>
      <tr><th>{{ t('状态', 'Status') }}</th><th>{{ t('效果', 'Effect') }}</th></tr>
    </thead>
    <tbody>
      <template v-for="s in rows" :key="s.id">
        <tr v-for="r in s.rows" :key="s.id + r.stage">
          <td :class="['nm', { danger: r.danger }]"><img class="badge" :src="withBase(`/img/status/${s.id}_${r.stage}.png`)" alt="" width="106" height="106" loading="lazy" />{{ r.name[loc] }}<span v-if="s.timed" class="sub"> · {{ t('有时限', 'timed') }}</span></td>
          <td>{{ r.effect[loc] }}</td>
        </tr>
      </template>
    </tbody>
  </table>
  <div class="afl-src">{{ t('来源', 'Source') }}: <code>StatusBadgeHud.java</code> · {{ t('语言文件', 'lang files') }}<template v-if="kind === 'bad'"> · {{ t('红色是危险档', 'red = dangerous stage') }}</template></div>
</template>

<style scoped>
.nm { min-width: 6em; white-space: nowrap; }
.badge { display: inline-block; width: 1.9em; height: 1.9em; margin: -0.3em 0.55em -0.3em 0; vertical-align: middle; }
.sub { font-size: 12px; color: var(--afl-dim); }
.danger { color: #e0796f; }
</style>
