# skyline-web

The web front-end for Skyline: flight search, booking, trips, online check-in and the operations console.
A single-page app (Vite, React 19, TypeScript, Tailwind CSS v4, shadcn/ui) that talks to the Skyline API
gateway under `/api/v1`.

## Setup

Requirements: Node 22+ and pnpm 10.

```bash
cd skyline-web
pnpm install
cp .env.example .env.local   # optional; the defaults work with the docker compose stack
pnpm dev                     # http://localhost:5173
```

The dev server proxies `/api` to the gateway (`SKYLINE_GATEWAY_URL`, default `http://localhost:8000`), so
start the back-end first (`docker compose up -d` in the repository root). The browser always calls the
relative `/api/v1`: same origin, no CORS preflight.

## Scripts

| Script                         | What it does                                                             |
| ------------------------------ | ------------------------------------------------------------------------ |
| `pnpm dev`                     | Dev server on port 5173 with the API proxy                               |
| `pnpm build`                   | Type-check and build to `dist/`                                          |
| `pnpm preview`                 | Serve the production build (port 4173, same proxy)                       |
| `pnpm typecheck`               | TypeScript, no emit                                                      |
| `pnpm lint`                    | ESLint (typescript-eslint, react-hooks, jsx-a11y), zero warnings allowed |
| `pnpm format` / `format:check` | Prettier with the Tailwind class sorter                                  |
| `pnpm test`                    | Vitest + Testing Library + MSW                                           |
| `pnpm e2e`                     | Playwright against the mocked API (MSW in the browser)                   |
| `pnpm e2e:live`                | Playwright against the real stack (`E2E_LIVE=1`)                         |

## Environment variables

| Variable              | Default                 | Purpose                                                                                                                                                     |
| --------------------- | ----------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `VITE_API_BASE`       | `/api/v1`               | Base of every API call. An absolute URL (e.g. `http://localhost:8000/api/v1`) also works and relies on the gateway's CORS (`SKYLINE_CORS_ALLOWED_ORIGINS`). |
| `SKYLINE_GATEWAY_URL` | `http://localhost:8000` | Where the dev/preview server proxies `/api`. Never shipped to the browser.                                                                                  |

## Product limits

These are deliberate product decisions in the API, and the UI is designed around them:

1. **No seat selection.** Seats are assigned automatically at check-in ("Your seat is assigned at check-in").
2. **Staff and admins can't book.** `POST /reservations` is passenger-only; for those roles, flight results
   offer "View in ops" instead of "Select".
3. **No server-side logout.** Logging out clears the tokens in this browser; a refresh token stays valid
   until it expires.
4. **Search dates are UTC days.** The date picker says so. A flight leaving at 01:00 Istanbul time on the
   2nd departs at 22:00 UTC on the 1st, so it's listed under the 1st.

## Security note

The access token is kept in memory only. The refresh token is kept in `localStorage` (`skyline.rt`) so a
reload can restore the session. An httpOnly cookie would be safer against XSS, but the API issues tokens in
the response body and has no cookie-based refresh, so that isn't possible without back-end changes.
