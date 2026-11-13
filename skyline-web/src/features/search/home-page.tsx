import * as React from 'react'
import { ArrowRight, CircleUser, Clock3, Luggage, Plane, Smartphone, Ticket, TicketCheck } from 'lucide-react'
import { Tabs } from 'radix-ui'
import { Link, useSearchParams } from 'react-router'

import type { AirportResponse, RouteResponse } from '@/api/types'
import { useAirports } from '@/components/skyline/pickers'
import { Stagger } from '@/components/skyline/states'
import { Skeleton } from '@/components/ui/skeleton'
import { cn } from '@/lib/cn'
import { formatDuration } from '@/lib/format'
import { useDocumentTitle } from '@/lib/hooks'
import heroPhoto from '@/assets/photos/hero-wing.webp'
import istanbulPhoto from '@/assets/photos/istanbul.webp'
import londonPhoto from '@/assets/photos/london.webp'
import newYorkPhoto from '@/assets/photos/new-york.webp'
import { useRoutes } from './queries'
import { SearchCard, type SearchValues } from './search-card'

/** The Manage booking and Check-in tabs load the first time they're opened (they bring zod with them). */
const ManageQuickForm = React.lazy(() =>
  import('./home-quick-forms').then((module) => ({ default: module.ManageQuickForm })),
)
const CheckinQuickForm = React.lazy(() =>
  import('./home-quick-forms').then((module) => ({ default: module.CheckinQuickForm })),
)

/** Destination photos by IATA code (Unsplash, see README "Photo credits"); other airports get a gradient. */
const DESTINATION_PHOTOS: Record<string, string> = {
  IST: istanbulPhoto,
  LHR: londonPhoto,
  JFK: newYorkPhoto,
}

type BookingTab = 'flights' | 'manage' | 'checkin'

const tabTrigger = cn(
  'relative inline-flex h-12 shrink-0 cursor-pointer items-center gap-2 px-3 text-sm font-semibold text-muted-foreground focus-ring transition-colors duration-200 outline-none hover:text-foreground sm:px-5',
  "after:absolute after:inset-x-3 after:bottom-0 after:h-[3px] after:rounded-t-sm after:content-[''] data-[state=active]:text-primary data-[state=active]:after:bg-primary",
)

function FormFallback() {
  return <Skeleton className="h-[136px] rounded-xl" />
}

/** The booking box: flight search, booking lookup and check-in, as tabs. */
function BookingBox({
  prefill,
  formKey,
  tab,
  onTabChange,
}: {
  prefill: Partial<SearchValues>
  formKey: number
  tab: BookingTab
  onTabChange: (tab: BookingTab) => void
}) {
  const iconProps = { className: 'size-4', strokeWidth: 2, 'aria-hidden': true } as const
  return (
    <Tabs.Root
      value={tab}
      onValueChange={(value) => onTabChange(value as BookingTab)}
      className="overflow-hidden rounded-card border border-border bg-card shadow-float"
    >
      <Tabs.List
        aria-label="What do you want to do?"
        className="flex overflow-x-auto border-b border-border px-1 sm:px-2"
      >
        <Tabs.Trigger value="flights" className={tabTrigger}>
          <Plane {...iconProps} />
          Flights
        </Tabs.Trigger>
        <Tabs.Trigger value="manage" className={tabTrigger}>
          <Ticket {...iconProps} />
          Manage<span className="hidden sm:inline">&nbsp;booking</span>
        </Tabs.Trigger>
        <Tabs.Trigger value="checkin" className={tabTrigger}>
          <TicketCheck {...iconProps} />
          Check-in
        </Tabs.Trigger>
      </Tabs.List>
      <Tabs.Content value="flights" className="outline-none">
        <SearchCard key={formKey} initial={prefill} className="rounded-none border-0 bg-transparent shadow-none" />
      </Tabs.Content>
      <Tabs.Content value="manage" className="p-5 outline-none sm:p-6">
        <React.Suspense fallback={<FormFallback />}>
          <ManageQuickForm />
        </React.Suspense>
      </Tabs.Content>
      <Tabs.Content value="checkin" className="p-5 outline-none sm:p-6">
        <React.Suspense fallback={<FormFallback />}>
          <CheckinQuickForm />
        </React.Suspense>
      </Tabs.Content>
    </Tabs.Root>
  )
}

