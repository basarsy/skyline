import * as React from 'react'
import { Download, Printer } from 'lucide-react'
import { toast } from 'sonner'

import type { BoardingPassResponse } from '@/api/types'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/cn'
import { formatDateInZone, formatLocal, formatTimeInZone } from '@/lib/format'
import { PlaneGlyph } from './brand'

interface BoardingPassProps {
  pass: BoardingPassResponse
  /** IANA zone of the origin airport, so boarding and departure show in local time. */
  originZone?: string | null
  className?: string
  /** Hide the download/print actions (e.g. in the style guide). */
  showActions?: boolean
}

type BarcodeState = 'loading' | 'ready' | 'failed'

/** PDF417 drawn by bwip-js, loaded on demand so the encoder stays out of the initial bundle. */
function Pdf417({
  data,
  canvasRef,
  onState,
}: {
  data: string
  canvasRef: React.RefObject<HTMLCanvasElement | null>
  onState: (state: BarcodeState) => void
}) {
  React.useEffect(() => {
    let cancelled = false
    onState('loading')
    import('bwip-js/browser')
      .then(({ toCanvas }) => {
        if (cancelled || !canvasRef.current) return
        toCanvas(canvasRef.current, {
          bcid: 'pdf417',
          text: data,
          scale: 3,
          height: 12,
          padding: 0,
        })
        onState('ready')
      })
      .catch(() => {
        if (!cancelled) onState('failed')
      })
    return () => {
      cancelled = true
    }
  }, [data, canvasRef, onState])

  return (
    <canvas
      ref={canvasRef}
      role="img"
      aria-label={`PDF417 barcode encoding ${data}`}
      className="block h-auto max-h-28 w-full object-contain [image-rendering:pixelated]"
    />
  )
}

function Cell({ label, value, hero }: { label: string; value: React.ReactNode; hero?: boolean }) {
  return (
    <div className="flex flex-col gap-1">
      <span className="text-[0.6875rem] font-medium tracking-[0.08em] text-pass-muted uppercase">{label}</span>
      <span className={cn('hero-number', hero ? 'text-[2rem]' : 'text-xl')}>{value}</span>
    </div>
  )
}

/**
 * Ticket-shaped boarding pass: always black on white, perforated between the flight details and the
 * barcode. Printing shows only the pass (print.css); "Download as image" renders it to PNG.
 */
export function BoardingPass({ pass, originZone, className, showActions = true }: BoardingPassProps) {
  const barcodeRef = React.useRef<HTMLCanvasElement>(null)
  const [barcode, setBarcode] = React.useState<BarcodeState>('loading')
  const boarding = formatTimeInZone(pass.boardingTime, originZone)
  const departs = formatTimeInZone(pass.departureTime, originZone)
  const date = formatDateInZone(pass.departureTime, originZone, 'EEE d MMM yyyy')

  function download() {
    try {
      const png = renderPassToPng(pass, { boarding, departs, date }, barcodeRef.current)
      const link = document.createElement('a')
      link.href = png
      link.download = `skyline-boarding-pass-${pass.pnr}.png`
      link.click()
    } catch {
      toast.error('Couldn’t create the image. Try printing instead.')
    }
  }

  return (
    <div className={cn('flex flex-col items-center gap-5', className)}>
      <article
        aria-label={`Boarding pass for ${pass.passengerName}, flight ${pass.flightNumber}`}
        className="print-area w-full max-w-[400px] overflow-hidden rounded-card bg-pass text-pass-foreground shadow-float ring-1 ring-border"
      >
        <div className="flex flex-col gap-6 p-6">
          <div className="flex items-center justify-between">
            <span className="inline-flex items-center gap-2 font-display text-lg font-bold tracking-[-0.04em]">
              <PlaneGlyph className="size-4" />
              skyline
            </span>
            <span className="font-mono text-sm font-semibold">{pass.flightNumber}</span>
          </div>

          <div className="flex items-end justify-between gap-4">
            <span className="hero-number text-[3.25rem]">{pass.origin ?? '—'}</span>
            <PlaneGlyph className="mb-4 size-5 rotate-45" />
            <span className="hero-number text-[3.25rem]">{pass.destination ?? '—'}</span>
          </div>

          <div className="flex flex-col gap-1">
            <span className="text-[0.6875rem] font-medium tracking-[0.08em] text-pass-muted uppercase">Passenger</span>
            <span className="text-lg font-semibold">{pass.passengerName}</span>
            <span className="text-sm text-pass-muted">{date}</span>
          </div>

          <div className="grid grid-cols-2 gap-x-6 gap-y-5">
            <Cell label="Gate" value={pass.gate} hero />
            <Cell label="Seat" value={pass.seatNumber} hero />
            <Cell label="Boarding" value={boarding} />
            <Cell label="Departs" value={departs} />
          </div>
        </div>

        <div className="pass-perforation" aria-hidden />

        <div className="flex flex-col items-center gap-3 px-6 pt-2 pb-6">
          <Pdf417 data={pass.barcodeData} canvasRef={barcodeRef} onState={setBarcode} />
          {barcode === 'failed' && <p className="text-sm text-pass-muted">The barcode couldn’t be drawn.</p>}
          <span className="font-mono text-xs tracking-[0.08em]">{pass.barcodeData}</span>
          <span className="text-[0.6875rem] text-pass-muted">Issued {formatLocal(pass.issuedAt)}</span>
        </div>
      </article>

      {showActions && (
        <div className="no-print flex flex-wrap justify-center gap-3">
          <Button variant="outline" onClick={download} disabled={barcode === 'loading'}>
            <Download strokeWidth={1.75} aria-hidden />
            Download as image
          </Button>
          <Button variant="outline" onClick={() => window.print()}>
            <Printer strokeWidth={1.75} aria-hidden />
            Print
          </Button>
        </div>
      )}
    </div>
  )
}

