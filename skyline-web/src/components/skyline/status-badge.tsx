import type { AircraftStatus, FlightStatus, ReservationStatus } from '@/api/types'
import { Badge } from '@/components/ui/badge'
import { cn } from '@/lib/cn'
import { aircraftStatusLabel, flightStatusLabel, reservationStatusLabel } from '@/lib/labels'

type Tone = 'neutral' | 'success' | 'warning' | 'destructive' | 'inverted' | 'muted'

function Dot({ tone }: { tone: Tone }) {
  return (
    <span
      aria-hidden
      className={cn(
        'size-1.5 shrink-0 rounded-full',
        tone === 'success' && 'bg-success',
        tone === 'warning' && 'bg-warning',
        tone === 'destructive' && 'bg-destructive',
        tone === 'inverted' && 'bg-background',
        tone === 'neutral' && 'bg-foreground',
        tone === 'muted' && 'bg-muted-foreground',
      )}
    />
  )
}

function StatusPill({ tone, label, className }: { tone: Tone; label: string; className?: string }) {
  return (
    <Badge variant={tone === 'inverted' ? 'inverted' : 'default'} className={className}>
      <Dot tone={tone} />
      {label}
    </Badge>
  )
}

const FLIGHT_TONE: Record<FlightStatus, Tone> = {
  SCHEDULED: 'neutral',
  DELAYED: 'warning',
  BOARDING: 'neutral',
  DEPARTED: 'muted',
  ARRIVED: 'muted',
  CANCELLED: 'destructive',
}

const RESERVATION_TONE: Record<ReservationStatus, Tone> = {
  PENDING: 'muted',
  CONFIRMED: 'success',
  CHECKED_IN: 'success',
  CANCELLED: 'destructive',
}

const AIRCRAFT_TONE: Record<AircraftStatus, Tone> = {
  ACTIVE: 'success',
  MAINTENANCE: 'warning',
  RETIRED: 'muted',
}

/**
 * Status as a monochrome pill with a dot and a text label, so it reads without color. Only
 * CONFIRMED/CHECKED_IN use the success hue, DELAYED/MAINTENANCE the warning hue (a dot only), and
 * CANCELLED the destructive one.
 */
export function FlightStatusBadge({ status, className }: { status: FlightStatus; className?: string }) {
  return <StatusPill tone={FLIGHT_TONE[status]} label={flightStatusLabel[status]} className={className} />
}

export function ReservationStatusBadge({ status, className }: { status: ReservationStatus; className?: string }) {
  return <StatusPill tone={RESERVATION_TONE[status]} label={reservationStatusLabel[status]} className={className} />
}

export function AircraftStatusBadge({ status, className }: { status: AircraftStatus; className?: string }) {
  return <StatusPill tone={AIRCRAFT_TONE[status]} label={aircraftStatusLabel[status]} className={className} />
}
