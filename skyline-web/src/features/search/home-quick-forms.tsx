import { zodResolver } from '@hookform/resolvers/zod'
import { Controller, useForm } from 'react-hook-form'
import { useNavigate } from 'react-router'
import { z } from 'zod'

import { FormErrorSummary, FormField } from '@/components/skyline/form'
import { PnrInput } from '@/components/skyline/pnr'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { DOCUMENT_ID } from '@/lib/document-id'
import { isValidPnr, PNR_HINT } from '@/lib/pnr'
import type { ManageState } from '@/features/manage/manage-page'

/*
 * The "Manage booking" and "Check-in" tabs of the home booking box. They load on first use, so zod and the
 * PNR input stay out of the home page's initial bundle. Both lead to the same pages as the full forms.
 */

const pnr = z.string().length(6, 'Booking references have 6 characters').refine(isValidPnr, PNR_HINT)
const manageSchema = z.object({ pnr, lastName: z.string().trim().min(1, 'Enter the last name on the booking') })
const checkinSchema = z.object({ pnr })

export function ManageQuickForm() {
  const navigate = useNavigate()
  const form = useForm<z.infer<typeof manageSchema>>({
    resolver: zodResolver(manageSchema),
    defaultValues: { pnr: '', lastName: '' },
  })
  const { errors, submitCount } = form.formState
  const onSubmit = form.handleSubmit((values) =>
    navigate(`/manage/${values.pnr}`, {
      state: { lastName: values.lastName.trim(), documentId: DOCUMENT_ID } satisfies ManageState,
    }),
  )

  return (
    <form onSubmit={onSubmit} noValidate aria-label="Find a booking" className="flex flex-col gap-4">
      <FormErrorSummary
        errors={errors}
        submitCount={submitCount}
        labels={{ pnr: 'Booking reference', lastName: 'Last name' }}
      />
      <div className="grid gap-4 lg:grid-cols-[auto_1fr_auto] lg:items-start">
        <Controller
          control={form.control}
          name="pnr"
          render={({ field, fieldState }) => (
            <FormField label="Booking reference" error={fieldState.error?.message}>
              {(control) => (
                <PnrInput
                  {...control}
                  ref={field.ref}
                  name={field.name}
                  value={field.value}
                  onChange={field.onChange}
                  onBlur={field.onBlur}
                  invalid={!!fieldState.error}
                />
              )}
            </FormField>
          )}
        />
        <FormField label="Last name" error={errors.lastName?.message}>
          {(control) => <Input {...control} autoComplete="family-name" {...form.register('lastName')} />}
        </FormField>
        <Button type="submit" size="lg" className="w-full lg:mt-7 lg:w-48">
          Find booking
        </Button>
      </div>
    </form>
  )
}

export function CheckinQuickForm() {
  const navigate = useNavigate()
  const form = useForm<z.infer<typeof checkinSchema>>({
    resolver: zodResolver(checkinSchema),
    defaultValues: { pnr: '' },
  })
  const { errors, submitCount } = form.formState
  const onSubmit = form.handleSubmit((values) => navigate(`/checkin/${values.pnr}`))

  return (
    <form onSubmit={onSubmit} noValidate aria-label="Check in" className="flex flex-col gap-4">
      <FormErrorSummary errors={errors} submitCount={submitCount} labels={{ pnr: 'Booking reference' }} />
      <div className="grid gap-4 lg:grid-cols-[auto_1fr] lg:items-start">
        <Controller
          control={form.control}
          name="pnr"
          render={({ field, fieldState }) => (
            <FormField label="Booking reference" error={fieldState.error?.message}>
              {(control) => (
                <PnrInput
                  {...control}
                  ref={field.ref}
                  name={field.name}
                  value={field.value}
                  onChange={field.onChange}
                  onBlur={field.onBlur}
                  invalid={!!fieldState.error}
                />
              )}
            </FormField>
          )}
        />
        <div className="flex flex-col gap-2 lg:mt-7 lg:flex-row lg:items-center lg:justify-between lg:gap-6">
          <p className="text-sm text-muted-foreground">
            Opens 48 hours and closes 1 hour before departure. Your seat is assigned at check-in.
          </p>
          <Button type="submit" size="lg" className="w-full shrink-0 lg:w-48">
            Check in
          </Button>
        </div>
      </div>
    </form>
  )
}
