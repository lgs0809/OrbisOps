export type AgentBuilderMode = 'basic' | 'advanced';

export type AgentBuilderPanel = 'node' | 'edge' | 'json' | 'test';

export type AgentBuilderModeDefinition = {
  key: AgentBuilderMode;
  label: string;
  description: string;
  panels: readonly AgentBuilderPanel[];
};

export const AGENT_BUILDER_MODE_DEFINITIONS: readonly AgentBuilderModeDefinition[] = [
  {
    key: 'basic',
    label: '基础',
    description: '聚焦适用场景、画布流程、Agent Node 能力和测试，并隐藏低频 Runtime 细节。',
    panels: ['node', 'test'],
  },
  {
    key: 'advanced',
    label: '高级',
    description: '展示条件路由、原始 JSON、版本操作和高级 Runtime 配置。',
    panels: ['node', 'edge', 'json', 'test'],
  },
] as const;

export const agentBuilderMode = (value: unknown): AgentBuilderMode =>
  value === 'advanced' ? 'advanced' : 'basic';

export const agentBuilderModeDefinition = (mode: AgentBuilderMode): AgentBuilderModeDefinition =>
  AGENT_BUILDER_MODE_DEFINITIONS.find((item) => item.key === mode) || AGENT_BUILDER_MODE_DEFINITIONS[0];

export const agentBuilderPanelForMode = (
  mode: AgentBuilderMode,
  panel: AgentBuilderPanel,
): AgentBuilderPanel => (
  agentBuilderModeDefinition(mode).panels.includes(panel) ? panel : 'node'
);

export const isAgentBuilderAdvanced = (mode: AgentBuilderMode): boolean => mode === 'advanced';
