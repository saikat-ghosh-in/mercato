import test from 'node:test';
import assert from 'node:assert/strict';
import {build} from 'esbuild';
import {readFile,readdir,mkdir} from 'node:fs/promises';
import {fileURLToPath,pathToFileURL} from 'node:url';

const root=fileURLToPath(new URL('..',import.meta.url));
async function fixture(view='login',path='/'){
  const bundle=await build({stdin:{contents:`
    import React from 'react';import {createRoot} from 'react-dom/client';import {MemoryRouter} from 'react-router-dom';
    import {Provider} from 'react-redux';import {configureStore} from '@reduxjs/toolkit';
    import Header from './src/components/Header.jsx';import Login from './src/components/auth/LogIn.jsx';
    import ProfileEdit from './src/components/user/ProfileEdit.jsx';
    const initial={user:{username:'maya'},userDetails:{userId:7,firstName:'Maya',lastName:'Shah',username:'maya',email:'maya@example.test',emailVerified:true,profileImageUrl:'placeholder',roles:['ROLE_USER']},sessionStatus:'authenticated'};
    window.store=configureStore({reducer:{auth:(s=initial,a)=>a.type==='set'?{...s,...a.payload}:s,categories:()=>({categories:[]}),cart:()=>({cartQty:0,cartItems:[]})}});
    window.account=value=>window.store.dispatch({type:'set',payload:value});
    createRoot(document.getElementById('root')).render(<Provider store={window.store}><MemoryRouter initialEntries={[${JSON.stringify(path)}]}><div className="app-page"><Header/><main id="main-content">${view==='profile'?'<ProfileEdit/>':'<Login/>'}</main></div></MemoryRouter></Provider>);
  `,resolveDir:root,loader:'jsx'},bundle:true,write:false,jsx:'automatic',plugins:[{name:'actions',setup(build){
    build.onResolve({filter:/reduxStore\/actions\/authActions(?:\.js)?$/},()=>({path:'auth',namespace:'fixture'}));
    build.onLoad({filter:/.*/,namespace:'fixture'},()=>({contents:`export const authenticateUser=data=>async()=>{window.loginPayload=data};export const logOutUser=()=>()=>window.account({user:null,userDetails:null,sessionStatus:'anonymous'});export const fetchUserDetails=()=>async()=>window.account({userDetails:{...window.store.getState().auth.userDetails,...window.savedProfile}});`,loader:'js'}));
    build.onResolve({filter:/backend\/api(?:\.js)?$/},()=>({path:'api',namespace:'fixture-api'}));
    build.onLoad({filter:/.*/,namespace:'fixture-api'},()=>({contents:`export default {put:async(path,data)=>{window.savedProfile=data;return {data}}};`,loader:'js'}));
    build.onResolve({filter:/utils\/toastUtils(?:\.js)?$/},()=>({path:'toast',namespace:'fixture-toast'}));
    build.onLoad({filter:/.*/,namespace:'fixture-toast'},()=>({contents:'export default {success(){},error(){}};',loader:'js'}));
  }}]});
  const {chromium}=await import(pathToFileURL(process.env.PLAYWRIGHT_MODULE).href);const browser=await chromium.launch({channel:'chrome',headless:true});
  try{
    const page=await browser.newPage({viewport:{width:1440,height:900}});page.setDefaultTimeout(5000);
    await page.route('http://localhost:4179/**',r=>r.fulfill({contentType:'text/html',body:'<div id="root"></div>'}));await page.goto('http://localhost:4179/');
    for(const file of (await readdir(root+'/dist/assets')).filter(n=>n.endsWith('.css'))){let css=await readFile(root+'/dist/assets/'+file,'utf8');await page.addStyleTag({content:css.replace(/@import\s*(?:url\([^)]*\)|"[^"]*"|'[^']*')\s*;/g,'')});}
    await page.addScriptTag({content:bundle.outputFiles[0].text});return {browser,page};
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
    for(const width of [320,390,768,1440]){await page.setViewportSize({width,height:900});assert.equal(await page.locator('header').getByRole('link',{name:'Sign in',exact:true}).isVisible(),true);assert.equal(await page.locator('header').getByRole('link',{name:'Register',exact:true}).isVisible(),true);assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);}
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

test('checkout retains guest sign in/register, search and Help at narrow mobile sizes',async()=>{
  const {browser,page}=await fixture('login','/checkout');try{
    await page.evaluate(()=>window.account({user:null,userDetails:null,sessionStatus:'ready'}));
    for(const width of [320,360,390,768,1024,1440]){
      await page.setViewportSize({width,height:900});
      assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);
      for(const name of ['Sign in','Register','Help'])assert.equal(await page.locator('header').getByRole('link',{name,exact:true}).isVisible(),true);
      assert.equal(await page.getByRole('button',{name:'Open search',exact:true}).isVisible(),true);
    }
    await page.setViewportSize({width:320,height:900});await page.locator('header').screenshot({path:root+'/../backend/target/email-login-preview/guest-mobile.png'});
  }finally{await browser.close();}
});
