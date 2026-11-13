import type * as React from 'react'

/** Centered narrow card on the plain background, with a large title. */
export function AuthLayout({
  title,
  subtitle,
  children,
}: {
  title: string
  subtitle?: React.ReactNode
  children: React.ReactNode
}) {
  return (
    <div className="mx-auto flex w-full max-w-[440px] flex-col gap-8 px-4 py-12 sm:py-20">
      <div className="flex flex-col gap-2">
        <h1 className="text-h1 font-semibold tracking-[-0.03em]">{title}</h1>
        {subtitle && <p className="text-muted-foreground">{subtitle}</p>}
      </div>
      <div className="rounded-card border border-border bg-card p-5 sm:p-6">{children}</div>
    </div>
  )
}
