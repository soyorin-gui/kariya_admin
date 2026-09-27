import { configureStore } from '@reduxjs/toolkit';
import auth from './authSlice';
import notifications from './notificationSlice';
export const store = configureStore({ reducer: { auth, notifications } });
export type RootState = ReturnType<typeof store.getState>;
export type AppDispatch = typeof store.dispatch;
