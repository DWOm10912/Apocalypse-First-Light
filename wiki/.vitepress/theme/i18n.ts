import { computed } from 'vue'
import { useData, withBase } from 'vitepress'

export type Loc = 'zh' | 'en'

export function useLoc() {
  const { lang } = useData()
  const loc = computed<Loc>(() => (lang.value.startsWith('zh') ? 'zh' : 'en'))
  const t = (zh: string, en: string) => (loc.value === 'zh' ? zh : en)
  const link = (p: string) => withBase((loc.value === 'zh' ? '' : '/en') + p)
  return { loc, t, link }
}

export const CLASS_NAMES: Record<string, { zh: string; en: string }> = {
  pistol: { zh: '手枪', en: 'Pistol' },
  rifle: { zh: '步枪', en: 'Rifle' },
  shotgun: { zh: '霰弹枪', en: 'Shotgun' },
  smg: { zh: '冲锋枪', en: 'SMG' },
  precision_rifle: { zh: '精确步枪', en: 'Precision rifle' },
  machine_gun: { zh: '机枪', en: 'Machine gun' },
  special: { zh: '特殊', en: 'Special' }
}

export const MODE_NAMES: Record<string, { zh: string; en: string }> = {
  semi: { zh: '半自动', en: 'Semi-auto' },
  auto: { zh: '全自动', en: 'Full-auto' },
  burst: { zh: '点射', en: 'Burst' }
}

export const SLOT_NAMES: Record<string, { zh: string; en: string }> = {
  sight: { zh: '瞄具', en: 'Sight' },
  muzzle: { zh: '枪口', en: 'Muzzle' },
  magazine: { zh: '弹匣', en: 'Magazine' },
  sling: { zh: '背带', en: 'Sling' }
}

// Images from images.json (scripts/images.mjs). Public images are served from R2 at the URL in images.lock.json;
// one not uploaded yet is staged under /img/staged/; internal images are bundled under /img/.
import images from '../../images.json'
import uploaded from '../../images.lock.json'
export const hasImage = (key: string) => Object.prototype.hasOwnProperty.call(images, key)
export function img(key: string) {
  const spec = (images as any)[key]
  const file = `${key}.${spec?.format ?? 'webp'}`
  if (spec?.visibility === 'public') {
    const remote = (uploaded as any)[key]?.url
    return remote ?? withBase(`/img/staged/${file}`)
  }
  return withBase(`/img/${file}`)
}

// Damage at a distance, same formula as NativeGunShot.damageAt:
// full damage up to falloff_start, then linear down to base × min_damage_multiplier at max_range.
// The shot is traced only to max_range. effective_range is just the tooltip's "Range" line.
export function damageAt(d: any, dist: number) {
  if (dist > d.maxRange) return 0
  if (dist <= d.falloffStart) return d.base
  const k = Math.min(1, (dist - d.falloffStart) / (d.maxRange - d.falloffStart))
  return d.base * (1 - k * (1 - d.minMultiplier))
}

export const fmt = (n: number | null | undefined, digits = 2) =>
  n == null ? '—' : String(+n.toFixed(digits))