function Hero() {
  return (
    <section className="relative isolate overflow-hidden bg-scrim">
      <img
        src={heroPhoto}
        alt=""
        width={1920}
        height={1280}
        fetchPriority="high"
        decoding="async"
        className="absolute inset-0 -z-20 h-full w-full object-cover object-[65%_60%]"
      />
      <div className="absolute inset-0 -z-10 hero-scrim" aria-hidden />
      <div className="mx-auto flex max-w-[1200px] flex-col gap-3 px-4 pt-14 pb-40 text-band-foreground sm:px-6 sm:pt-24 sm:pb-56">
        <h1 className="max-w-xl text-[2.25rem] leading-[1.1] font-bold tracking-[-0.015em] sm:text-[3rem]">
          Find your next flight
        </h1>
        <p className="max-w-md text-lg text-band-foreground">
          Book direct, manage your trip and check in online from 48 hours before departure.
        </p>
      </div>
    </section>
  )
}

const SHORTCUTS = [
  { to: '/trips', icon: Luggage, label: 'My trips' },
  { to: '/checkin', icon: TicketCheck, label: 'Online check-in' },
  { to: '/manage', icon: Ticket, label: 'Manage booking' },
  { to: '/register', icon: CircleUser, label: 'Create an account' },
]

function Shortcuts() {
  return (
    <nav
      aria-label="Shortcuts"
      className="grid grid-cols-2 overflow-hidden rounded-card border border-border bg-card sm:grid-cols-4"
    >
      {SHORTCUTS.map(({ to, icon: Icon, label }, index) => (
        <Link
          key={to}
          to={to}
          className={cn(
            'flex flex-col items-center justify-center gap-2 px-3 py-5 text-center text-sm font-medium focus-ring transition-colors duration-200 hover:bg-elevated hover:text-primary',
            index % 2 === 1 && 'border-l border-border',
            index > 1 && 'border-t border-border sm:border-t-0',
            index === 2 && 'sm:border-l',
          )}
        >
          <Icon className="size-6 text-primary" strokeWidth={1.75} aria-hidden />
          {label}
        </Link>
      ))}
    </nav>
  )
}

/** A stable fallback gradient for a destination without a photo. */
function artFor(key: string) {
  let hash = 0
  for (const char of key) hash = (hash * 31 + char.charCodeAt(0)) % 997
  return ['dest-art-0', 'dest-art-1', 'dest-art-2', 'dest-art-3', 'dest-art-4', 'dest-art-5'][hash % 6]
}

interface Destination {
  airport: AirportResponse
  /** Direct routes into this airport. */
  inbound: RouteResponse[]
}

function destinationDetail({ airport, inbound }: Destination) {
  const first = inbound[0]
  if (!first) return airport.name
  if (inbound.length === 1)
    return `Direct from ${first.origin.city} · ${formatDuration(first.estimatedDurationMinutes)}`
  return `Direct from ${inbound.map((route) => route.origin.city).join(', ')}`
}

function DestinationCard({ destination, onPick }: { destination: Destination; onPick: (d: Destination) => void }) {
  const { airport } = destination
  const photo = DESTINATION_PHOTOS[airport.iataCode]
  return (
    <button
      type="button"
      onClick={() => onPick(destination)}
      className="group flex h-full w-full cursor-pointer flex-col overflow-hidden rounded-card border border-border bg-card text-left shadow-card focus-ring transition-shadow duration-200 hover:shadow-float"
    >
      <div className={cn('relative aspect-[3/2] w-full overflow-hidden', !photo && artFor(airport.iataCode))}>
        {photo && (
          <img
            src={photo}
            alt=""
            width={800}
            height={533}
            loading="lazy"
            decoding="async"
            className="h-full w-full object-cover transition-transform duration-500 group-hover:scale-[1.03] motion-reduce:group-hover:scale-100"
          />
        )}
      </div>
      <div className="flex flex-1 items-end justify-between gap-4 p-4 sm:p-5">
        <div className="flex min-w-0 flex-col gap-1">
          <span className="text-h3 font-bold">
            {airport.city}{' '}
            <span className="font-mono text-sm font-medium text-muted-foreground">{airport.iataCode}</span>
          </span>
          <span className="truncate text-sm text-muted-foreground">{destinationDetail(destination)}</span>
        </div>
        <span className="inline-flex shrink-0 items-center gap-1 text-sm font-semibold text-primary">
          Search
          <ArrowRight
            className="size-4 transition-transform duration-200 group-hover:translate-x-0.5"
            strokeWidth={2}
            aria-hidden
          />
        </span>
      </div>
    </button>
  )
}

