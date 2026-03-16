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
    label: 'Basic',
    description: '聚焦场景、画布、节点能力与测试，隐藏低频运行时细节。',
    panels: ['node', 'test'],
  },
  {
    key: 'advanced',
    label: 'Advanced',
    description: '开放条件连线、原始 JSON、版本运维和运行时高级配置。',
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
