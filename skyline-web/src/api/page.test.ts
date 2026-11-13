import { describe, expect, it } from 'vitest'

import { parsePage } from './page'

describe('parsePage', () => {
  it('reads the classic Spring PageImpl shape', () => {
    const page = parsePage<number>({
      content: [1, 2],
      totalElements: 12,
      totalPages: 6,
      number: 2,
      size: 2,
      first: false,
      last: false,
      empty: false,
      numberOfElements: 2,
    })

    expect(page).toMatchObject({ content: [1, 2], totalElements: 12, totalPages: 6, number: 2, last: false })
  })

  it('reads the newer { content, page: {...} } shape', () => {
    const page = parsePage<string>({
      content: ['a'],
      page: { size: 20, number: 1, totalElements: 21, totalPages: 2 },
    })

    expect(page).toEqual({
      content: ['a'],
      size: 20,
      number: 1,
      totalElements: 21,
      totalPages: 2,
      first: false,
      last: true,
      empty: false,
      numberOfElements: 1,
    })
  })

  it('treats a missing body as an empty page', () => {
    expect(parsePage(null)).toMatchObject({ content: [], empty: true, last: true })
  })
})
