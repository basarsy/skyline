import * as React from 'react'
import { CircleUser, LayoutDashboard, LogIn, LogOut, Plane, Search, Ticket, UserPlus } from 'lucide-react'
import { Link, NavLink, Outlet, useNavigate } from 'react-router'

import { useAuth } from '@/auth/use-auth'
import { Wordmark } from '@/components/skyline/brand'
import { ResponsiveSheet } from '@/components/skyline/responsive-sheet'
import { SegmentedControl } from '@/components/skyline/segmented-control'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/cn'
import { userRoleLabel } from '@/lib/labels'
import { useTheme, type ThemePreference } from '@/lib/theme'
import { Avatar, THEME_OPTIONS, ThemeToggle, UserMenu } from './user-menu'

const navLinkClass = ({ isActive }: { isActive: boolean }) =>
  cn(
    'inline-flex h-11 items-center rounded-full px-4 text-sm font-medium focus-ring transition-colors duration-200 outline-none',
    isActive ? 'bg-elevated text-foreground' : 'text-muted-foreground hover:text-foreground',
  )

/** Frosted sticky header with the wordmark, primary nav, and the account controls. */
function PublicHeader() {
  const { isAuthenticated, isPassenger, isLoading } = useAuth()
  return (
    <header className="sticky top-0 z-40 border-b border-border frosted">
      <div className="mx-auto flex h-16 max-w-[1200px] items-center gap-6 px-4 sm:px-6">
        <Wordmark />
        <nav aria-label="Main" className="hidden items-center gap-1 md:flex">
          <NavLink to="/" end className={navLinkClass}>
            Book
          </NavLink>
          <NavLink to="/manage" className={navLinkClass}>
            Manage booking
          </NavLink>
          <NavLink to="/checkin" className={navLinkClass}>
            Check-in
          </NavLink>
          {isPassenger && (
            <NavLink to="/trips" className={navLinkClass}>
              My trips
            </NavLink>
          )}
        </nav>
        <div className="ml-auto flex items-center gap-2">
          {isLoading ? (
            <span className="size-11" aria-hidden />
          ) : isAuthenticated ? (
            <UserMenu />
          ) : (
            <>
              <ThemeToggle />
              <Button asChild size="sm" className="hidden md:inline-flex">
                <Link to="/login">Log in</Link>
              </Button>
            </>
          )}
        </div>
      </div>
    </header>
  )
}

const tabClass = ({ isActive }: { isActive: boolean }) =>
  cn(
    'flex min-h-14 flex-1 flex-col items-center justify-center gap-1 rounded-2xl text-[0.6875rem] font-medium focus-ring outline-none',
    isActive ? 'text-foreground' : 'text-muted-foreground',
  )

/** Account sheet behind the mobile tab bar's fourth tab. */
function AccountSheet({ open, onOpenChange }: { open: boolean; onOpenChange: (open: boolean) => void }) {
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

/** Frosted bottom tab bar on mobile: Book / Trips / Check-in / Account. */
function MobileTabBar() {
  const [accountOpen, setAccountOpen] = React.useState(false)
  const iconProps = { strokeWidth: 1.75, 'aria-hidden': true, className: 'size-[22px]' } as const
  return (
    <>
      <nav
        aria-label="Main"
        className="fixed inset-x-0 bottom-0 z-40 border-t border-border frosted pb-[env(safe-area-inset-bottom)] md:hidden"
      >
        <div className="mx-auto flex max-w-md gap-1 px-2 py-1">
          <NavLink to="/" end className={tabClass}>
            <Search {...iconProps} />
            Book
          </NavLink>
          <NavLink to="/trips" className={tabClass}>
            <Plane {...iconProps} />
            Trips
          </NavLink>
          <NavLink to="/checkin" className={tabClass}>
            <Ticket {...iconProps} />
            Check-in
          </NavLink>
          <button
            type="button"
            onClick={() => setAccountOpen(true)}
            className={tabClass({ isActive: accountOpen })}
            aria-haspopup="dialog"
          >
            <CircleUser {...iconProps} />
            Account
          </button>
        </div>
      </nav>
      <AccountSheet open={accountOpen} onOpenChange={setAccountOpen} />
    </>
  )
}

function Footer() {
  return (
    <footer className="border-t border-border">
      <div className="mx-auto flex max-w-[1200px] flex-col gap-2 px-4 py-10 text-sm text-muted-foreground sm:flex-row sm:items-center sm:justify-between sm:px-6">
        <span>© {new Date().getFullYear()} Skyline</span>
        <nav aria-label="Footer" className="flex gap-6">
          <Link to="/manage" className="rounded focus-ring hover:text-foreground">
            Manage booking
          </Link>
          <Link to="/checkin" className="rounded focus-ring hover:text-foreground">
            Online check-in
          </Link>
        </nav>
      </div>
    </footer>
  )
}

/** Public / passenger shell. */
export function AppShell() {
  return (
    <div className="flex min-h-dvh flex-col">
      <a
        href="#main"
        className="sr-only z-50 rounded-full bg-primary px-4 py-2 text-primary-foreground focus:not-sr-only focus:fixed focus:top-3 focus:left-3"
      >
        Skip to content
      </a>
      <PublicHeader />
      <main id="main" tabIndex={-1} className="flex-1 pb-24 outline-none md:pb-0">
        <Outlet />
      </main>
      <Footer />
      <MobileTabBar />
    </div>
  )
}

/** Standard page container: 1200px max, 16px gutter on mobile. */
export function Page({ children, className }: { children: React.ReactNode; className?: string }) {
  return <div className={cn('mx-auto w-full max-w-[1200px] px-4 py-8 sm:px-6 sm:py-12', className)}>{children}</div>
}
