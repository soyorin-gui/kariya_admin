import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios';
import { store } from '../store';
import { clearSession, setSession } from '../store/authSlice';
let refreshPromise: Promise<string> | null = null;
let authExpiredHandling = false;
const request = axios.create({ baseURL: import.meta.env.VITE_API_BASE_URL, withCredentials: true, timeout: 12_000 });
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
    if (response?.status !== 401 || original?._retry) return Promise.reject(error);
    original._retry = true;
    try {
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
