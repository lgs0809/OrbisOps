import React, { useMemo, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import styled from 'styled-components';
import { Button, Checkbox, Empty, Select, Space, Spin, Table, Tag, TextArea, Toast, Typography } from '@douyinfe/semi-ui';

import {
  OpsEvidenceCard,
  OpsEmptyState,
  OpsPageHeader,
  OpsPageShell,
  OpsSectionCard,
  OpsStatusBadge,
  OpsTimeline,
  TableScroll,
} from '../components/ops-layout';
import { ChangePackageSummaryCard } from '../features/changes/components/ChangePackageSummaryCard';
import { toChangePackageSummary } from '../features/changes/model/change-package-summary';
import { IncidentWorkspaceTabs, type IncidentWorkspaceTab } from '../features/incidents/components/IncidentWorkspaceTabs';
import { AlertCorrelationPanel } from '../features/incidents/components/AlertCorrelationPanel';
import { incidentEpoch, incidentLocalTime } from '../features/incidents/incident-time';
import { isAdminUser } from '../services/auth-session';
import type {
  OpsIncident,
  OpsIncidentTimelineItem,
} from '../services/ops-admin-service';
import {
  useIncidentCommandMutation,
  useIncidentDetailQuery,
  useIncidentMembersQuery,
  useIncidentsQuery,
} from '../features/incidents/api/incident-queries';
import { useProjectScope } from '../hooks/use-project-scope';
import { theme } from '../styles/theme';

const { Option } = Select;
const { Paragraph, Text, Title } = Typography;

const CURRENT = new Set(['OPEN', 'INVESTIGATING', 'ACTION_REQUIRED', 'REMEDIATING', 'VERIFYING']);
const HISTORY = new Set(['RESOLVED', 'CLOSED']);

const DetailGrid = styled.div`
  display: grid;
  grid-template-columns: minmax(0, 1.25fr) minmax(320px, 0.75fr);
  gap: ${theme.spacing.base};
  min-width: 0;

  @media (max-width: ${theme.breakpoints.lg}) {
    grid-template-columns: minmax(0, 1fr);
  }
`;

const Stack = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${theme.spacing.sm};
  min-width: 0;
`;

const FactCard = styled.div`
  padding: ${theme.spacing.sm} ${theme.spacing.base};
  border: 1px solid ${theme.colors.border.secondary};
  border-radius: ${theme.borderRadius.base};
  background: ${theme.colors.bg.secondary};
  overflow-wrap: anywhere;
`;

const Advanced = styled.details`
  margin-top: ${theme.spacing.base};
  padding: ${theme.spacing.base};
  border: 1px dashed ${theme.colors.border.secondary};
  border-radius: ${theme.borderRadius.base};
  background: ${theme.colors.bg.secondary};

  summary { cursor: pointer; font-weight: 600; }
  pre { white-space: pre-wrap; word-break: break-word; font-size: 12px; }
`;

const statusLabel = (status?: string) => ({
  OPEN: '待调查',
  INVESTIGATING: '调查中',
  ACTION_REQUIRED: '需要处置',
  REMEDIATING: '处置中',
  VERIFYING: '验证中',
  RESOLVED: '已恢复',
  CLOSED: '已关闭',
} as Record<string, string>)[String(status || '').toUpperCase()] || status || '未知';

const statusColor = (status?: string) => {
  const value = String(status || '').toUpperCase();
  if (value === 'RESOLVED') return 'green' as const;
  if (value === 'CLOSED') return 'grey' as const;
  if (value === 'ACTION_REQUIRED') return 'orange' as const;
  if (value === 'REMEDIATING' || value === 'VERIFYING' || value === 'INVESTIGATING') return 'blue' as const;
  return 'red' as const;
};

const sourceLabel = (source?: string) => ({
  ALERT: '告警', ALERT_EVENT: '告警', ALERTMANAGER: '告警', SCHEDULE: '巡检', CHAT: 'AI 对话', MANUAL: '人工创建', RUN: '分析运行',
} as Record<string, string>)[String(source || '').toUpperCase()] || source || '未知来源';

const timelineStatus = (item: OpsIncidentTimelineItem) => {
  const event = String(item.eventType || '').toUpperCase();
  if (event.includes('FAILED') || event.includes('ERROR') || event.includes('REJECTED')) return 'FAILED' as const;
  if (event.includes('VERIFICATION_SUCCEEDED') || event.includes('RESOLVED') || event.includes('CLOSED')) return 'VERIFIED' as const;
  if (event.includes('LANDED') || event.includes('LANDING')) return 'LANDING' as const;
  if (event.includes('APPROVED')) return 'APPROVED' as const;
  if (event.includes('APPROVAL') || event.includes('ACTION_REQUIRED')) return 'AWAITING_APPROVAL' as const;
  if (event.includes('CHANGE') || event.includes('REMEDIATION')) return 'PROPOSED_CHANGE' as const;
  if (event.includes('ANALYSIS') || event.includes('DIAGNOSIS') || event.includes('INVESTIGATION')) return 'REASONING' as const;
  return 'OBSERVE' as const;
};

const durationLabel = (item: OpsIncident) => {
  const started = item.firstSeenAt || item.createTime;
  if (!started) return '-';
  const start = incidentEpoch(started);
  if (!Number.isFinite(start)) return '-';
  const endRaw = ['RESOLVED', 'CLOSED'].includes(String(item.status || '').toUpperCase()) ? item.resolvedAt || item.updateTime : undefined;
  const end = endRaw ? incidentEpoch(endRaw) : Date.now();
  if (!Number.isFinite(end) || end < start) return '-';
  const minutes = Math.floor((end - start) / 60000);
  if (minutes < 60) return `${minutes} 分钟`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours} 小时 ${minutes % 60} 分`;
  return `${Math.floor(hours / 24)} 天 ${hours % 24} 小时`;
};

