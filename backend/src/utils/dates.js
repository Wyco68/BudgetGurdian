// Date handling contract with the desktop client.
//
// The Java side works in timezone-naive LocalDate / LocalDateTime and sends
// ISO strings ("2026-07-16", "2026-07-16T12:00:00"). To round-trip those
// bytes exactly we pin everything to UTC: naive strings are interpreted as
// UTC on the way in and formatted back without a zone suffix on the way out.
// No value ever passes through the server's local timezone.

/** "2026-07-16" -> Date at UTC midnight. */
export function parseDateOnly(value) {
  return new Date(`${value}T00:00:00.000Z`);
}

/** Date -> "2026-07-16". */
export function formatDateOnly(date) {
  return date.toISOString().slice(0, 10);
}

/** "2026-07-16T12:00:00" (naive) -> Date, interpreted as UTC. */
export function parseNaiveDateTime(value) {
  return new Date(value.endsWith('Z') ? value : `${value}Z`);
}

/** Date -> "2026-07-16T12:00:00" (naive, no zone, no millis). */
export function formatNaiveDateTime(date) {
  return date.toISOString().replace(/\.\d{3}Z$/, '');
}
