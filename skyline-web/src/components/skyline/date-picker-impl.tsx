import * as React from 'react'

import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import { isoFromLocalDate, localDateFromIso } from '@/lib/format'
import { DateTriggerContent, dateTriggerClass, type DatePickerProps } from './pickers'

const Calendar = React.lazy(() => import('@/components/ui/calendar').then((module) => ({ default: module.Calendar })))

/** The interactive date picker (Radix Popover + react-day-picker), loaded on first interaction. */
export function DatePickerImpl({
  value,
  onChange,
  disablePast,
  disableFuture,
  note,
  placeholder = 'Pick a date',
  variant = 'field',
  captionLayout,
  fromYear,
  toYear,
  defaultOpen = false,
  takeFocus = false,
  'aria-invalid': invalid,
  ...aria
}: DatePickerProps & { defaultOpen?: boolean; takeFocus?: boolean }) {
  const [open, setOpen] = React.useState(defaultOpen)
  const triggerRef = React.useRef<HTMLButtonElement>(null)
  const selected = value ? localDateFromIso(value) : undefined
  const today = new Date()
  today.setHours(0, 0, 0, 0)

  // Taking over from the static trigger: keep keyboard focus where the user left it.
  React.useEffect(() => {
    if (takeFocus) triggerRef.current?.focus()
  }, [takeFocus])

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <button ref={triggerRef} type="button" data-invalid={invalid} {...aria} className={dateTriggerClass(variant)}>
          <DateTriggerContent value={value} placeholder={placeholder} />
        </button>
      </PopoverTrigger>
      <PopoverContent align="start" className="w-auto p-3">
        <React.Suspense fallback={<div className="h-[300px] w-[252px]" role="status" aria-label="Loading calendar" />}>
          <Calendar
            mode="single"
            selected={selected}
            defaultMonth={selected}
            captionLayout={captionLayout}
            startMonth={fromYear ? new Date(fromYear, 0) : undefined}
            endMonth={toYear ? new Date(toYear, 11) : undefined}
            onSelect={(date) => {
              if (date) {
                onChange(isoFromLocalDate(date))
                setOpen(false)
              }
            }}
            disabled={[
              ...(disablePast ? [{ before: today }] : []),
              ...(disableFuture ? [{ after: new Date(today.getTime() - 86_400_000) }] : []),
            ]}
          />
        </React.Suspense>
        {note && <p className="px-2 pt-2 pb-1 text-caption text-muted-foreground">{note}</p>}
      </PopoverContent>
    </Popover>
  )
}
