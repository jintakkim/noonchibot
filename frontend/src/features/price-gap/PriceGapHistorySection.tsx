import { PAIRS, TIMELINES } from '../../constants'
import type { PriceGapHistory, PriceGapTimeline } from '../../types'
import { PriceGapHistoryChart } from './PriceGapHistoryChart'

type PriceGapHistorySectionProps = {
  selectedPair: string
  selectedTimeline: PriceGapTimeline
  history?: PriceGapHistory
  error: boolean
  onSelectPair: (pair: string) => void
  onSelectTimeline: (timeline: PriceGapTimeline) => void
}

export function PriceGapHistorySection({
  selectedPair,
  selectedTimeline,
  history,
  error,
  onSelectPair,
  onSelectTimeline,
}: PriceGapHistorySectionProps) {
  return (
    <section className="mt-6 overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm shadow-slate-200/70">
      <div className="flex flex-col gap-4 border-b border-slate-200 px-5 py-4 lg:flex-row lg:items-center lg:justify-between">
        <div>
          <h2 className="font-semibold text-slate-950">과거 가격 갭</h2>
          <p className="mt-1 text-xs text-slate-500">거래소 확정 캔들의 종가 기준 스프레드</p>
        </div>
        <div className="flex flex-wrap gap-2">
          {PAIRS.map((pair) => (
            <button type="button" key={pair} onClick={() => onSelectPair(pair)} className={`rounded-lg px-3 py-2 text-xs font-medium transition ${selectedPair === pair ? 'bg-cyan-50 text-cyan-700' : 'bg-slate-50 text-slate-500 hover:text-slate-900'}`}>
              {pair.split('-')[0]}
            </button>
          ))}
          <span className="mx-1 w-px bg-slate-200" />
          {TIMELINES.map((timeline) => (
            <button type="button" key={timeline.value} onClick={() => onSelectTimeline(timeline.value)} className={`rounded-lg px-3 py-2 text-xs font-medium transition ${selectedTimeline === timeline.value ? 'bg-cyan-600 text-white' : 'text-slate-500 hover:text-slate-900'}`}>
              {timeline.label}
            </button>
          ))}
        </div>
      </div>
      <PriceGapHistoryChart history={history} error={error} />
    </section>
  )
}
