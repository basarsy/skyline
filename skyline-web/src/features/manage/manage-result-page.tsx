import { LogIn } from 'lucide-react'
import { Link, Navigate, useLocation, useParams } from 'react-router'

import { isApiError } from '@/api/http'
import { useAuth } from '@/auth/use-auth'
import { Page } from '@/components/layout/app-shell'
import { ErrorState, LoadingRegion, SkeletonCard } from '@/components/skyline/states'
import { Button } from '@/components/ui/button'
import { useDocumentTitle } from '@/lib/hooks'
import { normalizePnr } from '@/lib/pnr'
import { useReservationByPnr } from '@/features/trips/queries'
import { ReservationView, TripActions } from '@/features/trips/reservation-view'
import type { ManageState } from './manage-page'

/**
 * A booking found by PNR. Anonymous visitors get the redacted view (masked passport, no date of birth); the
 * owner, logged in, gets the full view and the actions. The last name arrives in router state, never in the
 * URL; on a hard refresh without it, an anonymous visitor goes back to the form.
 */
export function Component() {
  const pnr = normalizePnr(useParams().pnr ?? '')
  const location = useLocation()
  const lastName = (location.state as ManageState | null)?.lastName
  const { status, user, isLoading } = useAuth()
  const canQuery = !isLoading && (!!lastName || status === 'authenticated')
  const query = useReservationByPnr(pnr, lastName, canQuery)
  useDocumentTitle(`Booking ${pnr}`)

  if (!isLoading && !lastName && status !== 'authenticated') {
    return <Navigate to="/manage" replace state={{ pnr }} />
  }

  if (query.isError) {
    const status = isApiError(query.error) ? query.error.status : 0
    // A wrong last name is a 404 on purpose; so is an unknown PNR. Never say which.
    if (status === 404 || status === 400) {
      return <Navigate to="/manage" replace state={{ pnr, lastName, notFound: true }} />
    }
    return (
      <Page>
        <ErrorState error={query.error} onRetry={() => void query.refetch()} />
      </Page>
    )
  }

  if (!query.data) {
    return (
      <Page>
        <LoadingRegion label="Finding your booking">
          <SkeletonCard className="h-96" lines={5} />
        </LoadingRegion>
      </Page>
    )
  }

  const reservation = query.data
  const full = reservation.passenger.dateOfBirth !== null
  const isOwner = !!user && reservation.passenger.userId === user.id

  return (
    <Page className="flex flex-col gap-6">
      {!full && (
        <div className="flex flex-col gap-3 rounded-2xl border border-border bg-elevated p-4 sm:flex-row sm:items-center sm:justify-between">
          <p className="text-sm">Log in to check in or cancel this booking.</p>
          <Button asChild size="sm" variant="outline">
            <Link to={`/login?next=${encodeURIComponent(`/manage/${pnr}`)}`}>
              <LogIn strokeWidth={1.75} aria-hidden />
              Log in
            </Link>
          </Button>
        </div>
      )}
      <ReservationView reservation={reservation} redacted={!full} />
      {full && (isOwner || user?.role !== 'PASSENGER') && <TripActions reservation={reservation} />}
    </Page>
  )
}
