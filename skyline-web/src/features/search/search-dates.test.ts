import { describe, expect, it } from 'vitest'

import { scheduleApi } from '@/api/endpoints/schedule'
import { makeFlight } from '@/test/fixtures'
import { db } from '@/test/handlers'

const search = (date: string) =>
  scheduleApi.search({ origin: 'IST', destination: 'LHR', date, page: 0, size: 20, sort: 'departureTime,asc' })

describe('flight search dates (mocked API)', () => {
  it('are calendar days at the origin airport, like the server', async () => {
    // 01:30 in Istanbul on 2 June is 22:30 UTC on 1 June.
    db.flights.push(makeFlight({ id: 'f0000000-0000-4000-8000-0000000000aa', departureTime: '2030-06-01T22:30:00' }))

    expect((await search('2030-06-02')).content.map((flight) => flight.id)).toContain(
      'f0000000-0000-4000-8000-0000000000aa',
    )
    expect((await search('2030-06-01')).content).toHaveLength(0)
  })
})
