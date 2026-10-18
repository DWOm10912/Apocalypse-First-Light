<script setup lang="ts">
// Value changes across all guns, newest first, read from git history of the data files.
import { computed } from 'vue'
import { data } from '../../data/game.data.mjs'
import { useLoc, fmt } from '../i18n'

const props = defineProps<{ limit?: number; gun?: string }>()
const { loc, t, link } = useLoc()

const rows = computed(() => {
  const out: any[] = []
  for (const g of data.guns) {
    if (props.gun && g.id !== props.gun) continue
    for (const h of g.history) {
      if (!h.added && !h.changes.length) continue
      out.push({ gun: g, ...h })
    }
  }
  out.sort((a, b) => (a.date < b.date ? 1 : a.date > b.date ? -1 : 0))
  return props.limit ? out.slice(0, props.limit) : out
})
const val = (v: any, u: any) => (v == null ? '—' : `${typeof v === 'number' ? fmt(v, 3) : v}${u[loc.value] ? ' ' + u[loc.value] : ''}`)
</script>

<template>
  <div class="afl-card rc">
    <div class="h">
      <b>{{ gun ? t('数值历史', 'Value history') : t('最近数值变更', 'Recent value changes') }}</b>
      <span class="afl-src">{{ t('来自游戏数据文件的 git 历史', 'From the git history of the game data') }}</span>
    </div>
    <div v-if="!rows.length" class="empty">{{ t('还没有记录', 'Nothing recorded yet') }}</div>
    <div class="tl">
      <div v-for="r in rows" :key="r.gun.id + r.hash" class="it">
        <div class="d afl-mono">{{ r.date }} · {{ r.version }} · {{ r.hash }}</div>
        <b v-if="!gun"><a :href="link('/arms/guns/' + r.gun.id)">{{ r.gun.name[loc] }}</a></b>
        <div class="tg">
          <span v-if="r.added" class="a">{{ t('加入游戏', 'Added') }}</span>
          <span v-for="c in r.changes" :key="c.key" class="n">
            {{ c.label[loc] }} {{ val(c.from, c.label.unit) }} → <em>{{ val(c.to, c.label.unit) }}</em>
          </span>
          <span v-if="r.other" class="o">{{ t(`另有 ${r.other} 项手感参数调整`, `+${r.other} handling tweaks`) }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.rc { padding: 22px 24px; }
.h { display: flex; justify-content: space-between; align-items: baseline; gap: 10px; flex-wrap: wrap; margin-bottom: 14px; }
.h b { font-size: 16px; }
.empty { color: var(--afl-dim); font-size: 13px; }
.tl { position: relative; padding-left: 22px; }
.tl::before { content: ''; position: absolute; left: 5px; top: 6px; bottom: 6px; width: 2px; background: #252a31; }
.it { position: relative; padding-bottom: 14px; }
.it::before { content: ''; position: absolute; left: -22px; top: 5px; width: 12px; height: 12px; border-radius: 50%; background: var(--afl-bg); border: 2px solid var(--afl-amber); }
.d { font-size: 11.5px; color: var(--afl-dim); }
.it b { display: block; font-size: 14.5px; margin: 2px 0 6px; }
.it b a { color: var(--afl-text); }
.tg { display: flex; gap: 6px; flex-wrap: wrap; }
.tg span { font-size: 12px; padding: 3px 9px; border-radius: 999px; background: rgba(255,255,255,.05); color: #d6d0c5; }
.tg .n { background: rgba(127,182,234,.12); color: var(--afl-blue); }
.tg .n em { font-style: normal; color: var(--afl-text); font-weight: 700; }
.tg .a { background: rgba(127,207,139,.12); color: var(--afl-green); }
.tg .o { color: var(--afl-dim); }
</style>
