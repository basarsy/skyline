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

## Architecture

```
src/
  app/          router (lazy route modules), providers, query client, root layout
  api/          fetch client (envelope → data, typed ApiError, single-flight token refresh),
                DTO types, page parser, one endpoints file per service, query-key factories
  auth/         token store (access token in memory, refresh token in localStorage), JWT decode,
                session (boot restore, login, register, logout), route guards
  components/
    ui/         shadcn/ui primitives restyled to the design tokens
    skyline/    domain components (FlightTimeline, FlightCard, PriceTag, BoardingPass, pickers, ...)
    layout/     public shell (frosted header, mobile tab bar), ops shell (sidebar, breadcrumbs)
  features/     one folder per area: search, booking, trips, manage, checkin, auth, ops/*
  lib/          formatting (UTC parsing, airport-local times, money), pricing preview, check-in
                window, flight status machine, PNR rules, theme store, hooks
  styles/       globals.css (all color tokens, light and dark), print.css (boarding pass only)
  test/         MSW handlers (an in-memory back-end), fixtures, test setup
e2e/            Playwright specs
```

- **Data:** every request goes through TanStack Query; the endpoint functions take the query's
  `AbortSignal`. Mutations never retry and invalidate exactly what they change (see `*/queries.ts`).
- **Times:** the API speaks UTC without offsets. `lib/format.ts` parses them as UTC, shows departure in the
  origin airport's zone and arrival in the destination's (with `+1` when the local date changes), and turns
  airport-local form input back into UTC strings (DST-aware, unit-tested).
- **Server is the authority:** check-in windows, status transitions and prices are previews; the UI always
  shows the server's answer and message.
- **Bundle:** every screen is a lazy route. Pickers (Radix Popover, cmdk, react-day-picker), the account
  menu, the toaster, motion's animation features, zod for the search form and the PDF417 encoder all load on
  demand. Initial JS (gzipped, before `load`): `/` 198 kB, `/flights` 198 kB, `/login` 186 kB.

## Design system

Tokens in OKLCH for light and dark live only in `src/styles/globals.css` and are mapped through `@theme inline`;
components use semantic classes (`bg-card`, `text-muted-foreground`, `bg-brand-soft`). The palette is cool,
faintly blue-tinted neutrals with one brand hue (indigo-blue `primary`, with `brand-soft` / `brand-line` tints)
and a warm `spark` accent used only as decoration. Status hues (`success`, `warning`, `destructive`) appear
as small dots or text next to a label, so status never depends on color. Corners are squared off (6px inputs and
buttons, 10px cards) and the layout is dense, the way airline sites are.

- `brand-band` is the solid royal-blue strip behind the header, the results header and the short task pages
  (`BandPage`: log in, sign up, manage booking, check-in). `on-band` and `on-footer` remap the semantic tokens
  inside the band and the dark footer, so ordinary components read light-on-dark there without extra classes.
- The home page leads with a photo (`hero-scrim` darkens it behind the headline) and a tabbed booking box
  (Flights, Manage booking, Check-in; the last two load on first use). "Where we fly" shows every airport with
  its photo; airports without one get a `dest-art-0`...`dest-art-5` gradient.

The theme follows the OS by default; logged-in users choose System / Light / Dark in the account menu (visitors
cycle it from the header). An inline script in `index.html` applies it before first paint.

`/styleguide` (development only) shows every token, the type scale and each component state in both themes,
and measures the contrast pairs below in the browser.

### Contrast

Measured WCAG ratios (target: 4.5:1 for text; 3:1 is the floor for the functional hues, which also clear 4.5:1):

| Text / surface                | Light   | Dark    |
| ----------------------------- | ------- | ------- |
| foreground / background       | 17.00:1 | 17.93:1 |
| foreground / card             | 17.75:1 | 16.61:1 |
| muted-foreground / card       | 6.00:1  | 7.04:1  |
| muted-foreground / background | 5.75:1  | 7.59:1  |
| muted-foreground / elevated   | 5.43:1  | 6.31:1  |
| primary-foreground / primary  | 6.05:1  | 7.60:1  |
| primary / card                | 6.05:1  | 7.20:1  |
| primary / brand-soft          | 5.20:1  | 5.83:1  |
| destructive / card            | 5.26:1  | 6.35:1  |
| success / card                | 4.62:1  | 8.49:1  |
| warning / card (dot only)     | 4.83:1  | 9.54:1  |
| band-foreground / band        | 11.28:1 | 13.98:1 |
| band-muted / band             | 7.36:1  | 9.13:1  |
| footer-muted / footer         | 8.68:1  | 8.68:1  |

## Testing

```bash
pnpm test        # Vitest: client, envelope and field errors, page shapes, JWT, single-flight refresh,
                 # time zones and DST, pricing, check-in window, status machine
pnpm e2e         # Playwright against the mocked API (starts `vite --mode msw` itself)
```

The mocked suite covers the passenger journey, PNR lookup, the ops lifecycle, and an accessibility and layout
sweep: axe (no serious or critical issues) on every route in both themes, no horizontal scroll at 375, 768,
1024 and 1440px, and reduced motion. MSW only exists in `--mode msw`; it is not part of production builds.

### Against the live stack

```bash
# repository root: start everything, including the web app on http://localhost:5173
docker compose up -d --build
set -a; source .env; set +a          # SKYLINE_ADMIN_EMAIL / SKYLINE_ADMIN_PASSWORD

cd skyline-web
E2E_LIVE=1 E2E_BASE_URL=http://localhost:5173 pnpm exec playwright test
```

Without `E2E_BASE_URL` the live mode starts the Vite dev server on port 5173 (proxying to the gateway on
8000), so stop the `web` container first. Live specs create their own aircraft, flights, accounts and crew
and tolerate data left by earlier runs; the mock-only specs are skipped.

## Docker

`Dockerfile` builds the app with Node and serves it with nginx (`nginx.conf`): SPA fallback, `/api/`
proxied to the gateway (`SKYLINE_GATEWAY_URL`, default `http://gateway-service:8000`), immutable caching for
hashed assets, `no-cache` for `index.html`, gzip, and `/healthz`. In `docker compose` it is the `web` service
on port 5173.

## Photo credits

Photos from [Unsplash](https://unsplash.com), used under the [Unsplash License](https://unsplash.com/license),
resized to WebP in `src/assets/photos`:

- Hero, airplane wing over clouds: [Johnny Goerend](https://unsplash.com/photos/KB9r_hTzyeQ)
- Istanbul, Galata Tower: [Anna Berdnik](https://unsplash.com/photos/0n0AHB1fgTQ)
- London, Tower Bridge: [Charles Postiaux](https://unsplash.com/photos/Q6UehpkBSnQ)
- New York, Manhattan and the Brooklyn Bridge: [Patrick Tomasso](https://unsplash.com/photos/SVVTZtTGyaU)
