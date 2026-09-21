import { createBrowserRouter, Navigate } from 'react-router-dom';
import { AppLayout } from '../layouts/AppLayout';
import LoginPage from '../pages/login';
import HomePage from '../pages/home';
import UserPage from '../pages/system/user';
import PlaceholderPage from '../pages/system/PlaceholderPage';
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
      { path: 'system/role', element: <PlaceholderPage title='角色管理' /> },
      { path: 'system/dept', element: <PlaceholderPage title='部门管理' /> },
      { path: 'system/menu', element: <PlaceholderPage title='菜单管理' /> },
    ],
  },
  { path: '*', element: <NotFound /> },
]);
