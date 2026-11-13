import * as React from 'react'
import { ArrowLeftRight, Search } from 'lucide-react'
import { Controller, useForm, type Resolver } from 'react-hook-form'
import { useNavigate } from 'react-router'

import type { CabinClass } from '@/api/types'
import { FormErrorSummary, FormField } from '@/components/skyline/form'
import { AirportCombobox, DatePicker } from '@/components/skyline/pickers'
import { SegmentedControl } from '@/components/skyline/segmented-control'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/cn'
import { toUtcDay } from '@/lib/format'
import { cabinLabel, CABINS } from '@/lib/labels'

import type { SearchValues } from './search-schema'

export type { SearchValues }

/** Validation is loaded on demand: zod (and the schema) only download when the form is first validated. */
const lazyResolver: Resolver<SearchValues> = async (values, context, options) => {
  const { searchResolver } = await import('./search-schema')
  return searchResolver(values, context, options)
}

export function searchUrl(values: SearchValues) {
  const params = new URLSearchParams({ from: values.from, to: values.to, date: values.date, cabin: values.cabin })
  return `/flights?${params.toString()}`
}

/** From/To/date are validated together: all required, origin ≠ destination. */
export function SearchCard({
  initial,
  variant = 'hero',
  className,
}: {
  initial?: Partial<SearchValues>
  /** "hero" on Home; "bar" as the editable summary on the results page. */
  variant?: 'hero' | 'bar'
  className?: string
}) {
  const navigate = useNavigate()
  const [defaultValues] = React.useState<SearchValues>(() => ({
    from: initial?.from ?? '',
    to: initial?.to ?? '',
    date: initial?.date ?? toUtcDay(new Date(Date.now() + 86_400_000)),
    cabin: (initial?.cabin as CabinClass | undefined) ?? 'ECONOMY',
  }))
  const form = useForm<SearchValues>({ resolver: lazyResolver, defaultValues })
  const { errors, submitCount } = form.formState

  const onSubmit = form.handleSubmit((values) => navigate(searchUrl(values)))

  function swap() {
    const { from, to } = form.getValues()
    form.setValue('from', to, { shouldDirty: true })
    form.setValue('to', from, { shouldDirty: true, shouldValidate: submitCount > 0 })
  }

  const bar = variant === 'bar'
  return (
    <form
      onSubmit={onSubmit}
      noValidate
      role="search"
      aria-label="Search flights"
      className={cn(
        'flex flex-col gap-5 rounded-card border border-border bg-card',
        bar ? 'p-4 sm:p-5' : 'p-5 shadow-float sm:p-6',
        className,
      )}
    >
      {submitCount > 0 && (
        <FormErrorSummary errors={errors} submitCount={submitCount} labels={{ from: 'From', to: 'To', date: 'Date' }} />
      )}

      <div className="grid gap-4 lg:grid-cols-[1fr_auto_1fr_minmax(0,0.9fr)] lg:items-start">
        <Controller
          control={form.control}
          name="from"
          render={({ field, fieldState }) => (
            <FormField label={<span className="micro-label">From</span>} error={fieldState.error?.message}>
              {(control) => (
                <AirportCombobox
                  {...control}
                  value={field.value}
                  onChange={(v) => field.onChange(v ?? '')}
                  variant="pill"
                />
              )}
            </FormField>
          )}
        />
        <Button
          type="button"
          variant="secondary"
          size="icon"
          onClick={swap}
          aria-label="Swap origin and destination"
          className="-my-2 justify-self-center lg:my-0 lg:mt-7"
        >
          <ArrowLeftRight className="rotate-90 lg:rotate-0" strokeWidth={1.75} aria-hidden />
        </Button>
        <Controller
          control={form.control}
          name="to"
          render={({ field, fieldState }) => (
            <FormField label={<span className="micro-label">To</span>} error={fieldState.error?.message}>
              {(control) => (
                <AirportCombobox
                  {...control}
                  value={field.value}
                  onChange={(v) => field.onChange(v ?? '')}
                  variant="pill"
                />
              )}
            </FormField>
          )}
        />
        <Controller
          control={form.control}
          name="date"
          render={({ field, fieldState }) => (
            <FormField label={<span className="micro-label">Departs</span>} error={fieldState.error?.message}>
              {(control) => (
                <DatePicker
                  {...control}
                  value={field.value}
                  onChange={field.onChange}
                  disablePast
                  variant="pill"
                  note="Dates are in UTC"
                />
              )}
            </FormField>
          )}
        />
      </div>

      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <Controller
          control={form.control}
          name="cabin"
          render={({ field }) => (
            <SegmentedControl
              label="Cabin"
              value={field.value}
              onValueChange={field.onChange}
              options={CABINS.map((cabin) => ({ value: cabin, label: cabinLabel[cabin] }))}
              className="w-full sm:w-auto"
            />
          )}
        />
        <Button type="submit" size={bar ? 'default' : 'lg'} className={cn('w-full', bar ? 'sm:w-auto' : 'sm:w-56')}>
          <Search strokeWidth={1.75} aria-hidden />
          Search flights
        </Button>
      </div>
    </form>
  )
}
