<script setup lang="ts">
// Every attachment some gun accepts, from native_guns slots + native_attachments, with its effect and the guns it fits.
import { computed } from 'vue'
import { data } from '../../data/game.data.mjs'
import { useLoc, SLOT_NAMES, fmt } from '../i18n'
const { loc, t, link } = useLoc()
const rows = computed(() => {
  const m = new Map<string, any>()
  for (const g of data.guns as any[]) {
    for (const a of g.attachments) {
      if (!m.has(a.id)) m.set(a.id, { ...a, guns: [] })
      m.get(a.id).guns.push(g)
    }
  }
  const order = ['sight', 'muzzle', 'magazine', 'sling']
  return [...m.values()].sort((a, b) => order.indexOf(a.slot) - order.indexOf(b.slot))
})
function effect(a: any) {
  const out: string[] = []
  if (a.noiseMultiplier != null) out.push(t(`枪声范围 ×${fmt(a.noiseMultiplier)}`, `Noise radius ×${fmt(a.noiseMultiplier)}`))
  if (a.rounds != null) out.push(t(`${a.rounds} 发`, `${a.rounds} rounds`))
  if (a.emptyReload != null) out.push(t(`空仓换弹 ${fmt(a.emptyReload)} 秒`, `Empty reload ${fmt(a.emptyReload)} s`))
  if (a.slot === 'sight') out.push(t('红点瞄准', 'Red dot sight'))
  if (a.slot === 'sling') out.push(t('长枪能背在背上，放进 1 号栏', 'The long gun can be carried on the back, in slot 1'))
  return out.join(' · ')
}
</script>

<template>
  <table>
    <thead>
      <tr><th>{{ t('配件', 'Attachment') }}</th><th>{{ t('位置', 'Slot') }}</th><th>{{ t('效果', 'Effect') }}</th><th>{{ t('适用', 'Fits') }}</th></tr>
    </thead>
    <tbody>
      <tr v-for="a in rows" :key="a.id">
        <td>{{ a.name[loc] }}</td>
        <td>{{ SLOT_NAMES[a.slot]?.[loc] ?? a.slot }}</td>
        <td>{{ effect(a) }}</td>
        <td><a v-for="(g, i) in a.guns" :key="g.id" :href="link(`/arms/guns/${g.id}`)">{{ g.name[loc] }}<span v-if="i < a.guns.length - 1">、</span></a></td>
      </tr>
    </tbody>
  </table>
  <div class="afl-src">{{ t('来源', 'Source') }}: native_guns/*.json · native_attachments/*.json</div>
</template>
