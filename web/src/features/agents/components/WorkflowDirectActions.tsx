import { Banner, Button, Space, TextArea, Toast, Typography } from '@douyinfe/semi-ui';
import { type AgentBuilderMode } from '../model/agent-builder-mode';

/** A read-only action overview in basic mode; configuration stays in the advanced editor. */
export const WorkflowDirectActions = ({ nodeId, actions, mode, toolNames, onAdvanced, onChange }: {
  nodeId: string;
  actions: unknown;
  mode: AgentBuilderMode;
  toolNames: ReadonlyMap<string, string>;
  onAdvanced: () => void;
  onChange: (actions: unknown[]) => void;
}) => {
  const items = Array.isArray(actions) ? actions : [];
  return <Space vertical align="start" style={{ width: '100%' }}>
    <Typography.Text strong>已配置动作 · {items.length} 项</Typography.Text>
    {items.length === 0 && <Banner type="warning" description="尚未配置动作。配置完成后才能执行这个节点。" />}
    {mode === 'basic' ? <>
      <ol style={{ margin: 0, paddingInlineStart: 22, width: '100%', boxSizing: 'border-box', overflowWrap: 'anywhere' }}>
        {items.map((value, index) => {
          const action = value && typeof value === 'object' ? value as Record<string, unknown> : {};
          const tool = String(action.remoteToolName || action.toolName || '未命名动作');
          const source = toolNames.get(String(action.mcpId || ''));
          return <li key={index} style={{ marginBlock: 6 }}><Typography.Text>{tool}{source ? ` · ${source}` : ''}</Typography.Text></li>;
        })}
      </ol>
      <Button theme="borderless" onClick={onAdvanced}>编辑动作配置</Button>
    </> : <>
      <Typography.Text type="tertiary" size="small">高级动作配置</Typography.Text>
      <TextArea
        aria-label="高级动作配置 JSON"
        key={`${nodeId}:${JSON.stringify(actions ?? [])}`}
        style={{ width: '100%' }}
        autosize={{ minRows: 5, maxRows: 12 }}
        defaultValue={JSON.stringify(actions ?? [], null, 2)}
        onBlur={(event) => {
          try {
            const parsed: unknown = JSON.parse(event.target.value);
            if (!Array.isArray(parsed)) throw new Error('Actions must be an array');
            onChange(parsed);
          } catch {
            Toast.error('动作配置格式有误，原配置已保留。');
          }
        }}
      />
    </>}
    <Typography.Text type="tertiary" size="small">DIRECT 节点按配置顺序执行受治理动作；动作执行不调用模型。</Typography.Text>
  </Space>;
};
