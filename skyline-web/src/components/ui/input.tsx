import * as React from 'react'

import { cn } from '@/lib/cn'

/** Inputs sit on the elevated surface with a hairline, 14px radius and a 48px height. */
const inputClassName = cn(
  'h-12 w-full min-w-0 rounded-input border border-transparent bg-elevated px-4 text-base text-foreground',
  'focus-ring transition-[border-color,background-color] duration-200 outline-none',
  'placeholder:text-muted-foreground hover:border-border focus-visible:border-ring',
  'disabled:cursor-not-allowed disabled:opacity-50',
  'aria-invalid:border-destructive',
  'file:inline-flex file:h-7 file:border-0 file:bg-transparent file:text-sm file:font-medium',
)

function Input({ className, type, ...props }: React.ComponentProps<'input'>) {
  return <input type={type} data-slot="input" className={cn(inputClassName, className)} {...props} />
}

export { Input, inputClassName }
