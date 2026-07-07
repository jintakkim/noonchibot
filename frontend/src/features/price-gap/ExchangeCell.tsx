import { exchangeName, formatPrice } from '../../formatters'
import type { ExchangePrice } from '../../types'

type ExchangeCellProps = {
  item?: ExchangePrice
  tone: 'low' | 'high'
}

export function ExchangeCell({ item, tone }: ExchangeCellProps) {
  return (
    <div>
      <p className={`text-sm font-medium ${tone === 'low' ? 'text-emerald-700' : 'text-rose-700'}`}>{exchangeName(item?.exchange)}</p>
      <p className="mt-1 font-mono text-xs text-slate-500">${formatPrice(item?.normalizedPrice)}</p>
    </div>
  )
}
