import { fireEvent, render, screen, within } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'

import type { SeatMapResponse } from '@/api/types'
import { expandLayout, LAYOUTS } from '@/test/seat-layouts'
import { neighbour, seatMapGeometry } from './geometry'
import { SeatMap } from './seat-map'

const A320 = '22222222-2222-2222-2222-222222222201'
const B787 = '22222222-2222-2222-2222-222222222203'
const A321 = '22222222-2222-2222-2222-222222222204'

/** A seat map like the API's: everything available, Economy priced at 0 (standard) or 6 (window/aisle). */
function seatMap(typeId: string, overrides: Partial<SeatMapResponse> = {}): SeatMapResponse {
  const layout = expandLayout(LAYOUTS[typeId]!)
  return {
    flightId: 'f1',
    flightNumber: 'SK101',
    aircraftTypeId: typeId,
    aircraftName: 'Test aircraft',
    body: layout.body,
    wing: layout.wing,
    sections: layout.sections,
    rows: layout.rows,
    facilities: layout.facilities,
    cabin: 'ECONOMY',
    fareType: 'STANDARD',
    currency: 'EUR',
    surchargePercent: 0,
    holdSeconds: 900,
    legend: [
      { category: 'STANDARD', price: 0, available: 10 },
      { category: 'PREFERRED', price: 6, available: 10 },
    ],
    seats: layout.seats.map((seat) => ({
      ...seat,
      status: 'AVAILABLE',
      price: seat.cabin === 'ECONOMY' ? (seat.category === 'STANDARD' ? 0 : 6) : null,
      selectable: seat.cabin === 'ECONOMY',
      mine: false,
    })),
    ...overrides,
  }
}

describe('seatMapGeometry', () => {
  it('draws every seat of any aircraft, and wide-bodies wider than narrow-bodies', () => {
    for (const [typeId, total] of [
      [A320, 180],
      [A321, 190],
      [B787, 296],
    ] as const) {
      expect(seatMapGeometry(seatMap(typeId)).seats).toHaveLength(total)
    }
    const narrow = seatMapGeometry(seatMap(A320))
    const wide = seatMapGeometry(seatMap(B787))
    expect(wide.right - wide.left).toBeGreaterThan(narrow.right - narrow.left)
  })

  it('puts the nose first and every row below the previous one', () => {
    const geometry = seatMapGeometry(seatMap(A321))
    const ys = geometry.rows.map((row) => row.y)
    expect(ys).toEqual([...ys].sort((a, b) => a - b))
    expect(geometry.dividers.map((d) => d.cabin)).toEqual(['BUSINESS', 'ECONOMY'])
    expect(geometry.exits.length).toBe(2)
  })

  it('moves the keyboard focus along rows and between rows', () => {
    const { seats } = seatMapGeometry(seatMap(A320))
    expect(neighbour(seats, '14C', 'ArrowRight')).toBe('14D') // across the aisle
    expect(neighbour(seats, '14A', 'ArrowLeft')).toBeNull()
    expect(neighbour(seats, '12B', 'ArrowDown')).toBe('14B') // row 13 doesn't exist
    expect(neighbour(seats, '1B', 'ArrowUp')).toBeNull()
  })
})

describe('SeatMap', () => {
  it('labels seats with number, position, category, state and price', () => {
    render(<SeatMap map={seatMap(A321)} selected={null} onSelect={() => {}} />)

    expect(screen.getByRole('button', { name: /^Seat 20A, window, window or aisle, available, €6\.00$/ })).toBeVisible()
    expect(screen.getByRole('button', { name: /^Seat 1A, window, extra legroom, not in your cabin$/ })).toHaveAttribute(
      'aria-disabled',
      'true',
    )
  })

  it('shows taken and held seats as unavailable, not only by color', () => {
    const map = seatMap(A320)
    map.seats = map.seats.map((seat) =>
      seat.code === '20A'
        ? { ...seat, status: 'TAKEN', selectable: false }
        : seat.code === '20B'
          ? { ...seat, status: 'HELD', selectable: false }
          : seat,
    )
    render(<SeatMap map={map} selected={null} onSelect={() => {}} />)

    const taken = screen.getByRole('button', { name: /^Seat 20A.*taken/ })
    expect(within(taken).getByText('×')).toBeInTheDocument()
    const held = screen.getByRole('button', { name: /^Seat 20B.*held by another passenger/ })
    expect(within(held).getByText('…')).toBeInTheDocument()
  })

  it('selects with Enter and moves with the arrow keys', () => {
    const onSelect = vi.fn()
    render(<SeatMap map={seatMap(A320)} selected="20C" onSelect={onSelect} />)

    const selected = screen.getByRole('button', { name: /^Seat 20C/, pressed: true })
    selected.focus()
    fireEvent.keyDown(selected, { key: 'ArrowRight' })
    expect(screen.getByRole('button', { name: /^Seat 20D/ })).toHaveFocus()
    fireEvent.keyDown(document.activeElement!, { key: 'Enter' })
    expect(onSelect).toHaveBeenCalledWith(expect.objectContaining({ code: '20D' }))
  })

  it('does not select a seat outside the passenger’s cabin', () => {
    const onSelect = vi.fn()
    render(<SeatMap map={seatMap(A321)} selected={null} onSelect={onSelect} />)

    fireEvent.click(screen.getByRole('button', { name: /^Seat 1A,/ }))
    expect(onSelect).not.toHaveBeenCalled()
  })

  it('shows the price legend for the passenger', () => {
    render(<SeatMap map={seatMap(A320)} selected={null} onSelect={() => {}} />)

    const legend = screen.getByRole('region', { name: 'Seat prices' })
    expect(within(legend).getByText('Included')).toBeVisible()
    expect(within(legend).getByText('€6.00')).toBeVisible()
  })
})
