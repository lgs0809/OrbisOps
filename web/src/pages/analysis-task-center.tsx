import React, { useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import styled from 'styled-components';
import {
  Button,
  Empty,
  Modal,
  Select,
  Space,
  Spin,
  Table,
  Tag,
  TextArea,
  Toast,
  Typography,
} from '@douyinfe/semi-ui';

import {
  JsonBlock,
  OpsAdvancedPreview,
  OpsEmptyState,
  OpsPageHeader,
  OpsPageShell,
  OpsResponsiveGrid,
  OpsSectionCard,
  TableScroll,
} from '../components/ops-layout';
import { WorkflowApprovalPanel } from '../features/execution/components/WorkflowApprovalPanel';
import { isAdminUser } from '../services/auth-session';
import type { OpsAnalysisTaskSummary } from '../services/ops-admin-service';
import {
  useAnalysisTaskDetailQuery,
  useAnalysisTaskFeedbackMutation,
  useAnalysisTaskProjectsQuery,
  useAnalysisTasksQuery,
} from '../features/analysis-tasks/api/analysis-task-queries';
import { theme } from '../styles/theme';

const { Option } = Select;
const { Text, Title } = Typography;

const Toolbar = styled.div`
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: ${theme.spacing.base};
  width: 100%;
  min-width: 0;
`;

const DetailColumns = styled.div`
  display: grid;
  grid-template-columns: minmax(0, 1.1fr) minmax(320px, 0.9fr);
  gap: ${theme.spacing.base};
  width: 100%;
  min-width: 0;

  @media (max-width: ${theme.breakpoints.lg}) {
    grid-template-columns: minmax(0, 1fr);
  }
`;

const Summary = styled.div`
  white-space: pre-wrap;
  line-height: 1.7;
  overflow-wrap: anywhere;
`;

const ReadableList = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${theme.spacing.sm};
  min-width: 0;
`;

const ReadableItem = styled.div`
  min-width: 0;
  padding: ${theme.spacing.sm} ${theme.spacing.base};
  border: 1px solid ${theme.colors.border.secondary};
  border-radius: ${theme.borderRadius.base};
  background: ${theme.colors.bg.secondary};
  line-height: 1.6;
  overflow-wrap: anywhere;
`;

const Meta = styled.dl`
  display: grid;
  grid-template-columns: 110px minmax(0, 1fr);
  gap: 8px 12px;
  margin: 0;

  dt { color: ${theme.colors.text.tertiary}; }
  dd { min-width: 0; margin: 0; overflow-wrap: anywhere; }
`;

const statusLabel = (status?: string) => {
  const normalized = String(status || '').toUpperCase();
  return ({
    PENDING: '等待执行', RUNNING: '执行中', WAITING_APPROVAL: '等待审批', SUCCEEDED: '已完成', FAILED: '失败',
    CANCELED: '已取消', CANCELLED: '已取消', RECOVERING: '恢复中',
  } as Record<string, string>)[normalized] || normalized || '未知';
};

const statusColor = (status?: string) => {
  const normalized = String(status || '').toUpperCase();
  if (normalized === 'SUCCEEDED') return 'green' as const;
  if (normalized === 'RUNNING' || normalized === 'RECOVERING') return 'blue' as const;
  if (normalized === 'WAITING_APPROVAL') return 'amber' as const;
  if (normalized === 'FAILED') return 'red' as const;
  if (normalized === 'CANCELED' || normalized === 'CANCELLED') return 'grey' as const;
  return 'orange' as const;
};

const sourceLabel = (source?: string) => ({
  CHAT: '对话', CHANNEL: '消息渠道', ALERTMANAGER: '告警', ALERT: '告警', SCHEDULE: '巡检',
} as Record<string, string>)[String(source || '').toUpperCase()] || source || '未知来源';

const field = (record: Record<string, any>, ...keys: string[]) => {
  for (const key of keys) {
    const value = record?.[key];
    if (value !== undefined && value !== null && value !== '') return value;
  }
  return undefined;
};

const text = (value: unknown, fallback = '-') => {
  if (value === undefined || value === null || value === '') return fallback;
  if (typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean') return String(value);
  return JSON.stringify(value);
};

const preview = (record: Record<string, any>) => text(field(
  record,
  'summary', 'preview', 'previewText', 'preview_text', 'content', 'message', 'reason',
));

const formatTime = (value?: string) => value ? new Date(value).toLocaleString() : '-';

export const AnalysisTaskCenterPage: React.FC = () => {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const requestedProjectId = searchParams.get('projectId')?.trim() || '';
  const requestedRunId = searchParams.get('runId')?.trim() || '';
  const deepLinkOpened = useRef('');
  const admin = isAdminUser();
  const scope = admin ? 'admin' as const : 'user' as const;
  const [projectId, setProjectId] = useState('');
  const [source, setSource] = useState('');
  const [status, setStatus] = useState('');
  const [selectedTask, setSelectedTask] = useState<{ projectId: string; runId: string } | null>(null);
  const [detailVisible, setDetailVisible] = useState(false);
  const [feedbackComment, setFeedbackComment] = useState('');

  const projectsQuery = useAnalysisTaskProjectsQuery(scope);
  const projects = projectsQuery.data || [];
  const effectiveProjectId = projectId || requestedProjectId || projects[0]?.projectId || '';
  const tasksQuery = useAnalysisTasksQuery(scope, effectiveProjectId, source, status);
  const tasks = tasksQuery.data || [];
  const detailQuery = useAnalysisTaskDetailQuery(
    scope,
    selectedTask?.projectId || '',
    selectedTask?.runId || '',
    detailVisible,
  );
  const feedbackMutation = useAnalysisTaskFeedbackMutation();
  const selected = detailQuery.data || null;
  const loading = tasksQuery.isFetching;
  const detailLoading = detailQuery.isFetching;
  const feedbackLoading = feedbackMutation.isPending;
  const listError = projectsQuery.error instanceof Error
    ? projectsQuery.error.message
    : tasksQuery.error instanceof Error
      ? tasksQuery.error.message
      : '';
  const detailError = detailQuery.error instanceof Error ? detailQuery.error.message : '';

  const openDetail = (task: OpsAnalysisTaskSummary) => {
    setSelectedTask({ projectId: task.projectId, runId: task.runId });
    setFeedbackComment('');
    setDetailVisible(true);
  };

  const sendFeedback = async (feedbackType: 'HELPFUL' | 'INACCURATE' | 'INSUFFICIENT_EVIDENCE') => {
    if (!selected) return;
    try {
      await feedbackMutation.mutateAsync({
        projectId: selected.projectId,
        runId: selected.runId,
        feedbackType,
        comment: feedbackComment.trim(),
        scope,
      });
      setFeedbackComment('');
      Toast.success('反馈已记录，并用于后续 Skill 效果评估');
    } catch (error) {
      Toast.error(error instanceof Error ? error.message : '记录反馈失败');
    }
  };
  useEffect(() => {
    if (!requestedRunId || deepLinkOpened.current === requestedRunId) return;
    const target = tasks.find((task) => task.runId === requestedRunId);
    if (!target) return;
    deepLinkOpened.current = requestedRunId;
    void openDetail(target);
  }, [requestedRunId, tasks]);

  const counts = useMemo(() => ({
    running: tasks.filter((item) => ['PENDING', 'RUNNING', 'WAITING_APPROVAL', 'RECOVERING'].includes(item.status)).length,
    succeeded: tasks.filter((item) => item.status === 'SUCCEEDED').length,
    failed: tasks.filter((item) => item.status === 'FAILED').length,
  }), [tasks]);

  return (
    <OpsPageShell selectedKey="analysis-tasks">
      <OpsPageHeader
        title="分析任务"
        description="统一查看对话、消息渠道、告警和巡检触发的运维分析。每条任务都关联真实证据、工具结果、ChangePackage 和 Skill 使用记录。"
        primaryAction={<Button loading={loading} onClick={() => tasksQuery.refetch()}>刷新</Button>}
      />

      <OpsResponsiveGrid $min="220px">
        <OpsSectionCard title="进行中"><Title heading={4} style={{ margin: 0 }}>{counts.running}</Title><Text type="tertiary">等待、执行或恢复中的任务</Text></OpsSectionCard>
        <OpsSectionCard title="已完成"><Title heading={4} style={{ margin: 0 }}>{counts.succeeded}</Title><Text type="tertiary">已有可追溯分析结论</Text></OpsSectionCard>
        <OpsSectionCard title="失败"><Title heading={4} style={{ margin: 0 }}>{counts.failed}</Title><Text type="tertiary">可在详情查看失败点和证据缺口</Text></OpsSectionCard>
      </OpsResponsiveGrid>

      <OpsSectionCard title="任务列表">
        <Toolbar>
          <Select value={effectiveProjectId} placeholder="选择项目" filter style={{ width: 240 }} onChange={(value) => setProjectId(String(value || ''))}>
            {projects.map((project) => <Option key={project.projectId} value={project.projectId}>{project.name}</Option>)}
          </Select>
          <Select value={source} style={{ width: 160 }} onChange={(value) => setSource(String(value || ''))}>
            <Option value="">全部来源</Option><Option value="CHAT">对话</Option><Option value="CHANNEL">消息渠道</Option><Option value="ALERTMANAGER">告警</Option><Option value="SCHEDULE">巡检</Option>
          </Select>
          <Select value={status} style={{ width: 160 }} onChange={(value) => setStatus(String(value || ''))}>
            <Option value="">全部状态</Option><Option value="PENDING">等待执行</Option><Option value="RUNNING">执行中</Option><Option value="WAITING_APPROVAL">等待审批</Option><Option value="SUCCEEDED">已完成</Option><Option value="FAILED">失败</Option><Option value="CANCELED">已取消</Option>
          </Select>
        </Toolbar>
        {listError && <OpsEmptyState title="加载分析任务失败" description={listError} />}
        <Spin spinning={loading}>
          {!listError && (tasks.length === 0 ? <OpsEmptyState title="暂无分析任务" description="从 AI 对话、消息渠道、告警或巡检发起一次运维分析后，会在这里形成统一任务记录。" /> : (
            <TableScroll>
              <Table
                rowKey="runId"
                dataSource={tasks}
                pagination={{ pageSize: 12 }}
                scroll={{ x: 820 }}
                columns={[
                  { title: '目标', dataIndex: 'goal', width: 360, render: (value: string) => <Text strong ellipsis={{ showTooltip: true }}>{value || '未命名分析任务'}</Text> },
                  { title: '来源', dataIndex: 'source', width: 110, render: (value: string) => sourceLabel(value) },
                  { title: '状态', dataIndex: 'status', width: 110, render: (value: string) => <Tag color={statusColor(value)}>{statusLabel(value)}</Tag> },
                  { title: '更新时间', dataIndex: 'updatedAt', width: 180, render: (value: string) => formatTime(value) },
                  { title: '操作', key: 'action', fixed: 'right' as const, width: 90, render: (_: unknown, task: OpsAnalysisTaskSummary) => <Button size="small" onClick={() => openDetail(task)}>查看</Button> },
                ]}
              />
            </TableScroll>
          ))}
        </Spin>
      </OpsSectionCard>

      <Modal title="分析任务详情" visible={detailVisible} footer={null} width={1160} style={{ maxWidth: '96vw' }} onCancel={() => setDetailVisible(false)}>
        <Spin spinning={detailLoading}>
          {detailError ? <OpsEmptyState title="加载任务详情失败" description={detailError} /> : !selected ? <div style={{ minHeight: 160 }} /> : (
            <div style={{ maxHeight: '76vh', overflow: 'auto', paddingRight: 8 }}>
              <DetailColumns>
                <Space vertical align="start" spacing="medium" style={{ width: '100%', minWidth: 0 }}>
                  <OpsSectionCard title="分析结论">
                    <Space wrap style={{ marginBottom: 12 }}>
                      <Tag>{sourceLabel(selected.source)}</Tag>
                      <Tag color={statusColor(selected.status)}>{statusLabel(selected.status)}</Tag>
                      <Tag color={selected.evidenceSufficient ? 'green' : 'orange'}>{selected.evidenceSufficient ? '证据已记录' : '证据不足'}</Tag>
                    </Space>
                    <Summary>{selected.summary || selected.errorMessage || '当前任务尚未产生最终结论。'}</Summary>
                  </OpsSectionCard>

                  {[
                    ['候选原因', selected.hypotheses],
                    ['已排除项', selected.excludedFindings],
                    ['仍待确认', selected.unknowns],
                    ['建议下一步', selected.recommendations],
                  ].map(([label, values]) => (
                    <OpsSectionCard key={String(label)} title={String(label)}>
                      {Array.isArray(values) && values.length > 0 ? <ReadableList>{values.map((item, index) => <ReadableItem key={`${label}-${index}`}>{item}</ReadableItem>)}</ReadableList> : <Text type="tertiary">暂无记录</Text>}
                    </OpsSectionCard>
                  ))}

                  <OpsSectionCard title={`证据 (${selected.evidence?.length || 0})`}>
                    {!selected.evidence?.length ? <Empty title="暂无可信证据" description="工具结果缺少 resultId/outputHash 时不会进入可信证据列表。" /> : (
                      <ReadableList>{selected.evidence.map((item, index) => <ReadableItem key={text(field(item, 'evidenceId', 'evidence_id'), String(index))}><Text strong>{text(field(item, 'sourceType', 'source_type'), '工具证据')}</Text><div>{preview(item)}</div><Text type="tertiary">结果：{text(field(item, 'toolResultId', 'tool_result_id', 'sourceId', 'source_id'))} · {text(field(item, 'outputHash', 'output_hash'))}</Text></ReadableItem>)}</ReadableList>
                    )}
                  </OpsSectionCard>

                  <OpsSectionCard title={`工具调用 (${selected.toolResults?.length || 0})`}>
                    {!selected.toolResults?.length ? <Text type="tertiary">暂无工具调用</Text> : <ReadableList>{selected.toolResults.map((item, index) => <ReadableItem key={text(field(item, 'resultId', 'result_id'), String(index))}><Space wrap><Text strong>{text(field(item, 'toolName', 'tool_name'), '未命名工具')}</Text><Tag>{text(field(item, 'status'), 'UNKNOWN')}</Tag></Space><div>{preview(item)}</div><Text type="tertiary">resultId：{text(field(item, 'resultId', 'result_id'))} · outputHash：{text(field(item, 'outputHash', 'output_hash'))}</Text></ReadableItem>)}</ReadableList>}
                  </OpsSectionCard>
                </Space>

                <Space vertical align="start" spacing="medium" style={{ width: '100%', minWidth: 0 }}>
                  <OpsSectionCard title="任务信息"><Meta><dt>项目</dt><dd>{projects.find((item) => item.projectId === selected.projectId)?.name || selected.projectId}</dd><dt>来源</dt><dd>{sourceLabel(selected.source)}</dd><dt>开始</dt><dd>{formatTime(selected.createdAt)}</dd><dt>更新</dt><dd>{formatTime(selected.updatedAt)}</dd></Meta></OpsSectionCard>

                  <WorkflowApprovalPanel
                    runId={selected.runId}
                    projectId={selected.projectId}
                    scope={scope}
                    onChanged={() => {
                      void tasksQuery.refetch();
                      void detailQuery.refetch();
                    }}
                  />

                  <OpsSectionCard title={`关联执行包 (${selected.changePackages?.length || 0})`}>
                    {!selected.changePackages?.length ? <Text type="tertiary">本次分析未生成执行包</Text> : <ReadableList>{selected.changePackages.map((item) => {
                      const params = new URLSearchParams({ projectId: selected.projectId, packageId: item.packageId });
                      return <ReadableItem key={item.packageId}><Space wrap><Text strong>{item.summary || item.objective || '未命名执行包'}</Text><Tag>{item.status}</Tag><Tag>{item.riskLevel || 'UNKNOWN'}</Tag></Space><div>版本 {item.version} · {item.targetEnvironment || '未声明环境'}</div><Button size="small" style={{ marginTop: 8 }} onClick={() => navigate(`/executions?${params.toString()}`)}>查看执行包</Button></ReadableItem>;
                    })}</ReadableList>}
                  </OpsSectionCard>

                  <OpsSectionCard title={`关联事件 (${selected.incidents?.length || 0})`}>
                    {!selected.incidents?.length ? <Text type="tertiary">没有关联告警事件</Text> : <ReadableList>{selected.incidents.map((item, index) => <ReadableItem key={text(field(item, 'incidentId'), String(index))}><Text strong>{text(field(item, 'title'), '未命名事件')}</Text><div>{text(field(item, 'serviceName'), '未知服务')} · {text(field(item, 'severity'), 'UNKNOWN')} · {text(field(item, 'status'))}</div></ReadableItem>)}</ReadableList>}
                  </OpsSectionCard>

                  <OpsSectionCard title={`本次使用的方法 (${selected.skillUsages?.length || 0})`}>
                    {!selected.skillUsages?.length ? <Text type="tertiary">本次没有选中 Skill</Text> : <ReadableList>{selected.skillUsages.map((item, index) => <ReadableItem key={`${text(field(item, 'skillId', 'skill_id'))}-${index}`}><Text strong>{text(field(item, 'skillName', 'skill_name', 'skillId', 'skill_id'), '未命名 Skill')}</Text><div>版本 {text(field(item, 'skillVersion', 'skill_version', 'version'))} · 使用节点 {text(field(item, 'usedAtNode', 'used_at_node'))}</div></ReadableItem>)}</ReadableList>}
                  </OpsSectionCard>

                  <OpsSectionCard title="结果反馈">
                    <TextArea value={feedbackComment} onChange={setFeedbackComment} placeholder="可选：说明哪些结论有帮助、哪里不准确，或缺少什么证据" autosize rows={3} />
                    <Space wrap style={{ marginTop: 12 }}>
                      <Button loading={feedbackLoading} onClick={() => sendFeedback('HELPFUL')}>有帮助</Button>
                      <Button loading={feedbackLoading} onClick={() => sendFeedback('INACCURATE')}>结论不准确</Button>
                      <Button loading={feedbackLoading} onClick={() => sendFeedback('INSUFFICIENT_EVIDENCE')}>证据不足</Button>
                    </Space>
                    {!!selected.feedback?.length && <Text type="tertiary" style={{ display: 'block', marginTop: 8 }}>已记录 {selected.feedback.length} 条反馈，后续 Skill 评测会使用这些结果。</Text>}
                  </OpsSectionCard>

                  <OpsAdvancedPreview title="完整运行轨迹" description="仅用于技术排障；日常判断请以上方摘要、证据和执行包为准。"><JsonBlock>{JSON.stringify({ runId: selected.runId, sessionId: selected.sessionId, agentId: selected.agentId, agentVersion: selected.agentVersion, executionHarness: selected.executionHarness, technicalError: selected.technicalError, events: selected.events, feedback: selected.feedback }, null, 2)}</JsonBlock></OpsAdvancedPreview>
                </Space>
              </DetailColumns>
            </div>
          )}
        </Spin>
      </Modal>
    </OpsPageShell>
  );
};
