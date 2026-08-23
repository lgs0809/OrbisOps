import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Button, Select, Space, Spin, Tag, Toast, Typography } from '@douyinfe/semi-ui';
import { IconComment, IconRefresh } from '@douyinfe/semi-icons';
import { useNavigate, useSearchParams } from 'react-router-dom';
import styled from 'styled-components';

import { JsonBlock, OpsAdvancedPreview, OpsPageShell } from '../components/ops-layout';
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

const Workbench = styled.section`
  height: 100dvh;
  min-height: 0;
  display: grid;
  grid-template-columns: 340px minmax(0, 1fr);
  background: #fff;
  overflow: hidden;

  @media (max-width: 920px) {
    grid-template-columns: 1fr;
    grid-template-rows: minmax(220px, 38%) minmax(0, 1fr);
  }

  @media (max-width: ${theme.breakpoints.md}) {
    height: calc(100dvh - 52px);
  }
`;

const RunInbox = styled.aside`
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  border-right: 1px solid ${theme.colors.border.secondary};
  background: #f7f8fa;

  @media (max-width: 920px) {
    max-height: none;
    border-right: 0;
    border-bottom: 1px solid ${theme.colors.border.secondary};
  }
`;

const InboxHeader = styled.div`
  padding: 17px 14px 12px;
  border-bottom: 1px solid ${theme.colors.border.tertiary};

  .titleRow {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 10px;
    margin-bottom: 12px;
  }

  h1 {
    margin: 0;
    font-size: 17px;
    font-weight: 650;
    letter-spacing: -0.025em;
  }
`;

const Filters = styled.div`
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 7px;

  .project {
    grid-column: 1 / -1;
  }
`;

const RunCount = styled.div`
  padding: 9px 14px 5px;
  color: ${theme.colors.text.tertiary};
  font-size: 10px;
`;

const RunList = styled.div`
  min-height: 0;
  flex: 1;
  overflow: auto;
  padding: 4px 7px 12px;
`;

const RunButton = styled.button<{ $active: boolean }>`
  width: 100%;
  display: block;
  margin: 2px 0;
  padding: 11px 10px;
  border: 0;
  border-radius: 9px;
  background: ${(props) => (props.$active ? '#e4e7eb' : 'transparent')};
  color: inherit;
  text-align: left;
  cursor: pointer;

  &:hover { background: ${(props) => (props.$active ? '#dfe2e7' : '#eceef1')}; }
`;

const RunTitle = styled(Text)`
  display: block;
  margin: 7px 0 4px;
  overflow: hidden;
  color: ${theme.colors.text.primary};
  font-size: 12px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
`;

const DetailPane = styled.main`
  min-width: 0;
  min-height: 0;
  overflow: auto;
  background: #fff;
`;

const DetailHeader = styled.header`
  position: sticky;
  top: 0;
  z-index: 3;
  min-height: 66px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 12px 22px;
  border-bottom: 1px solid ${theme.colors.border.tertiary};
  background: rgb(255 255 255 / 94%);
  backdrop-filter: blur(12px);

  .copy { min-width: 0; }
  strong { display: block; overflow: hidden; font-size: 14px; font-weight: 650; text-overflow: ellipsis; white-space: nowrap; }
  span { display: block; margin-top: 3px; color: ${theme.colors.text.tertiary}; font-size: 10px; }
`;

const DetailBody = styled.div`
  max-width: 960px;
  margin: 0 auto;
  padding: 28px 28px 44px;
`;

const EmptyDetail = styled.div`
  max-width: 520px;
  margin: 20vh auto 0;
  color: ${theme.colors.text.tertiary};
  text-align: center;

  strong { display: block; margin-bottom: 7px; color: ${theme.colors.text.primary}; font-size: 18px; }
  span { font-size: 12px; line-height: 1.6; }
`;

const DetailGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 9px;
  margin-bottom: 18px;
  @media (max-width: ${theme.breakpoints.sm}) { grid-template-columns: 1fr; }
`;

const DetailBox = styled.div`
  min-width: 0;
  padding: 13px 14px;
  border: 1px solid ${theme.colors.border.secondary};
  border-radius: 10px;
  background: #fafbfc;
`;

const DetailList = styled.div`
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 8px;
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
  if (normalized.startsWith('{') || normalized.startsWith('[')) return '已记录一项结构化工具证据，可在技术详情中查看。';
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
      setRuns([]); setSelectedRunId(''); setDetail(null); return;
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
      Toast.error(userFacingError(error, '加载运行列表失败，请稍后重试。'));
    } finally { setLoading(false); }
  }, [projectScope.projectId, requestedRunId, scope, source, status]);

  useEffect(() => { void loadRuns(); }, [loadRuns]);

  useEffect(() => {
    if (!projectScope.projectId || !selectedRunId) { setDetail(null); return; }
    let active = true;
    setDetailLoading(true);
    opsAdminService.getAnalysisTask(projectScope.projectId, selectedRunId, scope)
      .then((response) => { if (active) setDetail(response.data || null); })
      .catch((error) => { if (active) Toast.error(userFacingError(error, '加载运行详情失败，请稍后重试。')); })
      .finally(() => { if (active) setDetailLoading(false); });
    return () => { active = false; };
  }, [projectScope.projectId, scope, selectedRunId]);

  const selectedRun = useMemo(() => runs.find((run) => run.runId === selectedRunId), [runs, selectedRunId]);

  const continueInChat = () => {
    if (!projectScope.projectId) return;
    const params = new URLSearchParams({ projectId: projectScope.projectId });
    if (selectedRun?.sessionId) params.set('sessionId', selectedRun.sessionId);
    if (selectedRun?.runId) params.set('runId', selectedRun.runId);
    navigate(`/chat?${params.toString()}`);
  };

  return (
    <OpsPageShell selectedKey="workbench" maxWidth="none" padding="0">
      <Workbench>
        <RunInbox>
          <InboxHeader>
            <div className="titleRow">
              <h1>工作台</h1>
              <Button theme="borderless" size="small" icon={<IconRefresh />} loading={loading} onClick={() => void loadRuns()}>刷新运行</Button>
            </div>
            <Filters>
              <Select className="project" value={projectScope.projectId || undefined} placeholder="选择项目" onChange={(value) => projectScope.selectProject(String(value || ''))}>
                {projectScope.projects.map((project) => <Option key={project.projectId} value={project.projectId}>{project.name || project.projectId}</Option>)}
              </Select>
              <Select value={source} placeholder="全部来源" onChange={(value) => setSource(String(value || ''))}>
                <Option value="">全部来源</Option><Option value="CHAT">对话</Option><Option value="CHANNEL">渠道</Option><Option value="SCHEDULE">定时</Option><Option value="ALERTMANAGER">告警</Option>
              </Select>
              <Select value={status} placeholder="全部状态" onChange={(value) => setStatus(String(value || ''))}>
                <Option value="">全部状态</Option><Option value="RUNNING">运行中</Option><Option value="SUCCEEDED">已完成</Option><Option value="FAILED">失败</Option><Option value="BLOCKED">已阻塞</Option>
              </Select>
            </Filters>
          </InboxHeader>
          <RunCount>{runs.length} 个运行</RunCount>
          <RunList>
            {loading && runs.length === 0 && <Spin style={{ margin: 24 }} />}
            {!loading && runs.length === 0 && <Text type="tertiary" style={{ display: 'block', padding: 18 }}>当前筛选条件下没有运行。</Text>}
            {runs.map((run) => (
              <RunButton key={run.runId} type="button" $active={run.runId === selectedRunId} onClick={() => setSelectedRunId(run.runId)} aria-label={run.goal || run.runId}>
                <Space wrap spacing={4}><Tag color={statusColor(run.status)}>{statusLabel(run.status)}</Tag><Tag>{sourceLabel(run)}</Tag></Space>
                <RunTitle>{run.goal || run.runId}</RunTitle>
                <Text type="tertiary" size="small">{run.updatedAt || run.createdAt || run.runId}</Text>
              </RunButton>
            ))}
          </RunList>
        </RunInbox>

        <DetailPane>
          {detailLoading && <Spin style={{ margin: 28 }} />}
          {!detailLoading && !selectedRun && <EmptyDetail><strong>选择一个运行</strong><span>查看状态、结论、证据与后续动作。运行详情只在你打开时加载。</span></EmptyDetail>}
          {!detailLoading && selectedRun && (
            <>
              <DetailHeader>
                <div className="copy"><strong>{selectedRun.goal || '运行详情'}</strong><span>{sourceLabel(selectedRun)} · {projectScope.projects.find((project) => project.projectId === selectedRun.projectId)?.name || '当前项目'}</span></div>
                <Space wrap spacing="tight">
                  <Tag color={statusColor(selectedRun.status)}>{statusLabel(selectedRun.status)}</Tag>
                  <Button size="small" icon={<IconComment />} onClick={continueInChat}>{selectedRun.sessionId ? '继续对话' : '打开对话'}</Button>
                  {(detail?.changePackages?.length || 0) > 0 && <Button size="small" onClick={() => navigate(`/changes?projectId=${encodeURIComponent(projectScope.projectId)}`)}>打开变更</Button>}
                </Space>
              </DetailHeader>
              <DetailBody>
                <Paragraph style={{ fontSize: 18, lineHeight: 1.6, marginTop: 0 }}>{detail?.summary || selectedRun.goal || '运行详情'}</Paragraph>
                {detail?.errorMessage && <Paragraph type="danger">{userFacingDetail(detail.errorMessage, '本次执行未成功完成，请查看状态与技术详情。')}</Paragraph>}

                <DetailGrid>
                  <DetailBox><Text type="tertiary">来源</Text><Paragraph>{sourceLabel(selectedRun)}</Paragraph></DetailBox>
                  <DetailBox><Text type="tertiary">项目</Text><Paragraph>{projectScope.projects.find((project) => project.projectId === selectedRun.projectId)?.name || '当前项目'}</Paragraph></DetailBox>
                  <DetailBox><Text type="tertiary">执行方式</Text><Paragraph>{projectScope.projects.find((project) => project.projectId === selectedRun.projectId)?.defaultAgentId === selectedRun.agentId ? '默认助手' : selectedRun.agentId ? '已发布工作流' : '默认助手'}</Paragraph></DetailBox>
                  <DetailBox><Text type="tertiary">关联对话</Text><Paragraph>{selectedRun.sessionId ? '已关联，可继续对话' : '未关联对话'}</Paragraph></DetailBox>
                </DetailGrid>

                {!!detail?.recommendations?.length && <DetailBox><Text strong>建议</Text><DetailList>{detail.recommendations.map((item, index) => <Text key={index}>{item}</Text>)}</DetailList></DetailBox>}
                {!!detail?.unknowns?.length && <DetailBox style={{ marginTop: 12 }}><Text strong>仍需确认</Text><DetailList>{detail.unknowns.map((item, index) => <Text key={index}>{item}</Text>)}</DetailList></DetailBox>}
                {!!detail?.evidence?.length && <DetailBox style={{ marginTop: 12 }}><Text strong>证据摘要（{detail.evidence.length}）</Text><DetailList>{detail.evidence.slice(0, 10).map((item, index) => <Text key={index}>{evidenceSummary(item)}</Text>)}</DetailList></DetailBox>}

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
              </DetailBody>
            </>
          )}
        </DetailPane>
      </Workbench>
    </OpsPageShell>
  );
};