/** A design token's computed value (canvas understands oklch()). */
function token(name: string) {
  return getComputedStyle(document.documentElement).getPropertyValue(name).trim()
}

/** Draws the pass on a canvas and returns a PNG data URL (no DOM-to-image library needed). */
function renderPassToPng(
  pass: BoardingPassResponse,
  times: { boarding: string; departs: string; date: string },
  barcode: HTMLCanvasElement | null,
) {
  const width = 800
  const height = 1240
  const canvas = document.createElement('canvas')
  canvas.width = width
  canvas.height = height
  const ctx = canvas.getContext('2d')
  if (!ctx) throw new Error('Canvas unavailable')

  const ink = token('--pass-foreground')
  const muted = token('--pass-muted')
  const line = token('--pass-line')
  const display = "'Inter Tight Variable', 'Inter Variable', system-ui, sans-serif"
  const mono = "'JetBrains Mono Variable', ui-monospace, monospace"

  ctx.fillStyle = token('--pass')
  ctx.fillRect(0, 0, width, height)
  ctx.fillStyle = ink
  ctx.textBaseline = 'alphabetic'

  ctx.font = `700 40px ${display}`
  ctx.fillText('skyline', 56, 100)
  ctx.font = `600 32px ${mono}`
  ctx.textAlign = 'right'
  ctx.fillText(pass.flightNumber, width - 56, 100)

  ctx.font = `600 132px ${display}`
  ctx.textAlign = 'left'
  ctx.fillText(pass.origin ?? '—', 48, 270)
  ctx.textAlign = 'right'
  ctx.fillText(pass.destination ?? '—', width - 48, 270)

  const label = (text: string, x: number, y: number, align: CanvasTextAlign = 'left') => {
    ctx.textAlign = align
    ctx.fillStyle = muted
    ctx.font = `500 22px ${display}`
    ctx.fillText(text.toUpperCase(), x, y)
    ctx.fillStyle = ink
  }
  const value = (text: string, x: number, y: number, size: number) => {
    ctx.textAlign = 'left'
    ctx.font = `600 ${size}px ${display}`
    ctx.fillText(text, x, y)
  }

  label('Passenger', 56, 350)
  value(pass.passengerName, 56, 395, 38)
  ctx.fillStyle = muted
  ctx.font = `400 26px ${display}`
  ctx.fillText(times.date, 56, 435)
  ctx.fillStyle = ink

  label('Gate', 56, 520)
  value(pass.gate, 56, 590, 72)
  label('Seat', 420, 520)
  value(pass.seatNumber, 420, 590, 72)
  label('Boarding', 56, 660)
  value(times.boarding, 56, 712, 46)
  label('Departs', 420, 660)
  value(times.departs, 420, 712, 46)

  // Perforation
  ctx.strokeStyle = line
  ctx.setLineDash([14, 12])
  ctx.lineWidth = 2
  ctx.beginPath()
  ctx.moveTo(40, 790)
  ctx.lineTo(width - 40, 790)
  ctx.stroke()
  ctx.setLineDash([])

  if (barcode && barcode.width > 0) {
    const maxWidth = width - 112
    const scale = Math.min(maxWidth / barcode.width, 220 / barcode.height)
    const w = barcode.width * scale
    const h = barcode.height * scale
    ctx.imageSmoothingEnabled = false
    ctx.drawImage(barcode, (width - w) / 2, 840, w, h)
  }

  ctx.textAlign = 'center'
  ctx.font = `500 24px ${mono}`
  ctx.fillText(pass.barcodeData, width / 2, 1120)
  ctx.fillStyle = muted
  ctx.font = `400 20px ${display}`
  ctx.fillText(`Issued ${formatLocal(pass.issuedAt)}`, width / 2, 1160)

  return canvas.toDataURL('image/png')
}
