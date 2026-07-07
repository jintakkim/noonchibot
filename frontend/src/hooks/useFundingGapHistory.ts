import { useEffect, useState } from 'react'
import type { FundingGapHistory } from '../types'

export function useFundingGapHistory(asset: string) {
  const [history, setHistory] = useState<FundingGapHistory>()
  const [error, setError] = useState(false)

  useEffect(() => {
    let disposed = false
    const load = async () => {
      try {
        const response = await fetch(`/api/funding-gaps/${asset}/history`)
        if (!response.ok) throw new Error(`HTTP ${response.status}`)
        const nextHistory = await response.json() as FundingGapHistory
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
  }, [asset])

  return { history, error }
}
