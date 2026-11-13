import * as React from 'react'
import { ArrowRight, Plus } from 'lucide-react'

import type { CabinClass } from '@/api/types'
import { BoardingPass } from '@/components/skyline/boarding-pass'
import { CountdownTimer } from '@/components/skyline/countdown'
import { FlightCard, FlightTimeline } from '@/components/skyline/flight'
import { FormField, PasswordInput } from '@/components/skyline/form'
import { PnrDisplay, PnrInput } from '@/components/skyline/pnr'
import { PriceTag } from '@/components/skyline/price-tag'
import { SegmentedControl } from '@/components/skyline/segmented-control'
import { EmptyState, ErrorState, SkeletonCard } from '@/components/skyline/states'
import { AircraftStatusBadge, FlightStatusBadge, ReservationStatusBadge } from '@/components/skyline/status-badge'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { Input } from '@/components/ui/input'
import { cn } from '@/lib/cn'
import { useDocumentTitle } from '@/lib/hooks'
import { AIRCRAFT_STATUSES, CABINS, cabinLabel, FLIGHT_STATUSES } from '@/lib/labels'
import { ApiError } from '@/api/http'
import { makeBoardingPass, makeFlight } from '@/test/fixtures'

const TOKENS = [
  'background',
  'foreground',
  'card',
  'elevated',
  'primary',
  'primary-foreground',
  'secondary',
  'muted-foreground',
  'border',
  'ring',
  'destructive',
  'success',
] as const

/** Contrast pairs checked in both themes (see README, "Contrast"). */
const PAIRS = [
  ['foreground', 'background'],
  ['foreground', 'card'],
  ['muted-foreground', 'card'],
  ['muted-foreground', 'background'],
  ['muted-foreground', 'elevated'],
  ['primary-foreground', 'primary'],
  ['destructive', 'card'],
  ['success', 'card'],
] as const

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section className="flex flex-col gap-4">
      <h2 className="micro-label">{title}</h2>
      {children}
    </section>
  )
}

/** WCAG contrast of each pair, measured from the tokens as the browser resolves them in this theme. */
function measureContrast(theme: 'light' | 'dark') {
  const probe = document.createElement('div')
  probe.className = theme
  document.body.appendChild(probe)
  const styles = getComputedStyle(probe)
  const canvas = document.createElement('canvas')
  canvas.width = canvas.height = 1
  const ctx = canvas.getContext('2d', { willReadFrequently: true })
  const luminance = (token: string) => {
    if (!ctx) return 0
    ctx.clearRect(0, 0, 1, 1)
    ctx.fillStyle = styles.getPropertyValue(`--${token}`).trim()
    ctx.fillRect(0, 0, 1, 1)
    const [r = 0, g = 0, b = 0] = Array.from(ctx.getImageData(0, 0, 1, 1).data, (c) => {
      const v = c / 255
      return v <= 0.03928 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4
    })
    return 0.2126 * r + 0.7152 * g + 0.0722 * b
  }
  const result: Record<string, number> = {}
  for (const [fg, bg] of PAIRS) {
    const a = luminance(fg)
    const b = luminance(bg)
    result[`${fg}/${bg}`] = (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05)
  }
  probe.remove()
  return result
}

function ContrastTable({ theme }: { theme: 'light' | 'dark' }) {
  const [ratios] = React.useState(() => measureContrast(theme))

  return (
    <table className="w-full text-sm">
      <thead>
        <tr className="text-left text-muted-foreground">
          <th className="py-2 font-medium">Text on surface</th>
          <th className="py-2 font-medium">Ratio</th>
          <th className="py-2 font-medium">Needs</th>
        </tr>
      </thead>
      <tbody>
        {PAIRS.map(([fg, bg]) => {
          const ratio = ratios[`${fg}/${bg}`]
          const needs = fg === 'destructive' || fg === 'success' ? 3 : 4.5
          return (
            <tr key={`${fg}/${bg}`} className="border-t border-border">
              <td className="py-2">
                <span className="font-mono text-caption">
                  {fg} / {bg}
                </span>
              </td>
              <td className="py-2 tabular">{ratio ? `${ratio.toFixed(2)}:1` : '…'}</td>
              <td className="py-2 tabular">
                {needs}:1 {ratio !== undefined && (ratio >= needs ? '✓' : '✗')}
              </td>
            </tr>
          )
        })}
      </tbody>
    </table>
  )
}

