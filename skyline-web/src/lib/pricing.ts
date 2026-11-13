import type { CabinClass, FlightResponse } from '@/api/types'

/**
 * Client-side price preview, mirroring the reservation service. Guidance only: it's labelled "Estimated"
 * before booking, and the server's totalPrice is what the passenger pays.
 */
export const CABIN_MULTIPLIER: Record<CabinClass, number> = {
  ECONOMY: 1.0,
  BUSINESS: 2.5,
  FIRST: 4.0,
}

/** Capacity used for the load factor: the aircraft type's seats, or a safe fallback. */
export function capacityOf(flight: Pick<FlightResponse, 'availableSeats' | 'aircraft'>) {
  return flight.aircraft?.aircraftType.totalSeats ?? Math.max(flight.availableSeats, 180)
}

/** Share of seats already sold, 0..1. */
export function loadFactor(flight: Pick<FlightResponse, 'availableSeats' | 'aircraft'>) {
  const total = capacityOf(flight)
  if (total <= 0) return 0
  return Math.min(1, Math.max(0, (total - flight.availableSeats) / total))
}

/** 1.30 above 90% full, 1.15 above 80%, otherwise 1.0. */
export function loadMultiplier(factor: number) {
  if (factor > 0.9) return 1.3
  if (factor > 0.8) return 1.15
  return 1.0
}

export interface PriceEstimate {
  amount: number
  cabinMultiplier: number
  loadMultiplier: number
  /** e.g. 15 for "+15%", 0 when there's no surcharge. */
  surchargePercent: number
}

export function estimatePrice(
  flight: Pick<FlightResponse, 'basePrice' | 'availableSeats' | 'aircraft'>,
  cabin: CabinClass,
): PriceEstimate {
  const cabinMultiplier = CABIN_MULTIPLIER[cabin]
  const multiplier = loadMultiplier(loadFactor(flight))
  const amount = Math.round(flight.basePrice * cabinMultiplier * multiplier * 100) / 100
  return {
    amount,
    cabinMultiplier,
    loadMultiplier: multiplier,
    surchargePercent: Math.round((multiplier - 1) * 100),
  }
}

/** Plain-words description of a cabin multiplier, for the booking cabin cards. */
export function cabinMultiplierCopy(cabin: CabinClass) {
  switch (cabin) {
    case 'ECONOMY':
      return 'The base fare'
    case 'BUSINESS':
      return '2.5 × the base fare'
    case 'FIRST':
      return '4 × the base fare'
  }
}
