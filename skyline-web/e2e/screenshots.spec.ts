import path from 'node:path'

import { expect, test, type Page } from '@playwright/test'

import { bookableFlight, freshApp, live, logIn, MOCK, register } from './support'

/*
 * README screenshots. Not part of the normal suite: run with
 *   SCREENSHOTS=1 pnpm exec playwright test e2e/screenshots.spec.ts
 * against the mocked API (the default), and the images land in .github/screenshots at the repository root.
 */
test.skip(!process.env.SCREENSHOTS, 'Set SCREENSHOTS=1 to regenerate the README screenshots')
test.skip(live, 'Screenshots use the mocked API so the data is the same every time')
test.describe.configure({ mode: 'serial' })
test.use({ viewport: { width: 1440, height: 900 }, colorScheme: 'light', reducedMotion: 'reduce' })

const OUT = path.join(process.cwd(), '..', '.github', 'screenshots')

async function settle(page: Page) {
  await page.waitForLoadState('networkidle')
  // Lazy chunks (pickers, barcode) and images finish after network idle; give them a beat.
  await page.waitForTimeout(600)
}

async function shot(page: Page, name: string, options: { fullPage?: boolean } = {}) {
  await settle(page)
  // Toasts are transient; keep them out of the pictures.
  await page.addStyleTag({ content: '[data-sonner-toaster] { display: none !important; }' })
  await page.screenshot({ path: path.join(OUT, `${name}.png`), fullPage: options.fullPage ?? false })
}

function openPopover(page: Page) {
  return page.locator('[data-slot=popover-content][data-state=open]')
}

async function pickBirthDate(page: Page, year: string) {
  await page.getByRole('button', { name: 'Date of birth' }).click()
  const calendar = openPopover(page)
  await calendar.getByRole('combobox', { name: /year/i }).selectOption(year)
  await calendar.getByRole('button', { name: new RegExp(`15th, ${year}`) }).click()
}

test('passenger journey', async ({ page, request }) => {
  test.slow()
  const flight = await bookableFlight(request, 24)
  await freshApp(page)

  // Home: scroll the destination cards into view so their fares load, then back to the top.
  await page.getByRole('heading', { name: 'Where we fly' }).scrollIntoViewIfNeeded()
  await expect(page.getByText('from').first()).toBeVisible()
  await page.evaluate(() => window.scrollTo(0, 0))
  await shot(page, '01-home', { fullPage: true })

  // A fresh mocked back-end per test, so a fixed address is free (and gives the avatar the initials "AL").
  await register(page, 'ada.lovelace@example.com')

  await page.goto(`/flights?from=IST&to=LHR&date=${flight.date}&cabin=ECONOMY`)
  await expect(page.getByRole('link', { name: `Select flight ${flight.flightNumber}` })).toBeVisible()
  await shot(page, '02-results')

  await page.getByRole('link', { name: `Select flight ${flight.flightNumber}` }).click()
  await expect(page.getByRole('heading', { name: 'Choose your cabin' })).toBeVisible()
  await page.getByRole('radio', { name: /Business/ }).click()
  await shot(page, '03-booking-cabin')

  await page.getByRole('button', { name: 'Continue' }).first().click()
  await page.getByLabel('First name').fill('Ada')
  await page.getByLabel('Last name').fill('Lovelace')
  await page.getByLabel('Passport number').fill('U1234567')
  await pickBirthDate(page, '1990')
  await page.getByRole('combobox', { name: 'Nationality' }).click()
  await openPopover(page).getByPlaceholder('Search countries').fill('United Kingdom')
  await openPopover(page).getByRole('option', { name: 'United Kingdom' }).click()
  await page.getByRole('button', { name: 'Review booking' }).first().click()
  await expect(page.getByRole('heading', { name: 'Review your booking' })).toBeVisible()
  await shot(page, '04-booking-review')

  await page.getByRole('button', { name: 'Confirm booking' }).first().click()
  await expect(page.getByRole('heading', { name: 'You’re booked.' })).toBeVisible()
  await shot(page, '05-confirmation')

  await page.goto('/trips')
  await expect(page.getByRole('button', { name: 'Check in' }).first()).toBeVisible()
  await shot(page, '06-trips')

  await page.getByRole('button', { name: 'Check in' }).first().click()
  await page.getByLabel(/not carrying prohibited items/).check()
  await page.getByRole('button', { name: 'Check in' }).click()
  const pass = page.getByRole('article', { name: /Boarding pass for Ada Lovelace/ })
  await expect(pass.getByRole('img', { name: /PDF417 barcode/ })).toBeVisible()
  await shot(page, '07-boarding-pass')

  // The same pass on a phone.
  await page.setViewportSize({ width: 390, height: 844 })
  await page.reload()
  await expect(page.getByRole('article', { name: /Boarding pass/ })).toBeVisible()
  await shot(page, '12-mobile-boarding-pass')
})

test('ops console', async ({ page }) => {
  // A new browser context starts with a fresh mocked back-end; no reset needed before logging in.
  await logIn(page, MOCK.admin.email, MOCK.admin.password)

  await page.goto('/ops/flights?date=all')
  await expect(page.getByRole('row', { name: /Open flight SK101/ })).toBeVisible()
  await shot(page, '08-ops-board')

  await page.goto('/ops/flights/f0000000-0000-4000-8000-000000000001')
  await expect(page.getByRole('heading', { name: 'SK101' })).toBeVisible()
  await shot(page, '09-ops-flight', { fullPage: true })

  await page.goto('/ops/fleet')
  await expect(page.getByText('TC-SKA')).toBeVisible()
  await shot(page, '10-ops-fleet')
})

test('dark theme and mobile', async ({ browser }) => {
  const dark = await browser.newPage({
    viewport: { width: 1440, height: 900 },
    colorScheme: 'dark',
    reducedMotion: 'reduce',
  })
  await freshApp(dark)
  await dark.getByRole('heading', { name: 'Where we fly' }).scrollIntoViewIfNeeded()
  await expect(dark.getByText('from').first()).toBeVisible()
  await dark.evaluate(() => window.scrollTo(0, 0))
  await shot(dark, '11-home-dark')
  await dark.close()

  const phone = await browser.newPage({
    viewport: { width: 390, height: 844 },
    colorScheme: 'light',
    reducedMotion: 'reduce',
  })
  await freshApp(phone)
  await shot(phone, '13-mobile-home')
  await phone.close()
})
