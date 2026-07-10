import type { ReactNode } from 'react'

type FundingMetricProps = {
  label: string
  value: ReactNode
  note: ReactNode
  description?: string | string[]
  descriptionPlacement?: 'left' | 'right'
  accent?: boolean
}

export function FundingMetric({ label, value, note, description, descriptionPlacement = 'left', accent = false }: FundingMetricProps) {
  const descriptionPosition = descriptionPlacement === 'right' ? 'right-0' : 'left-0'

  return (
    <div className="rounded-xl border border-slate-100 bg-slate-50 px-4 py-3">
      <div className="flex items-start gap-1.5">
        <p className="text-[11px] uppercase tracking-wider text-slate-600">{label}</p>
        {description && (
          <span className="group relative">
            <span
              tabIndex={0}
              className="grid h-4 w-4 cursor-help place-items-center rounded-full border border-slate-300 bg-white text-[10px] font-semibold leading-none text-slate-500 outline-none transition hover:border-cyan-300 hover:text-cyan-700 focus:border-cyan-300 focus:text-cyan-700"
            >
              ?
            </span>
            <span className={`pointer-events-none absolute top-5 z-20 hidden w-72 rounded-lg border border-slate-200 bg-white px-3 py-2.5 text-xs leading-5 text-slate-600 shadow-xl shadow-slate-300/40 group-hover:block group-focus-within:block ${descriptionPosition}`}>
              {Array.isArray(description) ? description.map((line) => (
                <span key={line} className="block [&+&]:mt-2">{line}</span>
              )) : description}
            </span>
          </span>
        )}
      </div>
      <div className={`mt-2 break-words leading-snug ${accent ? 'text-cyan-700' : 'text-slate-900'}`}>{value}</div>
      <div className="mt-2 text-xs text-slate-500">{note}</div>
    </div>
  )
}
