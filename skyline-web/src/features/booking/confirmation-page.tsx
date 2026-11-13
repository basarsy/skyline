import { motion, useReducedMotion } from 'motion/react'
import { CalendarPlus, Clock3 } from 'lucide-react'
import { Link, useParams } from 'react-router'

import { Page } from '@/components/layout/app-shell'
import { CountdownTimer } from '@/components/skyline/countdown'
import { FlightTimeline, flightTimes } from '@/components/skyline/flight'
import { PnrDisplay } from '@/components/skyline/pnr'
import { PriceTag } from '@/components/skyline/price-tag'
import { ErrorState, LoadingRegion, SkeletonCard } from '@/components/skyline/states'
import { Button } from '@/components/ui/button'
import { checkinWindow } from '@/lib/checkin-window'
import { useDocumentTitle } from '@/lib/hooks'
import { downloadIcs } from '@/lib/ics'
import { cabinLabel } from '@/lib/labels'
import { useReservation } from '@/features/trips/queries'

/** A drawn check mark; with reduced motion it simply appears. */
function SuccessMark() {
  const reduce = useReducedMotion()
  return (
    <motion.svg
      viewBox="0 0 72 72"
      className="size-[72px] text-foreground"
      aria-hidden
      initial={reduce ? false : { scale: 0.8, opacity: 0 }}
      animate={{ scale: 1, opacity: 1 }}
      transition={{ type: 'spring', stiffness: 320, damping: 22 }}
    >
      <circle cx="36" cy="36" r="34" fill="none" stroke="currentColor" strokeWidth="2" opacity="0.15" />
      <motion.path
        d="M22 37.5 31.5 47 50 27"
        fill="none"
        stroke="currentColor"
        strokeWidth="4"
        strokeLinecap="round"
        strokeLinejoin="round"
        initial={reduce ? false : { pathLength: 0 }}
        animate={{ pathLength: 1 }}
        transition={{ duration: 0.45, delay: 0.15, ease: 'easeOut' }}
      />
    </motion.svg>
  )
}

function CheckinInfo({ reservation }: { reservation: Parameters<typeof checkinWindow>[0] & { pnr: string } }) {
  const state = checkinWindow(reservation)
  return (
    <div className="flex flex-col gap-4 rounded-card border border-border bg-card p-5 sm:flex-row sm:items-center sm:justify-between sm:p-6">
      <div className="flex items-start gap-3">
        <Clock3 className="mt-0.5 size-5 shrink-0" strokeWidth={1.75} aria-hidden />
        <div className="flex flex-col gap-1">
          <span className="font-medium">
            {state.kind === 'not-open' && (
              <>
                Check-in opens in <CountdownTimer target={state.opensAt} />
              </>
            )}
            {state.kind === 'open' && 'Online check-in is open'}
            {state.kind === 'closed' && 'Online check-in has closed'}
            {(state.kind === 'checked-in' || state.kind === 'ineligible') && 'Check-in'}
          </span>
          <span className="text-sm text-muted-foreground">
            Check-in opens 48 hours and closes 1 hour before departure. Your seat is assigned at check-in.
          </span>
        </div>
      </div>
      {state.kind === 'open' && (
        <Button asChild>
          <Link to={`/checkin/${reservation.pnr}`}>Check in</Link>
        </Button>
      )}
    </div>
  )
}

export function Component() {
  const { reservationId } = useParams()
  const { data: reservation, isPending, isError, error, refetch } = useReservation(reservationId)
  useDocumentTitle('Booking confirmed')

  if (isPending) {
    return (
      <Page>
        <LoadingRegion label="Loading your booking">
          <SkeletonCard className="h-96" lines={4} />
        </LoadingRegion>
      </Page>
    )
  }
  if (isError) {
    return (
      <Page>
        <ErrorState error={error} onRetry={() => void refetch()} />
      </Page>
    )
  }

  const flight = reservation.flight
  return (
    <Page className="flex max-w-[880px] flex-col gap-8">
      <div className="flex flex-col items-start gap-6">
        <SuccessMark />
        <div className="flex flex-col gap-2">
          <h1 className="text-h1 font-semibold sm:text-display-sm">You’re booked.</h1>
          <p className="text-muted-foreground">
            {reservation.passenger.firstName} {reservation.passenger.lastName} · {cabinLabel[reservation.cabinClass]}
            {flight && ` · ${flightTimes(flight).departureDate}`}
          </p>
        </div>
        <div className="flex flex-col gap-2">
          <span className="micro-label">Booking reference</span>
          <PnrDisplay pnr={reservation.pnr} />
        </div>
      </div>

      <div className="grid gap-4 sm:grid-cols-[1fr_auto]">
        {flight && (
          <div className="rounded-card border border-border bg-card p-5 sm:p-6">
            <FlightTimeline flight={flight} />
          </div>
        )}
        <div className="flex flex-col justify-center rounded-card border border-border bg-card p-5 sm:p-6">
          <PriceTag amount={reservation.totalPrice} currency={reservation.currency} size="large" label="Total" />
        </div>
      </div>

      <CheckinInfo reservation={reservation} />

      <div className="flex flex-wrap gap-3">
        <Button asChild size="lg">
          <Link to={`/trips/${reservation.id}`}>View trip</Link>
        </Button>
        {flight && (
          <Button size="lg" variant="outline" onClick={() => downloadIcs(reservation)}>
            <CalendarPlus strokeWidth={1.75} aria-hidden />
            Add to calendar
          </Button>
        )}
      </div>
    </Page>
  )
}
