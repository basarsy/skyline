import type { SeatCategory, SeatFeature, SeatPosition, SeatView } from '@/api/types'

/** Category styling: a fill token and a glyph, so a seat's category never relies on color alone. */
export const categoryStyle: Record<SeatCategory, { label: string; fill: string; glyph: string }> = {
  EXIT_ROW: { label: 'Exit row', fill: 'var(--seat-exit)', glyph: 'E' },
  EXTRA_LEGROOM: { label: 'Extra legroom', fill: 'var(--seat-legroom)', glyph: '+' },
  FRONT: { label: 'Front of cabin', fill: 'var(--seat-front)', glyph: 'F' },
  PREFERRED: { label: 'Window or aisle', fill: 'var(--seat-preferred)', glyph: '•' },
  STANDARD: { label: 'Standard', fill: 'var(--seat-standard)', glyph: '' },
}

export const positionLabel: Record<SeatPosition, string> = {
  WINDOW: 'Window',
  MIDDLE: 'Middle',
  AISLE: 'Aisle',
}

export const featureLabel: Record<SeatFeature, string> = {
  EXIT_ROW: 'Exit row (you must be able to help in an emergency)',
  BULKHEAD: 'Bulkhead',
  NEAR_LAVATORY: 'Near a lavatory',
  NEAR_GALLEY: 'Near a galley',
  LIMITED_RECLINE: 'Limited recline',
  ACCESSIBLE: 'Movable armrest',
  OVER_WING: 'Over the wing',
}

export function statusLabel(seat: SeatView, selected: boolean) {
  if (selected || seat.mine) return 'your seat'
  switch (seat.status) {
    case 'TAKEN':
      return 'taken'
    case 'HELD':
      return 'held by another passenger'
    case 'BLOCKED':
      return 'not available'
    default:
      return seat.price === null ? 'not in your cabin' : 'available'
  }
}
