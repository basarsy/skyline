import * as React from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { Armchair, X } from 'lucide-react'
import { toast } from 'sonner'

import type { SeatMapParams } from '@/api/endpoints/seats'
import { isApiError } from '@/api/http'
import { seatKeys } from '@/api/query-keys'
import type { SeatView } from '@/api/types'
import { SeatMap } from '@/components/skyline/seat-map/seat-map'
import { ErrorState, LoadingRegion, SkeletonCard } from '@/components/skyline/states'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/cn'
import { formatMoney } from '@/lib/format'
import { isNoSeatMap, useSeatMap } from './queries'

/** One passenger on one flight: the unit a seat is chosen for. */
export interface SeatSlot {
  key: string
  flightId: string
  /** e.g. "IST → LHR · SK101"; the leg switcher shows when slots span several flights. */
  legLabel: string
  /** e.g. "Ada Lovelace" */
  passengerLabel: string
  /** Shown on the seat map for this passenger, e.g. "1". */
  short: string
  params: SeatMapParams
  seat: string | null
  price: number | null
}

interface SeatSelectorProps {
  slots: SeatSlot[]
  currency: string
  /** Hold (and for a booked passenger, maybe confirm) the seat. Throw to report a failure. */
  onChoose: (slot: SeatSlot, seat: SeatView) => Promise<void>
  onClear: (slot: SeatSlot) => Promise<void>
  /** Shown when the aircraft has no seat map. */
  noSeatMapMessage?: string
  /** Extra content for the summary bar (e.g. a confirm button). */
  summaryAction?: React.ReactNode
}

/**
 * Seat selection for any number of passengers and legs: a leg switcher, a passenger switcher, the seat map of the
 * active passenger (with the others' seats marked), and a summary of every choice with its price. A seat someone
 * else just took is a 409: the map reloads and the passenger picks again.
 */
