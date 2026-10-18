<script setup lang="ts">
// A number read from the game: <V d="stamina/stamina_v1#costs.sprint" u="/s" />, <V j="Class.CONST" x="0.25" u="kW" />
// x multiplies, add adds after multiplying, digits rounds, u / ue are the zh / en unit (ue defaults to u).
import { computed } from 'vue'
import { data } from '../../data/values.data.mjs'
import { useLoc } from '../i18n'

const props = defineProps<{ d?: string; j?: string; x?: string | number; add?: string | number; digits?: string | number; u?: string; ue?: string; pct?: boolean }>()
const { loc } = useLoc()
const key = computed(() => (props.d ? `d:${props.d}` : `j:${props.j}`))
const text = computed(() => {
  let v: any = data.values[key.value]
  if (v === undefined) return '??'
  if (typeof v === 'number') {
    if (props.x != null) v *= +props.x
    if (props.add != null) v += +props.add
    if (props.pct) v *= 100
    const d = props.digits != null ? +props.digits : 3
    v = +v.toFixed(d)
    v = v.toLocaleString('en-US', { maximumFractionDigits: d })
  }
  const unit = loc.value === 'en' && props.ue != null ? props.ue : props.u
  return `${v}${props.pct ? '%' : ''}${unit ? (/^[%°/×]/.test(unit) ? '' : ' ') + unit : ''}`
})
</script>

<template><span class="v" :title="d ?? j">{{ text }}</span></template>

<style scoped>
.v { font-family: var(--vp-font-family-mono); font-size: .92em; color: var(--afl-amber2); white-space: nowrap; }
</style>
