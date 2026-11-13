import * as React from 'react'
import { Plane } from 'lucide-react'
import { Link, useSearchParams } from 'react-router'

import type { ReservationResponse } from '@/api/types'
import { Page } from '@/components/layout/app-shell'
import { flightTimes } from '@/components/skyline/flight'
import { SegmentedControl } from '@/components/skyline/segmented-control'
import { EmptyState, ErrorState, LoadingRegion, SkeletonCard, Stagger } from '@/components/skyline/states'
import { ReservationStatusBadge } from '@/components/skyline/status-badge'
import { Button } from '@/components/ui/button'
import { parseUtc } from '@/lib/format'
import { useDocumentTitle } from '@/lib/hooks'
import { useMyReservations } from './queries'
import { TripActions } from './reservation-view'

type Tab = 'upcoming' | 'past' | 'cancelled'
const TABS: { value: Tab; label: string }[] = [
  { value: 'upcoming', label: 'Upcoming' },
  { value: 'past', label: 'Past' },
  { value: 'cancelled', label: 'Cancelled' },
]

/** Split by status and departure time (UTC instants, so the browser's zone doesn't matter). */
export function tripTab(reservation: ReservationResponse, now = Date.now()): Tab {
  if (reservation.status === 'CANCELLED') return 'cancelled'
  const flight = reservation.flight
  if (!flight) return 'upcoming'
  if (flight.status === 'ARRIVED' || parseUtc(flight.arrivalTime).getTime() < now) return 'past'
  return 'upcoming'
}

function TripCard({ reservation }: { reservation: ReservationResponse }) {
  const flight = reservation.flight
  const times = flight ? flightTimes(flight) : null
  return (
    <article className="flex flex-col gap-5 rounded-card border border-border bg-card p-5 sm:p-6">
      <div className="flex items-start justify-between gap-4">
        <Link
          to={`/trips/${reservation.id}`}
          className="group -m-1 flex flex-col gap-2 rounded-2xl p-1 focus-ring"
          aria-label={`Trip ${flight?.route ? `${flight.route.origin.iataCode} to ${flight.route.destination.iataCode}` : ''}, booking ${reservation.pnr}`}
        >
          <span className="flex items-center gap-3 hero-number text-[2rem] sm:text-[2.5rem]">
            {flight?.route?.origin.iataCode ?? '—'}
            <Plane className="size-5 text-muted-foreground" strokeWidth={1.75} aria-hidden />
            {flight?.route?.destination.iataCode ?? '—'}
          </span>
          <span className="text-sm text-muted-foreground group-hover:text-foreground">
            {times ? `${times.departureDate} · ${times.departure}` : 'Flight details unavailable'}
            {flight && <span className="ml-2 font-mono">{flight.flightNumber}</span>}
          </span>
        </Link>
        <ReservationStatusBadge status={reservation.status} />
      </div>
      <dl className="grid grid-cols-2 gap-4 text-sm sm:grid-cols-3">
        <div className="flex flex-col gap-1">
          <dt className="micro-label">Booking</dt>
          <dd className="font-mono font-medium">{reservation.pnr}</dd>
        </div>
        <div className="flex flex-col gap-1">
          <dt className="micro-label">Seat</dt>
          <dd className="font-medium">
            {reservation.seatNumber ?? <span className="text-muted-foreground">Assigned at check-in</span>}
          </dd>
        </div>
        <div className="flex flex-col gap-1">
          <dt className="micro-label">Passenger</dt>
          <dd className="truncate font-medium">
            {reservation.passenger.firstName} {reservation.passenger.lastName}
          </dd>
        </div>
      </dl>
      <TripActions reservation={reservation} size="sm" />
    </article>
  )
}

export function Component() {
  useDocumentTitle('My trips')
  const [params, setParams] = useSearchParams()
  const tab: Tab = TABS.some((t) => t.value === params.get('tab')) ? (params.get('tab') as Tab) : 'upcoming'
  const { data, isPending, isError, error, refetch } = useMyReservations()

  const trips = React.useMemo(() => {
    const list = (data?.content ?? []).filter((r) => tripTab(r) === tab)
    // Upcoming: soonest first; past and cancelled: most recent first.
    return list.sort((a, b) => {
      const da = a.flight?.departureTime ?? a.bookedAt
      const db = b.flight?.departureTime ?? b.bookedAt
      return tab === 'upcoming' ? da.localeCompare(db) : db.localeCompare(da)
    })
  }, [data, tab])

  return (
    <Page className="flex flex-col gap-6 sm:gap-8">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <h1 className="text-h1 font-semibold">My trips</h1>
        <SegmentedControl
          label="Show trips"
          value={tab}
          onValueChange={(value) => setParams({ tab: value }, { replace: true })}
          options={TABS}
          className="w-full sm:w-auto"
        />
      </div>

      {isPending ? (
        <LoadingRegion label="Loading your trips">
          <div className="flex flex-col gap-3">
            {[0, 1, 2].map((i) => (
              <SkeletonCard key={i} className="h-[236px]" lines={2} />
            ))}
          </div>
        </LoadingRegion>
      ) : isError ? (
        <ErrorState error={error} onRetry={() => void refetch()} title="We couldn’t load your trips" />
      ) : trips.length === 0 ? (
        <EmptyState
          icon={<Plane strokeWidth={1.75} aria-hidden />}
          title={
            tab === 'upcoming' ? 'No upcoming trips' : tab === 'past' ? 'No past trips yet' : 'No cancelled bookings'
          }
          description={tab === 'upcoming' ? 'When you book a flight, it shows up here.' : undefined}
          action={
            tab === 'upcoming' && (
              <Button asChild>
                <Link to="/">Search flights</Link>
              </Button>
            )
          }
        />
      ) : (
        <div className="flex flex-col gap-3">
          {trips.map((reservation, index) => (
            <Stagger key={reservation.id} index={index}>
              <TripCard reservation={reservation} />
            </Stagger>
          ))}
          {data && data.totalElements > data.content.length && (
            <p className="text-center text-sm text-muted-foreground">
              Showing your {data.content.length} most recent bookings.
            </p>
          )}
        </div>
      )}
    </Page>
  )
}
