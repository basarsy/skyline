import { describe, expect, it } from 'vitest'

import { CATALOG } from '@/test/catalog'
import { makeFlight, routes } from '@/test/fixtures'
import { estimateOffers, includedFor, removalBlockedReason, routeScope } from './ancillaries'
import { estimatePrice } from './pricing'

const offer = (chosen: Record<string, number>, code: string, fare: 'BASIC' | 'STANDARD' | 'FLEX' = 'BASIC') =>
  estimateOffers(CATALOG, 'ECONOMY', fare, 'INTERNATIONAL', chosen).find((o) => o.code === code)!

describe('add-on estimates before booking', () => {
  it('shows what each fare includes', () => {
    expect(includedFor(CATALOG, 'ECONOMY', 'BASIC')).toEqual([])
    expect(includedFor(CATALOG, 'ECONOMY', 'FLEX').map((i) => i.code)).toEqual([
      'CABIN_BAG',
      'CHECKED_BAG_20',
      'PRIORITY_BOARDING',
    ])
    expect(includedFor(CATALOG, 'BUSINESS', 'BASIC').find((i) => i.code === 'CHECKED_BAG_30')?.quantity).toBe(2)
  })

  it('does not sell what the fare already includes', () => {
    expect(offer({}, 'CABIN_BAG', 'BASIC').available).toBe(true)
    const standard = offer({}, 'CABIN_BAG', 'STANDARD')
    expect(standard.available).toBe(false)
    expect(standard.reason).toBe('Included in your fare')
  })

  it('caps checked bags at three and needs a bag before extra weight', () => {
    expect(offer({}, 'EXTRA_WEIGHT_10').reason).toBe('Add a checked bag first')
    expect(offer({ CHECKED_BAG_20: 1 }, 'EXTRA_WEIGHT_10').maxAdditional).toBe(1)
    expect(offer({ CHECKED_BAG_20: 2, CHECKED_BAG_30: 1 }, 'CHECKED_BAG_15').available).toBe(false)
  })

  it('prices by route: domestic is cheaper than international, online cheaper than the airport', () => {
    const international = offer({}, 'CHECKED_BAG_20')
    const domestic = estimateOffers(CATALOG, 'ECONOMY', 'BASIC', 'DOMESTIC', {}).find(
      (o) => o.code === 'CHECKED_BAG_20',
    )!
    expect(domestic.price).toBeLessThan(international.price!)
    expect(international.airportPrice).toBeGreaterThan(international.price!)
  })

  it('tells domestic from international routes', () => {
    expect(routeScope({ route: routes[0]! })).toBe('INTERNATIONAL')
    expect(routeScope({ route: null })).toBe('INTERNATIONAL')
    const domestic = { ...routes[0]!, destination: { ...routes[0]!.origin, iataCode: 'ESB' } }
    expect(routeScope({ route: domestic })).toBe('DOMESTIC')
  })

  it('scales the fare estimate by fare type', () => {
    const flight = makeFlight({ basePrice: 100, availableSeats: 180 })
    expect(estimatePrice(flight, 'ECONOMY', 'BASIC').amount).toBe(85)
    expect(estimatePrice(flight, 'ECONOMY').amount).toBe(100)
    expect(estimatePrice(flight, 'ECONOMY', 'FLEX').amount).toBe(130)
  })
})

describe('online sales cut-off', () => {
  it('closes meals a day before departure and bags an hour before', () => {
    const at = (hours: number, code: string) =>
      estimateOffers(CATALOG, 'ECONOMY', 'BASIC', 'INTERNATIONAL', {}, hours).find((o) => o.code === code)!
    expect(at(30, 'MEAL_HOT').available).toBe(true)
    expect(at(20, 'MEAL_HOT').reason).toBe('Online sales closed 24h before departure')
    expect(at(20, 'CHECKED_BAG_20').available).toBe(true)
    expect(at(0.5, 'CHECKED_BAG_20').available).toBe(false)
  })
})

describe('the basket the server will accept', () => {
  const blocked = (chosen: Record<string, number>, code: string, fare: 'BASIC' | 'FLEX' = 'BASIC') =>
    removalBlockedReason(estimateOffers(CATALOG, 'ECONOMY', fare, 'INTERNATIONAL', chosen), chosen, code)

  it('keeps a bag that carries an extra-weight top-up until the top-up goes', () => {
    expect(blocked({ CHECKED_BAG_15: 1, EXTRA_WEIGHT_10: 1 }, 'CHECKED_BAG_15')).toBe(
      'Remove the extra weight on this bag first',
    )
    expect(blocked({ CHECKED_BAG_15: 1 }, 'CHECKED_BAG_15')).toBeNull()
    expect(blocked({ CHECKED_BAG_15: 2, EXTRA_WEIGHT_10: 1 }, 'CHECKED_BAG_15')).toBeNull()
    expect(blocked({ CHECKED_BAG_15: 1, EXTRA_WEIGHT_10: 1 }, 'EXTRA_WEIGHT_10')).toBeNull()
  })

  it('counts the bag the fare includes and the ones already bought', () => {
    expect(blocked({ CHECKED_BAG_15: 1, EXTRA_WEIGHT_10: 1 }, 'CHECKED_BAG_15', 'FLEX')).toBeNull()
    const offers = estimateOffers(CATALOG, 'ECONOMY', 'BASIC', 'INTERNATIONAL', {}).map((o) =>
      o.code === 'CHECKED_BAG_20' ? { ...o, purchasedQuantity: 1 } : o,
    )
    expect(removalBlockedReason(offers, { CHECKED_BAG_15: 1, EXTRA_WEIGHT_10: 1 }, 'CHECKED_BAG_15')).toBeNull()
  })
})
