import * as React from 'react'
import { ChevronsUpDown } from 'lucide-react'

import { cn } from '@/lib/cn'

export interface ComboboxOption {
  value: string
  /** Text shown in the closed trigger. */
  label: string
  /** Extra words to match against (IATA, city, employee number...). */
  keywords?: string[]
  /** Option body in the list; defaults to the label. */
  render?: React.ReactNode
  /** Disabled options stay visible with the reason. */
  disabledReason?: string
}

export interface ComboboxProps {
  value: string | null | undefined
  onChange: (value: string | null) => void
  options: readonly ComboboxOption[]
  placeholder?: string
  searchPlaceholder?: string
  emptyText?: string
  /** For server-side search: called with the query, and the built-in filter is turned off. */
  onSearch?: (query: string) => void
  loading?: boolean
  id?: string
  disabled?: boolean
  className?: string
  triggerClassName?: string
  'aria-invalid'?: true | undefined
  'aria-describedby'?: string | undefined
  /** Content of the trigger when a value is selected; defaults to the option label. */
  renderValue?: (option: ComboboxOption) => React.ReactNode
  variant?: 'field' | 'pill'
}

export function triggerClass(variant: ComboboxProps['variant'], extra?: string) {
  return cn(
    'flex h-12 w-full min-w-0 cursor-pointer items-center justify-between gap-2 border border-transparent bg-elevated px-4 text-left text-base focus-ring transition-colors outline-none hover:border-border disabled:cursor-not-allowed disabled:opacity-50 aria-invalid:border-destructive',
    variant === 'pill' ? 'rounded-full' : 'rounded-input',
    extra,
  )
}

export function TriggerContent({
  selected,
  placeholder,
  renderValue,
}: {
  selected: ComboboxOption | undefined
  placeholder: string
  renderValue?: (option: ComboboxOption) => React.ReactNode
}) {
  return (
    <>
      <span className={cn('min-w-0 flex-1 truncate', !selected && 'text-muted-foreground')}>
        {selected ? (renderValue?.(selected) ?? selected.label) : placeholder}
      </span>
      <ChevronsUpDown className="size-4 shrink-0 text-muted-foreground" strokeWidth={1.75} aria-hidden />
    </>
  )
}

const loadImpl = () => import('./combobox-impl')
const ComboboxImpl = React.lazy(() => loadImpl().then((module) => ({ default: module.ComboboxImpl })))

/**
 * Searchable select: a button that opens a filterable list (keyboard: type, arrows, Enter, Escape).
 *
 * Until the user reaches for it, this renders a static trigger that looks identical; the popover and list
 * code (Radix Popover, cmdk) loads on hover or focus and takes over, keeping focus and opening on click.
 * That keeps it out of the initial bundle of pages like Home.
 */
export function Combobox(props: ComboboxProps) {
  const {
    value,
    options,
    placeholder = 'Select…',
    renderValue,
    variant = 'field',
    triggerClassName,
    id,
    disabled,
  } = props
  const listId = React.useId()
  const [active, setActive] = React.useState<{ open: boolean; focus: boolean } | null>(null)
  const selected = options.find((option) => option.value === value)

  const staticTrigger = (
    <button
      id={id}
      type="button"
      role="combobox"
      aria-expanded={false}
      aria-controls={listId}
      aria-haspopup="listbox"
      aria-invalid={props['aria-invalid']}
      aria-describedby={props['aria-describedby']}
      disabled={disabled}
      className={triggerClass(variant, triggerClassName)}
      onPointerEnter={() => void loadImpl()}
      onFocus={() => {
        void loadImpl()
        setActive((current) => current ?? { open: false, focus: true })
      }}
      // Open on press, and stop the browser from moving focus: the real trigger replaces this one
      // mid-press, and a focus landing on the removed node would close the popover again.
      onMouseDown={(event) => {
        event.preventDefault()
        setActive({ open: true, focus: true })
      }}
      onClick={() => setActive({ open: true, focus: true })}
    >
      <TriggerContent selected={selected} placeholder={placeholder} renderValue={renderValue} />
    </button>
  )

  if (!active) return staticTrigger
  return (
    <React.Suspense fallback={staticTrigger}>
      {/* When opening, Radix moves focus into the popover; only take focus back for a plain focus hand-off. */}
      <ComboboxImpl {...props} listId={listId} defaultOpen={active.open} takeFocus={active.focus && !active.open} />
    </React.Suspense>
  )
}
