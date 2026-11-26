import * as React from 'react'
import { formatDistanceToNowStrict } from 'date-fns'
import { Bell, CalendarClock, CheckCheck, Clock, DoorOpen, Hash, PlaneTakeoff, Ticket, XCircle } from 'lucide-react'
import { useNavigate } from 'react-router'

import type { NotificationResponse } from '@/api/types'
import { Button } from '@/components/ui/button'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import { cn } from '@/lib/cn'
import { parseUtc } from '@/lib/format'
import { useMarkAllRead, useMarkRead, useNotifications, useUnreadCount } from './queries'

const ICONS: Record<string, typeof Bell> = {
  FLIGHT_DELAYED: Clock,
  FLIGHT_ON_TIME: Clock,
  FLIGHT_RESCHEDULED: CalendarClock,
  FLIGHT_GATE: DoorOpen,
  FLIGHT_CANCELLED: XCircle,
  FLIGHT_BOARDING: PlaneTakeoff,
  FLIGHT_NUMBER_CHANGED: Hash,
  BOOKING_CONFIRMED: Ticket,
  BOOKING_CANCELLED: XCircle,
  CHECK_IN_OPEN: Ticket,
}

function Entry({ notification, onOpen }: { notification: NotificationResponse; onOpen: () => void }) {
  const Icon = ICONS[notification.kind] ?? Bell
  return (
    <li>
      <button
        type="button"
        onClick={onOpen}
        className={cn(
          'flex w-full cursor-pointer gap-3 rounded-2xl p-3 text-left focus-ring outline-none hover:bg-elevated',
          !notification.read && 'bg-elevated/60',
        )}
      >
        <Icon
          className={cn(
            'mt-0.5 size-4 shrink-0',
            notification.kind === 'FLIGHT_CANCELLED' || notification.kind === 'FLIGHT_DELAYED'
              ? 'text-warning'
              : 'text-muted-foreground',
          )}
          strokeWidth={1.75}
          aria-hidden
        />
        <span className="flex min-w-0 flex-1 flex-col gap-0.5">
          <span className={cn('text-sm', !notification.read && 'font-semibold')}>
            {notification.title}
            {!notification.read && <span className="sr-only"> (unread)</span>}
          </span>
          <span className="text-sm text-muted-foreground">{notification.body}</span>
          <span className="text-caption text-muted-foreground">
            {formatDistanceToNowStrict(parseUtc(notification.createdAt), { addSuffix: true })}
          </span>
        </span>
        {!notification.read && <span className="mt-1.5 size-2 shrink-0 rounded-full bg-primary" aria-hidden />}
      </button>
    </li>
  )
}

/**
 * The inbox: a bell with the unread count, opening the latest notifications. Opening one marks it read and
 * goes to the page it is about (the trip, or check-in).
 */
export function NotificationBell({ className, align = 'end' }: { className?: string; align?: 'start' | 'end' }) {
  const [open, setOpen] = React.useState(false)
  const navigate = useNavigate()
  const unread = useUnreadCount(true)
  const list = useNotifications(open)
  const markRead = useMarkRead()
  const markAllRead = useMarkAllRead()
  const count = unread.data ?? 0
  const items = list.data?.content ?? []

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <button
          type="button"
          aria-label={count > 0 ? `Notifications, ${count} unread` : 'Notifications'}
          className={cn(
            'relative grid size-11 cursor-pointer place-items-center rounded-full focus-ring outline-none',
            className,
          )}
        >
          <Bell className="size-5" strokeWidth={1.75} aria-hidden />
          {count > 0 && (
            <span
              aria-hidden
              className="absolute top-1.5 right-1.5 grid min-w-4.5 place-items-center rounded-full bg-destructive px-1 text-[0.625rem] leading-4.5 font-semibold text-white tabular"
            >
              {count > 99 ? '99+' : count}
            </span>
          )}
        </button>
      </PopoverTrigger>
      <PopoverContent
        align={align}
        collisionPadding={16}
        className="flex w-[min(24rem,calc(100vw-2rem))] flex-col gap-2 p-2"
      >
        <div className="flex items-center justify-between gap-2 px-2 pt-1">
          <h2 className="font-semibold">Notifications</h2>
          <Button
            variant="ghost"
            size="sm"
            disabled={count === 0}
            loading={markAllRead.isPending}
            onClick={() => markAllRead.mutate()}
          >
            <CheckCheck strokeWidth={1.75} aria-hidden />
            Mark all read
          </Button>
        </div>
        {list.isPending ? (
          <p className="px-3 py-6 text-center text-sm text-muted-foreground">Loading…</p>
        ) : list.isError ? (
          <p role="alert" className="px-3 py-6 text-center text-sm text-muted-foreground">
            We couldn’t load your notifications.
          </p>
        ) : items.length === 0 ? (
          <p className="px-3 py-6 text-center text-sm text-muted-foreground">
            Nothing yet. Booking confirmations and flight changes show up here.
          </p>
        ) : (
          <ul aria-label="Latest notifications" className="flex max-h-[min(28rem,70vh)] flex-col gap-1 overflow-y-auto">
            {items.map((notification) => (
              <Entry
                key={notification.id}
                notification={notification}
                onOpen={() => {
                  if (!notification.read) markRead.mutate(notification.id)
                  setOpen(false)
                  if (notification.link) navigate(notification.link)
                }}
              />
            ))}
          </ul>
        )}
      </PopoverContent>
    </Popover>
  )
}
