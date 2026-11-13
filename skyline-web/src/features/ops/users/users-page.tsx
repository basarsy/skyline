import * as React from 'react'
import { zodResolver } from '@hookform/resolvers/zod'
import { Check, ChevronLeft, ChevronRight, Copy, KeyRound, Plus, Search } from 'lucide-react'
import { Controller, useForm } from 'react-hook-form'
import { toast } from 'sonner'
import { z } from 'zod'

import { isApiError } from '@/api/http'
import type { UserResponse, UserRole } from '@/api/types'
import { OpsPageHeader } from '@/components/layout/ops-shell'
import { DataTable, type ColumnDef } from '@/components/skyline/data-table'
import { applyServerErrors, FormErrorSummary, FormField, PasswordInput } from '@/components/skyline/form'
import { ResponsiveSheet } from '@/components/skyline/responsive-sheet'
import { EmptyState, ErrorState } from '@/components/skyline/states'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { useDebounced, useDocumentTitle } from '@/lib/hooks'
import { userRoleLabel } from '@/lib/labels'
import { useCreateUser, useUserSearch } from '../queries'

const PAGE_SIZE = 20

/** A 20-character password from crypto.getRandomValues, without look-alike characters. */
export function generatePassword(length = 20) {
  const alphabet = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%&*?'
  const values = crypto.getRandomValues(new Uint32Array(length))
  return Array.from(values, (v) => alphabet[v % alphabet.length]).join('')
}

function CopyButton({ value, label }: { value: string; label: string }) {
  const [copied, setCopied] = React.useState(false)
  return (
    <Button
      type="button"
      variant="ghost"
      size="icon-sm"
      aria-label={label}
      onClick={async (event) => {
        event.stopPropagation()
        try {
          await navigator.clipboard.writeText(value)
          setCopied(true)
          setTimeout(() => setCopied(false), 1500)
        } catch {
          toast.error('Couldn’t copy to the clipboard')
        }
      }}
    >
      {copied ? <Check strokeWidth={1.75} aria-hidden /> : <Copy strokeWidth={1.75} aria-hidden />}
    </Button>
  )
}

const schema = z.object({
  email: z.email('Enter a valid email address'),
  role: z.enum(['STAFF', 'ADMIN']),
  password: z.string().min(12, 'Use at least 12 characters').max(128, 'Use at most 128 characters'),
})
type Values = z.infer<typeof schema>

function CreateUserSheet({ open, onOpenChange }: { open: boolean; onOpenChange: (open: boolean) => void }) {
  const create = useCreateUser()
  const [serverError, setServerError] = React.useState<string | null>(null)
  const [created, setCreated] = React.useState<{ user: UserResponse; password: string } | null>(null)
  const form = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: { email: '', role: 'STAFF', password: '' },
  })
  const { errors, submitCount, isSubmitting } = form.formState

  const close = (next: boolean) => {
    onOpenChange(next)
    if (!next) {
      form.reset()
      setServerError(null)
      setCreated(null)
    }
  }
  const onSubmit = form.handleSubmit(async (values) => {
    setServerError(null)
    try {
      const result = await create.mutateAsync(values)
      setCreated({ user: result.data, password: values.password })
      toast.success(result.message ?? 'Account created')
    } catch (error) {
      if (isApiError(error) && error.status === 409) {
        form.setError('email', { type: 'server', message: error.message })
        return
      }
      setServerError(applyServerErrors(error, form.setError, ['email', 'role', 'password']))
    }
  })

  return (
    <ResponsiveSheet
      open={open}
      onOpenChange={close}
      title={created ? 'Account created' : 'Create staff account'}
      footer={
        created ? (
          <Button className="w-full" onClick={() => close(false)}>
            Done
          </Button>
        ) : (
          <Button className="w-full" loading={isSubmitting} onClick={() => void onSubmit()}>
            Create account
          </Button>
        )
      }
    >
      {created ? (
        <div className="flex flex-col gap-5">
          <p className="text-sm text-muted-foreground">
            This is the only time the password is shown. Share these credentials securely, e.g. in a password manager,
            not by email or chat.
          </p>
          <dl className="flex flex-col gap-4 rounded-2xl bg-elevated p-4">
            {[
              ['Email', created.user.email],
              ['Password', created.password],
            ].map(([label, value]) => (
              <div key={label} className="flex items-center justify-between gap-3">
                <div className="min-w-0">
                  <dt className="micro-label">{label}</dt>
                  <dd className="truncate font-mono text-sm">{value}</dd>
                </div>
                <CopyButton value={value!} label={`Copy ${label!.toLowerCase()}`} />
              </div>
            ))}
            <div>
              <dt className="micro-label">Role</dt>
              <dd className="text-sm">{userRoleLabel[created.user.role]}</dd>
            </div>
          </dl>
        </div>
      ) : (
        <form onSubmit={onSubmit} noValidate className="flex flex-col gap-5">
          <FormErrorSummary
            errors={errors}
            serverError={serverError}
            submitCount={submitCount}
            labels={{ email: 'Email', role: 'Role', password: 'Password' }}
          />
          <FormField label="Email" error={errors.email?.message}>
            {(control) => <Input {...control} type="email" autoComplete="off" {...form.register('email')} />}
          </FormField>
          <Controller
            control={form.control}
            name="role"
            render={({ field }) => (
              <FormField label="Role">
                {(control) => (
                  <Select value={field.value} onValueChange={field.onChange}>
                    <SelectTrigger {...control} className="w-full">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="STAFF" className="min-h-10">
                        Staff
                      </SelectItem>
                      <SelectItem value="ADMIN" className="min-h-10">
                        Admin
                      </SelectItem>
                    </SelectContent>
                  </Select>
                )}
              </FormField>
            )}
          />
          <FormField label="Password" description="At least 12 characters." error={errors.password?.message}>
            {(control) => (
              <div className="flex flex-col gap-2">
                <PasswordInput {...control} autoComplete="new-password" {...form.register('password')} />
                <div className="flex gap-2">
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    onClick={() => form.setValue('password', generatePassword(), { shouldValidate: submitCount > 0 })}
                  >
                    <KeyRound strokeWidth={1.75} aria-hidden />
                    Generate password
                  </Button>
                  <Button
                    type="button"
                    variant="ghost"
                    size="sm"
                    onClick={async () => {
                      const value = form.getValues('password')
                      if (value)
                        await navigator.clipboard.writeText(value).then(
                          () => toast.success('Password copied'),
                          () => toast.error('Couldn’t copy'),
                        )
                    }}
                  >
                    <Copy strokeWidth={1.75} aria-hidden />
                    Copy
                  </Button>
                </div>
              </div>
            )}
          </FormField>
        </form>
      )}
    </ResponsiveSheet>
  )
}

