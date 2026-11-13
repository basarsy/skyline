import { describe, expect, it } from 'vitest'

import { makeFlight, makeReservation } from '@/test/fixtures'
import { checkinWindow, isCancellable } from './checkin-window'
import { crewReadiness, nextStatuses } from './flight-status'
import {
  airportLocalToApi,
  apiToAirportLocal,
  arrivalDayOffset,
  formatDuration,
  formatMoney,
  formatTimeInZone,
  parseUtc,
  toApiDateTime,
} from './format'
import { hasAmbiguousCharacter, isValidPnr, normalizePnr } from './pnr'
import { estimatePrice, loadFactor } from './pricing'

describe('time contract', () => {
  it('parses offset-less API times as UTC, fractional seconds included', () => {
    expect(parseUtc('2026-12-01T14:30:00').toISOString()).toBe('2026-12-01T14:30:00.000Z')
    expect(parseUtc('2026-12-01T14:30:00.123456').toISOString()).toBe('2026-12-01T14:30:00.123Z')
    expect(parseUtc('2026-12-01T14:30:00Z').toISOString()).toBe('2026-12-01T14:30:00.000Z')
  })

  it('shows departure in the origin zone and arrival in the destination zone', () => {
    // 07:00 UTC: 10:00 in Istanbul (UTC+3), 07:00 in London in winter.
    expect(formatTimeInZone('2026-12-01T07:00:00', 'Europe/Istanbul')).toBe('10:00')
    expect(formatTimeInZone('2026-12-01T07:00:00', 'Europe/London')).toBe('07:00')
    expect(formatTimeInZone('2026-12-01T07:00:00', 'Not/AZone')).toBe('07:00')
  })

  it('flags an arrival on a later local date', () => {
    // Departs IST 23:30 local (20:30 UTC), lands LHR 01:30 local next day.
    expect(arrivalDayOffset('2026-12-01T20:30:00', 'Europe/Istanbul', '2026-12-02T01:30:00', 'Europe/London')).toBe(1)
    expect(arrivalDayOffset('2026-12-01T07:00:00', 'Europe/Istanbul', '2026-12-01T11:00:00', 'Europe/London')).toBe(0)
  })

  it('formats API date-times without Z or milliseconds', () => {
    expect(toApiDateTime(new Date('2026-12-01T14:30:45.678Z'))).toBe('2026-12-01T14:30:45')
  })

  it('converts airport-local input to UTC and back', () => {
    expect(airportLocalToApi('2026-12-01T10:00', 'Europe/Istanbul')).toBe('2026-12-01T07:00:00')
    expect(apiToAirportLocal('2026-12-01T07:00:00', 'Europe/Istanbul')).toBe('2026-12-01T10:00')
  })

  it('uses the right offset on each side of a DST change', () => {
    // Europe/London switches to BST (UTC+1) at 01:00 UTC on 28 March 2027.
    expect(airportLocalToApi('2027-03-27T09:00', 'Europe/London')).toBe('2027-03-27T09:00:00')
    expect(airportLocalToApi('2027-03-28T09:00', 'Europe/London')).toBe('2027-03-28T08:00:00')
    // And back to GMT on 31 October 2027.
    expect(airportLocalToApi('2027-10-30T09:00', 'America/New_York')).toBe('2027-10-30T13:00:00')
    expect(airportLocalToApi('2027-11-08T09:00', 'America/New_York')).toBe('2027-11-08T14:00:00')
    // Round trip across the change.
    for (const local of ['2027-03-27T23:30', '2027-03-28T02:30', '2027-10-31T00:30']) {
      expect(apiToAirportLocal(airportLocalToApi(local, 'Europe/London'), 'Europe/London')).toBe(local)
    }
  })

  it('formats durations and money', () => {
    expect(formatDuration(455)).toBe('7h 35m')
    expect(formatDuration(180)).toBe('3h')
    expect(formatDuration(45)).toBe('45m')
    expect(formatMoney(1234.5, 'EUR', 'en-GB')).toBe('€1,234.50')
    expect(formatMoney(99, 'TRY', 'en-GB')).toContain('99.00')
  })
})

describe('pricing preview', () => {
  const aircraft = makeFlight().aircraft
  it('multiplies by cabin and load', () => {
    expect(estimatePrice(makeFlight({ basePrice: 100, availableSeats: 150 }), 'BUSINESS')).toMatchObject({
      amount: 250,
      surchargePercent: 0,
    })
    // 180 seats, 30 left: 83% full → +15%
    expect(estimatePrice(makeFlight({ basePrice: 100, availableSeats: 30, aircraft }), 'ECONOMY')).toMatchObject({
      amount: 115,
      surchargePercent: 15,
    })
    // 10 left: 94% full → +30%
    expect(estimatePrice(makeFlight({ basePrice: 100, availableSeats: 10, aircraft }), 'FIRST').amount).toBe(520)
  })

  it('falls back to max(availableSeats, 180) seats without an aircraft', () => {
    expect(loadFactor(makeFlight({ aircraft: null, availableSeats: 90 }))).toBeCloseTo(0.5)
    expect(loadFactor(makeFlight({ aircraft: null, availableSeats: 250 }))).toBe(0)
  })
})

describe('check-in window', () => {
  const at = (departureTime: string) => makeReservation({ flight: makeFlight({ departureTime }) })
  const now = new Date('2026-12-01T12:00:00Z')

  it('opens 48h and closes 1h before departure', () => {
    expect(checkinWindow(at('2026-12-03T12:01:00'), now).kind).toBe('not-open')
    expect(checkinWindow(at('2026-12-03T12:00:00'), now).kind).toBe('open')
    expect(checkinWindow(at('2026-12-01T13:00:00'), now).kind).toBe('open')
    expect(checkinWindow(at('2026-12-01T12:59:00'), now).kind).toBe('closed')
  })

  it('knows checked-in and ineligible bookings', () => {
    expect(checkinWindow(makeReservation({ status: 'CHECKED_IN' }), now).kind).toBe('checked-in')
    expect(checkinWindow(makeReservation({ status: 'CANCELLED' }), now).kind).toBe('ineligible')
  })

  it('allows cancelling only while the flight is scheduled or delayed', () => {
    expect(isCancellable(makeReservation())).toBe(true)
    expect(isCancellable(makeReservation({ flight: makeFlight({ status: 'BOARDING' }) }))).toBe(false)
    expect(isCancellable(makeReservation({ status: 'CANCELLED' }))).toBe(false)
  })
})

describe('flight status machine', () => {
  it('offers only allowed transitions, never CANCELLED', () => {
    expect(nextStatuses('SCHEDULED')).toEqual(['BOARDING', 'DELAYED'])
    expect(nextStatuses('DEPARTED')).toEqual(['ARRIVED'])
    expect(nextStatuses('ARRIVED')).toEqual([])
  })

  it('checks crew readiness', () => {
    expect(crewReadiness([{ assignedRole: 'CAPTAIN' }])).toEqual({ captain: true, firstOfficer: false, ready: false })
    expect(crewReadiness([{ assignedRole: 'CAPTAIN' }, { assignedRole: 'FIRST_OFFICER' }]).ready).toBe(true)
  })
})

describe('PNR input', () => {
  it('normalizes and validates', () => {
    expect(normalizePnr(' k7q 2mx ')).toBe('K7Q2MX')
    expect(isValidPnr('K7Q2MX')).toBe(true)
    expect(isValidPnr('K7Q2M0')).toBe(false)
    expect(hasAmbiguousCharacter('ab1')).toBe(true)
    expect(hasAmbiguousCharacter('K7Q2MX')).toBe(false)
  })
})
