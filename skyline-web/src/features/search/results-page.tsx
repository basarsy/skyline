import * as React from 'react'
import { addDays, format } from 'date-fns'
import { ArrowRight, CalendarSearch } from 'lucide-react'
import { Link, useSearchParams } from 'react-router'

import type { CabinClass, FlightResponse } from '@/api/types'
import { useAuth } from '@/auth/use-auth'
import { Page } from '@/components/layout/app-shell'
import { FlightCard } from '@/components/skyline/flight'
import { PriceTag } from '@/components/skyline/price-tag'
import { SegmentedControl } from '@/components/skyline/segmented-control'
import { EmptyState, ErrorState, LoadingRegion, SkeletonCard, Stagger } from '@/components/skyline/states'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/cn'
import { isoFromLocalDate, localDateFromIso, toUtcDay } from '@/lib/format'
import { useDocumentTitle } from '@/lib/hooks'
import { cabinLabel } from '@/lib/labels'
import { estimatePrice } from '@/lib/pricing'
import { isBookable } from '@/lib/flight-status'
import { useFlightSearch, usePrefetchSearch } from './queries'
import { SearchCard } from './search-card'

const CABIN_VALUES = ['ECONOMY', 'BUSINESS', 'FIRST'] as const
type Sort = 'departureTime,asc' | 'departureTime,desc'

function useSearchState() {
  const [params, setParams] = useSearchParams()
  const from = params.get('from')?.toUpperCase() ?? ''
  const to = params.get('to')?.toUpperCase() ?? ''
  const date = params.get('date') ?? ''
  const cabinParam = params.get('cabin')
  const cabin: CabinClass = CABIN_VALUES.includes(cabinParam as CabinClass) ? (cabinParam as CabinClass) : 'ECONOMY'
  const sort: Sort = params.get('sort') === 'departureTime,desc' ? 'departureTime,desc' : 'departureTime,asc'
  const valid = /^[A-Z]{3}$/.test(from) && /^[A-Z]{3}$/.test(to) && /^\d{4}-\d{2}-\d{2}$/.test(date)

  const update = (next: Record<string, string>) =>
    setParams(
      (current) => {
        const copy = new URLSearchParams(current)
        Object.entries(next).forEach(([key, value]) => copy.set(key, value))
        return copy
      },
      { replace: false },
    )

  return { from, to, date, cabin, sort, valid, update }
}

/** ±3 days around the selected date; hovering a day prefetches its results. */
function DateStrip({
  date,
  onPick,
  prefetch,
}: {
  date: string
  onPick: (date: string) => void
  prefetch: (date: string) => void
}) {
  const selected = localDateFromIso(date)
  if (!selected) return null
  const today = toUtcDay(new Date())
  const days = Array.from({ length: 7 }, (_, i) => addDays(selected, i - 3))
  return (
    <nav aria-label="Nearby dates" className="-mx-4 overflow-x-auto px-4 sm:mx-0 sm:px-0">
      <ul className="flex min-w-max gap-2">
        {days.map((day) => {
          const iso = isoFromLocalDate(day)
          const active = iso === date
          const past = iso < today
          return (
            <li key={iso}>
              <button
                type="button"
                disabled={past}
                aria-current={active ? 'date' : undefined}
                onClick={() => onPick(iso)}
                onMouseEnter={() => !past && !active && prefetch(iso)}
                onFocus={() => !past && !active && prefetch(iso)}
                className={cn(
                  'flex h-16 w-20 cursor-pointer flex-col items-center justify-center gap-0.5 rounded-2xl text-sm focus-ring transition-colors duration-200 disabled:cursor-not-allowed disabled:opacity-35',
                  active ? 'bg-primary text-primary-foreground' : 'bg-card ring-1 ring-border hover:bg-elevated',
                )}
              >
                <span className={cn('text-caption', active ? 'font-medium' : 'text-muted-foreground')}>
                  {format(day, 'EEE')}
                </span>
                <span className="font-semibold tabular">{format(day, 'd MMM')}</span>
              </button>
            </li>
          )
        })}
      </ul>
    </nav>
  )
}

function ResultAction({ flight, cabin }: { flight: FlightResponse; cabin: CabinClass }) {
  const { isStaff } = useAuth()
  if (isStaff) {
    return (
      <Button asChild variant="outline">
        <Link to={`/ops/flights/${flight.id}`}>
          View in ops
          <ArrowRight strokeWidth={1.75} aria-hidden />
        </Link>
      </Button>
    )
  }
  if (!isBookable(flight)) {
    return <Button disabled>{flight.availableSeats <= 0 ? 'Sold out' : 'Not bookable'}</Button>
  }
  return (
    <Button asChild>
      <Link to={`/book/${flight.id}?cabin=${cabin}`} aria-label={`Select flight ${flight.flightNumber}`}>
        Select
      </Link>
    </Button>
  )
}

