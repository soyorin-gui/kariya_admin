import { Descriptions, Timeline, Typography } from 'antd';
import type { AgentTimelineItem } from '../../../../types/agent';

const colors = { INFO: 'blue', SUCCESS: 'green', WARNING: 'orange', HIGH: 'red', CRITICAL: 'red' } as const;

export function ArtifactTimeline({ items }: { items: AgentTimelineItem[] }) {
  return (
    <Timeline
      className='ai-report-timeline'
      items={items.map((item) => ({
        color: colors[item.status] ?? 'blue',
        children: (
          <div>
            <Typography.Text type='secondary'>{item.time}</Typography.Text>
            <div><Typography.Text strong>{item.title}</Typography.Text></div>
            {item.description && <Typography.Paragraph>{item.description}</Typography.Paragraph>}
            {!!item.attributes && Object.keys(item.attributes).length > 0 && (
              <Descriptions size='small' column={1} items={Object.entries(item.attributes).map(([key, value]) => ({
                key,
                label: key,
                children: String(value),
              }))} />
            )}
          </div>
        ),
      }))}
    />
  );
}
