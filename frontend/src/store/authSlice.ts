import { createSlice, type PayloadAction } from '@reduxjs/toolkit';
import type { CurrentUser, OnboardingProfile, MenuRoute, MenuRouteSummary } from '../types/auth';
interface AuthState {
  accessToken: string | null;
  principalType: 'MEMBER' | 'ONBOARDING' | null;
  user: CurrentUser | null;
  onboarding: OnboardingProfile | null;
  permissions: string[];
  menus: MenuRoute[];
  routes: MenuRouteSummary[];
  profileReady: boolean;
}
const initialState: AuthState = { accessToken: null, principalType: null, user: null, onboarding: null, permissions: [], menus: [], routes: [], profileReady: false };
const slice = createSlice({
  name: 'auth',
  initialState,
  reducers: {
    setSession: (s, a: PayloadAction<{ accessToken: string; principalType: 'MEMBER' | 'ONBOARDING'; user?: CurrentUser; onboarding?: OnboardingProfile }>) => {
      s.accessToken = a.payload.accessToken;
      s.principalType = a.payload.principalType;
      s.user = a.payload.user ?? null;
      s.onboarding = a.payload.onboarding ?? null;
    },
    setAccessToken: (s, a: PayloadAction<string>) => {
      s.accessToken = a.payload;
    },
    setProfile: (s, a: PayloadAction<{ permissions: string[]; menus: MenuRoute[]; routes: MenuRouteSummary[] }>) => {
      s.permissions = a.payload.permissions;
      s.menus = a.payload.menus;
      s.routes = a.payload.routes ?? [];
      s.profileReady = true;
    },
    setPasswordChangeRequired: (s, a: PayloadAction<boolean>) => {
      if (s.user) s.user.passwordChangeRequired = a.payload;
    },
    clearSession: () => initialState,
  },
});
export const { setSession, setAccessToken, setProfile, setPasswordChangeRequired, clearSession } = slice.actions;
export default slice.reducer;
