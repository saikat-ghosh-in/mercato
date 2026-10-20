export function activeDashboardTab(items, params) {
  return items.some(item => item.id === params.get("tab")) ? params.get("tab") : "overview";
}
export function dashboardParams(previous, tab) {
  const next = new URLSearchParams(previous);
  next.set("tab", tab);
  return next;
}
