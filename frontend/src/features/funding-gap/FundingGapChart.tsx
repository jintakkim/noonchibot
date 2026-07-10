import { useState, type MouseEvent } from 'react'
import { ExchangeRoute } from '../../components/ExchangeRoute'
import { formatDailyPercent, formatDateTime, formatSimpleApr } from '../../formatters'
import type { FundingGapHistory, FundingGapPoint } from '../../types'
import { FundingMetric } from './FundingMetric'

const CURRENT_PROFIT_DESCRIPTION = [
  '현재 펀딩비 기준으로 Buy 거래소에서 매수하고 Sell 거래소에서 매도했을 때 기대할 수 있는 1일 예상 수익률입니다.',
  '연환산은 이 1일 예상 수익률이 1년 동안 그대로 반복된다고 가정한 단순 환산 값입니다.',
  '수수료, 슬리피지, 가격 변동은 제외합니다.',
]
const AVERAGE_PROFIT_DESCRIPTION = [
  '최근 7일 펀딩비 갭을 평균낸 뒤 1일 예상 수익률로 환산한 값입니다.',
  '연환산은 이 1일 예상 수익률이 1년 동안 그대로 반복된다고 가정한 단순 환산 값입니다.',
  '단기 튐보다 전체 구간의 평균적인 기회를 볼 때 씁니다.',
  '수수료, 슬리피지, 가격 변동은 제외합니다.',
]
const WEIGHTED_PROFIT_DESCRIPTION = [
  '최근 7일 펀딩비 갭에 시간 가중치를 적용한 뒤 1일 예상 수익률로 환산한 값입니다.',
  '최근에 발생한 펀딩비일수록 더 높은 가중치를 두고, 오래된 펀딩비일수록 영향이 작아지도록 계산합니다.',
  '연환산은 이 1일 예상 수익률이 1년 동안 그대로 반복된다고 가정한 단순 환산 값입니다.',
  '단순 7일 평균보다 시장 변화를 빠르게 반영합니다.',
  '수수료, 슬리피지, 가격 변동은 제외합니다.',
]

type FundingGapChartProps = {
  history?: FundingGapHistory
  error: boolean
}

function FundingReturnValue({ value, accent = false }: { value: number; accent?: boolean }) {
  const dailyRate = (value * 100).toFixed(4)

  return (
    <div>
      <p className="flex flex-wrap items-baseline gap-1.5">
        <span className="text-2xl font-semibold">{dailyRate}%</span>
        <span className={`text-sm font-medium ${accent ? 'text-cyan-600' : 'text-slate-500'}`}>/일</span>
      </p>
      <p className={`mt-1 text-sm font-medium ${accent ? 'text-cyan-600' : 'text-slate-500'}`}>연환산 {formatSimpleApr(value)}</p>
    </div>
  )
}

