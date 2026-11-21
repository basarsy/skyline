import AxeBuilder from '@axe-core/playwright'
import { expect, test, type Page } from '@playwright/test'

import { bookViaApi, freshApp, live, register, uniqueEmail } from './support'

test.skip(live, 'Uses the mocked seat maps and seeded flights')

/** SK101: A321neo (Business 2-2, Economy 3-3). SK201: 787-9 (Business 2-2-2, Economy 3-3-3). */
const SK101 = 'f0000000-0000-4000-8000-000000000001'
const SK201 = 'f0000000-0000-4000-8000-000000000003'

const seat = (page: Page, code: string) => page.getByRole('button', { name: new RegExp(`^Seat ${code},`) })

async function fillPassenger(page: Page, index: number, first: string, last: string) {
  const fieldset = page.getByRole('group', { name: `Passenger ${index + 1}` })
  const scope = (await fieldset.count()) ? fieldset : page
  await scope.getByLabel('First name').fill(first)
  await scope.getByLabel('Last name').fill(last)
  await scope.getByLabel('Passport number').fill(`U${index}234567`)
  await scope.getByRole('button', { name: 'Date of birth' }).click()
  const calendar = page.locator('[data-slot=popover-content][data-state=open]')
  await calendar.getByRole('combobox', { name: /year/i }).selectOption('1990')
  await calendar.getByRole('button', { name: /15th, 1990/ }).click()
  await scope.getByRole('combobox', { name: 'Nationality' }).click()
  await page
    .locator('[data-slot=popover-content][data-state=open]')
    .getByPlaceholder('Search countries')
    .fill('United Kingdom')
  await page
    .locator('[data-slot=popover-content][data-state=open]')
    .getByRole('option', { name: 'United Kingdom' })
    .click()
}

/** Another passenger holds a seat through the API, in the same (mocked) back-end. */
async function someoneElseHolds(page: Page, seatNumber: string) {
  await page.evaluate(
    async ({ seatNumber, flightId }) => {
      const email = `rival-${Math.random().toString(36).slice(2)}@e2e.test`
      const json = { 'Content-Type': 'application/json' }
      await fetch('/api/v1/auth/register', {
        method: 'POST',
        headers: json,
        body: JSON.stringify({ email, password: 'Passw0rd!!' }),
      })
      const login = (await fetch('/api/v1/auth/login', {
        method: 'POST',
        headers: json,
        body: JSON.stringify({ email, password: 'Passw0rd!!' }),
      }).then((r) => r.json())) as { data: { accessToken: string } }
      await fetch('/api/v1/reservations/seat-holds', {
        method: 'POST',
        headers: { ...json, Authorization: `Bearer ${login.data.accessToken}` },
        body: JSON.stringify({ flightId, seatNumber, passengerIndex: 0, cabinClass: 'ECONOMY', fareType: 'STANDARD' }),
      })
    },
    { seatNumber, flightId: SK101 },
  )
}

test('two passengers pick seats next to each other and pay for the window', async ({ page }) => {
  test.slow()
  await freshApp(page)
  await register(page, uniqueEmail('seats'))
  await page.goto(`/book/${SK101}?cabin=ECONOMY&fare=STANDARD`)
  await page.getByRole('button', { name: 'Continue' }).first().click()

  await fillPassenger(page, 0, 'Ada', 'Lovelace')
  await page.getByRole('button', { name: 'Add a passenger' }).click()
  await fillPassenger(page, 1, 'Alan', 'Turing')
  await page.getByRole('button', { name: 'Choose seats' }).first().click()

  // A narrow-body with Business up front: Business seats are drawn but not for Economy passengers.
  await expect(page.getByRole('group', { name: /^Seat map/ })).toBeVisible()
  await expect(seat(page, '1A')).toHaveAttribute('aria-disabled', 'true')

  await seat(page, '20A').click() // window: 6.00 on Standard
  // The switcher moved on to passenger 2, and 20A now shows as their companion's seat ("1").
  await expect(page.getByRole('tab', { name: /Alan Turing/ })).toHaveAttribute('aria-selected', 'true')
  await expect(seat(page, '20A')).toHaveAccessibleName(/chosen for passenger 1/)
  await seat(page, '20B').click() // middle: included
  const summary = page.getByRole('region', { name: 'Seat summary' })
  await expect(summary).toContainText('20A')
  await expect(summary).toContainText('20B')
  await expect(summary).toContainText('€6.00')

  await page.getByRole('button', { name: 'Continue' }).first().click()
  await page.getByRole('button', { name: 'Review booking' }).first().click()
  await expect(page.getByRole('heading', { name: 'Review your booking' })).toBeVisible()
  await page.getByRole('button', { name: 'Confirm booking' }).first().click()
  await expect(page.getByRole('heading', { name: 'You’re booked.' })).toBeVisible()

  // One trip per passenger, each with its seat.
  await page.goto('/trips')
  await expect(page.getByRole('definition').filter({ hasText: /^20A$/ })).toBeVisible()
  await expect(page.getByRole('definition').filter({ hasText: /^20B$/ })).toBeVisible()
})

