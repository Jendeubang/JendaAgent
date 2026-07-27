export type AgentAuthSession = {
  accessToken: string;
  tokenType: string;
  expiresInSeconds: number;
  refreshExpiresInSeconds: number;
  userId: string;
  username: string;
};

const sessionKey = "jenda-agent-auth-session";
const userScopedKeys = ["jenda-agent-current-session-id", "jenda-image-studio-session"];
let refreshInFlight: Promise<boolean> | undefined;

export function readAgentAuthSession(): AgentAuthSession | undefined {
  if (typeof window === "undefined") return undefined;
  try { const value = window.localStorage.getItem(sessionKey); return value ? JSON.parse(value) as AgentAuthSession : undefined; } catch { return undefined; }
}
export function saveAgentAuthSession(session: AgentAuthSession) { window.localStorage.setItem(sessionKey, JSON.stringify(session)); }
export function clearAgentAuthSession() { if (typeof window === "undefined") return; window.localStorage.removeItem(sessionKey); userScopedKeys.forEach((key) => window.localStorage.removeItem(key)); }
export function agentHeaders(headers?: HeadersInit) { const result = new Headers(headers); const session = readAgentAuthSession(); if (session?.accessToken) result.set("Authorization", `Bearer ${session.accessToken}`); return result; }
function apiBaseUrl() { return process.env.NEXT_PUBLIC_AGENT_API_BASE_URL ?? "http://127.0.0.1:8080"; }

async function refreshSession(): Promise<boolean> {
  if (refreshInFlight) return refreshInFlight;
  refreshInFlight = (async () => {
    try {
      const response = await fetch(`${apiBaseUrl()}/api/v1/auth/refresh`, { method: "POST", credentials: "include" });
      if (!response.ok) return false;
      saveAgentAuthSession(await response.json() as AgentAuthSession);
      return true;
    } catch { return false; } finally { refreshInFlight = undefined; }
  })();
  return refreshInFlight;
}
function redirectForExpiredToken() { if (typeof window === "undefined" || window.location.pathname === "/login") return; clearAgentAuthSession(); window.location.replace("/login?reason=expired"); }
/** Access token stays in memory/local storage; rotating Refresh Token stays only in an HttpOnly cookie. */
export async function agentFetch(input: RequestInfo | URL, init: RequestInit = {}) {
  let response = await fetch(input, { ...init, credentials: "include", headers: agentHeaders(init.headers) });
  if (response.status !== 401) return response;
  if (await refreshSession()) { response = await fetch(input, { ...init, credentials: "include", headers: agentHeaders(init.headers) }); if (response.status !== 401) return response; }
  redirectForExpiredToken();
  return response;
}
export async function logoutAgentSession() {
  try { await fetch(`${apiBaseUrl()}/api/v1/auth/logout`, { method: "POST", credentials: "include", headers: agentHeaders() }); }
  finally { clearAgentAuthSession(); }
}