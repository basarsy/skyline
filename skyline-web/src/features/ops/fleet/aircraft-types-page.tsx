import * as React from 'react'
import { zodResolver } from '@hookform/resolvers/zod'
import { Plus } from 'lucide-react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'

import type { AircraftTypeResponse } from '@/api/types'
import { useAuth } from '@/auth/use-auth'
import { OpsPageHeader } from '@/components/layout/ops-shell'
import { DataTable, type ColumnDef } from '@/components/skyline/data-table'
import { applyServerErrors, FormErrorSummary, FormField } from '@/components/skyline/form'
import { ResponsiveSheet } from '@/components/skyline/responsive-sheet'
import { EmptyState, ErrorState } from '@/components/skyline/states'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { useDocumentTitle } from '@/lib/hooks'
import { useAircraftTypes, useCreateAircraftType } from '../queries'
import { FLEET_TABS, SectionTabs } from '../section-tabs'

const schema = z.object({
  manufacturer: z.string().trim().min(1, 'Enter the manufacturer').max(64, 'Use at most 64 characters'),
  model: z.string().trim().min(1, 'Enter the model').max(64, 'Use at most 64 characters'),
  totalSeats: z.number({ error: 'Enter the number of seats' }).int('Enter a whole number').min(1, 'At least 1 seat'),
  cabinConfig: z.string().trim().max(64, 'Use at most 64 characters').optional(),
})
type Values = z.infer<typeof schema>
const FIELDS = ['manufacturer', 'model', 'totalSeats', 'cabinConfig'] as const

function AddTypeSheet({ open, onOpenChange }: { open: boolean; onOpenChange: (open: boolean) => void }) {
  const create = useCreateAircraftType()
  const [serverError, setServerError] = React.useState<string | null>(null)
  const form = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: { manufacturer: '', model: '', cabinConfig: '' },
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
      await create.mutateAsync({ ...values, cabinConfig: values.cabinConfig || undefined })
      close(false)
    } catch (error) {
      setServerError(applyServerErrors(error, form.setError, FIELDS))
    }
  })
  return (
    <ResponsiveSheet
      open={open}
      onOpenChange={close}
      title="Add aircraft type"
      footer={
        <Button className="w-full" loading={isSubmitting} onClick={() => void onSubmit()}>
          Add type
        </Button>
      }
    >
      <form onSubmit={onSubmit} noValidate className="flex flex-col gap-5">
        <FormErrorSummary
          errors={errors}
          serverError={serverError}
          submitCount={submitCount}
          labels={{ manufacturer: 'Manufacturer', model: 'Model', totalSeats: 'Seats', cabinConfig: 'Cabin config' }}
        />
        <FormField label="Manufacturer" error={errors.manufacturer?.message}>
          {(control) => <Input {...control} {...form.register('manufacturer')} />}
        </FormField>
        <FormField label="Model" error={errors.model?.message}>
          {(control) => <Input {...control} {...form.register('model')} />}
        </FormField>
        <FormField label="Seats" error={errors.totalSeats?.message}>
          {(control) => (
            <Input
              {...control}
              type="number"
              inputMode="numeric"
              min={1}
              className="max-w-40 tabular"
              {...form.register('totalSeats', { valueAsNumber: true })}
            />
          )}
        </FormField>
        <FormField label="Cabin config" description="Optional, e.g. J30 Y266" error={errors.cabinConfig?.message}>
          {(control) => <Input {...control} className="font-mono" {...form.register('cabinConfig')} />}
        </FormField>
      </form>
    </ResponsiveSheet>
  )
}

export function Component() {
  useDocumentTitle('Aircraft types · Ops')
  const { isAdmin } = useAuth()
  const { data = [], isPending, isError, error, refetch } = useAircraftTypes()
  const [adding, setAdding] = React.useState(false)
  const columns = React.useMemo<ColumnDef<AircraftTypeResponse, unknown>[]>(
    () => [
      { id: 'manufacturer', accessorKey: 'manufacturer', header: 'Manufacturer' },
      {
        id: 'model',
        accessorKey: 'model',
        header: 'Model',
        cell: ({ row }) => <span className="font-medium">{row.original.model}</span>,
      },
      { id: 'seats', accessorKey: 'totalSeats', header: 'Seats' },
      {
        id: 'config',
        accessorKey: 'cabinConfig',
        header: 'Cabin config',
        enableSorting: false,
        cell: ({ row }) => (
          <span className="font-mono">
            {row.original.cabinConfig ?? <span className="text-muted-foreground">—</span>}
          </span>
        ),
      },
    ],
    [],
  )
  return (
    <>
      <OpsPageHeader
        title="Fleet"
        description={`${data.length} aircraft types`}
        actions={
          isAdmin && (
            <Button onClick={() => setAdding(true)}>
              <Plus strokeWidth={1.75} aria-hidden />
              Add type
            </Button>
          )
        }
      />
      <SectionTabs label="Fleet" tabs={FLEET_TABS} />
      {isError ? (
        <ErrorState error={error} onRetry={() => void refetch()} />
      ) : (
        <DataTable
          label="Aircraft types"
          columns={columns}
          data={data}
          loading={isPending}
          getRowId={(t) => t.id}
          empty={<EmptyState className="border-0" title="No aircraft types yet" />}
        />
      )}
      {isAdmin && <AddTypeSheet open={adding} onOpenChange={setAdding} />}
    </>
  )
}
