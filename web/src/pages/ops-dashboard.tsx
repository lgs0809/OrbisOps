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
import { activationRateLabel, productActivationMetrics } from '../features/dashboard/activation-metrics-model';
import { isAdminUser } from '../services/auth-session';
import { OpsChangePackage } from '../services/ops-admin-service';
import { OpsProjectWorkspace } from '../services/ops-project-service';
import { theme } from '../styles/theme';
import { userFacingError } from '../utils/user-facing-error';

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

const activityStatusLabel = (status?: string) => ({
  ACTION_REQUIRED: '需要处理',
  INVESTIGATING: '排查中',
  VERIFYING: '验证中',
  RESOLVED: '已解决',
  CLOSED: '已关闭',
  DRAFT: '草稿',
  READY_FOR_REVIEW: '待提交审批',
  REVIEWING: '审批中',
  APPROVED: '已批准',
  LANDING_RUNNING: '执行中',
  LANDED: '已执行',
  VERIFIED: '已验证',
  VALIDATION_FAILED: '校验失败',
  LANDING_FAILED: '执行失败',
  VERIFICATION_FAILED: '验证失败',
  NEEDS_REPLAN: '需要重新规划',
}[upper(status)] || '状态已更新');

const attentionLabel = (item: AttentionItem) => ({
  ACTION_REQUIRED: '需要处理',
  CRITICAL_INCIDENT: '高优先级事件',
  PENDING_DECISION: '待决策',
  FAILED_CHANGE: '变更执行失败',
  READINESS_BLOCKER: '项目配置阻塞',
  PENDING_OPERATION: '待执行操作',
  AUDIT_REMINDER: '审计提醒',
}[item.kind]);

