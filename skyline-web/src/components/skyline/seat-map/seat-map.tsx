import * as React from 'react'
import { Minus, Plus } from 'lucide-react'

import type { SeatMapResponse, SeatView } from '@/api/types'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/cn'
import { formatMoney } from '@/lib/format'
import { cabinLabel } from '@/lib/labels'
import { SEAT, neighbour, seatMapGeometry, type PlacedSeat } from './geometry'
import { categoryStyle, featureLabel, positionLabel, statusLabel } from './seat-labels'

interface SeatMapProps {
  map: SeatMapResponse
  /** The seat chosen for the passenger being edited. */
  selected: string | null
  /** Seats chosen for the other passengers of this booking, with a short label ("2"). */
  companions?: Record<string, string>
  /** The seat whose hold is in flight. */
  pending?: string | null
  onSelect: (seat: SeatView) => void
  className?: string
}

const FACILITY_LABEL = { LAVATORY: 'WC', GALLEY: 'Galley', CLOSET: 'Closet' } as const
const ZOOMS = [0.7, 0.85, 1, 1.2, 1.45] as const

/**
 * A top-down seat map of any aircraft, drawn from the layout data (no per-aircraft code): fuselage with nose and
 * tail, wings, exits, facilities, cabin dividers, row numbers and seat letters. Seats show their category by
 * fill and glyph, their state by pattern and icon. Keyboard: Tab into the map, arrows to move, Enter or Space
 * to choose. Hover, focus or tap shows the seat's details.
 */
