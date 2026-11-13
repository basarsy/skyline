import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'

import { toUtcDay } from '@/lib/format'

/** Loaded on first validation (see SearchCard), so zod stays out of the home page's initial bundle. */
export const searchSchema = z
  .object({
    from: z.string({ error: 'Choose where you’re flying from' }).min(1, 'Choose where you’re flying from'),
    to: z.string({ error: 'Choose where you’re flying to' }).min(1, 'Choose where you’re flying to'),
    date: z
      .string({ error: 'Choose a date' })
      .min(1, 'Choose a date')
      .refine((value) => value >= toUtcDay(new Date(Date.now() - 86_400_000)), 'Choose a date that isn’t in the past'),
    cabin: z.enum(['ECONOMY', 'BUSINESS', 'FIRST']),
  })
  .refine((values) => values.from !== values.to, {
    path: ['to'],
    message: 'Choose a destination different from the origin',
  })

export type SearchValues = z.infer<typeof searchSchema>

export const searchResolver = zodResolver(searchSchema)
