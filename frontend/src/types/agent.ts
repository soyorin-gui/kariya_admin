/** 工具交给 UI 的通用制品；具体业务模块按 type + schemaVersion 注册渲染器。 */
export interface AgentArtifact<T = unknown> {
  type: string;
  schemaVersion: number;
  title?: string;
  data: T;
}

/**
 * 发起本轮对话时所在的页面。这里只描述界面位置，不携带权限码或业务数据；
 * 后端只把它作为回答上下文，绝不能据此授权。
 */
export interface AgentPageContext {
  routePath: string;
  menuId?: number;
  pageTitle?: string;
  breadcrumb: string[];
}

/** 后端 SSE 推送的通用运行事件。 */
export interface AgentStreamEvent {
  type: 'message' | 'tool_call' | 'tool_result' | 'done' | 'error';
  runId?: string;
  toolName?: string;
  text?: string;
  summary?: string;
  artifact?: AgentArtifact;
}

/** 多轮上下文里的一条历史（不含 system 提示）。 */
export interface ChatHistoryItem {
  role: 'user' | 'assistant';
  content: string;
}

/** AI 面板内部的展示消息。 */
export interface AgentConversationMessage {
  id: number;
  role: 'assistant' | 'user';
  text: string;
  artifacts?: AgentArtifact[];
}
