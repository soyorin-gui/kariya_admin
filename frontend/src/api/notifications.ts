import request from '../utils/request';
import type { Result } from '../types/common';

export interface SystemNotification {
  id: number;
  type: string;
  title: string;
  content: string;
  businessType?: string;
  businessId?: number;
  readTime?: string;
  createdTime: string;
}

export const getNotifications = (limit = 50) => request.get<Result<SystemNotification[]>>('/account/notifications', { params: { limit } }).then((r) => r.data.data);
export const getUnreadCount = () => request.get<Result<{ count: number }>>('/account/notifications/unread-count').then((r) => r.data.data.count);
export const markNotificationRead = (id: number) => request.put(`/account/notifications/${id}/read`);
export const markAllNotificationsRead = () => request.put('/account/notifications/read-all');
export const getRealtimeTicket = () => request.post<Result<{ ticket: string }>>('/account/realtime/ticket').then((r) => r.data.data.ticket);
