import { useMemo, useState } from 'react'
import { FUNDING_ASSETS, PAIRS } from './constants'
import { DashboardHeader } from './components/DashboardHeader'
import { Metric } from './components/Metric'
import { FundingGapSection } from './features/funding-gap/FundingGapSection'
import { PriceGapHistorySection } from './features/price-gap/PriceGapHistorySection'
import { PriceGapMonitor } from './features/price-gap/PriceGapMonitor'
import { useFundingGapHistory } from './hooks/useFundingGapHistory'
import { usePriceGapHistory } from './hooks/usePriceGapHistory'
import { usePriceGapSocket } from './hooks/usePriceGapSocket'
import type { PriceGapTimeline } from './types'

function App() {
  const [sortDescending, setSortDescending] = useState(true)
  const [fundingAsset, setFundingAsset] = useState(FUNDING_ASSETS[0])
  const [priceHistoryPair, setPriceHistoryPair] = useState(PAIRS[0])
  const [priceTimeline, setPriceTimeline] = useState<PriceGapTimeline>('ONE_DAY')

  const { connection, snapshots, lastUpdated } = usePriceGapSocket()
  const { history: fundingHistory, error: fundingError } = useFundingGapHistory(fundingAsset)
  const { history: priceHistory, error: priceHistoryError } = usePriceGapHistory(priceHistoryPair, priceTimeline)

  const rows = useMemo(() => Object.values(snapshots).sort((a, b) => {
    const left = a.spread?.gapBps ?? 0
    const right = b.spread?.gapBps ?? 0
    return sortDescending ? right - left : left - right
  }), [snapshots, sortDescending])

  const strongest = rows[0]

  return (
    <main className="min-h-screen bg-slate-50 text-slate-950">
      <div className="mx-auto max-w-[1440px] px-5 py-8 lg:px-10">
        <DashboardHeader connection={connection} lastUpdated={lastUpdated} />

        <section className="mb-6 grid gap-4 md:grid-cols-3">
          <Metric label="추적 중인 마켓" value={`${rows.length} / ${PAIRS.length}`} note={PAIRS.join(' · ')} />
          <Metric label="가장 큰 스프레드" value={strongest?.spread ? `${strongest.spread.gapBps.toFixed(6)} bps` : '-'} note={strongest?.key.tradingPair ?? '데이터 대기 중'} accent />
          <Metric label="데이터 상태" value={connection === 'connected' ? 'Streaming' : 'Waiting'} note="WebSocket batch subscription" />
        </section>

        <PriceGapMonitor
          rows={rows}
          sortDescending={sortDescending}
          onToggleSort={() => setSortDescending((value) => !value)}
        />

        <PriceGapHistorySection
          selectedPair={priceHistoryPair}
          selectedTimeline={priceTimeline}
          history={priceHistory}
          error={priceHistoryError}
          onSelectPair={setPriceHistoryPair}
          onSelectTimeline={setPriceTimeline}
        />

        <FundingGapSection
          selectedAsset={fundingAsset}
          history={fundingHistory}
          error={fundingError}
          onSelectAsset={setFundingAsset}
        />
      </div>
    </main>
  )
}

export default App
