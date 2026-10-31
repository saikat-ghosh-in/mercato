import test from 'node:test';
import assert from 'node:assert/strict';
import {build} from 'esbuild';
import {readFile,readdir,mkdir} from 'node:fs/promises';
import {fileURLToPath,pathToFileURL} from 'node:url';

const root=fileURLToPath(new URL('..',import.meta.url));
async function fixture(view='login'){
  const bundle=await build({stdin:{contents:`
    import React from 'react';import {createRoot} from 'react-dom/client';import {MemoryRouter} from 'react-router-dom';
    import {Provider} from 'react-redux';import {configureStore} from '@reduxjs/toolkit';
    import Header from './src/components/Header.jsx';import Login from './src/components/auth/LogIn.jsx';
    import ProfileEdit from './src/components/user/ProfileEdit.jsx';
    const initial={user:{username:'maya'},userDetails:{userId:7,firstName:'Maya',lastName:'Shah',username:'maya',email:'maya@example.test',emailVerified:true,profileImageUrl:'placeholder',roles:['ROLE_USER']},sessionStatus:'authenticated'};
    window.store=configureStore({reducer:{auth:(s=initial,a)=>a.type==='set'?{...s,...a.payload}:s,categories:()=>({categories:[]}),cart:()=>({cartQty:0,cartItems:[]})}});
    window.account=value=>window.store.dispatch({type:'set',payload:value});
    window.epoch=1;window.uploads=0;
    createRoot(document.getElementById('root')).render(<Provider store={window.store}><MemoryRouter><div className="app-page"><Header/><main id="main-content">${view==='profile'?'<ProfileEdit/>':'<Login/>'}</main></div></MemoryRouter></Provider>);
  `,resolveDir:root,loader:'jsx'},bundle:true,write:false,jsx:'automatic',plugins:[{name:'actions',setup(build){
    build.onResolve({filter:/reduxStore\/actions\/authActions(?:\.js)?$/},()=>({path:'auth',namespace:'fixture'}));
    build.onLoad({filter:/.*/,namespace:'fixture'},()=>({contents:`export const authenticateUser=data=>async()=>{window.loginPayload=data};export const logOutUser=()=>()=>window.account({user:null,userDetails:null,sessionStatus:'anonymous'});export const fetchUserDetails=()=>async()=>window.account({userDetails:{...window.store.getState().auth.userDetails,...window.savedProfile}});`,loader:'js'}));
    build.onResolve({filter:/backend\/api(?:\.js)?$/},()=>({path:'api',namespace:'fixture-api'}));
    build.onLoad({filter:/.*/,namespace:'fixture-api'},()=>({contents:`export const session={getSnapshot:()=>({epoch:window.epoch})};export default {post:async(path,body)=>{window.uploads++;if(!(body instanceof FormData)||!body.get('file'))throw Error('Expected an uploaded file');if(window.delayUpload)await new Promise(resolve=>window.finishUpload=resolve);if(window.failUpload)throw Error('Network unavailable');return {data:{imageUrl:'/images/profile.png'}}},put:async(path,data)=>{window.savedProfile=data;return {data}}};`,loader:'js'}));
    build.onResolve({filter:/utils\/toastUtils(?:\.js)?$/},()=>({path:'toast',namespace:'fixture-toast'}));
    build.onLoad({filter:/.*/,namespace:'fixture-toast'},()=>({contents:'export default {success(){},error(){}};',loader:'js'}));
  }}]});
  const {chromium}=await import(pathToFileURL(process.env.PLAYWRIGHT_MODULE).href);const browser=await chromium.launch({channel:'chrome',headless:true});
  try{
    const page=await browser.newPage({viewport:{width:1440,height:900}});page.setDefaultTimeout(5000);
    await page.route('http://localhost:4179/**',r=>r.fulfill({contentType:'text/html',body:'<div id="root"></div>'}));await page.goto('http://localhost:4179/');
    await page.route('http://localhost:4179/images/profile.png',r=>r.fulfill({contentType:'image/png',body:Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aH1sAAAAASUVORK5CYII=','base64')}));
    for(const file of (await readdir(root+'/dist/assets')).filter(n=>n.endsWith('.css'))){let css=await readFile(root+'/dist/assets/'+file,'utf8');await page.addStyleTag({content:css.replace(/@import\s*(?:url\([^)]*\)|"[^"]*"|'[^']*')\s*;/g,'')});}
    await page.addScriptTag({content:bundle.outputFiles[0].text});return {browser,page};
  }catch(error){await browser.close();throw error;}
}

test('header clearly distinguishes signed in, guest and initialization states at mobile widths',async()=>{
  const {browser,page}=await fixture();try{
    const account=page.getByRole('button',{name:'Account menu',exact:true});await page.getByText('Hi, Maya',{exact:true}).waitFor();assert.match(await account.getAttribute('title'),/Signed in as Maya/);
    await mkdir(root+'/../backend/target/email-login-preview',{recursive:true});await account.screenshot({path:root+'/../backend/target/email-login-preview/signed-in.png'});
    for(const width of [320,390,768,1440]){await page.setViewportSize({width,height:900});assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);assert.equal(await page.locator('.account-avatar').isVisible(),true);}
    await page.setViewportSize({width:390,height:900});await account.screenshot({path:root+'/../backend/target/email-login-preview/mobile-signed-in.png'});
    await page.evaluate(()=>window.account({user:null,userDetails:null,sessionStatus:'anonymous'}));await page.setViewportSize({width:1440,height:900});await page.getByText('Sign in',{exact:true}).waitFor();assert.equal(await page.locator('.account-avatar').count(),0);await account.screenshot({path:root+'/../backend/target/email-login-preview/guest.png'});
    await page.evaluate(()=>window.account({sessionStatus:'loading'}));await page.getByText('Account',{exact:true}).waitFor();
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

const photo={name:'photo.png',mimeType:'image/png',buffer:Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aH1sAAAAASUVORK5CYII=','base64')};
test('profile photo uploads, previews, saves in the account header and can be removed',async()=>{
  const {browser,page}=await fixture('profile');try{
    await page.getByRole('heading',{name:'Profile photo',exact:true}).waitFor();
    for(const width of [320,390,768,1440]){await page.setViewportSize({width,height:1000});assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);}
    await page.locator('.profile-photo-section').screenshot({path:root+'/../backend/target/email-login-preview/profile-photo-control.png'});
    await page.setViewportSize({width:390,height:1000});await page.screenshot({path:root+'/../backend/target/email-login-preview/profile-mobile.png',fullPage:true});
    await page.getByLabel('Upload profile photo',{exact:true}).setInputFiles(photo);
    await page.locator('.profile-photo-preview img').waitFor();assert.equal(await page.locator('.profile-photo-preview img').getAttribute('src'),'/images/profile.png');
    await page.getByRole('button',{name:'Save changes',exact:true}).click();await page.waitForFunction(()=>window.savedProfile?.profileImageUrl==='/images/profile.png');
    await page.locator('.account-avatar img').waitFor();
    await page.getByRole('button',{name:'Remove photo',exact:true}).click();await page.getByRole('button',{name:'Save changes',exact:true}).click();
    await page.waitForFunction(()=>window.savedProfile?.profileImageUrl==='');assert.equal(await page.locator('.account-avatar img').count(),0);
    await page.setViewportSize({width:1440,height:1000});await page.screenshot({path:root+'/../backend/target/email-login-preview/profile-desktop.png',fullPage:true});
  }finally{await browser.close();}
});

test('invalid and failed uploads preserve edits, and an old account upload cannot replace a new profile',async()=>{
  const {browser,page}=await fixture('profile');try{
    const input=page.getByLabel('Upload profile photo',{exact:true});await input.setInputFiles({name:'document.pdf',mimeType:'application/pdf',buffer:Buffer.from('invalid')});
    await page.getByRole('alert').filter({hasText:'Choose a JPG'}).waitFor();assert.equal(await page.evaluate(()=>window.uploads),0);
    await page.getByLabel('First name',{exact:true}).fill('Maya edited');await page.evaluate(()=>window.failUpload=true);await input.setInputFiles(photo);
    await page.getByRole('alert').filter({hasText:'could not be uploaded'}).waitFor();assert.equal(await page.getByLabel('First name',{exact:true}).inputValue(),'Maya edited');
    assert.equal(await page.locator('.profile-photo-preview img').count(),0);
    await page.evaluate(()=>{window.failUpload=false;window.delayUpload=true});await input.setInputFiles(photo);await page.waitForFunction(()=>!!window.finishUpload);
    assert.equal(await page.getByRole('button',{name:'Save changes',exact:true}).isDisabled(),true);
    await page.evaluate(()=>{window.epoch++;window.account({user:{username:'other'},userDetails:{userId:8,username:'other',firstName:'Other',roles:['ROLE_USER']}})});
    await page.evaluate(()=>window.finishUpload());await page.getByLabel('First name',{exact:true}).fill('Other edited');
    assert.equal(await page.locator('.profile-photo-preview img').count(),0);assert.equal(await page.getByRole('button',{name:'Save changes',exact:true}).isEnabled(),true);
    await page.evaluate(()=>window.account({userDetails:{...window.store.getState().auth.userDetails,profileImageUrl:'/images/missing.png'}}));
    await page.waitForFunction(()=>!document.querySelector('.account-avatar img'));
  }finally{await browser.close();}
});
