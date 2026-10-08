import { Empty, List, Space, Typography } from 'antd';
import type { AgentFinding } from '../../../../types/agent';
import { ArtifactStatus } from './ArtifactStatus';

function entries(attributes?: Record<string, unknown>): string {
  if (!attributes) return '';
  return Object.entries(attributes)
    .map(([key, value]) => `${key}：${String(value)}`)
    .join(' · ');
}

export function ArtifactFindingList({ findings }: { findings: AgentFinding[] }) {
  if (!findings.length) return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description='当前规则未命中风险' />;
  return (
    <List
      size='small'
      dataSource={findings}
      renderItem={(finding) => (
        <List.Item className='ai-report-finding'>
          <Space direction='vertical' size={3}>
            <Space wrap>
              <ArtifactStatus status={finding.severity} />
              <Typography.Text strong>{finding.title}</Typography.Text>
            </Space>
            {finding.summary && <Typography.Text>{finding.summary}</Typography.Text>}
            {entries(finding.attributes) && <Typography.Text type='secondary'>{entries(finding.attributes)}</Typography.Text>}
            {!!finding.evidenceIds?.length && (
              <Typography.Text type='secondary'>证据：{finding.evidenceIds.join('、')}</Typography.Text>
            )}
          </Space>
        </List.Item>
      )}
    />
  );
}
