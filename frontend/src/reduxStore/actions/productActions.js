import api from "../../backend/api";

let requestSequence = 0;

export const fetchProducts = (queryString, signal) => async dispatch => {
  const requestId = ++requestSequence;
  dispatch({ type: "PRODUCTS_LOADING", requestId });
  try {
    const { data } = await api.get("/public/products?" + queryString, { signal });
    if (!signal?.aborted) dispatch({ type: "FETCH_PRODUCTS", requestId, payload: data.content ?? [],
      pageNumber: data.pageNumber, pageSize: data.pageSize, totalElements: data.totalElements,
      totalPages: data.totalPages, lastPage: data.lastPage });
  } catch (error) {
    if (!signal?.aborted) dispatch({ type: "PRODUCTS_ERROR", requestId,
      payload: error?.response?.data?.message || "Please check your connection and try again." });
  }
};

export const setProductToDisplay = (productId, signal) => async dispatch => {
  const { data } = await api.get("/public/products/" + productId, { signal });
  if (!signal?.aborted) dispatch({ type: "SET_PRODUCT_TO_DISPLAY", payload: data });
};

export const clearProductToDisplay = () => ({ type: "SET_PRODUCT_TO_DISPLAY", payload: null });
