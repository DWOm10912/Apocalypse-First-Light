<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { data } from '../../data/game.data.mjs'
import { useLoc, img } from '../i18n'
import VersionCard from './VersionCard.vue'
import RecentChanges from './RecentChanges.vue'

const { loc, t, link } = useLoc()

// Rotating hero: AFL is a whole survival mod, so the slides take turns across systems.
const slides = [
  { img: 'hero_forest', to: '/world/forests', k: 'FORESTS',
    title: { zh: '森林与荒野', en: 'Forests & Wilds' },
    text: { zh: '高大的网格树木组成真正的林地，树可以一斧一斧砍倒', en: 'Tall mesh trees make real woodland, and you can fell them axe stroke by axe stroke' } },
  { img: 'hero_vehicle', to: '/vehicles/t15', k: 'VEHICLES',
    title: { zh: 'T15 皮卡', en: 'T15 Pickup' },
    text: { zh: '能开的车：五档手动、四驱低速档、车灯和雨刷', en: 'A truck you can drive: five-speed manual, 4L, lights and wipers' } },
  { img: 'hero_building', to: '/industry/power', k: 'BUILDINGS',
    title: { zh: '建筑与供电', en: 'Buildings & Power' },
    text: { zh: '隐藏布线、配电盘、插座和灯：让一栋房子真正通上电', en: 'Hidden wiring, panels, outlets and lights: actually power a house' } },
  { img: 'hero_cold', to: '/guide/survival', k: 'SURVIVAL',
    title: { zh: '体温与天气', en: 'Cold & Weather' },
    text: { zh: '冷了屏幕结霜，热了眩光，雨水顺着车窗流下来', en: 'Frost creeps in when you are cold, glare when hot, and rain runs down the windshield' } },
  { img: 'hero_guns', to: '/arms/', k: 'ARMS',
    title: { zh: '枪械与配件', en: 'Arms & Attachments' },
    text: { zh: '从零建模的枪，弹药、配件和维护都按真实逻辑来', en: 'Guns modelled from scratch, with ammo, attachments and maintenance that follow real logic' } }
]
const cur = ref(0)
let timer: any
const go = (i: number) => { cur.value = (i + slides.length) % slides.length; restart() }
function restart() { clearInterval(timer); timer = setInterval(() => (cur.value = (cur.value + 1) % slides.length), 7000) }
onMounted(restart)
onBeforeUnmount(() => clearInterval(timer))

function openSearch() {
  window.dispatchEvent(new KeyboardEvent('keydown', { key: 'k', ctrlKey: true, metaKey: true }))
}

const tiles = computed(() => [
  { cls: 'w2 h2', img: 'world_map', k: 'WORLD', to: '/world/', title: t('世界与探索', 'World'), text: t('六州地图、城镇、企业、公路和隧道', 'Six states, towns, companies, highways and tunnels') },
  { cls: '', img: 'hero_vehicle', k: 'VEHICLES', to: '/vehicles/', title: t('载具', 'Vehicles'), text: t('T15、燃料、加油站', 'T15, fuel, stations') },
  { cls: '', img: 'hero_building', k: 'PLACES', to: '/world/places', title: t('建筑与地点', 'Places'), text: t('Linden、老宅、加油站', 'Linden, farmhouse, fuel stop') },
  { cls: '', img: 'hud', k: 'START', to: '/guide/', title: t('新手指南', 'Getting Started'), text: t('幸存者、成长、状态、按键', 'Survivor, growth, statuses, controls') },
  { cls: '', img: 'hero_guns', k: 'ARMS', to: '/arms/', title: t('枪械与装备', 'Arms & Gear'), text: t(`${data.guns.length} 把枪、弹药、配件`, `${data.guns.length} guns, ammo, attachments`) },
  { cls: '', img: 'hero_cold', k: 'INDUSTRY', to: '/industry/', title: t('工业制造', 'Industry'), text: t('发电机、供电、材料', 'Generators, power, materials') },
  { cls: '', img: '', k: 'INFECTED', to: '/infected/', title: t('感染体', 'Infected'), text: t('类型、行为、威胁', 'Types, behaviour, threat') },
  { cls: 'w2', img: 'maintenance_bench', k: 'CHANGELOG', to: '/changelog/', title: t('更新日志', 'Changelog'), text: t('版本更新与数值变更', 'Releases and value changes') }
])
</script>

