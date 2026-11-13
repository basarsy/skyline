import * as React from 'react'
import { useQuery } from '@tanstack/react-query'
import { CalendarDays } from 'lucide-react'

import { networkApi } from '@/api/endpoints/network'
import { networkKeys } from '@/api/query-keys'
import type { AirportResponse } from '@/api/types'
import { STATIC_STALE_TIME } from '@/app/query-client'
import { cn } from '@/lib/cn'
import { formatLocalDate } from '@/lib/format'
import { Combobox, type ComboboxOption } from './combobox'

type ControlAria = { id?: string; 'aria-invalid'?: true | undefined; 'aria-describedby'?: string | undefined }

export function useAirports() {
  return useQuery({
    queryKey: networkKeys.airports,
    queryFn: ({ signal }) => networkApi.airports(signal),
    staleTime: STATIC_STALE_TIME,
  })
}

export function airportOption(
  airport: Pick<AirportResponse, 'id' | 'iataCode' | 'city' | 'name' | 'country'>,
  valueBy: 'id' | 'iata' = 'id',
): ComboboxOption {
  return {
    value: valueBy === 'id' ? airport.id : airport.iataCode,
    label: `${airport.iataCode} · ${airport.city}`,
    keywords: [airport.iataCode, airport.city, airport.name, airport.country],
    render: (
      <span className="flex items-baseline gap-3">
        <span className="w-10 shrink-0 font-mono font-semibold">{airport.iataCode}</span>
        <span className="min-w-0">
          <span className="block truncate">{airport.city}</span>
          <span className="block truncate text-caption text-muted-foreground">{airport.name}</span>
        </span>
      </span>
    ),
  }
}

/** Airport picker over GET /airports, searchable by IATA, city and name. */
export function AirportCombobox({
  value,
  onChange,
  valueBy = 'iata',
  exclude,
  placeholder = 'City or airport',
  variant,
  ...aria
}: {
  value: string | null | undefined
  onChange: (value: string | null) => void
  /** Search uses IATA codes; ops forms use ids. */
  valueBy?: 'id' | 'iata'
  /** Hides one airport, e.g. the origin in the destination picker. */
  exclude?: string | null
  placeholder?: string
  variant?: 'field' | 'pill'
} & ControlAria) {
  const { data = [], isLoading } = useAirports()
  const options = React.useMemo(
    () => data.map((airport) => airportOption(airport, valueBy)).filter((option) => option.value !== exclude),
    [data, valueBy, exclude],
  )
  return (
    <Combobox
      value={value}
      onChange={onChange}
      options={options}
      loading={isLoading}
      placeholder={isLoading ? 'Loading airports…' : placeholder}
      searchPlaceholder="Search IATA, city or airport"
      emptyText="No airport matches"
      variant={variant}
      renderValue={(option) => {
        const airport = data.find((a) => (valueBy === 'id' ? a.id : a.iataCode) === option.value)
        return airport ? (
          <span className="flex items-baseline gap-2">
            <span className="font-mono font-semibold">{airport.iataCode}</span>
            <span className="truncate text-muted-foreground">{airport.city}</span>
          </span>
        ) : (
          option.label
        )
      }}
      {...aria}
    />
  )
}

let countryCache: ComboboxOption[] | null = null

/** Country names from Intl.DisplayNames; the API takes the name string. */
export function countryOptions(): ComboboxOption[] {
  if (countryCache) return countryCache
  const names = new Intl.DisplayNames(['en'], { type: 'region' })
  const codes: string[] = []
  for (let a = 65; a <= 90; a += 1) {
    for (let b = 65; b <= 90; b += 1) codes.push(String.fromCharCode(a, b))
  }
  const skip = new Set(['EU', 'EZ', 'UN', 'QO', 'XA', 'XB', 'ZZ'])
  const seen = new Set<string>()
  const options: ComboboxOption[] = []
  for (const code of codes) {
    if (skip.has(code)) continue
    let name: string | undefined
    try {
      name = names.of(code)
    } catch {
      continue
    }
    // Unknown codes come back unchanged; skip those and duplicates.
    if (!name || name === code || seen.has(name)) continue
    seen.add(name)
    options.push({ value: name, label: name, keywords: [code] })
  }
  countryCache = options.sort((x, y) => x.label.localeCompare(y.label))
  return countryCache
}

