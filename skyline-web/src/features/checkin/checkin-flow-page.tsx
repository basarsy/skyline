import * as React from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { CircleAlert, Clock3, Lock } from 'lucide-react'
import { Link, useParams } from 'react-router'
import { toast } from 'sonner'

import { checkinApi } from '@/api/endpoints/checkin'
import { isApiError } from '@/api/http'
import { checkinKeys, reservationKeys } from '@/api/query-keys'
import type { ReservationResponse } from '@/api/types'
import { Page } from '@/components/layout/app-shell'
import { BoardingPass } from '@/components/skyline/boarding-pass'
import { CountdownTimer } from '@/components/skyline/countdown'
import { FlightTimeline, flightTimes } from '@/components/skyline/flight'
import { EmptyState, ErrorState, LoadingRegion, SkeletonCard } from '@/components/skyline/states'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { checkinWindow } from '@/lib/checkin-window'
import { useDocumentTitle } from '@/lib/hooks'
import { cabinLabel } from '@/lib/labels'
import { normalizePnr } from '@/lib/pnr'
import { useBoardingPass, useReservationByPnr } from '@/features/trips/queries'

/** A dedicated state card (not a toast) for window/eligibility answers, including the server's 422s. */
function StateCard({ icon, title, children }: { icon: React.ReactNode; title: string; children?: React.ReactNode }) {
  return (
    <section
      role="status"
      className="flex flex-col items-start gap-5 rounded-card border border-border bg-card p-6 sm:p-8"
    >
      <span className="grid size-12 place-items-center rounded-full bg-elevated [&_svg]:size-5">{icon}</span>
      <h2 className="text-h2 font-semibold">{title}</h2>
      {children}
    </section>
  )
}

function Summary({ reservation }: { reservation: ReservationResponse }) {
  const flight = reservation.flight!
  return (
    <div className="flex flex-col gap-5 rounded-card border border-border bg-card p-5 sm:p-6">
      <div className="flex flex-wrap items-center justify-between gap-2 text-sm">
        <span className="font-medium">
          {reservation.passenger.firstName} {reservation.passenger.lastName}
        </span>
        <span className="text-muted-foreground">
          {cabinLabel[reservation.cabinClass]} · <span className="font-mono">{flight.flightNumber}</span> ·{' '}
          {flightTimes(flight).departureDate}
        </span>
      </div>
      <FlightTimeline flight={flight} />
    </div>
  )
}

function PassView({ reservation }: { reservation: ReservationResponse }) {
  const pass = useBoardingPass(reservation.pnr)
  if (pass.isPending) {
    return (
      <LoadingRegion label="Loading boarding pass">
        <SkeletonCard className="mx-auto h-[640px] w-full max-w-[400px]" />
      </LoadingRegion>
    )
  }
  if (pass.isError) {
    if (isApiError(pass.error) && pass.error.status === 404) {
      return (
        <EmptyState title="Not checked in yet" description="Your boarding pass appears here once you’ve checked in." />
      )
    }
    return <ErrorState error={pass.error} onRetry={() => void pass.refetch()} />
  }
  return <BoardingPass pass={pass.data} originZone={reservation.flight?.route?.origin.timezone} />
}

