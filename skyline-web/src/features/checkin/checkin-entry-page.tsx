import { zodResolver } from '@hookform/resolvers/zod'
import { ArrowRight } from 'lucide-react'
import { Controller, useForm } from 'react-hook-form'
import { Link, useNavigate } from 'react-router'
import { z } from 'zod'

import { useAuth } from '@/auth/use-auth'
import { Page } from '@/components/layout/app-shell'
import { CountdownTimer } from '@/components/skyline/countdown'
import { flightTimes, RouteLabel } from '@/components/skyline/flight'
import { FormErrorSummary, FormField } from '@/components/skyline/form'
import { PnrInput } from '@/components/skyline/pnr'
import { Skeleton } from '@/components/ui/skeleton'
import { Button } from '@/components/ui/button'
import { checkinWindow } from '@/lib/checkin-window'
import { useDocumentTitle } from '@/lib/hooks'
import { isValidPnr, PNR_HINT } from '@/lib/pnr'
import { tripTab } from '@/features/trips/trips-page'
import { useMyReservations } from '@/features/trips/queries'

const schema = z.object({
  pnr: z.string().length(6, 'Booking references have 6 characters').refine(isValidPnr, PNR_HINT),
})

function UpcomingTrips() {
  const { data, isPending } = useMyReservations()
  const upcoming = (data?.content ?? [])
    .filter((r) => tripTab(r) === 'upcoming')
    .sort((a, b) => (a.flight?.departureTime ?? '').localeCompare(b.flight?.departureTime ?? ''))

  if (isPending) return <Skeleton className="h-24 rounded-card" />
  if (upcoming.length === 0) return null

  return (
    <section aria-labelledby="your-trips" className="flex flex-col gap-3">
      <h2 id="your-trips" className="text-h3 font-semibold">
        Or pick a trip
      </h2>
      <ul className="flex flex-col gap-2">
        {upcoming.map((reservation) => {
          const state = checkinWindow(reservation)
          const flight = reservation.flight
          return (
            <li key={reservation.id}>
              <Link
                to={`/checkin/${reservation.pnr}`}
                className="flex items-center gap-4 rounded-card border border-border bg-card p-4 focus-ring transition-colors duration-200 hover:bg-elevated sm:p-5"
              >
                <div className="flex min-w-0 flex-1 flex-col gap-1">
                  <RouteLabel route={flight?.route} className="text-lg" />
                  <span className="text-sm text-muted-foreground">
                    {flight ? `${flightTimes(flight).departureDate} · ${flight.flightNumber}` : ''} ·{' '}
                    <span className="font-mono">{reservation.pnr}</span>
                  </span>
                </div>
                <span className="text-right text-sm">
                  {state.kind === 'open' && <span className="font-medium">Check-in open</span>}
                  {state.kind === 'checked-in' && <span className="font-medium">Boarding pass</span>}
                  {state.kind === 'not-open' && (
                    <span className="text-muted-foreground">
                      Opens in <CountdownTimer target={state.opensAt} />
                    </span>
                  )}
                  {state.kind === 'closed' && <span className="text-muted-foreground">Closed</span>}
                </span>
                <ArrowRight className="size-4 shrink-0 text-muted-foreground" strokeWidth={1.75} aria-hidden />
              </Link>
            </li>
          )
        })}
      </ul>
    </section>
  )
}

export function Component() {
  useDocumentTitle('Online check-in')
  const navigate = useNavigate()
  const { isPassenger } = useAuth()
  const form = useForm<z.infer<typeof schema>>({ resolver: zodResolver(schema), defaultValues: { pnr: '' } })
  const { errors, submitCount } = form.formState
  const onSubmit = form.handleSubmit(({ pnr }) => navigate(`/checkin/${pnr}`))

  return (
    <Page className="flex max-w-[560px] flex-col gap-8 py-12 sm:py-20">
      <div className="flex flex-col gap-2">
        <h1 className="text-h1 font-semibold">Online check-in</h1>
        <p className="text-muted-foreground">
          Opens 48 hours and closes 1 hour before departure. Your seat is assigned when you check in.
        </p>
      </div>
      <form
        onSubmit={onSubmit}
        noValidate
        className="flex flex-col gap-5 rounded-card border border-border bg-card p-5 sm:p-6"
      >
        <FormErrorSummary errors={errors} submitCount={submitCount} labels={{ pnr: 'Booking reference' }} />
        <Controller
          control={form.control}
          name="pnr"
          render={({ field, fieldState }) => (
            <FormField label="Booking reference" error={fieldState.error?.message}>
              {(control) => (
                <PnrInput
                  {...control}
                  ref={field.ref}
                  name={field.name}
                  value={field.value}
                  onChange={field.onChange}
                  onBlur={field.onBlur}
                  invalid={!!fieldState.error}
                />
              )}
            </FormField>
          )}
        />
        <Button type="submit" size="lg" className="w-full">
          Continue
        </Button>
      </form>
      {isPassenger && <UpcomingTrips />}
    </Page>
  )
}
