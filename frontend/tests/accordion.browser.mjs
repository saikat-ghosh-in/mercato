// Run with PLAYWRIGHT_MODULE pointing to the available Playwright package entry.
// No browser or DOM dependency is added to the application.
import test from 'node:test';
import assert from 'node:assert/strict';
import { build } from 'esbuild';
import { pathToFileURL, fileURLToPath } from 'node:url';

test('real browser: controlled state, keyboard navigation, retained drafts, inert close and interrupted exit', async () => {
  const { chromium } = await import(pathToFileURL(process.env.PLAYWRIGHT_MODULE).href);
  const bundle = await build({ stdin: { contents: `
    import React, { useState } from 'react';
    import { createRoot } from 'react-dom/client';
    import Accordion from './src/components/shared/Accordion.jsx';
    function App() {
      const [value, setValue] = useState(['one']);
      const [single, setSingle] = useState('controlled');
      return <><button id="external" onClick={() => setValue([])}>Close externally</button>
        <output>{JSON.stringify(value)}</output><Accordion multiple value={value} onValueChange={setValue}>
          {['one','two','three'].map(value => <Accordion.Item key={value} value={value}>
            <Accordion.Trigger disabled={value === 'two'}>{value}</Accordion.Trigger>
            <Accordion.Content><input aria-label={value + ' draft'} defaultValue="original" /></Accordion.Content>
          </Accordion.Item>)}
        </Accordion><Accordion defaultValue="a">{['a','b'].map(value => <Accordion.Item key={value} value={value}><Accordion.Trigger>{value}</Accordion.Trigger><Accordion.Content>{value}</Accordion.Content></Accordion.Item>)}</Accordion>
        <Accordion value={single} onValueChange={setSingle} defaultValue="controlled"><Accordion.Item value="controlled"><Accordion.Trigger>controlled</Accordion.Trigger><Accordion.Content>Controlled content</Accordion.Content></Accordion.Item></Accordion></>;
    }
    createRoot(document.getElementById('root')).render(<App />);`, resolveDir: fileURLToPath(new URL('..', import.meta.url)), loader: 'jsx' }, bundle: true, write: false, jsx: 'automatic' });
  let browser;
  try {
    browser = await chromium.launch({ channel: process.env.BROWSER_CHANNEL || 'chrome', headless: true });
    const page = await browser.newPage();
    await page.setContent('<div id="root"></div>');
    await page.addScriptTag({ content: bundle.outputFiles[0].text });
    const trigger = name => page.getByRole('button', { name, exact: true });
    const panel = async name => page.locator(`[id="${await trigger(name).getAttribute('aria-controls')}"]`);
    await trigger('one').focus();
    await page.keyboard.press('ArrowDown');
    assert.equal(await trigger('three').evaluate(el => el === document.activeElement), true);
    await page.keyboard.press('Home');
    assert.equal(await trigger('one').evaluate(el => el === document.activeElement), true);
    await page.keyboard.press('End');
    await page.keyboard.press('Enter');
    assert.equal(await page.locator('output').textContent(), '["one","three"]');
    await page.keyboard.press('ArrowUp');
    await page.keyboard.press('Space');
    assert.equal(await trigger('one').getAttribute('aria-expanded'), 'false');
    await trigger('one').click();
    await page.getByLabel('one draft').fill('retained');
    await page.getByLabel('one draft').focus();
    // Dispatch without moving focus: exercise controlled external close while focus is inside.
    await page.locator('#external').evaluate(el => el.click());
    assert.equal(await trigger('one').evaluate(el => el === document.activeElement), true);
    assert.equal(await (await panel('one')).evaluate(el => el.inert), true);
    assert.equal(await (await panel('one')).evaluate(el => el.hidden), false);
    await page.keyboard.press('Tab');
    assert.equal(await trigger('three').evaluate(el => el === document.activeElement), true);
    await trigger('one').click();
    await page.waitForTimeout(220);
    assert.equal(await (await panel('one')).evaluate(el => el.hidden), false);
    assert.equal(await page.getByLabel('one draft').inputValue(), 'retained');
    await trigger('one').click();
    await page.waitForTimeout(220);
    assert.equal(await (await panel('one')).evaluate(el => el.hidden), true);
    await trigger('b').click();
    assert.equal(await trigger('a').getAttribute('aria-expanded'), 'false');
    assert.equal(await trigger('b').getAttribute('aria-expanded'), 'true');
    await page.emulateMedia({ reducedMotion: 'reduce' });
    await trigger('b').click();
    assert.equal(await (await panel('b')).evaluate(el => el.hidden), true);
    await trigger('b').click();
    assert.equal(await (await panel('b')).evaluate(el => el.hidden), false);
    await trigger('controlled').click();
    assert.equal(await trigger('controlled').getAttribute('aria-expanded'), 'false');
  } finally {
    await browser?.close();
  }
});

test('filter disclosure keeps price drafts while collapsed and Apply submits the edited draft', async () => {
  const { chromium } = await import(pathToFileURL(process.env.PLAYWRIGHT_MODULE).href);
  const bundle = await build({ stdin: { contents: `
    import React, { useState } from 'react';
    import { createRoot } from 'react-dom/client';
    import FilterPanel from './src/components/product/FilterPanel.jsx';
    function App() {
      const [draft, setDraft] = useState({ category: '', sellers: [], minPrice: '', maxPrice: '', inStockOnly: false });
      const [applied, setApplied] = useState(null);
      return <><output>{JSON.stringify(applied)}</output><FilterPanel open categories={[{categoryName:'Home'}]} sellers={[{sellerDisplayName:'Studio'}]} draft={draft} onChange={updates => setDraft(value => ({...value,...updates}))} onClear={() => {}} onClose={() => {}} onApply={() => setApplied(draft)} /></>;
    }
    createRoot(document.getElementById('root')).render(<App />);`, resolveDir: fileURLToPath(new URL('..', import.meta.url)), loader: 'jsx' }, bundle: true, write: false, jsx: 'automatic' });
  const browser = await chromium.launch({ channel: process.env.BROWSER_CHANNEL || 'chrome', headless: true });
  try {
    const page = await browser.newPage();
    await page.setContent('<div id="root"></div>');
    await page.addScriptTag({ content: bundle.outputFiles[0].text });
    for (const name of ['Category', 'Seller', 'Price range', 'Availability']) {
      assert.equal(await page.getByRole('button', { name, exact: true }).getAttribute('aria-expanded'), 'true');
    }
    await page.getByRole('button', { name: 'Home', exact: true }).click();
    await page.getByLabel('Min price (₹)').fill('25');
    await page.getByRole('button', { name: 'Price range', exact: true }).click();
    await page.waitForTimeout(220);
    assert.equal(await page.getByLabel('Min price (₹)').inputValue(), '25');
    await page.getByRole('button', { name: 'Price range', exact: true }).click();
    assert.equal(await page.getByLabel('Min price (₹)').inputValue(), '25');
    await page.getByLabel('In stock only').check();
    assert.equal(await page.locator('output').textContent(), 'null');
    await page.getByRole('button', { name: 'Apply filters', exact: true }).click();
    assert.deepEqual(JSON.parse(await page.locator('output').textContent()), { category: 'Home', sellers: [], minPrice: '25', maxPrice: '', inStockOnly: true });
  } finally { await browser.close(); }
});
