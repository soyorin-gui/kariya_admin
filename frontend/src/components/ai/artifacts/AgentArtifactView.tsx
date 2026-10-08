import { Card, Typography } from 'antd';
import type { AgentArtifact, AgentReport } from '../../../types/agent';
import { registerArtifactRenderer, resolveArtifactRenderer, type AgentArtifactRendererProps } from './ArtifactRendererRegistry';
import { ArtifactRawDataDrawer } from './common/ArtifactRawDataDrawer';
import { GenericReportRenderer } from './renderers/GenericReportRenderer';

registerArtifactRenderer<AgentReport>('ui.report', 1, GenericReportRenderer);

export { registerArtifactRenderer } from './ArtifactRendererRegistry';
export type { AgentArtifactRendererProps } from './ArtifactRendererRegistry';

/** 未知制品安全降级为简短提示，原始 JSON 放入按需打开的抽屉。 */
export function AgentArtifactView({ artifact }: AgentArtifactRendererProps) {
  const Renderer = resolveArtifactRenderer(artifact.type, artifact.schemaVersion);
  if (Renderer) return <Renderer artifact={artifact as AgentArtifact<never>} />;
  return (
    <Card
      className='ai-artifact-fallback'
      size='small'
      title={artifact.title || `${artifact.type} · v${artifact.schemaVersion}`}
      extra={<ArtifactRawDataDrawer data={artifact.data} />}
    >
      <Typography.Text type='secondary'>已生成结构化结果，当前版本暂无专用展示组件。</Typography.Text>
    </Card>
  );
}
