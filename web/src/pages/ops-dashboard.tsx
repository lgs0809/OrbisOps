import React, { useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import styled from 'styled-components';
import { Button, Empty, Spin, Typography } from '@douyinfe/semi-ui';
import { IconAlertTriangle, IconBranch, IconClock, IconHistory, IconShield } from '@douyinfe/semi-icons';

import { OpsPageHeader, OpsPageShell, OpsSectionCard } from '../components/ops-layout';
import {
  AttentionItem,
  buildAdminAttentionItems,
  buildUserAttentionItems,
  projectNeedsAttention,
} from '../features/dashboard/attention-center-model';
import { useDashboardOverviewQuery } from '../features/dashboard/api/dashboard-queries';
import { isAdminUser } from '../services/auth-session';
import { OpsChangePackage } from '../services/ops-admin-service';
import { OpsProjectWorkspace } from '../services/ops-project-service';
import { theme } from '../styles/theme';

const { Text, Title } = Typography;

const SummaryGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(4, minmax(180px, 1fr));
  gap: ${theme.spacing.base};
  margin-bottom: ${theme.spacing.base};

  @media (max-width: ${theme.breakpoints.xl}) {
    grid-template-columns: repeat(2, minmax(180px, 1fr));
  }

  @media (max-width: ${theme.breakpoints.sm}) {
    grid-template-columns: 1fr;
  }
`;

const SummaryCard = styled.div<{ $tone?: 'danger' | 'warning' | 'info' }>`
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-height: 112px;
  padding: ${theme.spacing.base};
  border: 1px solid ${(props) => props.$tone === 'danger'
    ? 'rgba(220, 38, 38, 0.26)'
    : props.$tone === 'warning'
      ? 'rgba(217, 119, 6, 0.28)'
      : theme.colors.border.secondary};
  border-radius: ${theme.borderRadius.xl};
  background: ${theme.colors.bg.primary};
  box-shadow: ${theme.shadows.sm};
`;

const SummaryTop = styled.div`
  display: flex;
  justify-content: space-between;
  gap: ${theme.spacing.sm};
  color: ${theme.colors.text.secondary};
  font-size: ${theme.typography.fontSize.sm};
`;

const SummaryValue = styled.strong<{ $tone?: 'danger' | 'warning' | 'info' }>`
  font-size: ${theme.typography.fontSize['3xl']};
  line-height: 1;
  color: ${(props) => props.$tone === 'danger'
    ? theme.colors.error
    : props.$tone === 'warning'
      ? theme.colors.warning
      : theme.colors.primary};
`;

const SummaryHint = styled.span`
  color: ${theme.colors.text.tertiary};
  font-size: ${theme.typography.fontSize.xs};
  line-height: 1.5;
`;

const ContentGrid = styled.div`
  display: grid;
  grid-template-columns: minmax(0, 1.4fr) minmax(320px, 0.6fr);
  gap: ${theme.spacing.base};
  align-items: start;

  @media (max-width: ${theme.breakpoints.xl}) {
    grid-template-columns: 1fr;
  }
`;

const Queue = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${theme.spacing.sm};
`;

const QueueItem = styled.div<{ $tone: 'danger' | 'warning' | 'info' }>`
  display: grid;
  grid-template-columns: 8px minmax(0, 1fr) auto;
  align-items: center;
  gap: ${theme.spacing.base};
  padding: ${theme.spacing.base};
  border: 1px solid ${theme.colors.border.tertiary};
  border-radius: ${theme.borderRadius.base};
  background: ${theme.colors.bg.secondary};

  &::before {
    content: '';
    width: 8px;
    height: 36px;
    border-radius: 999px;
    background: ${(props) => props.$tone === 'danger'
      ? theme.colors.error
      : props.$tone === 'warning'
        ? theme.colors.warning
        : theme.colors.primary};
  }

  @media (max-width: ${theme.breakpoints.sm}) {
    grid-template-columns: 8px minmax(0, 1fr);

    > button {
      grid-column: 2;
      justify-self: start;
    }
  }
`;

const QueueCopy = styled.div`
  min-width: 0;

  strong {
    display: block;
    margin-bottom: 4px;
    color: ${theme.colors.text.primary};
    overflow-wrap: anywhere;
  }

  span {
    display: block;
    color: ${theme.colors.text.tertiary};
    font-size: ${theme.typography.fontSize.sm};
    line-height: 1.5;
    overflow-wrap: anywhere;
  }
`;

const CardToolbar = styled.div`
  display: flex;
  justify-content: flex-end;
  margin-bottom: ${theme.spacing.sm};
`;

const ActivityList = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${theme.spacing.sm};
`;

const ActivityItem = styled.div`
  padding: ${theme.spacing.sm} 0;
  border-bottom: 1px solid ${theme.colors.border.tertiary};

  &:last-child { border-bottom: 0; }
  strong { display: block; margin-bottom: 3px; color: ${theme.colors.text.primary}; }
  span { color: ${theme.colors.text.tertiary}; font-size: ${theme.typography.fontSize.sm}; line-height: 1.5; }
`;

const HealthRow = styled.div`
  display: flex;
  justify-content: space-between;
  gap: ${theme.spacing.base};
  padding: ${theme.spacing.sm} 0;
  border-bottom: 1px solid ${theme.colors.border.tertiary};

  &:last-child { border-bottom: 0; }
`;

const CRITICAL_SEVERITY = new Set(['P0', 'P1', 'CRITICAL', 'HIGH', 'SEV0', 'SEV1']);
const CLOSED_INCIDENT = new Set(['RESOLVED', 'CLOSED']);
const upper = (value?: string) => String(value || '').trim().toUpperCase();

const attentionLabel = (item: AttentionItem) => ({
  ACTION_REQUIRED: 'Action Required',
  CRITICAL_INCIDENT: 'P0/P1 Incident',
  PENDING_DECISION: 'Pending Decision',
  FAILED_CHANGE: 'Execution Failure',
  READINESS_BLOCKER: 'Readiness Blocker',
  PENDING_OPERATION: 'Pending Operation',
  AUDIT_REMINDER: 'Audit Reminder',
}[item.kind]);

export const OpsDashboardPage: React.FC = () => {
  const navigate = useNavigate();
  const admin = isAdminUser();
  const overviewQuery = useDashboardOverviewQuery(admin);
  const loading = overviewQuery.isLoading;
  const error = overviewQuery.isError
    ? (overviewQuery.error instanceof Error ? overviewQuery.error.message : '工作台数据加载失败')
    : '';
  const adminOverview = overviewQuery.data?.adminOverview || null;
  const userOverview = overviewQuery.data?.userOverview || null;

  const projects = useMemo(
    () => (adminOverview?.projects || []) as OpsProjectWorkspace[],
    [adminOverview],
  );
  const changes = useMemo(
    () => (adminOverview?.recentChanges || []) as OpsChangePackage[],
    [adminOverview],
  );
  const readinessBlockers = useMemo(() => projects.filter(projectNeedsAttention), [projects]);
  const attentionItems = useMemo(
    () => admin
      ? buildAdminAttentionItems(adminOverview, projects, changes)
      : buildUserAttentionItems(userOverview),
    [admin, adminOverview, projects, changes, userOverview],
  );

  const criticalIncidentCount = useMemo(() => {
    const incidents = admin ? adminOverview?.recentIncidents || [] : userOverview?.currentIncidents || [];
    return incidents.filter((incident) =>
      !CLOSED_INCIDENT.has(upper(incident.status)) && CRITICAL_SEVERITY.has(upper(incident.severity))).length;
  }, [admin, adminOverview, userOverview]);

  const summary = admin
    ? [
        {
          label: 'Action Required',
          value: adminOverview?.actionRequiredIncidentCount ?? 0,
          hint: `${adminOverview?.unownedActionRequiredIncidentCount ?? 0} 个尚未分配责任人`,
          tone: 'danger' as const,
          icon: <IconAlertTriangle />,
        },
        {
          label: 'P0 / P1 Incident',
          value: criticalIncidentCount,
          hint: '当前高严重度且尚未关闭的问题',
          tone: 'warning' as const,
          icon: <IconShield />,
        },
        {
          label: 'Pending Decision',
          value: (adminOverview?.pendingChangeCount ?? 0) + (adminOverview?.pendingWorkflowDecisionCount ?? 0),
          hint: 'ChangePackage 审批 + Workflow Human Decision',
          tone: 'warning' as const,
          icon: <IconClock />,
        },
        {
          label: 'Failed / Blocked',
          value: (adminOverview?.failedChangeCount ?? 0) + readinessBlockers.length,
          hint: 'Landing / Verification failure 与 Project Readiness blocker',
          tone: 'danger' as const,
          icon: <IconAlertTriangle />,
        },
      ]
    : [
        {
          label: '需要我处理',
          value: userOverview?.actionRequiredIncidents?.length ?? 0,
          hint: '已形成明确人工动作的 Incident',
          tone: 'danger' as const,
          icon: <IconAlertTriangle />,
        },
        {
          label: 'P0 / P1 Incident',
          value: criticalIncidentCount,
          hint: '当前授权范围内的高严重度问题',
          tone: 'warning' as const,
          icon: <IconShield />,
        },
        {
          label: '待我决策',
          value: (userOverview?.pendingApprovals?.length ?? 0) + (userOverview?.pendingWorkflowDecisions?.length ?? 0),
          hint: 'ChangePackage 审批 + Workflow Human Decision',
          tone: 'warning' as const,
          icon: <IconClock />,
        },
        {
          label: '待我执行 / 验证',
          value: userOverview?.pendingOperations?.length ?? 0,
          hint: '已审批或恢复验证阶段需要你的任务',
          tone: 'info' as const,
          icon: <IconBranch />,
        },
      ];

  const health = adminOverview?.capabilityHealth || {};
  const healthChecks = Array.isArray(health.checks) ? health.checks : [];

  return (
    <OpsPageShell selectedKey="dashboard">
      <OpsPageHeader
        title="Attention & Action"
        description="首页只回答一个问题：现在最值得我处理什么？高风险事件、人工决策、失败处置和 Readiness blocker 会按优先级排在最前。"
        primaryAction={<Button icon={<IconBranch />} onClick={() => navigate('/chat')}>AI 对话</Button>}
        extra={admin && <Button onClick={() => navigate('/incidents')}>Incident Center</Button>}
      />

      <Spin spinning={loading}>
        {error && (
          <OpsSectionCard title="工作台数据不可用">
            <Text type="danger">{error}</Text>
          </OpsSectionCard>
        )}

        <SummaryGrid>
          {summary.map((item) => (
            <SummaryCard key={item.label} $tone={item.tone}>
              <SummaryTop><span>{item.label}</span>{item.icon}</SummaryTop>
              <SummaryValue $tone={item.tone}>{item.value}</SummaryValue>
              <SummaryHint>{item.hint}</SummaryHint>
            </SummaryCard>
          ))}
        </SummaryGrid>

        <ContentGrid>
          <OpsSectionCard title="现在最值得处理">
            <CardToolbar>
              <Button icon={<IconHistory />} onClick={() => navigate('/executions')}>执行中心</Button>
            </CardToolbar>
            {attentionItems.length ? (
              <Queue>
                {attentionItems.slice(0, 14).map((item) => (
                  <QueueItem key={item.key} $tone={item.tone}>
                    <QueueCopy>
                      <Text type={item.tone === 'danger' ? 'danger' : item.tone === 'warning' ? 'warning' : 'tertiary'}>
                        {attentionLabel(item)}
                      </Text>
                      <strong>{item.title}</strong>
                      <span>{item.detail}</span>
                    </QueueCopy>
                    <Button type={item.priority <= 20 ? 'primary' : 'tertiary'} onClick={() => navigate(item.href)}>
                      {item.actionLabel}
                    </Button>
                  </QueueItem>
                ))}
              </Queue>
            ) : (
              <Empty
                title="当前没有阻塞事项"
                description="没有待人工动作、高严重度 Incident、待决策方案或失败处置。可以从 AI 对话发起新的诊断。"
              />
            )}
          </OpsSectionCard>

          <div style={{ display: 'flex', flexDirection: 'column', gap: theme.spacing.base }}>
            <OpsSectionCard title="Recent Activity">
              {admin ? (
                (adminOverview?.recentIncidents?.length || changes.length) ? (
                  <ActivityList>
                    {(adminOverview?.recentIncidents || []).slice(0, 4).map((incident) => (
                      <ActivityItem key={`activity-incident-${incident.incidentId}`}>
                        <strong>{incident.title || incident.incidentId}</strong>
                        <span>{incident.projectId || '-'} · {incident.status} · {incident.updateTime || incident.lastSeenAt || '-'}</span>
                      </ActivityItem>
                    ))}
                    {changes.slice(0, 4).map((pkg) => (
                      <ActivityItem key={`activity-change-${pkg.packageId}`}>
                        <strong>{pkg.objective || pkg.summary || pkg.packageId}</strong>
                        <span>{pkg.projectId || '-'} · {pkg.status} · ChangePackage</span>
                      </ActivityItem>
                    ))}
                  </ActivityList>
                ) : <Empty title="暂无最近活动" />
              ) : (
                userOverview?.recentSessions?.length ? (
                  <ActivityList>
                    {userOverview.recentSessions.slice(0, 6).map((session) => (
                      <ActivityItem key={session.sessionId}>
                        <strong>{session.title || session.sessionId}</strong>
                        <span>{session.projectId || '-'} · {session.lastActiveAt || session.createdAt || '-'}</span>
                      </ActivityItem>
                    ))}
                  </ActivityList>
                ) : <Empty title="暂无最近会话" />
              )}
            </OpsSectionCard>

            {admin && (
              <OpsSectionCard title="Platform Health">
                <CardToolbar>
                  <Button size="small" onClick={() => navigate('/platform/governance')}>查看治理</Button>
                </CardToolbar>
                <HealthRow><Text>Healthy capability</Text><Title heading={6} style={{ margin: 0 }}>{Number(health.healthyCount || 0)}</Title></HealthRow>
                <HealthRow><Text>Degraded capability</Text><Title heading={6} style={{ margin: 0 }}>{Number(health.degradedCount || 0)}</Title></HealthRow>
                <HealthRow><Text>Readiness blocker</Text><Title heading={6} style={{ margin: 0 }}>{readinessBlockers.length}</Title></HealthRow>
                {healthChecks.length > 0 && (
                  <Text type="tertiary">健康详情由 Resource Health 权威快照提供；首页只展示汇总，不复制平台治理控制面。</Text>
                )}
              </OpsSectionCard>
            )}
          </div>
        </ContentGrid>
      </Spin>
    </OpsPageShell>
  );
};
