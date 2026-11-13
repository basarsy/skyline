import { api, command } from '../client'
import { parsePage } from '../page'
import type { CreateUserRequest, PageParams, UserResponse, UserRole } from '../types'

export interface UserSearchParams extends PageParams {
  email?: string
  role?: UserRole
}

/** skyline-auth: account administration (ADMIN). */
export const usersApi = {
  search: async (params: UserSearchParams, signal?: AbortSignal) =>
    parsePage<UserResponse>(await api.get('/users', { query: { ...params }, signal })),
  create: (body: CreateUserRequest) => command.post<UserResponse>('/users', body),
}
