import { Alert, Typography } from 'antd';
import type { ComponentType } from 'react';
import type { AgentArtifact } from '../../../types/agent';

export interface AgentArtifactRendererProps<T = unknown> {
  artifact: AgentArtifact<T>;
}

type Renderer = ComponentType<AgentArtifactRendererProps<never>>;
const renderers = new Map<string, Renderer>();

function rendererKey(type: string, schemaVersion: number): string {
  return `${type}@${schemaVersion}`;
}

/** 业务模块在自身入口注册渲染器，公共 AI 面板无需添加业务 if/else。 */
export function registerArtifactRenderer<T>(type: string, schemaVersion: number, renderer: ComponentType<AgentArtifactRendererProps<T>>): () => void {
  const key = rendererKey(type, schemaVersion);
  renderers.set(key, renderer as Renderer);
  return () => renderers.delete(key);
}

/** 未知制品只显示安全的 JSON 文本，不解释 HTML，也不执行其中的脚本。 */
export function AgentArtifactView({ artifact }: AgentArtifactRendererProps) {
  const Renderer = renderers.get(rendererKey(artifact.type, artifact.schemaVersion));
  if (Renderer) return <Renderer artifact={artifact as AgentArtifact<never>} />;
  return (
    <Alert
      className='ai-artifact-fallback'
      type='info'
      showIcon
      message={artifact.title || `${artifact.type} · v${artifact.schemaVersion}`}
      description={
        <Typography.Text code className='ai-artifact-json'>
          {JSON.stringify(artifact.data, null, 2)}
        </Typography.Text>
      }
    />
  );
}
