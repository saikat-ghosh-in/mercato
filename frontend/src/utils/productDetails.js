export const detailsForForm = product => ({
  brand: product?.brand ?? "", productCode: product?.productCode ?? "",
  weightKg: product?.weightKg ?? "", lengthCm: product?.lengthCm ?? "", widthCm: product?.widthCm ?? "", heightCm: product?.heightCm ?? "",
  sellerPaysShipping: product?.sellerPaysShipping ?? false,
  materials: product?.materials ?? "", care: product?.care ?? "", warranty: product?.warranty ?? "",
  packageContents: product?.packageContents ?? "", deliveryReturns: product?.deliveryReturns ?? "",
  highlightsText: (product?.highlights ?? []).join("\n"),
  specificationsText: (product?.specifications ?? []).map(s => s.label + ": " + s.value).join("\n")
});
export function detailsPayload(form) {
  const measurements = packagePayload(form);
  const highlights = form.highlightsText.split("\n").map(s => s.trim()).filter(Boolean);
  const specifications = form.specificationsText.split("\n").map(s => s.trim()).filter(Boolean).map(line => {
    const split = line.indexOf(":");
    if (split < 1 || !line.slice(split + 1).trim()) throw new Error("Each specification needs a label and value separated by a colon.");
    return { label: line.slice(0, split).trim(), value: line.slice(split + 1).trim() };
  });
  if (highlights.length > 10 || specifications.length > 30) throw new Error("Use up to 10 highlights and 30 specifications.");
  return { ...Object.fromEntries(["brand", "productCode", "materials", "care", "warranty", "packageContents", "deliveryReturns"].map(key => [key, form[key].trim()])), ...measurements, sellerPaysShipping: Boolean(form.sellerPaysShipping), highlights, specifications };
}

export function packagePayload(form, required = false) {
  const keys = ["weightKg", "lengthCm", "widthCm", "heightCm"];
  const present = keys.filter(key => form[key] !== "" && form[key] != null);
  if (!present.length && !required) return {};
  if (present.length !== 4) throw new Error("Enter packed weight, length, width and height together.");
  for (const key of keys) {
    const value = Number(form[key]);
    const weight = key === "weightKg";
    if (!Number.isFinite(value) || value < (weight ? 0.001 : 0.01) || value > (weight ? 1000 : 600) || !/^\d+(\.\d{1,3})?$/.test(String(form[key])) || (!weight && !/^\d+(\.\d{1,2})?$/.test(String(form[key]))))
      throw new Error("Use weight from 0.001–1,000 kg (3 decimals) and dimensions from 0.01–600 cm (2 decimals).");
  }
  return Object.fromEntries(keys.map(key => [key, Number(form[key])]));
}
