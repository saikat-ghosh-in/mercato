const key = "mercato:preferences";
const sanitize = value => {
  const result = {};
  if (["light", "dark", "system"].includes(value?.theme)) result.theme = value.theme;
  if (["relevance", "price-asc", "price-desc", "newest"].includes(value?.sort)) result.sort = value.sort;
  return result;
};
export function readPreferences(storage) {
  try { const value = JSON.parse((storage ?? globalThis.localStorage)?.getItem(key)); return value?.version === 1 ? sanitize(value) : {}; } catch { return {}; }
}
export function writePreferences(value, storage) {
  try { (storage ?? globalThis.localStorage)?.setItem(key, JSON.stringify({ version: 1, ...sanitize(value) })); } catch { /* Preferences are optional when storage is disabled. */ }
}
