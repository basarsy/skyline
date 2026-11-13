import * as React from 'react'
import { zodResolver } from '@hookform/resolvers/zod'
import { useForm, useWatch } from 'react-hook-form'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { z } from 'zod'

import { isApiError } from '@/api/http'
import { homeFor, register, safeNext } from '@/auth/session'
import { applyServerErrors, FormErrorSummary, FormField, PasswordInput } from '@/components/skyline/form'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { cn } from '@/lib/cn'
import { useDocumentTitle } from '@/lib/hooks'
import { AuthLayout } from './auth-layout'

const schema = z.object({
  email: z.email('Enter a valid email address'),
  password: z.string().min(8, 'Use at least 8 characters').max(128, 'Use at most 128 characters'),
})
type Values = z.infer<typeof schema>

/** 0..4. The only server rule is 8–128 characters; the meter just nudges towards longer, varied passwords. */
export function passwordStrength(password: string) {
  if (password.length < 8) return 0
  let score = 1
  if (password.length >= 12) score += 1
  if (/[a-z]/.test(password) && /[A-Z]/.test(password)) score += 1
  if (/\d/.test(password) && /[^A-Za-z0-9]/.test(password)) score += 1
  return Math.min(score, 4)
}

const STRENGTH_LABEL = ['Too short', 'Okay', 'Good', 'Strong', 'Very strong']

function StrengthMeter({ password, id }: { password: string; id: string }) {
  const score = passwordStrength(password)
  return (
    <div className="flex flex-col gap-2" id={id}>
      <div className="grid grid-cols-4 gap-1.5" aria-hidden>
        {[1, 2, 3, 4].map((step) => (
          <span
            key={step}
            className={cn(
              'h-1 rounded-full transition-colors duration-200',
              score >= step ? 'bg-primary' : 'bg-elevated',
            )}
          />
        ))}
      </div>
      <p className="text-caption text-muted-foreground" aria-live="polite">
        {password ? `Strength: ${STRENGTH_LABEL[score]}` : '8 to 128 characters'}
      </p>
    </div>
  )
}

export function Component() {
  useDocumentTitle('Create an account')
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const next = safeNext(params.get('next'))
  const [serverError, setServerError] = React.useState<string | null>(null)
  const [emailTaken, setEmailTaken] = React.useState(false)
  const meterId = React.useId()

  const form = useForm<Values>({ resolver: zodResolver(schema), defaultValues: { email: '', password: '' } })
  const { errors, isSubmitting, submitCount } = form.formState
  const password = useWatch({ control: form.control, name: 'password' })

  const onSubmit = form.handleSubmit(async (values) => {
    setServerError(null)
    setEmailTaken(false)
    try {
      // Registering doesn't log in, so this logs in right after with the same credentials.
      const user = await register(values)
      navigate(next ?? homeFor(user?.role), { replace: true })
    } catch (error) {
      if (isApiError(error) && error.status === 409) setEmailTaken(true)
      setServerError(applyServerErrors(error, form.setError, ['email', 'password']))
    }
  })

  return (
    <AuthLayout
      title="Create your account"
      subtitle={
        <>
          Already have one?{' '}
          <Link
            to={`/login${next ? `?next=${encodeURIComponent(next)}` : ''}`}
            className="rounded font-medium text-foreground underline underline-offset-4 focus-ring"
          >
            Log in
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
        {emailTaken && (
          <p className="-mt-2 text-sm">
            <Link to="/login" className="rounded font-medium underline underline-offset-4 focus-ring">
              Log in with this email instead
            </Link>
          </p>
        )}
        <FormField label="Email" error={errors.email?.message}>
          {(control) => (
            <Input {...control} type="email" autoComplete="email" inputMode="email" {...form.register('email')} />
          )}
        </FormField>
        <FormField label="Password" error={errors.password?.message}>
          {(control) => (
            <PasswordInput
              {...control}
              aria-describedby={cn(control['aria-describedby'], meterId)}
              autoComplete="new-password"
              {...form.register('password')}
            />
          )}
        </FormField>
        <StrengthMeter password={password ?? ''} id={meterId} />
        <Button type="submit" size="lg" loading={isSubmitting} className="mt-1 w-full">
          Create account
        </Button>
      </form>
    </AuthLayout>
  )
}