export function SeatSelector({
  slots,
  currency,
  onChoose,
  onClear,
  noSeatMapMessage = 'Seat selection isn’t available on this aircraft. We’ll assign you a seat at check-in.',
  summaryAction,
}: SeatSelectorProps) {
  const queryClient = useQueryClient()
  const legs = [...new Map(slots.map((slot) => [slot.flightId, slot.legLabel])).entries()]
  const [activeKey, setActiveKey] = React.useState(slots[0]?.key)
  const active = slots.find((slot) => slot.key === activeKey) ?? slots[0]
  const [pending, setPending] = React.useState<string | null>(null)
  const map = useSeatMap(active?.flightId, active?.params ?? { reservationId: '' }, Boolean(active))

  if (!active) return null
  const legSlots = slots.filter((slot) => slot.flightId === active.flightId)
  const companions = Object.fromEntries(
    legSlots.filter((slot) => slot.key !== active.key && slot.seat).map((slot) => [slot.seat!, slot.short]),
  )
  const seatTotal = slots.reduce((sum, slot) => sum + (slot.price ?? 0), 0)

  const refresh = () => queryClient.invalidateQueries({ queryKey: seatKeys.flight(active.flightId) })

  const choose = async (seat: SeatView) => {
    if (pending) return
    setPending(seat.code)
    try {
      if (seat.code === active.seat) await onClear(active)
      else {
        await onChoose(active, seat)
        // Next passenger on this leg without a seat, so a family can be seated in a few taps.
        const next = legSlots.find((slot) => slot.key !== active.key && !slot.seat)
        if (next) setActiveKey(next.key)
      }
    } catch (error) {
      toast.error(isApiError(error) ? error.message : 'That didn’t work, please try again.')
    } finally {
      setPending(null)
      await refresh()
    }
  }

  return (
    <div className="flex flex-col gap-5">
      {legs.length > 1 && (
        <div role="tablist" aria-label="Flight" className="flex flex-wrap gap-2">
          {legs.map(([flightId, label]) => (
            <button
              key={flightId}
              type="button"
              role="tab"
              aria-selected={flightId === active.flightId}
              onClick={() => setActiveKey(slots.find((slot) => slot.flightId === flightId)?.key)}
              className={cn(
                'rounded-full border px-4 py-2 text-sm focus-ring',
                flightId === active.flightId ? 'border-primary bg-brand-soft font-medium' : 'border-border',
              )}
            >
              {label}
            </button>
          ))}
        </div>
      )}

      {legSlots.length > 1 && (
        <div role="tablist" aria-label="Passenger" className="flex flex-wrap gap-2">
          {legSlots.map((slot) => (
            <button
              key={slot.key}
              type="button"
              role="tab"
              aria-selected={slot.key === active.key}
              onClick={() => setActiveKey(slot.key)}
              className={cn(
                'flex items-center gap-2 rounded-full border px-4 py-2 text-sm focus-ring',
                slot.key === active.key ? 'border-primary bg-brand-soft font-medium' : 'border-border',
              )}
            >
              <span className="grid size-5 place-items-center rounded-full bg-elevated text-caption font-semibold">
                {slot.short}
              </span>
              {slot.passengerLabel}
              <span className="font-mono text-muted-foreground">{slot.seat ?? 'No seat'}</span>
            </button>
          ))}
        </div>
      )}

      {map.isPending ? (
        <LoadingRegion label="Loading seat map">
          <SkeletonCard className="h-[480px]" />
        </LoadingRegion>
      ) : map.isError ? (
        isNoSeatMap(map.error) ? (
          <p className="rounded-card border border-border bg-card p-5 text-muted-foreground">{noSeatMapMessage}</p>
        ) : (
          <ErrorState error={map.error} onRetry={() => void map.refetch()} />
        )
      ) : (
        <SeatMap map={map.data} selected={active.seat} companions={companions} pending={pending} onSelect={choose} />
      )}

      <div
        aria-label="Seat summary"
        role="region"
        className="sticky bottom-[calc(4.25rem+env(safe-area-inset-bottom))] z-20 flex flex-col gap-3 rounded-card border border-border bg-card p-4 md:bottom-4"
      >
        <ul className="flex flex-col gap-1.5 text-sm">
          {slots.map((slot) => (
            <li key={slot.key} className="flex items-center gap-2">
              <Armchair className="size-4 text-muted-foreground" strokeWidth={1.75} aria-hidden />
              <span className="min-w-0 flex-1 truncate">
                {slot.passengerLabel}
                {legs.length > 1 && <span className="text-muted-foreground"> · {slot.legLabel}</span>}
              </span>
              <span className="font-mono">{slot.seat ?? '—'}</span>
              <span className="w-20 text-right tabular">
                {slot.seat ? (slot.price ? formatMoney(slot.price, currency) : 'Included') : ''}
              </span>
              {slot.seat && (
                <Button
                  variant="ghost"
                  size="icon"
                  className="size-7"
                  aria-label={`Remove seat ${slot.seat} for ${slot.passengerLabel}`}
                  disabled={Boolean(pending)}
                  onClick={async () => {
                    setPending(slot.seat)
                    try {
                      await onClear(slot)
                    } catch (error) {
                      toast.error(isApiError(error) ? error.message : 'That didn’t work, please try again.')
                    } finally {
                      setPending(null)
                      await queryClient.invalidateQueries({ queryKey: seatKeys.flight(slot.flightId) })
                    }
                  }}
                >
                  <X strokeWidth={1.75} aria-hidden />
                </Button>
              )}
            </li>
          ))}
        </ul>
        <div className="flex flex-wrap items-center justify-between gap-3 border-t border-border pt-3">
          <span className="text-sm">
            Seats <span className="font-semibold tabular">{formatMoney(seatTotal, currency)}</span>
          </span>
          {summaryAction}
        </div>
      </div>
    </div>
  )
}
