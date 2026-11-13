import type { ReservationResponse } from '@/api/types'
import { parseUtc } from './format'

export const CHECKIN_OPENS_HOURS = 48
export const CHECKIN_CLOSES_HOURS = 1

export type CheckinWindowState =
  | { kind: 'not-open'; opensAt: Date; closesAt: Date }
  | { kind: 'open'; opensAt: Date; closesAt: Date }
  | { kind: 'closed'; opensAt: Date; closesAt: Date }
  | { kind: 'checked-in' }
  | { kind: 'ineligible'; reason: string }

/**
 * Where a reservation stands relative to online check-in (48h → 1h before departure). Compares UTC
 * instants, so it's right in any browser zone. Guidance only: the server's clock decides, and a 422 from
 * the server is shown as returned.
 */
export function checkinWindow(
  reservation: Pick<ReservationResponse, 'status' | 'flight'>,
  now: Date = new Date(),
): CheckinWindowState {
  if (reservation.status === 'CHECKED_IN') return { kind: 'checked-in' }
  if (reservation.status !== 'CONFIRMED') {
    return {
      kind: 'ineligible',
      reason:
        reservation.status === 'CANCELLED'
          ? 'This booking was cancelled, so it can’t be checked in.'
          : 'This booking isn’t confirmed yet, so it can’t be checked in.',
    }
  }
  const flight = reservation.flight
  if (!flight) return { kind: 'ineligible', reason: 'Flight details are unavailable right now. Please retry.' }
  if (flight.status === 'CANCELLED') {
    return { kind: 'ineligible', reason: 'This flight was cancelled.' }
  }

  const departure = parseUtc(flight.departureTime).getTime()
  const opensAt = new Date(departure - CHECKIN_OPENS_HOURS * 3_600_000)
  const closesAt = new Date(departure - CHECKIN_CLOSES_HOURS * 3_600_000)
  const t = now.getTime()

  if (t < opensAt.getTime()) return { kind: 'not-open', opensAt, closesAt }
  if (t > closesAt.getTime()) return { kind: 'closed', opensAt, closesAt }
  return { kind: 'open', opensAt, closesAt }
}

export function isCheckinOpen(reservation: Pick<ReservationResponse, 'status' | 'flight'>, now?: Date) {
  return checkinWindow(reservation, now).kind === 'open'
}

/** A booking can be cancelled while it isn't cancelled and its flight is still SCHEDULED or DELAYED. */
export function isCancellable(reservation: Pick<ReservationResponse, 'status' | 'flight'>) {
  const status = reservation.flight?.status
  return reservation.status !== 'CANCELLED' && (status === 'SCHEDULED' || status === 'DELAYED')
}
