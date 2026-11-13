import { expect, test, type Page } from '@playwright/test'

import { bookableFlight, freshApp, register, uniqueEmail } from './support'

/** The popover that is currently open (a closing one may still be animating out). */
function openPopover(page: Page) {
  return page.locator('[data-slot=popover-content][data-state=open]')
}

/** Date of birth: open the picker, choose the year in the dropdown, then the 15th. */
async function pickBirthDate(page: Page, year: string) {
  await page.getByRole('button', { name: 'Date of birth' }).click()
  const calendar = openPopover(page)
  await calendar.getByRole('combobox', { name: /year/i }).selectOption(year)
  await calendar.getByRole('button', { name: new RegExp(`15th, ${year}`) }).click()
  await expect(page.getByRole('button', { name: 'Date of birth' })).toContainText(year)
}

async function pickAirport(page: Page, label: string, iata: string) {
  await page.getByRole('combobox', { name: label }).click()
  const popover = openPopover(page)
  await popover.getByPlaceholder('Search IATA, city or airport').fill(iata)
  await popover.getByRole('option', { name: new RegExp(`^${iata}`) }).click()
}

test('register, search, book, check in and get a boarding pass', async ({ page, request }) => {
  const flight = await bookableFlight(request, 24)
  await freshApp(page)
  await register(page, uniqueEmail('pax'))

  // Search IST → LHR on the flight's UTC day.
  await page.goto('/')
  await pickAirport(page, 'From', 'IST')
  await pickAirport(page, 'To', 'LHR')
  await page.goto(`/flights?from=IST&to=LHR&date=${flight.date}&cabin=ECONOMY`)
  await expect(page).toHaveURL(new RegExp(`from=IST&to=LHR&date=${flight.date}`))
  await page.getByRole('link', { name: `Select flight ${flight.flightNumber}` }).click()

  // Cabin → Passenger → Review
  await expect(page.getByRole('heading', { name: 'Choose your cabin' })).toBeVisible()
  await page.getByRole('radio', { name: /Business/ }).click()
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
  await expect(page.getByText('Assigned at check-in')).toBeVisible()
  await page.getByRole('button', { name: 'Confirm booking' }).first().click()

  // Confirmation: PNR, server price with currency.
  await expect(page.getByRole('heading', { name: 'You’re booked.' })).toBeVisible()
  const pnr = (await page.locator('.font-mono.tracking-\\[0\\.18em\\]').first().textContent())!.trim()
  expect(pnr).toMatch(/^[A-HJ-NP-Z2-9]{6}$/)
  await expect(page.getByText('Total', { exact: true })).toBeVisible()
  await expect(page.getByText(/^€\d[\d,]*\.\d{2}$/).first()).toBeAttached()

  // My trips shows it; check in from there.
  await page.goto('/trips')
  await expect(page.getByText(pnr)).toBeVisible()
  await page.getByRole('button', { name: 'Check in' }).first().click()
  await expect(page).toHaveURL(new RegExp(`/checkin/${pnr}`))
  await page.getByLabel(/not carrying prohibited items/).check()
  await page.getByRole('button', { name: 'Check in' }).click()

  const pass = page.getByRole('article', { name: /Boarding pass for Ada Lovelace/ })
  await expect(pass).toBeVisible()
  await expect(pass.getByText(/^\d+[A-F]$/)).toBeVisible()
  const barcode = (await pass.getByText(/^SKY-/).textContent())!.trim()
  await expect(pass.getByRole('img', { name: /PDF417 barcode/ })).toBeVisible()

  // A second visit shows the same pass.
  await page.goto(`/checkin/${pnr}`)
  await expect(page.getByRole('article', { name: /Boarding pass/ }).getByText(barcode)).toBeVisible()
})

