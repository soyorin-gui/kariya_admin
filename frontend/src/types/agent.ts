export type ContentKind = 'TEXT' | 'DIFF' | 'FILE' | 'GRAPH';

/** 后端 SSE 推送的事件（与 org.lbl.agent.core.model.AgentModels.AgentEvent 对应）。 */
export interface AgentStreamEvent {
  type: 'message' | 'tool_call' | 'tool_result' | 'done' | 'error';
  toolName?: string;
  kind?: ContentKind;
  text?: string;
  summary?: string;
  payload?: unknown;
}

/** 多轮上下文里的一条历史（不含 system 提示）。 */
export interface ChatHistoryItem {
  role: 'user' | 'assistant';
  content: string;
}

export type DiffType = 'CONSISTENT' | 'DATA_INCONSISTENT' | 'SORT_ONLY' | 'MISSING_RECORDS';

export interface FieldDiff {
  key: string;
  field: string;
  left: string;
  right: string;
}

/** 双系统对比的结构化结果（后端 ContentKind.DIFF 的 payload）。 */
export interface DiffReport {
  type: DiffType;
  leftCount: number;
  rightCount: number;
  commonCount: number;
  leftOnly: string[];
  rightOnly: string[];
  fieldDiffs: FieldDiff[];
  orderMismatch: boolean;
  summaryText: string;
}
