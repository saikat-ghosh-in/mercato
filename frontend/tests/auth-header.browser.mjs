import test from 'node:test';
import assert from 'node:assert/strict';
import {build} from 'esbuild';
import {readFile,readdir,mkdir} from 'node:fs/promises';
import {fileURLToPath,pathToFileURL} from 'node:url';

const root=fileURLToPath(new URL('..',import.meta.url));
async function fixture(view='login',path='/'){
  const bundle=await build({stdin:{contents:`
    import React from 'react';import {createRoot} from 'react-dom/client';import {MemoryRouter,useLocation} from 'react-router-dom';
    import {Provider} from 'react-redux';import {configureStore} from '@reduxjs/toolkit';
    import Header from './src/components/Header.jsx';import Login from './src/components/auth/LogIn.jsx';
    import ProfileEdit from './src/components/user/ProfileEdit.jsx';
    ${view==='home'?"import Home from './src/components/home/Home.jsx';":""}
    const initial={user:{username:'maya'},userDetails:{userId:7,firstName:'Maya',lastName:'Shah',username:'maya',email:'maya@example.test',emailVerified:true,profileImageUrl:'placeholder',roles:['ROLE_USER']},sessionStatus:'authenticated'};
    const categories=${JSON.stringify(view==='home'?['Lighting','Kitchen & Dining','Clothing','Sports & Outdoors','Accessories']:[])}.map((categoryName,categoryId)=>({categoryName,categoryId}));
    window.store=configureStore({reducer:{auth:(s=initial,a)=>a.type==='set'?{...s,...a.payload}:s,categories:()=>({categories}),cart:()=>({cartQty:0,cartItems:[]})}});
    window.account=value=>window.store.dispatch({type:'set',payload:value});
    function CurrentRoute(){const location=useLocation();return <output data-testid="current-route" hidden>{location.pathname+location.search}</output>}
    createRoot(document.getElementById('root')).render(<Provider store={window.store}><MemoryRouter initialEntries={[${JSON.stringify(path)}]}><div className="app-page"><Header/><CurrentRoute/><main id="main-content">${view==='profile'?'<ProfileEdit/>':view==='home'?'<Home/>':'<Login/>'}</main></div></MemoryRouter></Provider>);
  `,resolveDir:root,loader:'jsx'},bundle:true,write:false,jsx:'automatic',loader:{'.webp':'dataurl'},plugins:[{name:'actions',setup(build){
    build.onResolve({filter:/reduxStore\/actions\/authActions(?:\.js)?$/},()=>({path:'auth',namespace:'fixture'}));
    build.onLoad({filter:/.*/,namespace:'fixture'},()=>({contents:`export const authenticateUser=data=>async()=>{window.loginPayload=data};export const logOutUser=()=>()=>window.account({user:null,userDetails:null,sessionStatus:'anonymous'});export const fetchUserDetails=()=>async()=>window.account({userDetails:{...window.store.getState().auth.userDetails,...window.savedProfile}});`,loader:'js'}));
    build.onResolve({filter:/backend\/api(?:\.js)?$/},()=>({path:'api',namespace:'fixture-api'}));
    build.onLoad({filter:/.*/,namespace:'fixture-api'},()=>({contents:`
      const products={'Lighting':{categoryName:'Lighting',secondaryImageUrls:['/images/lamp.jpg']},
        'Kitchen & Dining':{categoryName:'Kitchen & Dining',primaryImageUrl:'/images/plate.jpg'},
        'Sports & Outdoors':{categoryName:'Sports & Outdoors',primaryImageUrl:'/images/backpack.jpg'},
        'Accessories':{categoryName:'Clothing',primaryImageUrl:'/images/wrong.jpg'}};
      export const session={getSnapshot:()=>({epoch:0}),subscribe:()=>{}};
      export default {put:async(path,data)=>{window.savedProfile=data;return {data}},
        get:async(path,{params})=>({data:path.endsWith('/promotions')?[]:{content:products[params.category]?[products[params.category]]:[]}})};
    `,loader:'js'}));
    build.onResolve({filter:/utils\/toastUtils(?:\.js)?$/},()=>({path:'toast',namespace:'fixture-toast'}));
    build.onLoad({filter:/.*/,namespace:'fixture-toast'},()=>({contents:'export default {success(){},error(){}};export const showWarningToast=()=>{};',loader:'js'}));
  }}]});
  const {chromium}=await import(pathToFileURL(process.env.PLAYWRIGHT_MODULE).href);const browser=await chromium.launch({channel:'chrome',headless:true});
  try{
    const page=await browser.newPage({viewport:{width:1440,height:900}});page.setDefaultTimeout(5000);
    const errors=[];page.on('pageerror',error=>errors.push(error.message));
    await page.route('http://localhost:4179/**',r=>r.fulfill(r.request().resourceType()==='image'
      ? {contentType:'image/svg+xml',body:'<svg xmlns="http://www.w3.org/2000/svg" width="100" height="100"/>'}
      : {contentType:'text/html',body:'<div id="root"></div>'}));await page.goto('http://localhost:4179/');
    for(const file of (await readdir(root+'/dist/assets')).filter(n=>n.endsWith('.css'))){let css=await readFile(root+'/dist/assets/'+file,'utf8');await page.addStyleTag({content:css.replace(/@import\s*(?:url\([^)]*\)|"[^"]*"|'[^']*')\s*;/g,'')});}
    await page.addScriptTag({content:bundle.outputFiles[0].text});return {browser,page,errors};
  }catch(error){await browser.close();throw error;}
}

test('header clearly distinguishes signed in, guest and initialization states at mobile widths',async()=>{
  const {browser,page}=await fixture();try{
    const account=page.getByRole('button',{name:'Account menu',exact:true});await account.waitFor();assert.match(await account.getAttribute('title'),/Signed in as Maya/);
    assert.equal(await account.locator('svg').count(),1);assert.equal(await account.locator('img').count(),0);assert.equal((await account.innerText()).trim(),'');
    await mkdir(root+'/../backend/target/email-login-preview',{recursive:true});await account.screenshot({path:root+'/../backend/target/email-login-preview/signed-in.png'});
    await account.click();await page.getByRole('menuitem',{name:'My orders',exact:true}).waitFor();await page.keyboard.press('Escape');
    for(const width of [320,390,768,1440]){await page.setViewportSize({width,height:900});assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);assert.equal(await account.isVisible(),true);}
    await page.setViewportSize({width:390,height:900});await account.screenshot({path:root+'/../backend/target/email-login-preview/mobile-signed-in.png'});
    await page.evaluate(()=>window.account({user:null,userDetails:null,sessionStatus:'ready'}));
    for(const width of [320,390,768,1440]){await page.setViewportSize({width,height:900});assert.equal(await page.locator('header').getByRole('link',{name:'Sign in',exact:true}).isVisible(),true);assert.equal(await page.locator('header').getByRole('link',{name:'Register',exact:true}).count(),0);assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);}
    assert.equal(await account.count(),0);await page.locator('.header-auth-links').screenshot({path:root+'/../backend/target/email-login-preview/guest.png'});
    await page.evaluate(()=>window.account({sessionStatus:'loading'}));assert.equal(await page.locator('.header-auth-links').count(),0);
    await page.evaluate(()=>window.account({user:{username:'longname'},userDetails:{firstName:'A very long display name that must never overflow the header'},sessionStatus:'authenticated'}));await page.setViewportSize({width:768,height:900});assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);
  }finally{await browser.close();}
});
test('login accepts an email in the Username or Email field and preserves the existing request contract',async()=>{
  const {browser,page}=await fixture();try{
    const input=page.getByPlaceholder('Your username or email',{exact:true});await input.fill('maya@example.test');assert.equal(await input.getAttribute('autocomplete'),'username');
    await page.getByLabel(/^Password/).fill('ExamplePassword!123');await page.getByLabel(/^Password/).blur();await page.getByRole('button',{name:'Sign In',exact:true}).click();
    await page.waitForFunction(()=>!!window.loginPayload);assert.deepEqual(await page.evaluate(()=>window.loginPayload),{username:'maya@example.test',password:'ExamplePassword!123'});
    await page.setViewportSize({width:390,height:900});await page.locator('main').screenshot({path:root+'/../backend/target/email-login-preview/login-mobile.png'});
  }finally{await browser.close();}
});

