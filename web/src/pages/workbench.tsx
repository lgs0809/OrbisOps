import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Button, Select, Space, Spin, Tag, Toast, Typography } from '@douyinfe/semi-ui';
import { IconComment, IconRefresh } from '@douyinfe/semi-icons';
import { useNavigate, useSearchParams } from 'react-router-dom';
import styled from 'styled-components';

import { JsonBlock, OpsAdvancedPreview, OpsPageHeader, OpsPageShell, OpsSectionCard } from '../components/ops-layout';
import { useProjectScope } from '../hooks/use-project-scope';
import { isAdminUser } from '../services/auth-session';
import {
  opsAdminService,
  type OpsAnalysisTaskDetail,
  type OpsAnalysisTaskSummary,
} from '../services/ops-admin-service';
import { theme } from '../styles/theme';
import { userFacingDetail, userFacingError } from '../utils/user-facing-error';

const { Text, Paragraph } = Typography;
const { Option } = Select;

const FilterBar = styled.div`
  display: flex;
  align-items: center;
  gap: ${theme.spacing.sm};
  padding: 0 ${theme.spacing.lg} ${theme.spacing.base};
  flex-wrap: wrap;
`;

const WorkspaceGrid = styled.div`
  display: grid;
  grid-template-columns: minmax(320px, 0.85fr) minmax(0, 1.4fr);
  min-height: 560px;
  border-top: 1px solid ${theme.colors.border.secondary};

  @media (max-width: 980px) {
    grid-template-columns: 1fr;
  }
`;

const RunList = styled.div`
  min-width: 0;
  max-height: 720px;
  overflow: auto;
  border-right: 1px solid ${theme.colors.border.secondary};

  @media (max-width: 980px) {
    border-right: 0;
    border-bottom: 1px solid ${theme.colors.border.secondary};
  }
`;

const RunButton = styled.button<{ $active: boolean }>`
  width: 100%;
  padding: ${theme.spacing.base};
  border: 0;
  border-bottom: 1px solid ${theme.colors.border.secondary};
  background: ${(props) => (props.$active ? '#eff6ff' : theme.colors.bg.primary)};
  text-align: left;
  cursor: pointer;
  color: inherit;

  &:hover {
    background: ${theme.colors.bg.secondary};
  }
`;

const RunTitle = styled(Text)`
  display: block;
  margin: 6px 0;
  color: ${theme.colors.text.primary};
  font-weight: ${theme.typography.fontWeight.semibold};
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
`;

const RunDetail = styled.div`
  min-width: 0;
  padding: ${theme.spacing.lg};
`;

const DetailGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: ${theme.spacing.base};
  margin-bottom: ${theme.spacing.lg};

  @media (max-width: ${theme.breakpoints.sm}) {
    grid-template-columns: 1fr;
  }
`;

const DetailBox = styled.div`
  min-width: 0;
  padding: ${theme.spacing.base};
  border: 1px solid ${theme.colors.border.secondary};
  border-radius: ${theme.borderRadius.base};
  background: ${theme.colors.bg.secondary};
`;

const DetailList = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${theme.spacing.sm};
  margin-top: ${theme.spacing.sm};
`;

const statusColor = (status?: string) => {
  const normalized = String(status || '').toUpperCase();
  if (['FAILED', 'ERROR', 'BLOCKED'].includes(normalized)) return 'red';
  if (['RUNNING', 'QUEUED', 'PENDING'].includes(normalized)) return 'blue';
  if (['SUCCEEDED', 'COMPLETED', 'VERIFIED'].includes(normalized)) return 'green';
  return 'grey';
};

const statusLabel = (status?: string) => {
  const normalized = String(status || '').toUpperCase();
  if (normalized === 'RUNNING') return '运行中';
  if (normalized === 'QUEUED') return '排队中';
  if (normalized === 'PENDING') return '等待中';
  if (normalized === 'SUCCEEDED' || normalized === 'COMPLETED') return '已完成';
  if (normalized === 'VERIFIED') return '已验证';
  if (normalized === 'FAILED' || normalized === 'ERROR') return '失败';
  if (normalized === 'BLOCKED') return '已阻塞';
  return normalized || '未知';
};

const sourceLabel = (run: OpsAnalysisTaskSummary) => {
  const source = String(run.source || run.taskType || 'MANUAL').toUpperCase();
  if (source === 'SCHEDULE') return '定时自动化';
  if (source === 'ALERTMANAGER' || source === 'ALERT') return '告警自动化';
  if (source === 'CHANNEL') return '渠道';
  if (source === 'CHAT') return '对话';
  if (source === 'MANUAL') return '手动';
  return '其他来源';
};

const evidenceSummary = (item: Record<string, any>) => {
  const candidate = [item.summary, item.title, item.description, item.message, item.content, item.resultSummary]
    .find((value) => typeof value === 'string' && value.trim());
  const normalized = String(candidate || '').trim();
  if (normalized.startsWith('{') || normalized.startsWith('[')) {
    return '已记录一项结构化工具证据，可在技术详情中查看。';
  }
  return userFacingDetail(candidate, '已记录一项执行证据。');
};

