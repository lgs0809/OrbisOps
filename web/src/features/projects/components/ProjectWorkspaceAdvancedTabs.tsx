import React from 'react';
import { Button, Space, Typography } from '@douyinfe/semi-ui';

import { SetupNav } from '../../../components/project-setup-nav';
import {
  projectWorkspaceAdvancedTabs,
  projectWorkspaceTabDefinition,
  type ProjectWorkspaceAdvancedTab,
} from '../model/project-workspace-tabs';

const { Text } = Typography;

export interface ProjectWorkspaceAdvancedTabsProps {
  value: ProjectWorkspaceAdvancedTab;
  onChange: (value: ProjectWorkspaceAdvancedTab) => void;
}

export const ProjectWorkspaceAdvancedTabs: React.FC<ProjectWorkspaceAdvancedTabsProps> = ({ value, onChange }) => {
  const active = projectWorkspaceTabDefinition(value);
  return (
    <SetupNav aria-label="项目高级配置功能导航">
      <Space vertical align="start" spacing={4} style={{ minWidth: 180 }}>
        <Text strong>Advanced Workspace</Text>
        <Text type="tertiary" size="small">{active.description}</Text>
      </Space>
      <Space wrap>
        {projectWorkspaceAdvancedTabs.map((tab) => (
          <Button
            key={tab.key}
            size="small"
            theme={tab.key === value ? 'solid' : 'borderless'}
            aria-pressed={tab.key === value}
            onClick={() => onChange(tab.key)}
          >
            {tab.label}
          </Button>
        ))}
      </Space>
    </SetupNav>
  );
};