/** Every airport we serve, with its photo and the direct routes into it. */
function Destinations({ onPick }: { onPick: (d: Destination) => void }) {
  const airports = useAirports()
  const routes = useRoutes()
  if (airports.isError || (!airports.isLoading && !airports.data?.length)) return null
  const destinations: Destination[] = (airports.data ?? []).map((airport) => ({
    airport,
    inbound: (routes.data ?? []).filter((route) => route.destination.iataCode === airport.iataCode),
  }))
  // Airports with direct routes first, then alphabetically.
  destinations.sort(
    (a, b) =>
      Number(b.inbound.length > 0) - Number(a.inbound.length > 0) || a.airport.city.localeCompare(b.airport.city),
  )
  return (
    <section aria-labelledby="destinations" className="flex flex-col gap-5">
      <div className="flex flex-col gap-1">
        <h2 id="destinations" className="text-h2 font-bold">
          Where we fly
        </h2>
        <p className="text-muted-foreground">Pick a destination to start your search.</p>
      </div>
      <div className="-mx-4 flex snap-x gap-4 overflow-x-auto px-4 pb-2 sm:mx-0 sm:grid sm:grid-cols-2 sm:px-0 lg:grid-cols-3">
        {airports.isLoading
          ? Array.from({ length: 3 }, (_, i) => (
              <Skeleton key={i} className="aspect-[4/3] w-72 shrink-0 rounded-card sm:w-auto" />
            ))
          : destinations.slice(0, 6).map((destination, index) => (
              <Stagger key={destination.airport.id} index={index} className="w-72 shrink-0 snap-start sm:w-auto">
                <DestinationCard destination={destination} onPick={onPick} />
              </Stagger>
            ))}
      </div>
    </section>
  )
}

const GOOD_TO_KNOW = [
  {
    icon: Clock3,
    title: 'Check-in times',
    body: 'Online check-in opens 48 hours before departure and closes 1 hour before.',
  },
  {
    icon: TicketCheck,
    title: 'Seats',
    body: 'Your seat is assigned when you check in, at no extra cost.',
  },
  {
    icon: Smartphone,
    title: 'Boarding pass',
    body: 'Show it on your phone, print it, or save it as an image.',
  },
]

function GoodToKnow() {
  return (
    <section aria-labelledby="good-to-know" className="flex flex-col gap-5">
      <h2 id="good-to-know" className="text-h2 font-bold">
        Good to know before you fly
      </h2>
      <div className="grid overflow-hidden rounded-card border border-border bg-card md:grid-cols-3">
        {GOOD_TO_KNOW.map(({ icon: Icon, title, body }, index) => (
          <div
            key={title}
            className={cn('flex gap-4 p-5 sm:p-6', index > 0 && 'border-t border-border md:border-t-0 md:border-l')}
          >
            <Icon className="mt-0.5 size-6 shrink-0 text-primary" strokeWidth={1.75} aria-hidden />
            <div className="flex flex-col gap-1">
              <h3 className="font-semibold">{title}</h3>
              <p className="text-sm text-muted-foreground">{body}</p>
            </div>
          </div>
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
  const [tab, setTab] = React.useState<BookingTab>('flights')
  const boxRef = React.useRef<HTMLDivElement>(null)

  function pickDestination({ airport, inbound }: Destination) {
    setPrefill((current) => ({
      ...current,
      from: inbound[0]?.origin.iataCode ?? (current.from === airport.iataCode ? undefined : current.from),
      to: airport.iataCode,
    }))
    setFormKey((key) => key + 1)
    setTab('flights')
    boxRef.current?.scrollIntoView({ behavior: 'smooth', block: 'center' })
    requestAnimationFrame(() => boxRef.current?.querySelector<HTMLElement>('[role="combobox"]')?.focus())
  }

  return (
    <>
      <Hero />
      {/* The booking box overlaps the bottom of the photo. */}
      <div ref={boxRef} className="relative z-10 mx-auto -mt-32 max-w-[1200px] px-4 sm:-mt-36 sm:px-6">
        <BookingBox prefill={prefill} formKey={formKey} tab={tab} onTabChange={setTab} />
      </div>

      <div className="mx-auto flex max-w-[1200px] flex-col gap-14 px-4 pt-6 pb-20 sm:gap-20 sm:px-6 sm:pb-24">
        <Shortcuts />
        <Destinations onPick={pickDestination} />
        <GoodToKnow />
      </div>
    </>
  )
}
