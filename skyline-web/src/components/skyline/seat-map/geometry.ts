import type { CabinClass, SeatMapResponse, SeatView } from '@/api/types'

/**
 * Lays out any aircraft from its seat-map data, top-down with the nose up. Nothing here knows a specific
 * aircraft: widths come from the sections' seat letters and aisles, lengths from the rows, facilities and
 * cabin dividers, and the fuselage is narrow- or wide-body by the layout's `body`.
 */

export const SEAT = 30
const SEAT_GAP = 4
const AISLE = 24
const ROW = 38
const ROW_EXTRA = 50
const FACILITY_BAND = 34
const DIVIDER = 44
const WALL = 22
const WING_SPAN = { NARROW: 64, WIDE: 96 } as const
const NOSE = { NARROW: 150, WIDE: 200 } as const
const TAIL = { NARROW: 180, WIDE: 230 } as const

export interface PlacedSeat {
  seat: SeatView
  x: number
  y: number
}
export interface Band {
  kind: 'facility'
  y: number
  height: number
  items: { type: SeatMapResponse['facilities'][number]['type']; x: number; width: number }[]
}
export interface Divider {
  y: number
  cabin: CabinClass
  /** Seat letters with their x, for the header row. */
  letters: { letter: string; x: number }[]
}
export interface RowLabel {
  row: number
  y: number
  /** x of every aisle centre in this row's section; the number sits in each. */
  aisles: number[]
  exit: boolean
}
export interface Geometry {
  width: number
  height: number
  /** The fuselage's left and right outer edge. */
  left: number
  right: number
  fuselage: string
  wings: string[]
  stabilizers: string[]
  cockpit: string
  seats: PlacedSeat[]
  bands: Band[]
  dividers: Divider[]
  rows: RowLabel[]
  exits: { y: number }[]
}

type Section = SeatMapResponse['sections'][number]

function sectionWidth(section: Section) {
  const seats = section.columnGroups.reduce((sum, group) => sum + group.length, 0)
  const gaps = section.columnGroups.reduce((sum, group) => sum + Math.max(0, group.length - 1), 0)
  return seats * SEAT + gaps * SEAT_GAP + (section.columnGroups.length - 1) * AISLE
}

/** Each letter's x (left edge) and each aisle's centre, for a section centred between the walls. */
function columns(section: Section, cabinLeft: number, cabinWidth: number) {
  let x = cabinLeft + (cabinWidth - sectionWidth(section)) / 2
  const letters = new Map<string, number>()
  const aisles: number[] = []
  section.columnGroups.forEach((group, index) => {
    if (index > 0) {
      aisles.push(x + AISLE / 2)
      x += AISLE
    }
    group.forEach((letter, i) => {
      letters.set(letter, x)
      x += SEAT + (i < group.length - 1 ? SEAT_GAP : 0)
    })
  })
  return { letters, aisles }
}

