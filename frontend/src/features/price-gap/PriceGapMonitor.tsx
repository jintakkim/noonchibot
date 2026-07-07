import { formatPrice, formatTime } from '../../formatters'
import type { PriceGapSnapshot } from '../../types'
import { ExchangeCell } from './ExchangeCell'

type PriceGapMonitorProps = {
  rows: PriceGapSnapshot[]
  sortDescending: boolean
  onToggleSort: () => void
}

export function PriceGapMonitor({ rows, sortDescending, onToggleSort }: PriceGapMonitorProps) {
  return (
    <section className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm shadow-slate-200/70">
      <div className="flex items-center justify-between border-b border-slate-200 px-5 py-4">
        <div>
          <h2 className="font-semibold text-slate-950">Price Gap Monitor</h2>
          <p className="mt-1 text-xs text-slate-500">정규화 가격 기준 거래소 간 최대 스프레드</p>
        </div>
        <button
          type="button"
          onClick={onToggleSort}
          className="rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-xs font-medium text-slate-700 transition hover:border-cyan-300 hover:bg-cyan-50 hover:text-cyan-700"
        >
          Gap {sortDescending ? '높은 순 ↓' : '낮은 순 ↑'}
        </button>
      </div>

      <div className="overflow-x-auto">
        <table className="w-full min-w-[980px] text-left">
          <thead className="bg-slate-50 text-[11px] uppercase tracking-[0.12em] text-slate-500">
            <tr>
              <th className="px-5 py-3.5 font-medium">Market</th>
              <th className="px-5 py-3.5 font-medium">Reference</th>
              <th className="px-5 py-3.5 font-medium">Lowest</th>
              <th className="px-5 py-3.5 font-medium">Highest</th>
              <th className="px-5 py-3.5 text-right font-medium">Spread</th>
              <th className="px-5 py-3.5 text-right font-medium">Updated</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {rows.map((snapshot) => {
              const low = snapshot.exchanges.find((item) => item.exchange === snapshot.spread?.lowestExchange)
              const high = snapshot.exchanges.find((item) => item.exchange === snapshot.spread?.highestExchange)
              return (
                <tr key={snapshot.key.tradingPair} className="transition hover:bg-slate-50">
                  <td className="px-5 py-5">
                    <div className="flex items-center gap-3">
                      <span className="grid h-9 w-9 place-items-center rounded-lg bg-cyan-50 text-xs font-bold text-cyan-700">{snapshot.key.tradingPair.slice(0, 3)}</span>
                      <div>
                        <p className="font-semibold text-slate-950">{snapshot.key.tradingPair}</p>
                        <p key={snapshot.sequence} className="price-update-flash mt-1 text-xs text-slate-500">#{snapshot.sequence}</p>
                      </div>
                    </div>
                  </td>
                  <td className="px-5 py-5 font-mono text-sm text-slate-700"><span key={`${snapshot.sequence}-reference`} className="price-update-flash">${formatPrice(snapshot.referencePrice)}</span></td>
                  <td className="px-5 py-5"><ExchangeCell item={low} tone="low" /></td>
                  <td className="px-5 py-5"><ExchangeCell item={high} tone="high" /></td>
                  <td className="px-5 py-5 text-right">
                    <span className="inline-flex rounded-lg bg-cyan-50 px-3 py-1.5 font-mono text-sm font-semibold text-cyan-700"><span key={`${snapshot.sequence}-spread`} className="price-update-flash">{snapshot.spread?.gapBps.toFixed(6) ?? '0.000000'} bps</span></span>
                  </td>
                  <td className="px-5 py-5 text-right font-mono text-xs text-slate-500"><span key={`${snapshot.sequence}-time`} className="price-update-flash">{formatTime(snapshot.timestamp)}</span></td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>

      {rows.length === 0 && (
        <div className="grid min-h-64 place-items-center px-6 text-center">
          <div>
            <div className="mx-auto mb-4 h-8 w-8 animate-spin rounded-full border-2 border-slate-200 border-t-cyan-600" />
            <p className="text-sm text-slate-600">가격 데이터를 기다리는 중</p>
            <p className="mt-1 text-xs text-slate-600">Spring Boot가 8080 포트에서 실행 중인지 확인하세요.</p>
          </div>
        </div>
      )}
    </section>
  )
}
