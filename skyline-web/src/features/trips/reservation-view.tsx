import * as React from 'react'
import { Eye, EyeOff, Ticket, TicketCheck, X } from 'lucide-react'
import { useNavigate } from 'react-router'

import { isApiError } from '@/api/http'
import type { ReservationResponse } from '@/api/types'
import { useAuth } from '@/auth/use-auth'
import { ConfirmDialog } from '@/components/skyline/confirm-dialog'
import { FlightTimeline, flightTimes, RouteLabel } from '@/components/skyline/flight'
import { PnrDisplay } from '@/components/skyline/pnr'
import { PriceTag } from '@/components/skyline/price-tag'
import { FlightStatusBadge, ReservationStatusBadge } from '@/components/skyline/status-badge'
import { Button } from '@/components/ui/button'
import { checkinWindow, isCancellable } from '@/lib/checkin-window'
import { cn } from '@/lib/cn'
import { formatLocal, formatLocalDate } from '@/lib/format'
import { cabinLabel } from '@/lib/labels'
import { CABIN_MULTIPLIER } from '@/lib/pricing'
import { useCancelReservation } from './queries'

function maskPassport(value: string) {
  return value.length <= 3 ? value : `${'•'.repeat(value.length - 3)}${value.slice(-3)}`
}

function Row({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="flex items-baseline justify-between gap-4 border-b border-border py-3 last:border-0">
      <dt className="text-sm text-muted-foreground">{label}</dt>
      <dd className="text-right font-medium tabular">{children}</dd>
    </div>
  )
}

/** Booking actions: Check in (window open), Boarding pass (checked in), Cancel (still cancellable). */
export function TripActions({
  reservation,
  className,
  onCancelled,
  size = 'default',
}: {
  reservation: ReservationResponse
  className?: string
  onCancelled?: () => void
  size?: 'default' | 'sm'
}) {
  const navigate = useNavigate()
  const { isAuthenticated } = useAuth()
  const [confirmOpen, setConfirmOpen] = React.useState(false)
  const cancel = useCancelReservation()
  const checkin = checkinWindow(reservation)
  const flight = reservation.flight
  const date = flight ? flightTimes(flight).departureDate : ''

  if (!isAuthenticated) return null

  return (
    <div className={cn('flex flex-wrap gap-2', className)}>
      {checkin.kind === 'open' && (
        <Button size={size} onClick={() => navigate(`/checkin/${reservation.pnr}`)}>
          <TicketCheck strokeWidth={1.75} aria-hidden />
          Check in
        </Button>
      )}
      {reservation.status === 'CHECKED_IN' && (
        <Button size={size} onClick={() => navigate(`/checkin/${reservation.pnr}`)}>
          <Ticket strokeWidth={1.75} aria-hidden />
          Boarding pass
        </Button>
      )}
      {isCancellable(reservation) && (
        <>
          <Button size={size} variant="destructive" onClick={() => setConfirmOpen(true)}>
            <X strokeWidth={1.75} aria-hidden />
            Cancel booking
          </Button>
          <ConfirmDialog
            open={confirmOpen}
            onOpenChange={(open) => {
              setConfirmOpen(open)
              if (!open) cancel.reset()
            }}
            title="Cancel this booking?"
            description={
              <>
                <p>
                  Flight <span className="font-mono font-semibold text-foreground">{flight?.flightNumber}</span>
                  {flight?.route && (
                    <>
                      {' '}
                      from {flight.route.origin.city} to {flight.route.destination.city}
                    </>
                  )}{' '}
                  on <span className="font-medium text-foreground">{date}</span>, booking{' '}
                  <span className="font-mono font-semibold text-foreground">{reservation.pnr}</span>.
                </p>
                <p>Your seat will be released. This can’t be undone.</p>
              </>
            }
            confirmLabel="Cancel booking"
            destructive
            loading={cancel.isPending}
            error={cancel.error ? (isApiError(cancel.error) ? cancel.error.message : 'Please try again.') : null}
            onConfirm={() =>
              cancel.mutate(reservation, {
                onSuccess: () => {
                  setConfirmOpen(false)
                  onCancelled?.()
                },
              })
            }
          />
        </>
      )}
    </div>
  )
}

/**
 * The full booking. `redacted` is the public PNR view: masked passport, no date of birth, no actions.
 */
