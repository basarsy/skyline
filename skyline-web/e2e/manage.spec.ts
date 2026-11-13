import { expect, test } from '@playwright/test'

import { bookableFlight, bookViaApi, freshApp, logIn, logOut, register, uniqueEmail } from './support'

test('PNR lookup is redacted for visitors and full for the owner', async ({ page, request }) => {
  const flight = await bookableFlight(request, 24)
  await freshApp(page)
  const email = uniqueEmail('owner')
  await register(page, email)
  const { pnr } = await bookViaApi(page, flight.id, 'Lovelace')
  await page.reload()
  await logOut(page)

  // Anonymous, right reference, wrong name: generic "not found", never "wrong last name".
  await page.goto('/manage')
  await page.getByLabel('Booking reference').fill(pnr.toLowerCase())
  await page.getByLabel('Last name').fill('Byron')
  await page.getByRole('button', { name: 'Find booking' }).click()
  await expect(page.getByText('We couldn’t find a booking with those details')).toBeVisible()

  // Right name: redacted view.
  await page.getByLabel('Last name').fill('lovelace')
  await page.getByRole('button', { name: 'Find booking' }).click()
  await expect(page).toHaveURL(new RegExp(`/manage/${pnr}$`))
  await expect(page.getByText('Log in to check in or cancel this booking.')).toBeVisible()
  await expect(page.getByText(/^\*+678$/)).toBeVisible()
  await expect(page.getByText('Date of birth')).toHaveCount(0)
  await expect(page.getByRole('button', { name: 'Cancel booking' })).toHaveCount(0)
  // The last name never appears in the URL.
  expect(page.url().toLowerCase()).not.toContain('lovelace')

  // A hard refresh without the name goes back to the form.
  await page.reload()
  await expect(page).toHaveURL(/\/manage$/)

  // The owner sees the full view and the actions.
  await logIn(page, email, 'Passw0rd!!')
  await page.goto(`/manage/${pnr}`)
  await expect(page.getByText('Date of birth')).toBeVisible()
  await expect(page.getByRole('button', { name: 'Show passport number' })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Cancel booking' })).toBeVisible()
})
