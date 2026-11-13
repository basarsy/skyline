import { expect, test, type Page } from '@playwright/test'

import { ADMIN, freshApp, live, logIn, logOut, MOCK, register, uniqueEmail, utcIn } from './support'

function openPopover(page: Page) {
  return page.locator('[data-slot=popover-content][data-state=open]')
}

async function pick(page: Page, field: string, search: string, option: RegExp | string) {
  await page.getByRole('combobox', { name: field }).click()
  const popover = openPopover(page)
  await popover.getByRole('combobox').fill(search)
  await popover.getByRole('option', { name: option }).first().click()
}

const sheet = (page: Page) => page.getByRole('dialog')

async function createStaff(page: Page) {
  const email = uniqueEmail('ops-staff')
  const password = 'Staff-Passw0rd-123'
  await page.goto('/ops/users')
  await page.getByRole('button', { name: 'Create staff account' }).click()
  await sheet(page).getByLabel('Email').fill(email)
  await sheet(page).getByLabel('Password', { exact: true }).fill(password)
  await sheet(page).getByRole('button', { name: 'Create account' }).click()
  await expect(sheet(page).getByText('This is the only time the password is shown')).toBeVisible()
  await sheet(page).getByRole('button', { name: 'Done' }).click()
  return { email, password }
}

async function createCrewMember(page: Page, role: 'Captain' | 'First officer', accountEmail: string) {
  const employee = `E${Date.now().toString(36).toUpperCase()}${role[0]}`
  await page.goto('/ops/crew')
  await page.getByRole('button', { name: 'Add crew member' }).click()
  const form = sheet(page)
  await form.getByRole('combobox', { name: 'Linked account' }).click()
  await openPopover(page).getByRole('combobox').fill(accountEmail.slice(0, 12))
  await openPopover(page)
    .getByRole('option', { name: new RegExp(accountEmail) })
    .click()
  await form.getByLabel('First name').fill(role === 'Captain' ? 'Amelia' : 'Chuck')
  await form.getByLabel('Last name').fill(role === 'Captain' ? 'Earhart' : 'Yeager')
  await form.getByLabel('Employee number').fill(employee)
  await form.getByRole('combobox', { name: 'Role' }).click()
  await page.getByRole('option', { name: role, exact: true }).click()
  await form.getByLabel('License number').fill(`L-${employee}`)
  await form.getByRole('button', { name: 'License expiry' }).click()
  const calendar = openPopover(page)
  await calendar.getByRole('combobox', { name: /year/i }).selectOption(String(new Date().getFullYear() + 5))
  await calendar.getByRole('button', { name: /15th/ }).click()
  await sheet(page).getByRole('button', { name: 'Add crew member' }).click()
  await expect(page.getByRole('cell', { name: employee, exact: true })).toBeVisible()
  return employee
}

