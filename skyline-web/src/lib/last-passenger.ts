/**
 * The last passenger a user booked for, to prefill the booking form next time. Only the name and nationality
 * are kept: passport number and date of birth are never written to storage. Entries are per user and are
 * removed when the session ends.
 */
const PREFIX = 'skyline.lastPassenger'
const KEPT_FIELDS = ['firstName', 'lastName', 'nationality'] as const

export type SavedPassenger = Partial<Record<(typeof KEPT_FIELDS)[number], string>>

const keyFor = (userId: string) => `${PREFIX}.${userId}`

function pick(value: unknown): SavedPassenger {
  const saved: SavedPassenger = {}
  if (typeof value !== 'object' || value === null) return saved
  for (const field of KEPT_FIELDS) {
    const entry = (value as Record<string, unknown>)[field]
    if (typeof entry === 'string') saved[field] = entry
  }
  return saved
}

export function readLastPassenger(userId: string | undefined): SavedPassenger {
  try {
    // Older builds stored the whole form, passport number included, under one shared key.
    localStorage.removeItem(PREFIX)
    if (!userId) return {}
    const raw = localStorage.getItem(keyFor(userId))
    if (!raw) return {}
    const parsed: unknown = JSON.parse(raw)
    const saved = pick(parsed)
    // Rewrite an entry that still holds anything beyond the kept fields.
    if (Object.keys(parsed as object).length !== Object.keys(saved).length) {
      localStorage.setItem(keyFor(userId), JSON.stringify(saved))
    }
    return saved
  } catch {
    return {}
  }
}

export function saveLastPassenger(userId: string | undefined, values: SavedPassenger) {
  if (!userId) return
  try {
    localStorage.setItem(keyFor(userId), JSON.stringify(pick(values)))
  } catch {
    // Prefill is a convenience only.
  }
}

/** Removes every user's entry (and the legacy one). */
export function clearLastPassengers() {
  try {
    const keys = Array.from({ length: localStorage.length }, (_, i) => localStorage.key(i))
    keys.filter((key) => key?.startsWith(PREFIX)).forEach((key) => localStorage.removeItem(key!))
  } catch {
    // Storage unavailable: nothing was stored either.
  }
}
