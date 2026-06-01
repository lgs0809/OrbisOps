import { backgroundFailureMessage } from '../features/skill-evolver/background-failure';
import React, { useState } from 'react';
import styled from 'styled-components';
import { Button, Pagination, Select, SideSheet, Space, Table, Tabs, TabPane, Tag, Toast, Typography } from '@douyinfe/semi-ui';
import { IconRefresh } from '@douyinfe/semi-icons';

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
import {
  useRunSkillEvolverMutation,
  useSkillEvolverJobQuery,
  useSkillEvolverOverviewQuery,
} from '../features/skill-evolver/api/skill-evolver-queries';
import { withEvolutionPatch, evolutionStatus, evolutionDecision, evolutionTargets, evolutionPublishedVersion } from '../features/skill-evolver/evolution-record';
import { AtomicPublicationPanel } from '../features/skill-evolver/AtomicPublicationPanel';
import { MaintenancePanel } from '../features/skill-evolver/MaintenancePanel';
import { BackgroundFailureNotice } from '../features/skill-evolver/BackgroundFailureNotice';
import { BackgroundTaskRetryPanel } from '../features/skill-evolver/BackgroundTaskRetryPanel';
import { AuthoredPublicationNotice } from '../features/skill-evolver/AuthoredPublicationNotice';
import { AuthoredDecisionNotice, currentNoChange } from '../features/skill-evolver/AuthoredDecisionNotice';
import { PublicationRetryPanel } from '../features/skill-evolver/PublicationRetryPanel';
import { SavedExperiencePanel } from '../features/skill-evolver/SavedExperiencePanel';
import { SkillMethodChange } from '../features/skill-evolver/SkillMethodChange';
import { ReadOnlyMarkdown } from '../components/ReadOnlyMarkdown';
import { theme } from '../styles/theme';
import { userFacingError } from '../utils/user-facing-error';
import { useProjectScope } from '../hooks/use-project-scope';

const { Option } = Select;
const { Text } = Typography;

const FilterBar = styled.div`
  display: flex;
  flex-wrap: wrap;
  gap: ${theme.spacing.base};
  align-items: center;
  margin-bottom: ${theme.spacing.base};

  .semi-select,
  .semi-input-wrapper {
    min-width: 180px;
  }
  @media (max-width: 600px) {
    .semi-select { width: 100%; }
  }
`;

const FilterField = styled.div`
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 180px;
  label { color: ${theme.colors.text.tertiary}; font-size: 12px; }
  @media (max-width: 600px) { width: 100%; }
`;

const LearningWorkspace = styled.div`
  min-width: 0;
  .semi-tabs-content { padding-top: ${theme.spacing.base}; }
`;

const DetailBody = styled.div`
  min-width: 0;
  overflow-wrap: anywhere;
  .semi-space { min-width: 0; }
`;

const DesktopTable = styled.div`
  min-width: 0;

  @media (max-width: 760px) {
    display: none;
  }
`;

const MobileRecordList = styled.div`
  display: none;

  @media (max-width: 760px) {
    display: flex;
    flex-direction: column;
    gap: ${theme.spacing.sm};
    min-width: 0;
  }
`;

const MobileRecord = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${theme.spacing.sm};
  min-width: 0;
  padding: ${theme.spacing.base};
  border: 1px solid ${theme.colors.border.secondary};
  border-radius: ${theme.borderRadius.base};
  background: ${theme.colors.bg.primary};

  .semi-typography {
    min-width: 0;
    overflow-wrap: anywhere;
    word-break: break-word;
  }
`;

const MobileRecordHeader = styled.div`
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: ${theme.spacing.sm};
  min-width: 0;
`;

const MobileMeta = styled.div`
  display: grid;
  grid-template-columns: minmax(76px, auto) minmax(0, 1fr);
  gap: ${theme.spacing.xs} ${theme.spacing.sm};
  min-width: 0;
`;

const MobilePagination = styled.div`
  display: flex;
  justify-content: center;
  padding-top: ${theme.spacing.sm};
