import * as React from 'react'
import { ArrowRight, Clock3, Ticket, TicketCheck } from 'lucide-react'
import { Link, useSearchParams } from 'react-router'

import type { RouteResponse } from '@/api/types'
import { RouteLabel } from '@/components/skyline/flight'
import { Stagger } from '@/components/skyline/states'
import { Skeleton } from '@/components/ui/skeleton'
import { formatDuration } from '@/lib/format'
import { useDocumentTitle } from '@/lib/hooks'
import { useRoutes } from './queries'
import { SearchCard, type SearchValues } from './search-card'

/** Grayscale flight arcs drawn across the hero; the dashes drift slowly (static with reduced motion). */
function FlightArcs() {
  return (
    <svg
      aria-hidden
      viewBox="0 0 1200 520"
      preserveAspectRatio="xMidYMid slice"
      className="pointer-events-none absolute inset-0 -z-10 h-full w-full text-border"
    >
      <defs>
        <pattern id="dots" width="24" height="24" patternUnits="userSpaceOnUse">
          <circle cx="2" cy="2" r="1" fill="currentColor" />
        </pattern>
        <linearGradient id="fade" x1="0" x2="0" y1="0" y2="1">
          <stop offset="0" stopColor="currentColor" stopOpacity="1" />
          <stop offset="1" stopColor="currentColor" stopOpacity="0" />
        </linearGradient>
        <mask id="fade-mask" style={{ maskType: 'alpha' }}>
          <rect width="1200" height="520" fill="url(#fade)" />
        </mask>
      </defs>
      <g mask="url(#fade-mask)">
        <rect width="1200" height="520" fill="url(#dots)" />
        {[
          'M-40 420 C 260 60, 640 40, 1240 300',
          'M120 520 C 360 200, 820 120, 1240 120',
          'M-60 260 C 300 120, 700 260, 1260 40',
        ].map((d, index) => (
          <path
            key={d}
            d={d}
            fill="none"
            stroke="currentColor"
            strokeWidth="1.5"
            strokeDasharray="2 10"
            strokeLinecap="round"
            className="motion-safe:animate-[arc-drift_24s_linear_infinite]"
            style={{ animationDelay: `${index * -6}s` }}
          />
        ))}
      </g>
    </svg>
  )
}

function FeatureTile({ icon, title, body, to }: { icon: React.ReactNode; title: string; body: string; to?: string }) {
  const content = (
    <>
      <span className="grid size-11 place-items-center rounded-full bg-elevated [&_svg]:size-5">{icon}</span>
      <div className="flex flex-col gap-1">
        <h2 className="flex items-center gap-1.5 text-base font-semibold">
          {title}
          {to && (
            <ArrowRight
              className="size-4 transition-transform duration-200 group-hover:translate-x-0.5"
              strokeWidth={1.75}
              aria-hidden
            />
          )}
        </h2>
        <p className="text-sm text-muted-foreground">{body}</p>
      </div>
    </>
  )
  const className = 'group flex flex-col gap-4 rounded-card border border-border bg-card p-5 sm:p-6'
  return to ? (
    <Link to={to} className={`${className} focus-ring transition-colors duration-200 hover:bg-elevated`}>
      {content}
    </Link>
  ) : (
    <div className={className}>{content}</div>
  )
}

function PopularRoutes({ onPick }: { onPick: (route: RouteResponse) => void }) {
  const { data, isLoading, isError } = useRoutes()
  if (isError) return null
  return (
    <section aria-labelledby="popular-routes" className="flex flex-col gap-5">
      <div className="flex items-end justify-between gap-4">
        <h2 id="popular-routes" className="text-h2 font-semibold">
          Popular routes
        </h2>
      </div>
      <div className="-mx-4 flex snap-x gap-3 overflow-x-auto px-4 pb-2 sm:mx-0 sm:grid sm:grid-cols-2 sm:px-0 lg:grid-cols-4">
        {isLoading
          ? Array.from({ length: 4 }, (_, i) => (
              <Skeleton key={i} className="h-[132px] w-60 shrink-0 rounded-card sm:w-auto" />
            ))
          : data?.slice(0, 8).map((route, index) => (
              <Stagger key={route.id} index={index} className="w-60 shrink-0 snap-start sm:w-auto">
                <button
                  type="button"
                  onClick={() => onPick(route)}
                  className="flex h-full w-full cursor-pointer flex-col gap-4 rounded-card border border-border bg-card p-5 text-left focus-ring transition-[background-color,transform] duration-200 hover:bg-elevated active:scale-[0.98]"
                >
                  <RouteLabel route={route} className="text-xl" />
                  <span className="text-sm text-muted-foreground">
                    {route.origin.city} to {route.destination.city}
                  </span>
                  <span className="mt-auto flex gap-4 text-sm tabular">
                    <span>{formatDuration(route.estimatedDurationMinutes)}</span>
                    <span className="text-muted-foreground">{route.distanceKm.toLocaleString()} km</span>
                  </span>
                </button>
              </Stagger>
            ))}
      </div>
    </section>
  )
}

export function Component() {
  useDocumentTitle(null)
  const [params] = useSearchParams()
  const [prefill, setPrefill] = React.useState<Partial<SearchValues>>(() => ({
    from: params.get('from') ?? undefined,
    to: params.get('to') ?? undefined,
  }))
  const [formKey, setFormKey] = React.useState(0)
  const cardRef = React.useRef<HTMLDivElement>(null)

  function pickRoute(route: RouteResponse) {
    setPrefill((current) => ({ ...current, from: route.origin.iataCode, to: route.destination.iataCode }))
    setFormKey((key) => key + 1)
    cardRef.current?.scrollIntoView({ behavior: 'smooth', block: 'center' })
    requestAnimationFrame(() => cardRef.current?.querySelector<HTMLElement>('[role="combobox"]')?.focus())
  }

  return (
    <>
      <section className="relative isolate overflow-hidden">
        <FlightArcs />
        <div className="mx-auto flex max-w-[1200px] flex-col gap-10 px-4 pt-14 pb-12 sm:px-6 sm:pt-24 sm:pb-16">
          <div className="flex max-w-3xl flex-col gap-5">
            <h1 className="text-display-sm font-semibold tracking-[-0.035em] sm:text-display lg:text-[4.5rem] lg:leading-[1.02]">
              Fly somewhere
              <br />
              worth it.
            </h1>
            <p className="max-w-lg text-lg text-muted-foreground">
              Search our network, book in a minute, and check in from your phone.
            </p>
          </div>
          <div ref={cardRef}>
            <SearchCard key={formKey} initial={prefill} />
          </div>
        </div>
      </section>

      <div className="mx-auto flex max-w-[1200px] flex-col gap-16 px-4 pb-20 sm:gap-24 sm:px-6">
        <section aria-label="Services" className="grid gap-3 sm:grid-cols-3">
          <FeatureTile
            icon={<Ticket strokeWidth={1.75} aria-hidden />}
            title="Manage booking"
            body="Find your trip with your booking reference and last name."
            to="/manage"
          />
          <FeatureTile
            icon={<TicketCheck strokeWidth={1.75} aria-hidden />}
            title="Online check-in"
            body="Get your seat and a boarding pass on your phone."
            to="/checkin"
          />
          <FeatureTile
            icon={<Clock3 strokeWidth={1.75} aria-hidden />}
            title="Check-in opens 48h before departure"
            body="It closes 1 hour before take-off. Your seat is assigned at check-in."
          />
        </section>
        <PopularRoutes onPick={pickRoute} />
      </div>
    </>
  )
}
