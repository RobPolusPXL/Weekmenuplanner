// Datums als ISO-tekst "jjjj-mm-dd" (zoals de dag-documenten in Firestore).
// Dagnummers volgen java.util.Calendar zoals in de Android-app: 1 = zondag, 2 = maandag … 7 = zaterdag.

export const SUNDAY = 1;
export const MONDAY = 2;

const pad = n => String(n).padStart(2, '0');

function toDate(iso) {
  const [y, m, d] = iso.split('-').map(Number);
  return new Date(y, m - 1, d, 12, 0, 0, 0);
}

function fromDate(dt) {
  return `${dt.getFullYear()}-${pad(dt.getMonth() + 1)}-${pad(dt.getDate())}`;
}

export function today() {
  return fromDate(new Date());
}

export function isIsoDate(text) {
  return /^\d{4}-\d{2}-\d{2}$/.test(String(text || '').trim());
}

export function addDays(iso, days) {
  const dt = toDate(iso);
  dt.setDate(dt.getDate() + days);
  return fromDate(dt);
}

/** Calendar-dagnummer (1 = zondag … 7 = zaterdag) van een datum. */
export function calendarDay(iso) {
  return toDate(iso).getDay() + 1;
}

/** Eerste dag van de week waarin [iso] valt. */
export function weekStart(iso = today(), firstDayOfWeek = SUNDAY) {
  const back = ((calendarDay(iso) - firstDayOfWeek) % 7 + 7) % 7;
  return back === 0 ? iso : addDays(iso, -back);
}

export function weekDates(start) {
  return Array.from({ length: 7 }, (_, i) => addDays(start, i));
}

export function belgian(iso) {
  const [y, m, d] = iso.split('-');
  return `${d}/${m}/${y}`;
}

export function shortBelgian(iso) {
  const [, m, d] = iso.split('-');
  return `${d}/${m}`;
}

export function weekHeader(start) {
  return `${belgian(start)} – ${belgian(addDays(start, 6))}`;
}

const DAY_NAMES = ['Zondag', 'Maandag', 'Dinsdag', 'Woensdag', 'Donderdag', 'Vrijdag', 'Zaterdag'];

export function dayName(iso) {
  return DAY_NAMES[toDate(iso).getDay()];
}

/** Keuzes voor "Week begint op", maandag … zondag. */
export const WEEK_START_OPTIONS = [
  [2, 'Maandag'], [3, 'Dinsdag'], [4, 'Woensdag'], [5, 'Donderdag'],
  [6, 'Vrijdag'], [7, 'Zaterdag'], [1, 'Zondag'],
];

const MONTHS = ['januari', 'februari', 'maart', 'april', 'mei', 'juni', 'juli',
  'augustus', 'september', 'oktober', 'november', 'december'];

export function monthName(month) {
  return MONTHS[month - 1];
}
