import test from 'node:test';
import assert from 'node:assert/strict';
import {build} from 'esbuild';
import {fileURLToPath,pathToFileURL} from 'node:url';
import {readFile,readdir,mkdir} from 'node:fs/promises';

async function fixture(setup,value){
  const root=fileURLToPath(new URL('..',import.meta.url));
  const bundle=await build({stdin:{contents:`
    import React from 'react';import {createRoot} from 'react-dom/client';
    import {MemoryRouter,Routes,Route,useLocation} from 'react-router-dom';
    import {Provider} from 'react-redux';import {configureStore} from '@reduxjs/toolkit';
    import Checkout from './src/components/checkout/Checkout.jsx';
    import {cartReducer} from './src/reduxStore/reducers/cartReducer.js';
    import {authReducer} from './src/reduxStore/reducers/authReducer.js';
    import {privateApi} from './src/reduxStore/services/privateApi.js';
    const cart=cartReducer(undefined,{type:'SYNC_CART_SUCCESS',payload:window.cart});
    const auth={...authReducer(undefined,{}),user:{userId:'buyer-1'},addresses:window.addresses,selectedAddressForCheckout:window.addresses[1]};
    window.store=configureStore({reducer:{cart:cartReducer,auth:authReducer,[privateApi.reducerPath]:privateApi.reducer},preloadedState:{cart,auth},middleware:get=>get().concat(privateApi.middleware)});
    function Location(){const value=useLocation();window.locationState=value;return <p data-testid="location">{value.pathname}{value.search}</p>;}
    createRoot(document.getElementById('root')).render(<Provider store={window.store}><MemoryRouter initialEntries={[window.startUrl]}><Location/><Routes><Route path="/checkout" element={<Checkout/>}><Route path="cashfree-payment" element={<p>Payment screen</p>}/></Route><Route path="/orders" element={<p>Existing order</p>}/><Route path="/cart" element={<p>Bag screen</p>}/></Routes></MemoryRouter></Provider>);
  `,resolveDir:root,loader:'jsx'},bundle:true,write:false,jsx:'automatic',plugins:[{name:'fixture',setup(build){
    build.onResolve({filter:/backend\/api(?:\.js)?$/},()=>({path:'api',namespace:'fixture'}));
    build.onResolve({filter:/toastUtils(?:\.js)?$/},()=>({path:'toast',namespace:'fixture'}));
    build.onLoad({filter:/.*/,namespace:'fixture'},args=>({contents:args.path==='api'?`
      export const session={getSnapshot:()=>({epoch:window.epoch,user:{userId:'buyer-'+window.epoch}}),assertCurrent:config=>{if(config.sessionEpoch!==window.epoch)throw Error('Account changed')},subscribe:listener=>{const call=()=>listener({epoch:window.epoch});window.addEventListener('sessionchange',call);return()=>window.removeEventListener('sessionchange',call)}};
      export default {get:(...args)=>window.get(...args),post:(...args)=>window.post(...args),request:args=>window.get(args.url)};
    `:`export const showWarningToast=()=>{};export default {success:()=>{},error:()=>{}};`,loader:'js'}));
  }}]});
  const {chromium}=await import(pathToFileURL(process.env.PLAYWRIGHT_MODULE).href);
  const browser=await chromium.launch({channel:process.env.BROWSER_CHANNEL||'msedge',headless:true});
  try{
    const page=await browser.newPage({viewport:{width:390,height:844}});page.setDefaultTimeout(5000);
    await page.route('http://localhost:4178/**',route=>route.fulfill({contentType:'text/html',body:'<div id="root"></div>'}));
    await page.goto('http://localhost:4178/');
    for(const name of (await readdir(new URL('../dist/assets/',import.meta.url))).filter(name=>name.endsWith('.css')))await page.addStyleTag({content:await readFile(new URL('../dist/assets/'+name,import.meta.url),'utf8')});
    await page.evaluate(()=>{
      window.epoch=1;window.startUrl='/checkout';window.posts=[];window.reads=[];window.mode='network';
      window.addresses=[{addressId:'A1',recipientName:'Default buyer',addressLine1:'Default road',defaultAddress:true},{addressId:'A2',recipientName:'Chosen buyer',addressLine1:'Selected road',city:'City',state:'State',pincode:'123456',recipientPhone:'9999999999'}];
      window.cart={cartItems:[{productId:'P1',quantity:1,itemPrice:600,lineTotal:600}],version:7,subtotal:600,total:612,charges:[{type:'PLATFORM_FEE',amount:12}],deliveryMethod:'STANDARD'};
      window.original={operationId:'original-operation',addressId:'A2',expectedTotal:612,deliveryMethod:'STANDARD',expectedCartVersion:7};
      window.recovery={operationId:'original-operation',orderId:'O1',request:window.original,state:'REPLAY_CAPTURE',canReplayCapture:true,paymentSessionId:null,order:{orderId:'O1',totalAmount:612}};
      window.get=async url=>{window.reads.push(url);if(url.includes('checkout-intents'))return {data:window.recovery};if(url==='/cart')return {data:window.cart};if(url==='/user/addresses')return {data:window.addresses};return {data:{productId:'P1',productName:'Ceramic cup',sellerName:'Studio'}};};
      window.post=async(url,body)=>{window.posts.push({url,body});if(window.mode==='network')throw Error('offline');if(window.mode==='conflict'){window.cart={...window.cart,version:9,total:620};throw {response:{status:409}};}if(window.mode==='pending')return new Promise(resolve=>window.finish=()=>resolve({orderId:'wrong',data:{paymentSessionId:'late',order:{orderId:'O1',totalAmount:612}}}));return {data:{paymentSessionId:'safe',order:{orderId:'O1',totalAmount:612}}};};
    });
    if(setup)await page.evaluate(setup,value);
    await page.addScriptTag({content:bundle.outputFiles[0].text});
    return {browser,page};
  }catch(error){await browser.close();throw error;}
}
const pay=page=>page.getByRole('button',{name:'Continue to payment',exact:true}).filter({visible:true}).first();

