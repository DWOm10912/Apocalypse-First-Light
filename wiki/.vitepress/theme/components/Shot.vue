<script setup lang="ts">
// A wiki image from images.json, with a caption. Missing keys render a placeholder so the page still builds.
import { useLoc, hasImage, img } from '../i18n'
defineProps<{ k: string; zh?: string; en?: string }>()
const { t } = useLoc()
</script>

<template>
  <figure class="shot">
    <img v-if="hasImage(k)" :src="img(k)" :alt="t(zh ?? '', en ?? '')" loading="lazy" />
    <div v-else class="ph">{{ t('游戏截图待补', 'Screenshot coming') }}</div>
    <figcaption v-if="zh || en">{{ t(zh ?? '', en ?? '') }}</figcaption>
  </figure>
</template>

<style scoped>
.shot { margin: 18px 0; }
.shot img, .ph { width: 100%; border-radius: 14px; border: 1px solid var(--afl-line); display: block; }
.ph { aspect-ratio: 16 / 7; display: flex; align-items: center; justify-content: center; color: #4c535d; font-size: 13px; letter-spacing: .1em; background: radial-gradient(circle at 60% 40%, #22262d, #0b0d10 70%); }
figcaption { font-size: 12.5px; color: var(--afl-dim); margin-top: 6px; }
</style>
