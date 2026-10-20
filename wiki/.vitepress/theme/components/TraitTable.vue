<script setup lang="ts">
// Every trait from survivor_traits/*.json: group, point cost, what it changes, what it excludes.
import { data } from '../../data/survivor.data.mjs'
import { useLoc } from '../i18n'
import { effectValue } from './survivor-fmt'
const { loc, t } = useLoc()
const cost = (c: number) => (c > 0 ? t(`花 ${c} 点`, `costs ${c}`) : c < 0 ? t(`返 ${-c} 点`, `gives ${-c}`) : '0')
</script>

<template>
  <div class="scroll">
  <table>
    <thead>
      <tr><th>{{ t('特质', 'Trait') }}</th><th>{{ t('分组', 'Group') }}</th><th>{{ t('点数', 'Points') }}</th><th>{{ t('效果', 'Effect') }}</th></tr>
    </thead>
    <tbody>
      <tr v-for="tr in data.traits" :key="tr.id">
        <td class="nm">
          {{ tr.name[loc] }}
          <div v-if="tr.backgroundOnly" class="sub">{{ t('只能由背景给', 'Background only') }}</div>
          <div v-if="tr.exclusiveNames.length" class="sub">{{ t('不能和', 'Not with') }} {{ tr.exclusiveNames.map((n: any) => n[loc]).join(t('、', ', ')) }}</div>
        </td>
        <td class="nw">{{ tr.groupName[loc] }}</td>
        <td :class="['n', tr.cost > 0 ? 'good' : tr.cost < 0 ? 'bad' : '']">{{ cost(tr.cost) }}</td>
        <td>
          <div v-for="e in tr.effects" :key="e.attr">{{ e.name[loc] }} <span class="n">{{ effectValue(e.op, e.value, e.unit) }}</span></div>
          <div v-for="b in tr.bodyBonus" :key="'b' + b.id">{{ b.name[loc] }} <span class="n">+{{ b.value }}</span></div>
          <div v-for="g in tr.growthStart" :key="'s' + g.id">{{ g.name[loc] }} {{ t('从', 'starts at') }} <span class="n">Lv {{ g.value }}</span>{{ t(' 开始', '') }}</div>
          <div v-for="g in tr.growthCap" :key="'c' + g.id">{{ g.name[loc] }} {{ t('最高', 'max') }} <span class="n">Lv {{ g.value }}</span></div>
        </td>
      </tr>
    </tbody>
  </table>
  </div>
  <div class="afl-src">{{ t('来源', 'Source') }}: <code>survivor_traits/*.json</code> · {{ t('点数从背景给的预算里扣，坏特质返点', 'Points come out of the background budget; drawbacks give points back') }}</div>
</template>

<style scoped>
.scroll { overflow-x: auto; }
.nm { min-width: 6.5em; }
.nw { white-space: nowrap; }
.sub { font-size: 12px; color: var(--afl-dim); }
.n { font-family: var(--vp-font-family-mono); color: var(--afl-amber2); white-space: nowrap; }
.n.good { color: var(--afl-amber2); }
.n.bad { color: var(--afl-blue); }
</style>
