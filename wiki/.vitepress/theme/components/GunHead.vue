<script setup lang="ts">
import { computed } from 'vue'
import { data } from '../../data/game.data.mjs'
import { useLoc, CLASS_NAMES, MODE_NAMES, hasImage, img, fmt } from '../i18n'

const props = defineProps<{ id: string }>()
const { loc, t } = useLoc()
const g = computed(() => data.guns.find((x: any) => x.id === props.id))
const dmg = computed(() => (g.value.pellets > 1 ? `${fmt(g.value.damage.base)}×${g.value.pellets}` : fmt(g.value.damage.base)))
</script>

<template>
  <section v-if="g" class="head">
    <div class="img">
      <img v-if="hasImage('gun_' + g.id)" :src="img('gun_' + g.id)" :alt="g.name[loc]" />
      <div v-else class="noimg">{{ t('图片待补', 'Image coming') }}</div>
    </div>
    <div class="tx">
      <div class="cls">
        <span class="afl-pill amber">{{ CLASS_NAMES[g.weaponClass]?.[loc] ?? g.weaponClass }}</span>
        <span class="afl-pill">{{ g.ammo.name[loc] }}</span>
        <span class="afl-pill">{{ g.modes.map((m: any) => MODE_NAMES[m.mode]?.[loc] ?? m.mode).join(' · ') }}</span>
      </div>
      <div class="name">{{ g.name[loc] }}</div>
      <div class="en">{{ loc === 'zh' ? g.name.en : g.name.zh }}</div>
      <div class="kpi">
        <div><small>{{ t('伤害', 'Damage') }}</small><b>{{ dmg }}</b></div>
        <div><small>{{ t('弹匣', 'Magazine') }}</small><b>{{ g.capacity }}<em>{{ t('发', 'rds') }}</em></b></div>
        <div><small>{{ t('最大射程', 'Max range') }}</small><b>{{ g.damage.maxRange }}<em>{{ t('格', 'm') }}</em></b></div>
        <div><small>{{ t('战术换弹', 'Reload') }}</small><b>{{ fmt(g.reload.tactical) }}<em>{{ t('秒', 's') }}</em></b></div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.head { margin: 8px 0 28px; display: grid; grid-template-columns: 1fr 1fr; border-radius: 18px; overflow: hidden; border: 1px solid var(--afl-line); background: var(--afl-card); }
.img { background: #070809; position: relative; min-height: 280px; }
.img img { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: cover; }
.noimg { position: absolute; inset: 0; display: flex; align-items: center; justify-content: center; color: #4c535d; font-size: 13px; letter-spacing: .1em; background: radial-gradient(circle at 60% 40%, #22262d, #0b0d10 70%); }
.tx { padding: 24px 26px; }
.cls { display: flex; gap: 6px; flex-wrap: wrap; }
.name { font-size: 30px; font-weight: 900; margin: 12px 0 2px; line-height: 1.2; color: var(--afl-text); }
.en { color: var(--afl-dim); font-size: 14px; }
.kpi { display: grid; grid-template-columns: repeat(4, 1fr); gap: 10px; margin-top: 20px; }
.kpi div { background: var(--afl-card2); border: 1px solid var(--afl-line); border-radius: 12px; padding: 10px 12px; }
.kpi small { font-size: 11.5px; color: var(--afl-dim); display: block; }
.kpi b { white-space: nowrap; font-family: var(--vp-font-family-mono); font-size: 21px; color: var(--afl-text); }
.kpi em { font-style: normal; font-size: 12px; color: var(--afl-dim); margin-left: 3px; font-family: var(--vp-font-family-base); font-weight: 400; }
@media (max-width: 760px) {
  .head { grid-template-columns: 1fr; }
  .img { min-height: 200px; }
  .kpi { grid-template-columns: repeat(2, 1fr); }
}
</style>
