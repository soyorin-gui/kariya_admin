import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios';
import { store } from '../store';
import { clearSession, setSession } from '../store/authSlice';
let refreshPromise: Promise<string> | null = null;
let authExpiredHandling = false;
const request = axios.create({ baseURL: import.meta.env.VITE_API_BASE_URL, withCredentials: true, timeout: 12_000 });

/**
 * 会话引导类接口：它们本身不依赖 access token —— refresh / me / touch 靠的是 httpOnly Cookie
 * 里的会话标识，login / logout 更不需要令牌。
 * 因此这些接口返回 401 就代表"会话真的不存在了"，再调一次 refresh 毫无意义：
 * 只会白跑一次请求，还会在 AuthGuard 首次加载时造成 "refresh 401 → 又去 refresh" 的重复调用。
 * 这里显式排除，续期逻辑只服务于真正携带 Bearer 令牌的业务接口。
 */
const AUTH_BOOTSTRAP = /\/auth\//;

/**
 * 后端状态码约定（与 WebSecurityConfig / GlobalExceptionHandler 保持一致）：
 *   401 —— 未认证：令牌缺失/过期/被篡改，或会话已失效。→ 尝试静默续期，失败才跳登录页。
 *   403 —— 已认证但权限不足。→ 不续期，直接把后端返回的提示展示给用户。
 *   400 —— 业务规则拒绝（参数错误、"用户名已存在"等）。必须与 401 区分开，
 *          否则会把普通业务错误误判成登录失效，把用户莫名踢到登录页。
 */
request.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = store.getState().auth.accessToken;
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});
request.interceptors.response.use(
  (r) => r,
  async (error: AxiosError) => {
    const response = error.response;
    const original = error.config as InternalAxiosRequestConfig & { _retry?: boolean };
    if (response?.status !== 401 || original?._retry || AUTH_BOOTSTRAP.test(original?.url ?? '')) return Promise.reject(error);
    original._retry = true;
    try {
      // 用裸 axios 而不是 request 实例：避免续期请求自身再被这个拦截器处理而形成递归。
      // 并发请求共享同一个 refreshPromise，保证同一时刻只发一次续期请求。
      refreshPromise ??= axios
        .post<{ data: { accessToken: string } }>(`${import.meta.env.VITE_API_BASE_URL}/auth/refresh`, {}, { withCredentials: true })
        .then((r) => r.data.data.accessToken)
        .finally(() => {
          refreshPromise = null;
        });
      const token = await refreshPromise;
      const user = store.getState().auth.user;
      if (user) store.dispatch(setSession({ accessToken: token, user }));
      original.headers.Authorization = `Bearer ${token}`;
      return request(original);
    } catch {
      if (!authExpiredHandling) {
        authExpiredHandling = true;
        store.dispatch(clearSession());
        window.location.assign(`/login?redirect=${encodeURIComponent(location.pathname)}`);
        setTimeout(() => {
          authExpiredHandling = false;
        }, 500);
      }
      return Promise.reject(error);
    }
  },
);
export default request;
