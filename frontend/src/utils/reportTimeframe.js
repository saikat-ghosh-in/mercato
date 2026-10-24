const isoDay = date => date.toISOString().slice(0, 10);
const parsed = value => {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value || "")) return null;
  const date = new Date(`${value}T00:00:00Z`);
  return Number.isFinite(date.getTime()) && isoDay(date) === value ? date : null;
};
export function defaultTimeframe(today = new Date()) {
  const through = isoDay(today);
  const from = new Date(`${through}T00:00:00Z`);
  from.setUTCDate(from.getUTCDate() - 89);
  return { from: isoDay(from), through };
}
export function validateTimeframe({ from, through }, today = new Date()) {
  const start = parsed(from), end = parsed(through);
  if (!start || !end) return "Enter a valid start and end date.";
  if (start > end) return "The start date must be on or before the end date.";
  if (through > isoDay(today)) return "The end date cannot be in the future.";
  if ((end - start) / 86400000 > 365) return "Choose a timeframe of 366 days or fewer.";
  return "";
}
export function timeframeLabel({ from, through }) {
  const format = value => new Intl.DateTimeFormat("en-GB", { day: "numeric", month: "short", year: "numeric", timeZone: "UTC" }).format(parsed(value));
  return `${format(from)} – ${format(through)}`;
}
