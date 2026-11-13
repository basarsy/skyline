import { Monitor, Moon, Sun } from 'lucide-react'

import { Button } from '@/components/ui/button'
import { useTheme, type ThemePreference } from '@/lib/theme'

export const THEME_OPTIONS: { value: ThemePreference; label: string; Icon: typeof Sun }[] = [
  { value: 'system', label: 'System', Icon: Monitor },
  { value: 'light', label: 'Light', Icon: Sun },
  { value: 'dark', label: 'Dark', Icon: Moon },
]

/**
 * Theme control for visitors (logged-in users get the full choice in the account menu): one button that
 * cycles System → Light → Dark and says what it's set to.
 */
export function ThemeToggle() {
  const { preference, setPreference } = useTheme()
  const index = THEME_OPTIONS.findIndex((option) => option.value === preference)
  const current = THEME_OPTIONS[index] ?? THEME_OPTIONS[0]!
  const next = THEME_OPTIONS[(index + 1) % THEME_OPTIONS.length]!
  return (
    <Button
      variant="ghost"
      size="icon"
      aria-label={`Theme: ${current.label}. Switch to ${next.label.toLowerCase()}`}
      title={`Theme: ${current.label}`}
      onClick={() => setPreference(next.value)}
    >
      <current.Icon strokeWidth={1.75} aria-hidden />
    </Button>
  )
}
