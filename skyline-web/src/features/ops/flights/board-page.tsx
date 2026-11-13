import * as React from 'react'
import { ChevronLeft, ChevronRight, Plus, Search, X } from 'lucide-react'
import { Link, useNavigate, useSearchParams } from 'react-router'

import type { FlightResponse, FlightStatus } from '@/api/types'
import { OpsPageHeader } from '@/components/layout/ops-shell'
import { Combobox } from '@/components/skyline/combobox'
import { DataTable, type ColumnDef, type SortingState } from '@/components/skyline/data-table'
import { flightTimes, RouteLabel } from '@/components/skyline/flight'
import { DatePicker } from '@/components/skyline/pickers'
import { PriceTag } from '@/components/skyline/price-tag'
import { EmptyState, ErrorState } from '@/components/skyline/states'
import { FlightStatusBadge } from '@/components/skyline/status-badge'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { cn } from '@/lib/cn'
import { toUtcDay } from '@/lib/format'
import { useDebounced, useDocumentTitle } from '@/lib/hooks'
import { FLIGHT_STATUSES, flightStatusLabel } from '@/lib/labels'
import { capacityOf } from '@/lib/pricing'
import { useRoutes } from '@/features/search/queries'
import { useFlightBoard } from '../queries'

const PAGE_SIZE = 50

function useBoardParams() {
  const [params, setParams] = useSearchParams()
  const dateParam = params.get('date')
  const date = dateParam === 'all' ? undefined : (dateParam ?? toUtcDay(new Date()))
  const statuses = params.getAll('status').filter((s): s is FlightStatus => FLIGHT_STATUSES.includes(s as FlightStatus))
  const routeId = params.get('routeId') ?? undefined
  const flightNumber = params.get('q') ?? ''
  const page = Math.max(0, Number(params.get('page') ?? 0) || 0)
  const desc = params.get('sort') === 'desc'

  const update = (changes: Record<string, string | string[] | null>, resetPage = true) =>
    setParams(
      (current) => {
        const next = new URLSearchParams(current)
        for (const [key, value] of Object.entries(changes)) {
          next.delete(key)
          if (Array.isArray(value)) value.forEach((v) => next.append(key, v))
          else if (value !== null && value !== '') next.set(key, value)
        }
        if (resetPage && !('page' in changes)) next.delete('page')
        return next
      },
      { replace: true },
    )

  return { date, statuses, routeId, flightNumber, page, desc, update }
}

function StatTiles({ flights, loading }: { flights: FlightResponse[]; loading: boolean }) {
  const counts = FLIGHT_STATUSES.map((status) => ({ status, count: flights.filter((f) => f.status === status).length }))
  return (
    <div className="grid grid-cols-3 gap-3 sm:grid-cols-6">
      {counts.map(({ status, count }) => (
        <div key={status} className="flex flex-col gap-1 rounded-2xl border border-border bg-card px-4 py-3">
          <span className="text-caption text-muted-foreground">{flightStatusLabel[status]}</span>
          <span className={cn('hero-number text-h2', loading && 'opacity-40')}>{loading ? '–' : count}</span>
        </div>
      ))}
    </div>
  )
}

function SeatsBar({ flight }: { flight: FlightResponse }) {
  const capacity = capacityOf(flight)
  const sold = Math.max(0, capacity - flight.availableSeats)
  return (
    <div className="flex min-w-36 items-center gap-3">
      <div
        className="h-1.5 flex-1 overflow-hidden rounded-full bg-elevated"
        role="meter"
        aria-label="Seats sold"
        aria-valuemin={0}
        aria-valuemax={capacity}
        aria-valuenow={sold}
      >
        <div className="h-full rounded-full bg-foreground" style={{ width: `${(sold / capacity) * 100}%` }} />
      </div>
      <span className="text-sm tabular">
        {flight.availableSeats}
        <span className="text-muted-foreground">/{capacity}</span>
      </span>
    </div>
  )
}

