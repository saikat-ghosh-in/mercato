import axios from "axios";
import { createSessionController, isPrivateRequest, SessionChangedError } from "./sessionState.js";
import { normalizeOwnedImages } from "../utils/ownedImages.js";

const options = {
    baseURL: "/api",
    timeout: 20000,
    withCredentials: true,
    paramsSerializer: params => Object.entries(params).map(([key, val]) => `${encodeURIComponent(key)}=${encodeURIComponent(val)}`).join("&"),
};
const bootstrapTransport = axios.create(options);
let storage;
try { storage = globalThis.localStorage; } catch { /* Cookie sessions work without browser storage. */ }
const eventKey = "mercato:session-changed";
let channel;
try { if (typeof window !== "undefined" && typeof BroadcastChannel !== "undefined") channel = new BroadcastChannel(eventKey); } catch { /* Storage events provide a fallback. */ }
export const session = createSessionController({
    transport: config => bootstrapTransport.request(config).then(response => normalizeOwnedImages(response.data)),
    storage,
    publish: () => {
        const event = { type: "identity-changed", id: globalThis.crypto?.randomUUID?.() || String(Date.now()) };
        try { channel?.postMessage(event); } catch { /* A storage event can still notify other tabs. */ }
        try { storage?.setItem(eventKey, JSON.stringify(event)); } catch { /* BroadcastChannel remains available when storage is denied. */ }
    },
});
let lastEvent;
const receive = event => {
    if (event?.type !== "identity-changed" || event.id === lastEvent) return;
    lastEvent = event.id;
    session.refresh().catch(() => {});
};
if (channel) channel.onmessage = event => receive(event.data);
if (typeof window !== "undefined") window.addEventListener("storage", event => {
    if (event.key !== eventKey) return;
    try { receive(JSON.parse(event.newValue)); } catch { /* Ignore malformed cross-tab messages. */ }
});

const api = axios.create(options);
api.interceptors.request.use(config => session.prepare(config));
api.interceptors.response.use(response => {
    session.assertCurrent(response.config);
    session.recordActivity();
    response.data = normalizeOwnedImages(response.data);
    return response;
}, error => {
    if (error.config?.sessionEpoch !== undefined) {
        try { session.assertCurrent(error.config); } catch { return Promise.reject(new SessionChangedError()); }
    }
    const status = error.response?.status;
    const mutation = !["get", "head", "options"].includes((error.config?.method || "get").toLowerCase());
    if ((status === 401 && isPrivateRequest(error.config?.url)) ||
        (status === 403 && mutation) ||
        (status === 409 && error.response?.data?.code === "SESSION_CONTEXT_CHANGED")) {
        session.refresh({ broadcast: true }).catch(() => {});
        return Promise.reject(new SessionChangedError());
    }
    return Promise.reject(error);
});
export default api;
