import { proxyPaths } from '../proxy-paths.mjs';

export default async function trustedIngress(request, context) {
  if (!context.ip || !/^[0-9a-fA-F:.]+$/.test(context.ip)) throw new Error('Missing connection IP');
  const headers = new Headers(request.headers);
  for (const name of [...headers.keys()]) {
    if (name === 'forwarded' || name.startsWith('x-forwarded-') || name.startsWith('x-mercato-') || name === 'x-nf-sign' || name === 'x-nf-client-connection-ip') headers.delete(name);
  }
  headers.set('x-mercato-client-ip', context.ip);
  headers.set('x-mercato-ingress', 'edge-v1');
  // Clone the unconsumed stream. Never parse/reconstruct Cashfree's signed body.
  const response = await context.next(new Request(request, { headers }));
  if (!new URL(request.url).pathname.startsWith('/api')) return response;
  const outgoing = new Headers(response.headers);
  outgoing.set('Cache-Control', 'no-store');
  outgoing.set('CDN-Cache-Control', 'no-store');
  outgoing.set('Netlify-CDN-Cache-Control', 'no-store');
  return new Response(response.body, { status: response.status, statusText: response.statusText, headers: outgoing });
}

export const config = { path: proxyPaths, onError: 'fail' };
