import * as React from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'

import { ancillariesApi } from '@/api/endpoints/ancillaries'
import { seatsApi } from '@/api/endpoints/seats'
import { isApiError } from '@/api/http'
import { ancillaryKeys, reservationKeys } from '@/api/query-keys'
import type { ReservationResponse } from '@/api/types'
import { ErrorState, LoadingRegion, SkeletonCard } from '@/components/skyline/states'
import { Button } from '@/components/ui/button'
import { formatMoney } from '@/lib/format'
import { ExtrasPicker, extrasTotal } from './extras-picker'
import { useAncillaryOptions } from './queries'
import { SeatSelector } from './seat-selector'

const errorMessage = (error: unknown) => (isApiError(error) ? error.message : 'That didn’t work, please try again.')

function useRefreshReservation(reservation: ReservationResponse) {
  const queryClient = useQueryClient()
  return () =>
    Promise.all([
      queryClient.invalidateQueries({ queryKey: reservationKeys.all }),
      queryClient.invalidateQueries({ queryKey: ancillaryKeys.options(reservation.id) }),
    ])
}

/**
 * Choose or change the seat of a booked passenger (manage booking, check-in): hold a seat on the map, then confirm
 * it, which charges its fee. Final once checked in.
 */
export function ReservationSeatPicker({ reservation }: { reservation: ReservationResponse }) {
  const refresh = useRefreshReservation(reservation)
  const [held, setHeld] = React.useState<{ seat: string; price: number } | null>(null)
  const flight = reservation.flight!

  const confirm = useMutation({
    mutationFn: (seat: string) => seatsApi.confirmSeat(reservation.id, seat),
    onSuccess: async (result) => {
      setHeld(null)
      toast.success(`Seat ${result.data.seatNumber} is yours`)
      await refresh()
    },
    onError: (error) => toast.error(errorMessage(error)),
  })

  if (reservation.status === 'CHECKED_IN') {
    return (
      <p className="text-muted-foreground">
        You’re checked in: your seat {reservation.seatNumber} is on your boarding pass and can’t be changed.
      </p>
    )
  }

  const seat = held?.seat ?? reservation.seatNumber
  const price =
    held?.price ?? reservation.ancillaries?.find((l) => l.type === 'SEAT' && l.status === 'ACTIVE')?.price ?? 0

  return (
    <SeatSelector
      slots={[
        {
          key: reservation.id,
          flightId: flight.id,
          legLabel: flight.flightNumber,
          passengerLabel: `${reservation.passenger.firstName} ${reservation.passenger.lastName}`,
          short: '1',
          params: { reservationId: reservation.id },
          seat,
          price: seat ? price : null,
        },
      ]}
      currency={reservation.currency}
      onChoose={async (_, chosen) => {
        const result = await seatsApi.holdForReservation(reservation.id, chosen.code)
        setHeld({ seat: result.data.seatNumber, price: result.data.price })
      }}
      onClear={async () => {
        if (held) {
          await seatsApi.releaseForReservation(reservation.id)
          setHeld(null)
        } else {
          await seatsApi.removeSeat(reservation.id)
          await refresh()
        }
      }}
      summaryAction={
        held && (
          <Button loading={confirm.isPending} onClick={() => confirm.mutate(held.seat)}>
            {held.price
              ? `Pay ${formatMoney(held.price, reservation.currency)} for ${held.seat}`
              : `Confirm seat ${held.seat}`}
          </Button>
        )
      }
    />
  )
}

/** Bags and extras for a booked passenger, with what they already have and can remove. */
export function ReservationExtras({ reservation }: { reservation: ReservationResponse }) {
  const options = useAncillaryOptions(reservation.id)
  const refresh = useRefreshReservation(reservation)
  const [quantities, setQuantities] = React.useState<Record<string, number>>({})

  const purchase = useMutation({
    mutationFn: () =>
      ancillariesApi.purchase(reservation.id, {
        items: Object.entries(quantities)
          .filter(([, quantity]) => quantity > 0)
          .map(([productCode, quantity]) => ({ productCode, quantity })),
      }),
    onSuccess: async () => {
      setQuantities({})
      toast.success('Added to your booking')
      await refresh()
    },
    onError: (error) => toast.error(errorMessage(error)),
  })
  const remove = useMutation({
    mutationFn: (lineId: string) => ancillariesApi.remove(reservation.id, lineId),
    onSuccess: async (result) => {
      toast.success(result.message ?? 'Removed')
      await refresh()
    },
    onError: (error) => toast.error(errorMessage(error)),
  })

  if (options.isPending) {
    return (
      <LoadingRegion label="Loading extras">
        <SkeletonCard className="h-72" lines={4} />
      </LoadingRegion>
    )
  }
  if (options.isError) return <ErrorState error={options.error} onRetry={() => void options.refetch()} />

  const bought = (reservation.ancillaries ?? []).filter((line) => line.type !== 'SEAT' && line.status === 'ACTIVE')
  const total = extrasTotal(options.data.offers, quantities)

  return (
    <div className="flex flex-col gap-5">
      {bought.length > 0 && (
        <section aria-label="Your extras" className="rounded-card border border-border bg-card p-4">
          <h3 className="mb-2 micro-label">Your extras</h3>
          <ul className="flex flex-col gap-2 text-sm">
            {bought.map((line) => (
              <li key={line.id} className="flex items-center gap-3">
                <span className="flex-1">
                  {line.description}
                  {line.quantity > 1 && ` × ${line.quantity}`}
                  <span className="text-muted-foreground">
                    {line.refundable ? ' · refundable' : ' · non-refundable'}
                  </span>
                </span>
                <span className="tabular">{formatMoney(line.price, reservation.currency)}</span>
                <Button variant="ghost" size="sm" disabled={remove.isPending} onClick={() => remove.mutate(line.id)}>
                  Remove<span className="sr-only"> {line.description}</span>
                </Button>
              </li>
            ))}
          </ul>
        </section>
      )}
      <ExtrasPicker
        offers={options.data.offers}
        included={options.data.included}
        currency={options.data.currency}
        quantities={quantities}
        onChange={(code, quantity) => setQuantities((current) => ({ ...current, [code]: quantity }))}
        disabled={purchase.isPending}
      />
      <div className="flex flex-wrap items-center justify-between gap-3 rounded-card border border-border bg-card p-4">
        <span className="text-sm">
          To pay <span className="font-semibold tabular">{formatMoney(total, options.data.currency)}</span>
        </span>
        <Button
          disabled={total === 0 && !Object.values(quantities).some((q) => q > 0)}
          loading={purchase.isPending}
          onClick={() => purchase.mutate()}
        >
          Add to booking
        </Button>
      </div>
    </div>
  )
}
