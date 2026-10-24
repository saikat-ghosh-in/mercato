export function normalizeQuantity(value) {
  if (typeof value !== "number" && typeof value !== "string") return 0;
  const quantity = Number(value);
  return Number.isSafeInteger(quantity) && quantity > 0 ? quantity : 0;
}
export function countCartUnits(items) {
  return Array.isArray(items) ? items.reduce((sum, item) => sum + normalizeQuantity(item?.quantity), 0) : 0;
}
export function createSerialQueue() {
  let tail = Promise.resolve();
  return {
    run(task) { const result = tail.then(task); tail = result.catch(() => {}); return result; },
    drain() { return tail; }
  };
}
