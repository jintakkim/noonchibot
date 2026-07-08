import { useMemo, useState } from 'react'
import { FUNDING_ASSETS, PAIRS } from './constants'
import { DashboardHeader } from './components/DashboardHeader'
import { FundingGapSection } from './features/funding-gap/FundingGapSection'
import { PriceGapHistorySection } from './features/price-gap/PriceGapHistorySection'
import { PriceGapMonitor } from './features/price-gap/PriceGapMonitor'
import { useFundingGapHistory } from './hooks/useFundingGapHistory'
import { usePriceGapHistory } from './hooks/usePriceGapHistory'
import { usePriceGapSocket } from './hooks/usePriceGapSocket'
import type { PriceGapTimeline } from './types'

function App() {
  const [fundingAsset, setFundingAsset] = useState(FUNDING_ASSETS[0])
  const [priceHistoryPair, setPriceHistoryPair] = useState(PAIRS[0])
  const [priceTimeline, setPriceTimeline] = useState<PriceGapTimeline>('ONE_DAY')

  const { snapshots } = usePriceGapSocket()
  const { history: fundingHistory, error: fundingError } = useFundingGapHistory(fundingAsset)
  const { history: priceHistory, error: priceHistoryError } = usePriceGapHistory(priceHistoryPair, priceTimeline)

  const rows = useMemo(() => Object.values(snapshots).sort((a, b) => {
    const left = a.spread?.gapBps ?? 0
    const right = b.spread?.gapBps ?? 0
    return right - left
  }), [snapshots])

  return (
    <main className="min-h-screen bg-slate-50 text-slate-950">
      <div className="mx-auto max-w-[1440px] px-5 py-8 lg:px-10">
        <DashboardHeader />

        <PriceGapMonitor rows={rows} />

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
