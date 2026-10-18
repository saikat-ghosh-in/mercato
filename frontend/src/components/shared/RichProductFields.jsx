export default function RichProductFields({ form, set }) {
  return <section className="rich-editor" aria-label="Rich product information"><h3 className="text-lg font-medium">Product information</h3><p className="text-sm text-gray-500">Optional. Publish only verified product and policy information. Empty fields stay hidden on the product page.</p>
    {[["brand", "Brand", 100], ["productCode", "Product code", 100]].map(([key,label,max]) => <label key={key}>{label}<input value={form[key]} maxLength={max} onChange={e => set(key,e.target.value)} /></label>)}
    <label>Highlights<textarea value={form.highlightsText} onChange={e => set("highlightsText",e.target.value)} /><small>One highlight per line. Up to 10, each 300 characters.</small></label>
    <label>Specifications<textarea value={form.specificationsText} onChange={e => set("specificationsText",e.target.value)} placeholder={"Material: Cotton\nFit: Relaxed"} /><small>One label: value per line. Up to 30 specifications.</small></label>
    {[["materials", "Materials"], ["care", "Care instructions"], ["warranty", "Warranty"], ["packageContents", "What's in the box"], ["deliveryReturns", "Delivery & returns"]].map(([key,label]) => <label key={key}>{label}<textarea value={form[key]} maxLength={2000} onChange={e => set(key,e.target.value)} /></label>)}
  </section>;
}
