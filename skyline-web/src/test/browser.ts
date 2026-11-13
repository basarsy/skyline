import { setupWorker } from 'msw/browser'

import { exportDb, handlers, importDb, resetDb } from './handlers'

const STORAGE_KEY = 'skyline.mockDb'

/**
 * MSW in the browser, for the mocked Playwright suite (`vite --mode msw`). Never part of production.
 * The in-memory back-end is mirrored to sessionStorage so full page loads keep their data.
 */
export async function startMockWorker() {
  try {
    const saved = sessionStorage.getItem(STORAGE_KEY)
    if (saved) importDb(saved)
  } catch {
    resetDb()
  }

  const worker = setupWorker(...handlers)
  worker.events.on('response:mocked', () => {
    try {
      sessionStorage.setItem(STORAGE_KEY, exportDb())
    } catch {
      // Storage full or unavailable: data just won't survive a reload.
    }
  })
  await worker.start({ onUnhandledRequest: 'bypass', quiet: true })

  Object.assign(window, {
    __skylineResetMocks: () => {
      resetDb()
      sessionStorage.removeItem(STORAGE_KEY)
    },
  })
}
