import { proxyPaths } from '../../proxy-paths.mjs';
import { configureIngress } from './config.mjs';

export function onPreBuild({netlifyConfig,utils}) {
  try {
    const config=configureIngress(process.env);
    const declared=netlifyConfig.redirects || [];
    for(const path of proxyPaths)if(!declared.some(r=>r.from===path&&r.signed))throw new Error('Signed proxy declaration is missing');
    netlifyConfig.redirects=config.redirects.map(rule=>rule.signed?{...rule,signed:declared.find(r=>r.from===rule.from).signed}:rule);
    netlifyConfig.headers=config.headers;
  }
  catch(error){utils.build.failBuild(error.message);}
}