export function Component() {
  const pnr = normalizePnr(useParams().pnr ?? '')
  const queryClient = useQueryClient()
  const reservationQuery = useReservationByPnr(pnr, undefined)
  const [consent, setConsent] = React.useState(false)
  const consentId = React.useId()
  // A boundary was crossed while the page was open (countdown hit zero): re-evaluate.
  const [, forceTick] = React.useReducer((n: number) => n + 1, 0)
  useDocumentTitle(`Check in ${pnr}`)

  const checkIn = useMutation({
    mutationFn: () => checkinApi.checkIn(pnr),
    onSuccess: async (result) => {
      queryClient.setQueryData(checkinKeys.boardingPass(pnr), result.data)
      toast.success(result.message ?? 'You’re checked in')
      await queryClient.invalidateQueries({ queryKey: reservationKeys.all })
    },
  })

  if (reservationQuery.isPending) {
    return (
      <Page>
        <LoadingRegion label="Loading your booking">
          <SkeletonCard className="h-80" lines={4} />
        </LoadingRegion>
      </Page>
    )
  }
  if (reservationQuery.isError) {
    const status = isApiError(reservationQuery.error) ? reservationQuery.error.status : 0
    return (
      <Page className="max-w-[720px]">
        {status === 404 || status === 400 ? (
          <EmptyState
            title="We couldn’t find a booking with that reference on your account"
            description="Check the reference, or log in with the account that made the booking."
            action={
              <Button asChild variant="outline">
                <Link to="/checkin">Try another reference</Link>
              </Button>
            }
          />
        ) : (
          <ErrorState error={reservationQuery.error} onRetry={() => void reservationQuery.refetch()} />
        )}
      </Page>
    )
  }

  const reservation = reservationQuery.data
  const state = checkIn.isSuccess ? ({ kind: 'checked-in' } as const) : checkinWindow(reservation)
  const serverWindowError =
    checkIn.error && isApiError(checkIn.error) && checkIn.error.status === 422 ? checkIn.error.message : null

  return (
    <Page className="flex max-w-[720px] flex-col gap-6">
      <div className="flex flex-col gap-2">
        <span className="micro-label">Check-in · {pnr}</span>
        <h1 className="text-h1 font-semibold">
          {state.kind === 'checked-in' ? 'Your boarding pass' : 'Online check-in'}
        </h1>
      </div>

      {state.kind === 'checked-in' ? (
        <PassView reservation={reservation} />
      ) : serverWindowError ? (
        <StateCard icon={<Clock3 strokeWidth={1.75} aria-hidden />} title="Check-in isn’t available">
          <p className="text-muted-foreground">{serverWindowError}</p>
        </StateCard>
      ) : state.kind === 'not-open' ? (
        <>
          <StateCard icon={<Clock3 strokeWidth={1.75} aria-hidden />} title="Check-in opens in">
            <CountdownTimer target={state.opensAt} size="hero" onElapsed={forceTick} />
            <p className="text-muted-foreground">
              Online check-in opens 48 hours before departure, on{' '}
              {state.opensAt.toLocaleString(undefined, {
                weekday: 'short',
                day: 'numeric',
                month: 'short',
                hour: '2-digit',
                minute: '2-digit',
              })}
              .
            </p>
          </StateCard>
          {reservation.flight && <Summary reservation={reservation} />}
        </>
      ) : state.kind === 'closed' ? (
        <StateCard icon={<Lock strokeWidth={1.75} aria-hidden />} title="Online check-in has closed">
          <p className="text-muted-foreground">
            It closes 1 hour before departure. Please go to the airport check-in desk.
          </p>
        </StateCard>
      ) : state.kind === 'ineligible' ? (
        <StateCard icon={<CircleAlert strokeWidth={1.75} aria-hidden />} title="This booking can’t be checked in">
          <p className="text-muted-foreground">{state.reason}</p>
        </StateCard>
      ) : (
        <>
          <Summary reservation={reservation} />
          <form
            className="flex flex-col gap-5 rounded-card border border-border bg-card p-5 sm:p-6"
            onSubmit={(event) => {
              event.preventDefault()
              if (consent && !checkIn.isPending) checkIn.mutate()
            }}
          >
            <p className="text-sm text-muted-foreground">Your seat is assigned when you check in.</p>
            <div className="flex items-start gap-3">
              <Checkbox
                id={consentId}
                checked={consent}
                onCheckedChange={(value) => setConsent(value === true)}
                className="mt-0.5 size-5"
              />
              <label htmlFor={consentId} className="text-sm leading-snug">
                I confirm I’m not carrying prohibited items, such as explosives, flammable liquids, weapons or
                compressed gases.
              </label>
            </div>
            {checkIn.isError && !serverWindowError && (
              <p role="alert" className="text-sm text-destructive">
                {isApiError(checkIn.error) ? checkIn.error.message : 'Please try again.'}
              </p>
            )}
            <Button
              type="submit"
              size="lg"
              disabled={!consent}
              loading={checkIn.isPending}
              className="w-full sm:w-auto sm:self-start"
            >
              Check in
            </Button>
          </form>
        </>
      )}
    </Page>
  )
}
