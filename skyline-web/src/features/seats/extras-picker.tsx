import { BadgeCheck, Minus, Plus } from 'lucide-react'

import type { AncillaryOffer, AncillaryType } from '@/api/types'
import { Button } from '@/components/ui/button'
import { removalBlockedReason } from '@/lib/ancillaries'
import { formatMoney } from '@/lib/format'

const GROUPS: { title: string; types: AncillaryType[] }[] = [
  { title: 'Bags', types: ['CABIN_BAG', 'CHECKED_BAG', 'EXTRA_WEIGHT', 'SPORTS_EQUIPMENT'] },
  { title: 'Extras', types: ['PRIORITY_BOARDING', 'MEAL', 'LEGROOM_BUNDLE'] },
]

interface ExtrasPickerProps {
  offers: AncillaryOffer[]
  currency: string
  /** What the fare already includes, shown first so nobody pays twice. */
  included: { code: string; name: string; quantity: number }[]
  /** Quantity being added per product code. */
  quantities: Record<string, number>
  onChange: (code: string, quantity: number) => void
  /** "Estimated" before booking: the server confirms every price. */
  estimate?: boolean
  disabled?: boolean
}

/** Bags and extras for one passenger on one flight, with steppers bounded by what may still be added. */
export function ExtrasPicker({
  offers,
  currency,
  included,
  quantities,
  onChange,
  estimate,
  disabled,
}: ExtrasPickerProps) {
  return (
    <div className="flex flex-col gap-6">
      <section aria-label="Included in your fare" className="rounded-card border border-border bg-card p-4">
        <h3 className="mb-2 micro-label">Included in your fare</h3>
        {included.length === 0 ? (
          <p className="text-sm text-muted-foreground">A personal item that fits under the seat.</p>
        ) : (
          <ul className="flex flex-wrap gap-2 text-sm">
            <li className="flex items-center gap-1.5 rounded-full bg-elevated px-3 py-1">
              <BadgeCheck className="size-4 text-success" strokeWidth={1.75} aria-hidden />
              Personal item
            </li>
            {included.map((item) => (
              <li key={item.code} className="flex items-center gap-1.5 rounded-full bg-elevated px-3 py-1">
                <BadgeCheck className="size-4 text-success" strokeWidth={1.75} aria-hidden />
                {item.name}
                {item.quantity > 1 && ` × ${item.quantity}`}
              </li>
            ))}
          </ul>
        )}
      </section>

      {GROUPS.map((group) => {
        const items = offers.filter((offer) => group.types.includes(offer.type))
        if (items.length === 0) return null
        return (
          <section key={group.title} aria-label={group.title} className="flex flex-col gap-2">
            <h3 className="text-h3 font-semibold">{group.title}</h3>
            <ul className="divide-y divide-border rounded-card border border-border bg-card">
              {items.map((offer) => {
                const quantity = quantities[offer.code] ?? 0
                const max = offer.maxAdditional
                // A bag carrying an extra-weight top-up can't go first (the server refuses that basket).
                const blocked = quantity > 0 ? removalBlockedReason(offers, quantities, offer.code) : null
                return (
                  <li key={offer.code} className="flex flex-wrap items-center gap-4 p-4">
                    <div className="flex min-w-0 flex-1 flex-col gap-0.5">
                      <span className="font-medium">
                        {offer.name}
                        {offer.purchasedQuantity > 0 && (
                          <span className="text-sm font-normal text-muted-foreground">
                            {' '}
                            · you have {offer.purchasedQuantity}
                          </span>
                        )}
                      </span>
                      <span className="text-sm text-muted-foreground">{offer.description}</span>
                      {!offer.available && offer.reason && quantity === 0 && (
                        <span className="text-caption font-medium">{offer.reason}</span>
                      )}
                      {blocked && <span className="text-caption font-medium">{blocked}</span>}
                      {offer.price !== null && offer.airportPrice !== null && offer.airportPrice > offer.price && (
                        <span className="text-caption text-muted-foreground">
                          {formatMoney(offer.airportPrice, currency)} at the airport
                        </span>
                      )}
                    </div>
                    <span className="w-24 text-right font-medium tabular">
                      {offer.price === null ? '' : formatMoney(offer.price, currency)}
                      {estimate && offer.price !== null && <span className="sr-only"> estimated</span>}
                    </span>
                    <div className="flex items-center gap-2" role="group" aria-label={`${offer.name} quantity`}>
                      <Button
                        variant="outline"
                        size="icon"
                        aria-label={`Remove one ${offer.name}`}
                        disabled={disabled || quantity === 0 || blocked !== null}
                        onClick={() => onChange(offer.code, quantity - 1)}
                      >
                        <Minus strokeWidth={1.75} aria-hidden />
                      </Button>
                      <output aria-live="polite" className="w-6 text-center tabular">
                        {quantity}
                      </output>
                      <Button
                        variant="outline"
                        size="icon"
                        aria-label={`Add one ${offer.name}`}
                        disabled={disabled || !offer.available || quantity >= max}
                        onClick={() => onChange(offer.code, quantity + 1)}
                      >
                        <Plus strokeWidth={1.75} aria-hidden />
                      </Button>
                    </div>
                  </li>
                )
              })}
            </ul>
          </section>
        )
      })}
    </div>
  )
}

/** Sum of the chosen add-ons at their (estimated) unit prices. */
export function extrasTotal(offers: AncillaryOffer[], quantities: Record<string, number>) {
  return offers.reduce((sum, offer) => sum + (offer.price ?? 0) * (quantities[offer.code] ?? 0), 0)
}
