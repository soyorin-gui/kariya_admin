import { createSlice, type PayloadAction } from '@reduxjs/toolkit';
import type { CurrentUser, MenuRoute } from '../types/auth';
interface AuthState {
  accessToken: string | null;
  user: CurrentUser | null;
  permissions: string[];
  menus: MenuRoute[];
}
const initialState: AuthState = { accessToken: null, user: null, permissions: [], menus: [] };
const slice = createSlice({
  name: 'auth',
  initialState,
  reducers: {
    setSession: (s, a: PayloadAction<{ accessToken: string; user: CurrentUser }>) => {
      s.accessToken = a.payload.accessToken;
      s.user = a.payload.user;
    },
    setProfile: (s, a: PayloadAction<{ permissions: string[]; menus: MenuRoute[] }>) => {
      s.permissions = a.payload.permissions;
      s.menus = a.payload.menus;
    },
    clearSession: () => initialState,
  },
});
export const { setSession, setProfile, clearSession } = slice.actions;
export default slice.reducer;
