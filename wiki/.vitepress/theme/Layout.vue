<script setup lang="ts">
import DefaultTheme from 'vitepress/theme'
import { data } from '../data/game.data.mjs'
import { useLoc } from './i18n'
const { Layout } = DefaultTheme
const { t, link } = useLoc()
const v = data.version
</script>

<template>
  <Layout>
    <template #nav-bar-content-after>
      <a class="verpill" :href="link('/about/')" :title="t('当前数值对应的游戏版本', 'Game version these numbers match')">{{ v.mod }}</a>
    </template>
    <template #layout-bottom>
      <footer class="aflfoot">
        <span>Apocalypse: First Light Wiki · staticgrove.net</span>
        <span>{{ t('数值来自游戏数据', 'Numbers from the game data') }} · {{ v.mod }} · <code>{{ v.branch }}@{{ v.commit }}</code><template v-if="v.dirtyWiki"> {{ t('+ 未提交的 Wiki 改动', '+ uncommitted wiki edits') }}</template> · {{ t('同步', 'synced') }} {{ v.synced }}</span>
      </footer>
    </template>
  </Layout>
</template>

<style>
.verpill { margin-left: 12px; font-size: 12px; font-weight: 700; font-family: var(--vp-font-family-mono); padding: 3px 10px; border-radius: 999px; background: rgba(240,165,58,.14); color: var(--afl-amber2) !important; white-space: nowrap; }
.aflfoot { border-top: 1px solid var(--afl-line); padding: 20px 32px; font-size: 12.5px; color: var(--afl-dim); display: flex; justify-content: space-between; gap: 12px; flex-wrap: wrap; position: relative; z-index: 5; background: var(--afl-bg); }
.aflfoot code { background: none; color: var(--afl-blue); padding: 0; }
@media (min-width: 960px) { .VPDoc ~ .aflfoot, .has-sidebar ~ .aflfoot { padding-left: calc(var(--vp-sidebar-width) + 32px); } }
@media (max-width: 767px) { .verpill { display: none; } }
</style>
