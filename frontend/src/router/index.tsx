import { createBrowserRouter, Navigate } from 'react-router-dom';
import { AppLayout } from '../layouts/AppLayout';
import LoginPage from '../pages/login';
import HomePage from '../pages/home';
import UserPage from '../pages/system/user';
import RolePage from '../pages/system/role';
import DeptPage from '../pages/system/dept';
import MenuPage from '../pages/system/menu';
import NotFound from '../pages/error/NotFound';
import { AuthGuard } from '../components/common/AuthGuard';
export const router = createBrowserRouter([
  { path: '/login', element: <LoginPage /> },
  {
    path: '/',
    element: <AuthGuard><AppLayout /></AuthGuard>,
    children: [
      { index: true, element: <Navigate to='/home' replace /> },
      { path: 'home', element: <HomePage /> },
      { path: 'system/user', element: <UserPage /> },
      { path: 'system/role', element: <RolePage /> },
      { path: 'system/dept', element: <DeptPage /> },
      { path: 'system/menu', element: <MenuPage /> },
    ],
  },
  { path: '*', element: <NotFound /> },
]);
