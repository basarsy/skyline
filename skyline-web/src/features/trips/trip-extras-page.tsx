import * as React from 'react'
import { ArrowLeft } from 'lucide-react'
import { Link, useParams } from 'react-router'

import { Page } from '@/components/layout/app-shell'
import { ErrorState, LoadingRegion, SkeletonCard } from '@/components/skyline/states'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/cn'
import { useDocumentTitle } from '@/lib/hooks'
import { ReservationExtras, ReservationSeatPicker } from '@/features/seats/reservation-extras'
import { useReservation } from './queries'

const TABS = ['Seat', 'Bags and extras'] as const

/** Manage booking: change the seat or add bags, after booking and before check-in. */
export function Component() {
  const { reservationId } = useParams()
  const { data: reservation, isPending, isError, error, refetch } = useReservation(reservationId)
  const [tab, setTab] = React.useState<(typeof TABS)[number]>('Seat')
  useDocumentTitle(reservation ? `Seat and bags · ${reservation.pnr}` : 'Seat and bags')

  return (
    <Page className="flex flex-col gap-6">
      <Button asChild variant="ghost" size="sm" className="self-start">
        <Link to={`/trips/${reservationId}`}>
          <ArrowLeft strokeWidth={1.75} aria-hidden />
          Back to trip
        </Link>
      </Button>
      {isPending ? (
        <LoadingRegion label="Loading trip">
          <SkeletonCard className="h-[520px]" lines={6} />
        </LoadingRegion>
      ) : isError ? (
        <ErrorState error={error} onRetry={() => void refetch()} />
      ) : (
        <>
          <div className="flex flex-col gap-2">
            <span className="micro-label">
              {reservation.pnr} · {reservation.flight?.flightNumber}
            </span>
            <h1 className="text-h1 font-semibold">Seat and bags</h1>
          </div>
          <div role="tablist" aria-label="Manage" className="flex gap-2">
            {TABS.map((name) => (
              <button
                key={name}
                type="button"
                role="tab"
                id={`tab-${name}`}
                aria-selected={tab === name}
                aria-controls="manage-panel"
                onClick={() => setTab(name)}
                className={cn(
                  'rounded-full border px-4 py-2 text-sm focus-ring',
                  tab === name ? 'border-primary bg-brand-soft font-medium' : 'border-border',
                )}
              >
                {name}
              </button>
            ))}
          </div>
          <div id="manage-panel" role="tabpanel" aria-labelledby={`tab-${tab}`}>
            {tab === 'Seat' ? (
              <ReservationSeatPicker reservation={reservation} />
            ) : (
              <ReservationExtras reservation={reservation} />
            )}
          </div>
        </>
      )}
    </Page>
  )
}
