import test from 'node:test';
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { createElement as h } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { build } from 'esbuild';
import { fileURLToPath } from 'node:url';

const require = createRequire(import.meta.url);
async function loadAccordion() {
  const result = await build({ entryPoints: [fileURLToPath(new URL('../src/components/shared/Accordion.jsx', import.meta.url))], bundle: true, write: false, platform: 'node', format: 'cjs', packages: 'external', jsx: 'automatic' });
  const module = { exports: {} };
  new Function('require', 'module', 'exports', result.outputFiles[0].text)(require, module, module.exports);
  return module.exports.default;
}

test('accordion preserves closed inputs and connects each labelled region to its own trigger', async () => {
  const Accordion = await loadAccordion();
  const item = (value) => h(Accordion.Item, { value, key: value }, h(Accordion.Trigger, null, value), h(Accordion.Content, null, h('input', { defaultValue: 'draft' })));
  const markup = renderToStaticMarkup(h(Accordion, { multiple: true, defaultValue: ['first'] }, item('first'), item('second')));
  const triggers = [...markup.matchAll(/<button[^>]+id="([^"]+)"[^>]+aria-expanded="([^"]+)"[^>]+aria-controls="([^"]+)"/g)];
  assert.equal(triggers.length, 2);
  assert.deepEqual(triggers.map(match => match[2]), ['true', 'false']);
  assert.notEqual(triggers[0][1], triggers[1][1]);
  for (const [, trigger, , panel] of triggers) {
    assert.ok(markup.includes(`id="${panel}" role="region" aria-labelledby="${trigger}"`));
  }
  assert.equal((markup.match(/value="draft"/g) || []).length, 2);
  assert.match(markup, /hidden=""[^>]*inert=""/);
});

test('controlled accordion value takes precedence over defaults and single mode opens only its value', async () => {
  const Accordion = await loadAccordion();
  const markup = renderToStaticMarkup(h(Accordion, { value: 'second', defaultValue: 'first' }, ['first', 'second'].map(value => h(Accordion.Item, { value, key: value }, h(Accordion.Trigger, null, value), h(Accordion.Content, null, value)))));
  assert.deepEqual([...markup.matchAll(/aria-expanded="([^"]+)"/g)].map(match => match[1]), ['false', 'true']);
});
