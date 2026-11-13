import { setupServer } from 'msw/node'

import { handlers } from './handlers'

/** MSW for unit/integration tests. Tests override handlers per case with server.use(...). */
export const server = setupServer(...handlers)
