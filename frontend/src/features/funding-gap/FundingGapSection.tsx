import { FUNDING_ASSETS } from '../../constants'
import type { FundingGapHistory } from '../../types'
import { FundingGapChart } from './FundingGapChart'

type FundingGapSectionProps = {
  selectedAsset: string
  history?: FundingGapHistory
  error: boolean
  onSelectAsset: (asset: string) => void
}

export function FundingGapSection({ selectedAsset, history, error, onSelectAsset }: FundingGapSectionProps) {
  return (
    <section className="mt-6 overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm shadow-slate-200/70">
      <div className="flex flex-col gap-4 border-b border-slate-200 px-5 py-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h2 className="font-semibold text-slate-950">7일 펀딩비 갭</h2>
          <p className="mt-1 text-xs text-slate-500">거래소별 일간 환산 펀딩 수익률 차이 · 단순 APR은 일간 수익률 × 365</p>
        </div>
        <div className="flex gap-2">
          {FUNDING_ASSETS.map((asset) => (
            <button
              type="button"
              key={asset}
              onClick={() => onSelectAsset(asset)}
              className={`rounded-lg px-3 py-2 text-xs font-medium transition ${selectedAsset === asset ? 'bg-cyan-50 text-cyan-700' : 'bg-slate-50 text-slate-500 hover:text-slate-900'}`}
            >
              {asset}
            </button>
          ))}
        </div>
      </div>
      <FundingGapChart history={history} error={error} />
    </section>
  )
}