export function seatMapGeometry(map: SeatMapResponse): Geometry {
  const body = map.body === 'WIDE' ? 'WIDE' : 'NARROW'
  const cabinWidth = Math.max(...map.sections.map(sectionWidth))
  const fuselageWidth = cabinWidth + WALL * 2
  const left = WING_SPAN[body] + 8
  const right = left + fuselageWidth
  const cabinLeft = left + WALL
  const width = right + WING_SPAN[body] + 8

  const sectionOf = (row: number) => map.sections.find((s) => row >= s.fromRow && row <= s.toRow) ?? map.sections[0]!
  const seatsByRow = new Map<number, SeatView[]>()
  for (const seat of map.seats) seatsByRow.set(seat.row, [...(seatsByRow.get(seat.row) ?? []), seat])

  const bands: Band[] = []
  const dividers: Divider[] = []
  const rowLabels: RowLabel[] = []
  const placed: PlacedSeat[] = []
  const exits: { y: number }[] = []
  const rowY = new Map<number, number>()

  const facilityBand = (afterRow: number, y: number) => {
    const here = map.facilities.filter((f) => f.afterRow === afterRow)
    if (here.length === 0) return 0
    // FULL items get their own band below the side items.
    const sides = here.filter((f) => f.side !== 'FULL')
    const full = here.filter((f) => f.side === 'FULL')
    let height = 0
    if (sides.length) {
      const third = cabinWidth / 3
      bands.push({
        kind: 'facility',
        y: y + height,
        height: FACILITY_BAND,
        items: sides.map((f) => ({
          type: f.type,
          x: cabinLeft + (f.side === 'LEFT' ? 0 : f.side === 'CENTER' ? third : third * 2) + 4,
          width: third - 8,
        })),
      })
      height += FACILITY_BAND
    }
    for (const f of full) {
      bands.push({
        kind: 'facility',
        y: y + height,
        height: FACILITY_BAND,
        items: [{ type: f.type, x: cabinLeft + 4, width: cabinWidth - 8 }],
      })
      height += FACILITY_BAND
    }
    return height + 6
  }

  let y = NOSE[body]
  let previousRow = 0
  let previousSection: Section | null = null
  for (const row of map.rows) {
    y += facilityBand(previousRow, y)
    const section = sectionOf(row.number)
    const cols = columns(section, cabinLeft, cabinWidth)
    if (section !== previousSection) {
      dividers.push({
        y,
        cabin: section.cabin,
        letters: [...cols.letters.entries()].map(([letter, x]) => ({ letter, x: x + SEAT / 2 })),
      })
      y += DIVIDER
      previousSection = section
    }
    const seats = seatsByRow.get(row.number) ?? []
    const extra = seats.some((seat) => seat.legroom === 'EXTRA')
    const height = extra ? ROW_EXTRA : ROW
    const seatY = y + (height - SEAT) - 4 // extra legroom shows as space in front of the seat
    rowY.set(row.number, y)
    for (const seat of seats) {
      const x = cols.letters.get(seat.letter)
      if (x !== undefined) placed.push({ seat, x, y: seatY })
    }
    rowLabels.push({ row: row.number, y: seatY + SEAT / 2, aisles: cols.aisles, exit: row.exit })
    if (row.exit) exits.push({ y: seatY - 6 })
    y += height
    previousRow = row.number
  }
  y += facilityBand(previousRow, y)

  const bodyTop = NOSE[body]
  const bodyEnd = y + 10
  const tail = TAIL[body]
  const height = bodyEnd + tail + 10
  const mid = (left + right) / 2
  const w = fuselageWidth
  const fuselage = [
    `M ${left},${bodyTop}`,
    `C ${left},${bodyTop * 0.35} ${left + w * 0.22},4 ${mid},4`,
    `C ${right - w * 0.22},4 ${right},${bodyTop * 0.35} ${right},${bodyTop}`,
    `L ${right},${bodyEnd}`,
    `C ${right},${bodyEnd + tail * 0.55} ${mid + w * 0.12},${bodyEnd + tail} ${mid + 6},${bodyEnd + tail}`,
    `L ${mid - 6},${bodyEnd + tail}`,
    `C ${mid - w * 0.12},${bodyEnd + tail} ${left},${bodyEnd + tail * 0.55} ${left},${bodyEnd}`,
    'Z',
  ].join(' ')

  const wings: string[] = []
  if (map.wing) {
    const top = rowY.get(map.wing.fromRow) ?? bodyTop + (bodyEnd - bodyTop) * 0.35
    const lastRowTop = rowY.get(map.wing.toRow)
    const bottom = lastRowTop !== undefined ? lastRowTop + ROW : top + (bodyEnd - bodyTop) * 0.25
    const span = WING_SPAN[body]
    const sweep = (bottom - top) * 0.55
    wings.push(
      `M ${left},${top} L ${left - span},${top + sweep} L ${left - span},${top + sweep + 26} L ${left},${bottom} Z`,
      `M ${right},${top} L ${right + span},${top + sweep} L ${right + span},${top + sweep + 26} L ${right},${bottom} Z`,
    )
  }
  const stabTop = bodyEnd + tail * 0.3
  const stabSpan = WING_SPAN[body] * 0.45
  const stabilizers = [
    `M ${left + w * 0.12},${stabTop} L ${left - stabSpan},${stabTop + 46} L ${left + w * 0.2},${stabTop + 52} Z`,
    `M ${right - w * 0.12},${stabTop} L ${right + stabSpan},${stabTop + 46} L ${right - w * 0.2},${stabTop + 52} Z`,
  ]
  const cockpit = `M ${mid - w * 0.2},${bodyTop * 0.42} Q ${mid},${bodyTop * 0.3} ${mid + w * 0.2},${bodyTop * 0.42} L ${mid + w * 0.16},${bodyTop * 0.52} Q ${mid},${bodyTop * 0.44} ${mid - w * 0.16},${bodyTop * 0.52} Z`

  return {
    width,
    height,
    left,
    right,
    fuselage,
    wings,
    stabilizers,
    cockpit,
    seats: placed,
    bands,
    dividers,
    rows: rowLabels,
    exits,
  }
}

/**
 * Keyboard neighbours: left/right along the row (skipping aisles and gaps), up/down to the nearest seat in the
 * next row in that direction. Returns the seat code to focus, or null at an edge.
 */
export function neighbour(seats: PlacedSeat[], from: string, key: string): string | null {
  const current = seats.find((p) => p.seat.code === from)
  if (!current) return seats[0]?.seat.code ?? null
  if (key === 'ArrowLeft' || key === 'ArrowRight') {
    const row = seats.filter((p) => p.seat.row === current.seat.row).sort((a, b) => a.x - b.x)
    const index = row.indexOf(current) + (key === 'ArrowRight' ? 1 : -1)
    return row[index]?.seat.code ?? null
  }
  if (key === 'ArrowUp' || key === 'ArrowDown') {
    const rows = [...new Set(seats.map((p) => p.seat.row))].sort((a, b) => a - b)
    const target = rows[rows.indexOf(current.seat.row) + (key === 'ArrowDown' ? 1 : -1)]
    if (target === undefined) return null
    const candidates = seats.filter((p) => p.seat.row === target)
    candidates.sort((a, b) => Math.abs(a.x - current.x) - Math.abs(b.x - current.x))
    return candidates[0]?.seat.code ?? null
  }
  return null
}
