import * as React from 'react'
import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { AnimatePresence } from 'motion/react'
import * as m from 'motion/react-m'
import { ArrowLeft, ArrowRight, Check, Plus, Trash2 } from 'lucide-react'
import { Controller, useFieldArray, useForm, useWatch } from 'react-hook-form'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router'
import { toast } from 'sonner'
import { z } from 'zod'

import { reservationsApi } from '@/api/endpoints/reservations'
import { seatsApi } from '@/api/endpoints/seats'
import { isApiError } from '@/api/http'
import { flightKeys, reservationKeys, seatKeys } from '@/api/query-keys'
import type { CabinClass, FareType, FlightResponse, GroupReservationRequest } from '@/api/types'
import { useAuthState } from '@/auth/auth-store'
import { Page } from '@/components/layout/app-shell'
import { FlightTimeline, flightTimes, RouteLabel } from '@/components/skyline/flight'
import { applyServerErrors, FormErrorSummary, FormField } from '@/components/skyline/form'
import { CountryCombobox, DatePicker } from '@/components/skyline/pickers'
import { PriceTag } from '@/components/skyline/price-tag'
import { EmptyState, ErrorState, LoadingRegion, SkeletonCard } from '@/components/skyline/states'
import { FlightStatusBadge } from '@/components/skyline/status-badge'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { estimateOffers, FARE_TYPES, fareCopy, fareLabel, includedFor, routeScope } from '@/lib/ancillaries'
import { cn } from '@/lib/cn'
import { isBookable } from '@/lib/flight-status'
import { formatLocalDate, formatMoney, isoFromLocalDate } from '@/lib/format'
import { useDocumentTitle, usePrefersReducedMotion } from '@/lib/hooks'
import { readLastPassenger, saveLastPassenger } from '@/lib/last-passenger'
import { cabinLabel, CABINS } from '@/lib/labels'
import { cabinMultiplierCopy, estimatePrice } from '@/lib/pricing'
import { useFlight } from '@/features/search/queries'
import { ExtrasPicker, extrasTotal } from '@/features/seats/extras-picker'
import { useAncillaryCatalog, useSeatMap } from '@/features/seats/queries'
import { SeatSelector, type SeatSlot } from '@/features/seats/seat-selector'

const STEPS = ['Cabin', 'Passengers', 'Seats', 'Extras', 'Review'] as const
/** The step buttons sit outside the form (shared with the mobile bar); they submit it via form=. */
const PASSENGER_FORM_ID = 'passenger-form'
const MAX_PASSENGERS = 9
type Step = 0 | 1 | 2 | 3 | 4

const passengerSchema = z.object({
  firstName: z.string().trim().min(1, 'Enter the first name').max(128, 'Use at most 128 characters'),
  lastName: z.string().trim().min(1, 'Enter the last name').max(128, 'Use at most 128 characters'),
  passportNumber: z
    .string()
    .trim()
    .min(5, 'Passport numbers have at least 5 characters')
    .max(20, 'Use at most 20 characters')
    .regex(/^[A-Za-z0-9]+$/, 'Use letters and digits only'),
  dateOfBirth: z
    .string({ error: 'Enter the date of birth' })
    .min(1, 'Enter the date of birth')
    .refine((value) => value < isoFromLocalDate(new Date()), 'The date of birth must be in the past'),
  nationality: z.string({ error: 'Choose a nationality' }).min(1, 'Choose a nationality'),
})
const formSchema = z.object({ passengers: z.array(passengerSchema).min(1).max(MAX_PASSENGERS) })
type PassengerValues = z.infer<typeof passengerSchema>
type FormValues = z.infer<typeof formSchema>
const PASSENGER_FIELDS = ['firstName', 'lastName', 'passportNumber', 'dateOfBirth', 'nationality'] as const
const FIELD_LABELS: Record<string, string> = {
  firstName: 'First name',
  lastName: 'Last name',
  passportNumber: 'Passport number',
  dateOfBirth: 'Date of birth',
  nationality: 'Nationality',
}
const EMPTY_PASSENGER: PassengerValues = {
  firstName: '',
  lastName: '',
  passportNumber: '',
  dateOfBirth: '',
  nationality: '',
}

