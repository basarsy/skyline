/**
 * Unique per page load. Router state survives a hard refresh (it lives in history.state), so state that
 * must not outlive the page (e.g. the last name of a PNR lookup) is tagged with this id and ignored after a
 * reload.
 */
export const DOCUMENT_ID = crypto.randomUUID()
