import * as React from 'react'
import { zodResolver } from '@hookform/resolvers/zod'
import { differenceInCalendarDays } from 'date-fns'
import { AlertTriangle, Plus, Search } from 'lucide-react'
import { Controller, useForm } from 'react-hook-form'
import { z } from 'zod'

import { isApiError } from '@/api/http'
import type { CrewMemberResponse, CrewRole } from '@/api/types'
import { useAuth } from '@/auth/use-auth'
import { OpsPageHeader } from '@/components/layout/ops-shell'
import { Combobox } from '@/components/skyline/combobox'
import { DataTable, type ColumnDef } from '@/components/skyline/data-table'
import { applyServerErrors, FormErrorSummary, FormField } from '@/components/skyline/form'
import { DatePicker } from '@/components/skyline/pickers'
import { ResponsiveSheet } from '@/components/skyline/responsive-sheet'
import { EmptyState, ErrorState } from '@/components/skyline/states'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { formatLocalDate, localDateFromIso } from '@/lib/format'
import { useDebounced, useDocumentTitle } from '@/lib/hooks'
import { crewRoleLabel, CREW_ROLES } from '@/lib/labels'
import { useCreateCrewMember, useCrewList, useUserSearch } from '../queries'

/** Expired, or expiring within 30 days: a muted warning icon plus text, never color alone. */
function LicenseExpiry({ date }: { date: string }) {
  const expiry = localDateFromIso(date)
  const days = expiry ? differenceInCalendarDays(expiry, new Date()) : Infinity
  const label = days < 0 ? 'Expired' : days <= 30 ? `Expires in ${days} ${days === 1 ? 'day' : 'days'}` : null
  return (
    <span className="inline-flex items-center gap-2">
      {formatLocalDate(date)}
      {label && (
        <span className="inline-flex items-center gap-1 text-caption text-muted-foreground">
          <AlertTriangle className="size-3.5" strokeWidth={1.75} aria-hidden />
          {label}
        </span>
      )}
    </span>
  )
}

const schema = z.object({
  userId: z.string().min(1, 'Choose the linked account'),
  employeeNumber: z.string().trim().min(1, 'Enter the employee number').max(32, 'Use at most 32 characters'),
  firstName: z.string().trim().min(1, 'Enter the first name'),
  lastName: z.string().trim().min(1, 'Enter the last name'),
  role: z.enum(['CAPTAIN', 'FIRST_OFFICER', 'PURSER', 'FLIGHT_ATTENDANT'], { error: 'Choose a role' }),
  licenseNumber: z.string().trim().min(1, 'Enter the license number').max(64, 'Use at most 64 characters'),
  licenseExpiryDate: z.string().min(1, 'Choose the expiry date'),
})
type Values = z.infer<typeof schema>
const LABELS = {
  userId: 'Linked account',
  employeeNumber: 'Employee number',
  firstName: 'First name',
  lastName: 'Last name',
  role: 'Role',
  licenseNumber: 'License number',
  licenseExpiryDate: 'License expiry',
}

function UserPicker(
  props: { value: string; onChange: (value: string) => void } & Omit<
    React.ComponentProps<'button'>,
    'onChange' | 'value'
  >,
) {
  const { value, onChange, id, ...aria } = props
  const [query, setQuery] = React.useState('')
  const email = useDebounced(query.trim(), 250)
  const users = useUserSearch({ email: email || undefined, size: 20 })
  const options = (users.data?.content ?? []).map((user) => ({
    value: user.id,
    label: user.email,
    render: (
      <span className="flex flex-col">
        <span>{user.email}</span>
        <span className="font-mono text-caption text-muted-foreground">{user.id}</span>
      </span>
    ),
  }))
  return (
    <Combobox
      id={id}
      aria-invalid={aria['aria-invalid'] as true | undefined}
      aria-describedby={aria['aria-describedby']}
      value={value}
      onChange={(v) => onChange(v ?? '')}
      onSearch={setQuery}
      loading={users.isFetching}
      options={options}
      placeholder="Search by email"
      searchPlaceholder="Email"
      emptyText={isApiError(users.error) ? users.error.message : 'No accounts match'}
    />
  )
}