test('a seat someone else just took is refused and the map refreshes', async ({ page }) => {
  await freshApp(page)
  await register(page, uniqueEmail('race'))
  await page.goto(`/book/${SK101}?cabin=ECONOMY&fare=STANDARD`)
  await page.getByRole('button', { name: 'Continue' }).first().click()
  await fillPassenger(page, 0, 'Ada', 'Lovelace')
  await page.getByRole('button', { name: 'Choose seats' }).first().click()
  await expect(seat(page, '22C')).toHaveAttribute('aria-disabled', 'false')

  await someoneElseHolds(page, '22C')
  await seat(page, '22C').click()

  await expect(page.getByText(/Seat 22C was just taken by another passenger/)).toBeVisible()
  await expect(seat(page, '22C')).toHaveAccessibleName(/held by another passenger/)
  await expect(seat(page, '22C')).toHaveAttribute('aria-disabled', 'true')
})

test('every aircraft is drawn from its own layout', async ({ page }) => {
  await freshApp(page)
  await register(page, uniqueEmail('layouts'))

  await page.goto(`/book/${SK201}?cabin=ECONOMY`)
  await page.getByRole('button', { name: 'Continue' }).first().click()
  await fillPassenger(page, 0, 'Ada', 'Lovelace')
  await page.getByRole('button', { name: 'Choose seats' }).first().click()
  // 787-9: nine abreast in Economy, so a K seat exists, and 296 seats in all.
  await expect(seat(page, '20K')).toBeVisible()
  await expect(page.getByRole('button', { name: /^Seat \d+[A-K],/ })).toHaveCount(296)
  await expect(page.getByText(/Boeing 787-9/)).toBeVisible()
})

test('check-in: choose and pay for a seat, add a bag, and the pass shows the seat', async ({ page }) => {
  test.slow()
  await freshApp(page)
  await register(page, uniqueEmail('checkin-seat'))
  const booking = await bookViaApi(page, SK101, 'Lovelace')

  await page.goto(`/checkin/${booking.pnr}`)
  await page.getByRole('button', { name: 'Choose a seat' }).click()
  await seat(page, '26A').click() // exit row
  await page.getByRole('button', { name: /^Pay €\d+\.\d{2} for 26A$/ }).click()
  await expect(page.getByText('Seat 26A is yours')).toBeVisible()
  // A paid seat isn't given back by a tap, and removing it asks first.
  await seat(page, '26A').click()
  await expect(page.getByRole('region', { name: 'Seat summary' })).toContainText('26A')
  await page.getByRole('button', { name: /^Remove seat 26A/ }).click()
  await expect(page.getByRole('alertdialog')).toContainText('refunded only on Flex fares')
  await page.getByRole('button', { name: 'Keep it' }).click()
  await expect(page.getByRole('region', { name: 'Seat summary' })).toContainText('26A')

  await page.getByRole('button', { name: 'Add bags' }).click()
  await page.getByRole('button', { name: 'Add one Checked bag 20 kg' }).click()
  await page.getByRole('button', { name: 'Add to booking' }).click()
  await expect(page.getByRole('region', { name: 'Your extras' })).toContainText('Checked bag 20 kg')

  await page.getByLabel(/not carrying prohibited items/).check()
  await page.getByRole('button', { name: 'Check in' }).click()
  const pass = page.getByRole('article', { name: /Boarding pass/ })
  await expect(pass.getByText('26A')).toBeVisible()
})

test('manage booking: the seat map and extras have no serious accessibility violations', async ({ page }) => {
  await freshApp(page)
  await register(page, uniqueEmail('a11y-seats'))
  const booking = await bookViaApi(page, SK101, 'Lovelace')
  await page.goto(`/trips/${booking.id}/extras`)
  await expect(page.getByRole('group', { name: /^Seat map/ })).toBeVisible()

  for (const tab of ['Seat', 'Bags and extras']) {
    await page.getByRole('tab', { name: tab }).click()
    await page.waitForLoadState('networkidle')
    const results = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21aa']).analyze()
    const serious = results.violations.filter((v) => v.impact === 'serious' || v.impact === 'critical')
    expect(
      serious.map((v) => `${v.id}: ${v.nodes[0]?.target.join(' ')}`),
      tab,
    ).toEqual([])
  }
})
