// Existing database image references remain intact during the origin cutover.
const owned = /^https:\/\/sg-mercato-backend(?:-beta)?\.duckdns\.org(\/images\/[a-f\d-]{36}\.(?:jpg|jpeg|png|webp))$/i;
export function normalizeOwnedImages(value) {
  if (typeof value === 'string') return value.replace(owned, '$1');
  if (!value || typeof value !== 'object') return value;
  if (Array.isArray(value)) {
    const updated = value.map(normalizeOwnedImages);
    return updated.some((entry, i) => entry !== value[i]) ? updated : value;
  }
  let result = value;
  for (const [key, entry] of Object.entries(value)) {
    const updated = normalizeOwnedImages(entry);
    if (updated !== entry) { if (result === value) result = { ...value }; result[key] = updated; }
  }
  return result;
}
