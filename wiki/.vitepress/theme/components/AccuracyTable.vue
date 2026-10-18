<script setup lang="ts">
// Stance multipliers from NativeAccuracyProfile.java, with the guns that use each profile.
import { data } from '../../data/game.data.mjs'
import { useLoc } from '../i18n'
const { loc, t } = useLoc()
const profiles = Object.entries(data.accuracyProfiles) as [string, any][]
const users = (p: string) => data.guns.filter((g: any) => g.accuracyProfile === p).map((g: any) => g.name[loc.value]).join('、')
const rows = [
  ['crouchStill', '蹲着不动', 'Crouched, still'],
  ['standing', '站着不动', 'Standing still'],
  ['crouchMoving', '蹲着移动', 'Crouched, moving'],
  ['walking', '走路', 'Walking'],
  ['sprinting', '跑步', 'Running'],
  ['airborne', '空中', 'In the air']
]
</script>

<template>
  <table>
    <thead><tr><th>{{ t('姿势', 'Stance') }}</th><th v-for="[k] in profiles" :key="k">{{ users(k) }}</th></tr></thead>
    <tbody>
      <tr v-for="[key, zh, en] in rows" :key="key">
        <td>{{ t(zh, en) }}</td>
        <td v-for="[k, p] in profiles" :key="k" class="afl-mono">×{{ key === 'standing' ? 1 : p[key] }}</td>
      </tr>
    </tbody>
  </table>
</template>