export function Component() {
  const { from, to, date, cabin, sort, valid, update } = useSearchState()
  useDocumentTitle(valid ? `${from} to ${to}` : 'Search flights')
  const params = valid ? { origin: from, destination: to, date, sort } : null
  const query = useFlightSearch(params)
  const prefetchSearch = usePrefetchSearch()
  const flights = query.data?.pages.flatMap((page) => page.content) ?? []
  const total = query.data?.pages[0]?.totalElements ?? 0
  const resultsRef = React.useRef<HTMLHeadingElement>(null)

  const prefetch = (day: string) => valid && void prefetchSearch({ origin: from, destination: to, date: day, sort })
  const neighbours = valid
    ? [-1, 1]
        .map((offset) => isoFromLocalDate(addDays(localDateFromIso(date)!, offset)))
        .filter((d) => d >= toUtcDay(new Date()))
    : []

  return (
    <>
      <section aria-label="Your search" className="sky-band">
        <div className="mx-auto flex max-w-[1200px] flex-col gap-5 px-4 pt-8 pb-16 sm:px-6 sm:pt-10 sm:pb-20">
          <p className="font-display text-h3 font-semibold tracking-[-0.02em]">
            {valid ? (
              <>
                {from} <span className="text-sky-muted">to</span> {to}
              </>
            ) : (
              'Find your flight'
            )}
          </p>
        </div>
      </section>
      <Page className="flex flex-col gap-6 pt-0 sm:gap-8 sm:pt-0">
        <SearchCard
          key={`${from}-${to}-${date}-${cabin}`}
          initial={{ from, to, date, cabin }}
          variant="bar"
          className="-mt-12 shadow-float sm:-mt-14"
        />

        {!valid ? (
          <EmptyState
            icon={<CalendarSearch strokeWidth={1.75} aria-hidden />}
            title="Where to?"
            description="Choose where you’re flying from and to, and a date."
          />
        ) : (
          <>
            <DateStrip date={date} onPick={(day) => update({ date: day })} prefetch={prefetch} />

            <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
              <h1 ref={resultsRef} className="text-h3 font-semibold" aria-live="polite">
                {query.isSuccess
                  ? `${total} ${total === 1 ? 'flight' : 'flights'} from ${from} to ${to}`
                  : `Flights from ${from} to ${to}`}
                <span className="ml-2 font-sans text-sm font-normal text-muted-foreground">
                  {cabinLabel[cabin]} · prices are estimates
                </span>
              </h1>
              <SegmentedControl<Sort>
                label="Sort by departure"
                size="sm"
                value={sort}
                onValueChange={(value) => update({ sort: value })}
                options={[
                  { value: 'departureTime,asc', label: 'Earliest first' },
                  { value: 'departureTime,desc', label: 'Latest first' },
                ]}
              />
            </div>

            {query.isPending ? (
              <LoadingRegion label="Loading flights">
                <div className="flex flex-col gap-3">
                  {[0, 1, 2].map((i) => (
                    <SkeletonCard key={i} className="h-[214px] md:h-[150px]" />
                  ))}
                </div>
              </LoadingRegion>
            ) : query.isError ? (
              <ErrorState error={query.error} onRetry={() => void query.refetch()} title="We couldn’t load flights" />
            ) : flights.length === 0 ? (
              <EmptyState
                title="No flights on this day"
                description={`There are no bookable flights from ${from} to ${to} on this date. Try a nearby day, or another route.`}
                action={
                  <>
                    {neighbours.map((day) => (
                      <Button key={day} variant="outline" onClick={() => update({ date: day })}>
                        Try {format(localDateFromIso(day)!, 'EEE d MMM')}
                      </Button>
                    ))}
                    <Button asChild variant="ghost">
                      <Link to="/">Change route</Link>
                    </Button>
                  </>
                }
              />
            ) : (
              <div className="flex flex-col gap-3">
                {flights.map((flight, index) => (
                  <Stagger key={flight.id} index={index}>
                    <FlightCard
                      flight={flight}
                      price={
                        <PriceTag
                          amount={estimatePrice(flight, cabin).amount}
                          currency={flight.currency}
                          size="large"
                          label="Estimated"
                        />
                      }
                      action={<ResultAction flight={flight} cabin={cabin} />}
                    />
                  </Stagger>
                ))}
                {query.hasNextPage && (
                  <Button
                    variant="outline"
                    className="self-center"
                    loading={query.isFetchingNextPage}
                    onClick={() => void query.fetchNextPage()}
                  >
                    Load more
                  </Button>
                )}
              </div>
            )}
          </>
        )}
      </Page>
    </>
  )
}
