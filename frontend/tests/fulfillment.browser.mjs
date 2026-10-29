import test from 'node:test';
import assert from 'node:assert/strict';
import { build } from 'esbuild';
import { pathToFileURL, fileURLToPath } from 'node:url';
import { readFile, readdir, mkdir } from 'node:fs/promises';

const root = fileURLToPath(new URL('..', import.meta.url));
async function fixture(component='seller', setup) {
  const bundle=await build({stdin:{contents:`
    import React from 'react'; import {createRoot} from 'react-dom/client';
    import FulfillmentTab from './src/components/seller/FulfillmentTab.jsx';
    import OrderDetails from './src/components/orders/OrderDetails.jsx';
    import OrdersTab from './src/components/admin/OrdersTab.jsx';
    import Orders from './src/components/orders/Orders.jsx';
    import {privateApi} from './src/reduxStore/services/privateApi.js';
    import Help from './src/components/Help.jsx';
    import ReviewPaymentStep from './src/components/checkout/steps/ReviewPaymentStep.jsx';
    import MarketplaceFinance from './src/components/shared/MarketplaceFinance.jsx';
    import {RevenueCards} from './src/components/shared/DashboardWidgets.jsx';
    import useWorkflowResource from './src/hooks/useWorkflowResource.js';
    import {MemoryRouter} from 'react-router-dom';
    import {Provider} from 'react-redux'; import {configureStore} from '@reduxjs/toolkit';
    const store=configureStore({reducer:{cart:()=>({deliveryOptions:[],deliveryMethod:'STANDARD',cartLoading:false}),[privateApi.reducerPath]:privateApi.reducer},middleware:get=>get().concat(privateApi.middleware)});
    function Resource(){const [url,setUrl]=React.useState('/one');const value=useWorkflowResource(url);window.refresh=value.refresh;window.selectUrl=setUrl;return <p>{value.data?.name || 'Loading'}{value.error}</p>;}
    window.render=() => createRoot(document.getElementById('root')).render(<Provider store={store}><MemoryRouter initialEntries={[${component==='orders'?'"/orders?orderId=O1"':'"/"'}]}>${component==='seller'?'<FulfillmentTab />':component==='admin'?'<OrdersTab />':component==='orders'?'<Orders />':component==='resource'?'<Resource />':component==='help'?'<Help />':component==='finance'?'<><RevenueCards revenue={{allTime:189,thisMonth:189,today:189,averagePerOrder:63}} admin/><MarketplaceFinance admin/></>':component==='checkout'?'<ReviewPaymentStep selectedAddress={{}} cartWithProducts={[]} />':'<OrderDetails orderId="O1" />'}</MemoryRouter></Provider>);
    window.render();`,resolveDir:root,loader:'jsx'},bundle:true,write:false,jsx:'automatic',plugins:[{name:'fixture',setup(build){
      build.onResolve({filter:/backend\/api(?:\.js)?$/},() => ({path:'api',namespace:'fixture'}));
      build.onResolve({filter:/toastUtils(?:\.js)?$/},() => ({path:'toast',namespace:'fixture'}));
      build.onLoad({filter:/.*/,namespace:'fixture'},args => ({contents:args.path==='api'?`export const session={getSnapshot:()=>({epoch:window.epoch||1,user:{roles:['ROLE_USER','ROLE_SELLER','ROLE_ADMIN']}}),subscribe:(listener)=>{window.addEventListener('sessionchange',listener);return()=>window.removeEventListener('sessionchange',listener)}}; export default {get:(...args)=>window.fixtureGet(...args),request:args=>window.fixtureGet(args.url),post:(...args)=>window.fixturePost(...args)};`:`export const showWarningToast=()=>{}; export default {success:()=>{},error:()=>{}};`,loader:'js'}));
    }}]});
  const {chromium}=await import(pathToFileURL(process.env.PLAYWRIGHT_MODULE).href);
  const browser=await chromium.launch({channel:process.env.BROWSER_CHANNEL||'chrome',headless:true});
  try {
  const page=await browser.newPage({viewport:{width:390,height:844}});
  page.setDefaultTimeout(5000);
  await page.route('http://localhost:4178/**', route => route.fulfill({contentType:'text/html',body:'<p class="text-xs text-gray-500 p-2">Synthetic fixture - no real orders</p><div id="root"></div>'}));
  await page.goto('http://localhost:4178/');
  const assets=await readdir(new URL('../dist/assets/',import.meta.url));
  for (const asset of assets.filter(name=>name.endsWith('.css'))) await page.addStyleTag({content:(await readFile(new URL('../dist/assets/'+asset,import.meta.url),'utf8')) .replace(/@import\s*(?:url\([^)]*\)|"[^"]*"|'[^']*')\s*;/g,'')});
  assert.ok(assets.some(name=>name.endsWith('.css')), 'Run npm run build before browser checks');
  assert.match(await page.evaluate(()=>getComputedStyle(document.body).fontFamily), /sans-serif/);
  await page.evaluate(component => {
    window.posts=[]; window.mode='network'; window.version=3;
    const workflow={state:'CONFIRMED',version:3,serverNow:'2026-10-26T02:00:00Z',confirmedAt:'2026-10-26T00:00:00Z',sellerActionsAvailableAt:'2026-10-26T01:00:00Z',acceptDeadline:'2026-10-28T01:00:00Z',preparationReviewAt:'2026-10-28T00:00:00Z',allowedActions:['ACCEPT','START_PREPARING','MARK_PACKED','CANCEL'],actionMaxima:{ACCEPT:2,START_PREPARING:1,MARK_PACKED:1,CANCEL:4}};
    window.line={fulfillmentId:'F1',orderLineNumber:1,orderedQty:5,cancelledQty:1,acceptedQty:2,pendingQty:4,shippedQty:0,workflow:{...workflow,preparingQty:1,packedQty:0,deliveredQty:0},orderLineStatus:'PROCESSING',product:{productId:'P1',productName:'Ceramic cup',unitPrice:300,primaryImageUrl:'data:image/svg+xml,<svg xmlns="http://www.w3.org/2000/svg" width="100" height="100"><rect width="100" height="100" fill="tan"/></svg>'}};
    const customer=component==='customer'||component==='orders';
    window.record=()=>{
      const line=window.line, active=line.orderedQty-line.cancelledQty;
      const maxima={ACCEPT:active-line.acceptedQty,START_PREPARING:line.acceptedQty-line.workflow.preparingQty,MARK_PACKED:line.workflow.preparingQty-line.workflow.packedQty,CANCEL:active-(customer?line.workflow.preparingQty:line.shippedQty),...(component==='admin'?{SHIP:line.workflow.packedQty-line.shippedQty,DELIVER:line.shippedQty-line.workflow.deliveredQty}:{})};
      const state=active===0?'CANCELLED':line.workflow.deliveredQty===active?'DELIVERED':line.shippedQty===active?'SHIPPED':line.workflow.packedQty===active?'PACKED':line.workflow.preparingQty===active?'PREPARING':line.acceptedQty===active?'ACCEPTED':'CONFIRMED';
      const summary={...workflow,version:window.version,state,orderedQty:line.orderedQty,cancelledQty:line.cancelledQty,acceptedQty:line.acceptedQty,preparingQty:line.workflow.preparingQty,packedQty:line.workflow.packedQty,shippedQty:line.shippedQty,deliveredQty:line.workflow.deliveredQty};
      return {fulfillmentId:'F1',orderId:'O1',workflow:summary,orderStatus:'FULFILLMENT_PROCESSING',createdAt:'2026-10-26T00:00:00Z',orderLines:[{...line,workflow:{...line.workflow,state,version:window.version,actionMaxima:maxima,allowedActions:Object.keys(maxima).filter(action=>maxima[action]>0)}}],customer:{name:'Maya',email:'maya@example.test'},subTotal:1500,totalAmount:1500};
    };
    window.fixtureGet=async url=>({data:url.includes('metrics')?{coverage:{fromInclusive:'2026-09-26T02:00:00Z',toExclusive:'2026-10-26T02:00:00Z',matureCohortOnly:true},acceptance:{onTimeAcceptanceRate:null,acceptanceDenominatorQty:0,timeoutCancelledQty:0}}:url.includes('summary')?[window.record()]:url.includes('closed')?[]:url.includes('open')?[window.record()]:window.record()});
    window.fixturePost=async (url,body)=>{window.posts.push({url,body}); if(window.mode==='network') throw Error('offline'); if(window.mode==='conflict'){window.version=4;throw {response:{status:409}};} if(body.action==='ACCEPT'){window.line.acceptedQty+=body.qty;}else if(body.action==='CANCEL'){window.line.cancelledQty+=body.qty;}else if(body.action==='SHIP'){window.line.shippedQty+=body.qty;}else if(body.action==='DELIVER'){window.line.workflow.deliveredQty+=body.qty;}window.version++; return {data:window.record()};};
  }, component);
  if(setup) await page.evaluate(setup);
  await mkdir(new URL('../../backend/target/delhivery-m1-browser/',import.meta.url),{recursive:true});
  await page.addScriptTag({content:bundle.outputFiles[0].text});
  return {browser,page};
  } catch(error){await browser.close();throw error;}
}

test('seller quantities validate, retry identical uncertain payload, retain drafts on conflict and never offer Ship',async()=>{
  const {browser,page}=await fixture();
  try {
    await page.getByRole('button',{name:'View fulfillment F1'}).click();
    await page.getByRole('button',{name:'Start preparing',exact:true}).waitFor();
    assert.equal(await page.getByRole('button',{name:/ship/i}).count(),0);
    assert.ok(await page.getByRole('button',{name:'Refresh',exact:true}).evaluate(button=>button.getBoundingClientRect().height)>=36, 'Mercato action CSS must be loaded');
    await page.getByRole('button',{name:'Accept',exact:true}).click();
    await page.getByLabel('Quantity',{exact:true}).fill('3');
    assert.equal(await page.getByRole('button',{name:'Confirm accept',exact:true}).isDisabled(),true);
    await page.getByLabel('Quantity',{exact:true}).fill('2');
    await page.getByRole('button',{name:'Confirm accept',exact:true}).click();
    await page.getByRole('alert').waitFor();
    await page.getByRole('button',{name:'Confirm accept',exact:true}).click();
    let posts=await page.evaluate(()=>window.posts);
    assert.deepEqual(posts[0],posts[1]); assert.equal(posts[0].url,"/seller/fulfillment/commands"); assert.equal(posts[0].body.expectedVersion,3); assert.ok(posts[0].body.operationId);
    await page.evaluate(()=>window.mode='conflict');
    await page.getByRole('button',{name:'Confirm accept',exact:true}).click();
    await page.getByRole('alert').filter({hasText:'changed'}).waitFor();
    assert.equal(await page.getByLabel('Quantity',{exact:true}).inputValue(),'2');
    await page.evaluate(()=>window.mode='success');
    await page.getByRole('button',{name:'Confirm accept',exact:true}).click();
    await page.getByRole('dialog').waitFor({state:'hidden'});
    posts=await page.evaluate(()=>window.posts);
    assert.notEqual(posts[3].body.operationId,posts[2].body.operationId); assert.equal(posts[3].body.expectedVersion,4);
    assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>window.innerWidth),false);
    await mkdir(new URL('../../backend/target/delhivery-m1-browser/',import.meta.url),{recursive:true});
    await page.screenshot({path:fileURLToPath(new URL('../../backend/target/delhivery-m1-browser/seller-mobile.png',import.meta.url)),fullPage:true});
    await page.setViewportSize({width:768,height:1024});
    await page.screenshot({path:fileURLToPath(new URL('../../backend/target/delhivery-m1-browser/seller-tablet.png',import.meta.url)),fullPage:true});
    await page.setViewportSize({width:1440,height:1000});
    await page.screenshot({path:fileURLToPath(new URL('../../backend/target/delhivery-m1-browser/seller-desktop.png',import.meta.url)),fullPage:true});
  } finally {await browser.close();}
});

