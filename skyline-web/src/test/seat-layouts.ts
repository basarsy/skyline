import type {
  CabinClass,
  FacilitySide,
  FacilityType,
  SeatCategory,
  SeatFeature,
  SeatMapResponse,
  SeatPosition,
  SeatView,
} from '@/api/types'

/**
 * Seat layouts for the MSW back-end: the fleet service's seeded layouts (V4__cabin_layouts.sql) and a port of
 * its SeatLayoutExpander, so the mocked app draws the same aircraft as the real one. Test code only.
 */
interface CompactLayout {
  body: 'NARROW' | 'WIDE'
  sections: {
    cabin: CabinClass
    fromRow: number
    toRow: number
    columns: string
    frontRows: number
    extraLegroomRows?: number[]
  }[]
  skipRows: number[]
  exitRows: number[]
  wing: { fromRow: number; toRow: number }
  facilities: { type: FacilityType; afterRow: number; side: FacilitySide }[]
  omitSeats: string[]
  accessibleSeats: string[]
}

export const LAYOUTS: Record<string, CompactLayout> = {
  // A320neo, Y180
  '22222222-2222-2222-2222-222222222201': {
    body: 'NARROW',
    sections: [{ cabin: 'ECONOMY', fromRow: 1, toRow: 31, columns: 'ABC|DEF', frontRows: 4 }],
    skipRows: [13],
    exitRows: [11, 12],
    wing: { fromRow: 8, toRow: 17 },
    facilities: [
      { type: 'GALLEY', afterRow: 0, side: 'LEFT' },
      { type: 'LAVATORY', afterRow: 0, side: 'RIGHT' },
      { type: 'LAVATORY', afterRow: 31, side: 'LEFT' },
      { type: 'LAVATORY', afterRow: 31, side: 'RIGHT' },
      { type: 'GALLEY', afterRow: 31, side: 'FULL' },
    ],
    omitSeats: [],
    accessibleSeats: ['2C', '2D', '20C', '20D'],
  },
  // 737-800, Y189
  '22222222-2222-2222-2222-222222222202': {
    body: 'NARROW',
    sections: [{ cabin: 'ECONOMY', fromRow: 1, toRow: 32, columns: 'ABC|DEF', frontRows: 3 }],
    skipRows: [],
    exitRows: [16, 17],
    wing: { fromRow: 12, toRow: 21 },
    facilities: [
      { type: 'LAVATORY', afterRow: 0, side: 'LEFT' },
      { type: 'GALLEY', afterRow: 0, side: 'RIGHT' },
      { type: 'LAVATORY', afterRow: 32, side: 'LEFT' },
      { type: 'LAVATORY', afterRow: 32, side: 'RIGHT' },
      { type: 'GALLEY', afterRow: 32, side: 'FULL' },
    ],
    omitSeats: ['1A', '1B', '1C'],
    accessibleSeats: ['3C', '3D'],
  },
  // 787-9, C30 Y266
  '22222222-2222-2222-2222-222222222203': {
    body: 'WIDE',
    sections: [
      { cabin: 'BUSINESS', fromRow: 1, toRow: 5, columns: 'AC|DG|HK', frontRows: 0 },
      { cabin: 'ECONOMY', fromRow: 10, toRow: 39, columns: 'ABC|DEF|HJK', frontRows: 4 },
    ],
    skipRows: [],
    exitRows: [10, 25],
    wing: { fromRow: 14, toRow: 26 },
    facilities: [
      { type: 'GALLEY', afterRow: 0, side: 'CENTER' },
      { type: 'LAVATORY', afterRow: 0, side: 'LEFT' },
      { type: 'LAVATORY', afterRow: 0, side: 'RIGHT' },
      { type: 'GALLEY', afterRow: 5, side: 'CENTER' },
      { type: 'LAVATORY', afterRow: 5, side: 'LEFT' },
      { type: 'LAVATORY', afterRow: 5, side: 'RIGHT' },
      { type: 'LAVATORY', afterRow: 24, side: 'LEFT' },
      { type: 'LAVATORY', afterRow: 24, side: 'RIGHT' },
      { type: 'CLOSET', afterRow: 24, side: 'CENTER' },
      { type: 'LAVATORY', afterRow: 39, side: 'LEFT' },
      { type: 'LAVATORY', afterRow: 39, side: 'RIGHT' },
      { type: 'GALLEY', afterRow: 39, side: 'FULL' },
    ],
    omitSeats: ['38A', '38K', '39A', '39K'],
    accessibleSeats: ['10C', '10H', '1C', '1H'],
  },
  // A321neo, J16 Y174
  '22222222-2222-2222-2222-222222222204': {
    body: 'NARROW',
    sections: [
      { cabin: 'BUSINESS', fromRow: 1, toRow: 4, columns: 'AC|DF', frontRows: 0 },
      { cabin: 'ECONOMY', fromRow: 5, toRow: 33, columns: 'ABC|DEF', frontRows: 3, extraLegroomRows: [27] },
    ],
    skipRows: [],
    exitRows: [12, 26],
    wing: { fromRow: 10, toRow: 19 },
    facilities: [
      { type: 'GALLEY', afterRow: 0, side: 'LEFT' },
      { type: 'LAVATORY', afterRow: 0, side: 'RIGHT' },
      { type: 'LAVATORY', afterRow: 4, side: 'LEFT' },
      { type: 'LAVATORY', afterRow: 33, side: 'LEFT' },
      { type: 'LAVATORY', afterRow: 33, side: 'RIGHT' },
      { type: 'GALLEY', afterRow: 33, side: 'FULL' },
    ],
    omitSeats: [],
    accessibleSeats: ['5C', '5D'],
  },
}