export function CountryCombobox(
  props: { value: string | null | undefined; onChange: (value: string | null) => void } & ControlAria,
) {
  const options = React.useMemo(() => countryOptions(), [])
  return <Combobox {...props} options={options} placeholder="Select a country" searchPlaceholder="Search countries" />
}

/** IANA time zones the browser knows, for creating airports. */
export function TimezoneCombobox(
  props: { value: string | null | undefined; onChange: (value: string | null) => void } & ControlAria,
) {
  const options = React.useMemo<ComboboxOption[]>(() => {
    const zones = typeof Intl.supportedValuesOf === 'function' ? Intl.supportedValuesOf('timeZone') : ['UTC']
    return zones.map((zone) => ({ value: zone, label: zone, keywords: zone.split('/') }))
  }, [])
  return (
    <Combobox {...props} options={options} placeholder="e.g. Europe/Istanbul" searchPlaceholder="Search time zones" />
  )
}

export interface DatePickerProps extends ControlAria {
  value: string | null | undefined
  onChange: (value: string) => void
  disablePast?: boolean
  disableFuture?: boolean
  note?: string
  placeholder?: string
  variant?: 'field' | 'pill'
  captionLayout?: 'label' | 'dropdown'
  fromYear?: number
  toYear?: number
}

export function dateTriggerClass(variant: DatePickerProps['variant']) {
  return cn(
    'flex h-12 w-full cursor-pointer items-center gap-3 border border-transparent bg-elevated px-4 text-left text-base focus-ring transition-colors outline-none hover:border-border data-[invalid=true]:border-destructive',
    variant === 'pill' ? 'rounded-full' : 'rounded-input',
  )
}

export function DateTriggerContent({ value, placeholder }: { value: string | null | undefined; placeholder: string }) {
  return (
    <>
      <CalendarDays className="size-[18px] shrink-0 text-muted-foreground" strokeWidth={1.75} aria-hidden />
      <span className={cn('truncate tabular', !value && 'text-muted-foreground')}>
        {value ? formatLocalDate(value, 'EEE d MMM yyyy') : placeholder}
      </span>
    </>
  )
}

const loadDatePicker = () => import('./date-picker-impl')
const DatePickerImpl = React.lazy(() => loadDatePicker().then((module) => ({ default: module.DatePickerImpl })))

/**
 * Date picker for LocalDate values ("yyyy-MM-dd"), without zone conversion. The note renders under the
 * calendar (e.g. "Dates are in UTC" for search). Like Combobox, it renders a static trigger until the user
 * reaches for it; the popover and calendar load on hover or focus.
 */
export function DatePicker(props: DatePickerProps) {
  const { value, placeholder = 'Pick a date', variant = 'field', id } = props
  const [active, setActive] = React.useState<{ open: boolean; focus: boolean } | null>(null)
  const staticTrigger = (
    <button
      id={id}
      type="button"
      // Buttons can't carry aria-invalid; the error text is linked through aria-describedby.
      data-invalid={props['aria-invalid']}
      aria-describedby={props['aria-describedby']}
      className={dateTriggerClass(variant)}
      onPointerEnter={() => void loadDatePicker()}
      onFocus={() => {
        void loadDatePicker()
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
      <DateTriggerContent value={value} placeholder={placeholder} />
    </button>
  )
  if (!active) return staticTrigger
  return (
    <React.Suspense fallback={staticTrigger}>
      <DatePickerImpl {...props} defaultOpen={active.open} takeFocus={active.focus && !active.open} />
    </React.Suspense>
  )
}