test('customer mode cancels repeatedly after preparation elsewhere and preserves reason after failed submission',async()=>{
  const {browser,page}=await fixture('customer');
  try {
    assert.equal(await page.getByRole('button',{name:'Cancel entire order',exact:true}).count(),0);
    await page.getByRole('button',{name:'Cancel units',exact:true}).click();
    await page.getByLabel('Quantity',{exact:true}).fill('2');
    assert.equal(await page.getByLabel('Quantity',{exact:true}).evaluate(input=>getComputedStyle(input).borderTopWidth),'1px');
    await page.getByLabel('Reason',{exact:true}).fill('Ordered too many');
    await page.getByLabel('I confirm this cancellation.').check();
    await page.getByRole('button',{name:'Confirm cancel units',exact:true}).click();
    await page.getByRole('alert').waitFor();
    assert.equal(await page.getByLabel('Reason',{exact:true}).inputValue(),'Ordered too many');
    assert.equal((await page.evaluate(()=>window.posts))[0].url,'/user/fulfillment/commands');
    await page.evaluate(()=>{window.mode='success';});
    await page.getByRole('button',{name:'Confirm cancel units',exact:true}).click();
    await page.getByRole('dialog').waitFor({state:'hidden'});
    await page.getByRole('button',{name:'Cancel units',exact:true}).click();
    assert.equal(await page.getByLabel('Quantity',{exact:true}).getAttribute('max'),'1');
    assert.equal(await page.getByLabel('I confirm this cancellation.').isChecked(),false);
    await page.screenshot({path:fileURLToPath(new URL('../../backend/target/delhivery-m1-browser/customer-mobile.png',import.meta.url)),fullPage:true});
    await page.setViewportSize({width:1440,height:1000});
    assert.ok((await page.getByRole('dialog').locator('.modal-frame').boundingBox()).width<=448);
    await page.screenshot({path:fileURLToPath(new URL('../../backend/target/delhivery-m1-browser/customer-desktop.png',import.meta.url)),fullPage:true});
  } finally {await browser.close();}
});


