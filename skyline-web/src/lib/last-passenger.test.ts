import { describe, expect, it, vi } from 'vitest'

import { clearLastPassengers, readLastPassenger, saveLastPassenger } from './last-passenger'

const ADA = 'user-ada'
const full = {
  firstName: 'Ada',
  lastName: 'Lovelace',
  nationality: 'GB',
  passportNumber: 'U1234567',
  dateOfBirth: '1990-01-15',
}

describe('last passenger prefill', () => {
  it('stores the name and nationality only', () => {
    saveLastPassenger(ADA, full)

    const stored = localStorage.getItem(`skyline.lastPassenger.${ADA}`)!
    expect(stored).not.toContain('U1234567')
    expect(stored).not.toContain('1990-01-15')
    expect(readLastPassenger(ADA)).toEqual({ firstName: 'Ada', lastName: 'Lovelace', nationality: 'GB' })
  })

  it('is kept per user, and not at all without one', () => {
    saveLastPassenger(ADA, full)
    saveLastPassenger(undefined, full)

    expect(readLastPassenger('someone-else')).toEqual({})
    expect(readLastPassenger(undefined)).toEqual({})
    expect(localStorage.length).toBe(1)
  })

  it('deletes the legacy entry that held the whole form when the app loads', async () => {
    localStorage.setItem('skyline.lastPassenger', JSON.stringify(full))
    vi.resetModules()

    const fresh = await import('./last-passenger')

    expect(localStorage.getItem('skyline.lastPassenger')).toBeNull()
    expect(fresh.readLastPassenger(ADA)).toEqual({})
  })

  it('never hands back passport number or date of birth, whatever is stored', () => {
    localStorage.setItem(`skyline.lastPassenger.${ADA}`, JSON.stringify(full))

    expect(readLastPassenger(ADA)).toEqual({ firstName: 'Ada', lastName: 'Lovelace', nationality: 'GB' })
  })

  it('ignores a corrupt entry', () => {
    localStorage.setItem(`skyline.lastPassenger.${ADA}`, '{not json')

    expect(readLastPassenger(ADA)).toEqual({})
  })

  it('clears every entry and leaves other keys alone', () => {
    saveLastPassenger(ADA, full)
    saveLastPassenger('user-grace', full)
    localStorage.setItem('skyline.lastPassenger', '{}')
    localStorage.setItem('skyline.theme', 'dark')

    clearLastPassengers()

    expect(Object.keys(localStorage)).toEqual(['skyline.theme'])
  })
})