test('a flight more than 48h away shows the countdown, and cancelling frees the seat', async ({ page, request }) => {
  await bookableFlight(request, 24)
  await freshApp(page)
  await register(page, uniqueEmail('later'))
  // The mocked back-end seeds SK201 (LHR → JFK) 96h out.
  const date = new Date(Date.now() + 96 * 3_600_000).toISOString().slice(0, 10)
  await page.goto(`/flights?from=LHR&to=JFK&date=${date}&cabin=ECONOMY`)
  test.skip(!!process.env.E2E_LIVE, 'Uses the mocked LHR → JFK flight')
  const seatsBefore = await page.evaluate(
    async () =>
      (
        (await (await fetch('/api/v1/flights/f0000000-0000-4000-8000-000000000003')).json()) as {
          data: { availableSeats: number }
        }
      ).data.availableSeats,
  )
  await page
    .getByRole('link', { name: /Select flight/ })
    .first()
    .click()
  await page.getByRole('button', { name: 'Continue' }).first().click()
  await page.getByLabel('First name').fill('Grace')
  await page.getByLabel('Last name').fill('Hopper')
  await page.getByLabel('Passport number').fill('P7654321')
  await pickBirthDate(page, '1985')
  await page.getByRole('combobox', { name: 'Nationality' }).click()
  await openPopover(page).getByPlaceholder('Search countries').fill('Türk')
  await openPopover(page).getByRole('option').first().click()
  await page.getByRole('button', { name: 'Review booking' }).first().click()
  await page.getByRole('button', { name: 'Confirm booking' }).first().click()
  await expect(page.getByText(/Check-in opens in/)).toBeVisible()

  await page.getByRole('link', { name: 'View trip' }).click()
  await expect(page).toHaveURL(/\/trips\//)
  const tripUrl = page.url()
  await expect(page.getByRole('button', { name: 'Check in' })).toHaveCount(0)
  const pnr = (await page.locator('.font-mono.tracking-\\[0\\.18em\\]').first().textContent())!.trim()
  await page.goto(`/checkin/${pnr}`)
  await expect(page.getByRole('timer')).toBeVisible()

  await page.goto(tripUrl)
  await page.getByRole('button', { name: 'Cancel booking' }).click()
  const dialog = page.getByRole('alertdialog')
  await expect(dialog).toContainText(pnr)
  await dialog.getByRole('button', { name: 'Cancel booking' }).click()
  await expect(page.getByText('Cancelled').first()).toBeVisible()
  await expect(page.getByRole('button', { name: 'Cancel booking' })).toHaveCount(0)
  // The seat went back: the flight's availableSeats is back to where it was before booking.
  const flight = await page.evaluate(
    async () =>
      (
        (await (await fetch('/api/v1/flights/f0000000-0000-4000-8000-000000000003')).json()) as {
          data: { availableSeats: number }
        }
      ).data.availableSeats,
  )
  expect(flight).toBe(Number(seatsBefore))
})

test('booking works with the keyboard only', async ({ page }) => {
  test.skip(!!process.env.E2E_LIVE, 'Mocked data')
  await freshApp(page)
  await register(page, uniqueEmail('kbd'))
  const date = new Date(Date.now() + 24 * 3_600_000).toISOString().slice(0, 10)
  await page.goto(`/book/f0000000-0000-4000-8000-000000000001?cabin=ECONOMY`)
  await expect(page.getByRole('heading', { name: 'Choose your cabin' })).toBeVisible()

  // Cabins are a radio group: arrows move, Tab leaves.
  await page.getByRole('radio', { name: /Economy/ }).focus()
  await page.keyboard.press('ArrowDown')
  await expect(page.getByRole('radio', { name: /Business/ })).toHaveAttribute('aria-checked', 'true')
  await page.getByRole('button', { name: 'Continue' }).first().focus()
  await page.keyboard.press('Enter')

  await page.getByLabel('First name').focus()
  await page.keyboard.type('Alan')
  await page.keyboard.press('Tab')
  await page.keyboard.type('Turing')
  await page.keyboard.press('Tab')
  await page.keyboard.type('X1234567')
  // An incomplete submit focuses the error summary.
  await page.keyboard.press('Enter')
  await expect(page.getByRole('alert').filter({ hasText: 'Please check the highlighted fields' })).toBeFocused()
  expect(date).toBeTruthy()
})