test('admin cancellation adapter requires reason, version and stable retry identity',async()=>{
  const {browser,page}=await fixture('admin');
  try {
    await page.getByRole('row').filter({hasText:'O1'}).click();
    await page.getByRole('button',{name:'Cancel entire order',exact:true}).click();
    await page.getByLabel('Reason',{exact:true}).fill('Buyer requested admin help');
    await page.getByLabel('I confirm this cancellation.').check();
    await page.getByRole('button',{name:'Confirm cancel entire order',exact:true}).click();
    await page.getByRole('alert').waitFor();
    await page.getByRole('button',{name:'Confirm cancel entire order',exact:true}).click();
    const posts=await page.evaluate(()=>window.posts);
    assert.equal(posts[0].url,'/user/orders/cancel');
    assert.equal(posts[0].body.expectedVersion,3);
    assert.equal(posts[0].body.reason,'Buyer requested admin help');
    assert.ok(posts[0].body.operationId);
    assert.deepEqual(posts[0],posts[1]);
  } finally {await browser.close();}
});

test('admin ships and delivers only supported quantities with stable retry and mobile focus',async()=>{
  const {browser,page}=await fixture('admin',()=>{
    window.line.orderedQty=2;window.line.cancelledQty=0;window.line.acceptedQty=2;
    window.line.workflow.preparingQty=2;window.line.workflow.packedQty=2;
  });
  try {
    await page.getByRole('row').filter({hasText:'O1'}).click();
    assert.equal(await page.getByRole('button',{name:'Mark delivered',exact:true}).count(),0);
    await page.getByRole('button',{name:'Mark shipped',exact:true}).click();
    await page.getByRole('button',{name:'Confirm mark shipped',exact:true}).click();
    await page.getByRole('alert').waitFor();
    await page.evaluate(()=>window.mode='success');
    await page.getByRole('button',{name:'Confirm mark shipped',exact:true}).click();
    await page.getByRole('button',{name:'Mark delivered',exact:true}).waitFor();
    assert.deepEqual(...await page.evaluate(()=>window.posts.slice(0,2)));
    await page.getByRole('button',{name:'Mark delivered',exact:true}).click();
    assert.equal(await page.getByLabel('Quantity',{exact:true}).inputValue(),'1');
    assert.equal(await page.getByLabel('Quantity',{exact:true}).getAttribute('max'),'1');
    await page.getByRole('button',{name:'Confirm mark delivered',exact:true}).click();
    await page.getByRole('button',{name:'Mark delivered',exact:true}).waitFor({state:'hidden'});
    const posts=await page.evaluate(()=>window.posts);assert.equal(posts.at(-1).url,'/fulfillment/commands');assert.equal(posts.at(-1).body.action,'DELIVER');assert.equal(posts.at(-1).body.expectedVersion,4);
    await page.emulateMedia({reducedMotion:'reduce'});
    for(const width of [320,390]){
      await page.setViewportSize({width,height:844});
      assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'admin detail must fit mobile');
      await page.getByRole('button',{name:'Back',exact:true}).focus();await page.keyboard.press('Tab');assert.notEqual(await page.evaluate(()=>document.activeElement.tagName),'BODY');
      await page.screenshot({path:fileURLToPath(new URL(`../../backend/target/delhivery-m1-browser/admin-core-${width}.png`,import.meta.url)),fullPage:true});
    }
  }finally{await browser.close();}
});

