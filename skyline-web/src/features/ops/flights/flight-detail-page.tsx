import * as React from 'react'
import { Check, Pencil, Plus, Trash2, X } from 'lucide-react'
import { Link, useParams } from 'react-router'

import { isApiError } from '@/api/http'
import type { CrewMemberResponse, CrewRole, FlightCrewResponse, FlightResponse, FlightStatus } from '@/api/types'
import { useAuth } from '@/auth/use-auth'
import { OpsPageHeader } from '@/components/layout/ops-shell'
import { Combobox } from '@/components/skyline/combobox'
import { ConfirmDialog } from '@/components/skyline/confirm-dialog'
import { FlightTimeline, flightTimes, RouteLabel } from '@/components/skyline/flight'
import { FormField } from '@/components/skyline/form'
import { ResponsiveSheet } from '@/components/skyline/responsive-sheet'
import { EmptyState, ErrorState, LoadingRegion, SkeletonCard } from '@/components/skyline/states'
import { FlightStatusBadge } from '@/components/skyline/status-badge'
import { Button } from '@/components/ui/button'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { cn } from '@/lib/cn'
import {
  crewReadiness,
  isCancellableFlight,
  isEditableFlight,
  MAIN_LINE,
  nextStatuses,
  requiresCrew,
  transitionCopy,
} from '@/lib/flight-status'
import { formatLocalDate } from '@/lib/format'
import { useDebounced, useDocumentTitle } from '@/lib/hooks'
import { crewRoleLabel, CREW_ROLES, flightStatusLabel } from '@/lib/labels'
import { capacityOf, loadFactor } from '@/lib/pricing'
import { useFlight } from '@/features/search/queries'
import { useAssignCrew, useCancelFlight, useCrewList, useManifest, useRemoveCrew, useSetFlightStatus } from '../queries'

const errorText = (error: unknown) =>
  isApiError(error) || error instanceof Error ? error.message : 'Please try again.'

function Readiness({ manifest }: { manifest: FlightCrewResponse[] }) {
  const readiness = crewReadiness(manifest)
  const item = (ok: boolean, label: string) => (
    <li className="flex items-center gap-2 text-sm">
      <span
        className={cn(
          'grid size-6 place-items-center rounded-full',
          ok ? 'bg-primary text-primary-foreground' : 'bg-elevated text-muted-foreground',
        )}
      >
        {ok ? (
          <Check className="size-3.5" strokeWidth={2.25} aria-hidden />
        ) : (
          <X className="size-3.5" strokeWidth={2.25} aria-hidden />
        )}
      </span>
      <span>
        {label} <span className="sr-only">{ok ? 'assigned' : 'missing'}</span>
        {!ok && <span className="text-muted-foreground"> · missing</span>}
      </span>
    </li>
  )
  return (
    <ul aria-label="Crew readiness" className="flex flex-wrap gap-x-5 gap-y-2">
      {item(readiness.captain, 'Captain')}
      {item(readiness.firstOfficer, 'First officer')}
    </ul>
  )
}

/** Stepper along SCHEDULED → BOARDING → DEPARTED → ARRIVED; DELAYED and CANCELLED are shown as branches. */
function StatusStepper({ status }: { status: FlightStatus }) {
  const index = MAIN_LINE.indexOf(status)
  const reached = index === -1 ? (status === 'DELAYED' ? 0 : -1) : index
  return (
    <div className="flex flex-col gap-3">
      <ol className="grid grid-cols-4 gap-2" aria-label="Flight progress">
        {MAIN_LINE.map((step, i) => (
          <li key={step} className="flex flex-col gap-2" aria-current={step === status ? 'step' : undefined}>
            <span
              className={cn(
                'h-1 rounded-full',
                i <= reached && status !== 'CANCELLED' ? 'bg-foreground' : 'bg-elevated',
              )}
            />
            <span
              className={cn('text-caption sm:text-sm', step === status ? 'font-semibold' : 'text-muted-foreground')}
            >
              {flightStatusLabel[step]}
            </span>
          </li>
        ))}
      </ol>
      {(status === 'DELAYED' || status === 'CANCELLED') && (
        <p className="text-sm">
          <FlightStatusBadge status={status} /> <span className="text-muted-foreground">branch off the schedule</span>
        </p>
      )}
    </div>
  )
}

