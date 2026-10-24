// Server-owned HttpOnly cookies authenticate requests. Never restore bearer
// credentials from browser persistence.
import { session } from "../backend/api.js";
export const isAuthenticated = () => session.getSnapshot().authenticated;
