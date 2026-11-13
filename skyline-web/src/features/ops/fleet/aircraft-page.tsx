import * as React from 'react'
import { zodResolver } from '@hookform/resolvers/zod'
import { MoreHorizontal, Plus } from 'lucide-react'
import { Controller, useForm } from 'react-hook-form'
import { z } from 'zod'

import type { AircraftResponse, AircraftStatus } from '@/api/types'
import { useAuth } from '@/auth/use-auth'
import { OpsPageHeader } from '@/components/layout/ops-shell'
import { Combobox } from '@/components/skyline/combobox'
import { ConfirmDialog } from '@/components/skyline/confirm-dialog'
import { DataTable, type ColumnDef } from '@/components/skyline/data-table'
import { applyServerErrors, FormErrorSummary, FormField } from '@/components/skyline/form'
import { ResponsiveSheet } from '@/components/skyline/responsive-sheet'
import { EmptyState, ErrorState } from '@/components/skyline/states'
import { AircraftStatusBadge } from '@/components/skyline/status-badge'
import { Button } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { Input } from '@/components/ui/input'
import { useDocumentTitle } from '@/lib/hooks'
import { AIRCRAFT_STATUSES, aircraftStatusLabel } from '@/lib/labels'
import { useAircraft, useAircraftTypes, useCreateAircraft, useSetAircraftStatus } from '../queries'
import { FLEET_TABS, SectionTabs } from '../section-tabs'

const thisYear = new Date().getFullYear()
const schema = z.object({
  tailNumber: z
    .string()
    .trim()
    .min(2, 'Enter the tail number')
    .max(16, 'Use at most 16 characters')
    .regex(/^[A-Z0-9-]+$/, 'Letters, digits and dashes, e.g. TC-SKA'),
  aircraftTypeId: z.string().min(1, 'Choose an aircraft type'),
  manufacturedYear: z
    .number({ error: 'Enter the year' })
    .int('Enter a whole year')
    .min(1980, 'Between 1980 and 2100')
    .max(2100, 'Between 1980 and 2100'),
})
type Values = z.infer<typeof schema>

function AddAircraftSheet({ open, onOpenChange }: { open: boolean; onOpenChange: (open: boolean) => void }) {
  const types = useAircraftTypes()
  const create = useCreateAircraft()
  const [serverError, setServerError] = React.useState<string | null>(null)
  const form = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: { tailNumber: '', aircraftTypeId: '', manufacturedYear: thisYear },
  })
  const { errors, submitCount, isSubmitting } = form.formState

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
      setServerError(applyServerErrors(error, form.setError, ['tailNumber', 'aircraftTypeId', 'manufacturedYear']))
    }
  })

  return (
    <ResponsiveSheet
      open={open}
      onOpenChange={close}
      title="Add aircraft"
      description="New aircraft start as active."
      footer={
        <Button className="w-full" loading={isSubmitting} onClick={() => void onSubmit()}>
          Add aircraft
        </Button>
      }
    >
      <form onSubmit={onSubmit} noValidate className="flex flex-col gap-5">
        <FormErrorSummary
          errors={errors}
          serverError={serverError}
          submitCount={submitCount}
          labels={{ tailNumber: 'Tail number', aircraftTypeId: 'Aircraft type', manufacturedYear: 'Year built' }}
        />
        <FormField label="Tail number" error={errors.tailNumber?.message}>
          {(control) => (
            <Input
              {...control}
              autoComplete="off"
              className="font-mono uppercase"
              {...form.register('tailNumber', { setValueAs: (v: string) => v.toUpperCase().trim() })}
            />
          )}
        </FormField>
        <Controller
          control={form.control}
          name="aircraftTypeId"
          render={({ field, fieldState }) => (
            <FormField label="Aircraft type" error={fieldState.error?.message}>
              {(control) => (
                <Combobox
                  {...control}
                  value={field.value}
                  onChange={(v) => field.onChange(v ?? '')}
                  loading={types.isPending}
                  placeholder="Choose a type"
                  options={(types.data ?? []).map((type) => ({
                    value: type.id,
                    label: `${type.manufacturer} ${type.model}`,
                    render: (
                      <span>
                        {type.manufacturer} {type.model}{' '}
                        <span className="text-muted-foreground">· {type.totalSeats} seats</span>
                      </span>
                    ),
                  }))}
                />
              )}
            </FormField>
          )}
        />
        <FormField label="Year built" error={errors.manufacturedYear?.message}>
          {(control) => (
            <Input
              {...control}
              type="number"
              inputMode="numeric"
              min={1980}
              max={2100}
              className="max-w-40 tabular"
              {...form.register('manufacturedYear', { valueAsNumber: true })}
            />
          )}
        </FormField>
      </form>
    </ResponsiveSheet>
  )
}

