import type { ReservationResponse } from '@/api/types'
import { parseUtc } from './format'

/** RFC 5545 UTC date-time: 20261201T143000Z. */
function icsDate(date: Date) {
  return date
    .toISOString()
    .replace(/[-:]/g, '')
    .replace(/\.\d{3}/, '')
}

function escape(text: string) {
  return text.replace(/\\/g, '\\\\').replace(/;/g, '\\;').replace(/,/g, '\\,').replace(/\n/g, '\\n')
}

/** A calendar event for the flight; times are UTC instants, so any calendar shows them correctly. */
export function reservationToIcs(reservation: ReservationResponse, now = new Date()) {
  const flight = reservation.flight
  if (!flight) throw new Error('Flight details are unavailable')
  const route = flight.route
  const summary = route
    ? `${flight.flightNumber} ${route.origin.iataCode} → ${route.destination.iataCode}`
    : `Flight ${flight.flightNumber}`
  const description = [
    `Booking reference: ${reservation.pnr}`,
    `Passenger: ${reservation.passenger.firstName} ${reservation.passenger.lastName}`,
    'Online check-in opens 48 hours before departure.',
  ].join('\n')

  return [
    'BEGIN:VCALENDAR',
    'VERSION:2.0',
    'PRODID:-//Skyline//Booking//EN',
    'CALSCALE:GREGORIAN',
    'METHOD:PUBLISH',
    'BEGIN:VEVENT',
    `UID:${reservation.id}@skyline`,
    `DTSTAMP:${icsDate(now)}`,
    `DTSTART:${icsDate(parseUtc(flight.departureTime))}`,
    `DTEND:${icsDate(parseUtc(flight.arrivalTime))}`,
    `SUMMARY:${escape(summary)}`,
    `DESCRIPTION:${escape(description)}`,
    ...(route ? [`LOCATION:${escape(`${route.origin.name} (${route.origin.iataCode})`)}`] : []),
    'END:VEVENT',
    'END:VCALENDAR',
    '',
  ].join('\r\n')
}

export function downloadIcs(reservation: ReservationResponse) {
  const blob = new Blob([reservationToIcs(reservation)], { type: 'text/calendar;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `skyline-${reservation.pnr}.ics`
  link.click()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}