test('finance keeps collections, platform accrual and marketplace seller earnings distinct',async()=>{
  const {browser,page}=await fixture('finance',()=>{
    window.fixtureGet=async()=>({data:{from:'2026-10-01',through:'2026-10-27',recognitionBasis:'Verified observations; current fulfillment and committed refunds',capturedPayments:363,platformFees:30,handlingFees:3,platformAccrualBeforeCosts:34,successfulRefunds:111,pendingRefunds:0,shippingCollected:33,promotionBookings:0,promotionCredits:0,unclassifiedLegacyCharges:0,totals:{fulfilledSales:200,commission:1,commissionReversals:0,sellerPaidDelivery:10},sellerBreakdown:[],lines:[]}});
  });try{
    await page.getByRole('heading',{name:'Marketplace seller earnings',exact:true}).waitFor();
    await page.getByText('Platform accrual before costs',{exact:true}).waitFor();
    assert.equal(await page.getByText('Retained handling fees',{exact:true}).count(),1);
    assert.equal(await page.getByText('Verified buyer payments captured',{exact:true}).count(),1);
    assert.equal(await page.getByText('Average earnings / order',{exact:true}).count(),1);
    await page.getByText(/Recognition basis: Verified observations/).waitFor();
    assert.equal(await page.getByText('Revenue',{exact:true}).count(),0);
    for(const width of [320,390]){await page.setViewportSize({width,height:844});assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));await page.screenshot({path:fileURLToPath(new URL(`../../backend/target/delhivery-m1-browser/finance-core-${width}.png`,import.meta.url)),fullPage:true});}
  }finally{await browser.close();}
});