/** A chosen (held) seat for one passenger, with its server price. */
type ChosenSeat = { seatNumber: string; price: number }

function StepIndicator({ step }: { step: Step }) {
  return (
    <ol aria-label="Booking steps" className="flex items-center gap-2">
      {STEPS.map((label, index) => {
        const state = index < step ? 'done' : index === step ? 'current' : 'todo'
        return (
          <li
            key={label}
            className="flex flex-1 flex-col gap-2"
            aria-current={state === 'current' ? 'step' : undefined}
          >
            <span
              className={cn(
                'h-1 rounded-full transition-colors duration-300',
                state === 'todo' ? 'bg-elevated' : 'bg-primary',
              )}
            />
            <span
              className={cn(
                'flex items-center gap-1.5 text-sm',
                state === 'todo' ? 'text-muted-foreground' : 'font-medium',
              )}
            >
              {state === 'done' && <Check className="size-3.5" strokeWidth={2} aria-hidden />}
              <span className={cn(state !== 'current' && 'max-sm:sr-only')}>{label}</span>
              <span className="sr-only">
                {state === 'done' ? '(done)' : state === 'current' ? '(current step)' : ''}
              </span>
            </span>
          </li>
        )
      })}
    </ol>
  )
}

/** A radio group of cards; arrows move between options, as a native radio group would. */
function CardRadios<T extends string>({
  label,
  options,
  value,
  onChange,
  render,
}: {
  label: string
  options: readonly T[]
  value: T
  onChange: (value: T) => void
  render: (option: T) => React.ReactNode
}) {
  return (
    <div role="radiogroup" aria-label={label} className="grid gap-3">
      {options.map((option) => {
        const active = option === value
        return (
          <button
            key={option}
            type="button"
            role="radio"
            aria-checked={active}
            onClick={() => onChange(option)}
            onKeyDown={(event) => {
              const index = options.indexOf(option)
              if (event.key === 'ArrowDown' || event.key === 'ArrowRight') {
                event.preventDefault()
                onChange(options[(index + 1) % options.length]!)
              } else if (event.key === 'ArrowUp' || event.key === 'ArrowLeft') {
                event.preventDefault()
                onChange(options[(index + options.length - 1) % options.length]!)
              }
            }}
            tabIndex={active ? 0 : -1}
            className={cn(
              'flex cursor-pointer items-center gap-5 rounded-card border bg-card p-5 text-left focus-ring transition-[border-color,background-color,transform] duration-200 active:scale-[0.99] sm:p-6',
              active ? 'border-primary ring-1 ring-primary' : 'border-border hover:bg-elevated',
            )}
          >
            <span
              aria-hidden
              className={cn(
                'size-5 shrink-0 rounded-full border-2 transition-all',
                active ? 'border-[6px] border-primary' : 'border-border',
              )}
            />
            {render(option)}
          </button>
        )
      })}
    </div>
  )
}

