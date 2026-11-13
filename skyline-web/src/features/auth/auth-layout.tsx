import type * as React from 'react'

import { SkyPage } from '@/components/layout/app-shell'

/** Log in and sign up: the night-sky title band with the form card overlapping it. */
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
    <SkyPage title={title} subtitle={subtitle} width="max-w-[440px]">
      <div className="rounded-card border border-border bg-card p-5 shadow-float sm:p-6">{children}</div>
    </SkyPage>
  )
}
