import { useEffect, useRef, useState } from 'react'
import { PAIRS, WS_URL } from '../constants'
import type { ConnectionState, PriceGapMessage, PriceGapSnapshot } from '../types'

export function usePriceGapSocket() {
  const [connection, setConnection] = useState<ConnectionState>('connecting')
  const [snapshots, setSnapshots] = useState<Record<string, PriceGapSnapshot>>({})
  const [lastUpdated, setLastUpdated] = useState<string>()
  const retryRef = useRef<number>(0)
  const pendingSnapshotsRef = useRef<Record<string, PriceGapSnapshot>>({})
  const renderFrameRef = useRef<number | null>(null)

  useEffect(() => {
    let socket: WebSocket | undefined
    let retryTimer: number | undefined
    let disposed = false

    const connect = () => {
      setConnection('connecting')
      socket = new WebSocket(WS_URL)

      socket.onopen = () => {
        retryRef.current = 0
        setConnection('connected')
        socket?.send(JSON.stringify({
          method: 'subscribe',
          subscription: { type: 'priceGap', pairs: PAIRS },
        }))
      }

      socket.onmessage = (event) => {
        const message = JSON.parse(event.data) as PriceGapMessage
        if (message.type !== 'LAST_TRADE_PRICE_GAP' || !Array.isArray(message.data)) return
        message.data.forEach((snapshot) => {
          if (snapshot.spread == null || snapshot.exchanges.length < 2) return
          pendingSnapshotsRef.current[snapshot.key.tradingPair] = snapshot
        })

        if (renderFrameRef.current != null) return
        renderFrameRef.current = window.requestAnimationFrame(() => {
          const pending = pendingSnapshotsRef.current
          pendingSnapshotsRef.current = {}
          renderFrameRef.current = null
          if (Object.keys(pending).length === 0) return
          setSnapshots((current) => ({ ...current, ...pending }))
          setLastUpdated(new Date().toISOString())
        })
      }

      socket.onclose = () => {
        if (disposed) return
        setConnection('disconnected')
        const delay = Math.min(1000 * 2 ** retryRef.current++, 10_000)
        retryTimer = window.setTimeout(connect, delay)
      }

      socket.onerror = () => socket?.close()
    }

    connect()
    return () => {
      disposed = true
      if (retryTimer) window.clearTimeout(retryTimer)
      if (renderFrameRef.current != null) window.cancelAnimationFrame(renderFrameRef.current)
      socket?.close()
    }
  }, [])

  return { connection, snapshots, lastUpdated }
}
