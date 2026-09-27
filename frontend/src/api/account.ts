import request from '../utils/request';
import type { Result } from '../types/common';

export interface ExternalIdentity {
  providerKey: string;
  displayName: string;
  email: string;
  createdTime: string;
}
export interface ContactProfile {
  username: string;
  realName: string;
  phone?: string;
  email?: string;
}
export interface LoginSessionView {
  id: string;
  current: boolean;
  createdTime?: string;
  lastActiveTime?: string;
  loginIp?: string;
  userAgent?: string;
  rememberMe: boolean;
  authMethod: string;
  providerKey?: string;
}

export const getExternalIdentities = () => request.get<Result<ExternalIdentity[]>>('/account/identities').then((r) => r.data.data);
export const unbindExternalIdentity = (provider: string) => request.delete(`/account/identities/${encodeURIComponent(provider)}`);
export const startExternalBinding = (provider: string) =>
  request.post<Result<{ authorizationUrl: string }>>(`/account/identities/${encodeURIComponent(provider)}/start`).then((r) => r.data.data.authorizationUrl);
export const getContactProfile = () => request.get<Result<ContactProfile>>('/account/profile').then((r) => r.data.data);
export const updateContactProfile = (data: { phone?: string; email?: string }) => request.put<Result<ContactProfile>>('/account/profile', data).then((r) => r.data);
export const getMySessions = () => request.get<Result<LoginSessionView[]>>('/account/sessions').then((r) => r.data.data);
export const removeMySession = (id: string) => request.delete(`/account/sessions/${encodeURIComponent(id)}`);
export const removeMyOtherSessions = () => request.delete('/account/sessions/others');
