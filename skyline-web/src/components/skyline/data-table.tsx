import * as React from 'react'
import {
  flexRender,
  getCoreRowModel,
  getSortedRowModel,
  useReactTable,
  type ColumnDef,
  type OnChangeFn,
  type Row,
  type SortingState,
} from '@tanstack/react-table'
import { ArrowDown, ArrowUp, ArrowUpDown } from 'lucide-react'

import { Skeleton } from '@/components/ui/skeleton'
import { cn } from '@/lib/cn'

interface DataTableProps<T> {
  columns: ColumnDef<T, unknown>[]
  data: T[]
  /** Accessible name of the table. */
  label: string
  loading?: boolean
  /** Rows of skeleton while loading (reserves the final height). */
  skeletonRows?: number
  /** Rendered instead of the body when there are no rows. */
  empty?: React.ReactNode
  /** Makes rows actionable: click, or Enter/Space when focused. */
  onRowClick?: (row: T) => void
  rowLabel?: (row: T) => string
  getRowId?: (row: T) => string
  /** Server-side sorting: pass the state and handler, and rows are not sorted locally. */
  sorting?: SortingState
  onSortingChange?: OnChangeFn<SortingState>
  className?: string
}

/**
 * Dense ops table: sticky header, sortable headers (aria-sort), 52px rows at 14px, and keyboard row
 * navigation: Up/Down/Home/End move between rows, Enter opens.
 */
export function DataTable<T>({
  columns,
  data,
  label,
  loading,
  skeletonRows = 6,
  empty,
  onRowClick,
  rowLabel,
  getRowId,
  sorting: controlledSorting,
  onSortingChange,
  className,
}: DataTableProps<T>) {
  const [localSorting, setLocalSorting] = React.useState<SortingState>([])
  const manualSorting = !!onSortingChange
  const bodyRef = React.useRef<HTMLTableSectionElement>(null)

  // TanStack Table returns functions that the React Compiler must not memoize.
  // eslint-disable-next-line react-hooks/incompatible-library
  const table = useReactTable({
    data,
    columns,
    state: { sorting: controlledSorting ?? localSorting },
    onSortingChange: onSortingChange ?? setLocalSorting,
    manualSorting,
    getCoreRowModel: getCoreRowModel(),
    getSortedRowModel: manualSorting ? undefined : getSortedRowModel(),
    getRowId,
  })

  function focusRow(index: number) {
    const rows = bodyRef.current?.querySelectorAll<HTMLTableRowElement>('tr[data-row]')
    if (!rows?.length) return
    rows[Math.max(0, Math.min(rows.length - 1, index))]?.focus()
  }

  function onRowKeyDown(event: React.KeyboardEvent<HTMLTableRowElement>, row: Row<T>, index: number) {
    switch (event.key) {
      case 'ArrowDown':
        event.preventDefault()
        focusRow(index + 1)
        break
      case 'ArrowUp':
        event.preventDefault()
        focusRow(index - 1)
        break
      case 'Home':
        event.preventDefault()
        focusRow(0)
        break
      case 'End':
        event.preventDefault()
        focusRow(Number.MAX_SAFE_INTEGER)
        break
      case 'Enter':
      case ' ':
        if (event.target === event.currentTarget && onRowClick) {
          event.preventDefault()
          onRowClick(row.original)
        }
        break
    }
  }

  const rows = table.getRowModel().rows
  const columnCount = table.getVisibleLeafColumns().length

  return (
    <div className={cn('overflow-hidden rounded-card border border-border bg-card', className)}>
      <div className="max-h-[70vh] overflow-auto">
        <table className="w-full border-collapse text-sm" aria-label={label} aria-busy={loading || undefined}>
          <thead className="sticky top-0 z-10 bg-card">
            {table.getHeaderGroups().map((group) => (
              <tr key={group.id} className="border-b border-border">
                {group.headers.map((header) => {
                  const sortable = header.column.getCanSort()
                  const direction = header.column.getIsSorted()
                  return (
                    <th
                      key={header.id}
                      scope="col"
                      aria-sort={direction === 'asc' ? 'ascending' : direction === 'desc' ? 'descending' : undefined}
                      className="h-11 px-4 text-left align-middle text-xs font-medium tracking-[0.06em] whitespace-nowrap text-muted-foreground uppercase"
                    >
                      {header.isPlaceholder ? null : sortable ? (
                        <button
                          type="button"
                          onClick={header.column.getToggleSortingHandler()}
                          className="-mx-2 inline-flex h-9 cursor-pointer items-center gap-1.5 rounded-full px-2 uppercase focus-ring outline-none hover:text-foreground"
                        >
                          {flexRender(header.column.columnDef.header, header.getContext())}
                          {direction === 'asc' ? (
                            <ArrowUp className="size-3.5" strokeWidth={1.75} aria-hidden />
                          ) : direction === 'desc' ? (
                            <ArrowDown className="size-3.5" strokeWidth={1.75} aria-hidden />
                          ) : (
                            <ArrowUpDown className="size-3.5 opacity-50" strokeWidth={1.75} aria-hidden />
                          )}
                        </button>
                      ) : (
                        flexRender(header.column.columnDef.header, header.getContext())
                      )}
                    </th>
                  )
                })}
              </tr>
            ))}
          </thead>
          <tbody ref={bodyRef}>
            {loading ? (
              Array.from({ length: skeletonRows }, (_, i) => (
                <tr key={i} className="h-[52px] border-b border-border last:border-0">
                  {Array.from({ length: columnCount }, (__, j) => (
                    <td key={j} className="px-4">
                      <Skeleton className="h-4 w-full max-w-32" />
                    </td>
                  ))}
                </tr>
              ))
            ) : rows.length === 0 ? (
              <tr>
                <td colSpan={columnCount} className="p-4">
                  {empty ?? <p className="py-10 text-center text-muted-foreground">Nothing here yet.</p>}
                </td>
              </tr>
            ) : (
              rows.map((row, index) => (
                <tr
                  key={row.id}
                  data-row
                  tabIndex={onRowClick ? 0 : -1}
                  aria-label={onRowClick && rowLabel ? rowLabel(row.original) : undefined}
                  onClick={onRowClick ? () => onRowClick(row.original) : undefined}
                  onKeyDown={(event) => onRowKeyDown(event, row, index)}
                  className={cn(
                    'h-[52px] border-b border-border transition-colors duration-150 outline-none last:border-0',
                    onRowClick && 'cursor-pointer hover:bg-elevated focus-visible:bg-elevated',
                    'focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-ring',
                  )}
                >
                  {row.getVisibleCells().map((cell) => (
                    <td key={cell.id} className="px-4 align-middle whitespace-nowrap tabular">
                      {flexRender(cell.column.columnDef.cell, cell.getContext())}
                    </td>
                  ))}
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </div>
  )
}

export type { ColumnDef, SortingState }
