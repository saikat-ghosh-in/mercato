export const detailsForForm = product => ({
  brand: product?.brand ?? "", productCode: product?.productCode ?? "",
  materials: product?.materials ?? "", care: product?.care ?? "", warranty: product?.warranty ?? "",
  packageContents: product?.packageContents ?? "", deliveryReturns: product?.deliveryReturns ?? "",
  highlightsText: (product?.highlights ?? []).join("\n"),
  specificationsText: (product?.specifications ?? []).map(s => s.label + ": " + s.value).join("\n")
});
export function detailsPayload(form) {
  const highlights = form.highlightsText.split("\n").map(s => s.trim()).filter(Boolean);
  const specifications = form.specificationsText.split("\n").map(s => s.trim()).filter(Boolean).map(line => {
    const split = line.indexOf(":");
    if (split < 1 || !line.slice(split + 1).trim()) throw new Error("Each specification needs a label and value separated by a colon.");
    return { label: line.slice(0, split).trim(), value: line.slice(split + 1).trim() };
  });
  if (highlights.length > 10 || specifications.length > 30) throw new Error("Use up to 10 highlights and 30 specifications.");
  return { ...Object.fromEntries(["brand", "productCode", "materials", "care", "warranty", "packageContents", "deliveryReturns"].map(key => [key, form[key].trim()])), highlights, specifications };
}
