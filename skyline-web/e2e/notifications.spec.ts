import { expect, test } from '@playwright/test'

import { bookableFlight, bookViaApi, freshApp, live, register, uniqueEmail } from './support'

test('a booking shows up in the bell; opening it goes to the trip and clears the count', async ({ page, request }) => {
  test.slow()
  const flight = await bookableFlight(request, 30)
  await freshApp(page)
  await register(page, uniqueEmail('bell'))
  const booking = await bookViaApi(page, flight.id, 'Lovelace')

  const bell = page.getByRole('button', { name: /^Notifications/ })
  // Live, the confirmation arrives through Kafka; the bell polls and refetches on focus.
  await expect(async () => {
    await page.reload()
    await expect(bell).toHaveAccessibleName('Notifications, 1 unread', { timeout: 2_000 })
  }).toPass({ timeout: live ? 90_000 : 10_000 })

  await bell.click()
  const entry = page.getByRole('list', { name: 'Latest notifications' }).getByRole('button', {
    name: new RegExp(`Booking confirmed: ${booking.pnr}`),
  })
  await expect(entry).toBeVisible()
  await entry.click()

  await expect(page).toHaveURL(new RegExp(`/trips/${booking.id}$`))
  await expect(bell).toHaveAccessibleName('Notifications')
})

test('the booking form asks for a contact email, prefilled with the account', async ({ page, request }) => {
  test.skip(live, 'Reads the mocked back-end')
  const flight = await bookableFlight(request, 30)
  await freshApp(page)
  const email = uniqueEmail('contact')
  await register(page, email)
  await page.goto(`/book/${flight.id}`)
  await expect(page.getByRole('heading', { name: 'Choose your cabin' })).toBeVisible()
  await page.getByRole('radio', { name: /Economy/ }).click()
  await page.getByRole('button', { name: 'Continue' }).first().click()

  const contact = page.getByLabel('Contact email')
  await expect(contact).toHaveValue(email)
  await contact.fill('not-an-email')
  await page.getByRole('button', { name: 'Choose seats' }).first().click()
  await expect(page.getByText('Enter a valid email address').first()).toBeVisible()
  await contact.fill('family@example.com')
  await expect(contact).toHaveValue('family@example.com')
})