test('admin builds the network and a flight; times round-trip in airport-local time', async ({ page }) => {
  await freshApp(page)
  await logIn(page, ADMIN.email, ADMIN.password)
  await expect(page).toHaveURL(/\/ops\/flights/)
  const suffix = Date.now().toString(36).slice(-4).toUpperCase()

  // Aircraft type and aircraft
  await page.goto('/ops/fleet/types')
  await page.getByRole('button', { name: 'Add type' }).click()
  await sheet(page).getByLabel('Manufacturer').fill('Airbus')
  await sheet(page).getByLabel('Model').fill(`A321-${suffix}`)
  await sheet(page).getByLabel('Seats').fill('220')
  await sheet(page).getByRole('button', { name: 'Add type' }).click()
  await expect(page.getByRole('cell', { name: `A321-${suffix}` })).toBeVisible()

  const tail = `TC-${suffix}`
  await page.goto('/ops/fleet')
  await page.getByRole('button', { name: 'Add aircraft' }).click()
  await sheet(page).getByLabel('Tail number').fill(tail)
  await pick(page, 'Aircraft type', `A321-${suffix}`, new RegExp(`A321-${suffix}`))
  await sheet(page).getByRole('button', { name: 'Add aircraft' }).click()
  await expect(page.getByRole('cell', { name: tail, exact: true })).toBeVisible()

  // Airport (mocked mode only: the live stack keeps airports between runs, IATA codes are unique)
  if (!live) {
    await page.goto('/ops/network/airports')
    await page.getByRole('button', { name: 'Add airport' }).click()
    const form = sheet(page)
    await form.getByLabel('IATA code').fill('CDG')
    await form.getByLabel('ICAO code').fill('LFPG')
    await form.getByLabel('Name').fill('Charles de Gaulle Airport')
    await form.getByLabel('City').fill('Paris')
    await pick(page, 'Country', 'France', 'France')
    await pick(page, 'Time zone', 'Europe/Paris', 'Europe/Paris')
    await sheet(page).getByRole('button', { name: 'Add airport' }).click()
    await expect(page.getByRole('cell', { name: 'CDG' })).toBeVisible()

    await page.goto('/ops/network/routes')
    await page.getByRole('button', { name: 'Add route' }).click()
    await pick(page, 'Origin', 'IST', /^IST/)
    await pick(page, 'Destination', 'CDG', /^CDG/)
    await sheet(page).getByLabel('Distance (km)').fill('2250')
    await sheet(page).getByLabel('Duration (minutes)').fill('225')
    await sheet(page).getByRole('button', { name: 'Add route' }).click()
    await expect(page.getByText('Istanbul → Paris')).toBeVisible()
  }

  // Flight IST → LHR in airport-local times: 3 days out, 10:15 in Istanbul.
  const day = utcIn(72).slice(0, 10)
  await page.goto('/ops/flights/new')
  await page.getByLabel('Flight number').fill(`SK${suffix}`)
  await pick(page, 'Route', 'Istanbul', /IST.*LHR/)
  await pick(page, 'Aircraft', tail, new RegExp(tail))
  await page.getByLabel(/^Departure · Europe\/Istanbul/).fill(`${day}T10:15`)
  // Arrival is prefilled from the route duration, across zones (IST UTC+3 → LHR).
  await expect(page.getByLabel(/^Arrival · Europe\/London/)).not.toHaveValue('')
  const arrival = await page.getByLabel(/^Arrival · Europe\/London/).inputValue()
  await page.getByLabel(/^Base price/).fill('140')
  await page.getByRole('button', { name: 'Create flight' }).click()
  await expect(page).toHaveURL(/\/ops\/flights\/[0-9a-f-]{36}$/)
  await expect(page.getByRole('heading', { name: `SK${suffix}` })).toBeVisible()

  await page.getByRole('link', { name: 'Edit flight' }).click()
  await expect(page.getByLabel(/^Departure · Europe\/Istanbul/)).toHaveValue(`${day}T10:15`)
  await expect(page.getByLabel(/^Arrival · Europe\/London/)).toHaveValue(arrival)
})

test('crew, the status lifecycle, and role gating', async ({ page }) => {
  test.slow()
  await freshApp(page)
  await logIn(page, ADMIN.email, ADMIN.password)

  // A staff account, and two accounts for the crew members.
  const staff = await createStaff(page)
  const captainAccount = await createStaff(page)
  const officerAccount = await createStaff(page)
  await createCrewMember(page, 'Captain', captainAccount.email)
  await createCrewMember(page, 'First officer', officerAccount.email)

  // The flight: in the mocked mode the seeded SK101; live, the first flight on today's board is not reliable,
  // so create one through the form.
  let flightUrl: string
  if (live) {
    await page.goto('/ops/flights/new')
    const suffix = Date.now().toString(36).slice(-4).toUpperCase()
    await page.getByLabel('Flight number').fill(`SK${suffix}`)
    await pick(page, 'Route', 'Istanbul', /IST.*LHR/)
    await page.getByRole('combobox', { name: 'Aircraft' }).click()
    await openPopover(page)
      .getByRole('option')
      .filter({ hasNotText: /only active/ })
      .first()
      .click()
    await page.getByLabel(/^Departure · Europe\/Istanbul/).fill(`${utcIn(30).slice(0, 10)}T10:15`)
    await page.getByLabel(/^Base price/).fill('140')
    await page.getByRole('button', { name: 'Create flight' }).click()
    await expect(page).toHaveURL(/\/ops\/flights\/[0-9a-f-]{36}$/)
    flightUrl = new URL(page.url()).pathname
  } else {
    flightUrl = '/ops/flights/f0000000-0000-4000-8000-000000000001'
  }
  const flightNumber = live ? (await page.getByRole('heading', { level: 1 }).textContent())!.trim() : 'SK101'

  // As STAFF: no cancel action, and BOARDING is refused by the server while the crew is missing.
  await logOut(page)
  await logIn(page, staff.email, staff.password)
  await page.goto(flightUrl)
  await expect(page.getByRole('button', { name: 'Cancel flight' })).toHaveCount(0)
  await page.getByRole('button', { name: 'Move to boarding' }).click()
  await expect(sheet(page).getByText(/needs at least one captain and one first officer/)).toBeVisible()
  await sheet(page).getByRole('button', { name: 'Move to boarding' }).click()
  await expect(sheet(page).getByRole('alert')).toContainText('CAPTAIN and one FIRST_OFFICER')
  await page.keyboard.press('Escape')

  // Assign both through the crew combobox.
  for (const [name, role] of [
    ['Earhart', 'Captain'],
    ['Yeager', 'First officer'],
  ] as const) {
    await page.getByRole('button', { name: 'Assign crew' }).click()
    await sheet(page).getByRole('combobox', { name: 'Crew member' }).click()
    await openPopover(page).getByRole('combobox').fill(name)
    await openPopover(page)
      .getByRole('option', { name: new RegExp(name) })
      .first()
      .click()
    await expect(sheet(page).getByRole('combobox', { name: 'Assigned role' })).toContainText(role)
    await sheet(page).getByRole('button', { name: 'Assign to flight' }).click()
    await expect(page.getByRole('list').getByText(name)).toBeVisible()
  }
  await expect(page.getByRole('list', { name: 'Crew readiness' }).first()).not.toContainText('missing')

  // Walk the main line; the board filtered by each status shows it at every step.
  for (const [action, status] of [
    ['Move to boarding', 'Boarding'],
    ['Mark as departed', 'Departed'],
    ['Mark as arrived', 'Arrived'],
  ] as const) {
    await page.getByRole('button', { name: action }).click()
    await sheet(page).getByRole('button', { name: action }).click()
    await expect(page.getByRole('heading', { name: 'Status' }).locator('..')).toContainText(status)
    await page.goto(`/ops/flights?date=all&status=${status.toUpperCase()}&q=${flightNumber}`)
    await expect(page.getByRole('row', { name: `Open flight ${flightNumber}` })).toBeVisible()
    await page.goto(flightUrl)
  }

  // Role gating: STAFF can't open Users.
  await page.goto('/ops/users')
  await expect(page.getByRole('heading', { name: 'You don’t have access' })).toBeVisible()
})

