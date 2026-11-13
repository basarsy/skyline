/// <reference types="vitest/config" />
import path from 'node:path'
import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import fs from 'node:fs'
import { defineConfig, loadEnv, type Plugin } from 'vite'

/**
 * Serves MSW's service worker in the 'msw' mode only (mocked Playwright suite), straight from node_modules,
 * so it never lands in public/ or in a production build.
 */
function mockServiceWorker(): Plugin {
  const file = path.resolve(import.meta.dirname, 'node_modules/msw/lib/mockServiceWorker.js')
  return {
    name: 'skyline-msw-worker',
    configureServer(server) {
      server.middlewares.use('/mockServiceWorker.js', (_req, res) => {
        res.setHeader('Content-Type', 'text/javascript')
        res.end(fs.readFileSync(file))
      })
    },
  }
}

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  // Where the dev server forwards /api. The browser always calls a relative /api/v1 (same origin, no
  // preflight); only this proxy knows the gateway's address.
  const gateway = env.SKYLINE_GATEWAY_URL || 'http://localhost:8000'
  // The proxy makes API calls same-origin, so drop the browser's Origin header: with changeOrigin the
  // gateway would otherwise see a cross-origin request from whatever host the dev server runs on.
  const apiProxy = {
    target: gateway,
    changeOrigin: true,
    configure: (proxy: {
      on: (event: 'proxyReq', fn: (req: { removeHeader: (name: string) => void }) => void) => void
    }) => {
      proxy.on('proxyReq', (req) => req.removeHeader('origin'))
    },
  }

  return {
    plugins: [react(), tailwindcss(), ...(mode === 'msw' ? [mockServiceWorker()] : [])],
    resolve: {
      alias: { '@': path.resolve(import.meta.dirname, 'src') },
    },
    // Pre-bundle every dependency the lazy routes use at startup, so the dev server never re-optimizes
    // and reloads the page when a route is first visited.
    optimizeDeps: { entries: ['index.html', 'src/**/*.{ts,tsx}', '!src/**/*.test.{ts,tsx}'] },
    server: {
      port: 5173,
      strictPort: true,
      proxy: { '/api': apiProxy },
    },
    preview: {
      port: 4173,
      proxy: { '/api': apiProxy },
    },
    build: {
      target: 'es2022',
      sourcemap: true,
    },
    test: {
      globals: true,
      environment: 'jsdom',
      setupFiles: ['./src/test/setup.ts'],
      include: ['src/**/*.test.{ts,tsx}'],
      css: false,
      restoreMocks: true,
    },
  }
})
