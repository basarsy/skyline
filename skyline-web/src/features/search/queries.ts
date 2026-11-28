import { infiniteQueryOptions, useInfiniteQuery, useQuery, useQueryClient } from '@tanstack/react-query'

import { networkApi } from '@/api/endpoints/network'
import { scheduleApi, type FlightSearchParams } from '@/api/endpoints/schedule'
import { flightKeys, networkKeys } from '@/api/query-keys'
import { STATIC_STALE_TIME } from '@/app/query-client'

export const SEARCH_PAGE_SIZE = 10

type SearchKey = Omit<FlightSearchParams, 'page' | 'size'>

function searchPage(params: SearchKey, page: number, signal?: AbortSignal) {
  return scheduleApi.search({ ...params, page, size: SEARCH_PAGE_SIZE }, signal)
}

/** One definition of a search's paged query, shared by the hook and the prefetch so they can't drift apart. */
function searchOptions(params: SearchKey) {
  return infiniteQueryOptions({
    queryKey: flightKeys.search(params),
    queryFn: ({ pageParam, signal }) => searchPage(params, pageParam, signal),
    initialPageParam: 0,
    getNextPageParam: (last) => (last.last ? undefined : last.number + 1),
  })
}

/** Flight search with "Load more" pagination. Disabled until all three parameters are present. */
export function useFlightSearch(params: SearchKey | null) {
  return useInfiniteQuery({
    ...searchOptions(params ?? { origin: '', destination: '', date: '' }),
    enabled: !!params,
  })
}

/** Warms the cache for a neighbouring day (date strip hover): its first page. */
export function usePrefetchSearch() {
  const queryClient = useQueryClient()
  return (params: SearchKey) => queryClient.prefetchInfiniteQuery(searchOptions(params))
}

export function useFlight(id: string | undefined, options: { refetchInterval?: number } = {}) {
  return useQuery({
    queryKey: flightKeys.detail(id ?? ''),
    queryFn: ({ signal }) => scheduleApi.flight(id!, signal),
    enabled: !!id,
    ...options,
  })
}

export function useRoutes() {
  return useQuery({
    queryKey: networkKeys.routes,
    queryFn: ({ signal }) => networkApi.routes(signal),
    staleTime: STATIC_STALE_TIME,
  })
}
