<script setup lang="ts">
// Large 3D render of an item or block (images.json key model_<id>, made by scripts/icons.mjs from the game model).
import { useLoc, hasImage, img } from '../i18n'
const props = defineProps<{ id: string; zh?: string; en?: string }>()
const { t } = useLoc()
const k = 'model_' + props.id
</script>

<template>
  <figure class="model-shot">
    <div class="stage">
      <img v-if="hasImage(k)" :src="img(k)" :alt="t(zh ?? '', en ?? '')" loading="lazy" />
    </div>
    <figcaption v-if="zh || en">{{ t(zh ?? '', en ?? '') }}<span class="src">{{ t('游戏模型渲染', 'Game model render') }}</span></figcaption>
  </figure>
</template>

<style scoped>
.model-shot { margin: 18px 0; }
.stage {
  border-radius: 14px;
  border: 1px solid var(--afl-line);
  background: radial-gradient(ellipse at 50% 42%, #2a2f37, #14171c 62%, #0e1014);
  display: flex;
  justify-content: center;
  padding: 8px;
}
.stage img { width: 100%; max-width: 520px; aspect-ratio: 4 / 3; object-fit: contain; display: block; }
figcaption { font-size: 12.5px; color: var(--afl-dim); margin-top: 6px; }
.src { margin-left: 10px; opacity: 0.7; }
</style>
