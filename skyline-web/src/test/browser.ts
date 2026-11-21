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
  await worker.start({
    // Every API call the app makes has a handler, so an unhandled one is a missing mock: fail it loudly
    // instead of letting it slip through to a back-end that isn't there. Modules, fonts and images pass.
    onUnhandledRequest: (request, print) => {
      if (new URL(request.url).pathname.startsWith('/api/')) print.error()
    },
    quiet: true,
  })

  Object.assign(window, {
    __skylineResetMocks: () => {
      resetDb()
      sessionStorage.removeItem(STORAGE_KEY)
    },
  })
}