const timelineLabel = (item: OpsIncidentTimelineItem) => ({
  ALERT_TRIGGERED: '收到告警并启动调查',
  ANALYSIS_LINKED: 'AI 调查已关联',
  INVESTIGATION_STARTED: '开始调查',
  DIAGNOSIS_ACTION_REQUIRED: '诊断建议处置',
  USER_ACTION_REQUIRED: '需要人工介入',
  REMEDIATION_STARTED: '开始安全处置',
  RECOVERY_SIGNAL_RECEIVED: '收到恢复信号，等待验证',
  VERIFICATION_STARTED: '开始恢复验证',
  VERIFICATION_SUCCEEDED: '恢复验证通过',
  INCIDENT_OWNER_ASSIGNED: '事件责任人已更新',
  INCIDENT_OWNER_CLEARED: '事件责任人已清空',
  INCIDENT_WATCHER_ADDED: '新增事件关注人',
  INCIDENT_WATCHER_REMOVED: '取消事件关注',
  INCIDENT_RELATED: '关联相关 Incident',
  INCIDENT_RELATION_REMOVED: '解除相关 Incident',
  COMMENT: '团队评论',
  INCIDENT_CLOSED: '事件已关闭',
  INCIDENT_REOPENED: '事件已重新打开',
} as Record<string, string>)[String(item.eventType || '').toUpperCase()] || item.title || item.eventType;

