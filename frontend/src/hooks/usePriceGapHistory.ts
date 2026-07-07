import { useEffect, useState } from 'react'
import type { PriceGapHistory, PriceGapTimeline } from '../types'

export function usePriceGapHistory(pair: string, timeline: PriceGapTimeline) {
  const [history, setHistory] = useState<PriceGapHistory>()
  const [error, setError] = useState(false)

  useEffect(() => {
    let disposed = false
    const load = async () => {
      try {
        const response = await fetch(`/api/price-gaps/${pair}/history?timeline=${timeline}`)
        if (!response.ok) throw new Error(`HTTP ${response.status}`)
        const nextHistory = await response.json() as PriceGapHistory
        if (!disposed) {
          setHistory(nextHistory)
          setError(false)
        }
      } catch {
        if (!disposed) setError(true)
      }
    }
    setHistory(undefined)
    void load()
    const timer = window.setInterval(load, 60 * 1000)
    return () => {
      disposed = true
      window.clearInterval(timer)
    }
  }, [pair, timeline])

  return { history, error }
}
