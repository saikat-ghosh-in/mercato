export const emptyFilterDraft = () => ({ category: "", sellers: [], minPrice: "", maxPrice: "", inStockOnly: false });

export function filterDraftReducer(state, action) {
  switch (action.type) {
    case "OPEN": return { open: true, error: "", draft: { ...emptyFilterDraft(), ...action.filters, sellers: [...(action.filters.sellers ?? [])] } };
    case "CHANGE": return { ...state, error: "", draft: { ...state.draft, ...action.updates } };
    case "CLEAR": return { ...state, error: "", draft: { ...emptyFilterDraft(), sortBy: state.draft.sortBy, sortingOrder: state.draft.sortingOrder } };
    case "ERROR": return { ...state, error: action.message };
    case "CLOSE": return { ...state, open: false };
    default: return state;
  }
}

export function validateFilterDraft(draft) {
  const prices = [draft.minPrice, draft.maxPrice].map(value => String(value ?? "").trim());
  if (prices.some(value => value !== "" && (!Number.isFinite(Number(value)) || Number(value) < 0))) return "Enter prices of zero or more.";
  if (prices.every(value => value !== "") && Number(prices[0]) > Number(prices[1])) return "The minimum price must not exceed the maximum.";
  return "";
}

// Commit filters and an optional sort together; preserve search and legacy sort callers.
export function applyFilterDraft(current, draft) {
  const error = validateFilterDraft(draft);
  if (error) throw new Error(error);
  const next = new URLSearchParams(current);
  const fields = { category: draft.category, sellers: draft.sellers.join(","), minPrice: String(draft.minPrice).trim(), maxPrice: String(draft.maxPrice).trim(), inStock: draft.inStockOnly ? "true" : "" };
  for (const [key, value] of Object.entries(fields)) {
    if (value) next.set(key, value);
    else next.delete(key);
  }
  if (draft.sortBy && draft.sortingOrder) {
    next.set("sortBy", draft.sortBy);
    next.set("sortingOrder", draft.sortingOrder);
  }
  next.set("pageNumber", "0");
  return next;
}
