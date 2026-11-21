// Apply the theme before first paint so the page never flashes. Same key as src/lib/theme.ts.
// A separate file (not inline in index.html) so the Content-Security-Policy can stay script-src 'self'.
;(function () {
  try {
    var stored = localStorage.getItem('skyline.theme')
    var dark = stored === 'dark' || (stored !== 'light' && window.matchMedia('(prefers-color-scheme: dark)').matches)
    var root = document.documentElement
    if (dark) root.classList.add('dark')
    root.style.colorScheme = dark ? 'dark' : 'light'
  } catch (e) {}
})()
