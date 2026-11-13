import * as React from 'react'
import { useQuery } from '@tanstack/react-query'
import { CalendarDays } from 'lucide-react'

import { networkApi } from '@/api/endpoints/network'
import { networkKeys } from '@/api/query-keys'
import type { AirportResponse } from '@/api/types'
import { STATIC_STALE_TIME } from '@/app/query-client'
import { Calendar } from '@/components/ui/calendar'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import { cn } from '@/lib/cn'
import { formatLocalDate, isoFromLocalDate, localDateFromIso } from '@/lib/format'
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

/**
 * Date picker for LocalDate values ("yyyy-MM-dd"), without zone conversion. `note` renders under the
 * calendar (e.g. "Dates are in UTC" for search).
 */
export function DatePicker({
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
  ...aria
}: {
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
} & ControlAria) {
  const [open, setOpen] = React.useState(false)
  const selected = value ? localDateFromIso(value) : undefined
  const today = new Date()
  today.setHours(0, 0, 0, 0)

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <button
          type="button"
          {...aria}
          className={cn(
            'flex h-12 w-full cursor-pointer items-center gap-3 border border-transparent bg-elevated px-4 text-left text-base focus-ring transition-colors outline-none hover:border-border aria-invalid:border-destructive',
            variant === 'pill' ? 'rounded-full' : 'rounded-input',
          )}
        >
          <CalendarDays className="size-[18px] shrink-0 text-muted-foreground" strokeWidth={1.75} aria-hidden />
          <span className={cn('truncate tabular', !value && 'text-muted-foreground')}>
            {value ? formatLocalDate(value, 'EEE d MMM yyyy') : placeholder}
          </span>
        </button>
      </PopoverTrigger>
      <PopoverContent align="start" className="w-auto p-3">
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
        {note && <p className="px-2 pt-2 pb-1 text-caption text-muted-foreground">{note}</p>}
      </PopoverContent>
    </Popover>
  )
}