function CabinStep({
  flight,
  cabins,
  cabin,
  fare,
  onCabin,
  onFare,
}: {
  flight: FlightResponse
  cabins: readonly CabinClass[]
  cabin: CabinClass
  fare: FareType
  onCabin: (cabin: CabinClass) => void
  onFare: (fare: FareType) => void
}) {
  return (
    <div className="flex flex-col gap-8">
      <fieldset className="flex flex-col gap-4">
        <legend className="mb-4">
          <h2 tabIndex={-1} className="text-h2 font-semibold outline-none">
            Choose your cabin
          </h2>
        </legend>
        <CardRadios
          label="Cabin"
          options={cabins}
          value={cabin}
          onChange={onCabin}
          render={(option) => {
            const estimate = estimatePrice(flight, option, fare)
            return (
              <>
                <span className="flex min-w-0 flex-1 flex-col gap-1">
                  <span className="text-lg font-semibold">{cabinLabel[option]}</span>
                  <span className="text-sm text-muted-foreground">{cabinMultiplierCopy(option)}</span>
                  {estimate.surchargePercent > 0 && (
                    <span className="text-caption text-muted-foreground">
                      High demand · +{estimate.surchargePercent}%
                    </span>
                  )}
                </span>
                <PriceTag amount={estimate.amount} currency={flight.currency} size="inline" className="text-lg" />
              </>
            )
          }}
        />
        {cabins.length < CABINS.length && (
          <p className="text-sm text-muted-foreground">
            This aircraft has no{' '}
            {CABINS.filter((c) => !cabins.includes(c))
              .map((c) => cabinLabel[c])
              .join(' or ')}{' '}
            cabin.
          </p>
        )}
      </fieldset>

      <fieldset className="flex flex-col gap-4">
        <legend className="mb-4">
          <h3 className="text-h3 font-semibold">Choose your fare</h3>
        </legend>
        <CardRadios
          label="Fare"
          options={FARE_TYPES}
          value={fare}
          onChange={onFare}
          render={(option) => (
            <>
              <span className="flex min-w-0 flex-1 flex-col gap-1">
                <span className="text-lg font-semibold">{fareLabel[option]}</span>
                <span className="text-sm text-muted-foreground">
                  {cabin === 'ECONOMY' ? fareCopy[option] : 'Seat selection, bags and a meal included.'}
                </span>
              </span>
              <PriceTag
                amount={estimatePrice(flight, cabin, option).amount}
                currency={flight.currency}
                size="inline"
                className="text-lg"
              />
            </>
          )}
        />
      </fieldset>
    </div>
  )
}

