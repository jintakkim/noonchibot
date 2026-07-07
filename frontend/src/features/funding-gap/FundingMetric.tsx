type FundingMetricProps = {
  label: string
  value: string
  note: string
  accent?: boolean
}

export function FundingMetric({ label, value, note, accent = false }: FundingMetricProps) {
  return (
    <div className="rounded-xl border border-slate-100 bg-slate-50 px-4 py-3">
      <p className="text-[11px] uppercase tracking-wider text-slate-600">{label}</p>
      <p className={`mt-2 font-mono text-lg font-semibold ${accent ? 'text-cyan-700' : 'text-slate-900'}`}>{value}</p>
      <p className="mt-1 truncate text-xs text-slate-500">{note}</p>
    </div>
  )
}