test('profile editing has no photo controls and retains the personal details form',async()=>{
  const {browser,page}=await fixture('profile');try{
    await page.getByRole('heading',{name:'Edit profile',exact:true}).waitFor();
    assert.equal(await page.locator('input[type=file]').count(),0);
    await page.getByLabel('First name',{exact:true}).fill('Maya updated');
    await page.getByRole('button',{name:'Save changes',exact:true}).click();
    await page.waitForFunction(()=>window.savedProfile?.firstName==='Maya updated');
    assert.equal(await page.evaluate(()=>Object.hasOwn(window.savedProfile,'profileImageUrl')),false);
  }finally{await browser.close();}
});

test('checkout retains guest sign in, search and Help at narrow mobile sizes',async()=>{
  const {browser,page}=await fixture('login','/checkout');try{
    await page.evaluate(()=>window.account({user:null,userDetails:null,sessionStatus:'ready'}));
    for(const width of [320,360,390,768,1024,1440]){
      await page.setViewportSize({width,height:900});
      assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);
      for(const name of ['Sign in','Help'])assert.equal(await page.locator('header').getByRole('link',{name,exact:true}).isVisible(),true);
      assert.equal(await page.locator('header').getByRole('link',{name:'Register',exact:true}).count(),0);
      assert.equal(await page.getByRole('searchbox',{name:'Search products',exact:true}).isVisible(),true);
    }
    await page.setViewportSize({width:320,height:900});await page.locator('header').screenshot({path:root+'/../backend/target/email-login-preview/guest-mobile.png'});
  }finally{await browser.close();}
});

