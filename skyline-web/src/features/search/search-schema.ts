import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'

import { addIsoDays, toUtcDay } from '@/lib/format'

/** Loaded on first validation (see SearchCard), so zod stays out of the home page's initial bundle. */
export const searchSchema = z
  .object({
    from: z.string({ error: 'Choose where you’re flying from' }).min(1, 'Choose where you’re flying from'),
    to: z.string({ error: 'Choose where you’re flying to' }).min(1, 'Choose where you’re flying to'),
    date: z
      .string({ error: 'Choose a date' })
      .min(1, 'Choose a date')
      // Dates are days at the origin airport, which is at most a day behind UTC (UTC−12): this rejects days
      // that are past everywhere. The date picker holds the line at the origin's own today.
      .refine((value) => value >= addIsoDays(toUtcDay(new Date()), -1), 'Choose a date that isn’t in the past'),
    cabin: z.enum(['ECONOMY', 'BUSINESS', 'FIRST']),
  })
  .refine((values) => values.from !== values.to, {
    path: ['to'],
    message: 'Choose a destination different from the origin',
  })

export type SearchValues = z.infer<typeof searchSchema>

export const searchResolver = zodResolver(searchSchema)
