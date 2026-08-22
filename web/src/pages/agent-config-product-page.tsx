import React from 'react';
import { Button, Card, Space, Spin, Tag, Typography } from '@douyinfe/semi-ui';
import { IconAlertTriangle } from '@douyinfe/semi-icons';
import { useNavigate, useSearchParams } from 'react-router-dom';
import styled from 'styled-components';

import { WorkflowBuilderPage } from './workflow-builder';
import type { AgentReferenceImpact } from '../services/ops-agent-reference-impact-service';
import { useAgentReferenceImpactQuery } from '../features/agents/api/agent-list-queries';
import { theme } from '../styles/theme';

const { Paragraph, Text } = Typography;

const ImpactPanel = styled.div`
  position: fixed;
  z-index: 1200;
  top: 76px;
  right: 20px;
  width: min(390px, calc(100vw - 40px));
  pointer-events: none;

  .semi-card {
    pointer-events: auto;
    box-shadow: 0 10px 28px rgba(15, 23, 42, 0.16);
    border: 1px solid ${theme.colors.border.secondary};
  }

  @media (max-width: ${theme.breakpoints.md}) {
    position: static;
    width: auto;
    margin: ${theme.spacing.base};
  }
`;

const emptyImpact = (agentId: string): AgentReferenceImpact => ({
  agentId,
  scheduleCount: 0,
  alertCount: 0,
  channelCount: 0,
  totalCount: 0,
  latestPublishedCount: 0,
  pinnedVersionCount: 0,
  references: [],
});

export const AgentConfigProductPage: React.FC = () => {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const projectId = searchParams.get('projectId') || '';
  const agentId = searchParams.get('agentId') || '';
  const impactQuery = useAgentReferenceImpactQuery(projectId, agentId);
  const loading = impactQuery.isFetching;
  const impact = impactQuery.data || emptyImpact(agentId);

  return (
    <>
      <WorkflowBuilderPage />
      <ImpactPanel aria-label="Workflow 发布影响">
        <Card>
          <Spin spinning={loading}>
            <Space vertical align="start" style={{ width: '100%' }}>
              <Space wrap>
                <IconAlertTriangle />
                <Text strong>发布影响</Text>
                {!agentId && <Tag color="grey">新建 Workflow</Tag>}
              </Space>
              {!agentId ? (
                <Paragraph type="tertiary" style={{ margin: 0 }}>
                  当前 Workflow 还没有被自动化或 Channel 引用。发布后可以在工作流列表查看引用影响。
                </Paragraph>
              ) : impact.totalCount === 0 ? (
                <Paragraph type="tertiary" style={{ margin: 0 }}>
                  当前没有自动化或 Channel 引用该 Workflow，因此发布新版本不会切换任何执行绑定。
                </Paragraph>
              ) : (
                <>
                  <Space wrap>
                    <Tag color="blue">定时 {impact.scheduleCount}</Tag>
                    <Tag color="orange">告警 {impact.alertCount}</Tag>
                    <Tag color="green">Channel {impact.channelCount}</Tag>
                  </Space>
                  <Paragraph type="tertiary" style={{ margin: 0 }}>
                    {impact.latestPublishedCount} 个 `LATEST_PUBLISHED` 引用会跟随新版本；{' '}
                    {impact.pinnedVersionCount} 个 `PINNED_VERSION` 引用保持固定。
                  </Paragraph>
                </>
              )}
              <Button
                size="small"
                onClick={() => navigate(`/workflows${projectId ? `?projectId=${encodeURIComponent(projectId)}` : ''}`)}
              >
                查看全部 Workflow 引用
              </Button>
            </Space>
          </Spin>
        </Card>
      </ImpactPanel>
    </>
  );
};
