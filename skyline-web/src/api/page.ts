import type { Page } from './types'

interface NestedPage<T> {
  content: T[]
  page: { size: number; number: number; totalElements: number; totalPages: number }
}

function isNested<T>(value: unknown): value is NestedPage<T> {
  return (
    typeof value === 'object' && value !== null && 'page' in value && typeof (value as NestedPage<T>).page === 'object'
  )
}

/**
 * Normalizes a Spring page. Accepts the classic PageImpl shape and the newer VIA_DTO shape
 * ({ content, page: { size, number, totalElements, totalPages } }), in case the API switches modes.
 */
export function parsePage<T>(raw: unknown): Page<T> {
  if (raw === null || raw === undefined || typeof raw !== 'object') {
    return emptyPage<T>()
  }
  if (isNested<T>(raw)) {
    const { size, number, totalElements, totalPages } = raw.page
    const content = raw.content ?? []
    return {
      content,
      size,
      number,
      totalElements,
      totalPages,
      first: number === 0,
      last: totalPages === 0 || number >= totalPages - 1,
      empty: content.length === 0,
      numberOfElements: content.length,
    }
  }
  const page = raw as Partial<Page<T>>
  const content = page.content ?? []
  const number = page.number ?? 0
  const totalPages = page.totalPages ?? (content.length ? 1 : 0)
  return {
    content,
    size: page.size ?? content.length,
    number,
    totalElements: page.totalElements ?? content.length,
    totalPages,
    first: page.first ?? number === 0,
    last: page.last ?? (totalPages === 0 || number >= totalPages - 1),
    empty: page.empty ?? content.length === 0,
    numberOfElements: page.numberOfElements ?? content.length,
  }
}

export function emptyPage<T>(): Page<T> {
  return {
    content: [],
    size: 0,
    number: 0,
    totalElements: 0,
    totalPages: 0,
    first: true,
    last: true,
    empty: true,
    numberOfElements: 0,
  }
}
