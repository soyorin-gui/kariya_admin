import { Card, Collapse, Typography } from 'antd';
import type { AgentReport, AgentReportSection } from '../../../../types/agent';
import type { AgentArtifactRendererProps } from '../ArtifactRendererRegistry';
import { ArtifactFindingList } from '../common/ArtifactFindingList';
import { ArtifactMetricGrid } from '../common/ArtifactMetricGrid';
import { ArtifactRawDataDrawer } from '../common/ArtifactRawDataDrawer';
import { ArtifactStatus } from '../common/ArtifactStatus';
import { ArtifactTable } from '../common/ArtifactTable';
import { ArtifactTimeline } from '../common/ArtifactTimeline';

function ReportSection({ section }: { section: AgentReportSection }) {
  switch (section.type) {
    case 'metrics': return <ArtifactMetricGrid metrics={section.metrics ?? []} />;
    case 'table': return <ArtifactTable columns={section.columns ?? []} rows={section.rows ?? []} />;
    case 'findings': return <ArtifactFindingList findings={section.findings ?? []} />;
    case 'timeline': return <ArtifactTimeline items={section.timeline ?? []} />;
    case 'text': return <Typography.Paragraph className='ai-report-text'>{section.text}</Typography.Paragraph>;
    default: return <Typography.Text type='secondary'>当前版本暂不支持区块：{section.type}</Typography.Text>;
  }
}

export function GenericReportRenderer({ artifact }: AgentArtifactRendererProps<AgentReport>) {
  const report = artifact.data;
  return (
    <Card
      className='ai-report-card'
      size='small'
      title={<span className='ai-report-title'><ArtifactStatus status={report.status} />{artifact.title}</span>}
      extra={<ArtifactRawDataDrawer data={artifact.data} />}
    >
      <Typography.Paragraph className='ai-report-summary'>{report.summary}</Typography.Paragraph>
      <Collapse
        ghost
        items={report.sections.map((section, index) => ({
          key: String(index),
          label: section.title || '详情',
          children: <ReportSection section={section} />,
        }))}
      />
    </Card>
  );
}
