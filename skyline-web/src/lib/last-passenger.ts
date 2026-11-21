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

// Older builds stored the whole form, passport number included, under one shared key: delete it on load.
try {
  localStorage.removeItem(PREFIX)
} catch {
  // Storage unavailable.
}

/** Only the kept fields come back, whatever the stored entry holds. */
export function readLastPassenger(userId: string | undefined): SavedPassenger {
  if (!userId) return {}
  try {
    return pick(JSON.parse(localStorage.getItem(keyFor(userId)) ?? '{}'))
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
