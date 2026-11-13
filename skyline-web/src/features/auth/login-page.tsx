import * as React from 'react'
import { zodResolver } from '@hookform/resolvers/zod'
import { useForm } from 'react-hook-form'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { z } from 'zod'

import { isApiError } from '@/api/http'
import { homeFor, login, safeNext } from '@/auth/session'
import { applyServerErrors, FormErrorSummary, FormField, PasswordInput } from '@/components/skyline/form'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { useDocumentTitle } from '@/lib/hooks'
import { AuthLayout } from './auth-layout'

const schema = z.object({
  email: z.email('Enter a valid email address'),
  password: z.string().min(1, 'Enter your password'),
})
type Values = z.infer<typeof schema>

export function Component() {
  useDocumentTitle('Log in')
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const next = safeNext(params.get('next'))
  const [serverError, setServerError] = React.useState<string | null>(null)

  const form = useForm<Values>({ resolver: zodResolver(schema), defaultValues: { email: '', password: '' } })
  const { errors, isSubmitting, submitCount } = form.formState

  const onSubmit = form.handleSubmit(async (values) => {
    setServerError(null)
    try {
      const user = await login(values)
      navigate(next ?? homeFor(user?.role), { replace: true })
    } catch (error) {
      // 401 is "Invalid email or password", shown under the form as the server says it.
      setServerError(
        isApiError(error) && error.status === 401
          ? error.message
          : applyServerErrors(error, form.setError, ['email', 'password']),
      )
    }
  })

  return (
    <AuthLayout
      title="Log in to Skyline"
      subtitle={
        <>
          New here?{' '}
          <Link
            to={`/register${next ? `?next=${encodeURIComponent(next)}` : ''}`}
            className="rounded font-medium text-sky-foreground underline underline-offset-4 focus-ring"
          >
            Create an account
          </Link>
        </>
      }
    >
      <form onSubmit={onSubmit} noValidate className="flex flex-col gap-5">
        <FormErrorSummary
          errors={errors}
          serverError={serverError}
          submitCount={submitCount}
          labels={{ email: 'Email', password: 'Password' }}
        />
        <FormField label="Email" error={errors.email?.message}>
          {(control) => (
            <Input {...control} type="email" autoComplete="email" inputMode="email" {...form.register('email')} />
          )}
        </FormField>
        <FormField label="Password" error={errors.password?.message}>
          {(control) => <PasswordInput {...control} autoComplete="current-password" {...form.register('password')} />}
        </FormField>
        <Button type="submit" size="lg" loading={isSubmitting} className="mt-1 w-full">
          Log in
        </Button>
      </form>
    </AuthLayout>
  )
}
