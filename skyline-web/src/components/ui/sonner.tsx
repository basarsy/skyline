import type * as React from 'react'
import { CircleAlert, CircleCheck, Info, Loader2, TriangleAlert } from 'lucide-react'
import { Toaster as Sonner, type ToasterProps } from 'sonner'

import { useTheme } from '@/lib/theme'

const iconProps = { className: 'size-4', strokeWidth: 1.75, 'aria-hidden': true } as const

/** Toasts as small floating cards; success/error tint only the icon. */
function Toaster(props: ToasterProps) {
  const { resolved } = useTheme()
  return (
    <Sonner
      theme={resolved}
      className="toaster group"
      position="bottom-center"
      offset={{ bottom: 96 }}
      mobileOffset={{ bottom: 96 }}
      icons={{
        success: <CircleCheck {...iconProps} className="size-4 text-success" />,
        info: <Info {...iconProps} />,
        warning: <TriangleAlert {...iconProps} />,
        error: <CircleAlert {...iconProps} className="size-4 text-destructive" />,
        loading: <Loader2 {...iconProps} className="size-4 animate-spin" />,
      }}
      toastOptions={{
        classNames: {
          toast: 'rounded-2xl! border-border! bg-card! text-card-foreground! shadow-float! font-sans',
          description: 'text-muted-foreground!',
        },
      }}
      style={
        {
          '--normal-bg': 'var(--card)',
          '--normal-text': 'var(--card-foreground)',
          '--normal-border': 'var(--border)',
          '--border-radius': '16px',
        } as React.CSSProperties
      }
      {...props}
    />
  )
}

export { Toaster }