export const OpsDashboardPage: React.FC = () => {
  const navigate = useNavigate();
  const admin = isAdminUser();
  const overviewQuery = useDashboardOverviewQuery(admin);
  const loading = overviewQuery.isLoading;
  const error = overviewQuery.isError
    ? userFacingError(overviewQuery.error, '首页数据暂时不可用，请稍后重试。')
    : '';
  const adminOverview = overviewQuery.data?.adminOverview || null;
  const userOverview = overviewQuery.data?.userOverview || null;
  const activation = useMemo(
    () => productActivationMetrics(overviewQuery.data?.productMetrics),
    [overviewQuery.data?.productMetrics],
  );

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
          label: '需要处理',
          value: adminOverview?.actionRequiredIncidentCount ?? 0,
          hint: `${adminOverview?.unownedActionRequiredIncidentCount ?? 0} 项仍未分配负责人`,
          tone: 'danger' as const,
          icon: <IconAlertTriangle />,
        },
        {
          label: '高优先级事件',
          value: criticalIncidentCount,
          hint: '仍未关闭的高严重度事件',
          tone: 'warning' as const,
          icon: <IconShield />,
        },
        {
          label: '待决策',
          value: (adminOverview?.pendingChangeCount ?? 0) + (adminOverview?.pendingWorkflowDecisionCount ?? 0),
          hint: '变更审批与 Workflow 人工决策',
          tone: 'warning' as const,
          icon: <IconClock />,
        },
        {
          label: '失败 / 阻塞',
          value: (adminOverview?.failedChangeCount ?? 0) + readinessBlockers.length,
          hint: '生产执行、验证失败或项目配置未就绪',
          tone: 'danger' as const,
          icon: <IconAlertTriangle />,
        },
      ]
    : [
        {
          label: '需要处理',
          value: userOverview?.actionRequiredIncidents?.length ?? 0,
          hint: '需要你显式处理的事件',
          tone: 'danger' as const,
          icon: <IconAlertTriangle />,
        },
        {
          label: '高优先级事件',
          value: criticalIncidentCount,
          hint: '你有权限查看的 Project 中仍未关闭的高严重度事件',
          tone: 'warning' as const,
          icon: <IconShield />,
        },
        {
          label: '待决策',
          value: (userOverview?.pendingApprovals?.length ?? 0) + (userOverview?.pendingWorkflowDecisions?.length ?? 0),
          hint: '变更审批与 Workflow 人工决策',
          tone: 'warning' as const,
          icon: <IconClock />,
        },
        {
          label: '待执行操作',
          value: userOverview?.pendingOperations?.length ?? 0,
          hint: '已批准但仍需要你处理的 Landing 或 Verification 工作',
          tone: 'info' as const,
          icon: <IconBranch />,
        },
      ];

  const health = adminOverview?.capabilityHealth || {};
  const healthChecks = Array.isArray(health.checks) ? health.checks : [];

  return (
    <OpsPageShell selectedKey="home">
      <OpsPageHeader
        title="首页"
        description="快速查看当前需要处理的任务、待决策事项、失败变更和项目配置问题。"
        primaryAction={<Button icon={<IconBranch />} onClick={() => navigate('/chat')}>打开对话</Button>}
        extra={<Button onClick={() => navigate('/workbench')}>打开工作台</Button>}
      />

      <Spin spinning={loading}>
        {error && (
          <OpsSectionCard title="首页数据不可用">
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
          <OpsSectionCard title="需要关注">
            <CardToolbar>
              <Button icon={<IconHistory />} onClick={() => navigate('/workbench')}>打开工作台</Button>
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
                description="目前没有待人工处理事项、高优先级事件、待决策或失败变更；需要时可以从对话发起新的排查。"
              />
            )}
          </OpsSectionCard>

          <div style={{ display: 'flex', flexDirection: 'column', gap: theme.spacing.base }}>
            <OpsSectionCard title="最近活动">
              {admin ? (
                (adminOverview?.recentIncidents?.length || changes.length) ? (
                  <ActivityList>
                    {(adminOverview?.recentIncidents || []).slice(0, 4).map((incident) => (
                      <ActivityItem key={`activity-incident-${incident.incidentId}`}>
                        <strong>{incident.title || incident.incidentId}</strong>
                        <span>{incident.projectId || '-'} · {activityStatusLabel(incident.status)} · {incident.updateTime || incident.lastSeenAt || '-'}</span>
                      </ActivityItem>
                    ))}
                    {changes.slice(0, 4).map((pkg) => (
                      <ActivityItem key={`activity-change-${pkg.packageId}`}>
                        <strong>{pkg.objective || pkg.summary || pkg.packageId}</strong>
                        <span>{pkg.projectId || '-'} · {activityStatusLabel(pkg.status)} · 变更</span>
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
                ) : <Empty title="暂无最近对话" />
              )}
            </OpsSectionCard>

            {admin && activation && (
              <div data-testid="project-activation">
                <OpsSectionCard title="项目激活情况">
                  <HealthRow><Text>项目数量</Text><Title heading={6} style={{ margin: 0 }}>{activation.projectCount}</Title></HealthRow>
                  <HealthRow><Text>已连接证据源</Text><Title heading={6} style={{ margin: 0 }}>{activation.evidenceConnectedProjects}</Title></HealthRow>
                  <HealthRow><Text>真实查询证据</Text><Title heading={6} style={{ margin: 0 }}>{activation.queryProofVerifiedProjects}</Title></HealthRow>
                  <HealthRow><Text>默认执行能力已就绪</Text><Title heading={6} style={{ margin: 0 }}>{activation.defaultAgentReadyProjects}</Title></HealthRow>
                  <HealthRow><Text>已激活</Text><Title heading={6} style={{ margin: 0 }}>{activation.activatedProjects}</Title></HealthRow>
                  <HealthRow><Text>当前可诊断</Text><Title heading={6} style={{ margin: 0 }}>{activation.diagnosisReadyProjects}</Title></HealthRow>
                  <HealthRow><Text>激活率</Text><Title heading={6} style={{ margin: 0 }}>{activationRateLabel(activation)}</Title></HealthRow>
                  <Text type="tertiary" size="small">
                    完成必要的资源连接、默认执行能力和基础配置后，项目才会计为已激活。
                  </Text>
                </OpsSectionCard>
              </div>
            )}

            {admin && (
              <OpsSectionCard title="平台健康">
                <CardToolbar>
                  <Button size="small" onClick={() => navigate('/settings/governance')}>打开治理与审计</Button>
                </CardToolbar>
                <HealthRow><Text>健康能力</Text><Title heading={6} style={{ margin: 0 }}>{Number(health.healthyCount || 0)}</Title></HealthRow>
                <HealthRow><Text>降级能力</Text><Title heading={6} style={{ margin: 0 }}>{Number(health.degradedCount || 0)}</Title></HealthRow>
                <HealthRow><Text>项目配置阻塞</Text><Title heading={6} style={{ margin: 0 }}>{readinessBlockers.length}</Title></HealthRow>
                {healthChecks.length > 0 && (
                  <Text type="tertiary">首页仅展示健康摘要；详细检查和治理操作请前往“治理与审计”。</Text>
                )}
              </OpsSectionCard>
            )}
          </div>
        </ContentGrid>
      </Spin>
    </OpsPageShell>
  );
};
