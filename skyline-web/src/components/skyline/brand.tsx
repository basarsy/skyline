import * as React from 'react'
import { Link } from 'react-router'

import { cn } from '@/lib/cn'

/** A small custom monochrome plane glyph (not an icon-set plane), in currentColor. */
export function PlaneGlyph({ className, ...props }: React.ComponentProps<'svg'>) {
  return (
    <svg viewBox="0 0 24 24" fill="currentColor" aria-hidden className={cn('size-5', className)} {...props}>
      <path d="M2.5 13.2 21.5 4.6c.6-.3 1.2.3.9.9l-5.2 15c-.2.6-1 .7-1.4.2l-3.6-4.4-3 2.7c-.4.3-1 0-.9-.5l.5-4.1-5.9-.4c-.6 0-.8-.8-.4-1.1Z" />
    </svg>
  )
}

/**
 * The Skyline mark: a globe crossed by two rising flight paths, in currentColor. The paths are cut out of the
 * disc with a mask, so the mark works on any background. public/favicon.svg is the same drawing.
 */
export function SkylineMark({ className, ...props }: React.ComponentProps<'svg'>) {
  const maskId = `skyline-mark-${React.useId().replace(/:/g, '')}`
  return (
    <svg viewBox="0 0 24 24" aria-hidden className={cn('size-5', className)} {...props}>
      <defs>
        <mask id={maskId}>
          <rect width="24" height="24" fill="white" />
          <path d="M-1 17C6 15 13 11 25 4" stroke="black" strokeWidth="2.4" strokeLinecap="round" fill="none" />
          <path d="M-1 22C7 20 15 16 25 9" stroke="black" strokeWidth="2.4" strokeLinecap="round" fill="none" />
        </mask>
      </defs>
      <circle cx="12" cy="12" r="9.5" fill="currentColor" mask={`url(#${maskId})`} />
    </svg>
  )
}

/** The wordmark: the mark on a brand tile, then "Skyline". Inside a band, the tile turns white (on-band). */
export function Wordmark({ className, to = '/' }: { className?: string; to?: string }) {
  return (
    <Link
      to={to}
      className={cn('inline-flex items-center gap-2.5 rounded-md py-2 pr-2 text-foreground focus-ring', className)}
      aria-label="Skyline home"
    >
      <span className="grid size-8 place-items-center rounded-lg bg-primary text-primary-foreground">
        <SkylineMark className="size-6" />
      </span>
      <span className="font-display text-[1.375rem] leading-none font-bold tracking-[-0.02em]">Skyline</span>
    </Link>
  )
}
