// Shared formatting for the survivor tables: an attribute change as players read it.
import { fmt } from '../i18n'

/** "+10", "−2 kg", "×0.9 (−10%)". */
export function effectValue(op: string, value: number, unit = '') {
  if (op === 'add') return `${value > 0 ? '+' : value < 0 ? '−' : ''}${fmt(Math.abs(value), 2)}${unit ? ' ' + unit : ''}`
  const pct = Math.round((value - 1) * 100)
  return `×${fmt(value, 2)}${pct ? ` (${pct > 0 ? '+' : '−'}${Math.abs(pct)}%)` : ''}`
}
