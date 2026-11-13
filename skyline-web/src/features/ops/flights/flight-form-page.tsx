import * as React from 'react'
import { zodResolver } from '@hookform/resolvers/zod'
import { Info } from 'lucide-react'
import { Controller, useForm, useWatch } from 'react-hook-form'
import { Link, useNavigate, useParams } from 'react-router'
import { z } from 'zod'

import type { FlightResponse, RouteResponse } from '@/api/types'
import { OpsPageHeader } from '@/components/layout/ops-shell'
import { Combobox } from '@/components/skyline/combobox'
import { RouteLabel } from '@/components/skyline/flight'
import { applyServerErrors, FormErrorSummary, FormField } from '@/components/skyline/form'
import { ErrorState, LoadingRegion, SkeletonCard } from '@/components/skyline/states'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { isEditableFlight } from '@/lib/flight-status'
import { airportLocalToApi, apiToAirportLocal, formatDateTimeInZone, parseUtc, zoneOffsetLabel } from '@/lib/format'
import { useDocumentTitle } from '@/lib/hooks'
import { aircraftStatusLabel, flightStatusLabel } from '@/lib/labels'
import { useFlight, useRoutes } from '@/features/search/queries'
import { useAircraft, useSaveFlight } from '../queries'

const LOCAL = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/

function toUtcSafe(local: string, zone: string | undefined) {
  if (!LOCAL.test(local) || !zone) return null
  try {
    return airportLocalToApi(local, zone)
  } catch {
    return null
  }
}

function buildSchema(routes: RouteResponse[]) {
  const zones = (routeId: string) => {
    const route = routes.find((r) => r.id === routeId)
    return { origin: route?.origin.timezone, destination: route?.destination.timezone }
  }
  return z
    .object({
      flightNumber: z
        .string()
        .trim()
        .min(3, 'Use 3 to 16 characters')
        .max(16, 'Use 3 to 16 characters')
        .regex(/^[A-Z0-9]+$/, 'Letters and digits only, e.g. SK101'),
      routeId: z.string().min(1, 'Choose a route'),
      aircraftId: z.string().min(1, 'Choose an aircraft'),
      departureLocal: z.string().regex(LOCAL, 'Enter the departure date and time'),
      arrivalLocal: z.string().regex(LOCAL, 'Enter the arrival date and time'),
      basePrice: z.number({ error: 'Enter a base price' }).positive('The base price must be more than 0'),
    })
    .superRefine((values, ctx) => {
      const { origin, destination } = zones(values.routeId)
      const departure = toUtcSafe(values.departureLocal, origin)
      const arrival = toUtcSafe(values.arrivalLocal, destination)
      const now = Date.now()
      if (departure && parseUtc(departure).getTime() <= now) {
        ctx.addIssue({ code: 'custom', path: ['departureLocal'], message: 'Departure must be in the future' })
      }
      if (arrival && parseUtc(arrival).getTime() <= now) {
        ctx.addIssue({ code: 'custom', path: ['arrivalLocal'], message: 'Arrival must be in the future' })
      }
      if (departure && arrival && parseUtc(arrival).getTime() <= parseUtc(departure).getTime()) {
        ctx.addIssue({ code: 'custom', path: ['arrivalLocal'], message: 'Arrival must be after departure' })
      }
    })
}
type Values = z.infer<ReturnType<typeof buildSchema>>

const LABELS: Record<string, string> = {
  flightNumber: 'Flight number',
  routeId: 'Route',
  aircraftId: 'Aircraft',
  departureLocal: 'Departure',
  arrivalLocal: 'Arrival',
  basePrice: 'Base price',
}

/** "Equals 2026-12-01 07:00 UTC" under an airport-local input. */
function UtcHint({ local, zone }: { local: string; zone: string | undefined }) {
  const utc = toUtcSafe(local, zone)
  if (!utc) return null
  return <p className="text-caption text-muted-foreground tabular">{formatDateTimeInZone(utc, 'UTC')} UTC</p>
}

