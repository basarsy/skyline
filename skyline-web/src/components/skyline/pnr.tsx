import * as React from 'react'
import { Check, Copy } from 'lucide-react'
import { toast } from 'sonner'

import { Button } from '@/components/ui/button'
import { cn } from '@/lib/cn'
import { hasAmbiguousCharacter, normalizePnr, PNR_HINT, PNR_LENGTH } from '@/lib/pnr'

/** The PNR set huge in mono with letter-spacing, plus a copy button. */
export function PnrDisplay({
  pnr,
  size = 'hero',
  className,
}: {
  pnr: string
  size?: 'hero' | 'inline'
  className?: string
}) {
  const [copied, setCopied] = React.useState(false)

  async function copy() {
    try {
      await navigator.clipboard.writeText(pnr)
      setCopied(true)
      toast.success('Booking reference copied')
      setTimeout(() => setCopied(false), 1600)
    } catch {
      toast.error('Couldn’t copy. Select the reference and copy it manually.')
    }
  }

  if (size === 'inline') {
    return <span className={cn('font-mono font-medium tracking-[0.12em]', className)}>{pnr}</span>
  }

  return (
    <div className={cn('flex items-center gap-3', className)}>
      <span className="font-mono text-[2.75rem] leading-none font-semibold tracking-[0.18em] sm:text-[3.5rem]">
        {pnr}
      </span>
      <Button variant="secondary" size="icon" onClick={copy} aria-label="Copy booking reference">
        {copied ? <Check strokeWidth={1.75} aria-hidden /> : <Copy strokeWidth={1.75} aria-hidden />}
      </Button>
    </div>
  )
}

interface PnrInputProps extends Omit<React.ComponentProps<'input'>, 'value' | 'onChange'> {
  value: string
  onChange: (value: string) => void
  invalid?: boolean
}

/**
 * Six character cells in mono, backed by one real input (so typing, pasting and screen readers just work).
 * Uppercases, strips spaces, and hints when a character PNRs never use is typed.
 */
export const PnrInput = React.forwardRef<HTMLInputElement, PnrInputProps>(function PnrInput(
  { value, onChange, invalid, className, id, 'aria-describedby': describedBy, ...props },
  ref,
) {
  const hintId = React.useId()
  const [raw, setRaw] = React.useState('')
  const ambiguous = hasAmbiguousCharacter(raw)
  const cells = Array.from({ length: PNR_LENGTH }, (_, index) => value[index] ?? '')
  const [focused, setFocused] = React.useState(false)

  return (
    <div className={cn('flex flex-col gap-2', className)}>
      <div className="relative">
        <input
          ref={ref}
          id={id}
          value={value}
          inputMode="text"
          autoCapitalize="characters"
          autoComplete="off"
          spellCheck={false}
          maxLength={PNR_LENGTH + 4}
          aria-invalid={invalid || undefined}
          aria-describedby={cn(describedBy, ambiguous && hintId) || undefined}
          onFocus={() => setFocused(true)}
          onBlur={() => setFocused(false)}
          onChange={(event) => {
            setRaw(event.target.value)
            onChange(normalizePnr(event.target.value))
          }}
          className="peer absolute inset-0 z-10 h-full w-full cursor-text font-mono text-transparent caret-transparent opacity-0"
          {...props}
        />
        <div aria-hidden className="grid grid-cols-6 gap-2">
          {cells.map((char, index) => {
            const active =
              focused && (index === value.length || (index === PNR_LENGTH - 1 && value.length === PNR_LENGTH))
            return (
              <span
                key={index}
                className={cn(
                  'grid h-14 place-items-center rounded-input border bg-elevated font-mono text-2xl font-semibold transition-colors',
                  invalid ? 'border-destructive' : 'border-transparent',
                  active && 'border-ring',
                )}
              >
                {char || <span className="text-muted-foreground/50">·</span>}
              </span>
            )
          })}
        </div>
        {/* Visible focus ring around the cells, driven by the real input. */}
        <span className="pointer-events-none absolute -inset-1 rounded-[18px] outline-2 outline-offset-0 outline-ring outline-none peer-focus-visible:outline-solid" />
      </div>
      {ambiguous && (
        <p id={hintId} className="text-sm text-muted-foreground">
          {PNR_HINT}
        </p>
      )}
    </div>
  )
})
