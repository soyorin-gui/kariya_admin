import { createSlice, type PayloadAction } from '@reduxjs/toolkit';
import type { CurrentUser, MenuRoute, MenuRouteSummary } from '../types/auth';
interface AuthState {
  accessToken: string | null;
  user: CurrentUser | null;
  permissions: string[];
  menus: MenuRoute[];
  routes: MenuRouteSummary[];
}
const initialState: AuthState = { accessToken: null, user: null, permissions: [], menus: [], routes: [] };
const slice = createSlice({
  name: 'auth',
  initialState,
  reducers: {
    setSession: (s, a: PayloadAction<{ accessToken: string; user: CurrentUser }>) => {
      s.accessToken = a.payload.accessToken;
      s.user = a.payload.user;
    },
    setProfile: (s, a: PayloadAction<{ permissions: string[]; menus: MenuRoute[]; routes: MenuRouteSummary[] }>) => {
      s.permissions = a.payload.permissions;
      s.menus = a.payload.menus;
      s.routes = a.payload.routes ?? [];
    },
    clearSession: () => initialState,
  },
});
export const { setSession, setProfile, clearSession } = slice.actions;
export default slice.reducer;
