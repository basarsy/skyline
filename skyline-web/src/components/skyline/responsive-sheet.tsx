import type * as React from 'react'

import {
  Drawer,
  DrawerContent,
  DrawerDescription,
  DrawerFooter,
  DrawerHeader,
  DrawerTitle,
} from '@/components/ui/drawer'
import { Sheet, SheetContent, SheetDescription, SheetFooter, SheetHeader, SheetTitle } from '@/components/ui/sheet'
import { cn } from '@/lib/cn'
import { useIsDesktop } from '@/lib/hooks'

interface ResponsiveSheetProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  title: string
  description?: React.ReactNode
  children: React.ReactNode
  /** Sticky footer, usually the primary action. */
  footer?: React.ReactNode
  className?: string
}

/** Short tasks open in a right-side Sheet on desktop (≥ md) and a bottom Drawer on mobile. */
export function ResponsiveSheet({
  open,
  onOpenChange,
  title,
  description,
  children,
  footer,
  className,
}: ResponsiveSheetProps) {
  const desktop = useIsDesktop()

  if (desktop) {
    return (
      <Sheet open={open} onOpenChange={onOpenChange}>
        <SheetContent className={cn('gap-0 p-0', className)}>
          <SheetHeader className="border-b border-border p-6">
            <SheetTitle className="font-display text-h3 font-semibold tracking-[-0.02em]">{title}</SheetTitle>
            {description ? (
              <SheetDescription>{description}</SheetDescription>
            ) : (
              <SheetDescription className="sr-only">{title}</SheetDescription>
            )}
          </SheetHeader>
          <div className="flex-1 overflow-y-auto p-6">{children}</div>
          {footer && <SheetFooter className="border-t border-border p-6">{footer}</SheetFooter>}
        </SheetContent>
      </Sheet>
    )
  }

  return (
    <Drawer open={open} onOpenChange={onOpenChange}>
      <DrawerContent className={className}>
        <DrawerHeader className="px-5 text-left">
          <DrawerTitle className="font-display text-h3 font-semibold tracking-[-0.02em]">{title}</DrawerTitle>
          {description ? (
            <DrawerDescription>{description}</DrawerDescription>
          ) : (
            <DrawerDescription className="sr-only">{title}</DrawerDescription>
          )}
        </DrawerHeader>
        <div className="overflow-y-auto px-5 pb-4">{children}</div>
        {footer && (
          <DrawerFooter className="border-t border-border px-5 pb-[max(1rem,env(safe-area-inset-bottom))]">
            {footer}
          </DrawerFooter>
        )}
      </DrawerContent>
    </Drawer>
  )
}
