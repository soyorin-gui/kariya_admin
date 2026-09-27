import { createAsyncThunk, createSlice } from '@reduxjs/toolkit';
import {
  getNotifications as fetchNotificationsApi,
  getUnreadCount as fetchUnreadCountApi,
  markAllNotificationsRead as markAllNotificationsReadApi,
  markNotificationRead as markNotificationReadApi,
  type SystemNotification,
} from '../api/notifications';
import { clearSession } from './authSlice';

/*
 * 消息通知的唯一状态源：顶栏铃铛（角标 + 下拉列表）与消息中心页共用。
 *
 * 为什么必须共用一个 state
 *   之前两个组件各自持有一份 useState：铃铛拉 getNotifications(6) + 未读数，
 *   消息中心拉 getNotifications(100)。于是两边永远对不上 —— 在消息中心点"全部标为已读"，
 *   顶栏红点不会消失，要等下一次实时事件（notification.created / department.request.updated，
 *   见 AppLayout）或浏览器标签页切回来才刷新。用户看到的是"我明明都读过了，红点还在"。
 *   状态上移之后，"读一条/读全部"只发生一次、两个组件同时生效，不需要任何跨组件事件。
 *
 * 为什么只有一个列表、一个 limit
 *   不保留"顶栏 6 条 / 页面 100 条"两套列表：两个 fetch 谁后回来谁覆盖，
 *   会出现"顶栏只显示 2 条"这种随机的表现。这里统一拉 NOTIFICATION_FETCH_LIMIT 条，
 *   顶栏下拉与消息中心页展示同一份列表 —— 取数一处、展示两处。
 */

/**
 * 一次拉取的条数上限（后端还会再夹到 100 以内，见 NotificationService#latest）。
 * 取 100 是为了与改动前消息中心页的行为一致（那里原来是 `getNotifications(100)`）。
 * 顶栏下拉曾经只展示前 6 条，现已改为展示整份列表、用 CSS 高度上限 + 内部滚动代替截断
 * （见 AppLayout 的 .notification-panel-body），所以这个值现在同时决定两处的历史条数上限。
 */
export const NOTIFICATION_FETCH_LIMIT = 100;

interface NotificationState {
  items: SystemNotification[];
  unreadCount: number;
  loading: boolean;
}

/**
 * {@code loading} 初值为 true：消息中心在首次拉取返回前应显示加载态，而不是先闪一下
 * "暂无消息"空态（改动前那个组件的 loading 初值也是 true）。只有它读这个字段。
 */
const initialState: NotificationState = { items: [], unreadCount: 0, loading: true };

/** 拉取通知列表 + 未读数。两者必须同一个请求里更新，否则角标与列表会短暂不一致。 */
export const loadNotifications = createAsyncThunk('notifications/load', async () => {
  const [items, unreadCount] = await Promise.all([fetchNotificationsApi(NOTIFICATION_FETCH_LIMIT), fetchUnreadCountApi()]);
  return { items, unreadCount };
});

/** 标记单条已读。成功后只在本地改这一条并把角标减一，不再回拉整张列表。 */
export const readNotification = createAsyncThunk('notifications/read', async (id: number) => {
  await markNotificationReadApi(id);
  return id;
});

/** 全部标记已读。 */
export const readAllNotifications = createAsyncThunk('notifications/readAll', async () => {
  await markAllNotificationsReadApi();
});

const slice = createSlice({
  name: 'notifications',
  initialState,
  reducers: {},
  extraReducers: (builder) => {
    builder
      .addCase(loadNotifications.pending, (state) => {
        state.loading = true;
      })
      .addCase(loadNotifications.fulfilled, (state, action) => {
        state.items = action.payload.items;
        state.unreadCount = action.payload.unreadCount;
        state.loading = false;
      })
      // 加载失败保持上一次的数据（静默失败，与改动前的行为一致）：实时推送是"锦上添花"，
      // 拿不到就等下一次事件或用户手动刷新，不该把已有列表清空、也不该弹错误提示打断操作。
      .addCase(loadNotifications.rejected, (state) => {
        state.loading = false;
      })
      .addCase(readNotification.fulfilled, (state, action) => {
        const item = state.items.find((value) => value.id === action.payload);
        if (!item || item.readTime) return;
        item.readTime = new Date().toISOString();
        // 未读数是服务端算的（可能大于本页拉到的条数），这里只做"少了一条未读"的增量更新。
        state.unreadCount = Math.max(0, state.unreadCount - 1);
      })
      .addCase(readAllNotifications.fulfilled, (state) => {
        const now = new Date().toISOString();
        state.items.forEach((item) => {
          item.readTime = item.readTime ?? now;
        });
        state.unreadCount = 0;
      })
      /**
       * 会话结束即清空。
       *
       * 这一步不能省：状态上移到全局 store 之后，它不再随 AppLayout 卸载而消失。
       * 退出登录后换另一个账号登录（同一个 SPA 会话内，`/login` 是前端路由而不是整页刷新），
       * 在新账号的首次拉取返回之前，顶栏会显示<b>上一个账号</b>的通知与未读数 —— 属于串号泄露。
       * 而 {@link clearSession} 是"会话结束"的唯一收口（见 AppLayout 退出、
       * DynamicPage 无可用页面、request.ts 续期失败、AuthGuard 校验失败、开户确认页退出），
       * 跟着它清空，比在每个退出点各写一句可靠。
       */
      .addCase(clearSession, () => initialState);
  },
});

export default slice.reducer;
