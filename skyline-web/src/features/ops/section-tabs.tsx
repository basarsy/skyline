import { NavLink } from 'react-router'

import { cn } from '@/lib/cn'

/** Pill tabs between sibling ops pages (Aircraft / Aircraft types, Airports / Routes). Real links: deep-linkable. */
export function SectionTabs({ label, tabs }: { label: string; tabs: { to: string; label: string }[] }) {
  return (
    <nav aria-label={label} className="mb-6 inline-flex rounded-lg bg-elevated p-1">
      {tabs.map((tab) => (
        <NavLink
          key={tab.to}
          to={tab.to}
          end
          className={({ isActive }) =>
            cn(
              'inline-flex h-10 items-center rounded-md px-4 text-sm font-medium focus-ring transition-colors duration-200',
              isActive ? 'bg-primary text-primary-foreground' : 'text-muted-foreground hover:text-foreground',
            )
          }
        >
          {tab.label}
        </NavLink>
      ))}
    </nav>
  )
}

export const FLEET_TABS = [
  { to: '/ops/fleet', label: 'Aircraft' },
  { to: '/ops/fleet/types', label: 'Aircraft types' },
]

export const NETWORK_TABS = [
  { to: '/ops/network/airports', label: 'Airports' },
  { to: '/ops/network/routes', label: 'Routes' },
]