test('uncertain checkout retries exact body and URL stores only its opaque ID',async()=>{
  const {browser,page}=await fixture();try{
    await pay(page).click();await page.getByText(/result is uncertain/).filter({visible:true}).first().waitFor();
    await page.evaluate(()=>{window.store.dispatch({type:'SYNC_CART_SUCCESS',payload:{...window.cart,version:20,total:999}});window.store.dispatch({type:'SELECT_CHECKOUT_ADDRESS',payload:window.addresses[0]});});
    await pay(page).click();await page.waitForFunction(()=>window.posts.length===2);
    const posts=await page.evaluate(()=>window.posts);assert.deepEqual(posts[0],posts[1]);assert.equal(posts[0].body.expectedCartVersion,7);assert.equal(posts[0].body.addressId,'A2');
    const query=await page.evaluate(()=>window.locationState.search);assert.match(query,/checkoutIntent=/);assert.doesNotMatch(query,/address|total|session|612|A2/);
    assert.equal(await page.evaluate(()=>Object.keys(localStorage).length+Object.keys(sessionStorage).length),0);
    await page.emulateMedia({reducedMotion:'reduce'});await mkdir(new URL('../../backend/target/task56-browser/',import.meta.url),{recursive:true});
    for(const width of [320,390]){await page.setViewportSize({width,height:844});assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));assert.equal(await page.getByRole('button',{name:'Continue to payment',exact:true}).filter({visible:true}).count(),1);await page.locator('[data-testid="location"]').evaluate(element=>element.hidden=true);await page.screenshot({path:fileURLToPath(new URL(`../../backend/target/task56-browser/checkout-retry-${width}.png`,import.meta.url)),fullPage:true});}
  }finally{await browser.close();}
});
test('409 refreshes authoritative bag, preserves chosen address and creates an ID for the new reviewed quote',async()=>{
  const {browser,page}=await fixture(()=>window.mode='conflict');try{
    await pay(page).click();await page.getByText(/Your bag or checkout changed/).filter({visible:true}).first().waitFor();
    assert.equal(await page.evaluate(()=>window.store.getState().cart.version),9);assert.equal(await page.evaluate(()=>window.store.getState().auth.selectedAddressForCheckout.addressId),'A2');
    await page.getByText('Selected road',{exact:true}).waitFor();
    await page.evaluate(()=>window.mode='success');await pay(page).click();await page.getByText('Payment screen',{exact:true}).waitFor();
    const posts=await page.evaluate(()=>window.posts);assert.notEqual(posts[0].body.operationId,posts[1].body.operationId);assert.equal(posts[1].body.expectedCartVersion,9);assert.equal(posts[1].body.expectedTotal,620);
  }finally{await browser.close();}
});
test('reload recovers exact replay despite empty bag, ready session skips capture and completed order opens original order',async()=>{
  for(const state of ['REPLAY_CAPTURE','PAYMENT_READY','ORDER_UPDATED']){
    const {browser,page}=await fixture(state=>{window.startUrl='/checkout?checkoutIntent=original-operation';window.cart={...window.cart,cartItems:[]};window.mode='success';window.recovery.state=state;window.recovery.canReplayCapture=state!=='ORDER_UPDATED';window.recovery.paymentSessionId=state==='PAYMENT_READY'?'stored-safe':null;},state);try{
      if(state==='REPLAY_CAPTURE'){
        await pay(page).click();await page.getByText('Payment screen',{exact:true}).waitFor();assert.deepEqual((await page.evaluate(()=>window.posts))[0].body,await page.evaluate(()=>window.original));
      }else if(state==='PAYMENT_READY'){
        await pay(page).click();await page.getByText('Payment screen',{exact:true}).waitFor();assert.equal((await page.evaluate(()=>window.posts)).length,0);assert.equal(await page.evaluate(()=>window.store.getState().auth.paymentData.paymentSessionId),'stored-safe');
      }else{
        await page.getByText('Existing order',{exact:true}).waitFor();assert.equal((await page.evaluate(()=>window.posts)).length,0);assert.equal(await page.evaluate(()=>window.locationState.search),'?orderId=O1');
      }
    }finally{await browser.close();}
  }
});
test('account changes fence a late capture and foreign intent recovery never opens payment',async()=>{
  const {browser,page}=await fixture(()=>window.mode='pending');try{
    await pay(page).click();await page.waitForFunction(()=>window.finish);
    await page.evaluate(()=>{const get=window.get;window.get=async url=>{if(url.includes('checkout-intents'))throw {response:{status:404}};return get(url);};window.epoch=2;window.dispatchEvent(new Event('sessionchange'));window.finish();});
    await page.getByText(/could not be found for this account/).filter({visible:true}).first().waitFor();
    assert.equal(await page.getByText('Payment screen',{exact:true}).count(),0);assert.equal(await page.evaluate(()=>window.store.getState().auth.paymentData),null);
    assert.equal((await page.evaluate(()=>window.posts)).length,1);
  }finally{await browser.close();}
});
