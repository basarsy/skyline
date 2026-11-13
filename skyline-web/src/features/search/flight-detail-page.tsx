import * as React from 'react'
import { ArrowLeft, Info } from 'lucide-react'
import { Link, useNavigate, useParams } from 'react-router'

import { isApiError } from '@/api/http'
import type { CabinClass } from '@/api/types'
import { useAuth } from '@/auth/use-auth'
import { Page } from '@/components/layout/app-shell'
import { FlightTimeline, flightTimes } from '@/components/skyline/flight'
import { PriceTag } from '@/components/skyline/price-tag'
import { EmptyState, ErrorState, LoadingRegion, SkeletonCard } from '@/components/skyline/states'
import { FlightStatusBadge } from '@/components/skyline/status-badge'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/cn'
import { formatDuration } from '@/lib/format'
import { isBookable } from '@/lib/flight-status'
import { useDocumentTitle } from '@/lib/hooks'
import { cabinLabel, CABINS, flightStatusLabel } from '@/lib/labels'
import { cabinMultiplierCopy, estimatePrice } from '@/lib/pricing'
import { useFlight } from './queries'

function Fact({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div className="flex flex-col gap-1">
      <dt className="micro-label">{label}</dt>
      <dd className="font-medium tabular">{value}</dd>
    </div>
  )
}

export function Component() {
  const { id } = useParams()
  const navigate = useNavigate()
  const { isStaff } = useAuth()
  const { data: flight, isPending, isError, error, refetch } = useFlight(id)
  const [cabin, setCabin] = React.useState<CabinClass>('ECONOMY')
  useDocumentTitle(flight ? `Flight ${flight.flightNumber}` : 'Flight')

  if (isPending) {
    return (
      <Page>
        <LoadingRegion label="Loading flight">
          <SkeletonCard className="h-80" lines={4} />
        </LoadingRegion>
      </Page>
    )
  }
  if (isError) {
    return (
      <Page>
        {isApiError(error) && error.status === 404 ? (
          <EmptyState
            title="We couldn’t find this flight"
            description="It may have been removed, or the link is wrong."
            action={
              <Button asChild>
                <Link to="/">Search flights</Link>
              </Button>
            }
          />
        ) : (
          <ErrorState error={error} onRetry={() => void refetch()} />
        )}
      </Page>
    )
  }

  const times = flightTimes(flight)
  const bookable = isBookable(flight)
  const route = flight.route

  return (
    <Page className="flex flex-col gap-6 sm:gap-8">
      <Button variant="ghost" size="sm" className="self-start" onClick={() => navigate(-1)}>
        <ArrowLeft strokeWidth={1.75} aria-hidden />
        Back
      </Button>

      <header className="flex flex-col gap-3">
        <div className="flex flex-wrap items-center gap-3">
          <span className="font-mono text-sm font-semibold">{flight.flightNumber}</span>
          <FlightStatusBadge
            status={flight.status}
            className={cn(flight.status === 'DELAYED' && 'px-3 py-1.5 text-sm')}
          />
        </div>
        <h1 className="text-h1 font-semibold sm:text-display-sm">
          {route ? `${route.origin.city} to ${route.destination.city}` : 'Flight details'}
        </h1>
        <p className="text-muted-foreground">{times.departureDate} · times are local to each airport</p>
      </header>

      {!bookable && (
        <div role="status" className="flex items-start gap-3 rounded-2xl border border-border bg-elevated p-4">
          <Info className="mt-0.5 size-[18px] shrink-0" strokeWidth={1.75} aria-hidden />
          <p className="text-sm">
            {flight.availableSeats <= 0 && (flight.status === 'SCHEDULED' || flight.status === 'DELAYED')
              ? 'This flight is fully booked.'
              : `This flight is ${flightStatusLabel[flight.status].toLowerCase()}, so it can’t be booked.`}
          </p>
        </div>
      )}

      <section aria-label="Timeline" className="rounded-card border border-border bg-card p-5 sm:p-8">
        <FlightTimeline flight={flight} size="detail" />
        <dl className="mt-8 grid grid-cols-2 gap-6 border-t border-border pt-6 sm:grid-cols-4">
          <Fact label="Duration" value={formatDuration(times.durationMinutes)} />
          <Fact label="Distance" value={route ? `${route.distanceKm.toLocaleString()} km` : '—'} />
          <Fact
            label="Aircraft"
            value={
              flight.aircraft ? (
                <>
                  {flight.aircraft.aircraftType.manufacturer} {flight.aircraft.aircraftType.model}{' '}
                  <span className="font-mono text-sm text-muted-foreground">{flight.aircraft.tailNumber}</span>
                </>
              ) : (
                'To be confirmed'
              )
            }
          />
          <Fact label="Seats left" value={flight.availableSeats} />
        </dl>
      </section>

      <section aria-labelledby="cabins" className="flex flex-col gap-4">
        <h2 id="cabins" className="text-h2 font-semibold">
          Cabins
        </h2>
        <div role="radiogroup" aria-label="Cabin" className="grid gap-3 sm:grid-cols-3">
          {CABINS.map((option) => {
            const estimate = estimatePrice(flight, option)
            const active = option === cabin
            return (
              <button
                key={option}
                type="button"
                role="radio"
                aria-checked={active}
                onClick={() => setCabin(option)}
                className={cn(
                  'flex cursor-pointer flex-col gap-4 rounded-card border p-5 text-left focus-ring transition-colors duration-200',
                  active ? 'border-primary bg-card ring-1 ring-primary' : 'border-border bg-card hover:bg-elevated',
                )}
              >
                <span className="flex items-center justify-between">
                  <span className="text-lg font-semibold">{cabinLabel[option]}</span>
                  <span
                    aria-hidden
                    className={cn(
                      'size-5 rounded-full border-2',
                      active ? 'border-[6px] border-primary' : 'border-border',
                    )}
                  />
                </span>
                <span className="text-sm text-muted-foreground">{cabinMultiplierCopy(option)}</span>
                <PriceTag amount={estimate.amount} currency={flight.currency} size="large" label="Estimated" />
                {estimate.surchargePercent > 0 && (
                  <span className="text-caption text-muted-foreground">
                    High demand · +{estimate.surchargePercent}%
                  </span>
                )}
              </button>
            )
          })}
        </div>
        <p className="text-sm text-muted-foreground">Your seat is assigned at check-in.</p>
      </section>

      <div className="flex flex-wrap gap-3">
        {isStaff ? (
          <Button asChild size="lg">
            <Link to={`/ops/flights/${flight.id}`}>View in ops</Link>
          </Button>
        ) : (
          <Button size="lg" disabled={!bookable} onClick={() => navigate(`/book/${flight.id}?cabin=${cabin}`)}>
            Book {cabinLabel[cabin]}
          </Button>
        )}
      </div>
    </Page>
  )
}
