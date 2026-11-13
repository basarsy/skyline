import * as React from 'react'

import {
  AlertDialog,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

interface ConfirmDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  title: string
  description: React.ReactNode
  confirmLabel: string
  cancelLabel?: string
  destructive?: boolean
  /** When set, the user must type this exactly (e.g. a flight number) to confirm. */
  typedConfirmation?: string
  /** May return a promise; the dialog shows a spinner and stays open until it settles. */
  onConfirm: () => unknown
  loading?: boolean
  /** Error from the action, shown inside the dialog. */
  error?: string | null
}

/** Confirmation for consequential actions, optionally guarded by typing a value. */
export function ConfirmDialog({
  open,
  onOpenChange,
  title,
  description,
  confirmLabel,
  cancelLabel = 'Keep it',
  destructive,
  typedConfirmation,
  onConfirm,
  loading,
  error,
}: ConfirmDialogProps) {
  const [typed, setTyped] = React.useState('')
  const inputId = React.useId()
  const matches = !typedConfirmation || typed.trim().toUpperCase() === typedConfirmation.toUpperCase()

  return (
    <AlertDialog
      open={open}
      onOpenChange={(next) => {
        if (!next) setTyped('')
        onOpenChange(next)
      }}
    >
      <AlertDialogContent className="gap-6 rounded-card p-6">
        <AlertDialogHeader>
          <AlertDialogTitle className="font-display text-h3 font-semibold tracking-[-0.02em]">{title}</AlertDialogTitle>
          <AlertDialogDescription asChild>
            <div className="flex flex-col gap-2 text-sm text-muted-foreground">{description}</div>
          </AlertDialogDescription>
        </AlertDialogHeader>

        {typedConfirmation && (
          <div className="flex flex-col gap-2">
            <Label htmlFor={inputId}>
              Type <span className="font-mono font-semibold">{typedConfirmation}</span> to confirm
            </Label>
            <Input
              id={inputId}
              value={typed}
              onChange={(event) => setTyped(event.target.value)}
              autoComplete="off"
              spellCheck={false}
              className="font-mono uppercase"
            />
          </div>
        )}

        {error && (
          <p role="alert" className="text-sm text-destructive">
            {error}
          </p>
        )}

        <AlertDialogFooter className="gap-3 sm:gap-3">
          <AlertDialogCancel asChild>
            <Button variant="outline" disabled={loading}>
              {cancelLabel}
            </Button>
          </AlertDialogCancel>
          {/* Not AlertDialogAction: that closes immediately; this waits for the server. */}
          <Button
            variant={destructive ? 'destructive' : 'default'}
            disabled={!matches}
            loading={loading}
            onClick={() => void onConfirm()}
          >
            {confirmLabel}
          </Button>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  )
}
