import request from '../utils/request';
import type { AuthProfile, LoginRequest, LoginResult } from '../types/auth';
import type { Result } from '../types/common';
export const login = (payload: LoginRequest) => request.post<Result<LoginResult>>('/auth/login', payload).then((r) => r.data);
export const refresh = () => request.post<Result<{ accessToken: string }>>('/auth/refresh').then((r) => r.data.data.accessToken);
export const fetchMe = () => request.get<Result<AuthProfile>>('/auth/me').then((r) => r.data.data);
export const touch = () => request.post('/auth/touch');
export const logout = () => request.post('/auth/logout');
