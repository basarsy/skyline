import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { notificationsApi } from '@/api/endpoints/notifications'
import { notificationKeys } from '@/api/query-keys'

/** Polled once a minute (and on focus): flight changes arrive asynchronously. */
const POLL_MS = 60_000

export function useUnreadCount(enabled: boolean) {
  return useQuery({
    queryKey: notificationKeys.unread,
    queryFn: ({ signal }) => notificationsApi.unreadCount(signal),
    select: (data) => data.count,
    enabled,
    refetchInterval: POLL_MS,
    refetchOnWindowFocus: true,
  })
}

/** The latest entries, loaded when the inbox is opened. */
export function useNotifications(enabled: boolean) {
  return useQuery({
    queryKey: notificationKeys.list,
    queryFn: ({ signal }) => notificationsApi.list({ size: 20 }, signal),
    enabled,
    refetchOnWindowFocus: true,
  })
}

export function useMarkRead() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => notificationsApi.markRead(id),
    onSettled: () => queryClient.invalidateQueries({ queryKey: notificationKeys.all }),
  })
}

export function useMarkAllRead() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => notificationsApi.markAllRead(),
    onSettled: () => queryClient.invalidateQueries({ queryKey: notificationKeys.all }),
  })
}