export const WorkbenchPage: React.FC = () => {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const requestedRunId = searchParams.get('runId') || '';
  const projectScope = useProjectScope();
  const scope = isAdminUser() ? 'admin' : 'user';
  const [runs, setRuns] = useState<OpsAnalysisTaskSummary[]>([]);
  const [selectedRunId, setSelectedRunId] = useState('');
  const [detail, setDetail] = useState<OpsAnalysisTaskDetail | null>(null);
  const [source, setSource] = useState('');
  const [status, setStatus] = useState('');
  const [loading, setLoading] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);

  const loadRuns = useCallback(async () => {
    if (!projectScope.projectId) {
      setRuns([]);
      setSelectedRunId('');
      setDetail(null);
      return;
    }
    setLoading(true);
    try {
      const response = await opsAdminService.listAnalysisTasks({
        projectId: projectScope.projectId,
        source: source || undefined,
        status: status || undefined,
        limit: 100,
        scope,
      });
      const nextRuns = response.data || [];
      setRuns(nextRuns);
      setSelectedRunId((current) => {
        if (requestedRunId && nextRuns.some((run) => run.runId === requestedRunId)) return requestedRunId;
        return nextRuns.some((run) => run.runId === current) ? current : '';
      });
    } catch (error) {
      Toast.error(userFacingError(error, '加载 Run 列表失败，请稍后重试。'));
    } finally {
      setLoading(false);
    }
  }, [projectScope.projectId, requestedRunId, scope, source, status]);

  useEffect(() => {
    void loadRuns();
  }, [loadRuns]);

  useEffect(() => {
    if (!projectScope.projectId || !selectedRunId) {
      setDetail(null);
      return;
    }
    let active = true;
    setDetailLoading(true);
    opsAdminService.getAnalysisTask(projectScope.projectId, selectedRunId, scope)
      .then((response) => {
        if (active) setDetail(response.data || null);
      })
      .catch((error) => {
        if (active) Toast.error(userFacingError(error, '加载 Run 详情失败，请稍后重试。'));
      })
      .finally(() => {
        if (active) setDetailLoading(false);
      });
    return () => {
      active = false;
    };
  }, [projectScope.projectId, scope, selectedRunId]);

  const selectedRun = useMemo(
    () => runs.find((run) => run.runId === selectedRunId),
    [runs, selectedRunId],
  );

  const continueInChat = () => {
    if (!projectScope.projectId) return;
    const params = new URLSearchParams({ projectId: projectScope.projectId });
    if (selectedRun?.sessionId) params.set('sessionId', selectedRun.sessionId);
    if (selectedRun?.runId) params.set('runId', selectedRun.runId);
    navigate(`/chat?${params.toString()}`);
  };

  return (
    <OpsPageShell selectedKey="workbench">
      <OpsPageHeader
        title="工作台"
        description="用于查看正式运行、长时间执行、自动化任务、渠道触发任务，以及失败或阻塞中的执行。"
        extra={<Button icon={<IconRefresh />} loading={loading} onClick={() => void loadRuns()}>刷新运行</Button>}
      />

      <OpsSectionCard title="运行列表">
        <FilterBar>
          <Select
            value={projectScope.projectId || undefined}
            placeholder="选择项目"
            style={{ minWidth: 220 }}
            onChange={(value) => projectScope.selectProject(String(value || ''))}
          >
            {projectScope.projects.map((project) => (
              <Option key={project.projectId} value={project.projectId}>{project.name || project.projectId}</Option>
            ))}
          </Select>
          <Select value={source} placeholder="全部来源" style={{ width: 160 }} onChange={(value) => setSource(String(value || ''))}>
            <Option value="">全部来源</Option>
            <Option value="CHAT">对话</Option>
            <Option value="CHANNEL">渠道</Option>
            <Option value="SCHEDULE">定时自动化</Option>
            <Option value="ALERTMANAGER">告警自动化</Option>
          </Select>
          <Select value={status} placeholder="全部状态" style={{ width: 160 }} onChange={(value) => setStatus(String(value || ''))}>
            <Option value="">全部状态</Option>
            <Option value="RUNNING">运行中</Option>
            <Option value="SUCCEEDED">已完成</Option>
            <Option value="FAILED">失败</Option>
            <Option value="BLOCKED">已阻塞</Option>
          </Select>
          <Text type="tertiary">共 {runs.length} 个运行</Text>
        </FilterBar>

        <WorkspaceGrid>
          <RunList>
            {loading && runs.length === 0 && <Spin style={{ margin: 24 }} />}
            {!loading && runs.length === 0 && <Text type="tertiary" style={{ display: 'block', padding: 24 }}>当前筛选条件下没有运行。</Text>}
            {runs.map((run) => (
              <RunButton key={run.runId} type="button" $active={run.runId === selectedRunId} onClick={() => setSelectedRunId(run.runId)}>
                <Space wrap>
                  <Tag color={statusColor(run.status)}>{statusLabel(run.status)}</Tag>
                  <Tag>{sourceLabel(run)}</Tag>
                </Space>
                <RunTitle>{run.goal || run.runId}</RunTitle>
                <Text type="tertiary" size="small">{run.updatedAt || run.createdAt || run.runId}</Text>
              </RunButton>
            ))}
          </RunList>

          <RunDetail>
            {detailLoading && <Spin />}
            {!detailLoading && !selectedRun && <Text type="tertiary">请选择一个运行查看执行状态。</Text>}
            {!detailLoading && selectedRun && (
              <>
                <Space wrap style={{ marginBottom: 16 }}>
                  <Tag color={statusColor(selectedRun.status)}>{statusLabel(selectedRun.status)}</Tag>
                  <Tag>{sourceLabel(selectedRun)}</Tag>
                  <Button icon={<IconComment />} onClick={continueInChat}>
                    {selectedRun.sessionId ? '继续对话' : '打开对话'}
                  </Button>
                  {(detail?.changePackages?.length || 0) > 0 && (
                    <Button onClick={() => navigate(`/changes?projectId=${encodeURIComponent(projectScope.projectId)}`)}>打开变更</Button>
                  )}
                </Space>

                <Paragraph strong>{detail?.summary || selectedRun.goal || '运行详情'}</Paragraph>
                {detail?.errorMessage && <Paragraph type="danger">{userFacingDetail(detail.errorMessage, '本次执行未成功完成，请查看状态与技术详情。')}</Paragraph>}

                <DetailGrid>
                  <DetailBox>
                    <Text type="tertiary">来源</Text>
                    <Paragraph>{sourceLabel(selectedRun)}</Paragraph>
                  </DetailBox>
                  <DetailBox>
                    <Text type="tertiary">项目</Text>
                    <Paragraph>{projectScope.projects.find((project) => project.projectId === selectedRun.projectId)?.name || '当前项目'}</Paragraph>
                  </DetailBox>
                  <DetailBox>
                    <Text type="tertiary">执行方式</Text>
                    <Paragraph>{projectScope.projects.find((project) => project.projectId === selectedRun.projectId)?.defaultAgentId === selectedRun.agentId ? '默认模型' : selectedRun.agentId ? '已发布工作流' : '默认模型'}</Paragraph>
                  </DetailBox>
                  <DetailBox>
                    <Text type="tertiary">关联对话</Text>
                    <Paragraph>{selectedRun.sessionId ? '已关联，可继续对话' : '未关联对话'}</Paragraph>
                  </DetailBox>
                </DetailGrid>

                {!!detail?.recommendations?.length && (
                  <DetailBox>
                    <Text strong>建议</Text>
                    <DetailList>{detail.recommendations.map((item, index) => <Text key={index}>{item}</Text>)}</DetailList>
                  </DetailBox>
                )}

                {!!detail?.unknowns?.length && (
                  <DetailBox style={{ marginTop: 16 }}>
                    <Text strong>仍需确认</Text>
                    <DetailList>{detail.unknowns.map((item, index) => <Text key={index}>{item}</Text>)}</DetailList>
                  </DetailBox>
                )}

                {!!detail?.evidence?.length && (
                  <DetailBox style={{ marginTop: 16 }}>
                    <Text strong>证据摘要（{detail.evidence.length}）</Text>
                    <DetailList>
                      {detail.evidence.slice(0, 10).map((item, index) => <Text key={index}>{evidenceSummary(item)}</Text>)}
                    </DetailList>
                  </DetailBox>
                )}

                <div style={{ marginTop: 16 }}>
                  <OpsAdvancedPreview title="技术详情" description="用于排障和审计。这里保留精确标识、执行器信息、原始证据与运行事件。">
                    <DetailGrid>
                      <DetailBox><Text type="tertiary">Run ID</Text><Paragraph copyable>{selectedRun.runId}</Paragraph></DetailBox>
                      <DetailBox><Text type="tertiary">Session ID</Text><Paragraph copyable>{selectedRun.sessionId || '-'}</Paragraph></DetailBox>
                      <DetailBox><Text type="tertiary">Agent ID</Text><Paragraph copyable>{selectedRun.agentId || '-'}</Paragraph></DetailBox>
                      <DetailBox><Text type="tertiary">执行器</Text><Paragraph>{selectedRun.executionHarness || '-'}</Paragraph></DetailBox>
                    </DetailGrid>
                    {!!detail?.evidence?.length && <JsonBlock>{JSON.stringify(detail.evidence, null, 2)}</JsonBlock>}
                    {!!detail?.events?.length && <div style={{ marginTop: 12 }}><JsonBlock>{JSON.stringify(detail.events.slice(-20), null, 2)}</JsonBlock></div>}
                  </OpsAdvancedPreview>
                </div>
              </>
            )}
          </RunDetail>
        </WorkspaceGrid>
      </OpsSectionCard>
    </OpsPageShell>
  );
};
