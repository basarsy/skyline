import { LayoutDashboard, LogIn, LogOut, UserPlus } from 'lucide-react'
import { useNavigate } from 'react-router'

import { useAuth } from '@/auth/use-auth'
import { ResponsiveSheet } from '@/components/skyline/responsive-sheet'
import { SegmentedControl } from '@/components/skyline/segmented-control'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { userRoleLabel } from '@/lib/labels'
import { useTheme, type ThemePreference } from '@/lib/theme'
import { Avatar, THEME_OPTIONS } from './user-menu'

/** Account sheet behind the mobile tab bar's fourth tab. */
export default function AccountSheet({ open, onOpenChange }: { open: boolean; onOpenChange: (open: boolean) => void }) {
  const { user, isStaff, logout } = useAuth()
  const { preference, setPreference } = useTheme()
  const navigate = useNavigate()
  const go = (to: string) => {
    onOpenChange(false)
    navigate(to)
  }
  return (
    <ResponsiveSheet open={open} onOpenChange={onOpenChange} title="Account">
      <div className="flex flex-col gap-6">
        {user ? (
          <div className="flex items-center gap-3">
            <Avatar email={user.email} className="size-11" />
            <div className="flex min-w-0 flex-col gap-1">
              <span className="truncate font-medium">{user.email}</span>
              <Badge variant="outline">{userRoleLabel[user.role]}</Badge>
            </div>
          </div>
        ) : (
          <div className="grid grid-cols-2 gap-3">
            <Button onClick={() => go('/login')}>
              <LogIn strokeWidth={1.75} aria-hidden />
              Log in
            </Button>
            <Button variant="outline" onClick={() => go('/register')}>
              <UserPlus strokeWidth={1.75} aria-hidden />
              Sign up
            </Button>
          </div>
        )}
        <div className="flex flex-col gap-3">
          <span className="micro-label">Theme</span>
          <SegmentedControl
            label="Theme"
            value={preference}
            onValueChange={(value) => setPreference(value as ThemePreference)}
            options={THEME_OPTIONS.map(({ value, label }) => ({ value, label }))}
            className="w-full"
          />
        </div>
        {user && (
          <div className="flex flex-col gap-3">
            {isStaff && (
              <Button variant="outline" onClick={() => go('/ops/flights')}>
                <LayoutDashboard strokeWidth={1.75} aria-hidden />
                Ops console
              </Button>
            )}
            <Button
              variant="outline"
              onClick={() => {
                logout()
                go('/')
              }}
            >
              <LogOut strokeWidth={1.75} aria-hidden />
              Log out
            </Button>
          </div>
        )}
      </div>
    </ResponsiveSheet>
  )
}
