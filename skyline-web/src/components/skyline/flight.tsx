import type * as React from 'react'
import { ArrowRight } from 'lucide-react'

import type { FlightResponse, RouteResponse } from '@/api/types'
import { cn } from '@/lib/cn'
import { arrivalDayOffset, formatDateInZone, formatDuration, formatTimeInZone, minutesBetween } from '@/lib/format'
import { PlaneGlyph } from './brand'
import { FlightStatusBadge } from './status-badge'

/** "IST → LHR", optionally with the duration ("IST → LHR · 2h 50m"). */
export function RouteLabel({
  route,
  withDuration,
  className,
}: {
  route: Pick<RouteResponse, 'origin' | 'destination' | 'estimatedDurationMinutes'> | null | undefined
  withDuration?: boolean
  className?: string
}) {
  if (!route) return <span className={cn('text-muted-foreground', className)}>Route unavailable</span>
  return (
    <span className={cn('inline-flex items-center gap-1.5 font-mono font-medium', className)}>
      {route.origin.iataCode}
      <ArrowRight className="size-3.5" strokeWidth={1.75} aria-label="to" />
      {route.destination.iataCode}
      {withDuration && (
        <span className="font-sans font-normal text-muted-foreground">
          {' '}
          · {formatDuration(route.estimatedDurationMinutes)}
        </span>
      )}
    </span>
  )
}

/** Local times of a flight: departure in the origin's zone, arrival in the destination's. */
export function flightTimes(flight: Pick<FlightResponse, 'departureTime' | 'arrivalTime' | 'route'>) {
  const originZone = flight.route?.origin.timezone
  const destinationZone = flight.route?.destination.timezone
  return {
    departure: formatTimeInZone(flight.departureTime, originZone),
    arrival: formatTimeInZone(flight.arrivalTime, destinationZone),
    departureDate: formatDateInZone(flight.departureTime, originZone),
    arrivalDate: formatDateInZone(flight.arrivalTime, destinationZone),
    dayOffset: arrivalDayOffset(flight.departureTime, originZone, flight.arrivalTime, destinationZone),
    durationMinutes: flight.route?.estimatedDurationMinutes ?? minutesBetween(flight.departureTime, flight.arrivalTime),
    originZone,
    destinationZone,
  }
}

function TimeBlock({
  time,
  iata,
  sub,
  dayOffset,
  align,
  size,
}: {
  time: string
  iata: string
  sub?: React.ReactNode
  dayOffset?: number
  align: 'start' | 'end'
  size: 'card' | 'detail'
}) {
  return (
    <div className={cn('flex min-w-0 flex-col gap-1.5', align === 'end' && 'items-end text-right')}>
      <span
        className={cn(
          'relative hero-number',
          size === 'detail' ? 'text-[2.75rem] sm:text-display' : 'text-[2rem] sm:text-[2.5rem]',
        )}
      >
        {time}
        {!!dayOffset && (
          <sup
            className="absolute -top-1 -right-5 font-sans text-xs font-semibold tracking-normal text-muted-foreground"
            aria-label={`arrives ${dayOffset > 0 ? `${dayOffset} day later` : `${-dayOffset} day earlier`}`}
          >
            {dayOffset > 0 ? `+${dayOffset}` : dayOffset}
          </sup>
        )}
      </span>
      <span className="font-mono text-sm font-medium">{iata}</span>
      {sub && <span className="max-w-full truncate text-caption text-muted-foreground">{sub}</span>}
    </div>
  )
}

/**
 * Departure and arrival as hero numbers, joined by a hairline with the plane glyph and the duration.
 * Times are airport-local with the IATA code beneath; "+1" marks a later local arrival date.
 */
export function FlightTimeline({
  flight,
  size = 'card',
  className,
}: {
  flight: Pick<FlightResponse, 'departureTime' | 'arrivalTime' | 'route'>
  size?: 'card' | 'detail'
  className?: string
}) {
  const times = flightTimes(flight)
  const origin = flight.route?.origin
  const destination = flight.route?.destination
  return (
    <div className={cn('grid grid-cols-[auto_1fr_auto] items-start gap-4 sm:gap-6', className)}>
      <TimeBlock
        time={times.departure}
        iata={origin?.iataCode ?? '—'}
        sub={size === 'detail' && origin ? `${origin.city} · ${origin.name}` : undefined}
        align="start"
        size={size}
      />
      <div className={cn('flex flex-col items-center gap-1.5', size === 'detail' ? 'pt-5 sm:pt-7' : 'pt-3.5 sm:pt-4')}>
        <div className="flex w-full items-center gap-2 text-muted-foreground">
          <span className="size-1.5 shrink-0 rounded-full border border-current" aria-hidden />
          <span className="h-px flex-1 bg-border" aria-hidden />
          <PlaneGlyph className="size-4 shrink-0 rotate-45 text-foreground" />
          <span className="h-px flex-1 bg-border" aria-hidden />
          <span className="size-1.5 shrink-0 rounded-full bg-current" aria-hidden />
        </div>
        <span className="text-caption text-muted-foreground tabular">{formatDuration(times.durationMinutes)}</span>
      </div>
      <TimeBlock
        time={times.arrival}
        iata={destination?.iataCode ?? '—'}
        sub={size === 'detail' && destination ? `${destination.city} · ${destination.name}` : undefined}
        dayOffset={times.dayOffset}
        align="end"
        size={size}
      />
    </div>
  )
}

/** Result card: times, flight number, aircraft, seats left, price and the action. */
export function FlightCard({
  flight,
  price,
  action,
  className,
}: {
  flight: FlightResponse
  /** Usually a PriceTag with the estimate for the selected cabin. */
  price?: React.ReactNode
  action?: React.ReactNode
  className?: string
}) {
  const times = flightTimes(flight)
  return (
    <article
      className={cn('flex flex-col gap-5 rounded-card border border-border bg-card p-5 sm:p-6', className)}
      aria-label={`${flight.flightNumber}, departs ${times.departure} ${flight.route?.origin.iataCode ?? ''}, arrives ${times.arrival} ${flight.route?.destination.iataCode ?? ''}`}
    >
      <div className="flex flex-wrap items-center gap-x-3 gap-y-2 text-sm">
        <span className="font-mono font-semibold">{flight.flightNumber}</span>
        <span className="text-muted-foreground">
          {flight.aircraft
            ? `${flight.aircraft.aircraftType.manufacturer} ${flight.aircraft.aircraftType.model}`
            : 'Aircraft TBC'}
        </span>
        <span className="text-muted-foreground">· {times.departureDate}</span>
        {flight.status !== 'SCHEDULED' && <FlightStatusBadge status={flight.status} />}
        {flight.availableSeats > 0 && flight.availableSeats <= 20 && (
          <span className="ml-auto font-medium tabular">{flight.availableSeats} seats left</span>
        )}
      </div>
      <div className="flex flex-col gap-6 md:flex-row md:items-end md:gap-10">
        <FlightTimeline flight={flight} className="flex-1" />
        {(price || action) && (
          <div className="flex items-end justify-between gap-4 border-t border-border pt-5 md:flex-col md:items-end md:border-t-0 md:border-l md:pt-0 md:pl-10">
            {price}
            {action}
          </div>
        )}
      </div>
    </article>
  )
}
