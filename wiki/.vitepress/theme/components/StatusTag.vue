<script setup lang="ts">
// Status of a feature in the current build: what a player can rely on.
import { useLoc } from '../i18n'
const props = defineProps<{ s: 'live' | 'new' | 'creative' | 'test' | 'planned' }>()
const { t } = useLoc()
const map = {
  live: ['green', '已实装', 'In game'],
  new: ['blue', '新加入，可能还要调', 'New, may change'],
  creative: ['amber', '只能在创造模式拿到', 'Creative only for now'],
  test: ['amber', '测试中，普通世界还没有', 'Testing, not in normal worlds yet'],
  planned: ['dim', '计划中，还没有', 'Planned, not in yet']
} as const
const [cls, zh, en] = map[props.s]
</script>

<template>
  <span :class="['st', cls]">{{ t(zh, en) }}</span>
</template>

<style scoped>
.st { display: inline-block; font-size: 12px; font-weight: 500; padding: 2px 10px; border-radius: 999px; margin: 0 4px; vertical-align: 2px; line-height: 1.6; }
.green { color: var(--afl-green); background: rgba(127,207,139,.12); }
.blue { color: var(--afl-blue); background: rgba(127,182,234,.12); }
.amber { color: var(--afl-amber2); background: rgba(240,165,58,.12); }
.dim { color: var(--afl-dim); background: rgba(255,255,255,.06); }
</style>
