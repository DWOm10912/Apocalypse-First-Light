import { defineConfig } from 'vitepress'

const sections = {
  zh: [
    { key: 'guide', text: '新手指南' },
    { key: 'arms', text: '枪械与装备' },
    { key: 'vehicles', text: '载具' },
    { key: 'industry', text: '工业制造' },
    { key: 'world', text: '世界与探索' },
    { key: 'infected', text: '感染体' },
    { key: 'changelog', text: '更新日志' }
  ],
  en: [
    { key: 'guide', text: 'Getting Started' },
    { key: 'arms', text: 'Arms & Gear' },
    { key: 'vehicles', text: 'Vehicles' },
    { key: 'industry', text: 'Industry' },
    { key: 'world', text: 'World' },
    { key: 'infected', text: 'Infected' },
    { key: 'changelog', text: 'Changelog' }
  ]
}

// Sidebar entries per section; gun pages are generated from native_guns/*.json.
import { loadGuns } from './lib/gamedata.mjs'
const guns = loadGuns()
const byClass = (cls) => guns.filter((g) => g.weaponClass === cls)

function sidebar(loc: 'zh' | 'en') {
  const p = loc === 'zh' ? '' : '/en'
  const t = (zh: string, en: string) => (loc === 'zh' ? zh : en)
  const gunItems = (cls: string) => byClass(cls).map((g) => ({ text: g.name[loc], link: `${p}/arms/guns/${g.id}` }))
  return {
    [`${p}/guide/`]: [
      { text: t('新手指南', 'Getting Started'), items: [
        { text: t('开局', 'First Steps'), link: `${p}/guide/` },
        { text: t('操作按键', 'Controls'), link: `${p}/guide/controls` },
        { text: t('生存状态', 'Survival Stats'), link: `${p}/guide/survival` },
        { text: t('背包与负重', 'Inventory & Weight'), link: `${p}/guide/inventory` },
        { text: t('辐射', 'Radiation'), link: `${p}/guide/radiation` },
        { text: t('地图', 'Map'), link: `${p}/guide/map` }
      ] }
    ],
    [`${p}/arms/`]: [
      { text: t('枪械与装备', 'Arms & Gear'), items: [{ text: t('总览', 'Overview'), link: `${p}/arms/` }] },
      { text: t('手枪', 'Pistols'), items: gunItems('pistol') },
      { text: t('步枪', 'Rifles'), items: gunItems('rifle') },
      { text: t('霰弹枪', 'Shotguns'), items: gunItems('shotgun') },
      { text: t('弹药与配件', 'Ammo & Attachments'), items: [
        { text: t('弹药', 'Ammo'), link: `${p}/arms/ammo` },
        { text: t('配件', 'Attachments'), link: `${p}/arms/attachments` },
        { text: t('装备', 'Equipment'), link: `${p}/arms/equipment` }
      ] }
    ],
    [`${p}/vehicles/`]: [
      { text: t('载具', 'Vehicles'), items: [
        { text: t('总览', 'Overview'), link: `${p}/vehicles/` },
        { text: 'T15', link: `${p}/vehicles/t15` },
        { text: t('燃料与加油站', 'Fuel & Stations'), link: `${p}/vehicles/fuel` }
      ] }
    ],
    [`${p}/industry/`]: [
      { text: t('工业制造', 'Industry'), items: [
        { text: t('总览', 'Overview'), link: `${p}/industry/` },
        { text: t('发电机', 'Generators'), link: `${p}/industry/generators` },
        { text: t('建筑供电', 'Building Power'), link: `${p}/industry/power` },
        { text: t('材料与工作台', 'Materials & Benches'), link: `${p}/industry/materials` }
      ] }
    ],
    [`${p}/world/`]: [
      { text: t('世界与探索', 'World'), items: [
        { text: t('总览', 'Overview'), link: `${p}/world/` },
        { text: t('生物群系', 'Biomes'), link: `${p}/world/biomes` },
        { text: t('道路与城镇', 'Roads & Towns'), link: `${p}/world/roads` },
        { text: t('建筑与地点', 'Buildings & Places'), link: `${p}/world/places` },
        { text: t('森林与伐木', 'Forests & Felling'), link: `${p}/world/forests` },
        { text: t('天气', 'Weather'), link: `${p}/world/weather` }
      ] }
    ],
    [`${p}/infected/`]: [
      { text: t('感染体', 'Infected'), items: [{ text: t('总览', 'Overview'), link: `${p}/infected/` }] }
    ],
    [`${p}/changelog/`]: [
      { text: t('更新日志', 'Changelog'), items: [
        { text: t('版本', 'Versions'), link: `${p}/changelog/` },
        { text: t('数值变更', 'Value Changes'), link: `${p}/changelog/values` }
      ] }
    ],
    [`${p}/about/`]: [
      { text: t('关于', 'About'), items: [{ text: t('数值与版本', 'Data & Versions'), link: `${p}/about/` }] }
    ]
  }
}

function nav(loc: 'zh' | 'en') {
  const p = loc === 'zh' ? '' : '/en'
  return sections[loc].map((s) => ({ text: s.text, link: `${p}/${s.key}/`, activeMatch: `^${p}/${s.key}/` }))
}

export default defineConfig({
  title: 'AFL Wiki',
  description: 'Apocalypse: First Light',
  cleanUrls: true,
  // Gun prose fragments are injected into the gun pages, not pages of their own.
  srcExclude: ['content/**', 'scripts/**'],
  lastUpdated: false,
  appearance: 'force-dark',
  head: [
    ['link', { rel: 'icon', href: '/img/favicon.png' }],
    ['link', { rel: 'preconnect', href: 'https://fonts.googleapis.com' }],
    ['link', { rel: 'stylesheet', href: 'https://fonts.googleapis.com/css2?family=Noto+Sans+SC:wght@400;500;700;900&family=JetBrains+Mono:wght@500;700&display=swap' }],
    ['meta', { name: 'theme-color', content: '#0b0d10' }]
  ],
  // gun pages: page title from the generated params
  transformPageData(pageData) {
    const t = (pageData.params as any)?.title
    if (t) pageData.title = t
  },
  locales: {
    root: {
      label: '中文',
      lang: 'zh-CN',
      themeConfig: {
        nav: nav('zh'),
        sidebar: sidebar('zh'),
        outline: { level: [2, 3], label: '本页内容' },
        docFooter: { prev: '上一篇', next: '下一篇' },
        returnToTopLabel: '回到顶部',
        sidebarMenuLabel: '目录',
        langMenuLabel: '语言',
        darkModeSwitchLabel: '外观'
      }
    },
    en: {
      label: 'English',
      lang: 'en-US',
      link: '/en/',
      themeConfig: {
        nav: nav('en'),
        sidebar: sidebar('en'),
        outline: { level: [2, 3], label: 'On this page' }
      }
    }
  },
  themeConfig: {
    logo: '/img/afl_title.webp',
    siteTitle: 'WIKI',
    search: {
      provider: 'local',
      options: {
        locales: {
          root: { translations: { button: { buttonText: '搜索', buttonAriaLabel: '搜索' }, modal: { noResultsText: '没有找到', resetButtonTitle: '清除', footer: { selectText: '选择', navigateText: '切换', closeText: '关闭' } } } }
        }
      }
    }
  }
})
