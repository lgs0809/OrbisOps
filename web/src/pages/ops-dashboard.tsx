import React, { useEffect, useMemo, useState } from 'react';

import styled from 'styled-components';
import { Button, Empty, Spin, Typography } from '@douyinfe/semi-ui';
import { IconAlertTriangle, IconBranch, IconClock, IconFolder, IconHistory, IconShield } from '@douyinfe/semi-icons';
import { useNavigate } from 'react-router-dom';

import { theme } from '../styles/theme';
import { isAdminUser } from '../services/auth-session';
import { OpsProjectWorkspace } from '../services/ops-project-service';
import { OpsAdminDashboardOverview, OpsChangePackage, opsAdminService } from '../services/ops-admin-service';
import { opsUserService, UserDashboardOverview } from '../services/ops-user-service';
import { OpsPageHeader, OpsPageShell } from '../components/ops-layout';

const { Text } = Typography;

const StatGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(4, minmax(180px, 1fr));
  gap: ${theme.spacing.base};
  margin-bottom: ${theme.spacing.lg};

  @media (max-width: ${theme.breakpoints.xl}) {
    grid-template-columns: repeat(2, minmax(180px, 1fr));
  }

  @media (max-width: ${theme.breakpoints.sm}) {
    grid-template-columns: 1fr;
  }
`;

const StatCard = styled.div<{ $tone?: 'info' | 'warning' | 'danger' | 'success' }>`
  min-height: 118px;
  padding: ${theme.spacing.base};
  background: ${theme.colors.bg.primary};
  border: 1px solid
    ${(props) => {
      if (props.$tone === 'danger') return 'rgba(220, 38, 38, 0.26)';
      if (props.$tone === 'warning') return 'rgba(217, 119, 6, 0.28)';
      if (props.$tone === 'success') return 'rgba(15, 159, 110, 0.24)';
      return theme.colors.border.secondary;
    }};
  border-radius: ${theme.borderRadius.xl};
  box-shadow: ${theme.shadows.sm};
  display: flex;
  flex-direction: column;
  justify-content: space-between;
`;

const StatTop = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: ${theme.spacing.base};
`;

const StatLabel = styled.span`
  color: ${theme.colors.text.secondary};
  font-size: ${theme.typography.fontSize.sm};
`;

const StatValue = styled.strong<{ $tone?: 'info' | 'warning' | 'danger' | 'success' }>`
  color: ${(props) => {
    if (props.$tone === 'danger') return theme.colors.error;
    if (props.$tone === 'warning') return theme.colors.warning;
    if (props.$tone === 'success') return theme.colors.success;
    return theme.colors.primary;
  }};
  font-size: ${theme.typography.fontSize['3xl']};
  line-height: 1;
`;

const StatHint = styled.span`
  color: ${theme.colors.text.tertiary};
  font-size: ${theme.typography.fontSize.xs};
`;

const BoardGrid = styled.div`
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(360px, 0.8fr);
  gap: ${theme.spacing.base};

  @media (max-width: ${theme.breakpoints.xl}) {
    grid-template-columns: 1fr;
  }
`;

const Panel = styled.section`
  background: ${theme.colors.bg.primary};
  border: 1px solid ${theme.colors.border.secondary};
  border-radius: ${theme.borderRadius.xl};
  box-shadow: ${theme.shadows.sm};
  padding: ${theme.spacing.base};
`;

const PanelHeader = styled.div`
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: ${theme.spacing.base};
  padding-bottom: ${theme.spacing.base};
  border-bottom: 1px solid ${theme.colors.border.tertiary};
  margin-bottom: ${theme.spacing.base};

  h3 {
    margin: 0 0 4px;
    color: ${theme.colors.text.primary};
    font-size: ${theme.typography.fontSize.base};
  }

  p {
    margin: 0;
    color: ${theme.colors.text.tertiary};
    font-size: ${theme.typography.fontSize.sm};
  }
`;

const TaskList = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${theme.spacing.sm};
`;

const TaskItem = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: ${theme.spacing.base};
  padding: ${theme.spacing.base};
  border: 1px solid ${theme.colors.border.tertiary};
  border-radius: ${theme.borderRadius.base};
  background: ${theme.colors.bg.secondary};
`;

