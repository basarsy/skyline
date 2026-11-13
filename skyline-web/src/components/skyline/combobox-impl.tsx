import * as React from 'react'
import { Check } from 'lucide-react'

import { Command, CommandEmpty, CommandGroup, CommandInput, CommandItem, CommandList } from '@/components/ui/command'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import { cn } from '@/lib/cn'
import { triggerClass, TriggerContent, type ComboboxProps } from './combobox'

/** The interactive combobox (Radix Popover + cmdk), loaded on first interaction by Combobox. */
export function ComboboxImpl({
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
  defaultOpen = false,
  takeFocus = false,
  listId,
  ...aria
}: ComboboxProps & { defaultOpen?: boolean; takeFocus?: boolean; listId: string }) {
  const [open, setOpen] = React.useState(defaultOpen)
  const triggerRef = React.useRef<HTMLButtonElement>(null)
  // Taking over from the static trigger: keep keyboard focus where the user left it.
  React.useEffect(() => {
    if (takeFocus) triggerRef.current?.focus()
  }, [takeFocus])
  const selected = options.find((option) => option.value === value)

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <button
          ref={triggerRef}
          id={id}
          type="button"
          role="combobox"
          aria-expanded={open}
          aria-controls={listId}
          aria-haspopup="listbox"
          disabled={disabled}
          {...aria}
          className={triggerClass(variant, triggerClassName)}
        >
          <TriggerContent selected={selected} placeholder={placeholder} renderValue={renderValue} />
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
