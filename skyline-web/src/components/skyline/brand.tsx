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

/** The wordmark: "skyline" in lowercase, bold Inter Tight, with the glyph. */
export function Wordmark({ className, to = '/' }: { className?: string; to?: string }) {
  return (
    <Link
      to={to}
      className={cn('inline-flex items-center gap-2 rounded-full py-2 pr-2 text-foreground focus-ring', className)}
      aria-label="Skyline home"
    >
      <span className="grid size-8 place-items-center rounded-[10px] bg-primary text-primary-foreground">
        <PlaneGlyph className="size-[18px]" />
      </span>
      <span className="font-display text-[1.375rem] leading-none font-bold tracking-[-0.04em]">skyline</span>
    </Link>
  )
}
