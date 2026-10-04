import { useCallback, useEffect, useRef, useState } from 'react';
import type { AgentArtifact, AgentPageContext, AgentStreamEvent, ChatHistoryItem } from '../../types/agent';
import { authenticatedFetch } from '../../services/authSession';

interface SendAgentMessageOptions {
  message: string;
  history: ChatHistoryItem[];
  pageContext: AgentPageContext;
  onDelta?: (fullText: string) => void;
  onArtifact?: (artifact: AgentArtifact) => void;
}

export interface AgentChatHandle {
  /**
   * 发送一条消息并读取 SSE 流。
   * 页面上下文与回调统一放进 options，后续增加会话 id、选中对象等信息时无需继续扩展位置参数。
   * @returns 最终完整回复文本
   */
  send: (options: SendAgentMessageOptions) => Promise<string>;
  /** 取消当前请求；关闭面板和组件卸载时也会自动调用。 */
  cancel: () => void;
  loading: boolean;
  /** 当前正在执行的工具名（用于"正在调用 xx…"提示），空闲为 null。 */
  toolStatus: string | null;
}

/**
 * Agent 流式聊天 hook。
 *
 * 为什么不用 axios / EventSource：
 * - 现有 request.ts 的 axios 拦截器按"一个完整 JSON 响应"设计，不适合逐块读流；
 * - 原生 EventSource 不能自定义请求头，无法带 Authorization（JWT 在内存里）。
 * 所以这里用 fetch + ReadableStream 逐块解析 SSE（`data:` 行），认证和 401 静默续期
 * 由 authenticatedFetch 与普通 API 请求共享同一套逻辑。
 */
export function useAgentChat(): AgentChatHandle {
  const [loading, setLoading] = useState(false);
  const [toolStatus, setToolStatus] = useState<string | null>(null);
  const controllerRef = useRef<AbortController | null>(null);

  const cancel = useCallback(() => {
    controllerRef.current?.abort();
    controllerRef.current = null;
  }, []);

  useEffect(() => cancel, [cancel]);

  const send = useCallback(
    async ({ message, history, pageContext, onDelta, onArtifact }: SendAgentMessageOptions): Promise<string> => {
      cancel();
      setLoading(true);
      setToolStatus(null);
      const controller = new AbortController();
      controllerRef.current = controller;
      let text = '';
      try {
        const response = await authenticatedFetch('/agent/chat', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
          },
          signal: controller.signal,
          body: JSON.stringify({ message, history, pageContext }),
        });

        if (!response.ok || !response.body) {
          throw new Error(`请求失败（HTTP ${response.status}）`);
        }

        const reader = response.body.getReader();
        const decoder = new TextDecoder();
        let buffer = '';
        // eslint-disable-next-line no-constant-condition
        while (true) {
          const { done, value } = await reader.read();
          if (done) break;
          buffer += decoder.decode(value, { stream: true });
          const lines = buffer.split('\n');
          buffer = lines.pop() ?? '';
          for (const line of lines) {
            const trimmed = line.trim();
            if (!trimmed.startsWith('data:')) continue;
            const payload = trimmed.slice(5).trim();
            if (!payload) continue;
            try {
              const event = JSON.parse(payload) as AgentStreamEvent;
              if (event.type === 'message' && event.text) {
                text += event.text;
                onDelta?.(text);
              } else if (event.type === 'tool_call') {
                setToolStatus(event.toolName ?? null);
              } else if (event.type === 'tool_result') {
                setToolStatus(null);
                if (event.artifact) onArtifact?.(event.artifact);
              } else if (event.type === 'error') {
                // 只有前面已经有正文时才分段；首个事件就是错误时不能凭空留下顶部空行。
                text += `${text ? '\n\n' : ''}[处理出错] ${event.text ?? ''}`;
                onDelta?.(text);
              }
            } catch {
              // 忽略不完整/非 JSON 的分块
            }
          }
        }
        return text;
      } finally {
        if (controllerRef.current === controller) controllerRef.current = null;
        setLoading(false);
        setToolStatus(null);
      }
    },
    [cancel],
  );

  return { send, cancel, loading, toolStatus };
}
