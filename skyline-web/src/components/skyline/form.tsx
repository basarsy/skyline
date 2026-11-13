import * as React from 'react'
import { AlertCircle, Eye, EyeOff } from 'lucide-react'
import type { FieldErrors, FieldValues, Path, UseFormSetError } from 'react-hook-form'

import { isApiError } from '@/api/http'
import { Field, FieldDescription, FieldError, FieldLabel } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { cn } from '@/lib/cn'

export interface ControlProps {
  id: string
  'aria-invalid': true | undefined
  'aria-describedby': string | undefined
}

interface FormFieldProps {
  label: React.ReactNode
  /** Helper text under the label, e.g. "As shown on passport". */
  description?: React.ReactNode
  error?: string
  className?: string
  /** Visually hidden label (still announced). Use only when a visible label exists nearby. */
  hideLabel?: boolean
  children: (control: ControlProps) => React.ReactNode
}

/**
 * A labelled control: the label is always visible (never a placeholder), the error sits under the field
 * and both description and error are linked with aria-describedby.
 */
export function FormField({ label, description, error, className, hideLabel, children }: FormFieldProps) {
  const id = React.useId()
  const descriptionId = `${id}-description`
  const errorId = `${id}-error`
  const describedBy = cn(description && descriptionId, error && errorId) || undefined

  return (
    <Field data-invalid={!!error || undefined} className={className}>
      <FieldLabel htmlFor={id} className={cn('text-sm font-medium', hideLabel && 'sr-only')}>
        {label}
      </FieldLabel>
      {description && <FieldDescription id={descriptionId}>{description}</FieldDescription>}
      {children({ id, 'aria-invalid': error ? true : undefined, 'aria-describedby': describedBy })}
      {error && <FieldError id={errorId}>{error}</FieldError>}
    </Field>
  )
}

/** Password input with a show/hide toggle. */
export const PasswordInput = React.forwardRef<HTMLInputElement, React.ComponentProps<'input'>>(function PasswordInput(
  { className, ...props },
  ref,
) {
  const [visible, setVisible] = React.useState(false)
  return (
    <div className="relative">
      <Input ref={ref} type={visible ? 'text' : 'password'} className={cn('pr-14', className)} {...props} />
      <button
        type="button"
        onClick={() => setVisible((v) => !v)}
        aria-label={visible ? 'Hide password' : 'Show password'}
        aria-pressed={visible}
        className="absolute top-1/2 right-1 grid size-11 -translate-y-1/2 cursor-pointer place-items-center rounded-full text-muted-foreground focus-ring transition-colors hover:text-foreground"
      >
        {visible ? (
          <EyeOff className="size-[18px]" strokeWidth={1.75} aria-hidden />
        ) : (
          <Eye className="size-[18px]" strokeWidth={1.75} aria-hidden />
        )}
      </button>
    </div>
  )
})

/** Flattens RHF errors into [path, message] pairs (nested paths dotted, as the server sends them). */
export function flattenErrors(errors: FieldErrors, prefix = ''): [string, string][] {
  const result: [string, string][] = []
  for (const [key, value] of Object.entries(errors)) {
    if (!value) continue
    const path = prefix ? `${prefix}.${key}` : key
    if (typeof value === 'object' && 'message' in value && typeof value.message === 'string') {
      result.push([path, value.message])
    } else if (typeof value === 'object') {
      result.push(...flattenErrors(value as FieldErrors, path))
    }
  }
  return result
}

/**
 * Form-level summary. Takes focus when a submit fails so screen-reader and keyboard users land on it;
 * each entry links to its field.
 */
export function FormErrorSummary({
  errors,
  serverError,
  labels = {},
  submitCount,
  className,
}: {
  errors: FieldErrors
  serverError?: string | null
  labels?: Record<string, string>
  /** Pass formState.submitCount so the summary refocuses on every failed attempt. */
  submitCount: number
  className?: string
}) {
  const ref = React.useRef<HTMLDivElement>(null)
  const entries = flattenErrors(errors)
  const visible = entries.length > 0 || !!serverError

  React.useEffect(() => {
    if (visible && submitCount > 0) ref.current?.focus()
  }, [visible, submitCount, serverError])

  if (!visible) return null

  return (
    <div
      ref={ref}
      tabIndex={-1}
      role="alert"
      className={cn(
        'flex gap-3 rounded-2xl border border-border bg-elevated p-4 text-sm focus-ring outline-none',
        className,
      )}
    >
      <AlertCircle className="mt-0.5 size-[18px] shrink-0 text-destructive" strokeWidth={1.75} aria-hidden />
      <div className="flex flex-col gap-1">
        {serverError && <p className="font-medium">{serverError}</p>}
        {entries.length > 0 && (
          <>
            {!serverError && <p className="font-medium">Please check the highlighted fields.</p>}
            <ul className="flex flex-col gap-0.5 text-muted-foreground">
              {entries.map(([path, message]) => (
                <li key={path}>
                  <button
                    type="button"
                    className="cursor-pointer text-left underline decoration-border underline-offset-4 hover:decoration-foreground"
                    onClick={() => document.querySelector<HTMLElement>(`[name="${path}"]`)?.focus()}
                  >
                    {labels[path] ?? path}: {message}
                  </button>
                </li>
              ))}
            </ul>
          </>
        )}
      </div>
    </div>
  )
}

/**
 * Maps a server validation error onto the form's fields. Returns the message to show at form level
 * (null when every error landed on a field).
 */
export function applyServerErrors<T extends FieldValues>(
  error: unknown,
  setError: UseFormSetError<T>,
  knownFields: readonly string[],
): string | null {
  if (!isApiError(error)) return error instanceof Error ? error.message : 'Something went wrong. Please try again.'
  let unmatched = false
  for (const [field, message] of Object.entries(error.fieldErrors)) {
    if (knownFields.includes(field)) setError(field as Path<T>, { type: 'server', message })
    else unmatched = true
  }
  if (error.hasFieldErrors && !unmatched) return null
  return error.message
}