export function ReservationView({ reservation, redacted }: { reservation: ReservationResponse; redacted?: boolean }) {
  const [revealed, setRevealed] = React.useState(false)
  const flight = reservation.flight
  const passenger = reservation.passenger
  const times = flight ? flightTimes(flight) : null

  const history = [
    { label: 'Booked', at: formatLocal(reservation.bookedAt), done: true },
    {
      label: 'Checked in',
      at: reservation.seatNumber ? `Seat ${reservation.seatNumber}` : 'Opens 48h before departure',
      done: reservation.status === 'CHECKED_IN',
    },
    ...(reservation.status === 'CANCELLED'
      ? [{ label: 'Cancelled', at: formatLocal(reservation.updatedAt), done: true }]
      : []),
  ]

  return (
    <div className="flex flex-col gap-6">
      <header className="flex flex-col gap-4">
        <div className="flex flex-wrap items-center gap-3">
          <ReservationStatusBadge status={reservation.status} />
          {flight && flight.status !== 'SCHEDULED' && <FlightStatusBadge status={flight.status} />}
        </div>
        <h1 className="text-h1 font-semibold sm:text-display-sm">
          {flight?.route ? (
            <RouteLabel route={flight.route} className="gap-3 font-display font-semibold" />
          ) : (
            'Your booking'
          )}
        </h1>
        {times && (
          <p className="text-muted-foreground">
            {times.departureDate} · {flight?.flightNumber}
          </p>
        )}
        <div className="flex flex-col gap-2">
          <span className="micro-label">Booking reference</span>
          <PnrDisplay pnr={reservation.pnr} />
        </div>
      </header>

      {flight ? (
        <section aria-label="Flight" className="rounded-card border border-border bg-card p-5 sm:p-8">
          <FlightTimeline flight={flight} size="detail" />
        </section>
      ) : (
        <p className="rounded-card border border-border bg-card p-6 text-muted-foreground">
          Flight details are unavailable right now. Please retry in a moment.
        </p>
      )}

      <div className="grid gap-6 lg:grid-cols-3">
        <section
          aria-labelledby="passenger"
          className="flex flex-col gap-2 rounded-card border border-border bg-card p-5 sm:p-6"
        >
          <h2 id="passenger" className="text-h3 font-semibold">
            Passenger
          </h2>
          <dl>
            <Row label="Name">
              {passenger.firstName} {passenger.lastName}
            </Row>
            <Row label="Passport">
              <span className="inline-flex items-center gap-1 font-mono">
                {redacted || revealed ? passenger.passportNumber : maskPassport(passenger.passportNumber)}
                {!redacted && (
                  <Button
                    variant="ghost"
                    size="icon-sm"
                    aria-label={revealed ? 'Hide passport number' : 'Show passport number'}
                    aria-pressed={revealed}
                    onClick={() => setRevealed((value) => !value)}
                  >
                    {revealed ? <EyeOff strokeWidth={1.75} aria-hidden /> : <Eye strokeWidth={1.75} aria-hidden />}
                  </Button>
                )}
              </span>
            </Row>
            {!redacted && passenger.dateOfBirth && (
              <Row label="Date of birth">{formatLocalDate(passenger.dateOfBirth)}</Row>
            )}
            <Row label="Nationality">{passenger.nationality}</Row>
          </dl>
        </section>

        <section
          aria-labelledby="price"
          className="flex flex-col gap-2 rounded-card border border-border bg-card p-5 sm:p-6"
        >
          <h2 id="price" className="text-h3 font-semibold">
            Price
          </h2>
          <dl>
            <Row label="Cabin">{cabinLabel[reservation.cabinClass]}</Row>
            <Row label="Cabin multiplier">× {CABIN_MULTIPLIER[reservation.cabinClass].toFixed(1)}</Row>
            <Row label="Seat">
              {reservation.seatNumber ?? <span className="text-muted-foreground">Assigned at check-in</span>}
            </Row>
          </dl>
          <PriceTag
            amount={reservation.totalPrice}
            currency={reservation.currency}
            size="large"
            label="Total paid"
            className="mt-2"
          />
        </section>

        <section
          aria-labelledby="history"
          className="flex flex-col gap-4 rounded-card border border-border bg-card p-5 sm:p-6"
        >
          <h2 id="history" className="text-h3 font-semibold">
            Status
          </h2>
          <ol className="flex flex-col gap-4">
            {history.map((step) => (
              <li key={step.label} className="flex gap-3">
                <span
                  aria-hidden
                  className={cn(
                    'mt-1.5 size-2.5 shrink-0 rounded-full',
                    step.done ? 'bg-foreground' : 'border border-muted-foreground',
                  )}
                />
                <span className="flex flex-col">
                  <span className={cn('font-medium', !step.done && 'text-muted-foreground')}>
                    {step.label}
                    <span className="sr-only">{step.done ? ' (done)' : ' (not yet)'}</span>
                  </span>
                  <span className="text-sm text-muted-foreground">{step.at}</span>
                </span>
              </li>
            ))}
          </ol>
        </section>
      </div>
    </div>
  )
}
