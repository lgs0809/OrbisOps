import React, { useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import styled from 'styled-components';
import { Button, Empty, Spin, Typography } from '@douyinfe/semi-ui';
import { IconArrowRight, IconComment, IconHistory, IconRefresh } from '@douyinfe/semi-icons';

import { OpsPageShell } from '../components/ops-layout';
import {
  AttentionItem,
  buildAdminAttentionItems,
  buildUserAttentionItems,
  projectNeedsAttention,
} from '../features/dashboard/attention-center-model';
import { useDashboardOverviewQuery } from '../features/dashboard/api/dashboard-queries';
import { activationRateLabel, productActivationMetrics } from '../features/dashboard/activation-metrics-model';
import { getStoredUserInfo, isAdminUser } from '../services/auth-session';
import { OpsChangePackage } from '../services/ops-admin-service';
import { OpsProjectWorkspace } from '../services/ops-project-service';
import { theme } from '../styles/theme';
import { userFacingError } from '../utils/user-facing-error';

const { Text } = Typography;

const Page = styled.div`
  max-width: 1480px;
  margin: 0 auto;
`;

const Hero = styled.section`
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: end;
  gap: 24px;
  padding: 22px 0 28px;

  @media (max-width: ${theme.breakpoints.md}) {
    grid-template-columns: 1fr;
    align-items: start;
  }
`;

const Greeting = styled.div`
  h1 {
    margin: 0;
    color: ${theme.colors.text.primary};
    font-size: clamp(28px, 3vw, 34px);
    font-weight: 650;
    line-height: 1.08;
    letter-spacing: -0.045em;
  }

  p {
    max-width: 720px;
    margin: 10px 0 0;
    color: ${theme.colors.text.tertiary};
    font-size: 14px;
    line-height: 1.7;
  }
`;

const HeroActions = styled.div`
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
`;

const StatusStrip = styled.section`
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  margin-bottom: 18px;
  border: 1px solid ${theme.colors.border.secondary};
  border-radius: 13px;
  background: #fff;
  overflow: hidden;

  @media (max-width: ${theme.breakpoints.md}) {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  @media (max-width: ${theme.breakpoints.sm}) {
    grid-template-columns: 1fr;
  }
`;

const StatusItem = styled.div<{ $attention?: boolean }>`
  min-width: 0;
  padding: 15px 17px;
  border-right: 1px solid ${theme.colors.border.tertiary};

  &:last-child { border-right: 0; }

  @media (max-width: ${theme.breakpoints.md}) {
    &:nth-child(2) { border-right: 0; }
    &:nth-child(-n + 2) { border-bottom: 1px solid ${theme.colors.border.tertiary}; }
  }

  @media (max-width: ${theme.breakpoints.sm}) {
    border-right: 0;
    border-bottom: 1px solid ${theme.colors.border.tertiary};
    &:last-child { border-bottom: 0; }
  }

  .label {
    color: ${theme.colors.text.tertiary};
    font-size: 12px;
    font-weight: 600;
  }

  .value {
    display: block;
    margin-top: 5px;
    color: ${(props) => (props.$attention ? theme.colors.error : theme.colors.text.primary)};
    font-size: 25px;
    font-weight: 650;
    line-height: 1;
  }

  .hint {
    display: block;
    margin-top: 6px;
    overflow: hidden;
    color: ${theme.colors.text.tertiary};
    font-size: 12px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
`;

const MainGrid = styled.div`
  display: grid;
  grid-template-columns: minmax(0, 1.45fr) minmax(300px, 0.55fr);
  gap: 16px;
  align-items: start;

  @media (max-width: 1100px) {
    grid-template-columns: 1fr;
  }
`;

const Panel = styled.section`
  min-width: 0;
  border: 1px solid ${theme.colors.border.secondary};
  border-radius: 13px;
  background: #fff;
  overflow: hidden;
`;

const PanelHeader = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 15px 17px;
  border-bottom: 1px solid ${theme.colors.border.tertiary};

  strong,
  h2 {
    margin: 0;
    font-size: 14px;
    font-weight: 650;
  }

  span {
    display: block;
    margin-top: 2px;
    color: ${theme.colors.text.tertiary};
    font-size: 12px;
  }
`;

const Inbox = styled.div`
  min-height: 320px;
`;

const InboxItem = styled.button<{ $tone: 'danger' | 'warning' | 'info' }>`
  width: 100%;
  display: grid;
  grid-template-columns: 8px minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
  padding: 14px 17px;
  border: 0;
  border-bottom: 1px solid ${theme.colors.border.tertiary};
  background: #fff;
  color: inherit;
  text-align: left;
  cursor: pointer;

  &:last-child { border-bottom: 0; }
  &:hover { background: #fafbfc; }

  &::before {
    content: '';
    width: 8px;
    height: 8px;
    border-radius: 50%;
    background: ${(props) => props.$tone === 'danger' ? theme.colors.error : props.$tone === 'warning' ? theme.colors.warning : theme.colors.primary};
  }

  .copy { min-width: 0; }
  .kind { color: ${theme.colors.text.tertiary}; font-size: 12px; font-weight: 600; }
  strong { display: block; margin-top: 3px; font-size: 13px; font-weight: 600; }
  .detail { display: block; margin-top: 3px; color: ${theme.colors.text.tertiary}; font-size: 12px; line-height: 1.45; }
  .action { color: ${theme.colors.text.secondary}; font-size: 12px; white-space: nowrap; }
`;

const RightStack = styled.div`
  display: flex;
  flex-direction: column;
  gap: 16px;
`;

const ActivityItem = styled.button`
  width: 100%;
  display: block;
  padding: 12px 17px;
  border: 0;
  border-bottom: 1px solid ${theme.colors.border.tertiary};
  background: #fff;
  color: inherit;
  text-align: left;
  cursor: pointer;

  &:last-child { border-bottom: 0; }
  &:hover { background: #fafbfc; }
  strong { display: block; overflow: hidden; font-size: 12px; font-weight: 600; text-overflow: ellipsis; white-space: nowrap; }
  span { display: block; margin-top: 3px; color: ${theme.colors.text.tertiary}; font-size: 12px; line-height: 1.45; }
`;

const MetricList = styled.div`
  padding: 4px 17px 12px;
`;

const MetricRow = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 9px 0;
  border-bottom: 1px solid ${theme.colors.border.tertiary};

  &:last-child { border-bottom: 0; }
  span { color: ${theme.colors.text.secondary}; font-size: 12px; }
  strong { font-size: 13px; font-weight: 650; }
`;

const CRITICAL_SEVERITY = new Set(['P0', 'P1', 'CRITICAL', 'HIGH', 'SEV0', 'SEV1']);
const CLOSED_INCIDENT = new Set(['RESOLVED', 'CLOSED']);
const upper = (value?: string) => String(value || '').trim().toUpperCase();

const activityStatusLabel = (status?: string) => ({
  ACTION_REQUIRED: '需要处理', INVESTIGATING: '排查中', VERIFYING: '验证中', RESOLVED: '已解决', CLOSED: '已关闭',
  DRAFT: '草稿', READY_FOR_REVIEW: '待提交审批', REVIEWING: '审批中', APPROVED: '已批准', LANDING_RUNNING: '执行中',
  LANDED: '已执行', VERIFIED: '已验证', VALIDATION_FAILED: '校验失败', LANDING_FAILED: '执行失败',
  VERIFICATION_FAILED: '验证失败', NEEDS_REPLAN: '需要重新规划',
}[upper(status)] || '状态已更新');

const attentionLabel = (item: AttentionItem) => ({
  ACTION_REQUIRED: '需要处理', CRITICAL_INCIDENT: '高优先级事件', PENDING_DECISION: '待决策', FAILED_CHANGE: '变更执行失败',
  READINESS_BLOCKER: '项目配置阻塞', PENDING_OPERATION: '待执行操作', AUDIT_REMINDER: '审计提醒',
}[item.kind]);

export const OpsDashboardPage: React.FC = () => {
  const navigate = useNavigate();
  const principal = getStoredUserInfo();
  const admin = isAdminUser();
  const overviewQuery = useDashboardOverviewQuery(admin);
  const loading = overviewQuery.isLoading;
  const error = overviewQuery.isError ? userFacingError(overviewQuery.error, '首页数据暂时不可用，请稍后重试。') : '';
  const adminOverview = overviewQuery.data?.adminOverview || null;
  const userOverview = overviewQuery.data?.userOverview || null;
  const activation = useMemo(() => productActivationMetrics(overviewQuery.data?.productMetrics), [overviewQuery.data?.productMetrics]);
  const projects = useMemo(() => (adminOverview?.projects || []) as OpsProjectWorkspace[], [adminOverview]);
  const changes = useMemo(() => (adminOverview?.recentChanges || []) as OpsChangePackage[], [adminOverview]);
  const readinessBlockers = useMemo(() => projects.filter(projectNeedsAttention), [projects]);
  const attentionItems = useMemo(
    () => admin ? buildAdminAttentionItems(adminOverview, projects, changes) : buildUserAttentionItems(userOverview),
    [admin, adminOverview, projects, changes, userOverview],
  );
  const criticalIncidentCount = useMemo(() => {
    const incidents = admin ? adminOverview?.recentIncidents || [] : userOverview?.currentIncidents || [];
    return incidents.filter((incident) => !CLOSED_INCIDENT.has(upper(incident.status)) && CRITICAL_SEVERITY.has(upper(incident.severity))).length;
  }, [admin, adminOverview, userOverview]);

  const summary = admin
    ? [
        { label: '需要处理', value: adminOverview?.actionRequiredIncidentCount ?? 0, hint: `${adminOverview?.unownedActionRequiredIncidentCount ?? 0} 项仍未分配负责人`, attention: true },
        { label: '高优先级事件', value: criticalIncidentCount, hint: '仍未关闭的高严重度事件', attention: criticalIncidentCount > 0 },
        { label: '待决策', value: (adminOverview?.pendingChangeCount ?? 0) + (adminOverview?.pendingWorkflowDecisionCount ?? 0), hint: '变更审批与工作流人工决策', attention: false },
        { label: '失败 / 阻塞', value: (adminOverview?.failedChangeCount ?? 0) + readinessBlockers.length, hint: '执行失败或项目尚未就绪', attention: true },
      ]
    : [
        { label: '需要处理', value: userOverview?.actionRequiredIncidents?.length ?? 0, hint: '需要你显式处理的事项', attention: true },
        { label: '高优先级事件', value: criticalIncidentCount, hint: '仍未关闭的高严重度事件', attention: criticalIncidentCount > 0 },
        { label: '待决策', value: (userOverview?.pendingApprovals?.length ?? 0) + (userOverview?.pendingWorkflowDecisions?.length ?? 0), hint: '变更审批与工作流人工决策', attention: false },
        { label: '待执行操作', value: userOverview?.pendingOperations?.length ?? 0, hint: '已批准但仍需处理的操作', attention: false },
      ];

  const health = adminOverview?.capabilityHealth || {};

  return (
    <OpsPageShell selectedKey="home">
      <Page>
        <Hero>
          <Greeting>
            <h1>首页</h1>
            <p>{principal.username ? `${principal.username}，` : ''}这里集中显示真正需要你关注的运行、决策和项目阻塞。没有待办时，直接从对话开始新的排查。</p>
          </Greeting>
          <HeroActions>
            <Button icon={<IconHistory />} onClick={() => navigate('/workbench')}>查看运行</Button>
            <Button theme="solid" type="primary" icon={<IconComment />} onClick={() => navigate('/chat')}>开始对话</Button>
          </HeroActions>
        </Hero>

        <Spin spinning={loading}>
          {loading ? (
            <Panel role="status" aria-live="polite" style={{ padding: 40 }}>
              <strong>正在读取首页数据…</strong>
              <p>运行、决策和健康摘要会在读取完成后显示。</p>
            </Panel>
          ) : error ? (
            <Panel style={{ marginBottom: 16, padding: 16 }}>
              <Text type="danger">{error}</Text>
              <Button size="small" icon={<IconRefresh />} onClick={() => void overviewQuery.refetch()} style={{ marginLeft: 12 }}>重试</Button>
            </Panel>
          ) : <>

          <StatusStrip>
            {summary.map((item) => (
              <StatusItem key={item.label} $attention={item.attention && Number(item.value) > 0}>
                <span className="label">{item.label}</span>
                <strong className="value">{item.value}</strong>
                <span className="hint">{item.hint}</span>
              </StatusItem>
            ))}
          </StatusStrip>

          <MainGrid>
            <Panel>
              <PanelHeader>
                <div><h2>需要关注</h2><span>{attentionItems.length ? `${attentionItems.length} 项按优先级排序` : '当前没有阻塞事项'}</span></div>
                <Button size="small" theme="borderless" onClick={() => navigate('/workbench')}>全部运行</Button>
              </PanelHeader>
              <Inbox>
                {attentionItems.length ? attentionItems.slice(0, 14).map((item) => (
                  <InboxItem key={item.key} type="button" $tone={item.tone} onClick={() => navigate(item.href)}>
                    <div className="copy">
                      <span className="kind">{attentionLabel(item)}</span>
                      <strong>{item.title}</strong>
                      <span className="detail">{item.detail}</span>
                    </div>
                    <span className="action">{item.actionLabel} <IconArrowRight /></span>
                  </InboxItem>
                )) : (
                  <Empty title="当前没有需要处理的事项" description="可以直接开始新的对话或查看已有运行。" style={{ padding: '64px 20px' }} />
                )}
              </Inbox>
            </Panel>

            <RightStack>
              <Panel>
                <PanelHeader><div><strong>最近活动</strong><span>最近更新的运行与变更</span></div></PanelHeader>
                {admin ? (
                  (adminOverview?.recentIncidents?.length || changes.length) ? (
                    <>
                      {(adminOverview?.recentIncidents || []).slice(0, 4).map((incident) => (
                        <ActivityItem key={`activity-incident-${incident.incidentId}`} type="button" onClick={() => navigate('/workbench')}>
                          <strong>{incident.title || incident.incidentId}</strong>
                          <span>{incident.projectId || '-'} · {activityStatusLabel(incident.status)} · {incident.updateTime || incident.lastSeenAt || '-'}</span>
                        </ActivityItem>
                      ))}
                      {changes.slice(0, 3).map((pkg) => (
                        <ActivityItem key={`activity-change-${pkg.packageId}`} type="button" onClick={() => navigate('/changes')}>
                          <strong>{pkg.objective || pkg.summary || pkg.packageId}</strong>
                          <span>{pkg.projectId || '-'} · {activityStatusLabel(pkg.status)} · 变更</span>
                        </ActivityItem>
                      ))}
                    </>
                  ) : <Empty title="暂无最近活动" style={{ padding: 32 }} />
                ) : userOverview?.recentSessions?.length ? (
                  userOverview.recentSessions.slice(0, 6).map((session) => (
                    <ActivityItem key={session.sessionId} type="button" onClick={() => navigate(`/chat?sessionId=${encodeURIComponent(session.sessionId)}`)}>
                      <strong>{session.title || session.sessionId}</strong>
                      <span>{session.projectId || '-'} · {session.lastActiveAt || session.createdAt || '-'}</span>
                    </ActivityItem>
                  ))
                ) : <Empty title="暂无最近对话" style={{ padding: 32 }} />}
              </Panel>

              {admin && activation && (
                <Panel data-testid="project-activation">
                  <PanelHeader><div><strong>项目激活</strong><span>从接入到可诊断的真实完成度</span></div><Button size="small" theme="borderless" onClick={() => navigate('/projects')}>项目</Button></PanelHeader>
                  <MetricList>
                    <MetricRow><span>项目数量</span><strong>{activation.projectCount}</strong></MetricRow>
                    <MetricRow><span>已连接证据源</span><strong>{activation.evidenceConnectedProjects}</strong></MetricRow>
                    <MetricRow><span>真实查询证据</span><strong>{activation.queryProofVerifiedProjects}</strong></MetricRow>
                    <MetricRow><span>默认助手就绪</span><strong>{activation.defaultAgentReadyProjects}</strong></MetricRow>
                    <MetricRow><span>当前可诊断</span><strong>{activation.diagnosisReadyProjects}</strong></MetricRow>
                    <MetricRow><span>激活率</span><strong>{activationRateLabel(activation)}</strong></MetricRow>
                  </MetricList>
                </Panel>
              )}

              {admin && (
                <Panel>
                  <PanelHeader><div><strong>平台健康</strong><span>能力层健康摘要</span></div><Button size="small" theme="borderless" onClick={() => navigate('/settings/governance')}>治理</Button></PanelHeader>
                  <MetricList>
                    <MetricRow><span>健康能力</span><strong>{Number(health.healthyCount || 0)}</strong></MetricRow>
                    <MetricRow><span>降级能力</span><strong>{Number(health.degradedCount || 0)}</strong></MetricRow>
                    <MetricRow><span>项目配置阻塞</span><strong>{readinessBlockers.length}</strong></MetricRow>
                  </MetricList>
                </Panel>
              )}
            </RightStack>
          </MainGrid>
          </>}
        </Spin>
      </Page>
    </OpsPageShell>
  );
};
