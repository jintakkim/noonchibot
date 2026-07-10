import type { PriceGapTimeline } from './types'

export const PAIRS = ['BTC-USDT', 'ETH-USDT', 'SOL-USDT']
export const FUNDING_ASSETS = ['BTC', 'ETH', 'SOL']
export const WS_URL = import.meta.env.VITE_WS_URL ?? `ws://${window.location.hostname}:8080/ws`

export const TIMELINES: Array<{ value: PriceGapTimeline; label: string }> = [
  { value: 'ONE_HOUR', label: '1H' },
  { value: 'FOUR_HOURS', label: '4H' },
  { value: 'ONE_DAY', label: '1D' },
  { value: 'SEVEN_DAYS', label: '7D' },
]
