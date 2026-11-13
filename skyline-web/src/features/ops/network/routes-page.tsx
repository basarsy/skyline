import * as React from 'react'
import { zodResolver } from '@hookform/resolvers/zod'
import { Plus } from 'lucide-react'
import { Controller, useForm, useWatch } from 'react-hook-form'
import { z } from 'zod'

import type { RouteResponse } from '@/api/types'
import { useAuth } from '@/auth/use-auth'
import { OpsPageHeader } from '@/components/layout/ops-shell'
import { DataTable, type ColumnDef } from '@/components/skyline/data-table'
import { RouteLabel } from '@/components/skyline/flight'
import { applyServerErrors, FormErrorSummary, FormField } from '@/components/skyline/form'
import { AirportCombobox } from '@/components/skyline/pickers'
import { ResponsiveSheet } from '@/components/skyline/responsive-sheet'
import { EmptyState, ErrorState } from '@/components/skyline/states'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { formatDuration } from '@/lib/format'
import { useDocumentTitle } from '@/lib/hooks'
import { useRoutes } from '@/features/search/queries'
import { useCreateRoute } from '../queries'
import { NETWORK_TABS, SectionTabs } from '../section-tabs'

const schema = z
  .object({
    originAirportId: z.string().min(1, 'Choose the origin'),
    destinationAirportId: z.string().min(1, 'Choose the destination'),
    distanceKm: z.number({ error: 'Enter the distance' }).int('Whole kilometres').min(1, 'At least 1 km'),
    estimatedDurationMinutes: z
      .number({ error: 'Enter the duration' })
      .int('Whole minutes')
      .min(1, 'At least 1 minute'),
  })
  .refine((v) => v.originAirportId !== v.destinationAirportId, {
    path: ['destinationAirportId'],
    message: 'Destination must differ from the origin',
  })
type Values = z.infer<typeof schema>
const LABELS = {
  originAirportId: 'Origin',
  destinationAirportId: 'Destination',
  distanceKm: 'Distance',
  estimatedDurationMinutes: 'Duration',
}

function AddRouteSheet({ open, onOpenChange }: { open: boolean; onOpenChange: (open: boolean) => void }) {
  const create = useCreateRoute()
  const [serverError, setServerError] = React.useState<string | null>(null)
  const form = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: { originAirportId: '', destinationAirportId: '' },
  })
  const { errors, submitCount, isSubmitting } = form.formState
  const [origin, minutes] = useWatch({ control: form.control, name: ['originAirportId', 'estimatedDurationMinutes'] })
  const close = (next: boolean) => {
    onOpenChange(next)
    if (!next) {
      form.reset()
      setServerError(null)
    }
  }
  const onSubmit = form.handleSubmit(async (values) => {
    setServerError(null)
    try {
      await create.mutateAsync(values)
      close(false)
    } catch (error) {
      setServerError(applyServerErrors(error, form.setError, Object.keys(LABELS)))
    }
  })
  return (
    <ResponsiveSheet
      open={open}
      onOpenChange={close}
      title="Add route"
      footer={
        <Button className="w-full" loading={isSubmitting} onClick={() => void onSubmit()}>
          Add route
        </Button>
      }
    >
      <form onSubmit={onSubmit} noValidate className="flex flex-col gap-5">
        <FormErrorSummary errors={errors} serverError={serverError} submitCount={submitCount} labels={LABELS} />
        <Controller
          control={form.control}
          name="originAirportId"
          render={({ field, fieldState }) => (
            <FormField label="Origin" error={fieldState.error?.message}>
              {(control) => (
                <AirportCombobox
                  {...control}
                  valueBy="id"
                  value={field.value}
                  onChange={(v) => field.onChange(v ?? '')}
                />
              )}
            </FormField>
          )}
        />
        <Controller
          control={form.control}
          name="destinationAirportId"
          render={({ field, fieldState }) => (
            <FormField label="Destination" error={fieldState.error?.message}>
              {(control) => (
                <AirportCombobox
                  {...control}
                  valueBy="id"
                  exclude={origin}
                  value={field.value}
                  onChange={(v) => field.onChange(v ?? '')}
                />
              )}
            </FormField>
          )}
        />
        <div className="grid grid-cols-2 gap-4">
          <FormField label="Distance (km)" error={errors.distanceKm?.message}>
            {(control) => (
              <Input
                {...control}
                type="number"
                inputMode="numeric"
                min={1}
                className="tabular"
                {...form.register('distanceKm', { valueAsNumber: true })}
              />
            )}
          </FormField>
          <FormField
            label="Duration (minutes)"
            description={minutes && minutes > 0 ? formatDuration(minutes) : undefined}
            error={errors.estimatedDurationMinutes?.message}
          >
            {(control) => (
              <Input
                {...control}
                type="number"
                inputMode="numeric"
                min={1}
                className="tabular"
                {...form.register('estimatedDurationMinutes', { valueAsNumber: true })}
              />
            )}
          </FormField>
        </div>
      </form>
    </ResponsiveSheet>
  )
}

export function Component() {
  useDocumentTitle('Routes · Ops')
  const { isAdmin } = useAuth()
  const { data = [], isPending, isError, error, refetch } = useRoutes()
  const [adding, setAdding] = React.useState(false)
  const columns = React.useMemo<ColumnDef<RouteResponse, unknown>[]>(
    () => [
      {
        id: 'route',
        accessorFn: (r) => `${r.origin.iataCode}-${r.destination.iataCode}`,
        header: 'Route',
        cell: ({ row }) => <RouteLabel route={row.original} />,
      },
      {
        id: 'cities',
        accessorFn: (r) => r.origin.city,
        header: 'Cities',
        cell: ({ row }) => (
          <span className="text-muted-foreground">
            {row.original.origin.city} → {row.original.destination.city}
          </span>
        ),
      },
      {
        id: 'distance',
        accessorKey: 'distanceKm',
        header: 'Distance',
        cell: ({ row }) => `${row.original.distanceKm.toLocaleString()} km`,
      },
      {
        id: 'duration',
        accessorKey: 'estimatedDurationMinutes',
        header: 'Duration',
        cell: ({ row }) => formatDuration(row.original.estimatedDurationMinutes),
      },
    ],
    [],
  )
  return (
    <>
      <OpsPageHeader
        title="Network"
        description={`${data.length} routes`}
        actions={
          isAdmin && (
            <Button onClick={() => setAdding(true)}>
              <Plus strokeWidth={1.75} aria-hidden />
              Add route
            </Button>
          )
        }
      />
      <SectionTabs label="Network" tabs={NETWORK_TABS} />
      {isError ? (
        <ErrorState error={error} onRetry={() => void refetch()} />
      ) : (
        <DataTable
          label="Routes"
          columns={columns}
          data={data}
          loading={isPending}
          getRowId={(r) => r.id}
          empty={<EmptyState className="border-0" title="No routes yet" />}
        />
      )}
      {isAdmin && <AddRouteSheet open={adding} onOpenChange={setAdding} />}
    </>
  )
}
