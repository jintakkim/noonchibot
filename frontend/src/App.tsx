import { useEffect, useMemo, useRef, useState } from 'react'

type ConnectionState = 'connecting' | 'connected' | 'disconnected'

type ExchangePrice = {
  exchange: string
  sourceTradingPair: string
  sourcePrice: number
  normalizedPrice: number
  gapBps: number
  priceTimestamp: string
}

type PriceGapSnapshot = {
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

type PriceGapMessage = {
  type: 'LAST_TRADE_PRICE_GAP'
  data: PriceGapSnapshot[]
}

const PAIRS = ['BTC-USDT', 'ETH-USDT', 'SOL-USDT']
const WS_URL = import.meta.env.VITE_WS_URL ?? `ws://${window.location.hostname}:8080/ws`

const exchangeName = (exchange?: string) => {
  if (!exchange) return '—'
  return exchange
    .replace('_DERIVATIVE', ' Futures')
    .replace('_SPOT', ' Spot')
    .split('_')
    .map((part) => part.charAt(0) + part.slice(1).toLowerCase())
    .join(' ')
}

const formatPrice = (value?: number) => {
  if (value == null) return '—'
  return new Intl.NumberFormat('en-US', {
    minimumFractionDigits: value < 10 ? 4 : 2,
    maximumFractionDigits: value < 10 ? 6 : 2,
  }).format(value)
}

const formatTime = (value?: string) => {
  if (!value) return '—'
  return new Intl.DateTimeFormat('ko-KR', {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  }).format(new Date(value))
}

function App() {
  const [connection, setConnection] = useState<ConnectionState>('connecting')
  const [snapshots, setSnapshots] = useState<Record<string, PriceGapSnapshot>>({})
  const [sortDescending, setSortDescending] = useState(true)
  const [lastUpdated, setLastUpdated] = useState<string>()
  const retryRef = useRef<number>(0)

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
        setSnapshots((current) => {
          const next = { ...current }
          message.data.forEach((snapshot) => {
            next[snapshot.key.tradingPair] = snapshot
          })
          return next
        })
        setLastUpdated(new Date().toISOString())
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
      socket?.close()
    }
  }, [])

  const rows = useMemo(() => Object.values(snapshots).sort((a, b) => {
    const left = a.spread?.gapBps ?? 0
    const right = b.spread?.gapBps ?? 0
    return sortDescending ? right - left : left - right
  }), [snapshots, sortDescending])

  const strongest = rows[0]

  return (
    <main className="min-h-screen bg-[#070b12] text-slate-100">
      <div className="mx-auto max-w-[1440px] px-5 py-8 lg:px-10">
        <header className="mb-8 flex flex-col gap-5 border-b border-white/8 pb-7 sm:flex-row sm:items-end sm:justify-between">
          <div>
            <div className="mb-2 flex items-center gap-2 text-xs font-semibold uppercase tracking-[0.24em] text-cyan-400">
              <span className="h-1.5 w-1.5 rounded-full bg-cyan-400 shadow-[0_0_12px_#22d3ee]" />
              Noonchi Terminal
            </div>
            <h1 className="text-3xl font-semibold tracking-tight text-white sm:text-4xl">실시간 거래소 가격 갭</h1>
            <p className="mt-2 text-sm text-slate-500">Binance Futures · Hyperliquid · 1초 단위 업데이트</p>
          </div>
          <div className="flex items-center gap-3 rounded-xl border border-white/8 bg-white/[0.03] px-4 py-3">
            <span className={`h-2.5 w-2.5 rounded-full ${connection === 'connected' ? 'bg-emerald-400 shadow-[0_0_10px_#34d399]' : connection === 'connecting' ? 'animate-pulse bg-amber-400' : 'bg-rose-400'}`} />
            <div>
              <p className="text-xs font-medium text-slate-300">{connection === 'connected' ? 'LIVE' : connection === 'connecting' ? 'CONNECTING' : 'RECONNECTING'}</p>
              <p className="mt-0.5 text-[11px] text-slate-600">{lastUpdated ? `최근 수신 ${formatTime(lastUpdated)}` : WS_URL}</p>
            </div>
          </div>
        </header>

        <section className="mb-6 grid gap-4 md:grid-cols-3">
          <Metric label="추적 중인 마켓" value={`${rows.length} / ${PAIRS.length}`} note={PAIRS.join(' · ')} />
          <Metric label="가장 큰 스프레드" value={strongest?.spread ? `${strongest.spread.gapBps.toFixed(6)} bps` : '—'} note={strongest?.key.tradingPair ?? '데이터 대기 중'} accent />
          <Metric label="데이터 상태" value={connection === 'connected' ? 'Streaming' : 'Waiting'} note="WebSocket batch subscription" />
        </section>

        <section className="overflow-hidden rounded-2xl border border-white/8 bg-[#0b111b] shadow-2xl shadow-black/20">
          <div className="flex items-center justify-between border-b border-white/8 px-5 py-4">
            <div>
              <h2 className="font-semibold text-white">Price Gap Monitor</h2>
              <p className="mt-1 text-xs text-slate-500">정규화 가격 기준 거래소 간 최대 스프레드</p>
            </div>
            <button
              type="button"
              onClick={() => setSortDescending((value) => !value)}
              className="rounded-lg border border-white/10 bg-white/[0.04] px-3 py-2 text-xs font-medium text-slate-300 transition hover:border-cyan-400/40 hover:text-cyan-300"
            >
              Gap {sortDescending ? '높은 순 ↓' : '낮은 순 ↑'}
            </button>
          </div>

          <div className="overflow-x-auto">
            <table className="w-full min-w-[980px] text-left">
              <thead className="bg-white/[0.025] text-[11px] uppercase tracking-[0.12em] text-slate-500">
                <tr>
                  <th className="px-5 py-3.5 font-medium">Market</th>
                  <th className="px-5 py-3.5 font-medium">Reference</th>
                  <th className="px-5 py-3.5 font-medium">Lowest</th>
                  <th className="px-5 py-3.5 font-medium">Highest</th>
                  <th className="px-5 py-3.5 text-right font-medium">Spread</th>
                  <th className="px-5 py-3.5 text-right font-medium">Updated</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-white/[0.055]">
                {rows.map((snapshot) => {
                  const low = snapshot.exchanges.find((item) => item.exchange === snapshot.spread?.lowestExchange)
                  const high = snapshot.exchanges.find((item) => item.exchange === snapshot.spread?.highestExchange)
                  return (
                    <tr key={snapshot.key.tradingPair} className="transition hover:bg-white/[0.025]">
                      <td className="px-5 py-5">
                        <div className="flex items-center gap-3">
                          <span className="grid h-9 w-9 place-items-center rounded-lg bg-cyan-400/10 text-xs font-bold text-cyan-300">{snapshot.key.tradingPair.slice(0, 3)}</span>
                          <div><p className="font-semibold text-white">{snapshot.key.tradingPair}</p><p className="mt-1 text-xs text-slate-600">#{snapshot.sequence}</p></div>
                        </div>
                      </td>
                      <td className="px-5 py-5 font-mono text-sm text-slate-300">${formatPrice(snapshot.referencePrice)}</td>
                      <td className="px-5 py-5"><ExchangeCell item={low} tone="low" /></td>
                      <td className="px-5 py-5"><ExchangeCell item={high} tone="high" /></td>
                      <td className="px-5 py-5 text-right">
                        <span className="inline-flex rounded-lg bg-cyan-400/10 px-3 py-1.5 font-mono text-sm font-semibold text-cyan-300">{snapshot.spread?.gapBps.toFixed(6) ?? '0.000000'} bps</span>
                      </td>
                      <td className="px-5 py-5 text-right font-mono text-xs text-slate-500">{formatTime(snapshot.timestamp)}</td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>

          {rows.length === 0 && (
            <div className="grid min-h-64 place-items-center px-6 text-center">
              <div><div className="mx-auto mb-4 h-8 w-8 animate-spin rounded-full border-2 border-slate-800 border-t-cyan-400" /><p className="text-sm text-slate-400">가격 데이터를 기다리는 중</p><p className="mt-1 text-xs text-slate-600">Spring Boot가 8080 포트에서 실행 중인지 확인하세요.</p></div>
            </div>
          )}
        </section>
      </div>
    </main>
  )
}

function Metric({ label, value, note, accent = false }: { label: string; value: string; note: string; accent?: boolean }) {
  return <div className="rounded-2xl border border-white/8 bg-[#0b111b] p-5"><p className="text-xs font-medium uppercase tracking-[0.12em] text-slate-500">{label}</p><p className={`mt-3 text-2xl font-semibold ${accent ? 'text-cyan-300' : 'text-white'}`}>{value}</p><p className="mt-2 truncate text-xs text-slate-600">{note}</p></div>
}

function ExchangeCell({ item, tone }: { item?: ExchangePrice; tone: 'low' | 'high' }) {
  return <div><p className={`text-sm font-medium ${tone === 'low' ? 'text-emerald-300' : 'text-rose-300'}`}>{exchangeName(item?.exchange)}</p><p className="mt-1 font-mono text-xs text-slate-500">${formatPrice(item?.normalizedPrice)}</p></div>
}

export default App
