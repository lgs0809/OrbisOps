import React from 'react';
import { Button, Card, Space, Tag, Typography } from '@douyinfe/semi-ui';
import { IconClock, IconPlay, IconSetting } from '@douyinfe/semi-icons';
import { useNavigate } from 'react-router-dom';
import styled from 'styled-components';

import { OpsPageHeader, OpsPageShell } from '../components/ops-layout';
import { useProjectScope } from '../hooks/use-project-scope';
import { theme } from '../styles/theme';

const { Paragraph, Text, Title } = Typography;

const Grid = styled.div`
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: ${theme.spacing.base};

  @media (max-width: ${theme.breakpoints.md}) {
    grid-template-columns: 1fr;
  }
`;

const Flow = styled.div`
  display: flex;
  align-items: center;
  gap: ${theme.spacing.sm};
  flex-wrap: wrap;
  margin-top: ${theme.spacing.base};
`;

const Arrow = styled.span`
  color: ${theme.colors.text.tertiary};
`;

export const AutomationsPage: React.FC = () => {
  const navigate = useNavigate();
  const projectScope = useProjectScope();
  const projectQuery = projectScope.projectId ? `?projectId=${encodeURIComponent(projectScope.projectId)}` : '';

  return (
    <OpsPageShell selectedKey="automations">
      <OpsPageHeader
        title="自动化"
        description="按时间或告警自动启动项目任务，并选择默认智能执行或已发布工作流。"
        extra={<Button icon={<IconSetting />} onClick={() => navigate(`/projects${projectQuery}`)}>打开项目</Button>}
      />

      <Card style={{ marginBottom: theme.spacing.base }}>
        <Text strong>执行模型</Text>
        <Flow aria-label="自动化执行模型">
          <Tag color="blue">触发条件</Tag><Arrow>→</Arrow>
          <Tag color="blue">项目</Tag><Arrow>→</Arrow>
          <Tag color="green">默认模型</Tag>
          <Text type="tertiary">或</Text>
          <Tag color="green">已发布工作流</Tag><Arrow>→</Arrow>
          <Tag color="orange">策略</Tag><Arrow>→</Arrow>
          <Tag>运行</Tag><Arrow>→</Arrow>
          <Tag>工作台</Tag>
        </Flow>
      </Card>

      <Grid>
        <Card>
          <Space vertical align="start" spacing="medium" style={{ width: '100%' }}>
            <IconClock size="large" />
            <div>
              <Title heading={5} style={{ margin: 0 }}>定时自动化</Title>
              <Paragraph type="tertiary" style={{ marginBottom: 0 }}>
                按 cron 计划运行默认模型或已发布工作流，并可选轻量筛选和完成通知。
              </Paragraph>
            </div>
            <Button theme="solid" type="primary" onClick={() => navigate(`/automations/schedules${projectQuery}`)}>管理定时任务</Button>
          </Space>
        </Card>

        <Card>
          <Space vertical align="start" spacing="medium" style={{ width: '100%' }}>
            <IconPlay size="large" />
            <div>
              <Title heading={5} style={{ margin: 0 }}>告警触发器</Title>
              <Paragraph type="tertiary" style={{ marginBottom: 0 }}>
                将告警事件路由到指定项目和执行绑定；产生的运行记录与其他正式执行一样统一进入工作台。
              </Paragraph>
            </div>
            <Button onClick={() => navigate(`/automations/alert-triggers${projectQuery}`)}>管理告警触发器</Button>
          </Space>
        </Card>
      </Grid>
    </OpsPageShell>
  );
};
