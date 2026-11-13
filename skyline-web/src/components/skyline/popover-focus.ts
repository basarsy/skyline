/**
 * onCloseAutoFocus for picker popovers. Radix returns focus to the trigger once a popover has finished
 * closing. If the user has meanwhile opened another picker, focus already sits in that one: pulling it
 * back would make the new popover close as "focus moved outside". Only restore when focus is nowhere.
 */
export function keepFocusIfMoved(event: Event) {
  const active = document.activeElement
  const closing = event.target instanceof Node ? event.target : null
  // Focus still inside the closing popover (or nowhere) is the normal case: let Radix restore it.
  if (active && active !== document.body && !closing?.contains(active)) event.preventDefault()
}
