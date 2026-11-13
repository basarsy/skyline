import * as React from 'react'

import { cn } from '@/lib/cn'
import { useNow } from '@/lib/hooks'

function parts(ms: number) {
  const total = Math.max(0, Math.floor(ms / 1000))
  return {
    days: Math.floor(total / 86400),
    hours: Math.floor((total % 86400) / 3600),
    minutes: Math.floor((total % 3600) / 60),
    seconds: total % 60,
  }
}

const pad = (n: number) => String(n).padStart(2, '0')

/** Live countdown to a UTC instant; correct in any browser zone. */
export function CountdownTimer({
  target,
  size = 'inline',
  onElapsed,
  className,
}: {
  target: Date
  size?: 'hero' | 'inline'
  onElapsed?: () => void
  className?: string
}) {
  const now = useNow(1000)
  const remaining = target.getTime() - now.getTime()
  const elapsed = remaining <= 0
  React.useEffect(() => {
    if (elapsed) onElapsed?.()
  }, [elapsed, onElapsed])
  const { days, hours, minutes, seconds } = parts(remaining)

  if (size === 'inline') {
    const text = days > 0 ? `${days}d ${hours}h ${pad(minutes)}m` : `${hours}h ${pad(minutes)}m ${pad(seconds)}s`
    return <span className={cn('font-medium tabular', className)}>{text}</span>
  }

  const units = [
    { label: 'Days', value: days },
    { label: 'Hours', value: hours },
    { label: 'Min', value: minutes },
    { label: 'Sec', value: seconds },
  ]
  return (
    <div className={cn('flex gap-3 sm:gap-5', className)} role="timer" aria-live="off">
      {units.map((unit) => (
        <div key={unit.label} className="flex min-w-16 flex-col items-center gap-2 rounded-2xl bg-elevated px-3 py-4">
          <span className="hero-number text-h1 sm:text-[2.75rem]">{pad(unit.value)}</span>
          <span className="micro-label">{unit.label}</span>
        </div>
      ))}
      <span className="sr-only">
        {days} days, {hours} hours and {minutes} minutes remaining
      </span>
    </div>
  )
}
