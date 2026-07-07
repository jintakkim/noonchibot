import { WS_URL } from '../constants'
import { formatTime } from '../formatters'
import type { ConnectionState } from '../types'

type DashboardHeaderProps = {
  connection: ConnectionState
  lastUpdated?: string
}

export function DashboardHeader({ connection, lastUpdated }: DashboardHeaderProps) {
  return (
    <header className="mb-8 flex flex-col gap-5 border-b border-slate-200 pb-7 sm:flex-row sm:items-end sm:justify-between">
      <div>
        <div className="mb-2 flex items-center gap-2 text-xs font-semibold uppercase tracking-[0.24em] text-cyan-700">
          <span className="h-1.5 w-1.5 rounded-full bg-cyan-600 shadow-[0_0_10px_rgba(8,145,178,0.35)]" />
          Noonchi Terminal
        </div>
        <h1 className="text-3xl font-semibold tracking-tight text-slate-950 sm:text-4xl">실시간 거래소 가격 갭</h1>
        <p className="mt-2 text-sm text-slate-500">Binance Futures · Hyperliquid · 1초 단위 업데이트</p>
      </div>
      <div className="flex items-center gap-3 rounded-xl border border-slate-200 bg-white px-4 py-3 shadow-sm shadow-slate-200/70">
        <span className={`h-2.5 w-2.5 rounded-full ${connection === 'connected' ? 'bg-emerald-500 shadow-[0_0_10px_rgba(16,185,129,0.35)]' : connection === 'connecting' ? 'animate-pulse bg-amber-500' : 'bg-rose-500'}`} />
        <div>
          <p className="text-xs font-medium text-slate-700">{connection === 'connected' ? 'LIVE' : connection === 'connecting' ? 'CONNECTING' : 'RECONNECTING'}</p>
          <p className="mt-0.5 text-[11px] text-slate-500">{lastUpdated ? `최근 수신 ${formatTime(lastUpdated)}` : WS_URL}</p>
        </div>
      </div>
    </header>
  )
}
