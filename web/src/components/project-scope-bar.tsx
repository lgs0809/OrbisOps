import React from 'react';
import { Button, Select, Space, Typography } from '@douyinfe/semi-ui';
import { IconFolder, IconRefresh } from '@douyinfe/semi-icons';
import styled from 'styled-components';

import { OpsProjectWorkspace } from '../services/ops-project-service';
import { theme } from '../styles/theme';

const { Text } = Typography;

const Container = styled.section`
  box-sizing: border-box;
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  min-width: 0;
  margin: 0 0 16px;
  padding: 10px 12px;
  border: 1px solid ${theme.colors.border.secondary};
  border-radius: 11px;
  background: #fff;

  @media (max-width: ${theme.breakpoints.md}) {
    align-items: stretch;
    flex-direction: column;
  }
`;

const ScopeLabel = styled.div`
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
  color: ${theme.colors.text.tertiary};
  font-size: 12px;
`;

const ProjectSelect = styled(Select)`
  width: min(280px, 100%);
  min-width: 180px;

  @media (max-width: ${theme.breakpoints.md}) {
    width: 100%;
  }
`;

const ProjectMeta = styled.div`
  min-width: 0;
  flex: 1;
  overflow: hidden;
  color: ${theme.colors.text.tertiary};

  .semi-typography {
    display: block;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  @media (max-width: ${theme.breakpoints.sm}) {
    display: none;
  }
`;

const Actions = styled(Space)`
  flex-shrink: 0;

  @media (max-width: ${theme.breakpoints.md}) {
    justify-content: flex-end;
  }
`;

interface ProjectScopeBarProps {
  projects: OpsProjectWorkspace[];
  projectId: string;
  onChange: (projectId: string) => void;
  loading?: boolean;
  onRefresh?: () => void;
  actions?: React.ReactNode;
}

export const ProjectScopeBar: React.FC<ProjectScopeBarProps> = ({
  projects,
  projectId,
  onChange,
  loading,
  onRefresh,
  actions,
}) => {
  const project = projects.find((item) => item.projectId === projectId);

  return (
    <Container aria-label="当前项目">
      <ScopeLabel><IconFolder />项目</ScopeLabel>
      <ProjectSelect
        value={projectId || undefined}
        placeholder="选择项目"
        loading={loading}
        onChange={(value) => onChange(String(value || ''))}
      >
        {projects.map((item) => (
          <Select.Option key={item.projectId} value={item.projectId}>
            {item.name}
          </Select.Option>
        ))}
      </ProjectSelect>
      <ProjectMeta>
        <Text type="tertiary">{project?.description || (project ? '暂无项目说明' : '请选择项目后继续')}</Text>
      </ProjectMeta>
      <Actions wrap spacing="tight">
        {onRefresh && <Button theme="borderless" icon={<IconRefresh />} aria-label="刷新" onClick={onRefresh}>刷新</Button>}
        {actions}
      </Actions>
    </Container>
  );
};