`;

const MOBILE_PAGE_SIZE = 8;

const asText = (value: unknown, fallback = '-') => {
  if (value === undefined || value === null || value === '') return fallback;
  return String(value);
};

const statusColor = (status?: string) => {
  if (status === 'DONE' || status === 'COMPLETED' || status === 'APPLIED' || status === 'ACTIVE') return 'green';
  if (status === 'FAILED') return 'red';
  if (status === 'RUNNING' || status === 'CANARY' || status === 'SHADOW') return 'blue';
  if (status?.startsWith('SKIP')) return 'grey';
  return 'orange';
};

const statusLabel = (status?: string) => {
  switch (String(status || '').toUpperCase()) {
    case 'PENDING':
      return '已排队，等待自动分析';
    case 'RUNNING':
      return '分析中';
    case 'DONE':
    case 'COMPLETED':
      return '整理完成';
    case 'APPLIED':
      return '已完成';
    case 'DISABLED':
      return '生成时自动发布已关闭';
    case 'FAILED':
      return '失败';
    case 'READY':
      return '检查通过，等待自动发布';
    case 'PENDING_INDEX':
      return '版本已保存，等待检索就绪';
    case 'CANARY':
      return '历史灰度记录';
    case 'SHADOW':
      return '历史影子评测记录';
    case 'ACTIVE':
      return '已生效';
    case 'ROLLED_BACK':
      return '已回滚';
    case 'STAGED':
      return '整组版本已保存，等待检索就绪';
    case 'VALIDATION_FAILED':
      return '自动检查未通过';
    case 'POLICY_REJECTED':
      return '安全策略拒绝';
    case 'SKIPPED':
      return '本轮未发布';
    default:
      return asText(status);
  }
};

const triggerReasonLabel = (reason?: string) => {
  switch (String(reason || '').toUpperCase()) {
    case 'EXPERIENCE_GROUPING_BACKFILL':
      return '按新版规则整理已有验收经验';
    case 'RUN_COMPLETED_BACKGROUND':
      return '任务完成后后台整理';
    case 'AGENT_RUN_COMPLETED':
      return '运维对话完成后自动学习';
    case 'GRAPH_FINAL_REPORT_COMPLETED':
      return '最终报告完成后自动学习';
    case 'USER_EXPLICIT_REMEMBER':
      return '对话中要求沉淀方法';
    case 'MANUAL_RETRY':
      return '修复后重新分析';
    case 'MANUAL_SELECTED_RUN':
      return '从历史对话补选';
    case 'MANUAL':
      return '从历史对话补选';
    default:
      return asText(reason);
  }
};

const decisionColor = (decision?: string) => {
  if (decision === 'CREATE_SKILL' || decision === 'PATCH_SKILL') return 'blue';
  if (decision?.startsWith('SKIP')) return 'grey';
  if (decision === 'FAILED') return 'red';
  return 'green';
};

const decisionLabel = (decision?: string) => {
  switch (String(decision || '').toUpperCase()) {
    case 'SKIP_INSUFFICIENT_REPEATED_OBSERVATIONS':
      return '经验已保存，等待更多独立成功任务';
    case 'SKIP_INSUFFICIENT_SOURCE_DIVERSITY':
      return '经验已保存，等待不同条件的成功任务';
    case 'SKIP_TASK_OUTCOME_UNVERIFIED':
      return '任务尚未通过验收';
    case 'MERGE_SKILLS':
      return '合并方法';
    case 'SPLIT_SKILL':
      return '拆分方法';
    case 'CREATE':
    case 'CREATE_SKILL':
      return '创建新 Skill';
    case 'CREATE_SKILL_CANDIDATE':
      return '准备创建新 Skill';
    case 'PATCH_SKILL':
    case 'UPDATE':
    case 'UPDATE_SKILL':
      return '更新已有 Skill';
    case 'UPDATE_EVIDENCE_CRITERIA':
      return '补充证据要求';
    case 'UPDATE_SKILL_CANDIDATE':
      return '准备更新已有 Skill';
    case 'NO_CHANGE':
      return '无需变更';
    case 'SKIP_LOW_VALUE':
      return '价值不足，跳过';
    case 'SKIP_DUPLICATE_FROZEN':
    case 'SKIP_SIMILAR_SKILL_FROZEN':
    case 'DUPLICATE_FROZEN_SKIP':
      return '命中冻结 Skill，跳过';
    case 'SKIP_UNSAFE':
      return '安全检查未通过';
    case 'SKIP_NO_SIGNAL':
      return '没有可沉淀内容';
    case 'SKIP_NO_REUSABLE_PATTERN':
      return '本轮未新增可复用方法';
    case 'SKIP_WAITING_FOR_TRUSTED_EVIDENCE':
      return '已记录方法提示，等待真实运行证据';
    case 'SKIP_NO_TOOL_EVIDENCE':
      return '缺少工具证据';
    case 'SKIP_NO_PROJECT':
      return '缺少项目上下文';
    case 'SKIP_WORKER_DISABLED':
      return '自动分析未开启';
    case 'MANUAL_ONLY_SKIP':
      return '目标 Skill 仅允许手动维护';
    case 'MVCC_CONFLICT':
      return '版本已变化，本次未覆盖';
    case 'FAILED':
      return '处理失败';
    default:
      return asText(decision);
  }
};

const skipReasonLabel = (reason?: string) => {
  const background = backgroundFailureMessage(reason);
  if (background) return background;
  switch (String(reason || '').toUpperCase()) {
    case 'SKIP_INSUFFICIENT_REPEATED_OBSERVATIONS':
      return '已保留本次成功经验；发布方法需要至少三个独立成功任务，重试和重复对话不会凑数';
    case 'SKIP_INSUFFICIENT_SOURCE_DIVERSITY':
      return '成功来源还需覆盖至少两种有效条件，达到要求后会继续自动整理';
    case 'SKILL_GROUPING_DEFERRED':
      return '经验归组已暂存，后台会自动重试，不需要等待人工确认';
    case 'SKILL_CONTENT_REVIEW_UNAVAILABLE':
      return '后台内容审查暂时不可用，已保存进度并延后重试，不影响对话';
    case 'SKIP_TASK_OUTCOME_UNVERIFIED':
      return '任务尚未通过验收，当前仅保留审计；验收成功后会自动重新调度';
    case 'SKILL_EVOLUTION_SOURCE_REVOKED':
      return '任务已有新修正或验收证据失效，等待重新验收';
    case 'SKILL_EVOLUTION_LEASE_EXPIRED':
      return '前次执行中断，系统按剩余重试次数恢复';
    case 'SKILL_EVOLUTION_PROPOSAL_PENDING':
      return '同一方法已有待决提案；新证据保留，等待当前提案处理及频率窗口结束';
    case 'SKILL_EVOLUTION_PROPOSAL_HASH_MISMATCH':
      return '已冻结提案的完整性校验失败，需要检查审计记录';
    case 'SKILL_EVOLUTION_MODEL_UNAVAILABLE':
      return '等待已配置的 Terra 模型恢复，不消耗失败重试次数';
    case 'SKILL_EVOLUTION_SOURCE_SET_INVALID':
      return '完整成功来源不足、重复或不属于本次任务，未生成提案';
    case 'SKIP_NO_SIGNAL':
      return '本次运行没有可沉淀信号';
    case 'SKIP_NO_REUSABLE_PATTERN':
      return '本轮没有提出新的方法变更；已有经验与方法保留';
    case 'SKIP_WAITING_FOR_TRUSTED_EVIDENCE':
      return '已保存方法提示；后续同类运维运行产生真实工具证据后会自动继续分析';
    case 'SKIP_NO_TOOL_EVIDENCE':
      return '没有工具证据';
    case 'SKIP_NO_PROJECT':
      return '缺少项目上下文';
    case 'SKIP_WORKER_DISABLED':
      return '自动分析未开启';
    case 'SIMILAR_SKILL_FROZEN':
    case 'SKIP_SIMILAR_SKILL_FROZEN':
    case 'DUPLICATE_FROZEN_SKIP':
      return '相近 Skill 已冻结';
    case 'MANUAL_ONLY_SKIP':
      return 'Skill 设置为手动维护';
    case 'MVCC_CONFLICT':
      return '并发版本冲突，未覆盖';
    default:
      return asText(reason);
  }
};

const field = (record: Record<string, any> | null | undefined, ...keys: string[]) => {
  if (!record) return '';
  for (const key of keys) {
    const value = record[key];
    if (value !== undefined && value !== null && value !== '') {
      return value;
    }
  }
  return '';
};

const contentPreview = (value: unknown) => {
  const text = asText(value, '');
  if (!text) return '暂无内容';
  return text.length > 4000 ? `${text.slice(0, 4000)}\n...[已截断]` : text;
};

const skillSectionLabel = (section?: string) => {
  switch (String(section || '')) {
    case 'routingProfile': return '适用范围';
    case 'routingRules': return '工具与证据路由规则';
    case 'diagnosticRecipe': return '诊断步骤';
    case 'evidenceCriteria': return '证据要求';
    case 'negativeRules': return '禁止做法';
    default: return asText(section, '方法调整');
  }
};

const summaryText = (value: unknown) => {
  if (!value || typeof value !== 'object') return asText(value, '来自一次已完成的运维对话');
  const summary = value as Record<string, unknown>;
  return asText(summary.finalReport || summary.eventSummaries, '来自一次已完成的运维对话');
};

const changeItems = (record: Record<string, any> | null) => (
  Array.isArray(record?.changes) ? record?.changes as Record<string, any>[] : []
);

export const SkillEvolverManagementPage: React.FC = () => {
  const projectScope = useProjectScope();
  const [status, setStatus] = useState('');
  const [projectId, setProjectId] = useState('');
  const overviewQuery = useSkillEvolverOverviewQuery({ status, projectId });
  const runOnceMutation = useRunSkillEvolverMutation();
  const jobs = overviewQuery.data?.jobs || [];
  const patches = overviewQuery.data?.patches || [];
  const [selectedRecord, setSelected] = useState<Record<string, any> | null>(null);
  const selectedJobId = selectedRecord ? asText(field(selectedRecord, 'jobId', 'job_id'), '') : '';
  const selectedJobQuery = useSkillEvolverJobQuery(selectedJobId);
  const selected: Record<string, any> | null = selectedRecord ? withEvolutionPatch({ ...selectedRecord, ...selectedJobQuery.data }, patches) : null;
  const [jobPage, setJobPage] = useState(1);
  const [patchPage, setPatchPage] = useState(1);
  const selectedChanges = changeItems(selected);
  const visibleJobPage = Math.min(jobPage, Math.max(1, Math.ceil(jobs.length / MOBILE_PAGE_SIZE)));
  const visiblePatchPage = Math.min(patchPage, Math.max(1, Math.ceil(patches.length / MOBILE_PAGE_SIZE)));
  const mobileJobs = jobs.slice((visibleJobPage - 1) * MOBILE_PAGE_SIZE, visibleJobPage * MOBILE_PAGE_SIZE);
  const mobilePatches = patches.slice((visiblePatchPage - 1) * MOBILE_PAGE_SIZE, visiblePatchPage * MOBILE_PAGE_SIZE);

  const selectEvolutionRecord = (record: Record<string, any>, includePatch = false) => {
    if (includePatch) {
      setSelected(record);
      return;
    }
    setSelected(withEvolutionPatch(record, patches));
  };

  const refreshOverview = async () => {
    setJobPage(1);
    setPatchPage(1);
    await Promise.all([overviewQuery.refetch(), ...(selectedJobId ? [selectedJobQuery.refetch()] : [])]);
  };

  const runOnce = async () => {
    try {
      const results = await runOnceMutation.mutateAsync();
      setJobPage(1);
      setPatchPage(1);
      if (Array.isArray(results) && results.some((r) => r.reason === 'WORKER_DISABLED')) {
        Toast.warning('自动分析尚未开启，本次没有执行扫描');
      } else {
        Toast.success('自动分析扫描已完成，可查看学习任务和处理结论');
      }
    } catch (error) {
      Toast.error(userFacingError(error, '触发自动分析失败，请稍后重试。'));
    }
  };

  const jobColumns = [
    {
      title: '学习任务',
      dataIndex: 'jobId',
      render: (_: string, record: Record<string, any>) => (
        <Space vertical align="start" spacing={2}>
          <Text strong>{triggerReasonLabel(asText(record.triggerReason || record.trigger_reason))}</Text>
          <Text type="tertiary">系统会基于已验收的完整任务、工具证据和最终结论判断是否值得沉淀。</Text>
        </Space>
      ),
    },
    {
      title: '项目 / 执行角色',
      width: 210,
      render: (_: unknown, record: Record<string, any>) => (
        <Space vertical align="start" spacing={2}>
          <Text>{asText(record.projectId || record.project_id)}</Text>
          <Text type="tertiary">{asText(record.agentId || record.agent_id, '默认运维 Agent')}</Text>
        </Space>
      ),
    },
    {
      title: '来源对话',
      width: 210,
      render: (_: unknown, record: Record<string, any>) => (
        <Space vertical align="start" spacing={2}>
          <Text>{asText(record.sourceSummary || record.source_summary, '已关联一次运维对话')}</Text>
          <Text type="tertiary">查看详情可看到证据摘要和处理结论。</Text>
        </Space>
      ),
    },
    {
      title: '状态',
      width: 120,
      render: (_: unknown, record: Record<string, any>) => <Tag color={statusColor(evolutionStatus(record))}>{statusLabel(evolutionStatus(record))}</Tag>,
    },
    {
      title: '重试',
      dataIndex: 'attempts',
      width: 90,
      render: (value: number) => asText(value, '0'),
    },
    {
      title: '最近错误',
      dataIndex: 'lastError',
      render: (_: string, record: Record<string, any>) => skipReasonLabel(record.lastError || record.last_error),
    },
    {
      title: '操作',
      width: 90,
      render: (_: unknown, record: Record<string, any>) => (
        <Button theme="borderless" size="small" onClick={() => selectEvolutionRecord(record)}>查看</Button>
      ),
    },
  ];

  const patchColumns = [
    {
      title: '学习结果',
      dataIndex: 'patchId',
      width: 280,
      render: (_: string, record: Record<string, any>) => (
        <Space vertical align="start" spacing={2} style={{ width: '100%' }}>
          <Text strong>{decisionLabel(evolutionDecision(record))}</Text>
          <Text type="tertiary" style={{ width: '100%' }} ellipsis={{ rows: 3, showTooltip: true }}>{contentPreview(field(record, 'authoringReason', 'changeSummary', 'change_summary', 'reasonCode', 'reason_code'))}</Text>
        </Space>
      ),
    },
    {
      title: '目标 Skill',
      width: 220,
      render: (_: unknown, record: Record<string, any>) => (
        <Space vertical align="start" spacing={2}>
          <Text>{evolutionTargets(record)}</Text>
          <Text type="tertiary">{asText(record.projectId || record.project_id)}</Text>
        </Space>
      ),
    },
    {
      title: '状态',
      width: 120,
      render: (_: unknown, record: Record<string, any>) => <Tag color={statusColor(evolutionStatus(record))}>{statusLabel(evolutionStatus(record))}</Tag>,
    },
    {
      title: '跳过原因',
      width: 210,
      render: (_: unknown, record: Record<string, any>) => <Text style={{ width: '100%' }} ellipsis={{ rows: 3, showTooltip: true }}>{skipReasonLabel(record.skippedReason || record.skipped_reason)}</Text>,
    },
    {
      title: '本次发布版本',
      width: 150,
      render: (_: unknown, record: Record<string, any>) => evolutionPublishedVersion(record),
    },
    {
      title: '操作',
      width: 90,
      render: (_: unknown, record: Record<string, any>) => (
        <Button theme="borderless" size="small" onClick={() => selectEvolutionRecord(record, true)}>查看</Button>
      ),
    },
  ];

  return (
    <OpsPageShell selectedKey="skill-evolver-management">
      <OpsPageHeader
        title="Skill 自动沉淀"
        description="系统会在有真实工具证据的运维对话完成后自动分析可复用方法；这里用于查看结果、冻结治理和处理失败，不需要人工创建学习任务。"
        extra={
          <Space wrap>
            <Button onClick={runOnce} loading={runOnceMutation.isPending}>立即分析排队任务</Button>
            <Button icon={<IconRefresh />} onClick={refreshOverview} loading={overviewQuery.isFetching}>刷新</Button>
          </Space>
        }
      />

      <OpsSectionCard title="筛选">
        <FilterBar>
          <FilterField>
          <label id="skill-evolver-status-label">学习任务状态</label>
          <Select aria-labelledby="skill-evolver-status-label" placeholder="学习任务状态" value={status} onChange={(value) => {
            setStatus(String(value || ''));
            setJobPage(1);
            setPatchPage(1);
          }}>
            <Option value="">全部状态</Option>
            <Option value="PENDING">已排队</Option>
            <Option value="RUNNING">分析中</Option>
            <Option value="DONE">已完成</Option>
            <Option value="FAILED">失败</Option>
          </Select>
          </FilterField>
          <FilterField>
          <label id="skill-evolver-project-label">筛选项目</label>
          <Select aria-labelledby="skill-evolver-project-label" placeholder="选择项目" value={projectId} loading={projectScope.loading} onChange={(value) => {
            setProjectId(String(value || ''));
            setSelected(null);
            setJobPage(1);
            setPatchPage(1);
          }}>
            <Option value="">全部可见项目</Option>
            {projectScope.projects.map(project => <Option key={project.projectId} value={project.projectId}>{project.name}</Option>)}
          </Select>
          </FilterField>
          {(status || projectId) && <Button theme="borderless" onClick={() => {
            setStatus(''); setProjectId(''); setSelected(null); setJobPage(1); setPatchPage(1);
          }}>清除筛选</Button>}
          {projectScope.error && <Button onClick={projectScope.reloadProjects}>重试加载项目</Button>}
        </FilterBar>
        <Text type="tertiary">任务状态筛选适用于学习任务。Skill 的冻结和手动维护在项目工作区处理；生产落地审批在变更页面完成。</Text>
      </OpsSectionCard>

      {overviewQuery.isError && (
        <OpsSectionCard title="加载失败">
          <OpsEmptyState
            title="Skill Evolver 加载失败"
            description={overviewQuery.error instanceof Error ? overviewQuery.error.message : '请刷新后重试。'}
          />
        </OpsSectionCard>
      )}

      <Text type="tertiary" role="status" aria-live="off">
        {overviewQuery.dataUpdatedAt > 0
          ? `上次同步 ${new Date(overviewQuery.dataUpdatedAt).toLocaleTimeString('zh-CN')}；页面可见时每 10 秒自动同步。`
          : '正在读取后台处理记录…'}
      </Text>

      <LearningWorkspace>
        <Tabs type="line" defaultActiveKey="jobs">
        <TabPane tab="学习任务" itemKey="jobs">
        <OpsSectionCard title={`学习任务 · 已加载 ${jobs.length} 条`}>
          <Text type="tertiary">每类最多加载最近100条记录，可按项目缩小范围。整理完成只代表后台分析结束，发布状态请查看沉淀结果。</Text>
          {jobs.length === 0 && !overviewQuery.isLoading && !overviewQuery.isError ? (
            <OpsEmptyState title="暂无学习任务" description="Agent 完成一次有工具证据和最终结论的运维对话后会自动进入这里。普通聊天和无证据运行不会生成任务。" />
          ) : (
            <>
              <DesktopTable>
                <TableScroll>
                  <Table columns={jobColumns} dataSource={jobs} loading={overviewQuery.isLoading} rowKey={(record) => asText(record?.jobId || record?.job_id || record?.id)} pagination={{ pageSize: 8 }} />
                </TableScroll>
              </DesktopTable>
              <MobileRecordList>
                {overviewQuery.isLoading && <Text role="status">正在读取学习任务…</Text>}
                {mobileJobs.map((record) => (
                  <MobileRecord key={asText(record?.jobId || record?.job_id || record?.id)}>
                    <MobileRecordHeader>
                      <Text strong>{triggerReasonLabel(asText(record.triggerReason || record.trigger_reason))}</Text>
                      <Tag color={statusColor(evolutionStatus(record))}>{statusLabel(evolutionStatus(record))}</Tag>
                    </MobileRecordHeader>
                    <Text type="tertiary">系统会根据这次运维对话的真实工具证据和最终结论判断是否值得沉淀。</Text>
                    <MobileMeta>
                      <Text type="tertiary">项目</Text>
                      <Text>{asText(record.projectId || record.project_id)}</Text>
                      <Text type="tertiary">执行角色</Text>
                      <Text>{asText(record.agentId || record.agent_id, '默认运维 Agent')}</Text>
                      <Text type="tertiary">来源</Text>
                      <Text>{asText(record.sourceSummary || record.source_summary, '已关联一次运维对话')}</Text>
                      <Text type="tertiary">重试</Text>
                      <Text>{asText(record.attempts, '0')}</Text>
                    </MobileMeta>
                    {(record.lastError || record.last_error) && <Text type="danger">最近处理说明：{skipReasonLabel(record.lastError || record.last_error)}</Text>}
                    <Button block onClick={() => selectEvolutionRecord(record)}>查看处理详情</Button>
                  </MobileRecord>
                ))}
                {jobs.length > 0 && <MobilePagination>
                  <Pagination total={jobs.length} pageSize={MOBILE_PAGE_SIZE} currentPage={visibleJobPage} onPageChange={setJobPage} showSizeChanger={false} size="small" />
                </MobilePagination>}
              </MobileRecordList>
            </>
          )}
        </OpsSectionCard>
        </TabPane>
        <TabPane tab="沉淀结果" itemKey="results">
        <OpsSectionCard title="学习结果">
          <Text type="tertiary">已加载 {patches.length} 条最近记录。查看创建、更新、跳过原因和当前发布状态。</Text>
          {patches.length === 0 && !overviewQuery.isLoading && !overviewQuery.isError ? (
            <OpsEmptyState title="暂无学习结果" description="自动分析处理后会记录创建 Skill、更新 Skill、无需变更或跳过原因。" />
          ) : (
            <>
              <DesktopTable>
                <TableScroll>
                  <Table columns={patchColumns} dataSource={patches} loading={overviewQuery.isLoading} rowKey={(record) => asText(record?.patchId || record?.patch_id || record?.id)} pagination={{ pageSize: 8 }} />
                </TableScroll>
              </DesktopTable>
              <MobileRecordList>
                {overviewQuery.isLoading && <Text role="status">正在读取沉淀结果…</Text>}
                {mobilePatches.map((record) => (
                  <MobileRecord key={asText(record?.patchId || record?.patch_id || record?.id)}>
                    <MobileRecordHeader>
                      <Text strong>{decisionLabel(evolutionDecision(record))}</Text>
                      <Tag color={statusColor(evolutionStatus(record))}>{statusLabel(evolutionStatus(record))}</Tag>
                    </MobileRecordHeader>
                    <Text type="tertiary">
                      {contentPreview(field(record, 'authoringReason', 'changeSummary', 'change_summary', 'reasonCode', 'reason_code')).slice(0, 180)}
                    </Text>
                    <MobileMeta>
                      <Text type="tertiary">目标 Skill</Text>
                      <Text>{evolutionTargets(record)}</Text>
                      <Text type="tertiary">项目</Text>
                      <Text>{asText(record.projectId || record.project_id)}</Text>
                      <Text type="tertiary">处理结论</Text>
                      <Text>{decisionLabel(evolutionDecision(record))}</Text>
                      <Text type="tertiary">本次发布</Text>
                      <Text>{evolutionPublishedVersion(record)}</Text>
                      <Text type="tertiary">原因</Text>
                      <Text>{skipReasonLabel(record.skippedReason || record.skipped_reason)}</Text>
                    </MobileMeta>
                    <Button block onClick={() => selectEvolutionRecord(record, true)}>查看学习结果</Button>
                  </MobileRecord>
                ))}
                {patches.length > 0 && <MobilePagination>
                  <Pagination total={patches.length} pageSize={MOBILE_PAGE_SIZE} currentPage={visiblePatchPage} onPageChange={setPatchPage} showSizeChanger={false} size="small" />
                </MobilePagination>}
              </MobileRecordList>
            </>
          )}
        </OpsSectionCard>
        </TabPane>
        <TabPane tab="版本维护" itemKey="maintenance">
      <AtomicPublicationPanel projectId={projectId} />
      <MaintenancePanel projectId={projectId} />
        </TabPane>
        </Tabs>
      </LearningWorkspace>
      <SideSheet title="学习记录详情" visible={Boolean(selected)} width="min(880px, 100vw)" onCancel={() => setSelected(null)} closeOnEsc>
      <DetailBody>
        {selected ? (
          <Space vertical align="start" spacing="medium" style={{ width: '100%' }}>
            <Space wrap>
              {selected.decision && <Tag color={decisionColor(evolutionDecision(selected))}>{decisionLabel(evolutionDecision(selected))}</Tag>}
              {selected.status && <Tag color={statusColor(evolutionStatus(selected))}>{statusLabel(evolutionStatus(selected))}</Tag>}
              {(selected.skippedReason || selected.skipped_reason) && (
                <Tag color="grey">{skipReasonLabel(selected.skippedReason || selected.skipped_reason)}</Tag>
              )}
            </Space>
            <Text type="tertiary">
              Skill 自动沉淀只更新“长期方法”，不能授权工具、不能跳过审批、不能直接落地。要冻结或手动编辑 Skill，请到“通用 Skill / 项目工作区”的 Skill 管理入口。
            </Text>
            <OpsResponsiveGrid $min="280px">
              <OpsSectionCard title="这次想沉淀什么">
                {selectedChanges.length > 0 ? (
                  <Space vertical align="start" spacing="medium" style={{ width: '100%' }}>
                    {selectedChanges.map((change, index) => {
                      return (
                        <Space key={`${asText(change.key)}-${index}`} vertical align="start" spacing={4} style={{ width: '100%' }}>
                          <Text strong>{skillSectionLabel(change.section)}：{asText(change.key, `调整 ${index + 1}`)}</Text>
                          <SkillMethodChange value={change?.value} section={change?.section} />
                        </Space>
                      );
                    })}
                  </Space>
                ) : selectedJobQuery.data?.savedExperience ? (
                  <SavedExperiencePanel experience={selectedJobQuery.data.savedExperience} />
                ) : (
                  <Text style={{ whiteSpace: 'pre-wrap' }}>
                    {contentPreview(field(selected, 'candidateContent', 'candidate_content', 'generatedContent', 'generated_content', 'newContent', 'new_content', 'content'))}
                  </Text>
                )}
              </OpsSectionCard>
              <OpsSectionCard title="处理结论">
                <Space vertical align="start">
                  <Text>决策：{decisionLabel(currentNoChange(selectedJobQuery.data) ? 'NO_CHANGE' : evolutionDecision(selected))}</Text>
                  {selected.publication && <Text>当前发布状态：{statusLabel(evolutionStatus(selected))}</Text>}
                  <Text>生成时状态：{statusLabel(asText(field(selected, 'status')))}</Text>
                  <BackgroundFailureNotice job={selectedJobQuery.data} />
                  <BackgroundTaskRetryPanel job={selectedJobQuery.data as import('../features/skill-evolver/api/skill-evolver-queries').RetryableSkillJob | undefined} />
                  <AuthoredPublicationNotice publication={selectedJobQuery.data?.authoredPublication} />
                  <AuthoredDecisionNotice job={selectedJobQuery.data} />
                  {selected.candidateId && field(selected, 'projectId', 'project_id') && <PublicationRetryPanel
                    candidateId={String(selected.candidateId)} projectId={String(field(selected, 'projectId', 'project_id'))} />}
                  {!currentNoChange(selectedJobQuery.data) && <Text>原因：{skipReasonLabel(asText(field(selected, 'skippedReason', 'skipped_reason', 'reasonCode', 'reason_code')))}</Text>}
                  <Text>目标 Skill：{currentNoChange(selectedJobQuery.data) ? '本轮无需新增或改写 Skill' : evolutionTargets(selected)}</Text>
                  <div><Text strong>来源报告</Text><ReadOnlyMarkdown text={summaryText(field(selected, 'sourceSummary', 'source_summary', 'sourceTraceSummary', 'source_trace_summary'))} /></div>
                  <Text>已验收任务来源：{!selectedJobId ? '该历史记录未关联后台任务'
                    : selectedJobQuery.isPending ? '正在读取任务来源…'
                    : selectedJobQuery.isError ? '暂时无法读取，请刷新后重试'
                    : asText(selectedJobQuery.data?.acceptedSourceId, '尚无已验收的任务来源')}</Text>
                </Space>
              </OpsSectionCard>
            </OpsResponsiveGrid>
            {(field(selected, 'oldContent', 'old_content') || field(selected, 'newContent', 'new_content') || field(selected, 'diff')) && (
              <OpsResponsiveGrid $min="320px">
                <OpsSectionCard title="旧 Skill 内容">
                  <Text style={{ whiteSpace: 'pre-wrap' }}>{contentPreview(field(selected, 'oldContent', 'old_content'))}</Text>
                </OpsSectionCard>
                <OpsSectionCard title="新 Skill 内容">
                  <Text style={{ whiteSpace: 'pre-wrap' }}>{contentPreview(field(selected, 'newContent', 'new_content'))}</Text>
                </OpsSectionCard>
                <OpsSectionCard title="变化摘要">
                  <Text style={{ whiteSpace: 'pre-wrap' }}>{contentPreview(field(selected, 'diff', 'changeSummary', 'change_summary'))}</Text>
                </OpsSectionCard>
              </OpsResponsiveGrid>
            )}
            {(field(selected, 'toolEvidence', 'tool_evidence', 'sourceTraceSummary', 'source_trace_summary', 'validationResult', 'validation_result')) && (
              <OpsAdvancedPreview title="证据和安全检查" description="用于判断这条 Skill 为什么可以沉淀或为什么被跳过。">
                <JsonBlock>{JSON.stringify({
                  toolEvidence: field(selected, 'toolEvidence', 'tool_evidence'),
                  sourceTraceSummary: field(selected, 'sourceTraceSummary', 'source_trace_summary'),
                  validationResult: field(selected, 'validationResult', 'validation_result'),
                  safetyCheckResult: field(selected, 'safetyCheckResult', 'safety_check_result'),
                  similaritySearchResult: field(selected, 'similaritySearchResult', 'similarity_search_result'),
                }, null, 2)}</JsonBlock>
              </OpsAdvancedPreview>
            )}
            <OpsAdvancedPreview title="审计原始字段" description="用于排查自动分析、版本冲突、冻结和内容差异。默认折叠。">
              <JsonBlock>{JSON.stringify(selected, null, 2)}</JsonBlock>
            </OpsAdvancedPreview>
          </Space>
        ) : (
          <Text type="tertiary">选择一条学习任务或学习结果后，这里会展示它为什么创建、更新、跳过或失败。</Text>
        )}
      </DetailBody>
      </SideSheet>

    </OpsPageShell>
  );
};

export default SkillEvolverManagementPage;
