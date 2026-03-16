import React from 'react';
import { Button, Space, Typography } from '@douyinfe/semi-ui';

import {
  AGENT_BUILDER_MODE_DEFINITIONS,
  AgentBuilderMode,
  agentBuilderModeDefinition,
} from '../model/agent-builder-mode';

const { Text } = Typography;

type Props = {
  value: AgentBuilderMode;
  onChange: (mode: AgentBuilderMode) => void;
};

export const AgentBuilderModeSwitch: React.FC<Props> = ({ value, onChange }) => {
  const active = agentBuilderModeDefinition(value);

  return (
    <Space vertical align="start" spacing="tight">
      <Space wrap>
        {AGENT_BUILDER_MODE_DEFINITIONS.map((mode) => (
          <Button
            key={mode.key}
            size="small"
            theme={mode.key === value ? 'solid' : 'borderless'}
            aria-pressed={mode.key === value}
            onClick={() => onChange(mode.key)}
          >
            {mode.label}
          </Button>
        ))}
      </Space>
      <Text type="tertiary" size="small">{active.description}</Text>
    </Space>
  );
};