function StatusMenu({ aircraft }: { aircraft: AircraftResponse }) {
  const setStatus = useSetAircraftStatus()
  const [target, setTarget] = React.useState<AircraftStatus | null>(null)
  return (
    <>
      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <Button
            variant="ghost"
            size="icon-sm"
            aria-label={`Change status of ${aircraft.tailNumber}`}
            onClick={(event) => event.stopPropagation()}
          >
            <MoreHorizontal strokeWidth={1.75} aria-hidden />
          </Button>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="end" onClick={(event) => event.stopPropagation()}>
          <DropdownMenuLabel className="micro-label">Set status</DropdownMenuLabel>
          {AIRCRAFT_STATUSES.filter((status) => status !== aircraft.status).map((status) => (
            <DropdownMenuItem key={status} className="min-h-10 rounded-xl" onSelect={() => setTarget(status)}>
              {aircraftStatusLabel[status]}
            </DropdownMenuItem>
          ))}
        </DropdownMenuContent>
      </DropdownMenu>
      <ConfirmDialog
        open={target !== null}
        onOpenChange={(open) => !open && setTarget(null)}
        title={`Set ${aircraft.tailNumber} to ${target ? aircraftStatusLabel[target].toLowerCase() : ''}?`}
        description={
          target === 'ACTIVE'
            ? 'It can be scheduled on new flights again.'
            : 'It can’t be put on new flights while it isn’t active. Existing flights keep it.'
        }
        confirmLabel={target ? `Set to ${aircraftStatusLabel[target].toLowerCase()}` : 'Confirm'}
        cancelLabel="Cancel"
        destructive={target === 'RETIRED'}
        onConfirm={() => {
          if (target) setStatus.mutate({ id: aircraft.id, status: target })
          setTarget(null)
        }}
      />
    </>
  )
}

export function Component() {
  useDocumentTitle('Fleet · Ops')
  const { isAdmin } = useAuth()
  const { data = [], isPending, isError, error, refetch } = useAircraft()
  const [adding, setAdding] = React.useState(false)

  const columns = React.useMemo<ColumnDef<AircraftResponse, unknown>[]>(
    () => [
      {
        id: 'tail',
        accessorKey: 'tailNumber',
        header: 'Tail',
        cell: ({ row }) => <span className="font-mono font-semibold">{row.original.tailNumber}</span>,
      },
      {
        id: 'type',
        accessorFn: (a) => `${a.aircraftType.manufacturer} ${a.aircraftType.model}`,
        header: 'Type',
        cell: ({ row }) => (
          <span>
            {row.original.aircraftType.manufacturer} {row.original.aircraftType.model}
            <span className="ml-2 text-muted-foreground">{row.original.aircraftType.totalSeats} seats</span>
          </span>
        ),
      },
      { id: 'year', accessorKey: 'manufacturedYear', header: 'Year' },
      {
        id: 'status',
        accessorKey: 'status',
        header: 'Status',
        cell: ({ row }) => <AircraftStatusBadge status={row.original.status} />,
      },
      {
        id: 'actions',
        header: () => <span className="sr-only">Actions</span>,
        enableSorting: false,
        cell: ({ row }) => <StatusMenu aircraft={row.original} />,
      },
    ],
    [],
  )

  return (
    <>
      <OpsPageHeader
        title="Fleet"
        description={`${data.length} aircraft`}
        actions={
          isAdmin && (
            <Button onClick={() => setAdding(true)}>
              <Plus strokeWidth={1.75} aria-hidden />
              Add aircraft
            </Button>
          )
        }
      />
      <SectionTabs label="Fleet" tabs={FLEET_TABS} />
      {isError ? (
        <ErrorState error={error} onRetry={() => void refetch()} />
      ) : (
        <DataTable
          label="Aircraft"
          columns={columns}
          data={data}
          loading={isPending}
          getRowId={(a) => a.id}
          empty={<EmptyState className="border-0" title="No aircraft yet" />}
        />
      )}
      {isAdmin && <AddAircraftSheet open={adding} onOpenChange={setAdding} />}
    </>
  )
}
