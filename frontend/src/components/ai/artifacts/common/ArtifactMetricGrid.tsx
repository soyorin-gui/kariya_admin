import { Col, Row, Statistic } from 'antd';
import type { AgentMetric } from '../../../../types/agent';

export function ArtifactMetricGrid({ metrics }: { metrics: AgentMetric[] }) {
  return (
    <Row gutter={[8, 8]}>
      {metrics.map((metric, index) => (
        <Col span={12} key={`${metric.label}-${index}`}>
          <Statistic className='ai-report-metric' title={metric.label} value={String(metric.value ?? '-')} />
        </Col>
      ))}
    </Row>
  );
}
