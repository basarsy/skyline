import { useEffect, useState } from 'react'
import { networkApi } from '@/api/endpoints/network'
import type { AirportResponse } from '@/api/types'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { useTheme } from '@/lib/theme'

/** Temporary page while the app is being built: proves fonts, tokens, themes and the API proxy. */
export function Preview() {
  const { resolved, setPreference } = useTheme()
  const [airports, setAirports] = useState<AirportResponse[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    networkApi.airports().then(setAirports, (e: Error) => setError(e.message))
  }, [])

  return (
    <main className="mx-auto flex min-h-dvh max-w-[1200px] flex-col gap-12 px-4 py-16 sm:px-6">
      <header className="flex items-center justify-between">
        <span className="font-display text-xl font-bold tracking-[-0.03em]">skyline</span>
        <Button variant="outline" size="sm" onClick={() => setPreference(resolved === 'dark' ? 'light' : 'dark')}>
          {resolved === 'dark' ? 'Light' : 'Dark'} theme
        </Button>
      </header>
      <section className="flex flex-col gap-4">
        <h1 className="text-display-sm font-semibold tracking-[-0.035em] sm:text-display">
          Fly somewhere
          <br />
          worth it.
        </h1>
        <p className="max-w-prose text-muted-foreground">
          Preview build. The screens are still being built; this page checks fonts, tokens, themes and the API proxy.
        </p>
      </section>
      <Card>
        <span className="micro-label">Airports · GET /api/v1/airports</span>
        {error && <p className="text-sm text-destructive">{error}</p>}
        {!airports && !error && <p className="text-sm text-muted-foreground">Loading…</p>}
        <ul className="grid gap-3 sm:grid-cols-3">
          {airports?.map((a) => (
            <li key={a.id} className="rounded-2xl bg-elevated p-4">
              <div className="hero-number text-h1">{a.iataCode}</div>
              <div className="mt-2 text-sm">{a.city}</div>
              <div className="text-caption text-muted-foreground">{a.timezone}</div>
            </li>
          ))}
        </ul>
        <div className="flex flex-wrap gap-3">
          <Button>Search flights</Button>
          <Button variant="secondary">Manage booking</Button>
          <Button variant="destructive">Cancel</Button>
        </div>
      </Card>
    </main>
  )
}
