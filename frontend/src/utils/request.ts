import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios';
import { store } from '../store';
import { clearSession, setPasswordChangeRequired, setSession } from '../store/authSlice';
import { doneProgress, resetProgress, startProgress } from '../services/progress';
let refreshPromise: Promise<string> | null = null;
let authExpiredHandling = false;
const request = axios.create({ baseURL: import.meta.env.VITE_API_BASE_URL, withCredentials: true, timeout: 12_000 });

/**
 * 会话引导类接口：它们本身不依赖 access token —— refresh / me / touch 靠的是 httpOnly Cookie
 * 里的会话标识，login / register / captcha 更不需要令牌，外部登录入口同理。
 * 因此这些接口返回 401 就代表"会话真的不存在了"，再调一次 refresh 毫无意义：
 * 只会白跑一次请求，还会在 AuthGuard 首次加载时造成 "refresh 401 → 又去 refresh" 的重复调用。
 *
 * 这里必须是**精确列出**的，不能用 /\/auth\// 这种前缀匹配。原因是它同时会命中
 * /auth/onboarding/account/* —— 这两个开户确认接口真正需要访问令牌与静默续期。
 * 临时态的 access token 同样只有 15 分钟，不能被启动接口列表误排除。
 * 新增 /auth/ 下的接口时请一起判断：它到底"靠令牌"还是"靠 Cookie"。
 */
const AUTH_BOOTSTRAP = /\/auth\/(refresh|me|touch|login|logout|register|captcha|external)\b/;

/**
 * 后端状态码约定（与 WebSecurityConfig / GlobalExceptionHandler 保持一致）：
 *   401 —— 未认证：令牌缺失/过期/被篡改，或会话已失效。→ 尝试静默续期，失败才跳登录页。
 *   403 —— 已认证但权限不足。→ 不续期，直接把后端返回的提示展示给用户。
 *   400 —— 业务规则拒绝（参数错误、"用户名已存在"等）。必须与 401 区分开，
 *          否则会把普通业务错误误判成登录失效，把用户莫名踢到登录页。
 */
request.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  // 顶部进度条：与响应拦截器成对。请求即使最终失败（超时/网络错误）也会走 error 分支归还计数，
  // 因此这里不需要 try/catch。延迟显示与并发合并的逻辑见 services/progress.ts。
  startProgress();
  const token = store.getState().auth.accessToken;
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});
request.interceptors.response.use(
  (r) => {
    doneProgress();
    return r;
  },
  async (error: AxiosError) => {
    // ★ 必须放在所有早退分支之前。
    //   这个拦截器有 4 条 return 路径（不重试 / 重试成功 / 重试失败 / 抛异常），
    //   任何一条漏掉 doneProgress() 都会让计数器永久 +1 —— 表现为进度条跑到一半不再消失。
    doneProgress();
    const response = error.response;
    const original = error.config as InternalAxiosRequestConfig & { _retry?: boolean };
    if (response?.status !== 401 || original?._retry || AUTH_BOOTSTRAP.test(original?.url ?? '')) return Promise.reject(error);
    original._retry = true;
    try {
      // 用裸 axios 而不是 request 实例：避免续期请求自身再被这个拦截器处理而形成递归。
      // 也刻意不套 trackProgress：它属于"静默续期"，本来就不该被用户察觉；
      // 真正需要等待的是被重放的那个业务请求，它会自己重新 start/done。
      // 并发请求共享同一个 refreshPromise，保证同一时刻只发一次续期请求。
      refreshPromise ??= axios
        .post<{ data: { accessToken: string; passwordChangeRequired: boolean } }>(`${import.meta.env.VITE_API_BASE_URL}/auth/refresh`, {}, { withCredentials: true })
        .then((r) => {
          store.dispatch(setPasswordChangeRequired(r.data.data.passwordChangeRequired));
          return r.data.data.accessToken;
        })
        .finally(() => {
          refreshPromise = null;
        });
      const token = await refreshPromise;
      const user = store.getState().auth.user;
      const principalType = store.getState().auth.principalType;
      const onboarding = store.getState().auth.onboarding;
      if (principalType) store.dispatch(setSession({ accessToken: token, principalType, user: user ?? undefined, onboarding: onboarding ?? undefined }));
      original.headers.Authorization = `Bearer ${token}`;
      return request(original);
    } catch {
      if (!authExpiredHandling) {
        authExpiredHandling = true;
        // 会话彻底失效、马上整页跳转：把进度条强制收干净。
        // 不清的话，其他在飞请求的计数会残留，下一次进入应用时进度条可能卡在半路。
        resetProgress();
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
