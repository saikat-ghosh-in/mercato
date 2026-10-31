import { useCallback, useEffect, useState, useSyncExternalStore } from 'react';
import api, { session } from '../backend/api';
import { createResponseFence } from '../utils/fulfillment';

export function useWorkflowEpoch() {
  return useSyncExternalStore(session.subscribe, () => session.getSnapshot().epoch);
}

export default function useWorkflowResource(url) {
  const epoch = useWorkflowEpoch();
  const [fence] = useState(() => createResponseFence(() => session.getSnapshot().epoch));
  const [request] = useState(() => ({ controller:null }));
  const scope = `${epoch}:${url}`;
  const [state, setState] = useState({ scope, data:null, loading:true, error:'', receivedAt:0 });
  const refresh = useCallback(async () => {
    request.controller?.abort();
    const controller = new AbortController();
    request.controller = controller;
    const current = fence.next();
    setState(value => ({ ...(value.scope === scope ? value : {scope, data:null, receivedAt:0}), loading:true, error:'' }));
    try {
      const {data} = await api.get(url, {signal:controller.signal});
      if (current() && !controller.signal.aborted) setState({scope, data, loading:false, error:'', receivedAt:Date.now()});
    } catch (error) {
      if (current() && !controller.signal.aborted) setState(value => ({...value, loading:false, error:error?.response?.data?.message || 'Updates could not load. Your current view and drafts are retained.'}));
    }
  }, [url, fence, request, scope]);
  useEffect(() => { refresh(); return () => { fence.invalidate(); request.controller?.abort(); }; }, [refresh, fence, request]);
  return {...(state.scope === scope ? state : {data:null, loading:true, error:'', receivedAt:0}), refresh, epoch};
}
