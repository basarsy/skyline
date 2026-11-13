import * as React from 'react'
import { ArrowRight, Armchair, Clock3, Search, Smartphone, Ticket, TicketCheck, Zap } from 'lucide-react'
import { Link, useSearchParams } from 'react-router'

import type { RouteResponse } from '@/api/types'
import { PlaneGlyph } from '@/components/skyline/brand'
import { Stagger } from '@/components/skyline/states'
import { Skeleton } from '@/components/ui/skeleton'
import { cn } from '@/lib/cn'
import { formatDuration } from '@/lib/format'
import { useDocumentTitle } from '@/lib/hooks'
import { useRoutes } from './queries'
import { SearchCard, type SearchValues } from './search-card'

/** Stars and dashed flight arcs over the sky band; the dashes drift slowly (static with reduced motion). */
function SkyArt() {
  return (
    <svg
      aria-hidden
      viewBox="0 0 1200 560"
      preserveAspectRatio="xMidYMid slice"
      className="pointer-events-none absolute inset-0 -z-10 h-full w-full text-sky-line"
    >
      <defs>
        <pattern id="stars" width="56" height="56" patternUnits="userSpaceOnUse">
          <circle cx="6" cy="8" r="1" fill="currentColor" />
          <circle cx="34" cy="30" r="0.75" fill="currentColor" />
          <circle cx="18" cy="46" r="0.5" fill="currentColor" />
        </pattern>
      </defs>
      <rect width="1200" height="560" fill="url(#stars)" />
      {[
        'M-40 470 C 260 110, 640 60, 1240 330',
        'M140 560 C 380 240, 820 150, 1240 150',
        'M-60 290 C 300 150, 700 290, 1260 70',
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
      <circle cx="1040" cy="118" r="4" className="fill-spark" />
      <circle cx="1040" cy="118" r="12" className="fill-spark opacity-25" />
    </svg>
  )
}

const PERKS = [
  { icon: Zap, label: 'Book in a minute' },
  { icon: Smartphone, label: 'Boarding pass on your phone' },
  { icon: Armchair, label: 'Seat assigned at check-in' },
]

function Hero() {
  return (
    <section className="relative isolate overflow-hidden sky-band">
      <SkyArt />
      <div className="mx-auto flex max-w-[1200px] flex-col gap-8 px-4 pt-14 pb-36 sm:px-6 sm:pt-24 sm:pb-44">
        <div className="flex max-w-3xl flex-col gap-5">
          <span className="inline-flex w-fit items-center gap-2 rounded-full border border-sky-line px-3 py-1.5 text-xs font-medium text-sky-muted">
            <span className="size-1.5 rounded-full bg-spark" aria-hidden />
            Fares in real time, straight from the schedule
          </span>
          <h1 className="text-display-sm font-semibold tracking-[-0.035em] sm:text-display lg:text-[4.5rem] lg:leading-[1.02]">
            Fly somewhere
            <br />
            worth it.
          </h1>
          <p className="max-w-lg text-lg text-sky-muted">
            Search our network, book in a minute, and check in from your phone.
          </p>
        </div>
        <ul className="flex flex-wrap gap-x-6 gap-y-3 text-sm text-sky-muted">
          {PERKS.map(({ icon: Icon, label }) => (
            <li key={label} className="inline-flex items-center gap-2">
              <Icon className="size-4 text-sky-foreground" strokeWidth={1.75} aria-hidden />
              {label}
            </li>
          ))}
        </ul>
      </div>
    </section>
  )
}

/** A stable artwork for a route, so the same route always gets the same colors. */
function artFor(route: RouteResponse) {
  const key = route.origin.iataCode + route.destination.iataCode
  let hash = 0
  for (const char of key) hash = (hash * 31 + char.charCodeAt(0)) % 997
  return ['dest-art-0', 'dest-art-1', 'dest-art-2', 'dest-art-3', 'dest-art-4', 'dest-art-5'][hash % 6]
}

function RouteCard({ route, onPick }: { route: RouteResponse; onPick: (route: RouteResponse) => void }) {
  return (
    <button
      type="button"
      onClick={() => onPick(route)}
      className="group flex h-full w-full cursor-pointer flex-col overflow-hidden rounded-card border border-border bg-card text-left shadow-card focus-ring transition-[transform,box-shadow] duration-200 hover:-translate-y-0.5 hover:shadow-float active:scale-[0.98] motion-reduce:hover:translate-y-0"
    >
      <div className={cn('relative flex h-36 flex-col justify-between p-5 text-sky-foreground', artFor(route))}>
        <span className="font-mono text-sm font-medium opacity-90">
          {route.origin.iataCode} → {route.destination.iataCode}
        </span>
        <PlaneGlyph className="absolute top-5 right-5 size-6 rotate-45 opacity-80 transition-transform duration-300 group-hover:translate-x-1 group-hover:-translate-y-1" />
        <span className="font-display text-[1.75rem] leading-none font-semibold tracking-[-0.03em]">
          {route.destination.city}
        </span>
      </div>
      <div className="flex flex-1 items-center justify-between gap-4 p-5">
        <div className="flex flex-col gap-0.5">
          <span className="text-sm font-medium">From {route.origin.city}</span>
          <span className="text-sm text-muted-foreground tabular">
            {formatDuration(route.estimatedDurationMinutes)} · {route.distanceKm.toLocaleString()} km
          </span>
        </div>
        <span className="grid size-10 shrink-0 place-items-center rounded-full bg-brand-soft text-primary transition-colors duration-200 group-hover:bg-primary group-hover:text-primary-foreground">
          <ArrowRight className="size-4" strokeWidth={2} aria-hidden />
        </span>
      </div>
    </button>
  )
}

function SectionHeading({
  id,
  eyebrow,
  title,
  children,
}: {
  id: string
  eyebrow: string
  title: string
  children?: React.ReactNode
}) {
  return (
    <div className="flex flex-col gap-2 sm:flex-row sm:items-end sm:justify-between">
      <div className="flex flex-col gap-2">
        <span className="text-sm font-semibold text-primary">{eyebrow}</span>
        <h2 id={id} className="text-h2 font-semibold tracking-[-0.025em]">
          {title}
        </h2>
      </div>
      {children}
    </div>
  )
}

function PopularRoutes({ onPick }: { onPick: (route: RouteResponse) => void }) {
  const { data, isLoading, isError } = useRoutes()
  if (isError || (!isLoading && !data?.length)) return null
  return (
    <section aria-labelledby="popular-routes" className="flex flex-col gap-6">
      <SectionHeading id="popular-routes" eyebrow="Where to next" title="Popular routes">
        <p className="max-w-sm text-sm text-muted-foreground">Pick a route and we’ll fill in the search for you.</p>
      </SectionHeading>
      <div className="-mx-4 flex snap-x gap-4 overflow-x-auto px-4 pb-2 sm:mx-0 sm:grid sm:grid-cols-2 sm:px-0 lg:grid-cols-3">
        {isLoading
          ? Array.from({ length: 3 }, (_, i) => (
              <Skeleton key={i} className="h-[232px] w-72 shrink-0 rounded-card sm:w-auto" />
            ))
          : data?.slice(0, 6).map((route, index) => (
              <Stagger key={route.id} index={index} className="w-72 shrink-0 snap-start sm:w-auto">
                <RouteCard route={route} onPick={onPick} />
              </Stagger>
            ))}
      </div>
    </section>
  )
}

const STEPS = [
  { icon: Search, title: 'Search', body: 'Choose where you’re going and when. Prices show for every cabin.' },
  { icon: Ticket, title: 'Book', body: 'Add the passenger and confirm. Your booking reference arrives instantly.' },
  {
    icon: TicketCheck,
    title: 'Check in',
    body: 'From 48 hours before take-off. Your seat and boarding pass come with it.',
  },
]

function HowItWorks() {
  return (
    <section aria-labelledby="how-it-works" className="flex flex-col gap-6">
      <SectionHeading id="how-it-works" eyebrow="How it works" title="From search to seat in three steps" />
      <ol className="grid gap-4 md:grid-cols-3">
        {STEPS.map(({ icon: Icon, title, body }, index) => (
          <li
            key={title}
            className="relative flex flex-col gap-4 rounded-card border border-border bg-card p-6 shadow-card"
          >
            <div className="flex items-center justify-between">
              <span className="grid size-12 place-items-center rounded-2xl bg-brand-soft text-primary">
                <Icon className="size-[22px]" strokeWidth={1.75} aria-hidden />
              </span>
              <span className="rounded-full bg-brand-soft px-3 py-1 text-xs font-semibold text-primary">
                Step {index + 1}
              </span>
            </div>
            <div className="flex flex-col gap-1.5">
              <h3 className="text-h3 font-semibold">{title}</h3>
              <p className="text-sm text-muted-foreground">{body}</p>
            </div>
          </li>
        ))}
      </ol>
    </section>
  )
}

function ServiceLink({ icon, title, body, to }: { icon: React.ReactNode; title: string; body: string; to: string }) {
  return (
    <Link
      to={to}
      className="group flex items-center gap-4 rounded-2xl bg-card p-4 shadow-card focus-ring transition-colors duration-200 hover:bg-elevated sm:p-5"
    >
      <span className="grid size-12 shrink-0 place-items-center rounded-2xl bg-brand-soft text-primary [&_svg]:size-[22px]">
        {icon}
      </span>
      <span className="flex min-w-0 flex-1 flex-col gap-0.5">
        <span className="font-display font-semibold">{title}</span>
        <span className="text-sm text-muted-foreground">{body}</span>
      </span>
      <ArrowRight
        className="size-5 shrink-0 text-muted-foreground transition-transform duration-200 group-hover:translate-x-0.5 group-hover:text-foreground"
        strokeWidth={1.75}
        aria-hidden
      />
    </Link>
  )
}

function AlreadyBooked() {
  return (
    <section
      aria-labelledby="already-booked"
      className="grid gap-8 overflow-hidden rounded-[28px] border border-border bg-elevated p-6 sm:p-10 lg:grid-cols-[1fr_1.1fr] lg:items-center"
    >
      <div className="flex flex-col gap-4">
        <span className="text-sm font-semibold text-primary">Already booked?</span>
        <h2 id="already-booked" className="text-h2 font-semibold tracking-[-0.025em]">
          Your trip, in your pocket
        </h2>
        <p className="max-w-md text-muted-foreground">
          Look up any booking with its reference and last name, and check in online when the window opens.
        </p>
        <p className="inline-flex items-center gap-2 text-sm text-muted-foreground">
          <Clock3 className="size-4 text-primary" strokeWidth={1.75} aria-hidden />
          Check-in opens 48h before departure and closes 1h before.
        </p>
      </div>
      <div className="flex flex-col gap-3">
        <ServiceLink
          to="/manage"
          icon={<Ticket strokeWidth={1.75} aria-hidden />}
          title="Manage booking"
          body="Find your trip with your booking reference and last name."
        />
        <ServiceLink
          to="/checkin"
          icon={<TicketCheck strokeWidth={1.75} aria-hidden />}
          title="Online check-in"
          body="Get your seat and a boarding pass on your phone."
        />
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
      <Hero />
      {/* The search card overlaps the bottom of the sky band. */}
      <div ref={cardRef} className="relative z-10 mx-auto -mt-28 max-w-[1200px] px-4 sm:-mt-32 sm:px-6">
        <SearchCard key={formKey} initial={prefill} />
      </div>

      <div className="mx-auto flex max-w-[1200px] flex-col gap-20 px-4 pt-16 pb-20 sm:gap-28 sm:px-6 sm:pt-24 sm:pb-28">
        <PopularRoutes onPick={pickRoute} />
        <HowItWorks />
        <AlreadyBooked />
      </div>
    </>
  )
}
