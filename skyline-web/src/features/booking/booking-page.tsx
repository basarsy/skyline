import * as React from 'react'
import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { AnimatePresence, motion, useReducedMotion } from 'motion/react'
import { ArrowLeft, ArrowRight, Check } from 'lucide-react'
import { Controller, useForm, useWatch } from 'react-hook-form'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router'
import { toast } from 'sonner'
import { z } from 'zod'

import { reservationsApi } from '@/api/endpoints/reservations'
import { isApiError } from '@/api/http'
import { flightKeys, reservationKeys } from '@/api/query-keys'
import type { CabinClass, FlightResponse, PassengerRequest } from '@/api/types'
import { Page } from '@/components/layout/app-shell'
import { FlightTimeline, flightTimes, RouteLabel } from '@/components/skyline/flight'
import { applyServerErrors, FormErrorSummary, FormField } from '@/components/skyline/form'
import { CountryCombobox, DatePicker } from '@/components/skyline/pickers'
import { PriceTag } from '@/components/skyline/price-tag'
import { EmptyState, ErrorState, LoadingRegion, SkeletonCard } from '@/components/skyline/states'
import { FlightStatusBadge } from '@/components/skyline/status-badge'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { cn } from '@/lib/cn'
import { isBookable } from '@/lib/flight-status'
import { formatLocalDate, isoFromLocalDate } from '@/lib/format'
import { useDocumentTitle } from '@/lib/hooks'
import { cabinLabel, CABINS } from '@/lib/labels'
import { cabinMultiplierCopy, estimatePrice } from '@/lib/pricing'
import { useFlight } from '@/features/search/queries'

/** The last passenger this browser booked, to prefill the form the next time. */
const LAST_PASSENGER_KEY = 'skyline.lastPassenger'
const STEPS = ['Cabin', 'Passenger', 'Review'] as const
/** The step buttons sit outside the form (shared with the mobile bar); they submit it via form=. */
const PASSENGER_FORM_ID = 'passenger-form'
type Step = 0 | 1 | 2

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
type PassengerValues = z.infer<typeof passengerSchema>
const PASSENGER_FIELDS = ['firstName', 'lastName', 'passportNumber', 'dateOfBirth', 'nationality'] as const
const PASSENGER_LABELS: Record<string, string> = {
  firstName: 'First name',
  lastName: 'Last name',
  passportNumber: 'Passport number',
  dateOfBirth: 'Date of birth',
  nationality: 'Nationality',
}

function readLastPassenger(): Partial<PassengerValues> {
  try {
    return JSON.parse(localStorage.getItem(LAST_PASSENGER_KEY) ?? '{}') as Partial<PassengerValues>
  } catch {
    return {}
  }
}

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
                state === 'todo' ? 'bg-elevated' : 'bg-foreground',
              )}
            />
            <span
              className={cn(
                'flex items-center gap-1.5 text-sm',
                state === 'todo' ? 'text-muted-foreground' : 'font-medium',
              )}
            >
              {state === 'done' && <Check className="size-3.5" strokeWidth={2} aria-hidden />}
              {label}
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

function CabinStep({
  flight,
  cabin,
  onChange,
}: {
  flight: FlightResponse
  cabin: CabinClass
  onChange: (cabin: CabinClass) => void
}) {
  return (
    <fieldset className="flex flex-col gap-4">
      <legend className="mb-4">
        <h2 tabIndex={-1} className="text-h2 font-semibold outline-none">
          Choose your cabin
        </h2>
      </legend>
      <div role="radiogroup" aria-label="Cabin" className="grid gap-3">
        {CABINS.map((option) => {
          const estimate = estimatePrice(flight, option)
          const active = option === cabin
          return (
            <button
              key={option}
              type="button"
              role="radio"
              aria-checked={active}
              onClick={() => onChange(option)}
              onKeyDown={(event) => {
                const index = CABINS.indexOf(option)
                if (event.key === 'ArrowDown' || event.key === 'ArrowRight') {
                  event.preventDefault()
                  onChange(CABINS[(index + 1) % CABINS.length]!)
                } else if (event.key === 'ArrowUp' || event.key === 'ArrowLeft') {
                  event.preventDefault()
                  onChange(CABINS[(index + CABINS.length - 1) % CABINS.length]!)
                }
              }}
              tabIndex={active ? 0 : -1}
              className={cn(
                'flex cursor-pointer items-center gap-5 rounded-card border bg-card p-5 text-left focus-ring transition-[border-color,background-color,transform] duration-200 active:scale-[0.99] sm:p-6',
                active ? 'border-foreground' : 'border-border hover:bg-elevated',
              )}
            >
              <span
                aria-hidden
                className={cn(
                  'size-5 shrink-0 rounded-full border-2 transition-all',
                  active ? 'border-[6px] border-foreground' : 'border-border',
                )}
              />
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
            </button>
          )
        })}
      </div>
      <p className="text-sm text-muted-foreground">Your seat is assigned at check-in.</p>
    </fieldset>
  )
}