function StatusCard({ flight, manifest }: { flight: FlightResponse; manifest: FlightCrewResponse[] }) {
  const [target, setTarget] = React.useState<FlightStatus | null>(null)
  const setStatus = useSetFlightStatus(flight.id)
  const options = nextStatuses(flight.status)

  return (
    <section
      aria-labelledby="status-title"
      className="flex flex-col gap-5 rounded-card border border-border bg-card p-5 sm:p-6"
    >
      <div className="flex items-center justify-between gap-3">
        <h2 id="status-title" className="text-h3 font-semibold">
          Status
        </h2>
        <FlightStatusBadge status={flight.status} />
      </div>
      <StatusStepper status={flight.status} />
      {options.length > 0 ? (
        <div className="flex flex-wrap gap-2">
          {options.map((status) => (
            <Button
              key={status}
              variant={status === 'DELAYED' || status === 'SCHEDULED' ? 'outline' : 'default'}
              onClick={() => setTarget(status)}
            >
              {transitionCopy[status]}
            </Button>
          ))}
        </div>
      ) : (
        <p className="text-sm text-muted-foreground">
          This flight is {flightStatusLabel[flight.status].toLowerCase()}. No further changes.
        </p>
      )}

      <ResponsiveSheet
        open={target !== null}
        onOpenChange={(open) => {
          if (!open) {
            setTarget(null)
            setStatus.reset()
          }
        }}
        title={target ? transitionCopy[target] : ''}
        description={
          target && `${flight.flightNumber}: ${flightStatusLabel[flight.status]} → ${flightStatusLabel[target]}`
        }
        footer={
          <Button
            className="w-full"
            loading={setStatus.isPending}
            onClick={() => target && setStatus.mutate(target, { onSuccess: () => setTarget(null) })}
          >
            {target ? transitionCopy[target] : 'Confirm'}
          </Button>
        }
      >
        <div className="flex flex-col gap-4">
          {target && requiresCrew(target) && (
            <div className="flex flex-col gap-3 rounded-2xl bg-elevated p-4">
              <span className="text-sm font-medium">Crew check</span>
              <Readiness manifest={manifest} />
              {!crewReadiness(manifest).ready && (
                <p className="text-sm text-muted-foreground">
                  {flightStatusLabel[target]} needs at least one captain and one first officer. Assign them in the crew
                  manifest first.
                </p>
              )}
            </div>
          )}
          {target === 'DELAYED' && (
            <p className="text-sm text-muted-foreground">Passengers can still book and check in on a delayed flight.</p>
          )}
          {setStatus.isError && (
            <p role="alert" className="text-sm text-destructive">
              {errorText(setStatus.error)}
            </p>
          )}
        </div>
      </ResponsiveSheet>
    </section>
  )
}

function crewOption(member: CrewMemberResponse, departureDay: string) {
  const expired = member.licenseExpiryDate < departureDay
  return {
    value: member.id,
    label: `${member.firstName} ${member.lastName} · ${member.employeeNumber}`,
    keywords: [member.employeeNumber, member.firstName, member.lastName],
    disabledReason: expired
      ? `License expires ${formatLocalDate(member.licenseExpiryDate)}, before departure`
      : undefined,
    render: (
      <span className="flex flex-col">
        <span className="font-medium">
          {member.firstName} {member.lastName}{' '}
          <span className="ml-1 font-mono text-sm text-muted-foreground">{member.employeeNumber}</span>
        </span>
        <span className="text-caption text-muted-foreground">
          {crewRoleLabel[member.role]} · license to {formatLocalDate(member.licenseExpiryDate)}
        </span>
      </span>
    ),
  }
}