<template>
  <div class="home">
    <section class="hero">
      <TransitionGroup name="fade" tag="div" class="hero-bg">
        <img v-for="(s, i) in slides" v-show="i === cur" :key="s.img" :src="img(s.img)" alt="" />
      </TransitionGroup>
      <div class="hero-in">
        <div class="eyebrow">APOCALYPSE: FIRST LIGHT · WIKI</div>
        <h1>{{ t('末日之后，', 'After the end,') }}<br />{{ t('从第一道光开始', 'start at first light') }}</h1>
        <p>{{ t('Minecraft 1.20.1 写实末日生存模组：森林和公路、车辆、建筑供电、体温与辐射、枪械与感染者。页面上的数值直接来自游戏数据', 'A grounded post-apocalyptic survival mod for Minecraft 1.20.1: forests and highways, vehicles, building power, temperature and radiation, guns and the infected. Numbers on this site come straight from the game data') }}</p>
        <button class="bigsearch" @click="openSearch">
          <span>🔍 {{ t('搜索物品、方块、机器…', 'Search items, blocks, machines…') }}</span><i>Ctrl K</i>
        </button>
        <div class="slide-card">
          <a :href="link(slides[cur].to)">
            <span class="k">{{ slides[cur].k }}</span>
            <b>{{ slides[cur].title[loc] }}</b>
            <small>{{ slides[cur].text[loc] }}</small>
          </a>
          <div class="dots">
            <button v-for="(s, i) in slides" :key="s.k" :class="{ on: i === cur }" :aria-label="s.title[loc]" @click="go(i)"></button>
          </div>
        </div>
      </div>
    </section>

    <div class="main">
      <div class="sh"><h2>{{ t('探索系统', 'Explore') }}</h2></div>
      <div class="bento">
        <a v-for="tile in tiles" :key="tile.k" :class="['tile', tile.cls]" :href="link(tile.to)">
          <img v-if="tile.img" :src="img(tile.img)" alt="" loading="lazy" />
          <div v-else class="ph"></div>
          <div class="tx"><div class="k">{{ tile.k }}</div><b>{{ tile.title }}</b><small>{{ tile.text }}</small></div>
        </a>
      </div>

      <div class="sh"><h2>{{ t('版本与数值', 'Version & Data') }}</h2><a :href="link('/changelog/')">{{ t('更新日志 →', 'Changelog →') }}</a></div>
      <div class="row">
        <VersionCard />
        <RecentChanges :limit="6" />
      </div>
    </div>
  </div>
</template>

