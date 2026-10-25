import test from 'node:test';
import assert from 'node:assert/strict';
import { renderToStaticMarkup } from 'react-dom/server';
import { createElement } from 'react';
import { createRequire } from 'node:module';
import { build } from 'esbuild';
import { fileURLToPath } from 'node:url';
const require=createRequire(import.meta.url);
test('verification feedback distinguishes queued, accepted and failed delivery without claiming delivered',async()=>{
  const result=await build({entryPoints:[fileURLToPath(new URL('../src/components/auth/MailDeliveryStatus.jsx',import.meta.url))],bundle:true,write:false,platform:'node',format:'cjs',packages:'external',jsx:'automatic'});
  const module={exports:{}};new Function('require','module','exports',result.outputFiles[0].text)(require,module,module.exports);
  for(const state of ['QUEUED','ACCEPTED','FAILED','RETRYING','SKIPPED','UNKNOWN']){
    const markup=renderToStaticMarkup(createElement(module.exports.default,{delivery:{state}}));
    assert.match(markup,/role="status"/);assert.doesNotMatch(markup,/email delivered|email sent!|we sent/i);
    if(state==='FAILED')assert.match(markup,/couldn’t send/i);
    if(state==='ACCEPTED')assert.match(markup,/not.*confirmed/i);
  }
});