function FlightForm({ flight, routes }: { flight?: FlightResponse; routes: RouteResponse[] }) {
  const navigate = useNavigate()
  const { data: aircraft = [], isPending: aircraftLoading } = useAircraft()
  const save = useSaveFlight(flight?.id)
  const [serverError, setServerError] = React.useState<string | null>(null)
  const arrivalTouched = React.useRef(!!flight)
  const schema = React.useMemo(() => buildSchema(routes), [routes])

  const form = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: flight
      ? {
          flightNumber: flight.flightNumber,
          routeId: flight.route?.id ?? '',
          aircraftId: flight.aircraft?.id ?? '',
          departureLocal: apiToAirportLocal(flight.departureTime, flight.route?.origin.timezone),
          arrivalLocal: apiToAirportLocal(flight.arrivalTime, flight.route?.destination.timezone),
          basePrice: flight.basePrice,
        }
      : { flightNumber: '', routeId: '', aircraftId: '', departureLocal: '', arrivalLocal: '', basePrice: undefined },
  })
  const { errors, submitCount, isSubmitting } = form.formState
  const [routeId, departureLocal, arrivalLocal] = useWatch({
    control: form.control,
    name: ['routeId', 'departureLocal', 'arrivalLocal'],
  })
  const route = routes.find((r) => r.id === routeId)
  const originZone = route?.origin.timezone
  const destinationZone = route?.destination.timezone

  // Arrival = departure + route duration, converted across the two zones, until the user edits it.
  React.useEffect(() => {
    if (arrivalTouched.current || !route) return
    const departure = toUtcSafe(departureLocal, route.origin.timezone)
    if (!departure) return
    const arrivalUtc = new Date(parseUtc(departure).getTime() + route.estimatedDurationMinutes * 60_000)
    form.setValue('arrivalLocal', apiToAirportLocal(arrivalUtc.toISOString(), route.destination.timezone))
  }, [departureLocal, route, form])

  const onSubmit = form.handleSubmit(async (values) => {
    setServerError(null)
    try {
      const result = await save.mutateAsync({
        flightNumber: values.flightNumber,
        routeId: values.routeId,
        aircraftId: values.aircraftId,
        departureTime: airportLocalToApi(values.departureLocal, originZone),
        arrivalTime: airportLocalToApi(values.arrivalLocal, destinationZone),
        basePrice: values.basePrice,
      })
      navigate(`/ops/flights/${result.data.id}`)
    } catch (error) {
      setServerError(
        applyServerErrors(
          error,
          (field, message) =>
            form.setError(
              (field === 'departureTime'
                ? 'departureLocal'
                : field === 'arrivalTime'
                  ? 'arrivalLocal'
                  : field) as keyof Values,
              message,
            ),
          ['flightNumber', 'routeId', 'aircraftId', 'departureTime', 'arrivalTime', 'basePrice'],
        ),
      )
    }
  })

  const routeOptions = routes.map((r) => ({
    value: r.id,
    label: `${r.origin.iataCode} → ${r.destination.iataCode}`,
    keywords: [r.origin.city, r.destination.city],
    render: <RouteLabel route={r} withDuration />,
  }))
  const aircraftOptions = aircraft.map((a) => ({
    value: a.id,
    label: `${a.tailNumber} · ${a.aircraftType.model}`,
    keywords: [a.aircraftType.manufacturer, a.aircraftType.model],
    disabledReason:
      a.status === 'ACTIVE' ? undefined : `${aircraftStatusLabel[a.status]}: only active aircraft can fly`,
    render: (
      <span className="flex items-baseline gap-3">
        <span className="font-mono font-semibold">{a.tailNumber}</span>
        <span className="text-muted-foreground">
          {a.aircraftType.manufacturer} {a.aircraftType.model} · {a.aircraftType.totalSeats} seats
        </span>
      </span>
    ),
  }))

  return (
    <form onSubmit={onSubmit} noValidate className="flex max-w-3xl flex-col gap-6">
      <FormErrorSummary errors={errors} serverError={serverError} submitCount={submitCount} labels={LABELS} />
      <div className="flex flex-col gap-5 rounded-card border border-border bg-card p-5 sm:p-6">
        <div className="grid gap-5 sm:grid-cols-[200px_1fr]">
          <FormField label="Flight number" error={errors.flightNumber?.message}>
            {(control) => (
              <Input
                {...control}
                autoComplete="off"
                className="font-mono uppercase"
                {...form.register('flightNumber', { setValueAs: (v: string) => v.toUpperCase().replace(/\s/g, '') })}
              />
            )}
          </FormField>
          <Controller
            control={form.control}
            name="routeId"
            render={({ field, fieldState }) => (
              <FormField label="Route" error={fieldState.error?.message}>
                {(control) => (
                  <Combobox
                    {...control}
                    value={field.value}
                    onChange={(v) => field.onChange(v ?? '')}
                    options={routeOptions}
                    placeholder="Choose a route"
                  />
                )}
              </FormField>
            )}
          />
        </div>
        <Controller
          control={form.control}
          name="aircraftId"
          render={({ field, fieldState }) => (
            <FormField
              label="Aircraft"
              description="Only active aircraft can be scheduled."
              error={fieldState.error?.message}
            >
              {(control) => (
                <Combobox
                  {...control}
                  value={field.value}
                  onChange={(v) => field.onChange(v ?? '')}
                  options={aircraftOptions}
                  loading={aircraftLoading}
                  placeholder="Choose an aircraft"
                />
              )}
            </FormField>
          )}
        />
      </div>

      <div className="grid gap-5 rounded-card border border-border bg-card p-5 sm:grid-cols-2 sm:p-6">
        <FormField
          label={`Departure · ${originZone ?? 'origin time'}${originZone ? ` (${zoneOffsetLabel(originZone)})` : ''}`}
          error={errors.departureLocal?.message}
        >
          {(control) => (
            <div className="flex flex-col gap-1.5">
              <Input
                {...control}
                type="datetime-local"
                disabled={!route}
                className="tabular"
                {...form.register('departureLocal')}
              />
              <UtcHint local={departureLocal} zone={originZone} />
            </div>
          )}
        </FormField>
        <FormField
          label={`Arrival · ${destinationZone ?? 'destination time'}${destinationZone ? ` (${zoneOffsetLabel(destinationZone)})` : ''}`}
          error={errors.arrivalLocal?.message}
        >
          {(control) => (
            <div className="flex flex-col gap-1.5">
              <Input
                {...control}
                type="datetime-local"
                disabled={!route}
                className="tabular"
                {...form.register('arrivalLocal', { onChange: () => (arrivalTouched.current = true) })}
              />
              <UtcHint local={arrivalLocal} zone={destinationZone} />
            </div>
          )}
        </FormField>
        {!route && (
          <p className="text-sm text-muted-foreground sm:col-span-2">
            Choose a route to enter times in each airport’s local time.
          </p>
        )}
      </div>

      <div className="rounded-card border border-border bg-card p-5 sm:p-6">
        <FormField
          label={`Base price (${flight?.currency ?? 'EUR'})`}
          description="Economy fare before cabin and demand multipliers."
          error={errors.basePrice?.message}
        >
          {(control) => (
            <div className="relative max-w-xs">
              <span className="pointer-events-none absolute top-1/2 left-4 -translate-y-1/2 text-muted-foreground">
                {
                  new Intl.NumberFormat(undefined, { style: 'currency', currency: flight?.currency ?? 'EUR' })
                    .formatToParts(0)
                    .find((p) => p.type === 'currency')?.value
                }
              </span>
              <Input
                {...control}
                type="number"
                inputMode="decimal"
                step="0.01"
                min="0.01"
                className="pl-10 tabular"
                {...form.register('basePrice', { valueAsNumber: true })}
              />
            </div>
          )}
        </FormField>
      </div>

      <div className="flex flex-wrap gap-3">
        <Button type="submit" size="lg" loading={isSubmitting}>
          {flight ? 'Save changes' : 'Create flight'}
        </Button>
        <Button asChild variant="ghost" size="lg">
          <Link to={flight ? `/ops/flights/${flight.id}` : '/ops/flights'}>Cancel</Link>
        </Button>
      </div>
    </form>
  )
}

