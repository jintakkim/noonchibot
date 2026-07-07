type MetricProps = {
  label: string
  value: string
  note: string
  accent?: boolean
}

export function Metric({ label, value, note, accent = false }: MetricProps) {
  return (
    <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm shadow-slate-200/70">
      <p className="text-xs font-medium uppercase tracking-[0.12em] text-slate-500">{label}</p>
      <p className={`mt-3 text-2xl font-semibold ${accent ? 'text-cyan-700' : 'text-slate-950'}`}>{value}</p>
      <p className="mt-2 truncate text-xs text-slate-500">{note}</p>
    </div>
  )
}
