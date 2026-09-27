import request from '../utils/request';
import type { AuthProfile, CaptchaChallenge, ExternalProvider, LoginRequest, LoginResult, RegistrationRequest } from '../types/auth';
import type { Result } from '../types/common';
export const login = (payload: LoginRequest) => request.post<Result<LoginResult>>('/auth/login', payload).then((r) => r.data);
export const register = (payload: RegistrationRequest) => request.post<Result<LoginResult>>('/auth/register', payload).then((r) => r.data);
/**
 * 取一张新的图形验证码。
 * 返回的是 JSON 里的 base64 图片（data:image/png;base64,...），不是独立的图片地址：
 * 少一个匿名接口，滥用控制只需要守这一个入口。
 */
export const getCaptcha = () => request.get<Result<CaptchaChallenge>>('/auth/captcha').then((r) => r.data.data);
export const getExternalProviders = () => request.get<Result<ExternalProvider[]>>('/auth/external/providers').then((r) => r.data.data);
export interface RefreshResult {
  accessToken: string;
  passwordChangeRequired: boolean;
}
export const refresh = () => request.post<Result<RefreshResult>>('/auth/refresh').then((r) => r.data.data);
export const fetchMe = () => request.get<Result<AuthProfile>>('/auth/me').then((r) => r.data.data);
export const touch = () => request.post('/auth/touch');
export const logout = () => request.post('/auth/logout');
export const createOnboardingAccount = (payload: { username: string; realName: string; phone?: string; email?: string; password: string; confirmPassword: string }) =>
  request.post<Result<{ accessToken: string }>>('/auth/onboarding/account/create', payload).then((r) => r.data);
export const bindOnboardingAccount = (payload: { username: string; password: string }) => request.post<Result<{ accessToken: string }>>('/auth/onboarding/account/bind', payload).then((r) => r.data);
