import React from 'react';
import { Button, Select, Typography } from '@douyinfe/semi-ui';
import { IconClock, IconPlay, IconRefresh, IconSetting } from '@douyinfe/semi-icons';
import { useNavigate } from 'react-router-dom';
import styled from 'styled-components';

import { OpsPageShell } from '../components/ops-layout';
import { useProjectScope } from '../hooks/use-project-scope';
import { theme } from '../styles/theme';

const { Paragraph, Text, Title } = Typography;

const AutomationHeader = styled.header`
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 18px;
  align-items: end;
  padding: 22px 0 20px;

  h1 {
    margin: 0;
    color: ${theme.colors.text.primary};
    font-size: 36px;
    font-weight: 650;
    letter-spacing: -0.04em;
  }

  p {
    max-width: 720px;
    margin: 8px 0 0;
    color: ${theme.colors.text.tertiary};
    font-size: 12px;
    line-height: 1.65;
  }

  @media (max-width: ${theme.breakpoints.md}) {
    grid-template-columns: 1fr;
    align-items: start;
  }
`;

const ContextBar = styled.div`
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border: 1px solid ${theme.colors.border.secondary};
  border-radius: 11px;
  background: #fff;
  flex-wrap: wrap;

  .semi-select { min-width: 220px; }
`;

const Grid = styled.div`
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
  margin-top: 18px;

  @media (max-width: ${theme.breakpoints.md}) {
    grid-template-columns: 1fr;
  }
`;

const AutomationCard = styled.article`
  min-height: 230px;
  display: flex;
  flex-direction: column;
  padding: 20px;
  border: 1px solid ${theme.colors.border.secondary};
  border-radius: 13px;
  background: #fff;
  transition: transform ${theme.animation.duration.fast} ease, border-color ${theme.animation.duration.fast} ease, box-shadow ${theme.animation.duration.fast} ease;

  &:hover {
    transform: translateY(-1px);
    border-color: #d4d7dc;
    box-shadow: ${theme.shadows.card};
  }
`;

const IconBox = styled.div<{ $tone: 'blue' | 'orange' }>`
  width: 42px;
  height: 42px;
  display: grid;
  place-items: center;
  border-radius: 11px;
  background: ${(props) => (props.$tone === 'blue' ? '#eef4ff' : '#fff4e8')};
  color: ${(props) => (props.$tone === 'blue' ? '#2563eb' : '#c96a09')};

  .semi-icon {
    font-size: 20px;
  }
`;

const CardBody = styled.div`
  flex: 1;
  margin: 18px 0;

  .semi-typography-paragraph {
    max-width: 560px;
  }
`;

const Note = styled.div`
  margin-top: 14px;
  padding: 11px 13px;
  border: 1px solid ${theme.colors.border.tertiary};
  border-radius: 10px;
  background: #fafbfc;
  color: ${theme.colors.text.tertiary};
  font-size: 12px;
  line-height: 1.6;
`;

export const AutomationsPage: React.FC = () => {
  const navigate = useNavigate();
  const projectScope = useProjectScope();
  const projectQuery = projectScope.projectId ? `?projectId=${encodeURIComponent(projectScope.projectId)}` : '';

  return (
    <OpsPageShell selectedKey="automations">
      <AutomationHeader>
        <div>
          <h1>自动化</h1>
          <p>让任务按时间计划或告警事件自动启动。先选项目和中文运行频率，具体执行仍进入统一工作台。</p>
        </div>
        <Button icon={<IconSetting />} onClick={() => navigate(`/projects${projectQuery}`)}>项目配置</Button>
      </AutomationHeader>

      <ContextBar>
        <Text type="tertiary">项目</Text>
        <Select
          value={projectScope.projectId || undefined}
          placeholder="选择项目"
          loading={projectScope.loading}
          onChange={(value) => projectScope.selectProject(String(value || ''))}
        >
          {projectScope.projects.map((project) => <Select.Option key={project.projectId} value={project.projectId}>{project.name}</Select.Option>)}
        </Select>
        <Button icon={<IconRefresh />} onClick={() => void projectScope.reloadProjects()}>刷新</Button>
        <Text type="tertiary">{projectScope.selectedProject?.description || '选择项目后配置自动化。'}</Text>
      </ContextBar>

      <Grid>
        <AutomationCard>
          <IconBox $tone="blue"><IconClock /></IconBox>
          <CardBody>
            <Title heading={5} style={{ margin: 0 }}>定时任务</Title>
            <Paragraph type="tertiary" style={{ margin: '8px 0 0' }}>
              按固定时间或周期启动默认助手或已发布 Workflow，适合巡检、日报和周期检查。
            </Paragraph>
          </CardBody>
          <Button theme="solid" type="primary" disabled={!projectScope.projectId} onClick={() => navigate(`/automations/schedules${projectQuery}`)}>
            打开定时任务
          </Button>
        </AutomationCard>

        <AutomationCard>
          <IconBox $tone="orange"><IconPlay /></IconBox>
          <CardBody>
            <Title heading={5} style={{ margin: 0 }}>告警触发器</Title>
            <Paragraph type="tertiary" style={{ margin: '8px 0 0' }}>
              收到告警事件后自动启动对应项目任务，适合故障发现后的自动排查和标准处置流程。
            </Paragraph>
          </CardBody>
          <Button disabled={!projectScope.projectId} onClick={() => navigate(`/automations/alert-triggers${projectQuery}`)}>
            打开告警触发器
          </Button>
        </AutomationCard>
      </Grid>

      <Note>
        <Text type="tertiary">常用频率已经翻译成中文；需要特殊安排时选择“自定义”。自动化只负责“何时启动”和“运行什么”；具体执行仍受项目权限、工作流发布状态和变更治理规则约束。</Text>
      </Note>
    </OpsPageShell>
  );
};