export function SeatMap({ map, selected, companions = {}, pending, onSelect, className }: SeatMapProps) {
  const geometry = React.useMemo(() => seatMapGeometry(map), [map])
  const [zoom, setZoom] = React.useState(2)
  const scale = ZOOMS[zoom]!
  const [focused, setFocused] = React.useState<string | null>(null)
  const [hovered, setHovered] = React.useState<string | null>(null)
  const seatRefs = React.useRef(new Map<string, SVGGElement>())
  const patternId = React.useId().replace(/:/g, '')

  // One seat in the tab order: the selected one, else the first selectable one.
  const tabStop =
    focused ??
    selected ??
    geometry.seats.find((p) => p.seat.selectable)?.seat.code ??
    geometry.seats[0]?.seat.code ??
    null
  const active = geometry.seats.find((p) => p.seat.code === (hovered ?? focused))

  const moveFocus = (code: string | null) => {
    if (!code) return
    setFocused(code)
    const element = seatRefs.current.get(code)
    element?.focus()
    element?.scrollIntoView?.({ block: 'nearest', inline: 'nearest' })
  }

  return (
    <div className={cn('flex flex-col gap-4', className)}>
      <div className="flex items-center justify-between gap-3">
        <p className="text-sm text-muted-foreground">
          {map.aircraftName} · prices for {cabinLabel[map.cabin]}
          {map.surchargePercent > 0 && <> · high demand +{map.surchargePercent}%</>}
        </p>
        <div className="flex items-center gap-1" role="group" aria-label="Zoom">
          <Button
            variant="outline"
            size="icon"
            aria-label="Zoom out"
            disabled={zoom === 0}
            onClick={() => setZoom((z) => Math.max(0, z - 1))}
          >
            <Minus strokeWidth={1.75} aria-hidden />
          </Button>
          <Button
            variant="outline"
            size="icon"
            aria-label="Zoom in"
            disabled={zoom === ZOOMS.length - 1}
            onClick={() => setZoom((z) => Math.min(ZOOMS.length - 1, z + 1))}
          >
            <Plus strokeWidth={1.75} aria-hidden />
          </Button>
        </div>
      </div>

      <div className="relative max-h-[70vh] overflow-auto rounded-card border border-border bg-background">
        <div className="relative mx-auto" style={{ width: geometry.width * scale, height: geometry.height * scale }}>
          <svg
            role="group"
            aria-label={`Seat map, ${map.aircraftName}. Use the arrow keys to move between seats.`}
            width={geometry.width * scale}
            height={geometry.height * scale}
            viewBox={`0 0 ${geometry.width} ${geometry.height}`}
            className="block select-none"
          >
            <defs>
              <pattern
                id={`${patternId}-taken`}
                width="6"
                height="6"
                patternUnits="userSpaceOnUse"
                patternTransform="rotate(45)"
              >
                <rect width="6" height="6" fill="var(--seat-unavailable)" />
                <line x1="0" y1="0" x2="0" y2="6" stroke="var(--seat-unavailable-ink)" strokeWidth="1.5" />
              </pattern>
            </defs>

            {geometry.wings.map((d) => (
              <path key={d} d={d} fill="var(--wing)" stroke="var(--fuselage-line)" />
            ))}
            {geometry.stabilizers.map((d) => (
              <path key={d} d={d} fill="var(--wing)" stroke="var(--fuselage-line)" />
            ))}
            <path d={geometry.fuselage} fill="var(--fuselage)" stroke="var(--fuselage-line)" strokeWidth="1.5" />
            <path d={geometry.cockpit} fill="var(--facility)" aria-hidden />

            {geometry.exits.map((exit) => (
              <g key={exit.y} aria-hidden className="fill-destructive text-[9px] font-semibold">
                <path d={`M ${geometry.left - 2},${exit.y} l -9,-5 v 10 z`} />
                <path d={`M ${geometry.right + 2},${exit.y} l 9,-5 v 10 z`} />
                <text x={geometry.left + 4} y={exit.y + 3}>
                  EXIT
                </text>
                <text x={geometry.right - 4} y={exit.y + 3} textAnchor="end">
                  EXIT
                </text>
              </g>
            ))}

            {geometry.bands.map((band) =>
              band.items.map((item) => (
                <g key={`${band.y}-${item.x}`} aria-hidden>
                  <rect
                    x={item.x}
                    y={band.y + 3}
                    width={item.width}
                    height={band.height - 6}
                    rx="4"
                    fill="var(--facility)"
                  />
                  <text
                    x={item.x + item.width / 2}
                    y={band.y + band.height / 2 + 4}
                    textAnchor="middle"
                    className="fill-muted-foreground text-[10px] font-medium"
                  >
                    {FACILITY_LABEL[item.type]}
                  </text>
                </g>
              )),
            )}

            {geometry.dividers.map((divider, index) => (
              <g key={divider.y} aria-hidden>
                {index > 0 && (
                  <line
                    x1={geometry.left + 6}
                    x2={geometry.right - 6}
                    y1={divider.y + 2}
                    y2={divider.y + 2}
                    stroke="var(--fuselage-line)"
                    strokeDasharray="4 3"
                  />
                )}
                <text
                  x={(geometry.left + geometry.right) / 2}
                  y={divider.y + 18}
                  textAnchor="middle"
                  className="fill-foreground text-[11px] font-semibold tracking-wide uppercase"
                >
                  {cabinLabel[divider.cabin]}
                </text>
                {divider.letters.map(({ letter, x }) => (
                  <text
                    key={letter}
                    x={x}
                    y={divider.y + 36}
                    textAnchor="middle"
                    className="fill-muted-foreground text-[10px]"
                  >
                    {letter}
                  </text>
                ))}
              </g>
            ))}

            {geometry.rows.map((row) =>
              row.aisles.map((x) => (
                <text
                  key={`${row.row}-${x}`}
                  x={x}
                  y={row.y + 4}
                  textAnchor="middle"
                  aria-hidden
                  className="fill-muted-foreground text-[10px] tabular"
                >
                  {row.row}
                </text>
              )),
            )}

            {geometry.seats.map((placed) => (
              <Seat
                key={placed.seat.code}
                placed={placed}
                currency={map.currency}
                selected={placed.seat.code === selected}
                companion={companions[placed.seat.code]}
                pending={placed.seat.code === pending}
                tabbable={placed.seat.code === tabStop}
                takenPattern={`url(#${patternId}-taken)`}
                register={(element) => {
                  if (element) seatRefs.current.set(placed.seat.code, element)
                  else seatRefs.current.delete(placed.seat.code)
                }}
                onFocus={() => setFocused(placed.seat.code)}
                onBlur={() => setFocused((current) => (current === placed.seat.code ? null : current))}
                onHover={(on) => setHovered(on ? placed.seat.code : null)}
                onKey={(key) => moveFocus(neighbour(geometry.seats, placed.seat.code, key))}
                onSelect={() => onSelect(placed.seat)}
              />
            ))}
          </svg>

          {active && (
            <SeatCard
              placed={active}
              scale={scale}
              mapWidth={geometry.width}
              currency={map.currency}
              selected={active.seat.code === selected}
            />
          )}
        </div>
      </div>

      <SeatLegend map={map} />
    </div>
  )
}

