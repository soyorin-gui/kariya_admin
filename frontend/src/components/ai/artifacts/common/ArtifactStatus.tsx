import { Tag } from 'antd';
import type { AgentReportStatus } from '../../../../types/agent';

const statusPresentation: Record<AgentReportStatus, { color: string; text: string }> = {
  INFO: { color: 'blue', text: '信息' },
  SUCCESS: { color: 'green', text: '正常' },
  WARNING: { color: 'orange', text: '注意' },
  HIGH: { color: 'red', text: '高风险' },
  CRITICAL: { color: 'magenta', text: '严重' },
};

export function ArtifactStatus({ status }: { status: AgentReportStatus }) {
  const presentation = statusPresentation[status] ?? statusPresentation.INFO;
  return <Tag color={presentation.color}>{presentation.text}</Tag>;
}