function ThemePanel({ theme }: { theme: 'light' | 'dark' }) {
  const [cabin, setCabin] = React.useState<CabinClass>('ECONOMY')
  const [pnr, setPnr] = React.useState('K7Q2MX')
  const flight = makeFlight({ departureTime: '2026-12-01T20:30:00', arrivalTime: '2026-12-02T01:30:00' })
  const [opens] = React.useState(() => new Date(Date.now() + 26 * 3_600_000))

  return (
    <div
      className={cn(
        theme,
        'flex flex-col gap-10 rounded-card bg-background p-5 text-foreground ring-1 ring-border sm:p-8',
      )}
    >
      <h2 className="text-h2 font-semibold capitalize">{theme}</h2>

      <Section title="Color tokens">
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
          {TOKENS.map((token) => (
            <div key={token} className="flex items-center gap-3">
              <span
                className="size-10 shrink-0 rounded-xl ring-1 ring-border"
                style={{ background: `var(--${token})` }}
              />
              <span className="font-mono text-caption">{token}</span>
            </div>
          ))}
        </div>
      </Section>

      <Section title="Contrast">
        <ContrastTable theme={theme} />
      </Section>

      <Section title="Type scale">
        <div className="flex flex-col gap-3">
          <span className="font-display text-display font-semibold tracking-[-0.035em]">Display 3.5</span>
          <span className="font-display text-h1 font-semibold">Heading 1 · 2.25</span>
          <span className="font-display text-h2 font-semibold">Heading 2 · 1.75</span>
          <span className="font-display text-h3 font-semibold">Heading 3 · 1.25</span>
          <span>Body · 1rem. The quick brown fox jumps over the lazy dog.</span>
          <span className="text-sm">Small · 0.875rem</span>
          <span className="text-caption text-muted-foreground">Caption · 0.75rem, metadata only</span>
          <span className="micro-label">Micro label</span>
          <span className="hero-number text-display">14:30</span>
          <span className="font-mono">JetBrains Mono · TC-SKA · K7Q2MX</span>
        </div>
      </Section>

      <Section title="Buttons">
        <div className="flex flex-wrap gap-3">
          <Button>Primary</Button>
          <Button variant="secondary">Secondary</Button>
          <Button variant="outline">Outline</Button>
          <Button variant="ghost">Ghost</Button>
          <Button variant="destructive">Destructive</Button>
          <Button variant="link">Link</Button>
        </div>
        <div className="flex flex-wrap items-center gap-3">
          <Button className="bg-primary/85">Hover</Button>
          <Button className="outline-2 outline-offset-2 outline-ring">Focus</Button>
          <Button disabled>Disabled</Button>
          <Button loading>Loading</Button>
          <Button size="sm">Small</Button>
          <Button size="lg">
            Large <ArrowRight strokeWidth={1.75} aria-hidden />
          </Button>
          <Button size="icon" variant="secondary" aria-label="Add">
            <Plus strokeWidth={1.75} aria-hidden />
          </Button>
        </div>
      </Section>

      <Section title="Inputs">
        <div className="grid gap-4 sm:grid-cols-2">
          <FormField label="Default">{(c) => <Input {...c} placeholder="Placeholder" />}</FormField>
          <FormField label="Focus">
            {(c) => (
              <Input {...c} defaultValue="Focused" className="border-ring outline-2 outline-offset-2 outline-ring" />
            )}
          </FormField>
          <FormField label="Error" error="Enter the last name">
            {(c) => <Input {...c} defaultValue="" />}
          </FormField>
          <FormField label="Disabled">{(c) => <Input {...c} disabled defaultValue="Disabled" />}</FormField>
          <FormField label="Password">{(c) => <PasswordInput {...c} defaultValue="Passw0rd!!" />}</FormField>
          <FormField label="PNR">{(c) => <PnrInput {...c} value={pnr} onChange={setPnr} />}</FormField>
        </div>
        <label htmlFor={`sg-check-${theme}`} className="flex items-center gap-3 text-sm">
          <Checkbox id={`sg-check-${theme}`} defaultChecked className="size-5" /> Checkbox
        </label>
        <SegmentedControl
          label="Cabin"
          value={cabin}
          onValueChange={setCabin}
          options={CABINS.map((c) => ({ value: c, label: cabinLabel[c] }))}
        />
      </Section>

      <Section title="Badges">
        <div className="flex flex-wrap gap-2">
          {FLIGHT_STATUSES.map((s) => (
            <FlightStatusBadge key={s} status={s} />
          ))}
        </div>
        <div className="flex flex-wrap gap-2">
          {(['PENDING', 'CONFIRMED', 'CHECKED_IN', 'CANCELLED'] as const).map((s) => (
            <ReservationStatusBadge key={s} status={s} />
          ))}
          {AIRCRAFT_STATUSES.map((s) => (
            <AircraftStatusBadge key={s} status={s} />
          ))}
          <Badge variant="outline">Outline</Badge>
        </div>
      </Section>

      <Section title="Prices, PNR, countdown">
        <div className="flex flex-wrap items-end gap-8">
          <PriceTag amount={1234.5} currency="EUR" size="hero" label="Estimated" />
          <PriceTag amount={250} currency="EUR" size="large" label="Total" />
          <PriceTag amount={99} currency="TRY" />
        </div>
        <PnrDisplay pnr="K7Q2MX" />
        <CountdownTimer target={opens} size="hero" />
      </Section>

      <Section title="Flight">
        <div className="rounded-card border border-border bg-card p-5">
          <FlightTimeline flight={flight} />
        </div>
        <FlightCard
          flight={{ ...flight, status: 'DELAYED', availableSeats: 12 }}
          price={<PriceTag amount={138} currency="EUR" size="large" label="Estimated" />}
          action={<Button>Select</Button>}
        />
      </Section>

      <Section title="States">
        <SkeletonCard />
        <EmptyState
          title="No flights on this day"
          description="Try a nearby day."
          action={<Button variant="outline">Try Tue 2 Dec</Button>}
        />
        <ErrorState error={new ApiError(503, 'Flight details unavailable, please retry')} onRetry={() => {}} />
      </Section>

      <Section title="Boarding pass (always black on white)">
        <BoardingPass pass={makeBoardingPass()} originZone="Europe/Istanbul" showActions={false} />
      </Section>
    </div>
  )
}

/** Dev-only living style guide: every token, the type scale and component states, in both themes. */
export function Component() {
  useDocumentTitle('Style guide')
  return (
    <div className="mx-auto flex max-w-[1440px] flex-col gap-8 px-4 py-10 sm:px-6">
      <div className="flex flex-col gap-2">
        <h1 className="text-h1 font-semibold">Style guide</h1>
        <p className="text-muted-foreground">
          Development only. Tokens, type and every component state, in both themes.
        </p>
      </div>
      <div className="grid gap-6 xl:grid-cols-2">
        <ThemePanel theme="light" />
        <ThemePanel theme="dark" />
      </div>
    </div>
  )
}
