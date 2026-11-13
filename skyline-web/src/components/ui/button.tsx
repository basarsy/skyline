import * as React from 'react'
import { cva, type VariantProps } from 'class-variance-authority'
import { Loader2 } from 'lucide-react'
import { Slot } from 'radix-ui'

import { cn } from '@/lib/cn'

/**
 * Pill buttons. Every size keeps a 44px hit target (the small ones extend it with an invisible
 * pseudo-element), presses scale to 0.97, and transitions stay within 150-250ms.
 */
const buttonVariants = cva(
  [
    'relative inline-flex shrink-0 cursor-pointer items-center justify-center gap-2 rounded-full font-medium whitespace-nowrap select-none',
    'transition-[background-color,color,border-color,transform,opacity] duration-200 ease-out',
    'active:scale-[0.97] motion-reduce:active:scale-100',
    'focus-ring outline-none',
    'disabled:pointer-events-none disabled:opacity-40 aria-disabled:pointer-events-none aria-disabled:opacity-40',
    "[&_svg]:pointer-events-none [&_svg]:shrink-0 [&_svg:not([class*='size-'])]:size-[18px]",
  ].join(' '),
  {
    variants: {
      variant: {
        default: 'bg-primary text-primary-foreground shadow-card hover:bg-primary/90',
        secondary: 'bg-secondary text-secondary-foreground hover:bg-secondary/70',
        outline: 'border border-border bg-transparent text-foreground hover:bg-elevated',
        ghost: 'text-foreground hover:bg-elevated',
        /** Destructive actions stay monochrome: a hairline pill with destructive text, never a red fill. */
        destructive: 'border border-border bg-transparent text-destructive hover:bg-elevated',
        link: 'h-auto rounded-md px-0 text-foreground underline decoration-border underline-offset-4 hover:decoration-foreground',
      },
      size: {
        default: 'h-11 px-5 text-sm',
        lg: 'h-14 px-7 text-base',
        sm: "h-9 px-4 text-sm after:absolute after:inset-x-0 after:-inset-y-1 after:content-['']",
        icon: 'size-11',
        'icon-sm': "size-9 after:absolute after:-inset-1 after:content-['']",
      },
    },
    compoundVariants: [{ variant: 'link', className: 'h-auto px-0' }],
    defaultVariants: {
      variant: 'default',
      size: 'default',
    },
  },
)

type ButtonProps = React.ComponentProps<'button'> &
  VariantProps<typeof buttonVariants> & {
    asChild?: boolean
    /** Shows a spinner and disables the button, so an async action can't be submitted twice. */
    loading?: boolean
  }

function Button({
  className,
  variant = 'default',
  size = 'default',
  asChild = false,
  loading = false,
  disabled,
  children,
  ...props
}: ButtonProps) {
  const Comp = asChild ? Slot.Root : 'button'
  return (
    <Comp
      data-slot="button"
      data-variant={variant}
      data-size={size}
      className={cn(buttonVariants({ variant, size, className }))}
      disabled={asChild ? undefined : disabled || loading}
      aria-busy={loading || undefined}
      {...props}
    >
      {asChild ? (
        children
      ) : (
        <>
          {loading && <Loader2 className="animate-spin" strokeWidth={1.75} aria-hidden />}
          {children}
        </>
      )}
    </Comp>
  )
}

export { Button, buttonVariants }
export type { ButtonProps }
