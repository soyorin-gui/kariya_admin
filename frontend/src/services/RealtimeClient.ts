import { getRealtimeTicket } from '../api/notifications';

export interface RealtimeEvent {
  type: string;
  data?: unknown;
}

export class RealtimeClient {
  private socket: WebSocket | null = null;
  private stopped = false;
  private retry = 0;
  private timer: number | null = null;
  constructor(private readonly onEvent: (event: RealtimeEvent) => void) {}

  start() {
    this.stopped = false;
    void this.connect();
  }
  stop() {
    this.stopped = true;
    if (this.timer != null) window.clearTimeout(this.timer);
    this.socket?.close();
    this.socket = null;
  }
  private async connect() {
    try {
      const ticket = await getRealtimeTicket();
      if (this.stopped) return;
      const base = new URL(import.meta.env.VITE_API_BASE_URL || window.location.origin, window.location.origin);
      const protocol = base.protocol === 'https:' ? 'wss:' : 'ws:';
      this.socket = new WebSocket(`${protocol}//${base.host}/ws/realtime?ticket=${encodeURIComponent(ticket)}`);
      this.socket.onopen = () => {
        this.retry = 0;
      };
      this.socket.onmessage = (message) => {
        try {
          this.onEvent(JSON.parse(message.data) as RealtimeEvent);
        } catch {
          /* ignore malformed frames */
        }
      };
      this.socket.onclose = () => this.scheduleReconnect();
      this.socket.onerror = () => this.socket?.close();
    } catch {
      this.scheduleReconnect();
    }
  }
  private scheduleReconnect() {
    if (this.stopped || this.timer != null) return;
    const delay = Math.min(30_000, 1_000 * 2 ** Math.min(this.retry++, 5)) + Math.floor(Math.random() * 500);
    this.timer = window.setTimeout(() => {
      this.timer = null;
      void this.connect();
    }, delay);
  }
}
