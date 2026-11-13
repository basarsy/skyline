import { createBrowserRouter, type RouteObject } from 'react-router'

import { RedirectIfAuthenticated, RequireAuth, RequireRole } from '@/auth/guards'
import { AppShell } from '@/components/layout/app-shell'
import { OpsShell, type Crumb } from '@/components/layout/ops-shell'
import { BootFallback, RootLayout } from './root-layout'

/** Every screen is a lazy route module exporting `Component`, so each loads as its own chunk. */
const page = (load: () => Promise<{ Component: React.ComponentType }>): Pick<RouteObject, 'lazy'> => ({
  lazy: async () => ({ Component: (await load()).Component }),
})

const crumb = (value: Crumb['crumb']): Crumb => ({ crumb: value })

const routes: RouteObject[] = [
  {
    element: <RootLayout />,
    // Shown while the first lazy route module loads.
    hydrateFallbackElement: <BootFallback />,
    children: [
      {
        element: <AppShell />,
        children: [
          { index: true, ...page(() => import('@/features/search/home-page')) },
          { path: 'flights', ...page(() => import('@/features/search/results-page')) },
          { path: 'flights/:id', ...page(() => import('@/features/search/flight-detail-page')) },
          { path: 'manage', ...page(() => import('@/features/manage/manage-page')) },
          { path: 'manage/:pnr', ...page(() => import('@/features/manage/manage-result-page')) },
          {
            element: <RedirectIfAuthenticated />,
            children: [
              { path: 'login', ...page(() => import('@/features/auth/login-page')) },
              { path: 'register', ...page(() => import('@/features/auth/register-page')) },
            ],
          },
          {
            element: <RequireRole roles={['PASSENGER']} />,
            children: [
              {
                path: 'book/confirmation/:reservationId',
                ...page(() => import('@/features/booking/confirmation-page')),
              },
              { path: 'book/:flightId', ...page(() => import('@/features/booking/booking-page')) },
              { path: 'trips', ...page(() => import('@/features/trips/trips-page')) },
            ],
          },
          {
            element: <RequireAuth />,
            children: [
              { path: 'trips/:reservationId', ...page(() => import('@/features/trips/trip-detail-page')) },
              { path: 'checkin', ...page(() => import('@/features/checkin/checkin-entry-page')) },
              { path: 'checkin/:pnr', ...page(() => import('@/features/checkin/checkin-flow-page')) },
            ],
          },
          ...(import.meta.env.DEV
            ? [{ path: 'styleguide', ...page(() => import('@/features/styleguide/styleguide-page')) }]
            : []),
          { path: '*', ...page(() => import('@/features/not-found-page')) },
        ],
      },
      {
        path: 'ops',
        element: <RequireRole roles={['STAFF', 'ADMIN']} />,
        children: [
          {
            element: <OpsShell />,
            handle: crumb('Ops'),
            children: [
              {
                path: 'flights',
                handle: crumb('Flights'),
                children: [
                  { index: true, ...page(() => import('@/features/ops/flights/board-page')) },
                  {
                    path: 'new',
                    handle: crumb('New flight'),
                    ...page(() => import('@/features/ops/flights/flight-form-page')),
                  },
                  {
                    path: ':id',
                    handle: crumb('Flight'),
                    children: [
                      { index: true, ...page(() => import('@/features/ops/flights/flight-detail-page')) },
                      {
                        path: 'edit',
                        handle: crumb('Edit'),
                        ...page(() => import('@/features/ops/flights/flight-form-page')),
                      },
                    ],
                  },
                ],
              },
              {
                path: 'fleet',
                handle: crumb('Fleet'),
                children: [
                  { index: true, ...page(() => import('@/features/ops/fleet/aircraft-page')) },
                  {
                    path: 'types',
                    handle: crumb('Aircraft types'),
                    ...page(() => import('@/features/ops/fleet/aircraft-types-page')),
                  },
                ],
              },
              {
                path: 'network',
                handle: crumb('Network'),
                children: [
                  {
                    path: 'airports',
                    handle: crumb('Airports'),
                    ...page(() => import('@/features/ops/network/airports-page')),
                  },
                  {
                    path: 'routes',
                    handle: crumb('Routes'),
                    ...page(() => import('@/features/ops/network/routes-page')),
                  },
                ],
              },
              { path: 'crew', handle: crumb('Crew'), ...page(() => import('@/features/ops/crew/crew-page')) },
              {
                path: 'users',
                handle: crumb('Users'),
                element: <RequireRole roles={['ADMIN']} />,
                children: [{ index: true, ...page(() => import('@/features/ops/users/users-page')) }],
              },
              { path: '*', ...page(() => import('@/features/not-found-page')) },
            ],
          },
        ],
      },
    ],
  },
]

export const router = createBrowserRouter(routes)
