import { zodResolver } from '@hookform/resolvers/zod'
import { Controller, useForm } from 'react-hook-form'
import { useLocation, useNavigate } from 'react-router'
import { z } from 'zod'

import { Page } from '@/components/layout/app-shell'
import { FormErrorSummary, FormField } from '@/components/skyline/form'
import { PnrInput } from '@/components/skyline/pnr'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { useDocumentTitle } from '@/lib/hooks'
import { isValidPnr, PNR_HINT } from '@/lib/pnr'

const schema = z.object({
  pnr: z.string().length(6, 'Booking references have 6 characters').refine(isValidPnr, PNR_HINT),
  lastName: z.string().trim().min(1, 'Enter the last name on the booking'),
})
type Values = z.infer<typeof schema>

/** State passed to /manage/:pnr. The last name stays out of the URL. */
export interface ManageState {
  lastName: string
  notFound?: boolean
}

export function Component() {
  useDocumentTitle('Manage booking')
  const navigate = useNavigate()
  const location = useLocation()
  const previous = location.state as Partial<ManageState & { pnr: string }> | null

  const form = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: { pnr: previous?.pnr ?? '', lastName: previous?.lastName ?? '' },
  })
  const { errors, submitCount } = form.formState

  const onSubmit = form.handleSubmit((values) => {
    navigate(`/manage/${values.pnr}`, { state: { lastName: values.lastName.trim() } satisfies ManageState })
  })

  return (
    <Page className="flex max-w-[560px] flex-col gap-8 py-12 sm:py-20">
      <div className="flex flex-col gap-2">
        <h1 className="text-h1 font-semibold">Manage booking</h1>
        <p className="text-muted-foreground">
          Find your trip with the booking reference and the passenger’s last name.
        </p>
      </div>
      <form
        onSubmit={onSubmit}
        noValidate
        className="flex flex-col gap-5 rounded-card border border-border bg-card p-5 sm:p-6"
      >
        {previous?.notFound && (
          <p role="alert" className="rounded-2xl bg-elevated p-4 text-sm">
            We couldn’t find a booking with those details. Check the reference and last name and try again.
          </p>
        )}
        <FormErrorSummary
          errors={errors}
          submitCount={submitCount}
          labels={{ pnr: 'Booking reference', lastName: 'Last name' }}
        />
        <Controller
          control={form.control}
          name="pnr"
          render={({ field, fieldState }) => (
            <FormField
              label="Booking reference"
              description="6 characters, e.g. K7Q2MX"
              error={fieldState.error?.message}
            >
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
        <Button type="submit" size="lg" className="w-full">
          Find booking
        </Button>
      </form>
    </Page>
  )
}
