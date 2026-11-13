import { cn } from '@/lib/cn'
import { formatMoney, moneyParts } from '@/lib/format'

interface PriceTagProps {
  amount: number
  currency: string
  /** "hero": the headline figure; "inline": running text. */
  size?: 'hero' | 'large' | 'inline'
  /** Shown above the figure, e.g. "Estimated" before booking. */
  label?: string
  className?: string
}

/** A price in the response's currency. Hero size sets the integer part large and the cents smaller. */
export function PriceTag({ amount, currency, size = 'inline', label, className }: PriceTagProps) {
  if (size === 'inline') {
    return <span className={cn('font-medium tabular', className)}>{formatMoney(amount, currency)}</span>
  }

  const parts = moneyParts(amount, currency)
  const fraction = parts.filter((part) => part.type === 'decimal' || part.type === 'fraction')
  const main = parts.filter((part) => part.type !== 'decimal' && part.type !== 'fraction')

  return (
    <span className={cn('inline-flex flex-col', className)}>
      {label && <span className="mb-2 micro-label">{label}</span>}
      <span className={cn('hero-number', size === 'hero' ? 'text-display-sm sm:text-display' : 'text-h1')}>
        <span className="sr-only">{formatMoney(amount, currency)}</span>
        <span aria-hidden>{main.map((part) => part.value).join('')}</span>
        <span aria-hidden className="text-[0.5em] text-muted-foreground">
          {fraction.map((part) => part.value).join('')}
        </span>
      </span>
    </span>
  )
}
