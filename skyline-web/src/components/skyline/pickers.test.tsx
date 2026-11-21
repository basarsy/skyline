import * as React from 'react'
import { QueryClientProvider } from '@tanstack/react-query'
import { renderHook, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { createQueryClient } from '@/app/query-client'
import { isoFromLocalDate } from '@/lib/format'
import { airports } from '@/test/fixtures'
import { db } from '@/test/handlers'
import { useAirportToday } from './pickers'

// 22:30 UTC on 1 June: already 2 June in Istanbul.
const NOW = new Date('2030-06-01T22:30:00Z')

function renderToday(iata: string | null) {
  const client = createQueryClient()
  const wrapper = ({ children }: { children: React.ReactNode }) => (
    <QueryClientProvider client={client}>{children}</QueryClientProvider>
  )
  return renderHook(() => useAirportToday(iata), { wrapper })
}

describe('useAirportToday', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(NOW)
  })
  afterEach(() => vi.useRealTimers())

  it("is the browser's today until the airport list has loaded, then the airport's", async () => {
    const { result } = renderToday('IST')

    expect(result.current).toBe(isoFromLocalDate(NOW))
    await waitFor(() => expect(result.current).toBe('2030-06-02'))
  })

  it("is the browser's today for no or an unknown airport", async () => {
    const { result } = renderToday('XXX')
    await new Promise((resolve) => setTimeout(resolve, 50))

    expect(result.current).toBe(isoFromLocalDate(NOW))
    expect(renderToday(null).result.current).toBe(isoFromLocalDate(NOW))
  })

  it("is UTC's today for a known airport without a valid zone, as the API searches it", async () => {
    db.airports.push({ ...airports[0]!, id: '11111111-1111-1111-1111-111111111199', iataCode: 'NOZ', timezone: '' })

    const { result } = renderToday('NOZ')

    await waitFor(() => expect(result.current).toBe('2030-06-01'))
  })
})
