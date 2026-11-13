import type * as React from 'react'
import { motion } from 'motion/react'
import { CloudOff, Lock, RotateCw, SearchX } from 'lucide-react'
import { Link } from 'react-router'

import { isApiError } from '@/api/http'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { cn } from '@/lib/cn'

interface StateProps {
  icon?: React.ReactNode
  title: string
  description?: React.ReactNode
  action?: React.ReactNode
  className?: string
}

function StateFrame({ icon, title, description, action, className }: StateProps) {
  return (
    <div
      className={cn(
        'flex flex-col items-center gap-4 rounded-card border border-dashed border-border px-6 py-14 text-center',
        className,
      )}
    >
      {icon && (
        <span className="grid size-12 place-items-center rounded-full bg-elevated text-foreground [&_svg]:size-5">
          {icon}
        </span>
      )}
      <div className="flex max-w-sm flex-col gap-1.5">
        <h2 className="text-h3 font-semibold">{title}</h2>
        {description && <div className="text-sm text-muted-foreground">{description}</div>}
      </div>
      {action && <div className="mt-2 flex flex-wrap justify-center gap-3">{action}</div>}
    </div>
  )
}

/** A designed empty state with an optional next step. */
export function EmptyState({ icon = <SearchX strokeWidth={1.75} aria-hidden />, ...props }: StateProps) {
  return <StateFrame icon={icon} {...props} />
}

/** An error state that shows the server's message and offers a retry. */
export function ErrorState({
  error,
  onRetry,
  title = 'Something went wrong',
  className,
}: {
  error: unknown
  onRetry?: () => void
  title?: string
  className?: string
}) {
  const message = isApiError(error) || error instanceof Error ? error.message : 'Please try again.'
  return (
    <div role="alert">
      <StateFrame
        className={className}
        icon={<CloudOff strokeWidth={1.75} aria-hidden />}
        title={title}
        description={message}
        action={
          onRetry && (
            <Button variant="outline" onClick={onRetry}>
              <RotateCw strokeWidth={1.75} aria-hidden />
              Try again
            </Button>
          )
        }
      />
    </div>
  )
}

/** Full-page "no access" (403) with a way back. */
export function ForbiddenState({ className }: { className?: string }) {
  return (
    <StateFrame
      className={cn('mx-auto my-16 max-w-xl', className)}
      icon={<Lock strokeWidth={1.75} aria-hidden />}
      title="You don't have access"
      description="This area needs a different account. Go back, or log in with an account that has access."
      action={
        <>
          <Button asChild variant="outline">
            <Link to="/">Back to home</Link>
          </Button>
          <Button asChild>
            <Link to="/login">Switch account</Link>
          </Button>
        </>
      }
    />
  )
}

/** A card-shaped skeleton that reserves its final height (no layout shift when data arrives). */
export function SkeletonCard({ className, lines = 2 }: { className?: string; lines?: number }) {
  return (
    <div
      aria-hidden
      className={cn('flex flex-col gap-4 rounded-card border border-border bg-card p-5 sm:p-6', className)}
    >
      <div className="flex items-center justify-between">
        <Skeleton className="h-9 w-40" />
        <Skeleton className="h-9 w-24" />
      </div>
      {Array.from({ length: lines }, (_, i) => (
        <Skeleton key={i} className={cn('h-4', i % 2 ? 'w-1/2' : 'w-3/4')} />
      ))}
    </div>
  )
}

/** Announces loading to assistive tech while skeletons show. */
export function LoadingRegion({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div role="status" aria-live="polite" aria-busy="true">
      <span className="sr-only">{label}</span>
      {children}
    </div>
  )
}

/** List entries fade/slide in with at most a 40ms stagger; reduced motion renders the final state. */
export function Stagger({
  index,
  children,
  className,
}: {
  index: number
  children: React.ReactNode
  className?: string
}) {
  return (
    <motion.div
      className={className}
      initial={{ opacity: 0, y: 8 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ type: 'spring', stiffness: 420, damping: 36, delay: Math.min(index, 10) * 0.04 }}
    >
      {children}
    </motion.div>
  )
}
