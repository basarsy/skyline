import { Link } from 'react-router'

import { Page } from '@/components/layout/app-shell'
import { PlaneGlyph } from '@/components/skyline/brand'
import { Button } from '@/components/ui/button'
import { useDocumentTitle } from '@/lib/hooks'

export function Component() {
  useDocumentTitle('Page not found')
  return (
    <Page className="flex min-h-[60vh] flex-col items-start justify-center gap-6">
      <span className="micro-label">Error 404</span>
      <h1 className="flex items-center gap-4 text-display-sm font-semibold tracking-[-0.035em] sm:text-display">
        Off the map.
        <PlaneGlyph className="size-10 rotate-45 text-muted-foreground" />
      </h1>
      <p className="max-w-md text-muted-foreground">
        We couldn’t find this page. It may have moved, or the link may be wrong.
      </p>
      <div className="flex flex-wrap gap-3">
        <Button asChild>
          <Link to="/">Search flights</Link>
        </Button>
        <Button asChild variant="outline">
          <Link to="/manage">Manage booking</Link>
        </Button>
      </div>
    </Page>
  )
}
