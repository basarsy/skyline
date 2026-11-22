import { api, command } from '../client'
import { parsePage } from '../page'
import type { NotificationResponse, PageParams } from '../types'

/** skyline-notification: the signed-in user's inbox. */
export const notificationsApi = {
  list: async (params: PageParams = {}, signal?: AbortSignal) =>
    parsePage<NotificationResponse>(await api.get('/notifications', { query: { ...params }, signal })),
  unreadCount: (signal?: AbortSignal) => api.get<{ count: number }>('/notifications/unread-count', { signal }),
  markRead: (id: string) => command.post<null>(`/notifications/${id}/read`),
  markAllRead: () => command.post<{ count: number }>('/notifications/read-all'),
}