export function Component() {
  const { id } = useParams()
  const editing = !!id
  useDocumentTitle(editing ? 'Edit flight · Ops' : 'New flight · Ops')
  const flightQuery = useFlight(id)
  const routesQuery = useRoutes()

  if ((editing && flightQuery.isPending) || routesQuery.isPending) {
    return (
      <LoadingRegion label="Loading">
        <SkeletonCard className="h-96 max-w-3xl" lines={5} />
      </LoadingRegion>
    )
  }
  if (flightQuery.isError || routesQuery.isError) {
    return (
      <ErrorState
        error={flightQuery.error ?? routesQuery.error}
        onRetry={() => void (flightQuery.refetch(), routesQuery.refetch())}
      />
    )
  }

  const flight = flightQuery.data
  return (
    <>
      <OpsPageHeader
        title={editing && flight ? `Edit ${flight.flightNumber}` : 'New flight'}
        description="Times are entered in each airport’s local time and stored in UTC."
      />
      {flight && !isEditableFlight(flight.status) ? (
        <div className="flex max-w-3xl items-start gap-3 rounded-2xl border border-border bg-elevated p-4">
          <Info className="mt-0.5 size-[18px] shrink-0" strokeWidth={1.75} aria-hidden />
          <p className="text-sm">
            This flight is {flightStatusLabel[flight.status].toLowerCase()}. Flights can only be edited while scheduled
            or delayed.{' '}
            <Link
              to={`/ops/flights/${flight.id}`}
              className="rounded font-medium underline underline-offset-4 focus-ring"
            >
              Back to the flight
            </Link>
          </p>
        </div>
      ) : (
        <FlightForm flight={flight} routes={routesQuery.data} />
      )}
    </>
  )
}