export const IncidentCenterPage: React.FC = () => {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const [commentDraft, setCommentDraft] = useState('');
  const [watcherCandidate, setWatcherCandidate] = useState('');
  const [relatedCandidate, setRelatedCandidate] = useState('');
  const [workspaceTab, setWorkspaceTab] = useState<IncidentWorkspaceTab>('overview');
  const [view, setView] = useState<'current' | 'history'>((searchParams.get('view') as 'current' | 'history') || 'current');
  const projectScope = useProjectScope();
  const projectId = projectScope.projectId;
  const [selectedIncidentId, setSelectedIncidentId] = useState(searchParams.get('incidentId') || '');
  const [severity, setSeverity] = useState('');
  const [sourceType, setSourceType] = useState('');
  const [ownershipFilter, setOwnershipFilter] = useState<'all' | 'unowned'>('all');
  const [actionOnly, setActionOnly] = useState(false);
  const incidentScope: 'admin' | 'user' = isAdminUser() ? 'admin' : 'user';
  const projects = projectScope.projects;
  const incidentsQuery = useIncidentsQuery(projectId, incidentScope);
  const incidents = incidentsQuery.data || [];
  const detailQuery = useIncidentDetailQuery(selectedIncidentId, incidentScope);
  const detail = detailQuery.data || null;
  const membersQuery = useIncidentMembersQuery(
    detail?.incident?.projectId || '',
    isAdminUser() && Boolean(detail),
  );
  const members = membersQuery.data || [];
  const commandMutation = useIncidentCommandMutation(incidentScope);
  const loading = projectScope.loading || incidentsQuery.isLoading;
  const detailLoading = detailQuery.isLoading;
  const verificationLoading = commandMutation.isPending && commandMutation.variables?.kind === 'verify';
  const feedbackLoading = commandMutation.isPending && commandMutation.variables?.kind === 'feedback';
  const ownerSaving = commandMutation.isPending && commandMutation.variables?.kind === 'assign-owner';
  const collaborationSaving = commandMutation.isPending
    && ['comment', 'watcher', 'relation'].includes(commandMutation.variables?.kind || '');

  const visible = useMemo(() => incidents.filter((item) => {
    if (!(view === 'current' ? CURRENT : HISTORY).has(String(item.status || '').toUpperCase())) return false;
    if (severity && String(item.severity || '').toUpperCase() !== severity) return false;
    if (sourceType && String(item.sourceType || '').toUpperCase() !== sourceType) return false;
    if (ownershipFilter === 'unowned' && String(item.ownerUserId || '').trim()) return false;
    if (actionOnly && String(item.status || '').toUpperCase() !== 'ACTION_REQUIRED') return false;
    return true;
  }), [actionOnly, incidents, ownershipFilter, severity, sourceType, view]);

  const relatedCandidateOptions = useMemo(() => {
    if (!detail?.incident?.incidentId) return [];
    const relatedIds = new Set((detail.relatedIncidents || []).map((item) => item.incidentId));
    return incidents.filter((item) =>
      item.incidentId !== detail.incident.incidentId
      && item.projectId === detail.incident.projectId
      && !relatedIds.has(item.incidentId));
  }, [detail, incidents]);

  const openDetail = (incidentId: string) => {
    if (selectedIncidentId !== incidentId) setWorkspaceTab('overview');
    setSelectedIncidentId(incidentId);
    const next = new URLSearchParams(searchParams);
    next.set('incidentId', incidentId);
    if (projectId) next.set('projectId', projectId);
    next.set('view', view);
    setSearchParams(next, { replace: true });
  };

  const closeIncident = async (helpful = false) => {
    if (!detail?.incident?.incidentId) return;
    try {
      await commandMutation.mutateAsync({ kind: 'close', incidentId: detail.incident.incidentId, helpful });
      Toast.success('事件已关闭');
    } catch (error) {
      Toast.error(error instanceof Error ? error.message : '关闭事件失败');
    }
  };

  const submitDiagnosisFeedback = async (feedbackType: 'HELPFUL' | 'INACCURATE' | 'INSUFFICIENT_EVIDENCE') => {
    if (!detail?.incident?.projectId || !detail.incident.incidentId) return;
    const runId = String(detail.runs?.[0]?.runId || '');
    if (!runId) {
      Toast.warning('当前事件还没有可反馈的诊断运行');
      return;
    }
    try {
      await commandMutation.mutateAsync({
        kind: 'feedback',
        incidentId: detail.incident.incidentId,
        projectId: detail.incident.projectId,
        runId,
        feedbackType,
      });
      Toast.success('诊断反馈已记录');
    } catch (error) {
      Toast.error(error instanceof Error ? error.message : '诊断反馈记录失败');
    }
  };

  const reopenIncident = async () => {
    if (!detail?.incident?.incidentId) return;
    try {
      await commandMutation.mutateAsync({ kind: 'reopen', incidentId: detail.incident.incidentId });
      Toast.success('事件已重新打开');
    } catch (error) {
      Toast.error(error instanceof Error ? error.message : '重新打开事件失败');
    }
  };

  const assignOwner = async (ownerUserId = '') => {
    if (!detail?.incident?.incidentId) return;
    try {
      await commandMutation.mutateAsync({ kind: 'assign-owner', incidentId: detail.incident.incidentId, ownerUserId });
      Toast.success(isAdminUser() ? (ownerUserId ? '事件责任人已更新' : '事件责任人已清空') : '事件已认领');
    } catch (error) {
      Toast.error(error instanceof Error ? error.message : '更新事件责任人失败');
    }
  };

  const addComment = async () => {
    if (!detail?.incident?.incidentId || !commentDraft.trim()) return;
    try {
      await commandMutation.mutateAsync({ kind: 'comment', incidentId: detail.incident.incidentId, content: commentDraft.trim() });
      setCommentDraft('');
      Toast.success('评论已记录');
    } catch (error) {
      Toast.error(error instanceof Error ? error.message : '评论记录失败');
    }
  };

  const toggleWatch = async () => {
    if (!detail?.incident?.incidentId) return;
    try {
      await commandMutation.mutateAsync({
        kind: 'watcher',
        incidentId: detail.incident.incidentId,
        action: detail.currentUserWatching ? 'remove' : 'add',
        userId: '',
      });
      Toast.success(detail.currentUserWatching ? '已取消关注' : '已关注事件');
    } catch (error) {
      Toast.error(error instanceof Error ? error.message : '更新关注状态失败');
    }
  };

  const addWatcher = async () => {
    if (!detail?.incident?.incidentId || !watcherCandidate) return;
    try {
      await commandMutation.mutateAsync({
        kind: 'watcher',
        incidentId: detail.incident.incidentId,
        action: 'add',
        userId: watcherCandidate,
        adminOverride: true,
      });
      setWatcherCandidate('');
      Toast.success('关注人已添加');
    } catch (error) {
      Toast.error(error instanceof Error ? error.message : '添加关注人失败');
    }
  };

  const removeWatcher = async (userId: string) => {
    if (!detail?.incident?.incidentId) return;
    try {
      await commandMutation.mutateAsync({
        kind: 'watcher',
        incidentId: detail.incident.incidentId,
        action: 'remove',
        userId,
        adminOverride: true,
      });
      Toast.success('关注人已移除');
    } catch (error) {
      Toast.error(error instanceof Error ? error.message : '移除关注人失败');
    }
  };

  const addRelation = async () => {
    if (!detail?.incident?.incidentId || !relatedCandidate) return;
    try {
      await commandMutation.mutateAsync({
        kind: 'relation',
        incidentId: detail.incident.incidentId,
        action: 'add',
        relatedIncidentId: relatedCandidate,
      });
      setRelatedCandidate('');
      Toast.success('相关事件已关联');
    } catch (error) {
      Toast.error(error instanceof Error ? error.message : '关联事件失败');
    }
  };

  const removeRelation = async (relatedIncidentId: string) => {
    if (!detail?.incident?.incidentId) return;
    try {
      await commandMutation.mutateAsync({
        kind: 'relation',
        incidentId: detail.incident.incidentId,
        action: 'remove',
        relatedIncidentId,
      });
      Toast.success('相关事件已解除');
    } catch (error) {
      Toast.error(error instanceof Error ? error.message : '解除关联失败');
    }
  };

  const runVerification = async () => {
    if (!detail?.incident?.incidentId) return;
    const landed = detail.changePackages.find((item) => String(item.status || '').toUpperCase() === 'LANDED');
    if (!landed?.packageId) {
      Toast.warning('当前事件没有已落地的处置方案，无法运行恢复验证');
      return;
    }
    try {
      const result = await commandMutation.mutateAsync({
        kind: 'verify',
        incidentId: detail.incident.incidentId,
        packageId: landed.packageId,
      });
      if (result?.status === 'PASSED') Toast.success(result.summary || '恢复验证通过');
      else if (result?.status === 'FAILED') Toast.error(result.summary || '恢复验证失败，需要人工介入');
      else Toast.warning(result?.summary || '恢复验证证据不足，事件保持验证中');
    } catch (error) {
      Toast.error(error instanceof Error ? error.message : '恢复验证执行失败');
    }
  };

  const goPrimaryAction = () => {
    if (!detail) return;
    const incident = detail.incident;
    const status = String(incident.status || '').toUpperCase();
    if (status === 'ACTION_REQUIRED' || status === 'REMEDIATING') {
      navigate(`/executions?incidentId=${encodeURIComponent(incident.incidentId)}`);
      return;
    }
    navigate(`/chat?projectId=${encodeURIComponent(incident.projectId || '')}&incidentId=${encodeURIComponent(incident.incidentId)}`);
  };

  const columns = [
    {
      title: '事件', dataIndex: 'title',
      render: (_: unknown, item: OpsIncident) => (
        <Stack>
          <Text strong>{item.title}</Text>
          <Text type="tertiary" size="small">{item.serviceName || item.projectId || item.incidentId}</Text>
        </Stack>
      ),
    },
    { title: '状态', dataIndex: 'status', width: 120, render: (value: string) => <Tag color={statusColor(value)}>{statusLabel(value)}</Tag> },
    { title: '持续时间', width: 120, render: (_: unknown, item: OpsIncident) => durationLabel(item) },
    { title: '当前结论', dataIndex: 'summary', width: 260, render: (value: string) => <Text ellipsis={{ showTooltip: true }}>{value || '等待形成可信诊断'}</Text> },
    { title: '责任人', dataIndex: 'ownerUserId', width: 140, render: (value: string) => value || <Text type="tertiary">未认领</Text> },
    { title: '需要动作', width: 110, render: (_: unknown, item: OpsIncident) => String(item.status).toUpperCase() === 'ACTION_REQUIRED' ? <Tag color="orange">需要人工</Tag> : <Text type="tertiary">无需</Text> },
    { title: '来源', dataIndex: 'sourceType', width: 110, render: (value: string) => sourceLabel(value) },
    { title: '级别', dataIndex: 'severity', width: 100, render: (value: string) => value || '-' },
    { title: '最近更新（本地时间）', dataIndex: 'updateTime', width: 180, render: (value: string) => incidentLocalTime(value) },
    { title: '', width: 90, render: (_: unknown, item: OpsIncident) => <Button theme="borderless" onClick={() => void openDetail(item.incidentId)}>查看</Button> },
  ];

  return (
    <OpsPageShell selectedKey="workbench">
      <OpsPageHeader
        title="事件中心"
        description="以 Incident → Diagnosis → Remediation 为主线查看真实证据、诊断结论、安全处置与恢复证明。"
        extra={<Space><Button onClick={() => navigate(`/workbench?projectId=${encodeURIComponent(projectId)}`)}>运行记录</Button><Button onClick={() => { void projectScope.reloadProjects(); void incidentsQuery.refetch(); }}>刷新</Button></Space>}
      />

      {projectId && <AlertCorrelationPanel projectId={projectId} scope={incidentScope} onOpen={(id) => void openDetail(id)} />}
      <OpsSectionCard title="事件视图">
        <Paragraph type="tertiary">状态由后端事实聚合计算，页面不维护第二套事件状态机。</Paragraph>
        <Space wrap>
          <Select value={view} onChange={(value) => setView(String(value) === 'history' ? 'history' : 'current')} style={{ width: 160 }}>
            <Option value="current">当前事件</Option>
            <Option value="history">历史事件</Option>
          </Select>
          <Select value={projectId} onChange={(value) => projectScope.selectProject(String(value || ''))} style={{ width: 240 }} placeholder="全部项目">
            <Option value="">全部项目</Option>
            {projects.map((project) => <Option key={project.projectId} value={project.projectId}>{project.name || project.projectId}</Option>)}
          </Select>
          <Select value={severity} onChange={(value) => setSeverity(String(value || '').toUpperCase())} style={{ width: 140 }} placeholder="全部级别">
            <Option value="">全部级别</Option>
            {['CRITICAL', 'HIGH', 'WARNING', 'MEDIUM', 'LOW', 'INFO'].map((value) => <Option key={value} value={value}>{value}</Option>)}
          </Select>
          <Select value={sourceType} onChange={(value) => setSourceType(String(value || '').toUpperCase())} style={{ width: 140 }} placeholder="全部来源">
            <Option value="">全部来源</Option>
            <Option value="ALERT">告警</Option>
            <Option value="SCHEDULE">巡检</Option>
            <Option value="CHAT">AI 对话</Option>
            <Option value="MANUAL">人工创建</Option>
          </Select>
          <Select value={ownershipFilter} onChange={(value) => setOwnershipFilter(String(value || 'all') as 'all' | 'unowned')} style={{ width: 140 }}>
            <Option value="all">全部责任状态</Option>
            <Option value="unowned">仅未分配</Option>
          </Select>
          <Checkbox checked={actionOnly} onChange={(event) => setActionOnly(Boolean(event.target.checked))}>只看需要人工动作</Checkbox>
        </Space>
        <div style={{ marginTop: 16 }}>
          <Spin spinning={loading}>
            {projectScope.error || incidentsQuery.isError ? (
              <OpsEmptyState
                title="事件加载失败"
                description={projectScope.error || (incidentsQuery.error instanceof Error ? incidentsQuery.error.message : '请刷新后重试。')}
              />
            ) : visible.length ? (
              <TableScroll><Table rowKey="incidentId" columns={columns} dataSource={visible} pagination={false} /></TableScroll>
            ) : <OpsEmptyState title={view === 'current' ? '当前没有进行中的事件' : '还没有历史事件'} description="告警、巡检异常或人工诊断可以进入同一 Incident 主线。" />}
          </Spin>
        </div>
      </OpsSectionCard>

      <div style={{ height: 16 }} />
      <Spin spinning={detailLoading}>
        {detailQuery.isError ? (
          <OpsEmptyState
            title="事件详情加载失败"
            description={detailQuery.error instanceof Error ? detailQuery.error.message : '请刷新后重试。'}
          />
        ) : !detail ? (
          <Empty title="选择一个事件查看完整调查与处置链路" />
        ) : (
          <Stack>
            <OpsPageHeader
              context={<Tag color={statusColor(detail.incident.status)}>{statusLabel(detail.incident.status)}</Tag>}
              title={detail.incident.title}
              description={detail.incident.summary || '暂无事件摘要'}
              primaryAction={String(detail.incident.status).toUpperCase() === 'VERIFYING'
                ? <Button theme="solid" type="primary" loading={verificationLoading} onClick={() => void runVerification()}>运行恢复验证</Button>
                : String(detail.incident.status).toUpperCase() !== 'CLOSED'
                  ? <Button theme="solid" type="primary" onClick={goPrimaryAction}>{detail.suggestedUserAction || '继续处理'}</Button>
                  : undefined}
              extra={String(detail.incident.status).toUpperCase() === 'RESOLVED'
                ? <Space><Button onClick={() => void closeIncident(false)}>关闭</Button><Button theme="solid" type="primary" onClick={() => void closeIncident(true)}>有帮助并关闭</Button></Space>
                : String(detail.incident.status).toUpperCase() === 'CLOSED'
                  ? <Button onClick={() => void reopenIncident()}>重新打开</Button>
                  : undefined}
            />

            <OpsSectionCard title="Incident Workspace">
              <Paragraph type="tertiary">同一个 Incident 内串联调查、证据、变更与恢复验证；Chat、Run 和 ChangePackage 都回到这个上下文。</Paragraph>
              <IncidentWorkspaceTabs value={workspaceTab} onChange={setWorkspaceTab} />
            </OpsSectionCard>

            <DetailGrid>
              <Stack>
                {workspaceTab === 'overview' && (
                  <OpsSectionCard title="Incident Overview">
                    <Space wrap>
                      <OpsStatusBadge status="OBSERVE" label={sourceLabel(detail.incident.sourceType)} />
                      <Tag>{detail.incident.severity || 'UNKNOWN'}</Tag>
                      <Tag>{durationLabel(detail.incident)}</Tag>
                    </Space>
                    <Paragraph>{detail.diagnosis.summary || detail.incident.summary || '等待形成可信诊断'}</Paragraph>
                    {!!detail.diagnosis.impact.length && <Stack><Title heading={6}>当前影响</Title>{detail.diagnosis.impact.map((item) => <FactCard key={item}>{item}</FactCard>)}</Stack>}
                    <Space wrap style={{ marginTop: 12 }}>
                      <Button onClick={() => navigate(`/chat?projectId=${encodeURIComponent(detail.incident.projectId || '')}&incidentId=${encodeURIComponent(detail.incident.incidentId)}`)}>在 Diagnosis 中继续</Button>
                      {detail.changePackages.length > 0 && <Button onClick={() => setWorkspaceTab('change')}>查看 Change</Button>}
                    </Space>
                  </OpsSectionCard>
                )}

                {workspaceTab === 'investigation' && (
                  <OpsSectionCard title="Investigation">
                    <Paragraph type="tertiary">诊断判断与事实分离；事实证据在 Evidence 工作面核验。</Paragraph>
                    <Space wrap>
                      <Tag color={detail.diagnosis.evidenceCompleteness === 'COMPLETE' ? 'green' : detail.diagnosis.evidenceCompleteness === 'PARTIAL' ? 'orange' : 'red'}>Evidence {detail.diagnosis.evidenceCompleteness}</Tag>
                      <Tag>{`置信度 ${detail.diagnosis.confidence}`}</Tag>
                    </Space>
                    <Paragraph>{detail.diagnosis.summary}</Paragraph>
                    {!!detail.diagnosis.inferences.length && <Stack><Title heading={6}>诊断判断</Title>{detail.diagnosis.inferences.map((item, index) => <FactCard key={`${index}-${item.statement}`}>{item.statement}<div><Text type="tertiary" size="small">依据事实：{item.supports.join('、')}</Text></div></FactCard>)}</Stack>}
                    {!!detail.diagnosis.excludedHypotheses.length && <Stack><Title heading={6}>已排除假设</Title>{detail.diagnosis.excludedHypotheses.map((item) => <FactCard key={item}>{item}</FactCard>)}</Stack>}
                    {!!detail.diagnosis.unknowns.length && <Stack><Title heading={6}>未知项</Title>{detail.diagnosis.unknowns.map((item) => <FactCard key={item}>{item}</FactCard>)}</Stack>}
                    {!!detail.diagnosis.recommendations.length && <Stack><Title heading={6}>建议</Title>{detail.diagnosis.recommendations.map((item) => <FactCard key={item}>{item}</FactCard>)}</Stack>}
                    <div style={{ marginTop: 12 }}>
                      <Text type="tertiary" size="small">诊断质量反馈</Text>
                      <div style={{ marginTop: 6 }}><Space wrap>
                        <Button size="small" loading={feedbackLoading} onClick={() => void submitDiagnosisFeedback('HELPFUL')}>有帮助</Button>
                        <Button size="small" loading={feedbackLoading} onClick={() => void submitDiagnosisFeedback('INACCURATE')}>不准确</Button>
                        <Button size="small" loading={feedbackLoading} onClick={() => void submitDiagnosisFeedback('INSUFFICIENT_EVIDENCE')}>证据不足</Button>
                      </Space></div>
                    </div>
                  </OpsSectionCard>
                )}

                {workspaceTab === 'evidence' && (
                  <OpsSectionCard title="Evidence">
                    <Paragraph type="tertiary">每条事实必须可回到 evidenceRef / resultId / outputHash；无法核对的内容不进入已验证事实。</Paragraph>
                    {detail.diagnosis.facts.length ? <Stack>{detail.diagnosis.facts.map((fact) => (
                      <OpsEvidenceCard
                        key={fact.factId}
                        title={fact.statement}
                        summary={<Text type="secondary">Fact ID: {fact.factId}</Text>}
                        source={fact.evidenceRefs.map((ref) => `${ref.evidenceRef} · ${ref.resultId} · ${ref.outputHash}`).join('；')}
                      />
                    ))}</Stack> : <Text type="tertiary">当前还没有已验证事实。</Text>}
                  </OpsSectionCard>
                )}

                {workspaceTab === 'change' && (
                  <OpsSectionCard title="Governed Change Chain">
                    <Paragraph type="tertiary">ChangePackage → Approval → Trusted Landing → Verification。Incident 只承载上下文和状态，不成为第二审批入口。</Paragraph>
                    <Space wrap>
                      <OpsStatusBadge status={detail.changePackages.length ? 'PROPOSED_CHANGE' : 'BLOCKED'} label={detail.changePackages.length ? 'Change proposed' : 'No ChangePackage'} />
                      {String(detail.incident.status).toUpperCase() === 'VERIFYING' && <OpsStatusBadge status="LANDING" label="Awaiting verification" />}
                      {['RESOLVED', 'CLOSED'].includes(String(detail.incident.status).toUpperCase()) && <OpsStatusBadge status="VERIFIED" />}
                    </Space>
                    <Paragraph>{detail.suggestedUserAction || '当前无需额外操作'}</Paragraph>
                  </OpsSectionCard>
                )}

                {workspaceTab === 'timeline' && (
                  <OpsSectionCard title="Timeline">
                    <Paragraph type="tertiary">统一展示告警、调查、证据、变更、审批、Landing、验证和协作事件。</Paragraph>
                    {detail.timeline.length ? <OpsTimeline items={detail.timeline.map((item) => ({
                      id: `${item.id || ''}-${item.eventType}-${item.createTime || ''}`,
                      status: timelineStatus(item),
                      title: timelineLabel(item),
                      description: item.detail || (item.refType && item.refId ? `${sourceLabel(item.refType)} ${item.refId}` : undefined),
                      timestamp: incidentLocalTime(item.createTime),
                    }))} /> : <Text type="tertiary">还没有 Timeline 事件。</Text>}
                  </OpsSectionCard>
                )}
              </Stack>

              <Stack>
                {workspaceTab === 'overview' && <>
                <OpsSectionCard title="责任归属">
                  <Paragraph type="tertiary">责任人只用于协作和工作台分流，不改变审批、生产执行或 Incident 状态权限。</Paragraph>
                  {isAdminUser() ? (
                    <Select
                      value={detail.incident.ownerUserId || ''}
                      onChange={(value) => void assignOwner(String(value || ''))}
                      loading={ownerSaving}
                      style={{ width: '100%' }}
                      placeholder="选择项目成员"
                    >
                      <Option value="">未分配</Option>
                      {members.map((member) => <Option key={member.value} value={member.value}>{member.label}</Option>)}
                    </Select>
                  ) : detail.incident.ownerUserId ? (
                    <Text strong>{detail.incident.ownerUserId}</Text>
                  ) : (
                    <Button loading={ownerSaving} onClick={() => void assignOwner()}>认领这个事件</Button>
                  )}
                </OpsSectionCard>

                <OpsSectionCard title="团队协作">
                  <Paragraph type="tertiary">关注人用于接收协作上下文；评论直接进入 Incident Timeline，不形成第二套工单讨论区。</Paragraph>
                  <Stack>
                    <div>
                      <Text strong>关注人</Text>
                      <div style={{ marginTop: 8 }}>
                        <Space wrap>
                          {(detail.watchers || []).map((watcher) => (
                            <Tag
                              key={watcher.userId}
                              color="blue"
                              closable={isAdminUser()}
                              onClose={isAdminUser() ? () => void removeWatcher(watcher.userId) : undefined}
                            >
                              {watcher.userId}
                            </Tag>
                          ))}
                          {!detail.watchers?.length && <Text type="tertiary">暂无关注人</Text>}
                        </Space>
                      </div>
                    </div>
                    {isAdminUser() ? (
                      <Space wrap>
                        <Select
                          value={watcherCandidate}
                          onChange={(value) => setWatcherCandidate(String(value || ''))}
                          style={{ width: 220 }}
                          placeholder="添加项目成员关注"
                        >
                          {members
                            .filter((member) => !(detail.watchers || []).some((watcher) => watcher.userId === member.value))
                            .map((member) => <Option key={member.value} value={member.value}>{member.label}</Option>)}
                        </Select>
                        <Button loading={collaborationSaving} disabled={!watcherCandidate} onClick={() => void addWatcher()}>添加关注</Button>
                      </Space>
                    ) : (
                      <Button loading={collaborationSaving} onClick={() => void toggleWatch()}>
                        {detail.currentUserWatching ? '取消关注' : '关注这个事件'}
                      </Button>
                    )}
                    <TextArea
                      value={commentDraft}
                      onChange={setCommentDraft}
                      autosize={{ minRows: 2, maxRows: 5 }}
                      placeholder="补充排查进展、业务背景或交接说明"
                    />
                    <div>
                      <Button
                        theme="solid"
                        loading={collaborationSaving}
                        disabled={!commentDraft.trim()}
                        onClick={() => void addComment()}
                      >
                        记录评论
                      </Button>
                    </div>
                  </Stack>
                </OpsSectionCard>

                <OpsSectionCard title="相关事件">
                  <Paragraph type="tertiary">用于把同一项目中重复、上下游或共同根因的 Incident 放在一起追溯，不合并各自状态和审计链。</Paragraph>
                  <Stack>
                    {(detail.relatedIncidents || []).map((related) => (
                      <FactCard key={related.incidentId}>
                        <Space wrap style={{ justifyContent: 'space-between', width: '100%' }}>
                          <div>
                            <Text strong>{related.title || related.incidentId}</Text>
                            <div>
                              <Tag color={statusColor(related.status)}>{statusLabel(related.status)}</Tag>{' '}
                              <Text type="tertiary" size="small">{related.severity || '-'} · {related.relationType || 'RELATED'}</Text>
                            </div>
                          </div>
                          <Space>
                            <Button theme="borderless" onClick={() => void openDetail(related.incidentId)}>查看</Button>
                            <Button theme="borderless" type="danger" loading={collaborationSaving} onClick={() => void removeRelation(related.incidentId)}>解除</Button>
                          </Space>
                        </Space>
                      </FactCard>
                    ))}
                    {!detail.relatedIncidents?.length && <Text type="tertiary">暂无额外关联。自动归组结果见页面上方。</Text>}
                    <Space wrap>
                      <Select
                        value={relatedCandidate}
                        onChange={(value) => setRelatedCandidate(String(value || ''))}
                        style={{ width: 260 }}
                        placeholder="选择同项目 Incident"
                      >
                        {relatedCandidateOptions.map((item) => (
                          <Option key={item.incidentId} value={item.incidentId}>{item.title || item.incidentId}</Option>
                        ))}
                      </Select>
                      <Button loading={collaborationSaving} disabled={!relatedCandidate} onClick={() => void addRelation()}>关联事件</Button>
                    </Space>
                  </Stack>
                </OpsSectionCard>
                </>}

                {workspaceTab === 'investigation' && <>
                <OpsSectionCard title="数据源查询状态">
                  <Paragraph type="tertiary">“查询是否成功”和“结果是否说明异常”是两件事；未查询、查询失败或没有业务判定时都不会显示成正常。</Paragraph>
                  {!!detail.diagnosis.sourceStatus.length ? <Stack>{detail.diagnosis.sourceStatus.map((source) => <FactCard key={source.sourceId}><Text strong>{source.sourceName || source.sourceId}</Text><div><Tag>查询 {source.queryStatus}</Tag> <Tag>判断 {source.assessment}</Tag></div>{source.detail && <Text type="tertiary">{source.detail}</Text>}</FactCard>)}</Stack> : <Text type="tertiary">当前诊断还没有结构化的数据源查询状态。</Text>}
                </OpsSectionCard>
                <OpsSectionCard title="诊断运行记录">
                  <Paragraph type="tertiary">打开关联运行，核对实际查询、工具回执与执行结果。</Paragraph>
                  {detail.runs.length ? <Stack>{detail.runs.map((run, index) => {
                    const runId = String(run.runId || run.run_id || run.analysisId || '');
                    const status = String(run.status || run.runStatus || 'UNKNOWN');
                    return <FactCard key={runId || index}><Text strong>{runId || '运行标识缺失'}</Text><div><Tag>{status}</Tag>
                      <Button theme="borderless" disabled={!runId} onClick={() => navigate(`/workbench?projectId=${encodeURIComponent(detail.incident.projectId || projectId)}&runId=${encodeURIComponent(runId)}`)}>查看运行</Button>
                    </div>{run.summary && <Text type="tertiary">{String(run.summary)}</Text>}</FactCard>;
                  })}</Stack> : <Text type="tertiary">当前 Incident 尚未关联诊断 Run。</Text>}
                </OpsSectionCard>
                </>}

                {workspaceTab === 'evidence' && <>
                <OpsSectionCard title="关联来源">
                  <Paragraph type="tertiary">这里记录告警、巡检、对话和分析运行等来源关系，用于追溯事件从哪里来；它们不是业务健康判断。</Paragraph>
                  {detail.sourceRefs.length ? <Stack>{detail.sourceRefs.map((source) => <FactCard key={`${source.sourceType}-${source.sourceId}`}><Text strong>{sourceLabel(source.sourceType)}</Text><div>{source.title || source.sourceId}</div><Text type="tertiary" size="small">{source.status || '已关联'} · {source.sourceId}</Text></FactCard>)}</Stack> : <Text type="tertiary">暂无已关联来源。</Text>}
                </OpsSectionCard>
                </>}

                {workspaceTab === 'change' && <>
                <OpsSectionCard title="处置方案">
                  <Paragraph type="tertiary">Incident 只展示处置状态；正式审批与生产执行只能在执行中心完成。</Paragraph>
                  {detail.changePackages.length ? (
                    <Stack>
                      {detail.changePackages.map((change) => {
                        const summary = toChangePackageSummary(change);
                        return (
                          <ChangePackageSummaryCard
                            key={summary.packageId}
                            summary={summary}
                            actionLabel="前往执行中心"
                            onOpen={() => navigate(`/executions?incidentId=${encodeURIComponent(detail.incident.incidentId)}&packageId=${encodeURIComponent(summary.packageId)}`)}
                          />
                        );
                      })}
                    </Stack>
                  ) : <Text type="tertiary">当前没有 ChangePackage。诊断需要处置时，AI 可形成变更任务书并在执行中心交接。</Text>}
                </OpsSectionCard>

                <OpsSectionCard title="下一步">
                  <Text strong>{detail.suggestedUserAction || '无需额外操作'}</Text>
                  <Paragraph type="tertiary">生产审批与 Landing 不在本页执行，避免形成第二审批入口。</Paragraph>
                </OpsSectionCard>
                </>}
              </Stack>
            </DetailGrid>

            {isAdminUser() && (
              <Advanced>
                <summary>Advanced Trace</summary>
                <Paragraph type="tertiary">面向管理员调试和审计的 run、source ref 与原始 timeline payload。</Paragraph>
                <pre>{JSON.stringify({ runs: detail.runs, sourceRefs: detail.sourceRefs, timeline: detail.timeline }, null, 2)}</pre>
              </Advanced>
            )}
          </Stack>
        )}
      </Spin>
    </OpsPageShell>
  );
};
