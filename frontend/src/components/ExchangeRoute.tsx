import { exchangeName } from '../formatters'

type ExchangeRouteProps = {
  buy?: string
  sell?: string
  className?: string
  noWrap?: boolean
}

export function ExchangeRoute({ buy, sell, className = '', noWrap = false }: ExchangeRouteProps) {
  const wrapClassName = noWrap ? 'flex-nowrap' : 'flex-wrap'
  const badgeClassName = noWrap ? 'max-w-none whitespace-nowrap' : 'max-w-full'
  const exchangeClassName = noWrap ? 'normal-case' : 'min-w-0 truncate normal-case'

  return (
    <div className={`flex min-w-0 max-w-full items-center gap-1.5 ${wrapClassName} ${className}`}>
      <span className={`inline-flex min-w-0 items-center gap-1.5 rounded-md border border-emerald-200 bg-emerald-50 px-2 py-1 text-[11px] font-semibold uppercase text-emerald-700 shadow-sm shadow-emerald-100/70 ${badgeClassName}`}>
        <span>Buy</span>
        <span className={`text-emerald-950 ${exchangeClassName}`}>{exchangeName(buy)}</span>
      </span>
      <span className="text-[11px] font-medium text-slate-300">/</span>
      <span className={`inline-flex min-w-0 items-center gap-1.5 rounded-md border border-rose-200 bg-rose-50 px-2 py-1 text-[11px] font-semibold uppercase text-rose-700 shadow-sm shadow-rose-100/70 ${badgeClassName}`}>
        <span>Sell</span>
        <span className={`text-rose-950 ${exchangeClassName}`}>{exchangeName(sell)}</span>
      </span>
    </div>
  )
}