export type LayoutSeat = Omit<SeatView, 'status' | 'price' | 'selectable' | 'mine'>

export interface ExpandedLayout {
  body: CompactLayout['body']
  wing: CompactLayout['wing']
  sections: SeatMapResponse['sections']
  rows: SeatMapResponse['rows']
  facilities: SeatMapResponse['facilities']
  seats: LayoutSeat[]
}

function position(groupCount: number, groupIndex: number, groupSize: number, seatIndex: number): SeatPosition {
  const first = groupIndex === 0
  const last = groupIndex === groupCount - 1
  if ((first && seatIndex === 0) || (last && seatIndex === groupSize - 1)) return 'WINDOW'
  return (!first && seatIndex === 0) || (!last && seatIndex === groupSize - 1) ? 'AISLE' : 'MIDDLE'
}

export function expandLayout(layout: CompactLayout): ExpandedLayout {
  const rowSection = new Map<number, CompactLayout['sections'][number]>()
  for (const section of layout.sections) {
    for (let row = section.fromRow; row <= section.toRow; row += 1) {
      if (!layout.skipRows.includes(row)) rowSection.set(row, section)
    }
  }
  const rows = [...rowSection.keys()]
  const seats: LayoutSeat[] = []
  rows.forEach((row, i) => {
    const previous = i === 0 ? 0 : rows[i - 1]!
    const next = rows[i + 1]
    const section = rowSection.get(row)!
    const indexInSection = rows.filter((r) => rowSection.get(r) === section && r < row).length
    const exit = layout.exitRows.includes(row)
    const overWing = row >= layout.wing.fromRow && row <= layout.wing.toRow
    const bulkhead = indexInSection === 0 || layout.facilities.some((f) => f.afterRow === previous)
    const limitedRecline =
      next === undefined || layout.exitRows.includes(next) || layout.facilities.some((f) => f.afterRow === row)
    const extra = exit || bulkhead || (section.extraLegroomRows ?? []).includes(row)
    const near = (type: FacilityType) =>
      layout.facilities.some((f) => f.type === type && (f.afterRow === previous || f.afterRow === row))
    const groups = section.columns.split('|').map((g) => g.split(''))
    groups.forEach((group, g) =>
      group.forEach((letter, s) => {
        const code = `${row}${letter}`
        if (layout.omitSeats.includes(code)) return
        const pos = position(groups.length, g, group.length, s)
        const features: SeatFeature[] = []
        if (exit) features.push('EXIT_ROW')
        if (bulkhead) features.push('BULKHEAD')
        if (near('LAVATORY')) features.push('NEAR_LAVATORY')
        if (near('GALLEY')) features.push('NEAR_GALLEY')
        if (limitedRecline) features.push('LIMITED_RECLINE')
        if (layout.accessibleSeats.includes(code)) features.push('ACCESSIBLE')
        if (overWing) features.push('OVER_WING')
        const category: SeatCategory = exit
          ? 'EXIT_ROW'
          : extra
            ? 'EXTRA_LEGROOM'
            : indexInSection < section.frontRows
              ? 'FRONT'
              : pos !== 'MIDDLE'
                ? 'PREFERRED'
                : 'STANDARD'
        seats.push({
          code,
          row,
          letter,
          cabin: section.cabin,
          position: pos,
          legroom: extra ? 'EXTRA' : 'STANDARD',
          features,
          category,
        })
      }),
    )
  })
  return {
    body: layout.body,
    wing: layout.wing,
    sections: layout.sections.map((s) => ({
      cabin: s.cabin,
      fromRow: s.fromRow,
      toRow: s.toRow,
      columnGroups: s.columns.split('|').map((g) => g.split('')),
    })),
    rows: rows.map((number) => ({
      number,
      cabin: rowSection.get(number)!.cabin,
      exit: layout.exitRows.includes(number),
      overWing: number >= layout.wing.fromRow && number <= layout.wing.toRow,
    })),
    facilities: layout.facilities,
    seats,
  }
}

/** The seeded price table (V3__fares_and_seat_pricing.sql): Economy by fare; Business/First always included. */
export const SEAT_PRICES: Record<string, Partial<Record<SeatCategory, number>>> = {
  BASIC: { STANDARD: 8, PREFERRED: 12, FRONT: 18, EXTRA_LEGROOM: 28, EXIT_ROW: 32 },
  STANDARD: { STANDARD: 0, PREFERRED: 6, FRONT: 12, EXTRA_LEGROOM: 22, EXIT_ROW: 25 },
  FLEX: { STANDARD: 0, PREFERRED: 0, FRONT: 0, EXTRA_LEGROOM: 12, EXIT_ROW: 15 },
}
