import { useSyncExternalStore } from 'react'

export type ThemePreference = 'system' | 'light' | 'dark'
export type ResolvedTheme = 'light' | 'dark'

/** Same key as the inline script in index.html, which applies the class before first paint. */
export const THEME_STORAGE_KEY = 'skyline.theme'

const listeners = new Set<() => void>()
const media = typeof window !== 'undefined' ? window.matchMedia('(prefers-color-scheme: dark)') : null

function readPreference(): ThemePreference {
  try {
    const stored = localStorage.getItem(THEME_STORAGE_KEY)
    return stored === 'light' || stored === 'dark' ? stored : 'system'
  } catch {
    return 'system'
  }
}

let preference: ThemePreference = typeof window !== 'undefined' ? readPreference() : 'system'

function resolve(pref: ThemePreference): ResolvedTheme {
  if (pref === 'system') return media?.matches ? 'dark' : 'light'
  return pref
}

function apply() {
  const resolved = resolve(preference)
  const root = document.documentElement
  root.classList.toggle('dark', resolved === 'dark')
  root.style.colorScheme = resolved
}

function emit() {
  apply()
  listeners.forEach((listener) => listener())
}

media?.addEventListener('change', () => {
  if (preference === 'system') emit()
})

export function setThemePreference(next: ThemePreference) {
  preference = next
  try {
    if (next === 'system') localStorage.removeItem(THEME_STORAGE_KEY)
    else localStorage.setItem(THEME_STORAGE_KEY, next)
  } catch {
    // Private mode: the choice still applies for this session.
  }
  emit()
}

function subscribe(listener: () => void) {
  listeners.add(listener)
  return () => listeners.delete(listener)
}

/** The user's choice (System / Light / Dark) and the theme actually in effect. */
export function useTheme() {
  const pref = useSyncExternalStore(
    subscribe,
    () => preference,
    () => 'system' as ThemePreference,
  )
  const resolved = useSyncExternalStore(
    subscribe,
    () => resolve(preference),
    () => 'light' as ResolvedTheme,
  )
  return { preference: pref, resolved, setPreference: setThemePreference }
}