test('resource retains same-scope content but fences superseded reads, record switches and account changes',async()=>{
  const {browser,page}=await fixture('resource',()=>{
    window.pending=[];
    window.fixtureGet=(url)=>new Promise(resolve=>window.pending.push({url,resolve}));
    window.finish=(index,name)=>window.pending[index].resolve({data:{name}});
  });
  try {
    await page.waitForFunction(()=>window.pending.length===1);
    await page.evaluate(()=>window.finish(0,'First record'));
    await page.getByText('First record',{exact:true}).waitFor();
    await page.evaluate(()=>{window.refresh();window.refresh();});
    assert.equal(await page.getByText('First record',{exact:true}).count(),1);
    await page.evaluate(()=>window.finish(2,'Newest result'));
    await page.getByText('Newest result',{exact:true}).waitFor();
    await page.evaluate(()=>window.finish(1,'Outdated result'));
    assert.equal(await page.getByText('Outdated result',{exact:true}).count(),0);
    await page.evaluate(()=>{window.refresh();window.selectUrl('/two');});
    await page.waitForFunction(()=>window.pending.some(item=>item.url==='/two'));
    assert.equal(await page.getByText('Newest result',{exact:true}).count(),0);
    await page.evaluate(()=>window.finish(3,'Wrong record'));
    assert.equal(await page.getByText('Wrong record',{exact:true}).count(),0);
    await page.evaluate(()=>{window.epoch=2;window.dispatchEvent(new Event('sessionchange'));});
    await page.waitForFunction(()=>window.pending.length===6);
    await page.evaluate(()=>window.finish(4,'Previous account'));
    assert.equal(await page.getByText('Previous account',{exact:true}).count(),0);
    await page.evaluate(()=>window.finish(5,'Current account'));
    await page.getByText('Current account',{exact:true}).waitFor();
  } finally {await browser.close();}
});

