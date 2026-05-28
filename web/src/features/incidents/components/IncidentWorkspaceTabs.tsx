import React from 'react';
import { Button, Space } from '@douyinfe/semi-ui';

export type IncidentWorkspaceTab = 'overview' | 'investigation' | 'evidence' | 'change' | 'timeline';

const tabs: Array<{ key: IncidentWorkspaceTab; label: string }> = [
  { key: 'overview', label: 'Overview' },
  { key: 'investigation', label: 'Investigation' },
  { key: 'evidence', label: 'Evidence' },
  { key: 'change', label: 'Change' },
  { key: 'timeline', label: 'Timeline' },
];

export const IncidentWorkspaceTabs: React.FC<{
  value: IncidentWorkspaceTab;
  onChange: (value: IncidentWorkspaceTab) => void;
}> = ({ value, onChange }) => (
  <Space wrap data-testid="incident-workspace-tabs">
    {tabs.map((tab) => (
      <Button
        key={tab.key}
        theme={value === tab.key ? 'solid' : 'light'}
        type={value === tab.key ? 'primary' : 'tertiary'}
        onClick={() => onChange(tab.key)}
      >
        {tab.label}
      </Button>
    ))}
  </Space>
);
