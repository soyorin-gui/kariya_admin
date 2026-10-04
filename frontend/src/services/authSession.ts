import axios from 'axios';
import { store } from '../store';
import { clearSession, setAccessToken, setPasswordChangeRequired } from '../store/authSlice';

const API_BASE = (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/$/, '');
let refreshPromise: Promise<string> | null = null;
let expirationHandled = false;

interface RefreshEnvelope {
  data: { accessToken: string; passwordChangeRequired: boolean };
}

/** 全应用唯一的静默续期入口；并发 401 共享同一个请求。 */
export function refreshAccessTokenOnce(): Promise<string> {
  refreshPromise ??= axios
    .post<RefreshEnvelope>(`${API_BASE}/auth/refresh`, {}, { withCredentials: true })
    .then((response) => {
      const result = response.data.data;
      store.dispatch(setAccessToken(result.accessToken));
      store.dispatch(setPasswordChangeRequired(result.passwordChangeRequired));
      return result.accessToken;
    })
    .finally(() => {
      refreshPromise = null;
    });
  return refreshPromise;
}

export function expireSession(redirectPath = window.location.pathname) {
  if (expirationHandled) return;
  expirationHandled = true;
  store.dispatch(clearSession());
  window.location.assign(`/login?redirect=${encodeURIComponent(redirectPath)}`);
  window.setTimeout(() => {
    expirationHandled = false;
  }, 500);
}

/** 原生 fetch 的认证版本，供 SSE 等不能走 Axios 的请求使用。仅在响应体开始读取前重试。 */
export async function authenticatedFetch(path: string, init: RequestInit = {}): Promise<Response> {
  const execute = (token: string | null) => {
    const headers = new Headers(init.headers);
    if (token) headers.set('Authorization', `Bearer ${token}`);
    return fetch(`${API_BASE}${path}`, { ...init, headers, credentials: 'include' });
  };

  let response = await execute(store.getState().auth.accessToken);
  if (response.status !== 401) return response;
  try {
    response = await execute(await refreshAccessTokenOnce());
    if (response.status === 401) expireSession();
    return response;
  } catch (error) {
    expireSession();
    throw error;
  }
}