test('seller hold is read only and refreshes at the server-relative release without replacing content',async()=>{
  const {browser,page}=await fixture('seller',()=>{
    const get=window.fixtureGet;let reads=0;
    window.fixtureGet=async(url)=>{
      const result=await get(url);
      if(url.includes('/fulfillment-orders/')){
        reads++;
        result.data.workflow.sellerActionsAvailableAt='2026-10-26T02:00:01Z';
        if(reads===1){
          result.data.orderLines[0].workflow.allowedActions=[];
        } else result.data.workflow.serverNow='2026-10-26T02:00:01.150Z';
      }
      return result;
    };
  });
  try {
    await page.getByRole('button',{name:'View fulfillment F1'}).click();
    await page.getByText(/Read only · seller actions unlock/).waitFor();
    assert.equal(await page.getByRole('button',{name:'Accept',exact:true}).count(),0);
    await page.getByRole('button',{name:'Accept',exact:true}).waitFor();
    assert.equal(await page.getByText(/Read only · seller actions unlock/).count(),0);
    assert.equal(await page.locator('time[datetime="2026-10-26T02:00:01Z"]').count(),1);
    assert.equal(await page.getByText('Ceramic cup',{exact:true}).count(),1);
    assert.equal(await page.getByRole('button',{name:/ship/i}).count(),0);
  } finally {await browser.close();}
});

test('checkout and public help disclose actual cancellation allocation and immediate sandbox collection',async()=>{
  for(const component of ['checkout','help']){
    const {browser,page}=await fixture(component);
    try {
      await page.getByRole('button',{name:'Cancellation and refund policy'}).click();
      await page.getByText(/partial customer cancellation retains fixed platform and handling fees/).waitFor();
      if(component==='help') await page.getByRole('button',{name:'What happens if payment is interrupted?'}).click();
      await page.getByText(/Cashfree immediate payment collection/).waitFor();
    } finally {await browser.close();}
  }
});

test('customer order list refreshes on back navigation after quantity cancellation',async()=>{
  const {browser,page}=await fixture('orders');
  try {
    await page.getByRole('button',{name:'Cancel units',exact:true}).click();
    await page.getByLabel('Reason',{exact:true}).fill('Ordered too many');
    await page.getByLabel('I confirm this cancellation.').check();
    await page.evaluate(()=>{window.mode='success';const get=window.fixtureGet;window.fixtureGet=async(url)=>{const value=await get(url);if(url.includes('summary'))value.data[0].workflow.state='PACKED';return value;};});
    await page.getByRole('button',{name:'Confirm cancel units',exact:true}).click();
    await page.getByRole('dialog').waitFor({state:'hidden'});
    await page.getByRole('button',{name:'All orders',exact:true}).click();
    await page.getByText('Processing',{exact:true}).waitFor();
    await page.getByRole('button',{name:'View order O1',exact:true}).click();
    await page.getByRole('button',{name:'All orders',exact:true}).waitFor();
    await page.evaluate(()=>{const get=window.fixtureGet;window.fixtureGet=url=>url.includes('summary')?Promise.reject(Error('offline summary')):get(url);});
    await page.getByRole('button',{name:'All orders',exact:true}).click();
    await page.getByText(/Failed to fetch orders/).waitFor();
    assert.equal(await page.getByRole('button',{name:'View order O1',exact:true}).count(),1);
  } finally {await browser.close();}
});


test('seller acceptance deadline refreshes allowed actions while the detail remains mounted',async()=>{
  const {browser,page}=await fixture('seller',()=>{
    const get=window.fixtureGet;let reads=0;
    window.fixtureGet=async(url)=>{
      const result=await get(url);
      if(url.includes('/fulfillment-orders/')){
        result.data.workflow.acceptDeadline='2026-10-26T02:00:01Z';
        if(++reads>1){
          result.data.workflow.serverNow='2026-10-26T02:00:02Z';
          result.data.orderLines[0].workflow.allowedActions=['START_PREPARING','MARK_PACKED','CANCEL'];
        }
      }
      return result;
    };
  });
  try {
    await page.getByRole('button',{name:'View fulfillment F1'}).click();
    await page.getByRole('button',{name:'Accept',exact:true}).waitFor();
    await page.getByRole('button',{name:'Accept',exact:true}).waitFor({state:'hidden'});
    assert.equal(await page.getByText('Ceramic cup',{exact:true}).count(),1);
    await page.getByText(/Acceptance deadline passed/).waitFor();
  } finally {await browser.close();}
});