export function Component() {
  useDocumentTitle('Flights · Ops')
  const navigate = useNavigate()
  const { date, statuses, routeId, flightNumber, page, desc, update } = useBoardParams()
  const [search, setSearch] = React.useState(flightNumber)
  const debounced = useDebounced(search.trim(), 300)
  const { data: routes = [] } = useRoutes()

  React.useEffect(() => {
    if (debounced !== flightNumber) update({ q: debounced || null })
    // Only react to the debounced input.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [debounced])

  const params = {
    date,
    status: statuses.length ? statuses : undefined,
    routeId,
    flightNumber: flightNumber || undefined,
    page,
    size: PAGE_SIZE,
    sort: `departureTime,${desc ? 'desc' : 'asc'}`,
  }
  const board = useFlightBoard(params)
  // Exact per-status totals for the day (and route/number filters), independent of the status chips.
  const totals = useFlightBoard({ ...params, status: undefined, page: 0, size: 500 })
  const flights = board.data?.content ?? []
  const sorting: SortingState = [{ id: 'departure', desc }]

  const columns = React.useMemo<ColumnDef<FlightResponse, unknown>[]>(
    () => [
      {
        id: 'flightNumber',
        header: 'Flight',
        enableSorting: false,
        cell: ({ row }) => <span className="font-mono font-semibold">{row.original.flightNumber}</span>,
      },
      {
        id: 'route',
        header: 'Route',
        enableSorting: false,
        cell: ({ row }) => <RouteLabel route={row.original.route} />,
      },
      {
        id: 'departure',
        header: 'Departure',
        enableSorting: true,
        cell: ({ row }) => {
          const t = flightTimes(row.original)
          return (
            <span className="flex flex-col leading-tight">
              <span className="font-medium">
                {t.departure}{' '}
                <span className="font-mono text-caption text-muted-foreground">
                  {row.original.route?.origin.iataCode}
                </span>
              </span>
              <span className="text-caption text-muted-foreground">{t.departureDate}</span>
            </span>
          )
        },
      },
      {
        id: 'arrival',
        header: 'Arrival',
        enableSorting: false,
        cell: ({ row }) => {
          const t = flightTimes(row.original)
          return (
            <span className="font-medium">
              {t.arrival}
              {t.dayOffset > 0 && <sup className="ml-0.5 text-caption text-muted-foreground">+{t.dayOffset}</sup>}{' '}
              <span className="font-mono text-caption text-muted-foreground">
                {row.original.route?.destination.iataCode}
              </span>
            </span>
          )
        },
      },
      {
        id: 'aircraft',
        header: 'Aircraft',
        enableSorting: false,
        cell: ({ row }) => <span className="font-mono">{row.original.aircraft?.tailNumber ?? '—'}</span>,
      },
      {
        id: 'status',
        header: 'Status',
        enableSorting: false,
        cell: ({ row }) => <FlightStatusBadge status={row.original.status} />,
      },
      {
        id: 'seats',
        header: 'Seats left',
        enableSorting: false,
        cell: ({ row }) => <SeatsBar flight={row.original} />,
      },
      {
        id: 'price',
        header: 'Base price',
        enableSorting: false,
        cell: ({ row }) => <PriceTag amount={row.original.basePrice} currency={row.original.currency} />,
      },
    ],
    [],
  )

  const filtersActive = statuses.length > 0 || !!routeId || !!flightNumber || date !== toUtcDay(new Date())

  return (
    <>
      <OpsPageHeader
        title="Flights"
        description={date ? `Departures on ${date} (UTC day), every status` : 'All dates, every status'}
        actions={
          <Button asChild>
            <Link to="/ops/flights/new">
              <Plus strokeWidth={1.75} aria-hidden />
              New flight
            </Link>
          </Button>
        }
      />

      <div className="mb-6 flex flex-col gap-4">
        <div className="grid gap-3 md:grid-cols-[220px_auto_1fr_1fr]">
          <div className="flex flex-col gap-1.5">
            <span id="board-date" className="micro-label">
              Date (UTC)
            </span>
            <DatePicker
              aria-describedby="board-date"
              value={date ?? null}
              onChange={(value) => update({ date: value })}
              placeholder="All dates"
              note="Dates are in UTC"
            />
          </div>
          <Button
            variant="ghost"
            className="self-end"
            onClick={() => update({ date: date ? 'all' : null })}
            aria-label={date ? 'Show all dates' : 'Back to today'}
          >
            {date ? 'All dates' : 'Today'}
          </Button>
          <div className="flex flex-col gap-1.5">
            <span id="board-route" className="micro-label">
              Route
            </span>
            <Combobox
              aria-describedby="board-route"
              value={routeId}
              onChange={(value) => update({ routeId: value })}
              placeholder="All routes"
              searchPlaceholder="Search routes"
              options={routes.map((route) => ({
                value: route.id,
                label: `${route.origin.iataCode} → ${route.destination.iataCode}`,
                keywords: [route.origin.city, route.destination.city],
                render: <RouteLabel route={route} withDuration />,
              }))}
            />
          </div>
          <div className="flex flex-col gap-1.5">
            <label htmlFor="board-number" className="micro-label">
              Flight number
            </label>
            <div className="relative">
              <Search
                className="pointer-events-none absolute top-1/2 left-4 size-4 -translate-y-1/2 text-muted-foreground"
                strokeWidth={1.75}
                aria-hidden
              />
              <Input
                id="board-number"
                value={search}
                onChange={(event) => setSearch(event.target.value)}
                placeholder="e.g. SK10"
                className="pl-11 font-mono uppercase"
                autoComplete="off"
              />
            </div>
          </div>
        </div>

        <div role="group" aria-label="Filter by status" className="flex flex-wrap gap-2">
          {FLIGHT_STATUSES.map((status) => {
            const active = statuses.includes(status)
            return (
              <button
                key={status}
                type="button"
                aria-pressed={active}
                onClick={() =>
                  update({ status: active ? statuses.filter((s) => s !== status) : [...statuses, status] })
                }
                className={cn(
                  'h-11 cursor-pointer rounded-full px-4 text-sm font-medium focus-ring transition-colors duration-200',
                  active
                    ? 'bg-primary text-primary-foreground'
                    : 'bg-elevated text-muted-foreground hover:text-foreground',
                )}
              >
                {flightStatusLabel[status]}
              </button>
            )
          })}
          {filtersActive && (
            <Button
              variant="ghost"
              size="sm"
              className="h-11"
              onClick={() => {
                setSearch('')
                update({ status: null, routeId: null, q: null, date: null })
              }}
            >
              <X strokeWidth={1.75} aria-hidden />
              Reset filters
            </Button>
          )}
        </div>
      </div>

      <section aria-label="Totals by status" className="mb-6">
        <StatTiles flights={totals.data?.content ?? []} loading={totals.isPending} />
      </section>

      {board.isError ? (
        <ErrorState error={board.error} onRetry={() => void board.refetch()} />
      ) : (
        <>
          <DataTable
            label="Flights"
            columns={columns}
            data={flights}
            loading={board.isPending}
            sorting={sorting}
            onSortingChange={(updater) => {
              const next = typeof updater === 'function' ? updater(sorting) : updater
              update({ sort: next[0]?.desc ? 'desc' : null })
            }}
            getRowId={(flight) => flight.id}
            onRowClick={(flight) => navigate(`/ops/flights/${flight.id}`)}
            rowLabel={(flight) => `Open flight ${flight.flightNumber}`}
            empty={
              <EmptyState
                className="border-0"
                title="No flights match"
                description="Try another date, or clear the filters."
                action={
                  <Button asChild variant="outline">
                    <Link to="/ops/flights/new">New flight</Link>
                  </Button>
                }
              />
            }
          />
          {board.data && board.data.totalPages > 1 && (
            <nav aria-label="Pages" className="mt-4 flex items-center justify-end gap-3 text-sm">
              <span className="text-muted-foreground tabular">
                Page {board.data.number + 1} of {board.data.totalPages} · {board.data.totalElements} flights
              </span>
              <Button
                variant="outline"
                size="icon"
                aria-label="Previous page"
                disabled={board.data.first}
                onClick={() => update({ page: String(page - 1) }, false)}
              >
                <ChevronLeft strokeWidth={1.75} aria-hidden />
              </Button>
              <Button
                variant="outline"
                size="icon"
                aria-label="Next page"
                disabled={board.data.last}
                onClick={() => update({ page: String(page + 1) }, false)}
              >
                <ChevronRight strokeWidth={1.75} aria-hidden />
              </Button>
            </nav>
          )}
        </>
      )}
    </>
  )
}
