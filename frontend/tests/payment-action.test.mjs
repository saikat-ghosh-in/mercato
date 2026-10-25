import test from 'node:test';
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { renderToStaticMarkup } from 'react-dom/server';
import { createElement } from 'react';
import { build } from 'esbuild';
import { fileURLToPath } from 'node:url';
const require=createRequire(import.meta.url);
test('payment action stays generic, stable and unavailable while the quote is blocked or opening',async()=>{
  const result=await build({entryPoints:[fileURLToPath(new URL('../src/components/checkout/PaymentAction.jsx',import.meta.url))],bundle:true,write:false,platform:'node',format:'cjs',packages:'external',jsx:'automatic'});
  const module={exports:{}};new Function('require','module','exports',result.outputFiles[0].text)(require,module,module.exports);
  for(const props of [{},{loading:true},{disabled:true}]){
    const html=renderToStaticMarkup(createElement(module.exports.default,props));
    assert.match(html,/Continue to payment/);assert.doesNotMatch(html,/Cashfree/);
    assert.match(html,/type="button"/);assert.match(html,/aria-busy=/);
    assert.equal(/disabled=""/.test(html),Boolean(props.loading||props.disabled));
  }
});