function Seat({
  placed,
  currency,
  selected,
  companion,
  pending,
  tabbable,
  takenPattern,
  register,
  onFocus,
  onBlur,
  onHover,
  onKey,
  onSelect,
}: {
  placed: PlacedSeat
  currency: string
  selected: boolean
  companion?: string
  pending: boolean
  tabbable: boolean
  takenPattern: string
  register: (element: SVGGElement | null) => void
  onFocus: () => void
  onBlur: () => void
  onHover: (on: boolean) => void
  onKey: (key: string) => void
  onSelect: () => void
}) {
  const { seat, x, y } = placed
  const inCabin = seat.price !== null
  const unavailable =
    !selected &&
    !companion &&
    (seat.status === 'TAKEN' || seat.status === 'BLOCKED' || (seat.status === 'HELD' && !seat.mine))
  const interactive = selected || seat.selectable
  const style = categoryStyle[seat.category]
  const fill = selected
    ? 'var(--primary)'
    : companion
      ? 'var(--brand-line)'
      : unavailable
        ? takenPattern
        : inCabin
          ? style.fill
          : 'var(--seat-premium)'
  const ink = selected ? 'var(--primary-foreground)' : 'var(--seat-ink)'
  const glyph = selected
    ? '✓'
    : companion
      ? companion
      : unavailable
        ? seat.status === 'HELD'
          ? '…'
          : '×'
        : inCabin
          ? style.glyph
          : ''
  const price = seat.price === null ? null : seat.price === 0 ? 'included' : formatMoney(seat.price, currency)
  const label = [
    `Seat ${seat.code}`,
    positionLabel[seat.position].toLowerCase(),
    style.label.toLowerCase(),
    statusLabel(seat, selected),
    companion ? `chosen for passenger ${companion}` : null,
    price && !unavailable ? price : null,
  ]
    .filter(Boolean)
    .join(', ')

  return (
    <g
      ref={register}
      role="button"
      aria-label={label}
      aria-pressed={selected}
      aria-disabled={!interactive}
      tabIndex={tabbable ? 0 : -1}
      onFocus={onFocus}
      onBlur={onBlur}
      onMouseEnter={() => onHover(true)}
      onMouseLeave={() => onHover(false)}
      onClick={() => interactive && onSelect()}
      onKeyDown={(event) => {
        if (event.key === 'Enter' || event.key === ' ') {
          event.preventDefault()
          if (interactive) onSelect()
        } else if (event.key.startsWith('Arrow')) {
          event.preventDefault()
          onKey(event.key)
        }
      }}
      className={cn(
        'outline-none [&:focus-visible>.seat-ring]:opacity-100',
        interactive ? 'cursor-pointer' : 'cursor-not-allowed',
        !inCabin && !unavailable && !selected && 'opacity-45',
        pending && 'animate-pulse',
      )}
    >
      <rect
        className="seat-ring opacity-0 transition-opacity"
        x={x - 3}
        y={y - 3}
        width={SEAT + 6}
        height={SEAT + 6}
        rx="8"
        fill="none"
        stroke="var(--ring)"
        strokeWidth="2.5"
      />
      <rect x={x} y={y} width={SEAT} height={SEAT - 4} rx="6" fill={fill} stroke="var(--fuselage-line)" />
      {/* Backrest: passengers face the nose (up). */}
      <rect x={x + 3} y={y + SEAT - 6} width={SEAT - 6} height={6} rx="2.5" fill={fill} stroke="var(--fuselage-line)" />
      {glyph && (
        <text
          x={x + SEAT / 2}
          y={y + SEAT / 2 + 2}
          textAnchor="middle"
          fill={unavailable ? 'var(--seat-unavailable-ink)' : ink}
          className="pointer-events-none text-[12px] font-semibold"
          aria-hidden
        >
          {glyph}
        </text>
      )}
    </g>
  )
}

