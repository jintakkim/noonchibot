export const exchangeName = (exchange?: string) => {
  if (!exchange) return '-'
  return exchange
    .replace('_DERIVATIVE', ' Futures')
    .replace('_SPOT', ' Spot')
    .split('_')
    .map((part) => part.charAt(0) + part.slice(1).toLowerCase())
    .join(' ')
}

export const formatPrice = (value?: number) => {
  if (value == null) return '-'
  return new Intl.NumberFormat('en-US', {
    minimumFractionDigits: value < 10 ? 4 : 2,
    maximumFractionDigits: value < 10 ? 6 : 2,
  }).format(value)
}

export const formatTime = (value?: string) => {
  if (!value) return '-'
  return new Intl.DateTimeFormat('ko-KR', {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  }).format(new Date(value))
}

export const formatDateTime = (value?: string) => {
  if (!value) return '-'
  const date = new Date(value)
  const time = new Intl.DateTimeFormat('ko-KR', {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  }).format(date)
  return `${date.getMonth() + 1}월 ${date.getDate()}일 ${time}`
}

export const formatPercent = (value: number) => `${(value * 100).toFixed(4)}%`

export const formatSimpleApr = (dailyRate: number) => `${(dailyRate * 365 * 100).toFixed(2)}%`

export const formatExchangeRoute = (low?: string, high?: string) => `${exchangeName(low)} -> ${exchangeName(high)}`
