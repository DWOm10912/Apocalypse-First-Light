import DefaultTheme from 'vitepress/theme'
import type { Theme } from 'vitepress'
import Layout from './Layout.vue'
import HomePage from './components/HomePage.vue'
import GunHead from './components/GunHead.vue'
import GunStats from './components/GunStats.vue'
import GunTable from './components/GunTable.vue'
import RecentChanges from './components/RecentChanges.vue'
import VersionCard from './components/VersionCard.vue'
import StatusTag from './components/StatusTag.vue'
import Shot from './components/Shot.vue'
import V from './components/V.vue'
import AccuracyTable from './components/AccuracyTable.vue'
import BiomeTable from './components/BiomeTable.vue'
import AmmoTable from './components/AmmoTable.vue'
import AttachmentTable from './components/AttachmentTable.vue'
import UpdateLog from './components/UpdateLog.vue'
import TraitTable from './components/TraitTable.vue'
import BackgroundTable from './components/BackgroundTable.vue'
import LevelTable from './components/LevelTable.vue'
import StatusTable from './components/StatusTable.vue'
import GunLevelTable from './components/GunLevelTable.vue'
import TownTable from './components/TownTable.vue'
import ItemIcon from './components/ItemIcon.vue'
import ModelShot from './components/ModelShot.vue'
import './style.css'

export default {
  extends: DefaultTheme,
  Layout,
  enhanceApp({ app }) {
    app.component('HomePage', HomePage)
    app.component('GunHead', GunHead)
    app.component('GunStats', GunStats)
    app.component('GunTable', GunTable)
    app.component('RecentChanges', RecentChanges)
    app.component('VersionCard', VersionCard)
    app.component('StatusTag', StatusTag)
    app.component('Shot', Shot)
    app.component('V', V)
    app.component('AccuracyTable', AccuracyTable)
    app.component('BiomeTable', BiomeTable)
    app.component('AmmoTable', AmmoTable)
    app.component('AttachmentTable', AttachmentTable)
    app.component('UpdateLog', UpdateLog)
    app.component('TraitTable', TraitTable)
    app.component('BackgroundTable', BackgroundTable)
    app.component('LevelTable', LevelTable)
    app.component('StatusTable', StatusTable)
    app.component('GunLevelTable', GunLevelTable)
    app.component('TownTable', TownTable)
    app.component('ItemIcon', ItemIcon)
    app.component('ModelShot', ModelShot)
  }
} satisfies Theme
