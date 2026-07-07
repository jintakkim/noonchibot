import { useState, type MouseEvent } from 'react'
import { formatDateTime, formatExchangeRoute, formatPercent } from '../../formatters'
import type { PriceGapHistory } from '../../types'

type PriceGapHistoryChartProps = {
  history?: PriceGapHistory
  error: boolean
}

export function PriceGapHistoryChart({ history, error }: PriceGapHistoryChartProps) {
  const [hoverIndex, setHoverIndex] = useState<number | null>(null)

  if (error) return <div className="grid h-80 place-items-center text-sm text-rose-600">가격갭 이력을 불러오지 못했습니다.</div>
  if (!history) return <div className="grid h-80 place-items-center text-sm text-slate-500">가격갭 이력을 불러오는 중</div>
  if (history.points.length === 0) return <div className="grid h-80 place-items-center text-sm text-slate-500">확정된 가격갭 이력이 없습니다.</div>

  const values = history.points.map((point) => point.gapBps)
  const minimum = Math.min(0, ...values)
  const maximum = Math.max(0, ...values)
  const range = maximum - minimum || 1
  const x = (index: number) => history.points.length === 1 ? 500 : index / (history.points.length - 1) * 1000
  const y = (value: number) => 220 - ((value - minimum) / range) * 200
  const coordinates = history.points.map((point, index) => {
    return `${x(index)},${y(point.gapBps)}`
  }).join(' ')
  const latest = history.points.at(-1)!
  const hovered = hoverIndex == null ? null : history.points[hoverIndex]
  const tooltipIndex = hoverIndex ?? history.points.length - 1
  const tooltipX = x(tooltipIndex)
  const tooltipLeft = `${tooltipX / 10}%`
  const tooltipAlign = tooltipX > 700 ? '-translate-x-full' : tooltipX > 300 ? '-translate-x-1/2' : ''
  const updateHover = (event: MouseEvent<HTMLDivElement>) => {
    const rect = event.currentTarget.getBoundingClientRect()
    const ratio = Math.min(1, Math.max(0, (event.clientX - rect.left) / rect.width))
    setHoverIndex(Math.round(ratio * (history.points.length - 1)))
  }

  return (
    <div className="p-5">
      <div className="mb-4 flex items-end justify-between">
        <div>
          <p className="text-[11px] uppercase tracking-wider text-slate-600">최근 확정 갭</p>
          <p className="mt-2 font-mono text-2xl font-semibold text-cyan-700">{latest.gapBps.toFixed(4)} bps</p>
          <p className="mt-1 text-xs text-slate-500">{formatExchangeRoute(latest.lowestExchange, latest.highestExchange)}</p>
        </div>
        <p className="text-xs text-slate-600">{formatDateTime(latest.timestamp)} 기준</p>
      </div>
      <div className="relative h-64 overflow-hidden rounded-xl border border-slate-200 bg-white p-3">
        <svg viewBox="0 0 1000 240" preserveAspectRatio="none" className="h-full w-full">
          {[20, 70, 120, 170, 220].map((lineY) => <line key={lineY} x1="0" y1={lineY} x2="1000" y2={lineY} stroke="rgba(100,116,139,0.14)" />)}
          <line x1="0" y1={y(0)} x2="1000" y2={y(0)} stroke="rgba(100,116,139,0.45)" strokeDasharray="5 5" />
          <polyline points={coordinates} fill="none" stroke="#0891b2" strokeWidth="2.5" vectorEffect="non-scaling-stroke" />
          {history.points.map((point, index) => (
            <circle
              key={point.timestamp}
              cx={x(index)}
              cy={y(point.gapBps)}
              r={hoverIndex === index ? 4.5 : 3}
              fill={hoverIndex === index ? '#0e7490' : '#0891b2'}
              stroke="#ffffff"
              strokeWidth="2"
              vectorEffect="non-scaling-stroke"
            />
          ))}
          {hovered && (
            <>
              <line x1={tooltipX} y1="12" x2={tooltipX} y2="228" stroke="rgba(148,163,184,0.35)" strokeDasharray="5 5" />
              <circle cx={tooltipX} cy={y(hovered.gapBps)} r="5" fill="#0e7490" stroke="#ffffff" strokeWidth="2" vectorEffect="non-scaling-stroke" />
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
            className={`pointer-events-none absolute top-4 min-w-52 rounded-xl border border-slate-200 bg-white/95 px-4 py-3 text-xs shadow-xl shadow-slate-300/40 ${tooltipAlign}`}
            style={{ left: tooltipLeft }}
          >
            <div className="mb-2 flex items-center justify-between gap-4">
              <p className="font-medium text-slate-900">{formatDateTime(hovered.timestamp)}</p>
              <p className="font-mono text-cyan-700">{hovered.gapBps.toFixed(4)} bps</p>
            </div>
            <p className="flex justify-between gap-4">
              <span className="text-slate-500">가격 갭 비율</span>
              <span className="font-mono text-slate-900">{formatPercent(hovered.gapRate)}</span>
            </p>
            <p className="mt-1 truncate text-slate-500">{formatExchangeRoute(hovered.lowestExchange, hovered.highestExchange)}</p>
          </div>
        )}
      </div>
    </div>
  )
}