export function Component() {
  const { flightId } = useParams()
  const [params, setParams] = useSearchParams()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const userId = useAuthState().user?.id
  const { data: flight, isPending, isError, error, refetch } = useFlight(flightId)
  const catalog = useAncillaryCatalog()
  useDocumentTitle('Book your flight')

  const param = <T extends string>(name: string, options: readonly T[], fallback: T): T => {
    const value = params.get(name)
    return options.includes(value as T) ? (value as T) : fallback
  }
  const setParam = (name: string, value: string) =>
    setParams(
      (current) => {
        const copy = new URLSearchParams(current)
        copy.set(name, value)
        return copy
      },
      { replace: true },
    )

  // The aircraft's cabins come from its seat map; without one (404) every cabin can be sold, as before.
  const economyMap = useSeatMap(flightId, { cabin: 'ECONOMY', fareType: 'STANDARD', passenger: 0 })
  const cabins: readonly CabinClass[] = economyMap.data
    ? CABINS.filter((c) => economyMap.data.sections.some((s) => s.cabin === c))
    : CABINS
  const cabin = param('cabin', cabins, cabins[0] ?? 'ECONOMY')
  const fare = param<FareType>('fare', FARE_TYPES, 'STANDARD')

  const [step, setStep] = React.useState<Step>(0)
  const [serverError, setServerError] = React.useState<string | null>(null)
  const [seats, setSeats] = React.useState<Record<number, ChosenSeat>>({})
  const [extras, setExtras] = React.useState<Record<number, Record<string, number>>>({})
  const [extrasFor, setExtrasFor] = React.useState(0)
  const headingRef = React.useRef<HTMLDivElement>(null)
  // Read once: defaultValues only matter on the first render, and the form re-renders on every keystroke.
  const [prefill] = React.useState(() => readLastPassenger(userId))

  const form = useForm<FormValues>({
    resolver: zodResolver(formSchema),
    defaultValues: { passengers: [{ ...EMPTY_PASSENGER, ...prefill }] },
  })
  const passengers = useFieldArray({ control: form.control, name: 'passengers' })
  const { errors, submitCount } = form.formState
  const reduceMotion = usePrefersReducedMotion()
  const values = useWatch({ control: form.control }) as FormValues
  const passengerCount = passengers.fields.length

  /** Releases every seat hold (cabin, fare or passengers changed, so the seats no longer apply). */
  const releaseSeats = React.useCallback(
    (indexes: number[]) => {
      if (!flightId) return
      for (const index of indexes) void seatsApi.releaseForBooking(flightId, index).catch(() => undefined)
      setSeats((current) => Object.fromEntries(Object.entries(current).filter(([i]) => !indexes.includes(Number(i)))))
      void queryClient.invalidateQueries({ queryKey: seatKeys.flight(flightId) })
    },
    [flightId, queryClient],
  )
  const changeCabinOrFare = (name: 'cabin' | 'fare', value: string) => {
    const held = Object.keys(seats).map(Number)
    if (held.length) {
      releaseSeats(held)
      toast.info('Your seat choices were cleared: seats are priced by cabin and fare.')
    }
    setExtras({})
    setParam(name, value)
  }

  const book = useMutation({
    mutationFn: (body: GroupReservationRequest) => reservationsApi.bookGroup(body),
    onSuccess: async (result) => {
      saveLastPassenger(userId, form.getValues().passengers[0]!)
      toast.success(result.message ?? 'Flight booked')
      // Seats changed: the flight, cached search results and the home page's fares are all stale now.
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: reservationKeys.myAll }),
        queryClient.invalidateQueries({ queryKey: flightKeys.all }),
        queryClient.invalidateQueries({ queryKey: seatKeys.all }),
      ])
      for (const reservation of result.data.reservations) {
        queryClient.setQueryData(reservationKeys.detail(reservation.id), reservation)
      }
      navigate(`/book/confirmation/${result.data.reservations[0]!.id}`, { replace: true })
    },
    onError: (err) => {
      const known = passengers.fields.flatMap((_, i) => PASSENGER_FIELDS.map((f) => `passengers[${i}].${f}`))
      const message = applyServerErrors(
        err,
        (field, value) => form.setError(field.replace(/\[(\d+)\]/g, '.$1') as never, value),
        known,
      )
      if (isApiError(err) && err.hasFieldErrors && !message) setStep(1)
      // A seat hold that lapsed: back to the seat map.
      if (isApiError(err) && err.status === 409) {
        setSeats({})
        setStep(2)
      }
      setServerError(message)
    },
  })

  const goTo = (next: Step) => {
    setStep(next)
    setServerError(null)
    requestAnimationFrame(() => headingRef.current?.querySelector<HTMLElement>('h2')?.focus())
  }

  const next = (event?: React.BaseSyntheticEvent) => form.handleSubmit(() => goTo(2))(event)

  if (isPending) {
    return (
      <Page>
        <LoadingRegion label="Loading flight">
          <SkeletonCard className="h-96" lines={5} />
        </LoadingRegion>
      </Page>
    )
  }
  if (isError) {
    return (
      <Page>
        <ErrorState error={error} onRetry={() => void refetch()} />
      </Page>
    )
  }
  if (!isBookable(flight)) {
    return (
      <Page>
        <EmptyState
          title="This flight can’t be booked"
          description={flight.availableSeats <= 0 ? 'It is fully booked.' : 'It is no longer scheduled.'}
          action={
            <Button asChild>
              <Link to="/">Search other flights</Link>
            </Button>
          }
        />
      </Page>
    )
  }

  const scope = routeScope(flight)
  const offersFor = (index: number) =>
    catalog.data ? estimateOffers(catalog.data, cabin, fare, scope, extras[index] ?? {}) : []
  const included = catalog.data ? includedFor(catalog.data, cabin, fare) : []
  const farePerPassenger = estimatePrice(flight, cabin, fare)
  const fareTotal = farePerPassenger.amount * passengerCount
  const seatTotal = Object.values(seats).reduce((sum, seat) => sum + seat.price, 0)
  const extrasSum = passengers.fields.reduce((sum, _, i) => sum + extrasTotal(offersFor(i), extras[i] ?? {}), 0)
  const total = fareTotal + seatTotal + extrasSum
  const times = flightTimes(flight)
  const busy = book.isPending || book.isSuccess
  const name = (index: number) => {
    const p = values.passengers?.[index]
    const full = `${p?.firstName ?? ''} ${p?.lastName ?? ''}`.trim()
    return full || `Passenger ${index + 1}`
  }

  const slots: SeatSlot[] = passengers.fields.map((field, index) => ({
    key: field.id,
    flightId: flight.id,
    legLabel: `${flight.route?.origin.iataCode ?? ''} → ${flight.route?.destination.iataCode ?? ''} · ${flight.flightNumber}`,
    passengerLabel: name(index),
    short: String(index + 1),
    params: { cabin, fareType: fare, passenger: index },
    seat: seats[index]?.seatNumber ?? null,
    price: seats[index]?.price ?? null,
  }))
  const slotIndex = (slot: SeatSlot) => Number(slot.short) - 1

  const submit = () => {
    if (busy) return
    setServerError(null)
    const v = form.getValues()
    book.mutate({
      passengers: v.passengers.map((p) => ({
        firstName: p.firstName.trim(),
        lastName: p.lastName.trim(),
        passportNumber: p.passportNumber.trim().toUpperCase(),
        dateOfBirth: p.dateOfBirth,
        nationality: p.nationality,
      })),
      legs: [{ flightId: flight.id, cabinClass: cabin, fareType: fare }],
      seats: Object.entries(seats).map(([index, seat]) => ({
        passengerIndex: Number(index),
        flightId: flight.id,
        seatNumber: seat.seatNumber,
      })),
      extras: Object.entries(extras).flatMap(([index, chosen]) =>
        Object.entries(chosen)
          .filter(([, quantity]) => quantity > 0)
          .map(([productCode, quantity]) => ({
            passengerIndex: Number(index),
            flightId: flight.id,
            productCode,
            quantity,
          })),
      ),
    })
  }

  const primary =
    step === 0 ? (
      <Button size="lg" className="w-full sm:w-auto" onClick={() => goTo(1)}>
        Continue
        <ArrowRight strokeWidth={1.75} aria-hidden />
      </Button>
    ) : step === 1 ? (
      <Button type="submit" form={PASSENGER_FORM_ID} size="lg" className="w-full sm:w-auto">
        Choose seats
        <ArrowRight strokeWidth={1.75} aria-hidden />
      </Button>
    ) : step === 2 ? (
      <Button size="lg" className="w-full sm:w-auto" onClick={() => goTo(3)}>
        {Object.keys(seats).length ? 'Continue' : 'Skip seats'}
        <ArrowRight strokeWidth={1.75} aria-hidden />
      </Button>
    ) : step === 3 ? (
      <Button size="lg" className="w-full sm:w-auto" onClick={() => goTo(4)}>
        Review booking
        <ArrowRight strokeWidth={1.75} aria-hidden />
      </Button>
    ) : (
      <Button size="lg" className="w-full sm:w-auto" loading={busy} onClick={submit}>
        Confirm booking
      </Button>
    )

  return (
    <Page className="flex flex-col gap-8 pb-40 md:pb-12">
      <div className="flex flex-col gap-4">
        <h1 className="text-h2 font-semibold sm:text-h1">
          {flight.route ? `${flight.route.origin.city} to ${flight.route.destination.city}` : 'Book your flight'}
        </h1>
        <div className="flex flex-wrap items-center gap-3 text-sm">
          <RouteLabel route={flight.route} />
          <span className="text-muted-foreground">
            {flight.flightNumber} · {times.departureDate} · {times.departure}
          </span>
          {flight.status === 'DELAYED' && <FlightStatusBadge status="DELAYED" />}
        </div>
        <StepIndicator step={step} />
      </div>

      <div className="grid gap-8 lg:grid-cols-[1fr_360px]">
        <div ref={headingRef} className="min-w-0">
          <AnimatePresence mode="wait" initial={false}>
            <m.div
              key={step}
              initial={reduceMotion ? false : { opacity: 0, x: 16 }}
              animate={{ opacity: 1, x: 0 }}
              exit={reduceMotion ? undefined : { opacity: 0, x: -16 }}
              transition={{ type: 'spring', stiffness: 420, damping: 38 }}
            >
              {step === 0 && (
                <CabinStep
                  flight={flight}
                  cabins={cabins}
                  cabin={cabin}
                  fare={fare}
                  onCabin={(value) => changeCabinOrFare('cabin', value)}
                  onFare={(value) => changeCabinOrFare('fare', value)}
                />
              )}

              {step === 1 && (
                <form id={PASSENGER_FORM_ID} onSubmit={next} noValidate className="flex flex-col gap-8">
                  <h2 tabIndex={-1} className="text-h2 font-semibold outline-none">
                    Who’s flying?
                  </h2>
                  <FormErrorSummary errors={errors} submitCount={submitCount} labels={FIELD_LABELS} />
                  {passengers.fields.map((field, index) => (
                    <PassengerFields
                      key={field.id}
                      index={index}
                      count={passengerCount}
                      form={form}
                      onRemove={() => {
                        // Seats and extras are kept by position: drop this passenger's, shift the rest.
                        releaseSeats(
                          Object.keys(seats)
                            .map(Number)
                            .filter((i) => i >= index),
                        )
                        setExtras({})
                        passengers.remove(index)
                      }}
                    />
                  ))}
                  {passengerCount < MAX_PASSENGERS && (
                    <Button
                      type="button"
                      variant="outline"
                      className="self-start"
                      onClick={() => passengers.append({ ...EMPTY_PASSENGER })}
                    >
                      <Plus strokeWidth={1.75} aria-hidden />
                      Add a passenger
                    </Button>
                  )}
                </form>
              )}

              {step === 2 && (
                <section aria-labelledby="seats-heading" className="flex flex-col gap-5">
                  <div className="flex flex-col gap-1">
                    <h2 id="seats-heading" tabIndex={-1} className="text-h2 font-semibold outline-none">
                      Choose your seats
                    </h2>
                    <p className="text-sm text-muted-foreground">
                      Optional. Skip it and we’ll assign a free seat at check-in. A seat you pick is held for you for 15
                      minutes.
                    </p>
                  </div>
                  <SeatSelector
                    slots={slots}
                    currency={flight.currency}
                    onChoose={async (slot, seat) => {
                      const index = slotIndex(slot)
                      const result = await seatsApi.holdForBooking({
                        flightId: flight.id,
                        seatNumber: seat.code,
                        passengerIndex: index,
                        cabinClass: cabin,
                        fareType: fare,
                      })
                      setSeats((current) => ({
                        ...current,
                        [index]: { seatNumber: result.data.seatNumber, price: result.data.price },
                      }))
                    }}
                    onClear={async (slot) => {
                      const index = slotIndex(slot)
                      await seatsApi.releaseForBooking(flight.id, index)
                      setSeats((current) =>
                        Object.fromEntries(Object.entries(current).filter(([i]) => Number(i) !== index)),
                      )
                    }}
                  />
                </section>
              )}

              {step === 3 && (
                <section aria-labelledby="extras-heading" className="flex flex-col gap-5">
                  <div className="flex flex-col gap-1">
                    <h2 id="extras-heading" tabIndex={-1} className="text-h2 font-semibold outline-none">
                      Bags and extras
                    </h2>
                    <p className="text-sm text-muted-foreground">
                      Cheaper now than at the airport. You can also add them later in My trips or at check-in.
                    </p>
                  </div>
                  {passengerCount > 1 && (
                    <div role="tablist" aria-label="Passenger" className="flex flex-wrap gap-2">
                      {passengers.fields.map((field, index) => (
                        <button
                          key={field.id}
                          type="button"
                          role="tab"
                          aria-selected={index === extrasFor}
                          onClick={() => setExtrasFor(index)}
                          className={cn(
                            'rounded-full border px-4 py-2 text-sm focus-ring',
                            index === extrasFor ? 'border-primary bg-brand-soft font-medium' : 'border-border',
                          )}
                        >
                          {name(index)}
                        </button>
                      ))}
                    </div>
                  )}
                  {catalog.isPending ? (
                    <LoadingRegion label="Loading extras">
                      <SkeletonCard className="h-80" lines={5} />
                    </LoadingRegion>
                  ) : catalog.isError ? (
                    <ErrorState error={catalog.error} onRetry={() => void catalog.refetch()} />
                  ) : (
                    <ExtrasPicker
                      offers={offersFor(Math.min(extrasFor, passengerCount - 1))}
                      included={included}
                      currency={flight.currency}
                      estimate
                      quantities={extras[Math.min(extrasFor, passengerCount - 1)] ?? {}}
                      onChange={(code, quantity) => {
                        const index = Math.min(extrasFor, passengerCount - 1)
                        setExtras((current) => ({ ...current, [index]: { ...current[index], [code]: quantity } }))
                      }}
                    />
                  )}
                </section>
              )}

              {step === 4 && (
                <section aria-labelledby="review" className="flex flex-col gap-5">
                  <h2 id="review" tabIndex={-1} className="text-h2 font-semibold outline-none">
                    Review your booking
                  </h2>
                  <div className="rounded-card border border-border bg-card p-5 sm:p-6">
                    <FlightTimeline flight={flight} />
                  </div>
                  {passengers.fields.map((field, index) => {
                    const p = values.passengers[index]!
                    const chosen = Object.entries(extras[index] ?? {}).filter(([, q]) => q > 0)
                    const offers = offersFor(index)
                    return (
                      <dl
                        key={field.id}
                        aria-label={`Passenger ${index + 1}`}
                        className="grid gap-4 rounded-card border border-border bg-card p-5 sm:grid-cols-2 sm:p-6"
                      >
                        {[
                          ['Passenger', `${p.firstName} ${p.lastName}`],
                          ['Passport', p.passportNumber.toUpperCase()],
                          ['Date of birth', p.dateOfBirth ? formatLocalDate(p.dateOfBirth) : ''],
                          ['Nationality', p.nationality],
                          ['Cabin', `${cabinLabel[cabin]} · ${fareLabel[fare]}`],
                          ['Seat', seats[index]?.seatNumber ?? 'Assigned at check-in'],
                          [
                            'Extras',
                            chosen.length
                              ? chosen
                                  .map(
                                    ([code, q]) =>
                                      `${offers.find((o) => o.code === code)?.name ?? code}${q > 1 ? ` × ${q}` : ''}`,
                                  )
                                  .join(', ')
                              : 'None',
                          ],
                        ].map(([label, value]) => (
                          <div key={label} className="flex flex-col gap-1">
                            <dt className="micro-label">{label}</dt>
                            <dd className="font-medium">{value}</dd>
                          </div>
                        ))}
                      </dl>
                    )
                  })}
                  {serverError && (
                    <div
                      role="alert"
                      className="flex flex-col gap-2 rounded-2xl border border-border bg-elevated p-4 text-sm"
                    >
                      <p className="font-medium text-destructive">{serverError}</p>
                      <Link to="/" className="self-start rounded underline underline-offset-4 focus-ring">
                        Back to search results
                      </Link>
                    </div>
                  )}
                </section>
              )}
            </m.div>
          </AnimatePresence>

          <div className="mt-8 hidden items-center justify-between gap-3 md:flex">
            {step > 0 ? (
              <Button variant="ghost" onClick={() => goTo((step - 1) as Step)} disabled={busy}>
                <ArrowLeft strokeWidth={1.75} aria-hidden />
                Back
              </Button>
            ) : (
              <span />
            )}
            {primary}
          </div>
        </div>

        <aside aria-label="Price summary" className="hidden lg:block">
          <div className="sticky top-24 flex flex-col gap-5 rounded-card border border-border bg-card p-6">
            <PriceTag amount={total} currency={flight.currency} size="hero" label="Estimated" />
            <dl className="flex flex-col gap-2 text-sm">
              <SummaryLine
                label={`${cabinLabel[cabin]} ${fareLabel[fare]} × ${passengerCount}`}
                amount={fareTotal}
                currency={flight.currency}
              />
              {Object.keys(seats).length > 0 && (
                <SummaryLine label="Seats" amount={seatTotal} currency={flight.currency} />
              )}
              {extrasSum > 0 && <SummaryLine label="Bags and extras" amount={extrasSum} currency={flight.currency} />}
            </dl>
            {farePerPassenger.surchargePercent > 0 && (
              <p className="text-sm text-muted-foreground">High demand · +{farePerPassenger.surchargePercent}%</p>
            )}
            <p className="text-sm text-muted-foreground">The final price is confirmed when you book.</p>
          </div>
        </aside>
      </div>

      {/* Mobile: price and the next action stay visible above the tab bar. */}
      <div className="fixed inset-x-0 bottom-[calc(4.25rem+env(safe-area-inset-bottom))] z-30 border-t border-border frosted md:hidden">
        <div className="mx-auto flex max-w-[1200px] items-center gap-4 px-4 py-3">
          {step > 0 && (
            <Button
              variant="ghost"
              size="icon"
              aria-label="Back"
              onClick={() => goTo((step - 1) as Step)}
              disabled={busy}
            >
              <ArrowLeft strokeWidth={1.75} aria-hidden />
            </Button>
          )}
          <div className="flex min-w-0 flex-col">
            <span className="micro-label">Estimated</span>
            <PriceTag amount={total} currency={flight.currency} className="text-lg font-semibold" />
          </div>
          <div className="ml-auto">{primary}</div>
        </div>
      </div>
    </Page>
  )
}

