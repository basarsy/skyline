import type {
  AncillaryOffer,
  AncillaryProductResponse,
  AncillaryType,
  CabinClass,
  FareType,
  FlightResponse,
  RouteScope,
} from '@/api/types'

/**
 * Before a booking exists there is no per-reservation options endpoint, so the booking page builds the offers
 * from the public catalog: what the fare includes, the per-passenger caps, and the online price on this route.
 * Guidance only, mirroring the reservation service's rules; the server prices and validates every item when
 * the booking is made.
 */
const MAX_CHECKED_BAGS = 3
const MAX_MEALS = 1

/** Hours from now until a UTC API date-time (negative once it has passed). */
export function hoursUntil(utc: string, now = Date.now()) {
  return (new Date(`${utc}Z`).getTime() - now) / 3_600_000
}

export function routeScope(flight: Pick<FlightResponse, 'route'>): RouteScope {
  const route = flight.route
  if (!route?.origin.country || !route.destination.country) return 'INTERNATIONAL'
  return route.origin.country.toLowerCase() === route.destination.country.toLowerCase() ? 'DOMESTIC' : 'INTERNATIONAL'
}

export function includedFor(catalog: AncillaryProductResponse[], cabin: CabinClass, fare: FareType) {
  return catalog
    .map((product) => ({
      code: product.code,
      name: product.name,
      quantity: product.includedIn.find((a) => a.cabinClass === cabin && a.fareType === fare)?.quantity ?? 0,
    }))
    .filter((item) => item.quantity > 0)
}

/**
 * @param chosen           product code → quantity already picked for this passenger in this booking
 * @param hoursToDeparture online sales close `cutoffHours` before departure (24h for meals)
 */
export function estimateOffers(
  catalog: AncillaryProductResponse[],
  cabin: CabinClass,
  fare: FareType,
  scope: RouteScope,
  chosen: Record<string, number>,
  hoursToDeparture = Infinity,
): AncillaryOffer[] {
  const included = Object.fromEntries(includedFor(catalog, cabin, fare).map((item) => [item.code, item.quantity]))
  const byType = (type: AncillaryType) =>
    catalog.filter((p) => p.type === type).reduce((sum, p) => sum + (included[p.code] ?? 0) + (chosen[p.code] ?? 0), 0)
  const checkedBags = byType('CHECKED_BAG')

  return catalog
    .filter((product) => product.active)
    .map((product) => {
      const inc = included[product.code] ?? 0
      const mine = chosen[product.code] ?? 0
      let room = product.maxQuantity - inc - mine
      if (product.type === 'CHECKED_BAG') room = Math.min(room, MAX_CHECKED_BAGS - checkedBags)
      if (product.type === 'MEAL') room = Math.min(room, MAX_MEALS - byType('MEAL'))
      if (product.type === 'EXTRA_WEIGHT') room = Math.min(room, checkedBags - byType('EXTRA_WEIGHT'))
      const price = product.prices.find((p) => p.routeScope === scope)
      const closed = hoursToDeparture <= product.cutoffHours
      if (closed) room = 0
      const reason = !price
        ? 'Not sold on this route'
        : closed && mine === 0
          ? `Online sales closed ${product.cutoffHours}h before departure`
          : room > 0 || mine > 0
            ? null
            : inc > 0
              ? 'Included in your fare'
              : product.type === 'EXTRA_WEIGHT' && checkedBags === 0
                ? 'Add a checked bag first'
                : product.type === 'MEAL' && byType('MEAL') > 0
                  ? 'One meal per passenger'
                  : 'You have the maximum for this flight'
      return {
        code: product.code,
        type: product.type,
        name: product.name,
        description: product.description,
        weightKg: product.weightKg,
        includedQuantity: inc,
        purchasedQuantity: 0,
        maxAdditional: Math.max(0, room) + mine,
        price: price?.onlinePrice ?? null,
        onlinePrice: price?.onlinePrice ?? null,
        airportPrice: price?.airportPrice ?? null,
        available: Boolean(price) && (room > 0 || mine > 0),
        reason,
      }
    })
}

export const FARE_TYPES: readonly FareType[] = ['BASIC', 'STANDARD', 'FLEX']

export const fareLabel: Record<FareType, string> = { BASIC: 'Basic', STANDARD: 'Standard', FLEX: 'Flex' }

export const fareMultiplier: Record<FareType, number> = { BASIC: 0.85, STANDARD: 1, FLEX: 1.3 }

/** Plain-words fare summaries for Economy (Business and First include everything on every fare). */
export const fareCopy: Record<FareType, string> = {
  BASIC: 'A personal item only. Seats and bags cost extra.',
  STANDARD: 'Cabin bag and a standard seat included.',
  FLEX: 'Cabin bag, 20 kg bag and priority boarding; most seats free; add-ons refundable.',
}
