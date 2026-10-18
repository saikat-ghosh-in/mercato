// Cart prices and stock flags come from the cart response; this cache holds only
// product presentation data so quantity acknowledgements never reload images.
export function createProductMetadataCache(loadProduct) {
  let products = {};
  return {
    missing(ids) { return ids.filter(id => !products[id]); },
    async load(ids, signal) {
      const entries = await Promise.all(this.missing(ids).map(async id => {
        try { return [id, await loadProduct(id, signal)]; }
        catch { return [id, null]; }
      }));
      if (signal.aborted) return null;
      products = { ...products, ...Object.fromEntries(entries.filter(([, product]) => product)) };
      return products;
    },
  };
}