const TaskCopy = styled.div`
  min-width: 0;

  strong {
    display: block;
    color: ${theme.colors.text.primary};
    margin-bottom: 2px;
  }

  span {
    color: ${theme.colors.text.tertiary};
    font-size: ${theme.typography.fontSize.sm};
  }
`;

const Badge = styled.span<{ $tone?: 'info' | 'warning' | 'danger' | 'success' }>`
  display: inline-flex;
  align-items: center;
  height: 24px;
  padding: 0 ${theme.spacing.sm};
  border-radius: 999px;
  white-space: nowrap;
  font-size: ${theme.typography.fontSize.xs};
  color: ${(props) => {
    if (props.$tone === 'danger') return theme.colors.error;
    if (props.$tone === 'warning') return theme.colors.warning;
    if (props.$tone === 'success') return theme.colors.success;
    return theme.colors.primary;
  }};
  background: ${(props) => {
    if (props.$tone === 'danger') return 'rgba(220, 38, 38, 0.08)';
    if (props.$tone === 'warning') return 'rgba(217, 119, 6, 0.1)';
    if (props.$tone === 'success') return 'rgba(15, 159, 110, 0.1)';
    return 'rgba(37, 99, 235, 0.08)';
  }};
  border: 1px solid
    ${(props) => {
      if (props.$tone === 'danger') return 'rgba(220, 38, 38, 0.18)';
      if (props.$tone === 'warning') return 'rgba(217, 119, 6, 0.2)';
      if (props.$tone === 'success') return 'rgba(15, 159, 110, 0.18)';
      return 'rgba(37, 99, 235, 0.16)';
    }};
`;

const Flow = styled.div`
  display: grid;
  grid-template-columns: repeat(5, minmax(120px, 1fr));
  gap: ${theme.spacing.sm};

  @media (max-width: ${theme.breakpoints.md}) {
    grid-template-columns: 1fr;
  }
`;

const FlowStep = styled.div`
  padding: ${theme.spacing.base};
  border-radius: ${theme.borderRadius.base};
  border: 1px dashed ${theme.colors.border.primary};
  background: ${theme.colors.bg.secondary};

  strong {
    display: block;
    margin-bottom: 4px;
    color: ${theme.colors.text.primary};
  }

  span {
    color: ${theme.colors.text.tertiary};
    font-size: ${theme.typography.fontSize.sm};
  }
`;

const DashboardEmpty = styled(Empty)`
  padding: ${theme.spacing.lg} 0;
`;

const ERROR_STATUSES = new Set(['NEEDS_REPLAN', 'VALIDATION_FAILED', 'LANDING_FAILED']);
const RUNNING_STATUSES = new Set(['LANDING', 'LANDING_RUNNING']);
const APPROVAL_STATUSES = new Set(['REVIEWING', 'REVISING', 'APPROVED']);

const isProjectIncomplete = (project: OpsProjectWorkspace) =>
  project.readyForInvestigation === false ||
  project.onboarding?.some((item) => !item.optional && !item.completed) ||
  !project.defaultAgentId;

const statusTone = (status?: string): 'info' | 'warning' | 'danger' | 'success' => {
  if (!status) return 'info';
  if (ERROR_STATUSES.has(status)) return 'danger';
  if (RUNNING_STATUSES.has(status)) return 'info';
  if (APPROVAL_STATUSES.has(status)) return 'warning';
  if (['SUCCEEDED', 'APPROVED', 'ROLLED_BACK'].includes(status)) return 'success';
  return 'info';
};

