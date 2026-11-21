import type { AncillaryProductResponse, AncillaryType, CabinClass, FareType } from '@/api/types'

/** The seeded add-on catalog (V5__ancillary_catalog.sql) for the MSW back-end. Test code only. */
type Seed = [
  code: string,
  type: AncillaryType,
  name: string,
  description: string,
  weightKg: number | null,
  max: number,
  cutoff: number,
  refundable: boolean,
  domestic: [number, number],
  international: [number, number],
]

const SEEDS: Seed[] = [
  [
    'CABIN_BAG',
    'CABIN_BAG',
    'Cabin bag',
    'A wheeled carry-on up to 8 kg and 55 x 40 x 23 cm, on top of your personal item',
    8,
    1,
    1,
    false,
    [15, 30],
    [25, 45],
  ],
  [
    'CHECKED_BAG_15',
    'CHECKED_BAG',
    'Checked bag 15 kg',
    'One checked bag up to 15 kg',
    15,
    3,
    1,
    false,
    [20, 40],
    [30, 55],
  ],
  [
    'CHECKED_BAG_20',
    'CHECKED_BAG',
    'Checked bag 20 kg',
    'One checked bag up to 20 kg',
    20,
    3,
    1,
    false,
    [25, 50],
    [40, 70],
  ],
  [
    'CHECKED_BAG_30',
    'CHECKED_BAG',
    'Checked bag 30 kg',
    'One checked bag up to 30 kg',
    30,
    3,
    1,
    false,
    [40, 70],
    [60, 100],
  ],
  [
    'EXTRA_WEIGHT_10',
    'EXTRA_WEIGHT',
    'Extra 10 kg',
    'Adds 10 kg to one of your checked bags',
    10,
    3,
    1,
    false,
    [15, 30],
    [25, 45],
  ],
  [
    'EXTRA_WEIGHT_20',
    'EXTRA_WEIGHT',
    'Extra 20 kg',
    'Adds 20 kg to one of your checked bags',
    20,
    3,
    1,
    false,
    [25, 50],
    [40, 70],
  ],
  [
    'SPORTS_EQUIPMENT',
    'SPORTS_EQUIPMENT',
    'Sports or oversized item',
    'Skis, golf clubs, a bike or another item over 158 cm, up to 23 kg',
    23,
    2,
    1,
    false,
    [35, 60],
    [50, 80],
  ],
  [
    'PRIORITY_BOARDING',
    'PRIORITY_BOARDING',
    'Priority boarding',
    'Board first, with a dedicated lane at the gate',
    null,
    1,
    1,
    false,
    [8, 12],
    [10, 15],
  ],
  [
    'MEAL_HOT',
    'MEAL',
    'Hot meal',
    'A hot main course with a drink, pre-ordered',
    null,
    1,
    24,
    true,
    [12, 12],
    [15, 15],
  ],
  [
    'MEAL_VEGETARIAN',
    'MEAL',
    'Vegetarian meal',
    'A hot vegetarian main course with a drink, pre-ordered',
    null,
    1,
    24,
    true,
    [12, 12],
    [15, 15],
  ],
]

const FARES: FareType[] = ['BASIC', 'STANDARD', 'FLEX']
const PREMIUM: [CabinClass, number][] = [
  ['BUSINESS', 2],
  ['FIRST', 3],
]

function includedIn(code: string): AncillaryProductResponse['includedIn'] {
  const list: AncillaryProductResponse['includedIn'] = []
  if (code === 'CABIN_BAG') list.push({ cabinClass: 'ECONOMY', fareType: 'STANDARD', quantity: 1 })
  if (['CABIN_BAG', 'CHECKED_BAG_20', 'PRIORITY_BOARDING'].includes(code)) {
    list.push({ cabinClass: 'ECONOMY', fareType: 'FLEX', quantity: 1 })
  }
  if (['CABIN_BAG', 'CHECKED_BAG_30', 'PRIORITY_BOARDING', 'MEAL_HOT'].includes(code)) {
    for (const [cabin, bags] of PREMIUM) {
      for (const fare of FARES)
        list.push({ cabinClass: cabin, fareType: fare, quantity: code === 'CHECKED_BAG_30' ? bags : 1 })
    }
  }
  return list
}

export const CATALOG: AncillaryProductResponse[] = SEEDS.map(
  ([code, type, name, description, weightKg, maxQuantity, cutoffHours, alwaysRefundable, domestic, international]) => ({
    code,
    type,
    name,
    description,
    weightKg,
    maxQuantity,
    cutoffHours,
    alwaysRefundable,
    active: true,
    prices: [
      { routeScope: 'DOMESTIC', onlinePrice: domestic[0], airportPrice: domestic[1] },
      { routeScope: 'INTERNATIONAL', onlinePrice: international[0], airportPrice: international[1] },
    ],
    includedIn: includedIn(code),
    currency: 'EUR',
  }),
)
