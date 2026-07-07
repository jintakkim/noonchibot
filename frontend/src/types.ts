export type ConnectionState = 'connecting' | 'connected' | 'disconnected'

export type ExchangePrice = {
  exchange: string
  sourceTradingPair: string
  sourcePrice: number
  normalizedPrice: number
  gapBps: number
  priceTimestamp: string
}

export type PriceGapSnapshot = {
  key: { tradingPair: string }
  timestamp: string
  sequence: number
  referencePrice: number
  exchanges: ExchangePrice[]
  spread: {
    lowestExchange: string
    highestExchange: string
    gapBps: number
  } | null
}

export type PriceGapMessage = {
  type: 'LAST_TRADE_PRICE_GAP'
  data: PriceGapSnapshot[]
}

export type FundingGapPoint = {
  timestamp: string
  lowestExchange: string
  highestExchange: string
  lowestDailyRate: number
  highestDailyRate: number
  currentGap: number
  weightedLowestExchange: string
  weightedHighestExchange: string
  weightedAverageGap: number
}

export type FundingGapHistory = {
  asset: string
  from: string
  to: string
  weightedAverageHalfLife: string
  statistics: {
    lowestExchange: string
    highestExchange: string
    averageGap: number
    weightedLowestExchange: string
    weightedHighestExchange: string
    weightedAverageGap: number
  } | null
  gaps: FundingGapPoint[]
}

export type PriceGapTimeline = 'ONE_HOUR' | 'FOUR_HOURS' | 'ONE_DAY' | 'SEVEN_DAYS'

export type PriceGapHistory = {
  tradingPair: string
  timeline: PriceGapTimeline
  points: Array<{
    timestamp: string
    lowestExchange: string
    highestExchange: string
    gapRate: number
    gapBps: number
  }>
}
