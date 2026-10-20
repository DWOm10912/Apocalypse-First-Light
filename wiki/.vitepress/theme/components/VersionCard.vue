<script setup lang="ts">
import { data } from '../../data/game.data.mjs'
import { useLoc } from '../i18n'
const { t, link } = useLoc()
const v = data.version
</script>

<template>
  <div class="afl-card vc">
    <div class="top">
      <span class="lbl">{{ t('当前数据对应的游戏版本', 'Game version these numbers match') }}</span>
      <span class="afl-pill amber">{{ v.mod }}</span>
    </div>
    <div class="ver afl-mono">{{ v.mod }}</div>
    <dl>
      <dt>Minecraft</dt><dd>{{ v.minecraft }}</dd>
      <dt>Forge</dt><dd>{{ v.forge }}</dd>
      <dt>{{ t('数据提交', 'Data commit') }}</dt><dd class="afl-mono">{{ v.commit }} · {{ v.commitDate }}</dd>
      <dt>{{ t('分支', 'Branch') }}</dt><dd class="afl-mono">{{ v.branch }}</dd>
      <dt>{{ t('同步时间', 'Synced') }}</dt><dd>{{ v.synced }}</dd>
    </dl>
    <div v-if="v.dirtyWiki" class="warn">● {{ t(`Wiki 有 ${v.dirtyWiki} 个文件的改动还没提交：页面比提交 ${v.commit} 新，游戏数值仍是这个提交的`, `${v.dirtyWiki} wiki files have uncommitted edits: the pages are newer than commit ${v.commit}; the game numbers are that commit's`) }}</div>
    <div v-if="v.dirtyData" class="warn">● {{ t(`有 ${v.dirtyData} 个数据文件还没提交：本地预览显示的是未提交的值`, `${v.dirtyData} data files are uncommitted: this local preview shows unreleased values`) }}</div>
    <div v-else class="ok">● {{ t('页面数值与这个提交的游戏数据一致', 'Every number matches the game data in this commit') }}</div>
    <a class="more" :href="link('/about/')">{{ t('数值是怎么来的 →', 'Where the numbers come from →') }}</a>
  </div>
</template>

<style scoped>
.vc { padding: 22px 24px; }
.top { display: flex; justify-content: space-between; align-items: center; gap: 10px; }
.lbl { font-size: 13px; color: var(--afl-dim); }
.ver { font-size: 34px; font-weight: 700; color: var(--afl-amber2); margin: 10px 0 12px; }
dl { display: grid; grid-template-columns: auto 1fr; gap: 6px 16px; font-size: 13.5px; }
dt { color: var(--afl-dim); }
dd { margin: 0; color: var(--afl-text); }
.ok, .warn { margin-top: 14px; font-size: 12.5px; color: var(--afl-green); }
.warn { color: var(--afl-amber2); }
.more { display: inline-block; margin-top: 10px; font-size: 13px; color: var(--afl-amber2); }
</style>
