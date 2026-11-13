import { api, command } from '../client'
import type { BoardingPassResponse } from '../types'

/** skyline-checkin: online check-in and boarding passes. */
export const checkinApi = {
  /** Idempotent: a repeat returns the same pass. */
  checkIn: (pnr: string) => command.post<BoardingPassResponse>('/checkin', { pnr }),
  boardingPass: (pnr: string, signal?: AbortSignal) =>
    api.get<BoardingPassResponse>(`/checkin/${encodeURIComponent(pnr)}/boarding-pass`, { signal }),
}