function SeatCard({
  placed,
  scale,
  mapWidth,
  currency,
  selected,
}: {
  placed: PlacedSeat
  scale: number
  mapWidth: number
  currency: string
  selected: boolean
}) {
  const { seat, x, y } = placed
  const CARD = 220
  const right = x * scale + SEAT * scale + 10
  const left = right + CARD > mapWidth * scale ? x * scale - CARD - 10 : right
  return (
    <div
      aria-hidden
      className="pointer-events-none absolute z-10 w-[220px] rounded-xl border border-border bg-popover p-3 text-sm shadow-[var(--shadow-float)]"
      style={{ left: Math.max(4, left), top: Math.max(4, y * scale - 12) }}
    >
      <div className="flex items-baseline justify-between gap-2">
        <span className="font-semibold">Seat {seat.code}</span>
        <span className="font-medium tabular">
          {seat.price === null
            ? cabinLabel[seat.cabin]
            : seat.price === 0
              ? 'Included'
              : formatMoney(seat.price, currency)}
        </span>
      </div>
      <p className="text-muted-foreground">
        {positionLabel[seat.position]} · {seat.legroom === 'EXTRA' ? 'Extra legroom' : 'Standard legroom'}
      </p>
      <p className="text-muted-foreground">{categoryStyle[seat.category].label}</p>
      {seat.features.length > 0 && (
        <ul className="mt-1.5 list-disc pl-4 text-caption text-muted-foreground">
          {seat.features.map((feature) => (
            <li key={feature}>{featureLabel[feature]}</li>
          ))}
        </ul>
      )}
      <p className="mt-1.5 text-caption font-medium">
        {selected ? 'Your seat' : seat.selectable ? 'Available' : statusLabel(seat, false)}
      </p>
    </div>
  )
}

/** Prices by category for this passenger, and how each state is drawn. */
export function SeatLegend({ map }: { map: SeatMapResponse }) {
  const states = [
    { key: 'selected', label: 'Your seat', fill: 'var(--primary)', glyph: '✓', ink: 'var(--primary-foreground)' },
    { key: 'companion', label: 'Another passenger in this booking', fill: 'var(--brand-line)', glyph: '2' },
    { key: 'taken', label: 'Taken', fill: 'var(--seat-unavailable)', glyph: '×', ink: 'var(--seat-unavailable-ink)' },
    {
      key: 'held',
      label: 'Being chosen by someone else',
      fill: 'var(--seat-unavailable)',
      glyph: '…',
      ink: 'var(--seat-unavailable-ink)',
    },
  ]
  return (
    <div className="grid gap-4 sm:grid-cols-2">
      <section aria-labelledby="seat-legend-prices">
        <h3 id="seat-legend-prices" className="mb-2 micro-label">
          Seat prices
        </h3>
        <ul className="flex flex-col gap-1.5 text-sm">
          {map.legend.map((band) => {
            const style = categoryStyle[band.category]
            return (
              <li key={band.category} className="flex items-center gap-2.5">
                <Swatch fill={style.fill} glyph={style.glyph} />
                <span className="flex-1">{style.label}</span>
                <span className="font-medium tabular">
                  {band.price === 0 ? 'Included' : formatMoney(band.price, map.currency)}
                </span>
                <span className="sr-only">, {band.available} available</span>
              </li>
            )
          })}
        </ul>
      </section>
      <section aria-labelledby="seat-legend-states">
        <h3 id="seat-legend-states" className="mb-2 micro-label">
          Key
        </h3>
        <ul className="flex flex-col gap-1.5 text-sm">
          {states.map((state) => (
            <li key={state.key} className="flex items-center gap-2.5">
              <Swatch fill={state.fill} glyph={state.glyph} ink={state.ink} />
              {state.label}
            </li>
          ))}
          <li className="flex items-center gap-2.5">
            <Swatch fill="var(--seat-premium)" glyph="" faded />
            Another cabin
          </li>
        </ul>
      </section>
    </div>
  )
}

function Swatch({ fill, glyph, ink, faded }: { fill: string; glyph: string; ink?: string; faded?: boolean }) {
  return (
    <svg width="22" height="22" viewBox="0 0 22 22" aria-hidden className={cn('shrink-0', faded && 'opacity-45')}>
      <rect x="1" y="1" width="20" height="20" rx="5" fill={fill} stroke="var(--fuselage-line)" />
      {glyph && (
        <text x="11" y="15" textAnchor="middle" fill={ink ?? 'var(--seat-ink)'} className="text-[11px] font-semibold">
          {glyph}
        </text>
      )}
    </svg>
  )
}
