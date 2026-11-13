import * as React from 'react'
import { CircleUser, Plane, Search, Ticket } from 'lucide-react'
import { Link, NavLink, Outlet } from 'react-router'

import { useAuth } from '@/auth/use-auth'
import { Wordmark } from '@/components/skyline/brand'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/cn'
import { ThemeToggle } from './theme-toggle'

/** The account menu (Radix dropdown) loads only for logged-in users. */
const UserMenu = React.lazy(() => import('./user-menu').then((module) => ({ default: module.UserMenu })))

const navLinkClass = ({ isActive }: { isActive: boolean }) =>
  cn(
    'inline-flex h-11 items-center rounded-full px-4 text-sm font-medium focus-ring transition-colors duration-200 outline-none',
    isActive ? 'bg-brand-soft text-primary' : 'text-muted-foreground hover:bg-elevated hover:text-foreground',
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
            <React.Suspense fallback={<span className="size-11" aria-hidden />}>
              <UserMenu />
            </React.Suspense>
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
    isActive ? 'text-primary' : 'text-muted-foreground',
  )

/** Loaded the first time the Account tab is opened (it pulls in the drawer). */
const AccountSheet = React.lazy(() => import('./account-sheet'))

/** Frosted bottom tab bar on mobile: Book / Trips / Check-in / Account. */
function MobileTabBar() {
  const [accountOpen, setAccountOpen] = React.useState(false)
  const [accountRequested, setAccountRequested] = React.useState(false)
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
            onClick={() => {
              setAccountRequested(true)
              setAccountOpen(true)
            }}
            className={tabClass({ isActive: accountOpen })}
            aria-haspopup="dialog"
          >
            <CircleUser {...iconProps} />
            Account
          </button>
        </div>
      </nav>
      {accountRequested && (
        <React.Suspense fallback={null}>
          <AccountSheet open={accountOpen} onOpenChange={setAccountOpen} />
        </React.Suspense>
      )}
    </>
  )
}

const footerLink = 'w-fit rounded focus-ring transition-colors duration-200 hover:text-foreground'

function Footer() {
  return (
    <footer className="border-t border-border bg-card">
      <div className="mx-auto flex max-w-[1200px] flex-col gap-10 px-4 py-12 text-sm sm:px-6">
        <div className="flex flex-col gap-10 sm:flex-row sm:justify-between">
          <div className="flex max-w-xs flex-col gap-3">
            <Wordmark />
            <p className="text-muted-foreground">Search, book and check in for flights across our network.</p>
          </div>
          <nav aria-label="Footer" className="grid grid-cols-2 gap-x-16 gap-y-3 text-muted-foreground">
            <span className="font-semibold text-foreground">Travel</span>
            <span className="font-semibold text-foreground">Account</span>
            <Link to="/" className={footerLink}>
              Book a flight
            </Link>
            <Link to="/trips" className={footerLink}>
              My trips
            </Link>
            <Link to="/manage" className={footerLink}>
              Manage booking
            </Link>
            <Link to="/login" className={footerLink}>
              Log in
            </Link>
            <Link to="/checkin" className={footerLink}>
              Online check-in
            </Link>
            <Link to="/register" className={footerLink}>
              Create an account
            </Link>
          </nav>
        </div>
        <div className="border-t border-border pt-6 text-muted-foreground">© {new Date().getFullYear()} Skyline</div>
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

/**
 * A short task page (log in, manage booking, check-in): a night-sky band with the title, and the content
 * (usually one form card) overlapping its bottom edge.
 */
export function SkyPage({
  icon,
  title,
  subtitle,
  width = 'max-w-[560px]',
  children,
}: {
  icon?: React.ReactNode
  title: string
  subtitle?: React.ReactNode
  /** Max width of the title and content column. */
  width?: string
  children: React.ReactNode
}) {
  return (
    <>
      <section className="relative isolate overflow-hidden sky-band [--ring:var(--sky-foreground)]">
        <div className={cn('mx-auto flex w-full flex-col gap-3 px-4 pt-12 pb-28 sm:pt-16 sm:pb-32', width)}>
          {icon && (
            <span className="mb-1 grid size-12 place-items-center rounded-2xl bg-sky-line text-sky-foreground [&_svg]:size-[22px]">
              {icon}
            </span>
          )}
          <h1 className="text-h1 font-semibold tracking-[-0.03em]">{title}</h1>
          {subtitle && <div className="text-sky-muted">{subtitle}</div>}
        </div>
      </section>
      <div className={cn('relative mx-auto -mt-20 flex w-full flex-col gap-6 px-4 pb-16 sm:pb-24', width)}>
        {children}
      </div>
    </>
  )
}

/** Standard page container: 1200px max, 16px gutter on mobile. */
export function Page({ children, className }: { children: React.ReactNode; className?: string }) {
  return <div className={cn('mx-auto w-full max-w-[1200px] px-4 py-8 sm:px-6 sm:py-12', className)}>{children}</div>
}
