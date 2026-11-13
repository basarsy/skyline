import * as React from 'react'
import * as m from 'motion/react-m'
import { RadioGroup } from 'radix-ui'

import { cn } from '@/lib/cn'
import { usePrefersReducedMotion } from '@/lib/hooks'

interface Option<T extends string> {
  value: T
  label: React.ReactNode
}

interface SegmentedControlProps<T extends string> {
  value: T
  onValueChange: (value: T) => void
  options: readonly Option<T>[]
  /** Accessible name of the group. */
  label: string
  className?: string
  size?: 'default' | 'sm'
}

/**
 * Pill segmented control on the elevated track; the active pill slides with a spring. Radio-group
 * semantics, so arrow keys move between options.
 */
export function SegmentedControl<T extends string>({
  value,
  onValueChange,
  options,
  label,
  className,
  size = 'default',
}: SegmentedControlProps<T>) {
  const layoutId = React.useId()
  const reduce = usePrefersReducedMotion()
  return (
    <RadioGroup.Root
      value={value}
      onValueChange={(next) => onValueChange(next as T)}
      aria-label={label}
      orientation="horizontal"
      className={cn('inline-flex rounded-full bg-elevated p-1', className)}
    >
      {options.map((option) => {
        const active = option.value === value
        return (
          <RadioGroup.Item
            key={option.value}
            value={option.value}
            className={cn(
              'relative flex-1 cursor-pointer rounded-full font-medium whitespace-nowrap focus-ring transition-colors duration-200 outline-none',
              size === 'sm' ? 'h-9 px-3.5 text-sm' : 'h-10 px-4 text-sm',
              active ? 'text-primary-foreground' : 'text-muted-foreground hover:text-foreground',
            )}
          >
            {active && (
              <m.span
                layoutId={reduce ? undefined : layoutId}
                aria-hidden
                className="absolute inset-0 rounded-full bg-primary"
                transition={{ type: 'spring', stiffness: 500, damping: 40 }}
              />
            )}
            <span className="relative">{option.label}</span>
          </RadioGroup.Item>
        )
      })}
    </RadioGroup.Root>
  )
}