export const OpsDashboardPage: React.FC = () => {
  const navigate = useNavigate();
  const admin = isAdminUser();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [projects, setProjects] = useState<OpsProjectWorkspace[]>([]);
  const [changes, setChanges] = useState<OpsChangePackage[]>([]);
  const [adminOverview, setAdminOverview] = useState<OpsAdminDashboardOverview | null>(null);
  const [userOverview, setUserOverview] = useState<UserDashboardOverview | null>(null);
  const [productMetrics, setProductMetrics] = useState<Record<string, any> | null>(null);

  useEffect(() => {
    setLoading(true);
    setError('');
    const request = admin
      ? Promise.all([opsAdminService.adminDashboardOverview(), opsAdminService.productMetrics()]).then(([response, metricsResponse]) => {
          if (response.code !== '0000') {
            throw new Error(response.info || '管理员工作台加载失败');
          }
          setAdminOverview(response.data);
          setProjects((response.data?.projects || []) as OpsProjectWorkspace[]);
          setChanges((response.data?.recentChanges || []) as OpsChangePackage[]);
          setProductMetrics(metricsResponse.data || null);
          setUserOverview(null);
        })
      : opsUserService.dashboardOverview().then((response) => {
          setUserOverview(response.data);
          setAdminOverview(null);
          setProductMetrics(null);
          setProjects([]);
          setChanges([]);
        });
    request
      .catch((err: unknown) => {
        setError(err instanceof Error ? err.message : '工作台数据加载失败');
      })
      .finally(() => setLoading(false));
  }, [admin]);

  const incompleteProjects = useMemo(() => projects.filter(isProjectIncomplete), [projects]);
  const pendingApproval = useMemo(
    () => changes.filter((item) => APPROVAL_STATUSES.has(item.status)),
    [changes],
  );
  const runningChanges = useMemo(() => changes.filter((item) => RUNNING_STATUSES.has(item.status)), [changes]);
  const failedChanges = useMemo(() => changes.filter((item) => ERROR_STATUSES.has(item.status)), [changes]);
  const recentChanges = useMemo(() => changes.slice(0, 6), [changes]);

  return (
    <OpsPageShell selectedKey="dashboard">
          <OpsPageHeader
            title="工作台"
            description={admin
              ? '管理员在这里查看项目接入、待审批、执行中、失败介入和最近变更。'
              : '普通用户在这里查看可用项目、可用 Agent、最近对话、我的待处理执行和我的审计提醒。'}
            primaryAction={<Button icon={<IconBranch />} onClick={() => navigate('/chat')}>AI 对话</Button>}
            extra={admin && <Button type="primary" icon={<IconFolder />} onClick={() => navigate('/project-workspace')}>配置项目空间</Button>}
          />

          <Spin spinning={loading}>
            {error && (
              <Panel style={{ marginBottom: theme.spacing.base }}>
                <Text type="danger">工作台数据暂时不可用：{error}</Text>
              </Panel>
            )}

            {!admin && (
              <>
                <StatGrid>
                  <StatCard>
                    <StatTop>
                      <StatLabel>当前事件</StatLabel>
                      <IconAlertTriangle />
                    </StatTop>
                    <StatValue>{userOverview?.currentIncidents?.length ?? 0}</StatValue>
                    <StatHint>当前授权项目中尚未恢复或关闭的问题</StatHint>
                  </StatCard>
                  <StatCard $tone="warning">
                    <StatTop>
                      <StatLabel>需要我处理</StatLabel>
                      <IconClock />
                    </StatTop>
                    <StatValue $tone="warning">{userOverview?.actionRequiredIncidents?.length ?? 0}</StatValue>
                    <StatHint>诊断已形成下一步，需要人工决策或处置</StatHint>
                  </StatCard>
                  <StatCard $tone="warning">
                    <StatTop>
                      <StatLabel>我的待处理执行</StatLabel>
                      <IconShield />
                    </StatTop>
                    <StatValue $tone="warning">{userOverview?.pendingExecutions?.length ?? 0}</StatValue>
                    <StatHint>待审批、待执行或待恢复验证的安全处置</StatHint>
                  </StatCard>
                  <StatCard>
                    <StatTop>
                      <StatLabel>可用项目</StatLabel>
                      <IconFolder />
                    </StatTop>
                    <StatValue>{userOverview?.availableProjects?.length ?? 0}</StatValue>
                    <StatHint>当前账号可进入的业务系统范围</StatHint>
                  </StatCard>
                </StatGrid>

                <BoardGrid>
                  <Panel>
                    <PanelHeader>
                      <div>
                        <h3>当前事件</h3>
                        <p>先处理正在调查、等待动作或恢复验证的问题；没有事件时再从 AI 对话发起诊断。</p>
                      </div>
                    </PanelHeader>
                    {(userOverview?.currentIncidents?.length ?? 0) > 0 ? (
                      <TaskList>
                        {(userOverview?.currentIncidents || []).slice(0, 6).map((incident) => (
                          <TaskItem key={incident.incidentId}>
                            <TaskCopy>
                              <strong>{incident.title || incident.incidentId}</strong>
                              <span>{incident.projectId || '-'} · {incident.status || '-'} · {incident.ownerUserId ? `责任人 ${incident.ownerUserId}` : '未认领'} · {incident.serviceName || '未标注服务'}</span>
                            </TaskCopy>
                            <Button onClick={() => navigate(`/incidents?projectId=${encodeURIComponent(incident.projectId || '')}&incidentId=${encodeURIComponent(incident.incidentId)}`)}>处理</Button>
                          </TaskItem>
                        ))}
                      </TaskList>
                    ) : (
                      <TaskList>
                        <TaskItem>
                          <TaskCopy>
                            <strong>当前没有进行中的事件</strong>
                            <span>遇到生产问题时，从 AI 对话发起一次真实证据诊断。</span>
                          </TaskCopy>
                          <Button type="primary" onClick={() => navigate('/chat')}>开始诊断</Button>
                        </TaskItem>
                      </TaskList>
                    )}
                  </Panel>
                  <Panel>
                    <PanelHeader>
                      <div>
                        <h3>我的审批 / 执行 / 审计提醒</h3>
                        <p>工作队列只计算后端确认你有权限处理的任务，不把“可查看”误当成“待你处理”。</p>
                      </div>
                    </PanelHeader>
                    {(userOverview?.pendingApprovals?.length || userOverview?.pendingOperations?.length || userOverview?.auditReminders?.length) ? (
                      <TaskList>
                        {(userOverview?.pendingApprovals || []).slice(0, 3).map((pkg) => (
                          <TaskItem key={`approval-${pkg.packageId}`}>
                            <TaskCopy>
                              <strong>{pkg.title || pkg.packageId}</strong>
                              <span>{pkg.projectId || '-'} · 待你审批</span>
                            </TaskCopy>
                            <Button onClick={() => navigate(`/executions?projectId=${encodeURIComponent(pkg.projectId || '')}`)}>审批</Button>
                          </TaskItem>
                        ))}
                        {(userOverview?.pendingOperations || []).slice(0, 3).map((pkg) => (
                          <TaskItem key={`operation-${pkg.packageId}`}>
                            <TaskCopy>
                              <strong>{pkg.title || pkg.packageId}</strong>
                              <span>{pkg.projectId || '-'} · 待你执行或确认</span>
                            </TaskCopy>
                            <Button onClick={() => navigate(`/executions?projectId=${encodeURIComponent(pkg.projectId || '')}`)}>处理</Button>
                          </TaskItem>
                        ))}
                        {(userOverview?.auditReminders || []).slice(0, 3).map((audit) => (
                          <TaskItem key={`audit-${audit.id}`}>
                            <TaskCopy>
                              <strong>{audit.summary || `${audit.moduleName || '-'} / ${audit.actionName || '-'}`}</strong>
                              <span>{audit.projectId || '未关联项目'} · {audit.createdAt || '-'}</span>
                            </TaskCopy>
                            <Button onClick={() => navigate('/my-audit')}>查看</Button>
                          </TaskItem>
                        ))}
                      </TaskList>
                    ) : (
                      <DashboardEmpty
                        title="暂无个人提醒"
                        description="当你有待审批、待验证或导出记录时，会在这里展示。"
                      />
                    )}
                  </Panel>
                </BoardGrid>
              </>
            )}

            {admin && (
            <>
              <StatGrid>
              <StatCard $tone="success">
                <StatTop>
                  <StatLabel>本周有帮助 Incident</StatLabel>
                  <IconShield />
                </StatTop>
                <StatValue $tone="success">{productMetrics?.northStar?.weeklyHelpfulResolvedIncidents ?? 0}</StatValue>
                <StatHint>成功完成且由用户明确确认有帮助；不从模型结果自动推断</StatHint>
              </StatCard>
              <StatCard>
                <StatTop>
                  <StatLabel>当前事件</StatLabel>
                  <IconAlertTriangle />
                </StatTop>
                <StatValue>{adminOverview?.currentIncidentCount ?? 0}</StatValue>
                <StatHint>尚未恢复或关闭的 Incident</StatHint>
              </StatCard>
              <StatCard $tone="warning">
                <StatTop>
                  <StatLabel>需要人工动作</StatLabel>
                  <IconClock />
                </StatTop>
                <StatValue $tone="warning">{adminOverview?.actionRequiredIncidentCount ?? 0}</StatValue>
                <StatHint>诊断已形成处置/决策需求，其中 {adminOverview?.unownedActionRequiredIncidentCount ?? 0} 个尚未分配责任人</StatHint>
              </StatCard>
              <StatCard>
                <StatTop>
                  <StatLabel>AI 调查中</StatLabel>
                  <IconBranch />
                </StatTop>
                <StatValue>{adminOverview?.investigatingIncidentCount ?? 0}</StatValue>
                <StatHint>正在获取真实证据或综合诊断</StatHint>
              </StatCard>
              <StatCard>
                <StatTop>
                  <StatLabel>恢复验证中</StatLabel>
                  <IconShield />
                </StatTop>
                <StatValue>{adminOverview?.verifyingIncidentCount ?? 0}</StatValue>
                <StatHint>处置已落地，等待权威恢复证明</StatHint>
              </StatCard>
              <StatCard $tone="warning">
                <StatTop>
                  <StatLabel>待确认方案</StatLabel>
                  <IconClock />
                </StatTop>
                <StatValue $tone="warning">{adminOverview?.pendingChangeCount ?? pendingApproval.length}</StatValue>
                <StatHint>处置方案必须先确认再执行</StatHint>
              </StatCard>
              <StatCard>
                <StatTop>
                  <StatLabel>执行中方案</StatLabel>
                  <IconShield />
                </StatTop>
                <StatValue>{adminOverview?.runningChangeCount ?? runningChanges.length}</StatValue>
                <StatHint>已审批或正在生产执行</StatHint>
              </StatCard>
              <StatCard $tone="danger">
                <StatTop>
                  <StatLabel>执行失败需介入</StatLabel>
                  <IconAlertTriangle />
                </StatTop>
                <StatValue $tone="danger">{adminOverview?.failedChangeCount ?? failedChanges.length}</StatValue>
                <StatHint>验证失败、落地失败或需要重新规划</StatHint>
              </StatCard>
              <StatCard $tone="warning">
                <StatTop>
                  <StatLabel>待完善项目</StatLabel>
                  <IconFolder />
                </StatTop>
                <StatValue $tone="warning">{admin ? incompleteProjects.length : '-'}</StatValue>
                <StatHint>诊断或生产处置 Readiness 尚未闭环</StatHint>
              </StatCard>
              </StatGrid>

              <BoardGrid>
              <Panel>
                <PanelHeader>
                  <div>
                    <h3>下一步待处理</h3>
                    <p>优先展示会阻塞诊断或生产执行的事项。</p>
                  </div>
                  <Button icon={<IconHistory />} onClick={() => navigate('/executions')}>进入执行中心</Button>
                </PanelHeader>

                {!admin ? (
                  <TaskList>
                    <TaskItem>
                      <TaskCopy>
                        <strong>从 AI 对话开始诊断</strong>
                        <span>选择项目和 Agent 后发起问题描述，执行或审批事项会回到执行中心。</span>
                      </TaskCopy>
                      <Button type="primary" onClick={() => navigate('/chat')}>去对话</Button>
                    </TaskItem>
                    <TaskItem>
                      <TaskCopy>
                        <strong>查看执行中心</strong>
                          <span>确认待处理、执行中和失败需介入的处置方案。</span>
                      </TaskCopy>
                      <Button onClick={() => navigate('/executions')}>查看</Button>
                    </TaskItem>
                  </TaskList>
                ) : (adminOverview?.currentIncidentCount || 0) > 0 || incompleteProjects.length || failedChanges.length || pendingApproval.length ? (
                  <TaskList>
                    {(adminOverview?.recentIncidents || [])
                      .filter((incident) => !['RESOLVED', 'CLOSED'].includes(String(incident.status || '').toUpperCase()))
                      .slice(0, 4)
                      .map((incident) => (
                        <TaskItem key={`incident-${incident.incidentId}`}>
                          <TaskCopy>
                            <strong>{incident.title || incident.incidentId}</strong>
                            <span>{incident.projectId || '-'} · {incident.status || '-'} · {incident.summary || '等待形成可信诊断'}</span>
                          </TaskCopy>
                          <Button type={String(incident.status).toUpperCase() === 'ACTION_REQUIRED' ? 'primary' : 'tertiary'} onClick={() => navigate(`/incidents?projectId=${encodeURIComponent(incident.projectId || '')}&incidentId=${encodeURIComponent(incident.incidentId)}`)}>处理事件</Button>
                        </TaskItem>
                      ))}
                    {incompleteProjects.slice(0, 3).map((project) => (
                      <TaskItem key={`project-${project.projectId}`}>
                        <TaskCopy>
                          <strong>{project.name || project.projectId}</strong>
                          <span>项目能力未就绪：检查默认 Agent、数据连接、权限策略和已生成工具。</span>
                        </TaskCopy>
                        <Button onClick={() => navigate(`/project-workspace?projectId=${encodeURIComponent(project.projectId)}`)}>继续接入</Button>
                      </TaskItem>
                    ))}
                    {pendingApproval.slice(0, 3).map((plan) => (
                      <TaskItem key={`approval-${plan.packageId}`}>
                        <TaskCopy>
                          <strong>{plan.objective || plan.summary || plan.packageId}</strong>
                          <span>{plan.projectId} / {plan.targetEnvironment || '-'} · 等待确认</span>
                        </TaskCopy>
                        <Badge $tone="warning">{plan.status}</Badge>
                      </TaskItem>
                    ))}
                    {failedChanges.slice(0, 3).map((plan) => (
                      <TaskItem key={`failed-${plan.packageId}`}>
                        <TaskCopy>
                          <strong>{plan.objective || plan.summary || plan.packageId}</strong>
                          <span>{plan.projectId} / {plan.targetEnvironment || '-'} · 需要人工介入</span>
                        </TaskCopy>
                        <Badge $tone="danger">{plan.status}</Badge>
                      </TaskItem>
                    ))}
                  </TaskList>
                ) : (
                  <DashboardEmpty
                    title="暂无阻塞事项"
                    description="当前没有待完善项目、待确认方案或失败需介入任务。"
                  />
                )}
              </Panel>

              <Panel>
                <PanelHeader>
                  <div>
                    <h3>能力边界</h3>
                    <p>模板、项目能力、Agent 绑定和执行必须分开。</p>
                  </div>
                </PanelHeader>
                <Flow>
                  <FlowStep>
                    <strong>全局模板</strong>
                    <span>工具模板、复用经验模板、执行适配器。</span>
                  </FlowStep>
                  <FlowStep>
                    <strong>项目能力</strong>
                    <span>数据连接、权限策略、已生成工具。</span>
                  </FlowStep>
                  <FlowStep>
                    <strong>Agent 绑定</strong>
                    <span>Agent 只选择项目内可用能力。</span>
                  </FlowStep>
                  <FlowStep>
                    <strong>执行中心</strong>
                    <span>变更、审批、验证、回滚。</span>
                  </FlowStep>
                  <FlowStep>
                    <strong>审计回放</strong>
                    <span>记录配置、工具调用和执行轨迹。</span>
                  </FlowStep>
                </Flow>
              </Panel>
              </BoardGrid>

              <Panel style={{ marginTop: theme.spacing.base }}>
                <PanelHeader>
                  <div>
                    <h3>最近处置方案</h3>
                    <p>只展示摘要。执行详情、Dry Run 和回滚请进入执行中心。</p>
                  </div>
                </PanelHeader>
                {recentChanges.length ? (
                  <TaskList>
                    {recentChanges.map((plan) => (
                      <TaskItem key={plan.packageId}>
                        <TaskCopy>
                          <strong>{plan.objective || plan.summary || plan.packageId}</strong>
                          <span>{plan.projectId} / {plan.targetEnvironment || '-'} · 风险 {plan.riskLevel || '-'}</span>
                        </TaskCopy>
                        <Badge $tone={statusTone(plan.status)}>{plan.status}</Badge>
                      </TaskItem>
                    ))}
                  </TaskList>
                ) : (
                  <DashboardEmpty
                    title="暂无处置方案"
                    description="Agent 生成处置方案后，会在这里出现最近状态。"
                  />
                )}
              </Panel>
            </>
            )}
          </Spin>
    </OpsPageShell>
  );
};
