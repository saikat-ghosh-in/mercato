import { proxyPaths } from '../../proxy-paths.mjs';

export function configureIngress(env) {
  if (env.MERCATO_COOKIE_CUTOVER_READY !== 'yes') throw new Error('Cookie authentication cutover is not ready. Complete DEPLOY.md and set MERCATO_COOKIE_CUTOVER_READY=yes in the target Netlify build environment.');
  const origin = new URL(env.MERCATO_ORIGIN || 'invalid:');
  if (origin.protocol !== 'https:' || !origin.hostname || origin.username || origin.password || origin.port || origin.pathname !== '/' || origin.search || origin.hash || /^[0-9.]+$/.test(origin.hostname) || origin.hostname.includes(':') || origin.hostname.endsWith('.netlify.app')) throw new Error('MERCATO_ORIGIN must be the dedicated HTTPS Caddy hostname');
  if (!env.NETLIFY_PROXY_SIGNING_KEY || env.NETLIFY_PROXY_SIGNING_KEY.length < 32) throw new Error('A runtime-scoped signed-proxy key is required');
  const mode = env.MERCATO_CSP_MODE || 'enforce';
  if (!['enforce','report-only'].includes(mode)) throw new Error('Invalid CSP mode');
  const hsts = env.MERCATO_HSTS_SECONDS || '300';
  if (!/^\d+$/.test(hsts) || Number(hsts)>31536000) throw new Error('Invalid HSTS duration');
  const policy = "default-src 'self'; base-uri 'self'; object-src 'none'; frame-ancestors 'none'; form-action 'self'; script-src 'self' https://sdk.cashfree.com; style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; font-src 'self' https://fonts.gstatic.com; img-src 'self' data: blob: https://images.unsplash.com; connect-src 'self' https://sdk.cashfree.com https://api.cashfree.com https://sandbox.cashfree.com; frame-src https://sdk.cashfree.com https://api.cashfree.com https://sandbox.cashfree.com https://payments.cashfree.com https://payments-test.cashfree.com; upgrade-insecure-requests";
  const values = {
    [mode==='enforce'?'Content-Security-Policy':'Content-Security-Policy-Report-Only']: policy,
    'Strict-Transport-Security': `max-age=${hsts}`,
    'X-Content-Type-Options':'nosniff','X-Frame-Options':'DENY','Referrer-Policy':'strict-origin-when-cross-origin',
    'Permissions-Policy':'camera=(), microphone=(), geolocation=(), payment=(self "https://sdk.cashfree.com")'
  };
  return {
    redirects:[...proxyPaths.map(from=>({from,to:origin.origin+from.replace('*',':splat'),status:200,force:true,signed:'NETLIFY_PROXY_SIGNING_KEY'})),{from:'/*',to:'/index.html',status:200}],
    headers:[{for:'/*',values}]
  };
}