<style scoped>
.home { margin-top: calc(var(--vp-nav-height) * -1); }
.hero { position: relative; height: 640px; overflow: hidden; }
.hero-bg img { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: cover; }
.hero::after { content: ''; position: absolute; inset: 0; background: linear-gradient(90deg, rgba(11,13,16,.95) 0%, rgba(11,13,16,.72) 40%, rgba(11,13,16,.08) 75%), linear-gradient(180deg, rgba(11,13,16,.5) 0%, transparent 25%, transparent 60%, var(--afl-bg) 100%); pointer-events: none; }
.fade-enter-active, .fade-leave-active { transition: opacity 1s ease; }
.fade-enter-from, .fade-leave-to { opacity: 0; }
.hero-in { position: relative; z-index: 2; padding: calc(var(--vp-nav-height) + 80px) 72px 0; max-width: 820px; }
.eyebrow { font-size: 12px; letter-spacing: .3em; color: var(--afl-amber); font-weight: 700; }
.hero h1 { font-size: 54px; line-height: 1.12; font-weight: 900; margin: 14px 0 16px; color: var(--afl-text); }
.hero p { font-size: 16.5px; line-height: 1.75; color: #cbc5ba; max-width: 600px; }
.bigsearch { margin-top: 28px; width: min(560px, 100%); height: 54px; border-radius: 14px; background: rgba(20,23,28,.9); border: 1px solid rgba(255,255,255,.14); display: flex; align-items: center; padding: 0 18px; gap: 12px; color: var(--afl-dim); font-size: 15px; box-shadow: 0 10px 40px rgba(0,0,0,.5); cursor: text; }
.bigsearch i { margin-left: auto; font-style: normal; font-family: var(--vp-font-family-mono); font-size: 12px; border: 1px solid var(--afl-line); border-radius: 6px; padding: 2px 8px; }
.slide-card { margin-top: 26px; display: flex; align-items: flex-end; gap: 20px; }
.slide-card a { display: block; border-left: 3px solid var(--afl-amber); padding: 4px 0 4px 14px; color: var(--afl-text); max-width: 440px; }
.slide-card .k { font-size: 11px; letter-spacing: .22em; color: var(--afl-amber2); font-weight: 700; }
.slide-card b { display: block; font-size: 20px; font-weight: 900; margin: 2px 0; }
.slide-card small { color: #cfc9bf; font-size: 13.5px; }
.dots { display: flex; gap: 6px; padding-bottom: 6px; }
.dots button { width: 22px; height: 4px; border-radius: 2px; background: rgba(255,255,255,.25); }
.dots button.on { background: var(--afl-amber); width: 36px; }
.main { max-width: 1320px; margin: -30px auto 0; padding: 0 48px 70px; position: relative; z-index: 3; }
.sh { display: flex; align-items: baseline; justify-content: space-between; margin: 40px 0 16px; }
.sh h2 { font-size: 24px; font-weight: 900; color: var(--afl-text); }
.sh a { color: var(--afl-amber2); font-size: 13.5px; }
.bento { display: grid; grid-template-columns: repeat(4, 1fr); grid-auto-rows: 190px; gap: 14px; }
.tile { position: relative; border-radius: 16px; overflow: hidden; background: var(--afl-card); border: 1px solid var(--afl-line); color: var(--afl-text); transition: transform .2s, border-color .2s; }
.tile:hover { transform: translateY(-2px); border-color: rgba(240,165,58,.5); }
.tile img { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: cover; }
.tile::after { content: ''; position: absolute; inset: 0; background: linear-gradient(180deg, rgba(0,0,0,0) 30%, rgba(0,0,0,.86) 100%); }
.tile .ph { position: absolute; inset: 0; background: radial-gradient(circle at 70% 30%, #2a2f37, #14171c 70%); }
.tile .tx { position: absolute; left: 18px; right: 18px; bottom: 16px; z-index: 2; }
.tile .k { font-size: 11px; letter-spacing: .2em; color: var(--afl-amber2); font-weight: 700; }
.tile b { display: block; font-size: 21px; font-weight: 900; margin: 3px 0 4px; }
.tile small { color: #cfc9bf; font-size: 13px; }
.tile.w2 { grid-column: span 2; }
.tile.h2 { grid-row: span 2; }
.row { display: grid; grid-template-columns: 1fr 1.3fr; gap: 14px; }
@media (max-width: 960px) {
  .hero { height: 600px; }
  .hero-in { padding: calc(var(--vp-nav-height) + 48px) 28px 0; }
  .hero h1 { font-size: 40px; }
  .main { padding: 0 24px 60px; }
  .bento { grid-template-columns: repeat(2, 1fr); grid-auto-rows: 170px; }
  .row { grid-template-columns: 1fr; }
}
@media (max-width: 560px) {
  .hero { height: 620px; }
  .hero::after { background: linear-gradient(180deg, rgba(11,13,16,.75) 0%, rgba(11,13,16,.6) 50%, var(--afl-bg) 100%); }
  .hero-in { padding: calc(var(--vp-nav-height) + 32px) 16px 0; }
  .hero p { font-size: 15px; }
  .bigsearch i { display: none; }
  .hero p { font-size: 15px; }
  .main { padding: 0 16px 50px; }
  .bento { grid-template-columns: 1fr; grid-auto-rows: 160px; }
  .tile.w2 { grid-column: auto; }
  .tile.h2 { grid-row: auto; }
}
</style>
