import type { UserRole } from '@/api/types'
import { useAuthState } from './auth-store'
import { login, logout, register } from './session'

/** The session as components see it. */
export function useAuth() {
  const state = useAuthState()
  const role = state.user?.role
  return {
    status: state.status,
    user: state.user,
    isAuthenticated: state.status === 'authenticated',
    isLoading: state.status === 'loading',
    role,
    isStaff: role === 'STAFF' || role === 'ADMIN',
    isAdmin: role === 'ADMIN',
    isPassenger: role === 'PASSENGER',
    hasRole: (...roles: UserRole[]) => !!role && roles.includes(role),
    login,
    register,
    logout,
  }
}
