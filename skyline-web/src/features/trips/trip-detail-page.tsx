import { ArrowLeft } from 'lucide-react'
import { Link, useParams } from 'react-router'

import { isApiError } from '@/api/http'
import { useAuth } from '@/auth/use-auth'
import { Page } from '@/components/layout/app-shell'
import { EmptyState, ErrorState, LoadingRegion, SkeletonCard } from '@/components/skyline/states'
import { Button } from '@/components/ui/button'
import { useDocumentTitle } from '@/lib/hooks'
import { useReservation } from './queries'
import { ReservationView, TripActions } from './reservation-view'

export function Component() {
  const { reservationId } = useParams()
  const { isPassenger } = useAuth()
  const { data: reservation, isPending, isError, error, refetch } = useReservation(reservationId)
  useDocumentTitle(reservation ? `Trip ${reservation.pnr}` : 'Trip')

  return (
    <Page className="flex flex-col gap-6">
      {isPassenger && (
        <Button asChild variant="ghost" size="sm" className="self-start">
          <Link to="/trips">
            <ArrowLeft strokeWidth={1.75} aria-hidden />
            My trips
          </Link>
        </Button>
      )}
      {isPending ? (
        <LoadingRegion label="Loading trip">
          <SkeletonCard className="h-[520px]" lines={6} />
        </LoadingRegion>
      ) : isError ? (
        isApiError(error) && (error.status === 404 || error.status === 403) ? (
          <EmptyState
            title="We couldn’t find this trip"
            description="It may belong to another account, or the link is wrong."
            action={
              <Button asChild>
                <Link to="/trips">Go to my trips</Link>
              </Button>
            }
          />
        ) : (
          <ErrorState error={error} onRetry={() => void refetch()} />
        )
      ) : (
        <>
          <ReservationView reservation={reservation} />
          <TripActions reservation={reservation} />
        </>
      )}
    </Page>
  )
}
