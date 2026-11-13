/** PNRs are 6 characters from an alphabet without the look-alikes 0, O, 1 and I. */
export const PNR_ALPHABET = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'
export const PNR_LENGTH = 6
const AMBIGUOUS = /[0O1I]/

/** Uppercases and strips everything that can't be part of a PNR input (spaces, dashes). */
export function normalizePnr(value: string) {
  return value
    .toUpperCase()
    .replace(/[^A-Z0-9]/g, '')
    .slice(0, PNR_LENGTH)
}

export function isValidPnr(value: string) {
  return value.length === PNR_LENGTH && [...value].every((char) => PNR_ALPHABET.includes(char))
}

/** True when the input contains a character PNRs never use, to show the "never contain 0, O, 1 or I" hint. */
export function hasAmbiguousCharacter(value: string) {
  return AMBIGUOUS.test(value.toUpperCase())
}

export const PNR_HINT = 'PNRs never contain 0, O, 1 or I'
