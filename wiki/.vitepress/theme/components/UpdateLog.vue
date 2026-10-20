<script setup lang="ts">
// The main line's recent commits (first parent of the deployed commit), newest first, read from git at build time.
import { data } from '../../data/updates.data.mjs'
import { useLoc } from '../i18n'
const { t } = useLoc()
const KIND = {
  wiki: ['资料', 'Wiki'],
  data: ['数值', 'Data'],
  code: ['代码', 'Code'],
  docs: ['文档', 'Docs']
} as const
</script>

<template>
  <div class="afl-card ul">
    <div class="h">
      <b>{{ t('主线提交', 'Main-line commits') }}</b>
      <span class="afl-src">{{ t('部署时从 git 读取，最近 80 条', 'Read from git when deployed, latest 80') }}</span>
    </div>
    <ol>
      <li v-for="c in data" :key="c.hash" :class="{ merge: c.merge }">
        <div class="meta">
          <code class="afl-mono">{{ c.hash }}</code>
          <span class="date">{{ c.date }}</span>
          <span v-for="k in c.kinds" :key="k" class="kind">{{ t(KIND[k][0], KIND[k][1]) }}</span>
        </div>
        <div class="subj">{{ c.subject }}</div>
      </li>
    </ol>
  </div>
</template>

<style scoped>
.ul { padding: 20px 22px; }
.h { display: flex; justify-content: space-between; gap: 10px; flex-wrap: wrap; margin-bottom: 10px; }
ol { list-style: none; padding: 0; margin: 0; }
li { border-top: 1px solid var(--afl-line); padding: 10px 0; }
li.merge .subj { color: var(--afl-text); font-weight: 500; }
.meta { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; font-size: 12px; color: var(--afl-dim); }
.meta code { color: var(--afl-blue); background: none; padding: 0; }
.kind { padding: 0 8px; border-radius: 999px; background: rgba(255,255,255,.06); }
.subj { font-size: 13.5px; line-height: 1.6; color: var(--afl-dim); margin-top: 4px; overflow-wrap: anywhere; }
</style>