test('a dual-role customer can cancel an eligible whole order only through the explicit customer route',async()=>{
  const {browser,page}=await fixture('customer',()=>{window.line.workflow.preparingQty=0;});
  try {
    await page.getByRole('button',{name:'Cancel entire order',exact:true}).click();
    await page.getByLabel('Reason',{exact:true}).fill('Please cancel this order');
    assert.equal(await page.getByRole('button',{name:'Confirm cancel entire order',exact:true}).isDisabled(),true);
    await page.getByLabel('I confirm this cancellation.').check();
    await page.getByRole('button',{name:'Confirm cancel entire order',exact:true}).click();
    await page.getByRole('alert').waitFor();
    const [post]=await page.evaluate(()=>window.posts);
    assert.equal(post.url,'/user/fulfillment/cancellations');
    assert.equal(post.body.orderId,'O1');
    assert.equal(post.body.expectedVersion,3);
    assert.ok(post.body.operationId);
    assert.equal('units' in post.body,false);
    assert.equal(await page.getByLabel('Reason',{exact:true}).inputValue(),'Please cancel this order');
  } finally {await browser.close();}
});


test('admin initial detail failure shows recovery, retries successfully and can return to the list',async()=>{
  const {browser,page}=await fixture('admin',()=>{
    const get=window.fixtureGet;
    window.adminDetailReads=0;
    window.fixtureGet=async(url)=>{
      if(url==='/admin/orders/O1' && ++window.adminDetailReads%2===1){
        throw {response:{data:{message:'Order service temporarily unavailable.'}}};
      }
      return get(url);
    };
  });
  try {
    await page.getByRole('row').filter({hasText:'O1'}).click();
    await page.getByRole('alert').filter({hasText:'Order service temporarily unavailable.'}).waitFor();
    assert.equal(await page.getByRole('button',{name:'Back to orders',exact:true}).isEnabled(),true);
    await page.getByRole('button',{name:'Try again',exact:true}).click();
    await page.getByText('Order #O1',{exact:true}).waitFor();
    await page.getByText('Ceramic cup',{exact:true}).waitFor();
    assert.equal(await page.evaluate(()=>window.adminDetailReads),2);
    await page.getByRole('button',{name:'Back',exact:true}).click();
    await page.getByRole('row').filter({hasText:'O1'}).click();
    await page.getByRole('alert').filter({hasText:'Order service temporarily unavailable.'}).waitFor();
    await page.getByRole('button',{name:'Back to orders',exact:true}).click();
    await page.getByRole('row').filter({hasText:'O1'}).waitFor();
  } finally {await browser.close();}
});

test('compact customer summaries show disjoint mixed quantities and fit mobile and desktop',async()=>{
  const {browser,page}=await fixture('customer',()=>{
    window.line.orderedQty=10;window.line.cancelledQty=2;window.line.acceptedQty=8;
    window.line.workflow.preparingQty=7;window.line.workflow.packedQty=6;
    window.line.shippedQty=5;window.line.workflow.deliveredQty=2;
  });
  try {
    await page.getByText('Qty 10 · Processing',{exact:true}).waitFor();
    const breakdowns=page.getByRole('definition',{name:''});
    assert.deepEqual(await breakdowns.allTextContents(),['3','3','2','2','3','3','2','2']);
    assert.equal(await page.getByText(/ordered:|accepted:|preparing:|packed:/i).count(),0);
    const policy=page.getByRole('button',{name:'Cancellation and refund policy',exact:true});
    assert.equal(await policy.getAttribute('aria-expanded'),'false');
    await page.emulateMedia({reducedMotion:'reduce'});
    await policy.focus();await page.keyboard.press('Enter');
    assert.equal(await policy.getAttribute('aria-expanded'),'true');
    await page.keyboard.press('Enter');
    assert.equal(await policy.getAttribute('aria-expanded'),'false');
    await mkdir(new URL('../../backend/target/delhivery-m1-browser/',import.meta.url),{recursive:true});
    for(const width of [320,390,1440]){
      await page.setViewportSize({width,height:1000});
      assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'customer summary must fit');
      await page.screenshot({path:fileURLToPath(new URL(`../../backend/target/delhivery-m1-browser/customer-mixed-${width}.png`,import.meta.url)),fullPage:true});
    }
  }finally{await browser.close();}
});

