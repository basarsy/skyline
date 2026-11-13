import * as React from 'react'
import { Globe2, Menu, Plane, PlaneTakeoff, Users, UsersRound } from 'lucide-react'
import { Link, NavLink, Outlet, useLocation, useMatches } from 'react-router'

import { useAuth } from '@/auth/use-auth'
import { Wordmark } from '@/components/skyline/brand'
import {
  Breadcrumb,
  BreadcrumbItem,
  BreadcrumbLink,
  BreadcrumbList,
  BreadcrumbPage,
  BreadcrumbSeparator,
} from '@/components/ui/breadcrumb'
import { Button } from '@/components/ui/button'
import { Sheet, SheetContent, SheetDescription, SheetTitle } from '@/components/ui/sheet'
import { cn } from '@/lib/cn'
import { UserMenu } from './user-menu'

/** Route handle that names a breadcrumb; a function receives the route params. */
export interface Crumb {
  crumb: string | ((params: Record<string, string | undefined>) => string)
}

const SECTIONS = [
  { to: '/ops/flights', label: 'Flights', Icon: PlaneTakeoff, adminOnly: false },
  { to: '/ops/fleet', label: 'Fleet', Icon: Plane, adminOnly: false },
  { to: '/ops/network/airports', match: '/ops/network', label: 'Network', Icon: Globe2, adminOnly: false },
  { to: '/ops/crew', label: 'Crew', Icon: UsersRound, adminOnly: false },
  { to: '/ops/users', label: 'Users', Icon: Users, adminOnly: true },
] as const

function SidebarNav({ onNavigate }: { onNavigate?: () => void }) {
  const { isAdmin } = useAuth()
  const { pathname } = useLocation()
  return (
    <nav aria-label="Ops console" className="flex flex-col gap-1">
      {SECTIONS.filter((section) => !section.adminOnly || isAdmin).map(({ to, label, Icon, ...rest }) => {
        const active = pathname.startsWith('match' in rest ? rest.match : to)
        return (
          <NavLink
            key={to}
            to={to}
            onClick={onNavigate}
            aria-current={active ? 'page' : undefined}
            className={cn(
              'flex h-11 items-center gap-3 rounded-md px-4 text-sm font-medium focus-ring transition-colors duration-200 outline-none',
              active
                ? 'bg-primary text-primary-foreground'
                : 'text-muted-foreground hover:bg-elevated hover:text-foreground',
            )}
          >
            <Icon className="size-[18px]" strokeWidth={1.75} aria-hidden />
            {label}
          </NavLink>
        )
      })}
    </nav>
  )
}

function Breadcrumbs() {
  const matches = useMatches()
  const crumbs = matches
    .filter((match) => (match.handle as Crumb | undefined)?.crumb)
    .map((match) => {
      const { crumb } = match.handle as Crumb
      return {
        to: match.pathname,
        label: typeof crumb === 'function' ? crumb(match.params) : crumb,
      }
    })
  return (
    <Breadcrumb>
      <BreadcrumbList>
        {crumbs.map((crumb, index) => (
          <React.Fragment key={crumb.to}>
            {index > 0 && <BreadcrumbSeparator />}
            <BreadcrumbItem>
              {index === crumbs.length - 1 ? (
                <BreadcrumbPage className="font-medium">{crumb.label}</BreadcrumbPage>
              ) : (
                <BreadcrumbLink asChild>
                  <Link to={crumb.to}>{crumb.label}</Link>
                </BreadcrumbLink>
              )}
            </BreadcrumbItem>
          </React.Fragment>
        ))}
      </BreadcrumbList>
    </Breadcrumb>
  )
}

/** Ops console: 264px sidebar (a sheet below 1024px), breadcrumbs and the user menu. Full width. */
export function OpsShell() {
  const [navOpen, setNavOpen] = React.useState(false)
  return (
    <div className="flex min-h-dvh">
      <a
        href="#ops-main"
        className="sr-only z-50 rounded-full bg-primary px-4 py-2 text-primary-foreground focus:not-sr-only focus:fixed focus:top-3 focus:left-3"
      >
        Skip to content
      </a>
      <aside className="sticky top-0 hidden h-dvh w-[264px] shrink-0 flex-col gap-8 border-r border-border bg-card p-5 lg:flex">
        <Wordmark to="/ops/flights" />
        <SidebarNav />
        <Link
          to="/"
          className="mt-auto rounded-full px-4 py-2 text-sm text-muted-foreground focus-ring hover:text-foreground"
        >
          Back to the public site
        </Link>
      </aside>

      <Sheet open={navOpen} onOpenChange={setNavOpen}>
        <SheetContent side="left" className="w-[280px] gap-8 p-5">
          <SheetTitle className="sr-only">Ops console navigation</SheetTitle>
          <SheetDescription className="sr-only">Sections of the ops console</SheetDescription>
          <Wordmark to="/ops/flights" />
          <SidebarNav onNavigate={() => setNavOpen(false)} />
        </SheetContent>
      </Sheet>

      <div className="flex min-w-0 flex-1 flex-col">
        <header className="sticky top-0 z-30 flex h-16 items-center gap-3 border-b border-border frosted px-4 sm:px-6">
          <Button
            variant="ghost"
            size="icon"
            className="lg:hidden"
            aria-label="Open navigation"
            onClick={() => setNavOpen(true)}
          >
            <Menu strokeWidth={1.75} aria-hidden />
          </Button>
          <div className="min-w-0 flex-1 overflow-hidden">
            <Breadcrumbs />
          </div>
          <UserMenu />
        </header>
        <main id="ops-main" tabIndex={-1} className="flex-1 px-4 py-6 outline-none sm:px-6 lg:py-8">
          <Outlet />
        </main>
      </div>
    </div>
  )
}

/** Ops page header: big title, optional subtitle, actions on the right. */
export function OpsPageHeader({
  title,
  description,
  actions,
  children,
}: {
  title: React.ReactNode
  description?: React.ReactNode
  actions?: React.ReactNode
  children?: React.ReactNode
}) {
  return (
    <div className="mb-6 flex flex-col gap-4 sm:mb-8 sm:flex-row sm:items-end sm:justify-between">
      <div className="flex min-w-0 flex-col gap-1.5">
        <h1 className="text-h2 font-semibold sm:text-h1">{title}</h1>
        {description && <p className="text-sm text-muted-foreground">{description}</p>}
        {children}
      </div>
      {actions && <div className="flex flex-wrap gap-3">{actions}</div>}
    </div>
  )
}
