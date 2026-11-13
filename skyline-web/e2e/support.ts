import { expect, type APIRequestContext, type Page } from '@playwright/test'

export const live = !!process.env.E2E_LIVE

/** Seeded accounts in the mocked back-end (src/test/handlers.ts). */
export const MOCK = {
  passenger: { email: 'ada@skyline.test', password: 'Passw0rd!!' },
  staff: { email: 'staff@skyline.test', password: 'Staff-Passw0rd!' },
  admin: { email: 'admin@skyline.test', password: 'Admin-Passw0rd!' },
}

export const ADMIN = live
  ? {
      email: process.env.SKYLINE_ADMIN_EMAIL ?? 'admin@skyline.local',
      password: process.env.SKYLINE_ADMIN_PASSWORD ?? '',
    }
  : MOCK.admin

export function uniqueEmail(prefix: string) {
  return `${prefix}-${Date.now().toString(36)}${Math.random().toString(36).slice(2, 6)}@e2e.test`
}

/** Opens the app with a fresh mocked back-end (no-op against the live stack). */
export async function freshApp(page: Page, path = '/') {
  await page.goto(path)
  if (!live) {
    await page.evaluate(() => {
      ;(window as unknown as { __skylineResetMocks?: () => void }).__skylineResetMocks?.()
      localStorage.clear()
    })
    await page.goto(path)
  }
}

export async function logIn(page: Page, email: string, password: string) {
  await page.goto('/login')
  await page.getByLabel('Email').fill(email)
  await page.getByLabel('Password', { exact: true }).fill(password)
  await page.getByRole('button', { name: 'Log in' }).click()
  await expect(page).not.toHaveURL(/\/login/)
}

export async function logOut(page: Page) {
  await page.getByRole('button', { name: /Account menu/ }).click()
  await page.getByRole('menuitem', { name: 'Log out' }).click()
  await expect(page.getByRole('link', { name: 'Log in' }).first()).toBeVisible()
}

export async function register(page: Page, email: string, password = 'Passw0rd!!') {
  await page.goto('/register')
  await page.getByLabel('Email').fill(email)
  await page.getByLabel('Password', { exact: true }).fill(password)
  await page.getByRole('button', { name: 'Create account' }).click()
  await expect(page).toHaveURL(/\/trips/)
}

/** A UTC date-time string the API accepts ("yyyy-MM-ddTHH:mm:ss"), `hours` from now. */
export function utcIn(hours: number) {
  const date = new Date(Date.now() + hours * 3_600_000)
  date.setUTCSeconds(0, 0)
  return date.toISOString().slice(0, 19)
}

async function apiLogin(request: APIRequestContext, email: string, password: string) {
  const response = await request.post('/api/v1/auth/login', { data: { email, password } })
  expect(response.ok(), await response.text()).toBeTruthy()
  return ((await response.json()) as { data: { accessToken: string } }).data.accessToken
}

/**
 * An IST → LHR flight departing in `hours`, on a new aircraft, created through the API as admin. In the
 * mocked mode the seeded SK101 (departing in 24h) is used instead.
 */
export async function bookableFlight(
  request: APIRequestContext,
  hours = 24,
): Promise<{ id: string; flightNumber: string; date: string }> {
  if (!live) {
    return { id: 'f0000000-0000-4000-8000-000000000001', flightNumber: 'SK101', date: utcIn(24).slice(0, 10) }
  }
  const token = await apiLogin(request, ADMIN.email, ADMIN.password)
  const headers = { Authorization: `Bearer ${token}` }
  const suffix = Math.random().toString(36).slice(2, 7).toUpperCase()
  const aircraft = await request.post('/api/v1/aircraft', {
    headers,
    data: {
      tailNumber: `TC-W${suffix}`,
      aircraftTypeId: '22222222-2222-2222-2222-222222222201',
      manufacturedYear: 2021,
    },
  })
  expect(aircraft.ok(), await aircraft.text()).toBeTruthy()
  const routes = await request.get('/api/v1/routes/search?originIata=IST&destinationIata=LHR')
  let routeId = ((await routes.json()) as { data: { id: string }[] }).data[0]?.id
  if (!routeId) {
    const created = await request.post('/api/v1/routes', {
      headers,
      data: {
        originAirportId: '11111111-1111-1111-1111-111111111101',
        destinationAirportId: '11111111-1111-1111-1111-111111111102',
        distanceKm: 2500,
        estimatedDurationMinutes: 240,
      },
    })
    routeId = ((await created.json()) as { data: { id: string } }).data.id
  }
  const departure = utcIn(hours)
  const flightNumber = `W${suffix.slice(0, 4)}`
  const flight = await request.post('/api/v1/flights', {
    headers,
    data: {
      flightNumber,
      routeId,
      aircraftId: ((await aircraft.json()) as { data: { id: string } }).data.id,
      departureTime: departure,
      arrivalTime: utcIn(hours + 4),
      basePrice: 120,
    },
  })
  expect(flight.ok(), await flight.text()).toBeTruthy()
  return { id: ((await flight.json()) as { data: { id: string } }).data.id, flightNumber, date: departure.slice(0, 10) }
}
