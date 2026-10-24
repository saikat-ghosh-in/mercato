import test from 'node:test';
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { renderToStaticMarkup } from 'react-dom/server';
import { createElement } from 'react';
import { build } from 'esbuild';
import { fileURLToPath } from 'node:url';

const require = createRequire(import.meta.url);
async function components(entry) {
  const result = await build({ entryPoints: [fileURLToPath(new URL(entry, import.meta.url))], bundle: true, write: false, platform: 'node', format: 'cjs', packages: 'external', jsx: 'automatic' });
  const module = { exports: {} };
  new Function('require', 'module', 'exports', result.outputFiles[0].text)(require, module, module.exports);
  return module.exports;
}

test('spinner uses twelve segments, current color and one accessible status', async () => {
  const { Spinner } = await components('../src/components/shared/Spinner.jsx');
  const markup = renderToStaticMarkup(createElement(Spinner, { size: 20, label: 'Saving address' }));
  assert.equal((markup.match(/class="spinner-segment"/g) || []).length, 12);
  assert.match(markup, /role="status"/);
  assert.match(markup, /Saving address/);
  assert.match(markup, /width:20px/);
  const decorative = renderToStaticMarkup(createElement(Spinner));
  assert.match(decorative, /aria-hidden="true"/);
  assert.doesNotMatch(decorative, /role="status"/);
});

test('skeleton shapes are decorative and layouts announce loading only once', async () => {
  const { Skeleton, LoadingLayout } = await components('../src/components/shared/LoadingStates.jsx');
  for (const shape of ['circle', 'text', 'block', 'card']) {
    const markup = renderToStaticMarkup(createElement(Skeleton, { shape }));
    assert.match(markup, /aria-hidden="true"/);
    assert.match(markup, new RegExp(`data-shape="${shape}"`));
  }
  for (const variant of ['catalog', 'product', 'bag', 'checkout', 'profile', 'form', 'dashboard', 'table', 'list', 'payment']) {
    const markup = renderToStaticMarkup(createElement(LoadingLayout, { variant, label: 'Loading content' }));
    assert.equal((markup.match(/role="status"/g) || []).length, 1, variant);
    assert.match(markup, /aria-busy="true"/);
    assert.match(markup, /Loading content/);
    assert.doesNotMatch(markup, /role="status"[^>]*aria-busy="true"/);
    assert.match(markup, /data-shape=/);
    assert.doesNotMatch(markup, /<button|<input|<a /);
  }
});


test('cached payment information cannot open the gateway until verification completes', async () => {
  const { paymentHandoffAllowed } = await import('../src/utils/accountState.js');
  assert.equal(paymentHandoffAllowed({ isLoading: false, isFetching: true }), false);
  assert.equal(paymentHandoffAllowed({ isLoading: true, isFetching: true }), false);
  assert.equal(paymentHandoffAllowed({ isLoading: false, isFetching: false, error: { status: 503 } }), false);
  assert.equal(paymentHandoffAllowed({ isLoading: false, isFetching: false, error: null }), true);
});

test('repeated identical errors update one toast; distinct actionable failures stay separate', async () => {
  const { default: toast } = await import('../src/utils/toastUtils.js');
  const { toast: engine } = await import('sonner');
  const first = toast.error('Could not save your address. Try again.');
  const repeat = toast.error('Could not save your address. Try again.');
  assert.equal(first, repeat);
  assert.equal(engine.getToasts().filter(item => item.id === first).length, 1);
  const distinct = toast.error('Could not cancel the order. Check its status.');
  assert.notEqual(first, distinct);
  toast.dismiss();
});
