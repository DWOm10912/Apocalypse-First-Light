<script setup lang="ts">
// How far a feature is, as the Main workspace knows it. Four steps, never skipped:
//   verified   - in master and the user checked it in game (d = date of that check)
//   integrated - in master, not checked in game yet
//   test       - only in test worlds / dev commands, or still on a branch
//   planned    - designed or planned, not in the game
// "creative" is not a step: it says the thing can only be had in creative for now.
// check-wiki.mjs rejects any other value.
import { useLoc } from '../i18n'
const props = defineProps<{ s: 'verified' | 'integrated' | 'test' | 'planned' | 'creative'; d?: string }>()
const { t } = useLoc()
const map = {
  verified: ['green', '已实机验收', 'Checked in game'],
  integrated: ['blue', '已集成，未实机验收', 'Integrated, not checked in game'],
  test: ['amber', '测试中，普通世界还没有', 'Testing, not in normal worlds yet'],
  planned: ['dim', '规划中，还没有', 'Planned, not in yet'],
  creative: ['amber', '只能在创造模式拿到', 'Creative only for now']
} as const
const [cls, zh, en] = map[props.s]
</script>

<template>
  <span :class="['st', cls]">{{ t(zh, en) }}<template v-if="d"> · {{ d }}</template></span>
</template>

<style scoped>
.st { display: inline-block; font-size: 12px; font-weight: 500; padding: 2px 10px; border-radius: 999px; margin: 0 4px; vertical-align: 2px; line-height: 1.6; }
.green { color: var(--afl-green); background: rgba(127,207,139,.12); }
.blue { color: var(--afl-blue); background: rgba(127,182,234,.12); }
.amber { color: var(--afl-amber2); background: rgba(240,165,58,.12); }
.dim { color: var(--afl-dim); background: rgba(255,255,255,.06); }
</style>
