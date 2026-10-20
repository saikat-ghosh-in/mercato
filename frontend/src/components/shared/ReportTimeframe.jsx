import { useState } from "react";
import { Popover, PopoverButton, PopoverPanel } from "@headlessui/react";
import { FiCalendar, FiEdit2 } from "react-icons/fi";
import { defaultTimeframe, timeframeLabel, validateTimeframe } from "../../utils/reportTimeframe";

export default function ReportTimeframe({ range, loading, onApply }) {
  const current = range || defaultTimeframe();
  const [draft, setDraft] = useState(current);
  const [error, setError] = useState("");
  return <Popover className="report-timeframe">{({ open, close }) => <>
    <PopoverButton className="report-timeframe-trigger" aria-label={`Edit timeframe, ${timeframeLabel(current)}`} disabled={loading && !range} onClick={() => { if (!open) { setDraft(current); setError(""); } }}><FiCalendar aria-hidden="true" /><span>{timeframeLabel(current)}</span><FiEdit2 aria-hidden="true" /></PopoverButton>
    <PopoverPanel transition anchor="bottom start" className="report-timeframe-panel" aria-label="Edit reporting timeframe" focus>
      <form onSubmit={event => { event.preventDefault(); const message = validateTimeframe(draft); setError(message); if (!message) { onApply(draft); close(); } }} noValidate>
        <h3>Edit timeframe</h3><p>Reports use dates in UTC.</p>
        <div className="report-timeframe-fields"><label>From<input type="date" value={draft.from} max={draft.through} onInput={event => { const value = event.currentTarget.value; setDraft(d => ({ ...d, from: value })); setError(""); }} aria-invalid={Boolean(error)} aria-describedby={error ? "timeframe-error" : undefined} /></label><label>Through<input type="date" value={draft.through} min={draft.from} max={new Date().toISOString().slice(0, 10)} onInput={event => { const value = event.currentTarget.value; setDraft(d => ({ ...d, through: value })); setError(""); }} aria-invalid={Boolean(error)} aria-describedby={error ? "timeframe-error" : undefined} /></label></div>
        {error && <p id="timeframe-error" className="inline-error" role="alert">{error}</p>}
        <div className="report-timeframe-actions"><button type="button" className="button button-outline" onClick={() => close()}>Cancel</button><button className="button button-dark" type="submit">Apply</button></div>
      </form>
    </PopoverPanel>
  </>}</Popover>;
}
