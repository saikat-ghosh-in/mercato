export const SORT_OPTIONS = [
    { label: "Newest First", sortBy: "createdAt", sortingOrder: "desc" },
    { label: "Oldest First", sortBy: "createdAt", sortingOrder: "asc" },
    { label: "Biggest savings", sortBy: "discountPercent", sortingOrder: "desc" },
    { label: "Price: Low → High", sortBy: "sellingPrice", sortingOrder: "asc" },
    { label: "Price: High → Low", sortBy: "sellingPrice", sortingOrder: "desc" },
    { label: "Name: A → Z", sortBy: "productName", sortingOrder: "asc" },
    { label: "Name: Z → A", sortBy: "productName", sortingOrder: "desc" },
];

export const buildQueryString = ({
    sortBy, sortingOrder, pageNumber,
    category, sellers, keyword,
    minPrice, maxPrice, inStockOnly,
}) => {
    const parts = [
        `sortBy=${encodeURIComponent(sortBy)}`,
        `sortingOrder=${encodeURIComponent(sortingOrder)}`,
        `pageSize=12`,
        `pageNumber=${pageNumber}`,
    ];
    if (category) parts.push(`category=${encodeURIComponent(category)}`);
    if (sellers?.length) {
        const sellerStr = Array.isArray(sellers) ? sellers.join(",") : sellers;
        parts.push(`sellers=${encodeURIComponent(sellerStr)}`);
    }
    if (keyword) parts.push(`keyword=${encodeURIComponent(keyword)}`);
    if (minPrice) parts.push(`minPrice=${encodeURIComponent(minPrice)}`);
    if (maxPrice) parts.push(`maxPrice=${encodeURIComponent(maxPrice)}`);
    if (inStockOnly) parts.push(`inStock=true`);
    return parts.join("&");
};

export const parseParams = (searchParams) => ({
    sortBy: searchParams.get("sortBy") || "createdAt",
    sortingOrder: searchParams.get("sortingOrder") || "desc",
    pageNumber: /^\d{1,8}$/.test(searchParams.get("pageNumber") || "0") ? Number(searchParams.get("pageNumber") || 0) : 0,
    category: searchParams.get("category") || "",
    sellers: searchParams.get("sellers")
        ? searchParams.get("sellers").split(",").filter(Boolean)
        : [],
    keyword: searchParams.get("keyword") || "",
    minPrice: searchParams.get("minPrice") || "",
    maxPrice: searchParams.get("maxPrice") || "",
    inStockOnly: searchParams.get("inStock") === "true",
});

export const countActiveFilters = ({ category, sellers, minPrice, maxPrice, inStockOnly, keyword }) =>
    [category, sellers?.length, minPrice || maxPrice, inStockOnly, keyword].filter(Boolean).length;
