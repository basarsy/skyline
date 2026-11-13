import * as React from 'react'
import { zodResolver } from '@hookform/resolvers/zod'
import { Plus } from 'lucide-react'
import { Controller, useForm } from 'react-hook-form'
import { z } from 'zod'

import type { AirportResponse } from '@/api/types'
import { useAuth } from '@/auth/use-auth'
import { OpsPageHeader } from '@/components/layout/ops-shell'
import { DataTable, type ColumnDef } from '@/components/skyline/data-table'
import { applyServerErrors, FormErrorSummary, FormField } from '@/components/skyline/form'
import { CountryCombobox, TimezoneCombobox, useAirports } from '@/components/skyline/pickers'
import { ResponsiveSheet } from '@/components/skyline/responsive-sheet'
import { EmptyState, ErrorState } from '@/components/skyline/states'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { useDocumentTitle } from '@/lib/hooks'
import { useCreateAirport } from '../queries'
import { NETWORK_TABS, SectionTabs } from '../section-tabs'

const upper = (v: string) => v.toUpperCase().trim()
const schema = z.object({
  iataCode: z.string().regex(/^[A-Z]{3}$/, '3 letters, e.g. IST'),
  icaoCode: z.string().regex(/^[A-Z]{4}$/, '4 letters, e.g. LTFM'),
  name: z.string().trim().min(1, 'Enter the airport name'),
  city: z.string().trim().min(1, 'Enter the city'),
  country: z.string().min(1, 'Choose the country'),
  timezone: z.string().min(1, 'Choose the time zone'),
})
type Values = z.infer<typeof schema>
const LABELS = {
  iataCode: 'IATA code',
  icaoCode: 'ICAO code',
  name: 'Name',
  city: 'City',
  country: 'Country',
  timezone: 'Time zone',
}

function AddAirportSheet({ open, onOpenChange }: { open: boolean; onOpenChange: (open: boolean) => void }) {
  const create = useCreateAirport()
  const [serverError, setServerError] = React.useState<string | null>(null)
  const form = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: { iataCode: '', icaoCode: '', name: '', city: '', country: '', timezone: '' },
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
      setServerError(applyServerErrors(error, form.setError, Object.keys(LABELS)))
    }
  })
  return (
    <ResponsiveSheet
      open={open}
      onOpenChange={close}
      title="Add airport"
      footer={
        <Button className="w-full" loading={isSubmitting} onClick={() => void onSubmit()}>
          Add airport
        </Button>
      }
    >
      <form onSubmit={onSubmit} noValidate className="flex flex-col gap-5">
        <FormErrorSummary errors={errors} serverError={serverError} submitCount={submitCount} labels={LABELS} />
        <div className="grid grid-cols-2 gap-4">
          <FormField label="IATA code" error={errors.iataCode?.message}>
            {(control) => (
              <Input
                {...control}
                maxLength={3}
                className="font-mono uppercase"
                {...form.register('iataCode', { setValueAs: upper })}
              />
            )}
          </FormField>
          <FormField label="ICAO code" error={errors.icaoCode?.message}>
            {(control) => (
              <Input
                {...control}
                maxLength={4}
                className="font-mono uppercase"
                {...form.register('icaoCode', { setValueAs: upper })}
              />
            )}
          </FormField>
        </div>
        <FormField label="Name" error={errors.name?.message}>
          {(control) => <Input {...control} {...form.register('name')} />}
        </FormField>
        <FormField label="City" error={errors.city?.message}>
          {(control) => <Input {...control} {...form.register('city')} />}
        </FormField>
        <Controller
          control={form.control}
          name="country"
          render={({ field, fieldState }) => (
            <FormField label="Country" error={fieldState.error?.message}>
              {(control) => (
                <CountryCombobox {...control} value={field.value} onChange={(v) => field.onChange(v ?? '')} />
              )}
            </FormField>
          )}
        />
        <Controller
          control={form.control}
          name="timezone"
          render={({ field, fieldState }) => (
            <FormField
              label="Time zone"
              description="IANA zone; flight times at this airport are shown in it."
              error={fieldState.error?.message}
            >
              {(control) => (
                <TimezoneCombobox {...control} value={field.value} onChange={(v) => field.onChange(v ?? '')} />
              )}
            </FormField>
          )}
        />
      </form>
    </ResponsiveSheet>
  )
}

export function Component() {
  useDocumentTitle('Airports · Ops')
  const { isAdmin } = useAuth()
  const { data = [], isPending, isError, error, refetch } = useAirports()
  const [adding, setAdding] = React.useState(false)
  const columns = React.useMemo<ColumnDef<AirportResponse, unknown>[]>(
    () => [
      {
        id: 'iata',
        accessorKey: 'iataCode',
        header: 'IATA',
        cell: ({ row }) => <span className="font-mono font-semibold">{row.original.iataCode}</span>,
      },
      {
        id: 'icao',
        accessorKey: 'icaoCode',
        header: 'ICAO',
        cell: ({ row }) => <span className="font-mono">{row.original.icaoCode}</span>,
      },
      { id: 'name', accessorKey: 'name', header: 'Name' },
      { id: 'city', accessorKey: 'city', header: 'City' },
      { id: 'country', accessorKey: 'country', header: 'Country' },
      {
        id: 'timezone',
        accessorKey: 'timezone',
        header: 'Time zone',
        cell: ({ row }) => <span className="font-mono text-sm">{row.original.timezone}</span>,
      },
    ],
    [],
  )
  return (
    <>
      <OpsPageHeader
        title="Network"
        description={`${data.length} airports`}
        actions={
          isAdmin && (
            <Button onClick={() => setAdding(true)}>
              <Plus strokeWidth={1.75} aria-hidden />
              Add airport
            </Button>
          )
        }
      />
      <SectionTabs label="Network" tabs={NETWORK_TABS} />
      {isError ? (
        <ErrorState error={error} onRetry={() => void refetch()} />
      ) : (
        <DataTable
          label="Airports"
          columns={columns}
          data={data}
          loading={isPending}
          getRowId={(a) => a.id}
          empty={<EmptyState className="border-0" title="No airports yet" />}
        />
      )}
      {isAdmin && <AddAirportSheet open={adding} onOpenChange={setAdding} />}
    </>
  )
}
