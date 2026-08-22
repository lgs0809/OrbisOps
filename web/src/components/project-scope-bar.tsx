import React from 'react';
import { Button, Select, Space, Typography } from '@douyinfe/semi-ui';
import { IconFolder, IconRefresh } from '@douyinfe/semi-icons';
import styled from 'styled-components';

import { OpsProjectWorkspace } from '../services/ops-project-service';
import { theme } from '../styles/theme';

const { Text, Title } = Typography;

const Container = styled.section`
  box-sizing: border-box;
  display: grid;
  grid-template-columns: minmax(260px, 420px) minmax(0, 1fr) auto;
  align-items: center;
  gap: ${theme.spacing.lg};
  padding: ${theme.spacing.base} ${theme.spacing.lg};
  margin: 0 auto ${theme.spacing.lg};
  width: 100%;
  max-width: 100%;
  min-width: 0;
  background: ${theme.colors.bg.primary};
  border: 1px solid ${theme.colors.border.secondary};
  border-radius: ${theme.borderRadius.base};

  @media (max-width: ${theme.breakpoints.lg}) {
    grid-template-columns: 1fr;
    padding: ${theme.spacing.base};
  }
`;

const ProjectSelect = styled(Select)`
  width: 100%;
  min-width: 0;
`;

const ProjectMeta = styled.div`
  min-width: 0;

  .project-name {
    margin: 0 0 ${theme.spacing.xs} !important;
  }

  .semi-typography {
    display: block;
    max-width: 100%;
    white-space: normal;
    overflow-wrap: anywhere;
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
      <div>
        <Text strong>当前项目</Text>
        <ProjectSelect
          value={projectId || undefined}
          placeholder="选择项目"
          loading={loading}
          prefix={<IconFolder />}
          onChange={(value) => onChange(String(value || ''))}
        >
          {projects.map((item) => (
            <Select.Option key={item.projectId} value={item.projectId}>
              {item.name}
            </Select.Option>
          ))}
        </ProjectSelect>
      </div>
      <ProjectMeta>
        <Title className="project-name" heading={6}>{project?.name || '尚未选择项目'}</Title>
        <Text type="tertiary">
          {project ? `${project.projectId} · ${project.description || '暂无项目说明'}` : '列表、创建操作和权限都会限定在当前选择的项目范围内。'}
        </Text>
      </ProjectMeta>
      <Space wrap>
        {onRefresh && <Button icon={<IconRefresh />} onClick={onRefresh}>刷新</Button>}
        {actions}
      </Space>
    </Container>
  );
};
