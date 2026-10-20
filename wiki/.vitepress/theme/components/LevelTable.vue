<script setup lang="ts">
// One body stat or growth skill (survivor_body / survivor_growth): what each level changes, how to train it,
// the experience to each next level, and the nodes it unlocks.
import { computed } from 'vue'
import { data } from '../../data/survivor.data.mjs'
import { useLoc, fmt } from '../i18n'
import { effectValue } from './survivor-fmt'
const props = defineProps<{ id: string }>()
const { loc, t } = useLoc()
const s = computed<any>(() => [...data.body, ...data.growth].find((x: any) => x.id === props.id))
const isBody = computed(() => data.body.some((x: any) => x.id === props.id))
const lv = computed(() => Array.from({ length: s.value.max }, (_, i) => i + (isBody.value ? 1 : 1)))
</script>

<template>
  <div v-if="s" class="afl-card lt">
    <div class="h">
      <b>{{ s.name[loc] }}</b>
      <span class="afl-src">
        <template v-if="isBody">{{ t('开局', 'Start') }} {{ s.startMin }}–{{ s.startMax }} · {{ t('默认', 'default') }} {{ s.default }} · </template>
        <template v-else>{{ t('开局', 'Start') }} Lv {{ s.default }} · </template>
        {{ t('最高', 'max') }} {{ s.max }}
      </span>
    </div>
    <p v-if="s.how" class="how">{{ t('怎么练：', 'How to train: ') }}{{ s.how[loc] }}</p>

    <div v-if="s.levels.length" class="scroll">
      <table>
        <thead><tr><th>Lv</th><th v-for="l in s.levels" :key="l.attr">{{ l.name[loc] }}</th></tr></thead>
        <tbody>
          <tr v-for="n in lv" :key="n">
            <td class="n">{{ n }}</td>
            <td v-for="l in s.levels" :key="l.attr" class="n">{{ l.values[n - 1] == null ? '—' : effectValue(l.op, l.values[n - 1], l.unit) }}</td>
          </tr>
        </tbody>
      </table>
    </div>

    <div class="cols">
      <div>
        <div class="sub">{{ t('经验来源', 'Experience') }}</div>
        <div v-for="x in s.training.xp" :key="x.id">{{ x.label[loc] }} <span class="n">{{ fmt(x.xp, 2) }}</span></div>
      </div>
      <div>
        <div class="sub">{{ t('升到下一级要的经验', 'Experience to next level') }}</div>
        <div class="steps"><span v-for="st in s.training.toNext" :key="st.level">Lv {{ st.level }}→{{ st.level + 1 }} <b class="n">{{ st.xp }}</b></span></div>
        <div class="sub">{{ t('练满一共', 'Total to max') }} <b class="n">{{ s.training.total }}</b></div>
      </div>
      <div v-if="s.perks?.length">
        <div class="sub">{{ t('节点', 'Nodes') }}</div>
        <div v-for="p in s.perks" :key="p.id">Lv {{ p.level }} · {{ p.name[loc] }}<div v-if="p.desc" class="sub">{{ p.desc[loc] }}</div></div>
      </div>
    </div>
    <div class="afl-src">{{ t('来源', 'Source') }}: <code>{{ isBody ? 'survivor_body' : 'survivor_growth' }}/{{ s.id }}.json</code></div>
  </div>
</template>

<style scoped>
.lt { padding: 18px 20px; margin: 16px 0; }
.h { display: flex; justify-content: space-between; gap: 10px; flex-wrap: wrap; align-items: baseline; }
.how { margin: 8px 0 4px; font-size: 14px; }
.scroll { overflow-x: auto; }
.cols { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 14px; margin: 12px 0 8px; font-size: 13.5px; }
.steps { display: flex; flex-wrap: wrap; gap: 4px 12px; font-size: 12.5px; color: var(--afl-dim); }
.sub { font-size: 12px; color: var(--afl-dim); margin-bottom: 4px; }
.n { font-family: var(--vp-font-family-mono); color: var(--afl-amber2); white-space: nowrap; }
</style>