function AddCrewSheet({ open, onOpenChange }: { open: boolean; onOpenChange: (open: boolean) => void }) {
  const create = useCreateCrewMember()
  const [serverError, setServerError] = React.useState<string | null>(null)
  const form = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: {
      userId: '',
      employeeNumber: '',
      firstName: '',
      lastName: '',
      licenseNumber: '',
      licenseExpiryDate: '',
    },
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
      // A duplicate employee number (409) belongs under that field.
      if (isApiError(error) && error.status === 409 && /employee number/i.test(error.message)) {
        form.setError('employeeNumber', { type: 'server', message: error.message })
        return
      }
      setServerError(applyServerErrors(error, form.setError, Object.keys(LABELS)))
    }
  })
  return (
    <ResponsiveSheet
      open={open}
      onOpenChange={close}
      title="Add crew member"
      footer={
        <Button className="w-full" loading={isSubmitting} onClick={() => void onSubmit()}>
          Add crew member
        </Button>
      }
    >
      <form onSubmit={onSubmit} noValidate className="flex flex-col gap-5">
        <FormErrorSummary errors={errors} serverError={serverError} submitCount={submitCount} labels={LABELS} />
        <Controller
          control={form.control}
          name="userId"
          render={({ field, fieldState }) => (
            <FormField
              label="Linked account"
              description="The crew member’s Skyline login."
              error={fieldState.error?.message}
            >
              {(control) => <UserPicker {...control} value={field.value} onChange={field.onChange} />}
            </FormField>
          )}
        />
        <div className="grid grid-cols-2 gap-4">
          <FormField label="First name" error={errors.firstName?.message}>
            {(control) => <Input {...control} {...form.register('firstName')} />}
          </FormField>
          <FormField label="Last name" error={errors.lastName?.message}>
            {(control) => <Input {...control} {...form.register('lastName')} />}
          </FormField>
        </div>
        <div className="grid grid-cols-2 gap-4">
          <FormField label="Employee number" error={errors.employeeNumber?.message}>
            {(control) => (
              <Input
                {...control}
                className="font-mono uppercase"
                {...form.register('employeeNumber', { setValueAs: (v: string) => v.toUpperCase().trim() })}
              />
            )}
          </FormField>
          <Controller
            control={form.control}
            name="role"
            render={({ field, fieldState }) => (
              <FormField label="Role" error={fieldState.error?.message}>
                {(control) => (
                  <Select value={field.value ?? ''} onValueChange={field.onChange}>
                    <SelectTrigger {...control} className="w-full">
                      <SelectValue placeholder="Choose" />
                    </SelectTrigger>
                    <SelectContent>
                      {CREW_ROLES.map((role) => (
                        <SelectItem key={role} value={role} className="min-h-10">
                          {crewRoleLabel[role]}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                )}
              </FormField>
            )}
          />
        </div>
        <FormField label="License number" error={errors.licenseNumber?.message}>
          {(control) => <Input {...control} className="font-mono" {...form.register('licenseNumber')} />}
        </FormField>
        <Controller
          control={form.control}
          name="licenseExpiryDate"
          render={({ field, fieldState }) => (
            <FormField label="License expiry" error={fieldState.error?.message}>
              {(control) => (
                <DatePicker
                  {...control}
                  value={field.value}
                  onChange={field.onChange}
                  captionLayout="dropdown"
                  fromYear={new Date().getFullYear() - 1}
                  toYear={new Date().getFullYear() + 15}
                />
              )}
            </FormField>
          )}
        />
      </form>
    </ResponsiveSheet>
  )
}

export function Component() {
  useDocumentTitle('Crew · Ops')
  const { isAdmin } = useAuth()
  const [role, setRole] = React.useState<CrewRole | 'ALL'>('ALL')
  const [query, setQuery] = React.useState('')
  const q = useDebounced(query.trim(), 300)
  const {
    data = [],
    isPending,
    isError,
    error,
    refetch,
  } = useCrewList({ role: role === 'ALL' ? undefined : role, q: q || undefined })
  const [adding, setAdding] = React.useState(false)

  const columns = React.useMemo<ColumnDef<CrewMemberResponse, unknown>[]>(
    () => [
      {
        id: 'name',
        accessorFn: (c) => `${c.lastName} ${c.firstName}`,
        header: 'Name',
        cell: ({ row }) => (
          <span className="font-medium">
            {row.original.firstName} {row.original.lastName}
          </span>
        ),
      },
      {
        id: 'employee',
        accessorKey: 'employeeNumber',
        header: 'Employee no',
        cell: ({ row }) => <span className="font-mono">{row.original.employeeNumber}</span>,
      },
      { id: 'role', accessorKey: 'role', header: 'Role', cell: ({ row }) => crewRoleLabel[row.original.role] },
      {
        id: 'license',
        accessorKey: 'licenseNumber',
        header: 'License',
        enableSorting: false,
        cell: ({ row }) => <span className="font-mono">{row.original.licenseNumber}</span>,
      },
      {
        id: 'expiry',
        accessorKey: 'licenseExpiryDate',
        header: 'License expiry',
        cell: ({ row }) => <LicenseExpiry date={row.original.licenseExpiryDate} />,
      },
    ],
    [],
  )

  return (
    <>
      <OpsPageHeader
        title="Crew"
        description="Sorted by last name"
        actions={
          isAdmin && (
            <Button onClick={() => setAdding(true)}>
              <Plus strokeWidth={1.75} aria-hidden />
              Add crew member
            </Button>
          )
        }
      />
      <div className="mb-6 grid gap-3 sm:grid-cols-[1fr_220px]">
        <div className="flex flex-col gap-1.5">
          <label htmlFor="crew-search" className="micro-label">
            Search
          </label>
          <div className="relative">
            <Search
              className="pointer-events-none absolute top-1/2 left-4 size-4 -translate-y-1/2 text-muted-foreground"
              strokeWidth={1.75}
              aria-hidden
            />
            <Input
              id="crew-search"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Name or employee number"
              className="pl-11"
            />
          </div>
        </div>
        <div className="flex flex-col gap-1.5">
          <span id="crew-role" className="micro-label">
            Role
          </span>
          <Select value={role} onValueChange={(value) => setRole(value as CrewRole | 'ALL')}>
            <SelectTrigger aria-labelledby="crew-role" className="w-full">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="ALL" className="min-h-10">
                All roles
              </SelectItem>
              {CREW_ROLES.map((r) => (
                <SelectItem key={r} value={r} className="min-h-10">
                  {crewRoleLabel[r]}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
      </div>
      {isError ? (
        <ErrorState error={error} onRetry={() => void refetch()} />
      ) : (
        <DataTable
          label="Crew members"
          columns={columns}
          data={data}
          loading={isPending}
          getRowId={(c) => c.id}
          empty={
            <EmptyState
              className="border-0"
              title={q || role !== 'ALL' ? 'No crew members match' : 'No crew members yet'}
            />
          }
        />
      )}
      {isAdmin && <AddCrewSheet open={adding} onOpenChange={setAdding} />}
    </>
  )
}
