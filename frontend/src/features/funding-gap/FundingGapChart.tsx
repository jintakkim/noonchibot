import { useState, type MouseEvent } from 'react'
import { formatDateTime, formatExchangeRoute, formatPercent, formatSimpleApr } from '../../formatters'
import type { FundingGapHistory, FundingGapPoint } from '../../types'
import { FundingMetric } from './FundingMetric'

type FundingGapChartProps = {
  history?: FundingGapHistory
  error: boolean
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
  const averageRoute = formatExchangeRoute(history.statistics?.lowestExchange ?? latest.lowestExchange, history.statistics?.highestExchange ?? latest.highestExchange)
  const weightedRoute = formatExchangeRoute(history.statistics?.weightedLowestExchange ?? latest.weightedLowestExchange, history.statistics?.weightedHighestExchange ?? latest.weightedHighestExchange)
  const updateHover = (event: MouseEvent<HTMLDivElement>) => {
    const rect = event.currentTarget.getBoundingClientRect()
    const ratio = Math.min(1, Math.max(0, (event.clientX - rect.left) / rect.width))
    const index = Math.round(ratio * (history.gaps.length - 1))
    setHoverIndex(index)
  }

  return (
    <div className="p-5">
      <div className="mb-5 grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        <FundingMetric label="현재 일간 수익률 갭" value={formatPercent(latest.currentGap)} note={formatExchangeRoute(latest.lowestExchange, latest.highestExchange)} />
        <FundingMetric label="현재 단순 APR 갭" value={formatSimpleApr(latest.currentGap)} note={formatExchangeRoute(latest.lowestExchange, latest.highestExchange)} />
        <FundingMetric label="7일 평균 일간 수익률 갭" value={formatPercent(history.statistics?.averageGap ?? latest.currentGap)} note={averageRoute} />
        <FundingMetric label="24H 반감기 가중 일간 수익률 갭" value={formatPercent(history.statistics?.weightedAverageGap ?? latest.weightedAverageGap)} note={weightedRoute} accent />
      </div>
      <div className="relative h-64 overflow-hidden rounded-xl border border-slate-200 bg-white p-3">
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
            className={`pointer-events-none absolute top-4 min-w-64 rounded-xl border border-slate-200 bg-white/95 px-4 py-3 text-xs shadow-xl shadow-slate-300/40 ${tooltipAlign}`}
            style={{ left: tooltipLeft }}
          >
            <div className="mb-2 flex items-center justify-between gap-4">
              <p className="font-medium text-slate-900">{formatDateTime(tooltipPoint.timestamp)}</p>
              <p className="font-mono text-slate-500">APR {formatSimpleApr(tooltipPoint.currentGap)}</p>
            </div>
            <div className="space-y-1.5">
              <p className="flex justify-between gap-4"><span className="text-slate-500">일간 수익률 갭</span><span className="font-mono text-slate-900">{formatPercent(tooltipPoint.currentGap)}</span></p>
              <p className="truncate text-slate-500">{formatExchangeRoute(tooltipPoint.lowestExchange, tooltipPoint.highestExchange)}</p>
              <p className="flex justify-between gap-4"><span className="text-slate-500">가중 일간 갭</span><span className="font-mono text-cyan-700">{formatPercent(tooltipPoint.weightedAverageGap)}</span></p>
              <p className="truncate text-slate-500">{formatExchangeRoute(tooltipPoint.weightedLowestExchange, tooltipPoint.weightedHighestExchange)}</p>
            </div>
          </div>
        )}
      </div>
      <div className="mt-3 flex items-center gap-5 text-xs text-slate-500">
        <span><i className="mr-2 inline-block h-0.5 w-5 bg-slate-500 align-middle" />일간 수익률 갭</span>
        <span><i className="mr-2 inline-block h-0.5 w-5 bg-cyan-700 align-middle" />24H 반감기 가중 일간 수익률 갭</span>
        <span className="ml-auto">{formatDateTime(latest.timestamp)} 기준</span>
      </div>
    </div>
  )
}
