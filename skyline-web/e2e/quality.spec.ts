import AxeBuilder from '@axe-core/playwright'
import { expect, test, type Page } from '@playwright/test'

import { bookViaApi, freshApp, live, logIn, MOCK } from './support'

test.skip(live, 'Accessibility and layout checks run against the mocked API')

const tomorrow = () => new Date(Date.now() + 86_400_000).toISOString().slice(0, 10)
const FLIGHT = 'f0000000-0000-4000-8000-000000000001'

const PUBLIC = [
  '/',
  `/flights?from=IST&to=LHR&date=${tomorrow()}&cabin=ECONOMY`,
  `/flights/${FLIGHT}`,
  '/manage',
  '/login',
  '/register',
  '/does-not-exist',
]
const PASSENGER = ['/trips', `/book/${FLIGHT}?cabin=BUSINESS`, '/checkin']
const OPS = [
  '/ops/flights',
  '/ops/flights/new',
  `/ops/flights/${FLIGHT}`,
  '/ops/fleet',
  '/ops/fleet/types',
  '/ops/network/airports',
  '/ops/network/routes',
  '/ops/crew',
  '/ops/users',
]

/** Waits for the route's content (skeletons gone, a heading present). */
async function settle(page: Page) {
  await page.waitForLoadState('networkidle')
  await expect(page.locator('h1').first()).toBeVisible()
  await expect(page.locator('[aria-busy="true"]')).toHaveCount(0)
}

async function axe(page: Page, route: string) {
  const results = await new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa'])
    .analyze()
  const serious = results.violations.filter((v) => v.impact === 'serious' || v.impact === 'critical')
  expect(
    serious.map(
      (v) =>
        `${v.id} (${v.impact}): ${v.help}\n    ${v.nodes
          .map((n) => `${n.target.join(' ')} :: ${n.html.slice(0, 200)}`)
          .slice(0, 4)
          .join('\n    ')}`,
    ),
    `axe on ${route}`,
  ).toEqual([])
}

for (const theme of ['light', 'dark'] as const) {
  test.describe(`${theme} theme`, () => {
    test.use({ colorScheme: theme })
    // Each test sweeps many routes.
    test.slow()

    test('public routes have no serious accessibility violations', async ({ page }) => {
      await freshApp(page)
      for (const route of PUBLIC) {
        await page.goto(route)
        await settle(page)
        await axe(page, route)
      }
    })

    test('passenger routes have no serious accessibility violations', async ({ page }) => {
      await freshApp(page)
      await logIn(page, MOCK.passenger.email, MOCK.passenger.password)
      const booking = await bookViaApi(page, FLIGHT, 'Lovelace')
      const routes = [
        ...PASSENGER,
        `/book/confirmation/${booking.id}`,
        `/trips/${booking.id}`,
        `/manage/${booking.pnr}`,
        `/checkin/${booking.pnr}`,
      ]
      for (const route of routes) {
        await page.goto(route)
        await settle(page)
        await axe(page, route)
      }
      // Check in, then the boarding pass page.
      await page.getByLabel(/not carrying prohibited items/).check()
      await page.getByRole('button', { name: 'Check in' }).click()
      await expect(page.getByRole('img', { name: /PDF417/ })).toBeVisible()
      // Measure at rest: a toast caught mid-animation has partial opacity.
      await page.waitForTimeout(1000)
      await axe(page, 'boarding pass')
    })

    test('ops routes have no serious accessibility violations', async ({ page }) => {
      await freshApp(page)
      await logIn(page, MOCK.admin.email, MOCK.admin.password)
      for (const route of OPS) {
        await page.goto(route)
        await settle(page)
        await axe(page, route)
      }
    })
  })
}

async function noHorizontalScroll(page: Page, routes: string[]) {
  for (const width of [375, 768, 1024, 1440]) {
    await page.setViewportSize({ width, height: 900 })
    for (const route of routes) {
      await page.goto(route)
      await settle(page)
      const overflow = await page.evaluate(
        () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
      )
      expect(overflow, `${route} at ${width}px`).toBeLessThanOrEqual(0)
    }
  }
}

test.describe('no horizontal scroll at 375, 768, 1024 and 1440px', () => {
  test.describe.configure({ timeout: 120_000 })

  test('public routes', async ({ page }) => {
    await freshApp(page)
    await noHorizontalScroll(page, PUBLIC)
  })

  test('passenger routes', async ({ page }) => {
    await freshApp(page)
    await logIn(page, MOCK.passenger.email, MOCK.passenger.password)
    const booking = await bookViaApi(page, FLIGHT, 'Lovelace')
    await noHorizontalScroll(page, [...PASSENGER, `/trips/${booking.id}`, `/checkin/${booking.pnr}`])
  })

  test('ops routes', async ({ page }) => {
    await freshApp(page)
    await logIn(page, MOCK.admin.email, MOCK.admin.password)
    await noHorizontalScroll(page, OPS)
  })
})

test('reduced motion renders final states without springs', async ({ page }) => {
  await page.emulateMedia({ reducedMotion: 'reduce' })
  await freshApp(page, `/flights?from=IST&to=LHR&date=${tomorrow()}&cabin=ECONOMY`)
  await settle(page)
  // List entries render at their final position immediately (no translate/opacity transition in flight).
  const styles = await page
    .getByRole('article')
    .first()
    .locator('..')
    .evaluate((el) => {
      const s = getComputedStyle(el)
      return { opacity: s.opacity, transform: s.transform }
    })
  expect(styles.opacity).toBe('1')
  expect(['none', 'matrix(1, 0, 0, 1, 0, 0)']).toContain(styles.transform)
  const duration = await page
    .getByRole('button', { name: 'Search flights' })
    .evaluate((el) => getComputedStyle(el).transitionDuration)
  expect(parseFloat(duration)).toBeLessThan(0.001)
})