export function FundingGapChart({ history, error }: FundingGapChartProps) {
  const [hoverIndex, setHoverIndex] = useState<number | null>(null)

  if (error) return <div className="grid h-72 place-items-center text-sm text-rose-600">펀딩 이력을 불러오지 못했습니다.</div>
  if (!history) return <div className="grid h-72 place-items-center text-sm text-slate-500">펀딩 이력을 불러오는 중</div>
  if (history.gaps.length === 0) return <div className="grid h-72 place-items-center text-sm text-slate-500">동기화된 펀딩 이력이 없습니다.</div>

  const values = history.gaps.flatMap((point) => [point.currentGap, point.weightedAverageGap])
  const minimum = Math.min(...values)
  const maximum = Math.max(...values)
  const range = maximum - minimum || 1
  const xFor = (index: number) => history.gaps.length === 1 ? 500 : index / (history.gaps.length - 1) * 1000
  const yFor = (value: number) => 220 - ((value - minimum) / range) * 200
  const coordinates = (selector: (point: FundingGapPoint) => number) => history.gaps
    .map((point, index) => `${xFor(index)},${yFor(selector(point))}`)
    .join(' ')
  const latest = history.gaps.at(-1)!
  const hovered = hoverIndex == null ? null : history.gaps[hoverIndex]
  const tooltipPoint = hovered ?? latest
  const tooltipIndex = hoverIndex ?? history.gaps.length - 1
  const tooltipX = xFor(tooltipIndex)
  const tooltipLeft = `${tooltipX / 10}%`
  const tooltipAlign = tooltipX > 700 ? '-translate-x-full' : tooltipX > 300 ? '-translate-x-1/2' : ''
  const averageBuyExchange = history.statistics?.lowestExchange ?? latest.lowestExchange
  const averageSellExchange = history.statistics?.highestExchange ?? latest.highestExchange
  const weightedBuyExchange = history.statistics?.weightedLowestExchange ?? latest.weightedLowestExchange
  const weightedSellExchange = history.statistics?.weightedHighestExchange ?? latest.weightedHighestExchange
  const updateHover = (event: MouseEvent<HTMLDivElement>) => {
    const rect = event.currentTarget.getBoundingClientRect()
    const ratio = Math.min(1, Math.max(0, (event.clientX - rect.left) / rect.width))
    const index = Math.round(ratio * (history.gaps.length - 1))
    setHoverIndex(index)
  }

  return (
    <div className="p-5">
      <div className="mb-5 grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
        <FundingMetric label="현재 펀딩비 기준 1일 예상 수익률" value={<FundingReturnValue value={latest.currentGap} />} description={CURRENT_PROFIT_DESCRIPTION} note={<ExchangeRoute buy={latest.lowestExchange} sell={latest.highestExchange} />} />
        <FundingMetric label="7일 평균 펀딩비 갭 기준 1일 예상 수익률" value={<FundingReturnValue value={history.statistics?.averageGap ?? latest.currentGap} />} description={AVERAGE_PROFIT_DESCRIPTION} note={<ExchangeRoute buy={averageBuyExchange} sell={averageSellExchange} />} />
        <FundingMetric label="7일 가중평균 펀딩비 갭 기준 1일 예상 수익률" value={<FundingReturnValue value={history.statistics?.weightedAverageGap ?? latest.weightedAverageGap} />} description={WEIGHTED_PROFIT_DESCRIPTION} descriptionPlacement="right" note={<ExchangeRoute buy={weightedBuyExchange} sell={weightedSellExchange} />} />
      </div>
      <div className="relative h-64 rounded-xl border border-slate-200 bg-white p-3">
        <svg viewBox="0 0 1000 240" preserveAspectRatio="none" className="h-full w-full">
          {[20, 70, 120, 170, 220].map((lineY) => <line key={lineY} x1="0" y1={lineY} x2="1000" y2={lineY} stroke="rgba(100,116,139,0.14)" />)}
          <polyline points={coordinates((point) => point.currentGap)} fill="none" stroke="#64748b" strokeWidth="2" vectorEffect="non-scaling-stroke" />
          <polyline points={coordinates((point) => point.weightedAverageGap)} fill="none" stroke="#0891b2" strokeWidth="2.5" vectorEffect="non-scaling-stroke" />
          {hovered && (
            <>
              <line x1={tooltipX} y1="12" x2={tooltipX} y2="228" stroke="rgba(148,163,184,0.35)" strokeDasharray="5 5" />
              <circle cx={tooltipX} cy={yFor(hovered.currentGap)} r="4" fill="#64748b" stroke="#ffffff" strokeWidth="2" vectorEffect="non-scaling-stroke" />
              <circle cx={tooltipX} cy={yFor(hovered.weightedAverageGap)} r="4" fill="#0891b2" stroke="#ffffff" strokeWidth="2" vectorEffect="non-scaling-stroke" />
            </>
          )}
        </svg>
        <div
          className="absolute inset-3 cursor-crosshair"
          onMouseMove={updateHover}
          onMouseLeave={() => setHoverIndex(null)}
        />
        {hovered && (
          <div
            className={`pointer-events-none absolute top-4 w-[28rem] max-w-[calc(100%-1.5rem)] rounded-xl border border-slate-200 bg-white/95 px-4 py-3 text-xs shadow-xl shadow-slate-300/40 ${tooltipAlign}`}
            style={{ left: tooltipLeft }}
          >
            <div className="mb-2 flex items-center justify-between gap-4">
              <p className="font-medium text-slate-900">{formatDateTime(tooltipPoint.timestamp)}</p>
            </div>
            <div className="space-y-1.5">
              <p className="flex justify-between gap-4"><span className="text-sm font-semibold text-slate-700">해당 시점 1일 예상 수익률</span><span className="text-right font-mono text-slate-900">{formatDailyPercent(tooltipPoint.currentGap)}<br /><span className="text-slate-500">연환산 {formatSimpleApr(tooltipPoint.currentGap)}</span></span></p>
              <ExchangeRoute buy={tooltipPoint.lowestExchange} sell={tooltipPoint.highestExchange} noWrap />
              <p className="flex justify-between gap-4"><span className="text-sm font-semibold text-slate-700">7일 가중평균 기준</span><span className="text-right font-mono text-cyan-700">{formatDailyPercent(tooltipPoint.weightedAverageGap)}<br /><span className="text-cyan-600">연환산 {formatSimpleApr(tooltipPoint.weightedAverageGap)}</span></span></p>
              <ExchangeRoute buy={tooltipPoint.weightedLowestExchange} sell={tooltipPoint.weightedHighestExchange} noWrap />
            </div>
          </div>
        )}
      </div>
      <div className="mt-3 flex items-center gap-5 text-xs text-slate-500">
        <span title={CURRENT_PROFIT_DESCRIPTION.join(' ')}><i className="mr-2 inline-block h-0.5 w-5 bg-slate-500 align-middle" />현재 펀딩비 기준 1일 예상 수익률</span>
        <span title={WEIGHTED_PROFIT_DESCRIPTION.join(' ')}><i className="mr-2 inline-block h-0.5 w-5 bg-cyan-700 align-middle" />7일 가중평균 펀딩비 갭 기준</span>
        <span className="ml-auto">{formatDateTime(latest.timestamp)} 기준</span>
      </div>
    </div>
  )
}