function SummaryLine({ label, amount, currency }: { label: string; amount: number; currency: string }) {
  return (
    <div className="flex justify-between gap-3">
      <dt className="text-muted-foreground">{label}</dt>
      <dd className="font-medium tabular">{formatMoney(amount, currency)}</dd>
    </div>
  )
}

function PassengerFields({
  index,
  count,
  form,
  onRemove,
}: {
  index: number
  count: number
  form: ReturnType<typeof useForm<FormValues>>
  onRemove: () => void
}) {
  const errors = form.formState.errors.passengers?.[index]
  const path = <K extends keyof PassengerValues>(field: K) => `passengers.${index}.${field}` as const
  return (
    <fieldset className="flex flex-col gap-5">
      {count > 1 && (
        <legend className="mb-4 flex w-full items-center justify-between">
          <span className="text-h3 font-semibold">Passenger {index + 1}</span>
          <Button type="button" variant="ghost" size="sm" onClick={onRemove}>
            <Trash2 strokeWidth={1.75} aria-hidden />
            Remove
            <span className="sr-only">passenger {index + 1}</span>
          </Button>
        </legend>
      )}
      <div className="grid gap-5 sm:grid-cols-2">
        <FormField label="First name" error={errors?.firstName?.message}>
          {(control) => (
            <Input
              {...control}
              autoComplete={index === 0 ? 'given-name' : 'off'}
              {...form.register(path('firstName'))}
            />
          )}
        </FormField>
        <FormField label="Last name" description="As shown on passport" error={errors?.lastName?.message}>
          {(control) => (
            <Input
              {...control}
              autoComplete={index === 0 ? 'family-name' : 'off'}
              {...form.register(path('lastName'))}
            />
          )}
        </FormField>
      </div>
      <FormField label="Passport number" error={errors?.passportNumber?.message}>
        {(control) => (
          <Input
            {...control}
            autoComplete="off"
            className="font-mono uppercase"
            {...form.register(path('passportNumber'))}
          />
        )}
      </FormField>
      <div className="grid gap-5 sm:grid-cols-2">
        <Controller
          control={form.control}
          name={path('dateOfBirth')}
          render={({ field, fieldState }) => (
            <FormField label="Date of birth" error={fieldState.error?.message}>
              {(control) => (
                <DatePicker
                  {...control}
                  value={field.value}
                  onChange={field.onChange}
                  disableFuture
                  captionLayout="dropdown"
                  fromYear={1900}
                  toYear={new Date().getFullYear()}
                />
              )}
            </FormField>
          )}
        />
        <Controller
          control={form.control}
          name={path('nationality')}
          render={({ field, fieldState }) => (
            <FormField label="Nationality" error={fieldState.error?.message}>
              {(control) => (
                <CountryCombobox {...control} value={field.value} onChange={(v) => field.onChange(v ?? '')} />
              )}
            </FormField>
          )}
        />
      </div>
    </fieldset>
  )
}
