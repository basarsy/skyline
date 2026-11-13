import { LayoutDashboard, LogOut, Plane } from 'lucide-react'
import { Link, useNavigate } from 'react-router'

import { useAuth } from '@/auth/use-auth'
import { Badge } from '@/components/ui/badge'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuRadioGroup,
  DropdownMenuRadioItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { cn } from '@/lib/cn'
import { userRoleLabel } from '@/lib/labels'
import { useTheme, type ThemePreference } from '@/lib/theme'
import { THEME_OPTIONS } from './theme-toggle'

export { THEME_OPTIONS }

const icon = { strokeWidth: 1.75, 'aria-hidden': true, className: 'size-4' } as const

/** Two-letter avatar from the email. */
export function Avatar({ email, className }: { email: string; className?: string }) {
  return (
    <span
      aria-hidden
      data-initials={email.slice(0, 2)}
      // Initials come from CSS so they aren't text competing with the trigger's accessible name.
      className={cn(
        'grid size-9 place-items-center rounded-full bg-primary text-xs font-semibold text-primary-foreground uppercase before:content-[attr(data-initials)]',
        className,
      )}
    />
  )
}

/** Avatar menu: account, role, theme, ops console or trips, log out. */
export function UserMenu({ align = 'end' }: { align?: 'start' | 'end' }) {
  const { user, isStaff, isPassenger, logout } = useAuth()
  const { preference, setPreference } = useTheme()
  const navigate = useNavigate()
  if (!user) return null

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <button
          type="button"
          aria-label={`Account menu for ${user.email}`}
          className="grid size-11 cursor-pointer place-items-center rounded-full focus-ring outline-none"
        >
          <Avatar email={user.email} />
        </button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align={align} className="w-64">
        <div className="flex flex-col gap-2 px-2 py-2">
          <span className="truncate text-sm font-medium">{user.email}</span>
          <Badge variant="outline">{userRoleLabel[user.role]}</Badge>
        </div>
        <DropdownMenuSeparator />
        {isStaff && (
          <DropdownMenuItem asChild className="min-h-10 gap-2 rounded-xl">
            <Link to="/ops/flights">
              <LayoutDashboard {...icon} />
              Ops console
            </Link>
          </DropdownMenuItem>
        )}
        {isPassenger && (
          <DropdownMenuItem asChild className="min-h-10 gap-2 rounded-xl">
            <Link to="/trips">
              <Plane {...icon} />
              My trips
            </Link>
          </DropdownMenuItem>
        )}
        <DropdownMenuSeparator />
        <DropdownMenuLabel className="micro-label">Theme</DropdownMenuLabel>
        <DropdownMenuRadioGroup value={preference} onValueChange={(v) => setPreference(v as ThemePreference)}>
          {THEME_OPTIONS.map(({ value, label, Icon }) => (
            <DropdownMenuRadioItem key={value} value={value} className="min-h-10 gap-2 rounded-xl">
              <Icon {...icon} />
              {label}
            </DropdownMenuRadioItem>
          ))}
        </DropdownMenuRadioGroup>
        <DropdownMenuSeparator />
        <DropdownMenuItem
          className="min-h-10 gap-2 rounded-xl"
          onSelect={() => {
            logout()
            navigate('/')
          }}
        >
          <LogOut {...icon} />
          Log out
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
