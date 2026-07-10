import { useState } from 'react'
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
  const [assetMenuOpen, setAssetMenuOpen] = useState(false)

  const selectAsset = (asset: string) => {
    onSelectAsset(asset)
    setAssetMenuOpen(false)
  }

  return (
    <section className="mt-6 overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm shadow-slate-200/70">
      <div className="flex flex-col gap-4 border-b border-slate-200 px-5 py-4 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <h2 className="text-2xl font-semibold tracking-tight text-slate-950">펀딩비 기준 수익률</h2>
          <p className="mt-1 text-xs text-slate-500">현재 펀딩비 기준 1일 예상 수익률과 7일 평균 기회를 비교합니다 · 연환산은 1일 예상 수익률 × 365</p>
        </div>
        <div className="relative min-w-44">
          <p className="mb-1.5 text-[11px] font-semibold uppercase tracking-[0.16em] text-slate-400">마켓 선택</p>
          <button
            type="button"
            onClick={() => setAssetMenuOpen((open) => !open)}
            className="flex h-11 w-full items-center justify-between rounded-xl border border-slate-200 bg-white px-3.5 text-left text-sm font-semibold text-slate-950 shadow-sm shadow-slate-200/70 transition hover:border-cyan-300 hover:bg-cyan-50/40 focus:outline-none focus:ring-2 focus:ring-cyan-200"
            aria-haspopup="listbox"
            aria-expanded={assetMenuOpen}
          >
            <span className="flex items-center gap-2">
              <span className="h-2 w-2 rounded-full bg-cyan-500" />
              {selectedAsset}-USDT
            </span>
            <span className={`text-xs text-slate-400 transition ${assetMenuOpen ? 'rotate-180' : ''}`}>⌄</span>
          </button>
          {assetMenuOpen && (
            <div className="absolute right-0 z-30 mt-2 w-full overflow-hidden rounded-xl border border-slate-200 bg-white p-1.5 shadow-xl shadow-slate-300/50" role="listbox">
              {FUNDING_ASSETS.map((asset) => {
                const selected = selectedAsset === asset
                return (
                  <button
                    type="button"
                    key={asset}
                    onClick={() => selectAsset(asset)}
                    className={`flex w-full items-center justify-between rounded-lg px-3 py-2.5 text-left text-sm font-semibold transition ${selected ? 'bg-cyan-50 text-cyan-700' : 'text-slate-700 hover:bg-slate-50 hover:text-slate-950'}`}
                    role="option"
                    aria-selected={selected}
                  >
                    <span>{asset}-USDT</span>
                    {selected && <span className="text-xs text-cyan-600">선택됨</span>}
                  </button>
                )
              })}
            </div>
          )}
        </div>
      </div>
      <FundingGapChart history={history} error={error} />
    </section>
  )
}