test('admin cancels a flight and its passengers see their booking cancelled; passengers get 403 in ops', async ({
  page,
}) => {
  test.skip(live, 'Uses the mocked seeded flights')
  await freshApp(page)
  const passenger = uniqueEmail('cancelled')
  await register(page, passenger)
  await page.goto('/book/f0000000-0000-4000-8000-000000000002?cabin=ECONOMY')
  await page.getByRole('button', { name: 'Continue' }).first().click()
  await page.getByLabel('First name').fill('Ada')
  await page.getByLabel('Last name').fill('Lovelace')
  await page.getByLabel('Passport number').fill('U1234567')
  await page.getByRole('button', { name: 'Date of birth' }).click()
  await openPopover(page).getByRole('combobox', { name: /year/i }).selectOption('1990')
  await openPopover(page)
    .getByRole('button', { name: /15th, 1990/ })
    .click()
  await pick(page, 'Nationality', 'United Kingdom', 'United Kingdom')
  await page.getByRole('button', { name: 'Review booking' }).first().click()
  await page.getByRole('button', { name: 'Confirm booking' }).first().click()
  await expect(page.getByRole('heading', { name: 'You’re booked.' })).toBeVisible()

  // A passenger in the ops console sees the 403 state.
  await page.goto('/ops/flights')
  await expect(page.getByRole('heading', { name: 'You don’t have access' })).toBeVisible()

  // Switch account logs out and returns here after logging in as admin.
  await page.getByRole('button', { name: 'Switch account' }).click()
  await page.getByLabel('Email').fill(MOCK.admin.email)
  await page.getByLabel('Password', { exact: true }).fill(MOCK.admin.password)
  await page.getByRole('button', { name: 'Log in' }).click()
  await expect(page).toHaveURL(/\/ops\/flights$/)
  await page.goto('/ops/flights/f0000000-0000-4000-8000-000000000002')
  await page.getByRole('button', { name: 'Cancel flight' }).click()
  const dialog = page.getByRole('alertdialog')
  await expect(dialog).toContainText('Every passenger booking on this flight will be cancelled')
  await expect(dialog.getByRole('button', { name: 'Cancel flight' })).toBeDisabled()
  await dialog.getByLabel(/Type SK103 to confirm/).fill('sk103')
  await dialog.getByRole('button', { name: 'Cancel flight' }).click()
  await expect(page.getByRole('heading', { name: 'Status' }).locator('..')).toContainText('Cancelled')

  await logOut(page)
  await logIn(page, passenger, 'Passw0rd!!')
  await page.goto('/trips?tab=cancelled')
  await expect(page.getByRole('article').first()).toContainText('Cancelled')
})
