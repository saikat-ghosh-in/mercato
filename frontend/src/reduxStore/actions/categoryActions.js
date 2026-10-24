import api from "../../backend/api";

export const fetchCategories = () => async (dispatch) => {
    try {
        dispatch({ type: "CATEGORY_LOADER" });
        const { data } = await api.get(`/public/categories`);
        dispatch({ type: "FETCH_CATEGORIES", payload: Array.isArray(data) ? data : [] });
        dispatch({ type: "CATEGORY_SUCCESS" });
    } catch (error) {
        console.error(error);
        dispatch({
            type: "CATEGORY_ERROR",
            payload: error?.response?.data?.message || "Oops! Something went wrong!",
        });
    }
};
