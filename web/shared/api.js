// Shared fetch wrapper for student/ and admin/.
// Owner: web lead. Rules: add token, refresh once on 401, one error shape.
export const API_BASE = window.EVENTROUTE_API_BASE || "http://localhost:8000/api/v1";
export const USE_MOCK = window.EVENTROUTE_USE_MOCK ?? true; // flip to false when endpoints are live

const store = {
  get access() { return localStorage.getItem("er_access"); },
  get refresh() { return localStorage.getItem("er_refresh"); },
  set(a, r) { localStorage.setItem("er_access", a); localStorage.setItem("er_refresh", r); },
  clear() { localStorage.removeItem("er_access"); localStorage.removeItem("er_refresh"); },
};

async function refreshToken() {
  if (!store.refresh) return false;
  const res = await fetch(`${API_BASE}/auth/refresh`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ refresh_token: store.refresh }),
  });
  if (!res.ok) { store.clear(); return false; }
  const data = await res.json();
  store.set(data.access_token, data.refresh_token);
  return true;
}

export async function api(path, { method = "GET", body, retry = true } = {}) {
  const headers = { "Content-Type": "application/json" };
  if (store.access) headers.Authorization = `Bearer ${store.access}`;
  const res = await fetch(`${API_BASE}${path}`, {
    method, headers, body: body ? JSON.stringify(body) : undefined,
  });
  if (res.status === 401 && retry && (await refreshToken())) {
    return api(path, { method, body, retry: false });
  }
  if (!res.ok) {
    const err = await res.json().catch(() => ({ code: "UNKNOWN", message: res.statusText }));
    throw err; // { code, message, details? }
  }
  return res.status === 204 ? null : res.json();
}

export const auth = store;

// Always escape user text before putting it in innerHTML.
export function esc(s) {
  return String(s ?? "").replace(/[&<>"']/g, c => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
}