test('one cancelled item has a single compact state and no quantity breakdown',async()=>{
  const {browser,page}=await fixture('customer',()=>{
    window.line.orderedQty=1;window.line.cancelledQty=1;window.line.acceptedQty=0;
    window.line.workflow.preparingQty=0;window.line.workflow.packedQty=0;
  });
  try{
    await page.getByText('Qty 1 · Cancelled',{exact:true}).waitFor();
    assert.equal(await page.locator('dl[aria-label="Quantity breakdown"]').count(),0);
    assert.equal(await page.locator('p').filter({hasText:/^Qty 1$/}).count(),1,'order total remains visible');
    assert.equal(await page.getByRole('button',{name:'Cancel units',exact:true}).count(),0);
    await page.screenshot({path:fileURLToPath(new URL('../../backend/target/delhivery-m1-browser/customer-cancelled-390.png',import.meta.url)),fullPage:true});
  }finally{await browser.close();}
});

test('operational details retain Packed while admin line quantity appears only once',async()=>{
  for(const role of ['seller','admin']){
    const {browser,page}=await fixture(role,()=>{
      window.line.orderedQty=2;window.line.cancelledQty=0;window.line.acceptedQty=2;
      window.line.workflow.preparingQty=2;window.line.workflow.packedQty=2;
    });
    try{
      if(role==='seller') await page.getByRole('button',{name:'View fulfillment F1'}).click();
      else await page.getByRole('row').filter({hasText:'O1'}).click();
      await page.getByText('Packed',{exact:true}).first().waitFor();
      assert.equal(await page.locator('dl[aria-label="Quantity breakdown"]').count(),0);
      assert.equal(await page.getByText('Qty 2 · Packed',{exact:true}).count(),1,'order summary retains quantity and stage');
      if(role==='admin') assert.equal(await page.getByText('Qty 2 · ₹300.00 each',{exact:true}).count(),1);
      await page.emulateMedia({reducedMotion:'reduce'});
      for(const width of [320,390,1440]){
        await page.setViewportSize({width,height:1000});
        assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),`${role} summary must fit`);
        await page.getByRole('button',{name:'Back',exact:true}).focus();await page.keyboard.press('Tab');
        assert.notEqual(await page.evaluate(()=>document.activeElement.tagName),'BODY');
        await page.screenshot({path:fileURLToPath(new URL(`../../backend/target/delhivery-m1-browser/${role}-packed-${width}.png`,import.meta.url)),fullPage:true});
      }
    }finally{await browser.close();}
  }
});

test('seller incoming list revalidates on focus and each visible minute',async()=>{
  const {browser,page}=await fixture('seller',()=>{
    window.reads=[];const get=window.fixtureGet;window.fixtureGet=async(...args)=>{window.reads.push(args[0]);return get(...args);};
    const interval=window.setInterval;window.setInterval=(fn,ms,...args)=>{if(ms===60000)window.minute=fn;return interval(fn,ms,...args);};
  });
  try {
    await page.getByRole('button',{name:'View fulfillment F1'}).waitFor();
    await page.evaluate(()=>{window.reads=[];window.dispatchEvent(new Event('focus'));});
    await page.waitForFunction(()=>window.reads.includes('/seller/open-fulfillment-orders'));
    await page.evaluate(()=>{window.reads=[];window.minute?.();});
    await page.waitForFunction(()=>window.reads.includes('/seller/open-fulfillment-orders'));
    await page.evaluate(()=>{window.reads=[];Object.defineProperty(document,'visibilityState',{configurable:true,value:'hidden'});window.minute();});
    assert.deepEqual(await page.evaluate(()=>window.reads),[]);
    assert.equal(await page.getByRole('button',{name:'View fulfillment F1'}).count(),1);
  } finally {await browser.close();}
});

test('dual-role seller whole cancellation uses the explicit seller route',async()=>{
  const {browser,page}=await fixture('seller');
  try {
    await page.getByRole('button',{name:'View fulfillment F1'}).click();
    await page.getByRole('button',{name:'Cancel all remaining',exact:true}).click();
    await page.getByLabel('Reason',{exact:true}).fill('Seller cancellation');
    await page.getByLabel('I confirm this cancellation.').check();
    await page.getByRole('button',{name:'Confirm cancel all remaining',exact:true}).click();
    await page.getByRole('alert').waitFor();
    const [post]=await page.evaluate(()=>window.posts);
    assert.equal(post.url,'/seller/fulfillment/cancellations');
    assert.equal(post.body.fulfillmentId,'F1');assert.equal(post.body.expectedVersion,3);assert.ok(post.body.operationId);
  } finally {await browser.close();}
});
