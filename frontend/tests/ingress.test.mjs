import test from 'node:test';
import assert from 'node:assert/strict';
import { readdirSync, statSync } from 'node:fs';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import ingress, { config } from '../../netlify/edge-functions/trusted-ingress.js';
import { proxyPaths } from '../../netlify/proxy-paths.mjs';
import { configureIngress } from '../../netlify/plugins/secure-ingress/config.mjs';
import * as buildPlugin from '../../netlify/plugins/secure-ingress/index.mjs';

test('the Netlify plugin entry point exports only supported lifecycle events', () => {
  const events = new Set(['onPreBuild', 'onBuild', 'onPostBuild', 'onSuccess', 'onError', 'onEnd']);
  assert.deepEqual(Object.keys(buildPlugin).filter(name => !events.has(name)), []);
  assert.equal(typeof buildPlugin.onPreBuild, 'function');
});

test('static storefront assets never collide with forced backend proxy paths', () => {
  const publicDir = fileURLToPath(new URL('../public/', import.meta.url));
  const collisions = readdirSync(publicDir, { recursive: true })
    .filter(name => statSync(join(publicDir, name)).isFile())
    .map(name => '/' + name.replaceAll('\\', '/'))
    .filter(path => proxyPaths.some(from => from.endsWith('*') ? path.startsWith(from.slice(0, -1)) : path === from));
  assert.deepEqual(collisions, []);
});

test('a main-branch build cannot publish cookie authentication before the coordinated origin cutover', () => {
  const env = { MERCATO_ORIGIN: 'https://review.example.test', NETLIFY_PROXY_SIGNING_KEY: 'a'.repeat(40) };
  for (const value of [undefined, '', 'no', 'YES', true, 'yes ']) {
    assert.throws(() => configureIngress({ ...env, MERCATO_COOKIE_CUTOVER_READY: value }), /cutover/i);
  }
});

test('every signed proxy path is covered by a fail-closed edge function', () => {
  const value = configureIngress({ MERCATO_COOKIE_CUTOVER_READY: 'yes', MERCATO_ORIGIN: 'https://review.example.test', NETLIFY_PROXY_SIGNING_KEY: 'a'.repeat(40), CONTEXT: 'production' });
  assert.deepEqual(value.redirects.slice(0,-1).map(r => r.from), proxyPaths);
  assert.deepEqual(config.path, proxyPaths);
  assert.equal(config.onError,'fail');
  for(const rule of value.redirects.slice(0,-1)) assert.equal(rule.signed,'NETLIFY_PROXY_SIGNING_KEY');
  assert.equal(value.redirects.at(-1).to,'/index.html');
});
test('ingress refuses absent/invalid upstream configuration and insecure CSP relaxation', () => {
  for(const env of [{}, {MERCATO_ORIGIN:'http://origin.test'}, {MERCATO_ORIGIN:'https://origin.test/path'}, {MERCATO_ORIGIN:'https://127.0.0.1'}]) assert.throws(()=>configureIngress({ MERCATO_COOKIE_CUTOVER_READY:'yes', ...env }));
  const c = configureIngress({ MERCATO_COOKIE_CUTOVER_READY:'yes', MERCATO_ORIGIN:'https://review.example.test', NETLIFY_PROXY_SIGNING_KEY:'a'.repeat(40),CONTEXT:'production',MERCATO_CSP_MODE:'enforce' });
  const headers=c.headers[0].values;
  assert.ok(headers['Content-Security-Policy']);
  assert.doesNotMatch(headers['Content-Security-Policy'].split(';').find(x=>x.includes('script-src')),/unsafe-inline|unsafe-eval|\*/);
  assert.equal(headers['X-Content-Type-Options'],'nosniff');
});

test('Cashfree sandbox checkout forms work in enforced and report-only CSP without broad form destinations', () => {
  for (const mode of ['enforce', 'report-only']) {
    const c=configureIngress({MERCATO_COOKIE_CUTOVER_READY:'yes',MERCATO_ORIGIN:'https://review.example.test',NETLIFY_PROXY_SIGNING_KEY:'a'.repeat(40),MERCATO_CSP_MODE:mode});
    const policy=c.headers[0].values[mode==='enforce'?'Content-Security-Policy':'Content-Security-Policy-Report-Only'];
    const form=policy.split(';').map(part=>part.trim()).find(part=>part.startsWith('form-action ')).split(/\s+/).slice(1);
    assert.deepEqual(form,["'self'",'https://sandbox.cashfree.com','https://payments-test.cashfree.com']);
    assert.doesNotMatch(policy,/unsafe-eval|script-src[^;]*\*/);
    assert.match(policy,/frame-ancestors 'none'/);
  }
});
test('edge replaces caller metadata while preserving exact body bytes and cookies', async () => {
  const raw = Buffer.from('{"amount": 12.00,"name":"म"}\r\n');
  const request=new Request('https://shop.example.test/api/public/cashfree/webhook-v2', {method:'POST',headers:{'X-Mercato-Client-IP':'1.2.3.4','Forwarded':'for=forged;proto=http','X-Forwarded-For':'forged','x-nf-sign':'forged','cookie':'session=opaque'},body:raw});
  let forwarded;
  await ingress(request,{ip:'2001:db8::1',next:async r=>{forwarded=r;return new Response('ok');}});
  assert.equal(forwarded.headers.get('x-mercato-client-ip'),'2001:db8::1');
  assert.equal(forwarded.headers.get('forwarded'),null);
  assert.equal(forwarded.headers.get('x-forwarded-for'),null);
  assert.equal(forwarded.headers.get('x-nf-sign'),null);
  assert.equal(forwarded.headers.get('cookie'),'session=opaque');
  assert.deepEqual(Buffer.from(await forwarded.arrayBuffer()),raw);
});
test('missing IP and edge execution failure cannot pass a request to origin', async () => {
  let called=false;
  await assert.rejects(()=>ingress(new Request('https://shop.test/api'),{ip:'',next:async()=>{called=true;}}));
  assert.equal(called,false);
  await assert.rejects(()=>ingress(new Request('https://shop.test/api'),{ip:'127.0.0.1',next:async()=>{throw Error('edge failed');}}));
});