export function Component() {
  useDocumentTitle('Users · Ops')
  const [query, setQuery] = React.useState('')
  const email = useDebounced(query.trim(), 300)
  const [role, setRole] = React.useState<UserRole | 'ALL'>('ALL')
  const filterKey = `${email}|${role}`
  const [paging, setPaging] = React.useState({ key: filterKey, page: 0 })
  // A new search starts at the first page.
  const page = paging.key === filterKey ? paging.page : 0
  const setPage = (update: (p: number) => number) => setPaging({ key: filterKey, page: update(page) })
  const [creating, setCreating] = React.useState(false)
  const users = useUserSearch({
    email: email || undefined,
    role: role === 'ALL' ? undefined : role,
    page,
    size: PAGE_SIZE,
    sort: 'email,asc',
  })
  const data = users.data

  const columns = React.useMemo<ColumnDef<UserResponse, unknown>[]>(
    () => [
      {
        id: 'email',
        accessorKey: 'email',
        header: 'Email',
        enableSorting: false,
        cell: ({ row }) => <span className="font-medium">{row.original.email}</span>,
      },
      {
        id: 'role',
        accessorKey: 'role',
        header: 'Role',
        enableSorting: false,
        cell: ({ row }) => (
          <Badge variant={row.original.role === 'PASSENGER' ? 'default' : 'outline'}>
            {userRoleLabel[row.original.role]}
          </Badge>
        ),
      },
      {
        id: 'id',
        accessorKey: 'id',
        header: 'Id',
        enableSorting: false,
        cell: ({ row }) => (
          <span className="inline-flex items-center gap-1">
            <span className="font-mono text-caption text-muted-foreground">{row.original.id}</span>
            <CopyButton value={row.original.id} label={`Copy id of ${row.original.email}`} />
          </span>
        ),
      },
    ],
    [],
  )

  return (
    <>
      <OpsPageHeader
        title="Users"
        description="Find accounts and create staff or admin accounts."
        actions={
          <Button onClick={() => setCreating(true)}>
            <Plus strokeWidth={1.75} aria-hidden />
            Create staff account
          </Button>
        }
      />
      <div className="mb-6 grid gap-3 sm:grid-cols-[1fr_220px]">
        <div className="flex flex-col gap-1.5">
          <label htmlFor="user-search" className="micro-label">
            Email
          </label>
          <div className="relative">
            <Search
              className="pointer-events-none absolute top-1/2 left-4 size-4 -translate-y-1/2 text-muted-foreground"
              strokeWidth={1.75}
              aria-hidden
            />
            <Input
              id="user-search"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Search by email"
              className="pl-11"
            />
          </div>
        </div>
        <div className="flex flex-col gap-1.5">
          <span id="user-role" className="micro-label">
            Role
          </span>
          <Select value={role} onValueChange={(value) => setRole(value as UserRole | 'ALL')}>
            <SelectTrigger aria-labelledby="user-role" className="w-full">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="ALL" className="min-h-10">
                All roles
              </SelectItem>
              {(['PASSENGER', 'STAFF', 'ADMIN'] as const).map((r) => (
                <SelectItem key={r} value={r} className="min-h-10">
                  {userRoleLabel[r]}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
      </div>
      {users.isError ? (
        <ErrorState error={users.error} onRetry={() => void users.refetch()} />
      ) : (
        <>
          <DataTable
            label="Users"
            columns={columns}
            data={data?.content ?? []}
            loading={users.isPending}
            getRowId={(u) => u.id}
            empty={<EmptyState className="border-0" title="No accounts match" />}
          />
          {data && data.totalPages > 1 && (
            <nav aria-label="Pages" className="mt-4 flex items-center justify-end gap-3 text-sm">
              <span className="text-muted-foreground tabular">
                Page {data.number + 1} of {data.totalPages} · {data.totalElements} accounts
              </span>
              <Button
                variant="outline"
                size="icon"
                aria-label="Previous page"
                disabled={data.first}
                onClick={() => setPage((p) => p - 1)}
              >
                <ChevronLeft strokeWidth={1.75} aria-hidden />
              </Button>
              <Button
                variant="outline"
                size="icon"
                aria-label="Next page"
                disabled={data.last}
                onClick={() => setPage((p) => p + 1)}
              >
                <ChevronRight strokeWidth={1.75} aria-hidden />
              </Button>
            </nav>
          )}
        </>
      )}
      <CreateUserSheet open={creating} onOpenChange={setCreating} />
    </>
  )
}
