import * as React from 'react'
import { Check, ChevronsUpDown } from 'lucide-react'

import { Command, CommandEmpty, CommandGroup, CommandInput, CommandItem, CommandList } from '@/components/ui/command'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
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

interface ComboboxProps {
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

/** Searchable select: a button that opens a filterable list. Keyboard: type, arrows, Enter, Escape. */
export function Combobox({
  value,
  onChange,
  options,
  placeholder = 'Select…',
  searchPlaceholder = 'Search…',
  emptyText = 'No matches',
  onSearch,
  loading,
  id,
  disabled,
  className,
  triggerClassName,
  renderValue,
  variant = 'field',
  ...aria
}: ComboboxProps) {
  const [open, setOpen] = React.useState(false)
  const listId = React.useId()
  const selected = options.find((option) => option.value === value)

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <button
          id={id}
          type="button"
          role="combobox"
          aria-expanded={open}
          aria-controls={listId}
          aria-haspopup="listbox"
          disabled={disabled}
          {...aria}
          className={cn(
            'flex h-12 w-full min-w-0 cursor-pointer items-center justify-between gap-2 border border-transparent bg-elevated px-4 text-left text-base focus-ring transition-colors outline-none hover:border-border disabled:cursor-not-allowed disabled:opacity-50 aria-invalid:border-destructive',
            variant === 'pill' ? 'rounded-full' : 'rounded-input',
            triggerClassName,
          )}
        >
          <span className={cn('min-w-0 flex-1 truncate', !selected && 'text-muted-foreground')}>
            {selected ? (renderValue?.(selected) ?? selected.label) : placeholder}
          </span>
          <ChevronsUpDown className="size-4 shrink-0 text-muted-foreground" strokeWidth={1.75} aria-hidden />
        </button>
      </PopoverTrigger>
      <PopoverContent
        align="start"
        className={cn('w-[min(var(--radix-popover-trigger-width),calc(100vw-2rem))] min-w-72 p-0', className)}
      >
        <Command shouldFilter={!onSearch} className="rounded-2xl">
          <CommandInput placeholder={searchPlaceholder} onValueChange={onSearch} className="h-12 text-base" />
          <CommandList id={listId} className="max-h-72">
            <CommandEmpty>{loading ? 'Searching…' : emptyText}</CommandEmpty>
            <CommandGroup>
              {options.map((option) => (
                <CommandItem
                  key={option.value}
                  value={option.value}
                  keywords={[option.label, ...(option.keywords ?? [])]}
                  disabled={!!option.disabledReason}
                  onSelect={() => {
                    onChange(option.value === value ? null : option.value)
                    setOpen(false)
                  }}
                  className="min-h-11 cursor-pointer gap-3 rounded-xl px-3 py-2"
                >
                  <div className="min-w-0 flex-1">
                    {option.render ?? option.label}
                    {option.disabledReason && (
                      <div className="mt-0.5 text-caption text-muted-foreground">{option.disabledReason}</div>
                    )}
                  </div>
                  <Check
                    className={cn('size-4 shrink-0', option.value === value ? 'opacity-100' : 'opacity-0')}
                    strokeWidth={1.75}
                    aria-hidden
                  />
                </CommandItem>
              ))}
            </CommandGroup>
          </CommandList>
        </Command>
      </PopoverContent>
    </Popover>
  )
}
