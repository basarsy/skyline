import { differenceInCalendarDays, format, isValid, parseISO } from 'date-fns'
import { formatInTimeZone, fromZonedTime } from 'date-fns-tz'

/**
 * Time contract: every LocalDateTime from the API is UTC without an offset ("2026-12-01T14:30:00").
 * Flight times are shown in the airport's zone; other timestamps in the browser's zone.
 */

const HAS_OFFSET = /(Z|[+-]\d{2}:?\d{2})$/i

/** Parses an API LocalDateTime as UTC. */
export function parseUtc(value: string): Date {
  const trimmed = value.trim()
  return parseISO(HAS_OFFSET.test(trimmed) ? trimmed : `${trimmed}Z`)
}

/** A zone that Intl can use; falls back to UTC for null or unknown ids. */
export function safeZone(zone: string | null | undefined): string {
  if (!zone) return 'UTC'
  try {
    new Intl.DateTimeFormat('en', { timeZone: zone })
    return zone
  } catch {
    return 'UTC'
  }
}

/** "14:30" in the given zone. */
export function formatTimeInZone(utc: string, zone: string | null | undefined) {
  return formatInTimeZone(parseUtc(utc), safeZone(zone), 'HH:mm')
}

/** "Tue 1 Dec" in the given zone. */
export function formatDateInZone(utc: string, zone: string | null | undefined, pattern = 'EEE d MMM') {
  return formatInTimeZone(parseUtc(utc), safeZone(zone), pattern)
}

/** "Tue 1 Dec 2026, 14:30" in the given zone. */
export function formatDateTimeInZone(utc: string, zone: string | null | undefined) {
  return formatInTimeZone(parseUtc(utc), safeZone(zone), 'EEE d MMM yyyy, HH:mm')
}

/** A timestamp that isn't a flight time (bookedAt, issuedAt): the browser's own zone. */
export function formatLocal(utc: string, pattern = 'd MMM yyyy, HH:mm') {
  return format(parseUtc(utc), pattern)
}

/**
 * Calendar days between departure and arrival, each in its own airport's zone: 1 for an overnight flight
 * that lands "tomorrow" locally (shown as a +1 superscript), 0 for the same day, -1 when crossing west.
 */
export function arrivalDayOffset(
  departureUtc: string,
  departureZone: string | null | undefined,
  arrivalUtc: string,
  arrivalZone: string | null | undefined,
) {
  const departureDay = formatInTimeZone(parseUtc(departureUtc), safeZone(departureZone), 'yyyy-MM-dd')
  const arrivalDay = formatInTimeZone(parseUtc(arrivalUtc), safeZone(arrivalZone), 'yyyy-MM-dd')
  return differenceInCalendarDays(parseISO(arrivalDay), parseISO(departureDay))
}

/** 455 → "7h 35m"; 180 → "3h"; 45 → "45m". */
export function formatDuration(minutes: number) {
  const safe = Math.max(0, Math.round(minutes))
  const hours = Math.floor(safe / 60)
  const rest = safe % 60
  if (hours === 0) return `${rest}m`
  return rest === 0 ? `${hours}h` : `${hours}h ${rest}m`
}

/** Minutes between two API timestamps. */
export function minutesBetween(fromUtc: string, toUtc: string) {
  return Math.round((parseUtc(toUtc).getTime() - parseUtc(fromUtc).getTime()) / 60_000)
}

const moneyFormatters = new Map<string, Intl.NumberFormat>()

/** Amount in the response's currency, always 2 decimals. */
export function formatMoney(amount: number, currency: string | null | undefined, locale?: string) {
  const code = currency && /^[A-Z]{3}$/.test(currency) ? currency : 'EUR'
  const key = `${locale ?? ''}|${code}`
  let formatter = moneyFormatters.get(key)
  if (!formatter) {
    formatter = new Intl.NumberFormat(locale, {
      style: 'currency',
      currency: code,
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    })
    moneyFormatters.set(key, formatter)
  }
  return formatter.format(amount)
}

/**
 * A "from" price in whole units ("€120"), rounded up so it never shows less than the passenger would pay.
 */
export function formatMoneyFrom(amount: number, currency: string | null | undefined, locale?: string) {
  const code = currency && /^[A-Z]{3}$/.test(currency) ? currency : 'EUR'
  return new Intl.NumberFormat(locale, { style: 'currency', currency: code, maximumFractionDigits: 0 }).format(
    Math.ceil(amount),
  )
}

/** The same amount split for hero display: symbol/sign parts and digits rendered separately. */
export function moneyParts(amount: number, currency: string | null | undefined, locale?: string) {
  const code = currency && /^[A-Z]{3}$/.test(currency) ? currency : 'EUR'
  return new Intl.NumberFormat(locale, {
    style: 'currency',
    currency: code,
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).formatToParts(amount)
}

/** Date → "yyyy-MM-dd'T'HH:mm:ss" in UTC, with no Z and no milliseconds (toISOString adds both). */
export function toApiDateTime(date: Date) {
  return formatInTimeZone(date, 'UTC', "yyyy-MM-dd'T'HH:mm:ss")
}

/**
 * An airport-local wall time from a datetime-local input ("2027-03-28T09:15") → the UTC string the API
 * expects. DST-aware: the zone's offset on that date is used.
 */
export function airportLocalToApi(localValue: string, zone: string | null | undefined) {
  const date = fromZonedTime(localValue, safeZone(zone))
  if (!isValid(date)) throw new RangeError(`Invalid local date-time: ${localValue}`)
  return toApiDateTime(date)
}

/** The inverse, for prefilling a datetime-local input: UTC API string → "yyyy-MM-dd'T'HH:mm" in the zone. */
export function apiToAirportLocal(utc: string, zone: string | null | undefined) {
  return formatInTimeZone(parseUtc(utc), safeZone(zone), "yyyy-MM-dd'T'HH:mm")
}

/** "+03:00"-style label of a zone's offset at an instant, e.g. for "Departure · Europe/Istanbul (UTC+3)". */
export function zoneOffsetLabel(zone: string | null | undefined, at: Date = new Date()) {
  const offset = formatInTimeZone(at, safeZone(zone), 'xxx')
  if (offset === '+00:00') return 'UTC'
  const [hours, minutes] = offset.slice(1).split(':')
  return `UTC${offset[0]}${Number(hours)}${minutes === '00' ? '' : `:${minutes}`}`
}

/** "2026-12-01" for a Date, in UTC: search dates are UTC calendar days. */
export function toUtcDay(date: Date) {
  return formatInTimeZone(date, 'UTC', 'yyyy-MM-dd')
}

/** A LocalDate ("1990-05-14") formatted without any zone conversion. */
export function formatLocalDate(value: string, pattern = 'd MMM yyyy') {
  const [year, month, day] = value.split('-').map(Number)
  if (!year || !month || !day) return value
  return format(new Date(year, month - 1, day), pattern)
}

/** "2026-12-01" → Date at local midnight (for date pickers), without zone shifts. */
export function localDateFromIso(value: string): Date | undefined {
  const [year, month, day] = value.split('-').map(Number)
  if (!year || !month || !day) return undefined
  return new Date(year, month - 1, day)
}

/** Date picked in a calendar (local midnight) → "yyyy-MM-dd" as the user saw it. */
export function isoFromLocalDate(date: Date) {
  return format(date, 'yyyy-MM-dd')
}
