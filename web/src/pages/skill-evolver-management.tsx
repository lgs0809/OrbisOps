import React, { useCallback, useEffect, useState } from 'react';
import styled from 'styled-components';
import { Button, Input, Pagination, Select, Space, Table, Tag, Toast, Typography } from '@douyinfe/semi-ui';
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
import { opsAdminService } from '../services/ops-admin-service';
import { theme } from '../styles/theme';

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
`;

const TwoPanels = styled.div`
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: ${theme.spacing.base};
  width: 100%;
  min-width: 0;
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
  if (status === 'DONE' || status === 'APPLIED' || status === 'ACTIVE') return 'green';
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
    case 'APPLIED':
      return '已完成';
    case 'FAILED':
      return '失败';
    case 'CANARY':
      return '小流量验证中';
    case 'SHADOW':
      return '历史案例验证中';
    case 'ACTIVE':
      return '已生效';
    case 'VALIDATION_FAILED':
      return '自动检查未通过';
    case 'POLICY_REJECTED':
      return '安全策略拒绝';
    case 'SKIPPED':
      return '已跳过';
    default:
      return asText(status);
  }
};

const triggerReasonLabel = (reason?: string) => {
  switch (String(reason || '').toUpperCase()) {
    case 'AGENT_RUN_COMPLETED':
      return '运维对话完成后自动学习';
    case 'GRAPH_FINAL_REPORT_COMPLETED':
      return '最终报告完成后自动学习';
    case 'USER_EXPLICIT_REMEMBER':
      return '对话中要求沉淀方法';
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
    case 'CREATE_SKILL':
      return '创建新 Skill';
    case 'CREATE_SKILL_CANDIDATE':
      return '准备创建新 Skill';
    case 'PATCH_SKILL':
    case 'UPDATE_SKILL':
      return '更新已有 Skill';
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
      return '没有稳定的可复用方法';
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
  switch (String(reason || '').toUpperCase()) {
    case 'SKIP_NO_SIGNAL':
      return '本次运行没有可沉淀信号';
    case 'SKIP_NO_REUSABLE_PATTERN':
      return '本次结果不足以形成稳定、可复用的方法';
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
  const [loading, setLoading] = useState(false);
  const [jobs, setJobs] = useState<Record<string, any>[]>([]);
  const [patches, setPatches] = useState<Record<string, any>[]>([]);
  const [status, setStatus] = useState('');
  const [projectId, setProjectId] = useState('');
  const [selected, setSelected] = useState<Record<string, any> | null>(null);
  const [jobPage, setJobPage] = useState(1);
  const [patchPage, setPatchPage] = useState(1);
  const selectedChanges = changeItems(selected);
  const mobileJobs = jobs.slice((jobPage - 1) * MOBILE_PAGE_SIZE, jobPage * MOBILE_PAGE_SIZE);
  const mobilePatches = patches.slice((patchPage - 1) * MOBILE_PAGE_SIZE, patchPage * MOBILE_PAGE_SIZE);

  const selectEvolutionRecord = (record: Record<string, any>, includePatch = false) => {
    if (includePatch) {
      setSelected(record);
      return;
    }
    const jobId = asText(field(record, 'jobId', 'job_id'), '');
    const runId = asText(field(record, 'runId', 'run_id'), '');
    const patch = patches.find((item) => (
      (jobId && asText(field(item, 'jobId', 'job_id'), '') === jobId)
      || (runId && asText(field(item, 'runId', 'run_id'), '') === runId)
    ));
    setSelected(patch ? { ...record, ...patch } : record);
  };

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [jobResponse, patchResponse] = await Promise.all([
        opsAdminService.listSkillEvolverJobs({ status, projectId, limit: 100 }),
        opsAdminService.listSkillEvolverPatches({ projectId, limit: 100 }),
      ]);
      if (jobResponse.code !== '0000') throw new Error(jobResponse.info || '查询 Evolver job 失败');
      if (patchResponse.code !== '0000') throw new Error(patchResponse.info || '查询 Evolver patch 失败');
      setJobs((jobResponse.data || []).filter((job) => !projectId || asText(job.projectId || job.project_id, '') === projectId));
      setPatches((patchResponse.data || []).filter((patch) => !projectId || asText(patch.projectId || patch.project_id, '') === projectId));
      setJobPage(1);
      setPatchPage(1);
    } catch (error) {
      Toast.error(error instanceof Error ? error.message : '查询 Skill Evolver 失败');
    } finally {
      setLoading(false);
    }
  }, [projectId, status]);

  useEffect(() => {
    load();
  }, [load]);

  const runOnce = async () => {
    setLoading(true);
    try {
      const response = await opsAdminService.runSkillEvolverOnce(5);
      if (response.code !== '0000') {
        throw new Error(response.info || '触发自动分析失败');
      }
      Toast.success('已触发一次自动分析扫描');
      load();
    } catch (error) {
      Toast.error(error instanceof Error ? error.message : '触发自动分析失败');
    } finally {
      setLoading(false);
    }
  };

  const jobColumns = [
    {
      title: '学习任务',
      dataIndex: 'jobId',
      render: (_: string, record: Record<string, any>) => (
        <Space vertical align="start" spacing={2}>
          <Text strong>{triggerReasonLabel(asText(record.triggerReason || record.trigger_reason))}</Text>
          <Text type="tertiary">系统会基于来源对话、工具证据和最终结论判断是否值得沉淀。</Text>
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
      render: (_: unknown, record: Record<string, any>) => <Tag color={statusColor(asText(record.status))}>{statusLabel(asText(record.status))}</Tag>,
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
      render: (_: string, record: Record<string, any>) => asText(record.lastError || record.last_error),
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
      render: (_: string, record: Record<string, any>) => (
        <Space vertical align="start" spacing={2}>
          <Text strong>{decisionLabel(asText(record.decision))}</Text>
          <Text type="tertiary">{contentPreview(field(record, 'authoringReason', 'changeSummary', 'change_summary', 'reasonCode', 'reason_code')).slice(0, 120)}</Text>
        </Space>
      ),
    },
    {
      title: '目标 Skill',
      width: 220,
      render: (_: unknown, record: Record<string, any>) => (
        <Space vertical align="start" spacing={2}>
          <Text>{asText(record.targetSkillTitle || record.target_skill_title || record.skillTitle || record.skill_title, '待创建或待匹配 Skill')}</Text>
          <Text type="tertiary">{asText(record.projectId || record.project_id)}</Text>
        </Space>
      ),
    },
    {
      title: '决策',
      width: 170,
      render: (_: unknown, record: Record<string, any>) => (
        <Tag color={decisionColor(asText(record.decision))}>{decisionLabel(asText(record.decision))}</Tag>
      ),
    },
    {
      title: '状态',
      width: 120,
      render: (_: unknown, record: Record<string, any>) => <Tag color={statusColor(asText(record.status))}>{statusLabel(asText(record.status))}</Tag>,
    },
    {
      title: '跳过原因',
      render: (_: unknown, record: Record<string, any>) => skipReasonLabel(record.skippedReason || record.skipped_reason),
    },
    {
      title: '版本',
      width: 90,
      render: (_: unknown, record: Record<string, any>) => asText(record.appliedVersion || record.applied_version),
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
          <Space>
            <Button onClick={runOnce} loading={loading}>立即分析排队任务</Button>
            <Button icon={<IconRefresh />} onClick={load} loading={loading}>刷新</Button>
          </Space>
        }
      />

      <OpsSectionCard title="筛选">
        <FilterBar>
          <Select placeholder="状态" value={status} onChange={(value) => setStatus(String(value || ''))}>
            <Option value="">全部状态</Option>
            <Option value="PENDING">已排队</Option>
            <Option value="RUNNING">分析中</Option>
            <Option value="DONE">已完成</Option>
            <Option value="FAILED">失败</Option>
          </Select>
          <Input placeholder="按项目过滤，例如 payment-platform" value={projectId} onChange={setProjectId} />
          <Button onClick={load} loading={loading}>查询</Button>
        </FilterBar>
        <Text type="tertiary">这里不是执行中心审批。Skill 是长期运维方法，冻结/手动维护在 Skill 管理页面处理；ChangePackage 审批只管生产落地。</Text>
      </OpsSectionCard>

      <TwoPanels>
        <OpsSectionCard title="待处理的学习任务">
          {jobs.length === 0 && !loading ? (
            <OpsEmptyState title="暂无学习任务" description="Agent 完成一次有工具证据和最终结论的运维对话后会自动进入这里。普通聊天和无证据运行不会生成任务。" />
          ) : (
            <>
              <DesktopTable>
                <TableScroll>
                  <Table columns={jobColumns} dataSource={jobs} loading={loading} rowKey={(record) => asText(record?.jobId || record?.job_id || record?.id)} pagination={{ pageSize: 8 }} />
                </TableScroll>
              </DesktopTable>
              <MobileRecordList>
                {mobileJobs.map((record) => (
                  <MobileRecord key={asText(record?.jobId || record?.job_id || record?.id)}>
                    <MobileRecordHeader>
                      <Text strong>{triggerReasonLabel(asText(record.triggerReason || record.trigger_reason))}</Text>
                      <Tag color={statusColor(asText(record.status))}>{statusLabel(asText(record.status))}</Tag>
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
                    {(record.lastError || record.last_error) && <Text type="danger">最近错误：{asText(record.lastError || record.last_error)}</Text>}
                    <Button block onClick={() => selectEvolutionRecord(record)}>查看处理详情</Button>
                  </MobileRecord>
                ))}
                <MobilePagination>
                  <Pagination total={jobs.length} pageSize={MOBILE_PAGE_SIZE} currentPage={jobPage} onPageChange={setJobPage} showSizeChanger={false} size="small" />
                </MobilePagination>
              </MobileRecordList>
            </>
          )}
        </OpsSectionCard>
        <OpsSectionCard title="学习结果">
          {patches.length === 0 && !loading ? (
            <OpsEmptyState title="暂无学习结果" description="自动分析处理后会记录创建 Skill、更新 Skill、无需变更或跳过原因。" />
          ) : (
            <>
              <DesktopTable>
                <TableScroll>
                  <Table columns={patchColumns} dataSource={patches} loading={loading} rowKey={(record) => asText(record?.patchId || record?.patch_id || record?.id)} pagination={{ pageSize: 8 }} />
                </TableScroll>
              </DesktopTable>
              <MobileRecordList>
                {mobilePatches.map((record) => (
                  <MobileRecord key={asText(record?.patchId || record?.patch_id || record?.id)}>
                    <MobileRecordHeader>
                      <Text strong>{decisionLabel(asText(record.decision))}</Text>
                      <Tag color={statusColor(asText(record.status))}>{statusLabel(asText(record.status))}</Tag>
                    </MobileRecordHeader>
                    <Text type="tertiary">
                      {contentPreview(field(record, 'authoringReason', 'changeSummary', 'change_summary', 'reasonCode', 'reason_code')).slice(0, 180)}
                    </Text>
                    <MobileMeta>
                      <Text type="tertiary">目标 Skill</Text>
                      <Text>{asText(record.targetSkillTitle || record.target_skill_title || record.skillTitle || record.skill_title, '待创建或待匹配 Skill')}</Text>
                      <Text type="tertiary">项目</Text>
                      <Text>{asText(record.projectId || record.project_id)}</Text>
                      <Text type="tertiary">处理结论</Text>
                      <Text>{decisionLabel(asText(record.decision))}</Text>
                      <Text type="tertiary">原因</Text>
                      <Text>{skipReasonLabel(record.skippedReason || record.skipped_reason)}</Text>
                    </MobileMeta>
                    <Button block onClick={() => selectEvolutionRecord(record, true)}>查看学习结果</Button>
                  </MobileRecord>
                ))}
                <MobilePagination>
                  <Pagination total={patches.length} pageSize={MOBILE_PAGE_SIZE} currentPage={patchPage} onPageChange={setPatchPage} showSizeChanger={false} size="small" />
                </MobilePagination>
              </MobileRecordList>
            </>
          )}
        </OpsSectionCard>
      </TwoPanels>

      <OpsSectionCard title="选中记录详情">
        {selected ? (
          <Space vertical align="start" spacing="medium" style={{ width: '100%' }}>
            <Space wrap>
              {selected.decision && <Tag color={decisionColor(asText(selected.decision))}>{decisionLabel(asText(selected.decision))}</Tag>}
              {selected.status && <Tag color={statusColor(asText(selected.status))}>{statusLabel(asText(selected.status))}</Tag>}
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
                      const value = change?.value && typeof change.value === 'object' ? change.value as Record<string, any> : {};
                      return (
                        <Space key={`${asText(change.key)}-${index}`} vertical align="start" spacing={4} style={{ width: '100%' }}>
                          <Text strong>{skillSectionLabel(change.section)}：{asText(change.key, `调整 ${index + 1}`)}</Text>
                          {value.procedure && <Text style={{ whiteSpace: 'pre-wrap' }}>{contentPreview(value.procedure)}</Text>}
                          {Array.isArray(value.when) && <Text type="tertiary">适用场景：{value.when.join('；')}</Text>}
                          {Array.isArray(value.preferredSources) && value.preferredSources.length > 0 && (
                            <Text type="tertiary">优先证据：{value.preferredSources.join('；')}</Text>
                          )}
                          {Array.isArray(value.mustHaveEvidence) && (
                            <Text type="tertiary">必须具备：{value.mustHaveEvidence.join('；')}</Text>
                          )}
                          {Array.isArray(value.mustNot) && <Text type="danger">禁止：{value.mustNot.join('；')}</Text>}
                        </Space>
                      );
                    })}
                  </Space>
                ) : (
                  <Text style={{ whiteSpace: 'pre-wrap' }}>
                    {contentPreview(field(selected, 'candidateContent', 'candidate_content', 'generatedContent', 'generated_content', 'newContent', 'new_content', 'content'))}
                  </Text>
                )}
              </OpsSectionCard>
              <OpsSectionCard title="处理结论">
                <Space vertical align="start">
                  <Text>决策：{decisionLabel(asText(field(selected, 'decision')))}</Text>
                  <Text>状态：{statusLabel(asText(field(selected, 'status')))}</Text>
                  <Text>原因：{skipReasonLabel(asText(field(selected, 'skippedReason', 'skipped_reason', 'reasonCode', 'reason_code')))}</Text>
                  <Text>目标 Skill：{asText(field(selected, 'targetSkillTitle', 'target_skill_title', 'skillTitle', 'skill_title'), '待创建或待匹配 Skill')}</Text>
                  <Text>来源：{summaryText(field(selected, 'sourceSummary', 'source_summary', 'sourceTraceSummary', 'source_trace_summary'))}</Text>
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
      </OpsSectionCard>

    </OpsPageShell>
  );
};

export default SkillEvolverManagementPage;
