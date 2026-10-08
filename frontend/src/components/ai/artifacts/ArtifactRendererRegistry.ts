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

/** 业务扩展只需要注册 type + version，无需修改公共消息列表。 */
export function registerArtifactRenderer<T>(
  type: string,
  schemaVersion: number,
  renderer: ComponentType<AgentArtifactRendererProps<T>>,
): () => void {
  const key = rendererKey(type, schemaVersion);
  renderers.set(key, renderer as Renderer);
  return () => renderers.delete(key);
}

export function resolveArtifactRenderer(type: string, schemaVersion: number): Renderer | undefined {
  return renderers.get(rendererKey(type, schemaVersion));
}