function AssignCrewSheet({
  flight,
  open,
  onOpenChange,
}: {
  flight: FlightResponse
  open: boolean
  onOpenChange: (open: boolean) => void
}) {
  const [query, setQuery] = React.useState('')
  const q = useDebounced(query.trim(), 250)
  const crew = useCrewList({ q: q || undefined })
  const assign = useAssignCrew(flight.id)
  const [memberId, setMemberId] = React.useState<string | null>(null)
  const [role, setRole] = React.useState<CrewRole | ''>('')
  const departureDay = flight.departureTime.slice(0, 10)
  const members = crew.data ?? []
  const selected = members.find((m) => m.id === memberId)

  const close = (next: boolean) => {
    onOpenChange(next)
    if (!next) {
      setMemberId(null)
      setRole('')
      setQuery('')
      assign.reset()
    }
  }

  return (
    <ResponsiveSheet
      open={open}
      onOpenChange={close}
      title="Assign crew"
      description={`${flight.flightNumber} · ${flightTimes(flight).departureDate}`}
      footer={
        <Button
          className="w-full"
          disabled={!memberId || !role}
          loading={assign.isPending}
          onClick={() =>
            memberId && role && assign.mutate({ crewMemberId: memberId, role }, { onSuccess: () => close(false) })
          }
        >
          Assign to flight
        </Button>
      }
    >
      <div className="flex flex-col gap-5">
        <FormField label="Crew member">
          {(control) => (
            <Combobox
              {...control}
              value={memberId}
              onChange={(value) => {
                setMemberId(value)
                const member = members.find((m) => m.id === value)
                if (member) setRole(member.role)
              }}
              onSearch={setQuery}
              loading={crew.isFetching}
              options={members.map((member) => crewOption(member, departureDay))}
              placeholder="Search name or employee number"
              searchPlaceholder="Name or employee number"
              emptyText="No crew members match"
            />
          )}
        </FormField>
        <FormField
          label="Assigned role"
          description={selected ? `Home role: ${crewRoleLabel[selected.role]}` : undefined}
        >
          {(control) => (
            <Select value={role} onValueChange={(value) => setRole(value as CrewRole)}>
              <SelectTrigger {...control} className="w-full">
                <SelectValue placeholder="Choose a role" />
              </SelectTrigger>
              <SelectContent>
                {CREW_ROLES.map((r) => (
                  <SelectItem key={r} value={r} className="min-h-10">
                    {crewRoleLabel[r]}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          )}
        </FormField>
        {assign.isError && (
          <p role="alert" className="rounded-2xl bg-elevated p-4 text-sm text-destructive">
            {errorText(assign.error)}
          </p>
        )}
      </div>
    </ResponsiveSheet>
  )
}

function CrewCard({
  flight,
  manifest,
  loading,
}: {
  flight: FlightResponse
  manifest: FlightCrewResponse[]
  loading: boolean
}) {
  const [assignOpen, setAssignOpen] = React.useState(false)
  const [removing, setRemoving] = React.useState<FlightCrewResponse | null>(null)
  const remove = useRemoveCrew(flight.id)

  return (
    <section
      aria-labelledby="crew-title"
      className="flex flex-col gap-5 rounded-card border border-border bg-card p-5 sm:p-6"
    >
      <div className="flex items-center justify-between gap-3">
        <h2 id="crew-title" className="text-h3 font-semibold">
          Crew manifest
        </h2>
        <Button
          size="sm"
          onClick={() => setAssignOpen(true)}
          disabled={flight.status === 'ARRIVED' || flight.status === 'CANCELLED'}
        >
          <Plus strokeWidth={1.75} aria-hidden />
          Assign crew
        </Button>
      </div>
      <Readiness manifest={manifest} />
      {loading ? (
        <SkeletonCard className="h-32 border-0 p-0" lines={2} />
      ) : manifest.length === 0 ? (
        <p className="rounded-2xl bg-elevated p-4 text-sm text-muted-foreground">No crew assigned yet.</p>
      ) : (
        <ul className="flex flex-col">
          {manifest.map((member) => (
            <li
              key={member.crewMemberId}
              className="flex min-h-[52px] items-center gap-3 border-b border-border py-2 text-sm last:border-0"
            >
              <span className="min-w-0 flex-1">
                <span className="font-medium">
                  {member.firstName} {member.lastName}
                </span>
                <span className="ml-2 font-mono text-muted-foreground">{member.employeeNumber}</span>
              </span>
              <span className="text-muted-foreground">{crewRoleLabel[member.assignedRole]}</span>
              <Button
                variant="ghost"
                size="icon-sm"
                aria-label={`Remove ${member.firstName} ${member.lastName} from the flight`}
                onClick={() => setRemoving(member)}
              >
                <Trash2 strokeWidth={1.75} aria-hidden />
              </Button>
            </li>
          ))}
        </ul>
      )}
      <AssignCrewSheet flight={flight} open={assignOpen} onOpenChange={setAssignOpen} />
      <ConfirmDialog
        open={removing !== null}
        onOpenChange={(open) => {
          if (!open) {
            setRemoving(null)
            remove.reset()
          }
        }}
        title="Remove from this flight?"
        description={
          removing &&
          `${removing.firstName} ${removing.lastName} (${crewRoleLabel[removing.assignedRole]}) will be taken off ${flight.flightNumber}.`
        }
        confirmLabel="Remove"
        destructive
        loading={remove.isPending}
        error={remove.isError ? errorText(remove.error) : null}
        onConfirm={() => removing && remove.mutate(removing.crewMemberId, { onSuccess: () => setRemoving(null) })}
      />
    </section>
  )
}

function InventoryCard({ flight }: { flight: FlightResponse }) {
  const capacity = capacityOf(flight)
  const factor = loadFactor(flight)
  return (
    <section
      aria-labelledby="inventory-title"
      className="flex flex-col gap-5 rounded-card border border-border bg-card p-5 sm:p-6"
    >
      <h2 id="inventory-title" className="text-h3 font-semibold">
        Inventory
      </h2>
      <div className="flex items-end gap-2">
        <span className="hero-number text-display-sm">{flight.availableSeats}</span>
        <span className="pb-1 text-muted-foreground tabular">/ {capacity} seats left</span>
      </div>
      <div
        className="h-2 overflow-hidden rounded-full bg-elevated"
        role="meter"
        aria-label="Load factor"
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuenow={Math.round(factor * 100)}
      >
        <div
          className="h-full rounded-full bg-foreground transition-[width] duration-500"
          style={{ width: `${factor * 100}%` }}
        />
      </div>
      <p className="text-sm text-muted-foreground tabular">
        Estimated load factor {Math.round(factor * 100)}%{factor > 0.8 ? ' · demand surcharge applies' : ''}
      </p>
    </section>
  )
}

function DangerZone({ flight }: { flight: FlightResponse }) {
  const [open, setOpen] = React.useState(false)
  const cancel = useCancelFlight(flight.id)
  if (!isCancellableFlight(flight.status)) return null
  return (
    <section
      aria-labelledby="danger-title"
      className="flex flex-col gap-4 rounded-card border border-border p-5 sm:flex-row sm:items-center sm:justify-between sm:p-6"
    >
      <div className="flex flex-col gap-1">
        <h2 id="danger-title" className="font-semibold">
          Cancel this flight
        </h2>
        <p className="text-sm text-muted-foreground">All bookings on it are cancelled too. This can’t be undone.</p>
      </div>
      <Button variant="destructive" onClick={() => setOpen(true)}>
        Cancel flight
      </Button>
      <ConfirmDialog
        open={open}
        onOpenChange={(next) => {
          setOpen(next)
          if (!next) cancel.reset()
        }}
        title={`Cancel ${flight.flightNumber}?`}
        description={
          <>
            <p>
              {flight.route ? `${flight.route.origin.city} to ${flight.route.destination.city}, ` : ''}
              {flightTimes(flight).departureDate}.
            </p>
            <p className="font-medium text-foreground">Every passenger booking on this flight will be cancelled.</p>
          </>
        }
        typedConfirmation={flight.flightNumber}
        confirmLabel="Cancel flight"
        cancelLabel="Keep flight"
        destructive
        loading={cancel.isPending}
        error={cancel.isError ? errorText(cancel.error) : null}
        onConfirm={() => cancel.mutate(undefined, { onSuccess: () => setOpen(false) })}
      />
    </section>
  )
}

export function Component() {
  const { id = '' } = useParams()
  const { isAdmin } = useAuth()
  const flightQuery = useFlight(id)
  const manifestQuery = useManifest(id)
  const flight = flightQuery.data
  useDocumentTitle(flight ? `${flight.flightNumber} · Ops` : 'Flight · Ops')

  if (flightQuery.isPending) {
    return (
      <LoadingRegion label="Loading flight">
        <SkeletonCard className="h-96" lines={5} />
      </LoadingRegion>
    )
  }
  if (flightQuery.isError) {
    return isApiError(flightQuery.error) && flightQuery.error.status === 404 ? (
      <EmptyState
        title="Flight not found"
        action={
          <Button asChild variant="outline">
            <Link to="/ops/flights">Back to the board</Link>
          </Button>
        }
      />
    ) : (
      <ErrorState error={flightQuery.error} onRetry={() => void flightQuery.refetch()} />
    )
  }

  const manifest = manifestQuery.data ?? []
  return (
    <div className="flex flex-col gap-6">
      <OpsPageHeader
        title={<span className="font-mono tracking-[-0.02em]">{flight!.flightNumber}</span>}
        description={
          <span className="flex flex-wrap items-center gap-3">
            <RouteLabel route={flight!.route} withDuration />
            <span>{flightTimes(flight!).departureDate}</span>
            <span className="font-mono">{flight!.aircraft?.tailNumber}</span>
          </span>
        }
        actions={
          isEditableFlight(flight!.status) && (
            <Button asChild variant="outline">
              <Link to={`/ops/flights/${flight!.id}/edit`}>
                <Pencil strokeWidth={1.75} aria-hidden />
                Edit flight
              </Link>
            </Button>
          )
        }
      />
      <div className="rounded-card border border-border bg-card p-5 sm:p-6">
        <FlightTimeline flight={flight!} />
      </div>
      <div className="grid gap-6 xl:grid-cols-3">
        <StatusCard flight={flight!} manifest={manifest} />
        <CrewCard flight={flight!} manifest={manifest} loading={manifestQuery.isPending} />
        <InventoryCard flight={flight!} />
      </div>
      {manifestQuery.isError && (
        <ErrorState
          error={manifestQuery.error}
          onRetry={() => void manifestQuery.refetch()}
          title="We couldn’t load the crew manifest"
        />
      )}
      {isAdmin && <DangerZone flight={flight!} />}
    </div>
  )
}