export function Component() {
  const { flightId } = useParams()
  const [params, setParams] = useSearchParams()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { data: flight, isPending, isError, error, refetch } = useFlight(flightId)
  useDocumentTitle('Book your flight')

  const cabinParam = params.get('cabin')
  const cabin: CabinClass = CABINS.includes(cabinParam as CabinClass) ? (cabinParam as CabinClass) : 'ECONOMY'
  const setCabin = (next: CabinClass) =>
    setParams(
      (current) => {
        const copy = new URLSearchParams(current)
        copy.set('cabin', next)
        return copy
      },
      { replace: true },
    )

  const [step, setStep] = React.useState<Step>(0)
  const [serverError, setServerError] = React.useState<string | null>(null)
  const headingRef = React.useRef<HTMLDivElement>(null)

  const form = useForm<PassengerValues>({
    resolver: zodResolver(passengerSchema),
    defaultValues: {
      firstName: '',
      lastName: '',
      passportNumber: '',
      dateOfBirth: '',
      nationality: '',
      ...readLastPassenger(),
    },
  })
  const { errors, submitCount } = form.formState
  const reduceMotion = useReducedMotion()
  const values = useWatch({ control: form.control }) as PassengerValues

  const book = useMutation({
    mutationFn: (passenger: PassengerRequest) =>
      reservationsApi.book({ flightId: flightId!, cabinClass: cabin, passenger }),
    onSuccess: async (result) => {
      try {
        localStorage.setItem(LAST_PASSENGER_KEY, JSON.stringify(form.getValues()))
      } catch {
        // Prefill is a convenience only.
      }
      toast.success(result.message ?? 'Flight booked')
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: reservationKeys.myAll }),
        queryClient.invalidateQueries({ queryKey: flightKeys.detail(flightId!) }),
      ])
      queryClient.setQueryData(reservationKeys.detail(result.data.id), result.data)
      navigate(`/book/confirmation/${result.data.id}`, { replace: true })
    },
    onError: (err) => {
      const message = applyServerErrors(
        err,
        (field, value) => form.setError(field.replace(/^passenger\./, '') as keyof PassengerValues, value),
        PASSENGER_FIELDS.map((f) => `passenger.${f}`),
      )
      if (isApiError(err) && err.hasFieldErrors && !message) setStep(1)
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

  const estimate = estimatePrice(flight, cabin)
  const times = flightTimes(flight)
  const busy = book.isPending || book.isSuccess

  const primary =
    step === 0 ? (
      <Button size="lg" className="w-full sm:w-auto" onClick={() => goTo(1)}>
        Continue
        <ArrowRight strokeWidth={1.75} aria-hidden />
      </Button>
    ) : step === 1 ? (
      <Button type="submit" form={PASSENGER_FORM_ID} size="lg" className="w-full sm:w-auto">
        Review booking
        <ArrowRight strokeWidth={1.75} aria-hidden />
      </Button>
    ) : (
      <Button
        size="lg"
        className="w-full sm:w-auto"
        loading={busy}
        onClick={() => {
          if (busy) return
          setServerError(null)
          const v = form.getValues()
          book.mutate({
            firstName: v.firstName.trim(),
            lastName: v.lastName.trim(),
            passportNumber: v.passportNumber.trim().toUpperCase(),
            dateOfBirth: v.dateOfBirth,
            nationality: v.nationality,
          })
        }}
      >
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
            <motion.div
              key={step}
              initial={reduceMotion ? false : { opacity: 0, x: 16 }}
              animate={{ opacity: 1, x: 0 }}
              exit={reduceMotion ? undefined : { opacity: 0, x: -16 }}
              transition={{ type: 'spring', stiffness: 420, damping: 38 }}
            >
              {step === 0 && <CabinStep flight={flight} cabin={cabin} onChange={setCabin} />}

              {step === 1 && (
                <form id={PASSENGER_FORM_ID} onSubmit={next} noValidate>
                  <fieldset className="flex flex-col gap-5">
                    <legend className="mb-4">
                      <h2 tabIndex={-1} className="text-h2 font-semibold outline-none">
                        Who’s flying?
                      </h2>
                    </legend>
                    <FormErrorSummary errors={errors} submitCount={submitCount} labels={PASSENGER_LABELS} />
                    <div className="grid gap-5 sm:grid-cols-2">
                      <FormField label="First name" error={errors.firstName?.message}>
                        {(control) => <Input {...control} autoComplete="given-name" {...form.register('firstName')} />}
                      </FormField>
                      <FormField label="Last name" description="As shown on passport" error={errors.lastName?.message}>
                        {(control) => <Input {...control} autoComplete="family-name" {...form.register('lastName')} />}
                      </FormField>
                    </div>
                    <FormField label="Passport number" error={errors.passportNumber?.message}>
                      {(control) => (
                        <Input
                          {...control}
                          autoComplete="off"
                          className="font-mono uppercase"
                          {...form.register('passportNumber')}
                        />
                      )}
                    </FormField>
                    <div className="grid gap-5 sm:grid-cols-2">
                      <Controller
                        control={form.control}
                        name="dateOfBirth"
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
                        name="nationality"
                        render={({ field, fieldState }) => (
                          <FormField label="Nationality" error={fieldState.error?.message}>
                            {(control) => (
                              <CountryCombobox
                                {...control}
                                value={field.value}
                                onChange={(v) => field.onChange(v ?? '')}
                              />
                            )}
                          </FormField>
                        )}
                      />
                    </div>
                  </fieldset>
                </form>
              )}

              {step === 2 && (
                <section aria-labelledby="review" className="flex flex-col gap-5">
                  <h2 id="review" tabIndex={-1} className="text-h2 font-semibold outline-none">
                    Review your booking
                  </h2>
                  <div className="rounded-card border border-border bg-card p-5 sm:p-6">
                    <FlightTimeline flight={flight} />
                  </div>
                  <dl className="grid gap-4 rounded-card border border-border bg-card p-5 sm:grid-cols-2 sm:p-6">
                    {[
                      ['Passenger', `${values.firstName} ${values.lastName}`],
                      ['Passport', values.passportNumber.toUpperCase()],
                      ['Date of birth', values.dateOfBirth ? formatLocalDate(values.dateOfBirth) : ''],
                      ['Nationality', values.nationality],
                      ['Cabin', cabinLabel[cabin]],
                      ['Seat', 'Assigned at check-in'],
                    ].map(([label, value]) => (
                      <div key={label} className="flex flex-col gap-1">
                        <dt className="micro-label">{label}</dt>
                        <dd className="font-medium">{value}</dd>
                      </div>
                    ))}
                  </dl>
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
            </motion.div>
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
            <PriceTag amount={estimate.amount} currency={flight.currency} size="hero" label="Estimated" />
            {estimate.surchargePercent > 0 && (
              <p className="text-sm text-muted-foreground">High demand · +{estimate.surchargePercent}%</p>
            )}
            <p className="text-sm text-muted-foreground">
              {cabinLabel[cabin]} for one passenger. The final price is confirmed when you book.
            </p>
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
            <PriceTag amount={estimate.amount} currency={flight.currency} className="text-lg font-semibold" />
          </div>
          <div className="ml-auto">{primary}</div>
        </div>
      </div>
    </Page>
  )
}
