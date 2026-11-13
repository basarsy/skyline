import '@fontsource-variable/inter'
import '@fontsource-variable/inter-tight'
import '@fontsource-variable/jetbrains-mono'
import './styles/globals.css'
import './styles/print.css'

import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { RouterProvider } from 'react-router'

import { Providers } from '@/app/providers'
import { router } from '@/app/router'
import { bootstrapSession } from '@/auth/session'

async function start() {
  // Mocked API for the default Playwright suite only: the branch is compiled out of production builds.
  if (import.meta.env.VITE_MSW === 'true') {
    const { startMockWorker } = await import('@/test/browser')
    await startMockWorker()
  }

  // Restore the session in the background; guards show a placeholder until it settles.
  void bootstrapSession()

  createRoot(document.getElementById('root')!).render(
    <StrictMode>
      <Providers>
        <RouterProvider router={router} />
      </Providers>
    </StrictMode>,
  )
}

void start()
