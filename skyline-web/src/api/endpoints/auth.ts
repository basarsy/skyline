import { api } from '../client'
import { rawRequest } from '../http'
import type { AuthResponse, LoginRequest, RegisterRequest, UserResponse } from '../types'

/** skyline-auth: accounts and tokens. */
export const authApi = {
  register: (body: RegisterRequest) => rawRequest<UserResponse>('/auth/register', { method: 'POST', body }),
  login: (body: LoginRequest) => rawRequest<AuthResponse>('/auth/login', { method: 'POST', body }),
  me: (signal?: AbortSignal) => api.get<UserResponse>('/auth/me', { signal }),
}