test('storefront search fills the header and submits through the URL with click or Enter',async()=>{
  const {browser,page}=await fixture('login','/products?keyword=desk+lamp');try{
    const search=page.getByRole('searchbox',{name:'Search products'});
    await search.waitFor();
    assert.equal(await search.inputValue(),'desk lamp');
    const submit=page.getByRole('button',{name:'Search',exact:true});
    const form=page.getByRole('search',{name:'Marketplace'});
    const brand=page.getByRole('link',{name:'Mercato home'});
    assert.ok((await form.boundingBox()).width>=500);
    await search.fill('  tshirt  ');await search.press('Enter');
    await page.waitForFunction(()=>document.querySelector('[data-testid=current-route]').textContent==='/products?keyword=tshirt');
    assert.equal(await search.inputValue(),'tshirt');
    await page.setViewportSize({width:320,height:900});
    const [mobileForm,mobileBrand]=await Promise.all([form.boundingBox(),brand.boundingBox()]);
    assert.ok(mobileForm.width>=300);
    assert.ok(mobileForm.y>=mobileBrand.y+mobileBrand.height);
    await search.fill('IKEA & JBL');await submit.click();
    await page.waitForFunction(()=>document.querySelector('[data-testid=current-route]').textContent==='/products?keyword=IKEA+%26+JBL');
    await search.fill('');await search.press('Enter');
    await page.waitForFunction(()=>document.querySelector('[data-testid=current-route]').textContent==='/products');
  }finally{await browser.close();}
});

test('home banners show relevant gallery photos and keep every category browsable',async()=>{
  const {browser,page,errors}=await fixture('home');try{
    const lighting=page.getByRole('link',{name:'Explore Lighting',exact:true});
    const kitchen=page.getByRole('link',{name:'Explore Kitchen & Dining',exact:true});
    await lighting.locator('img').waitFor({state:'attached'});
    assert.equal(await lighting.locator('img').getAttribute('src'),'/images/lamp.jpg');
    assert.equal(await kitchen.locator('img').getAttribute('src'),'/images/plate.jpg');
    assert.equal(await kitchen.getAttribute('href'),'/products?category=Kitchen%20%26%20Dining');
    const accessories=page.getByRole('link',{name:'Explore Accessories',exact:true});
    assert.equal(await accessories.isVisible(),true);
    assert.equal(await accessories.locator('img').count(),0);
    assert.equal(await page.locator('#collections .collection-card').count(),5);
  }catch(error){throw new Error(errors.join('\n') || (await page.locator('body').innerText()).slice(0,1000),{cause:error});}
  finally{await browser.close();}
});
