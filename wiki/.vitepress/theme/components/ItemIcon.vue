<script setup lang="ts">
// Small 3D picture of an item, rendered from the game model by scripts/icons.mjs (bundled, not on R2).
import { withBase } from 'vitepress'
defineProps<{ id: string }>()
</script>

<template>
  <!-- a page may name an item Main has not rendered yet (branch work): hide the broken image -->
  <img class="item-icon" :src="withBase(`/img/icons/${id}.webp`)" alt="" loading="lazy" width="128" height="128" onerror="this.style.display='none'" />
</template>

<style scoped>
.item-icon {
  display: inline-block;
  width: 2.4em;
  height: 2.4em;
  margin: -0.35em 0.45em -0.35em 0;
  vertical-align: middle;
  border-radius: 8px;
  background: radial-gradient(circle at 50% 45%, rgba(255, 255, 255, 0.13), rgba(255, 255, 255, 0.03) 70%);
}
</style>
