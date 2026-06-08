import React, { useMemo, useState } from 'react';
import styled from 'styled-components';
import { Button, Input, Modal, Select, Space, Table, Tag, Typography, Toast } from '@douyinfe/semi-ui';
import { IconEyeOpened } from '@douyinfe/semi-icons';

import { ProjectScopeBar } from '../components/project-scope-bar';
import type { OpsAuditPolicy, OpsConfigAuditRecord } from '../services/ops-admin-service';
import {
  useAuditPolicyQuery,
  useExportAuditMutation,
  useGovernanceAuditDetailQuery,
  useGovernanceAuditsQuery,
  useSaveAuditPolicyMutation,
} from '../features/governance/api/governance-queries';
import {
  auditPolicySavePayload,
  auditRetentionDays,
  auditRetentionDirty,
  effectiveAuditControls,
} from '../features/governance/model/audit-policy-governance';
import { theme } from '../styles/theme';
import { useProjectScope } from '../hooks/use-project-scope';
import {
  JsonBlock,
  OpsAdvancedPreview,
  OpsDangerZone,
  OpsEmptyState,
  OpsPageHeader,
  OpsPageShell,
  OpsResponsiveGrid,
  OpsSectionCard,
  TableScroll,
} from '../components/ops-layout';

const { Title, Text } = Typography;

const HeaderRow = styled.div`
  width: 100%;
  min-width: 0;
`;

const AuditPanel = styled(OpsSectionCard)`
  margin-bottom: 0;
`;

const Toolbar = styled.div`
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: ${theme.spacing.base};
  margin-bottom: ${theme.spacing.base};

  @media (max-width: 720px) {
    flex-direction: column;
    align-items: stretch;

    .semi-space {
      width: 100%;
      flex-direction: column;
      align-items: stretch;
    }

    .semi-select,
    .semi-input-wrapper {
      width: 100% !important;
    }
  }
`;

const AuditTabNav = styled.div`
  display: flex;
  flex-wrap: wrap;
  gap: ${theme.spacing.xs};
  padding: ${theme.spacing.xs};
  border: 1px solid ${theme.colors.border.secondary};
  border-radius: ${theme.borderRadius.base};
  background: ${theme.colors.bg.primary};
  margin-bottom: ${theme.spacing.base};
`;

const AuditTabButton = styled.button<{ $active?: boolean }>`
  border: 1px solid ${(props) => (props.$active ? theme.colors.primary : 'transparent')};
  border-radius: ${theme.borderRadius.sm};
  background: ${(props) => (props.$active ? '#eff6ff' : 'transparent')};
  color: ${(props) => (props.$active ? theme.colors.primary : theme.colors.text.secondary)};
  padding: 8px 12px;
  cursor: pointer;
  font-weight: ${theme.typography.fontWeight.medium};

  &:hover {
    color: ${theme.colors.primary};
    background: #eff6ff;
  }
`;

const moduleLabel = (module?: string) => ({
  project: '项目设置',
  'project-resource': '数据连接',
  'project-mcp': 'Agent 工具',
  'agent-definition': 'Agent',
  'agent-decision': 'Agent 决策',
  'tool-call': '工具调用',
  'change-package': '执行包',
  'change-plan': '历史变更记录',
  'task-schedule': '巡检',
  'alert-trigger': '告警规则',
  incident: '故障',
  'mcp-config': 'MCP',
  skill: 'Skill',
}[module || ''] || module || '-');

type AuditTabKey = 'operation' | 'agent' | 'tool' | 'change' | 'approval' | 'policy';

const auditTabs: Array<{ key: AuditTabKey; label: string; description: string }> = [
  { key: 'operation', label: '操作审计', description: '项目、资源、Agent、任务等配置变更记录。' },
  { key: 'agent', label: 'Agent 决策审计', description: 'Agent 规划、路由、复盘和停止决策记录。' },
  { key: 'tool', label: '工具调用审计', description: 'MCP、RAG、通知等工具调用摘要。' },
  { key: 'change', label: '变更执行审计', description: '变更提案、执行、验证和回滚过程。' },
  { key: 'approval', label: '权限与审批审计', description: '审批、授权、拒绝和职责分离记录。' },
  { key: 'policy', label: '审计策略', description: '审计保留、导出、脱敏和风险操作策略。' },
];

const auditTabMatches = (record: OpsConfigAuditRecord, tab: AuditTabKey) => {
  const moduleName = String(record.module_name || '');
  const actionName = String(record.action_name || '');
  if (tab === 'operation') return true;
  if (tab === 'agent') return moduleName.includes('agent');
  if (tab === 'tool') return moduleName.includes('tool') || moduleName.includes('mcp') || moduleName.includes('rag') || moduleName.includes('skill');
  if (tab === 'change') return moduleName.includes('change') || moduleName.includes('incident');
  if (tab === 'approval') return /approve|reject|role|permission|审批|权限/i.test(`${moduleName} ${actionName}`);
  return false;
};

const prettyJson = (value?: string) => {
  if (!value) return '无';
  try {
    return JSON.stringify(JSON.parse(value), null, 2);
  } catch (_error) {
    return value;
  }
};

const policyNumber = (policy: OpsAuditPolicy, camelKey: string, snakeKey: string, fallback: number) => {
  const value = policy[camelKey] ?? policy[snakeKey];
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : fallback;
};

const policyBoolean = (policy: OpsAuditPolicy, camelKey: string, snakeKey: string, fallback = true) => {
  const value = policy[camelKey] ?? policy[snakeKey];
  if (typeof value === 'boolean') return value;
  if (typeof value === 'number') return value === 1;
  if (typeof value === 'string') return ['1', 'true', 'yes', 'enabled'].includes(value.toLowerCase());
  return fallback;
};

const normalizePolicy = (policy?: OpsAuditPolicy): OpsAuditPolicy => ({
  projectId: policy?.projectId || policy?.project_id || 'GLOBAL',
  retentionDays: policyNumber(policy || {}, 'retentionDays', 'retention_days', 180),
  maskingEnabled: policyBoolean(policy || {}, 'maskingEnabled', 'masking_enabled'),
  exportApprovalRequired: policyBoolean(policy || {}, 'exportApprovalRequired', 'export_approval_required'),
  highRiskConfirmationRequired: policyBoolean(policy || {}, 'highRiskConfirmationRequired', 'high_risk_confirmation_required'),
  replayEnabled: policyBoolean(policy || {}, 'replayEnabled', 'replay_enabled'),
  status: policy?.status || 'ENABLED',
  updateTime: policy?.updateTime || policy?.update_time,
  persistence: policy?.persistence,
});

export const OpsStatusManagementPage: React.FC = () => {
  const projectScope = useProjectScope();
  const [activeTab, setActiveTab] = useState<AuditTabKey>('operation');
  const [moduleFilter, setModuleFilter] = useState('');
  const [userFilter, setUserFilter] = useState('');
  const [agentFilter, setAgentFilter] = useState('');
  const [riskFilter, setRiskFilter] = useState('');
  const [timeFilter, setTimeFilter] = useState('');
  const [keyword, setKeyword] = useState('');
  const [detailRecord, setDetailRecord] = useState<OpsConfigAuditRecord | null>(null);
  const [policyDraft, setPolicyDraft] = useState<{ scopeKey: string; value: OpsAuditPolicy } | null>(null);

  const auditFilters = useMemo(() => ({
    projectId: projectScope.projectId || undefined,
    userId: userFilter,
    agentId: agentFilter,
    module: moduleFilter,
    riskLevel: riskFilter,
    timeFilter,
  }), [agentFilter, moduleFilter, projectScope.projectId, riskFilter, timeFilter, userFilter]);
  const auditScopeReady = !projectScope.loading && (Boolean(projectScope.projectId) || projectScope.projects.length === 0);
  const auditsQuery = useGovernanceAuditsQuery(auditFilters, auditScopeReady);
  const audits = auditsQuery.data || [];
  const loading = auditsQuery.isFetching;

  const detailAuditId = detailRecord ? (detailRecord.audit_id || String(detailRecord.id)) : '';
  const detailQuery = useGovernanceAuditDetailQuery(detailAuditId, Boolean(detailRecord));
  const detail = detailQuery.data || detailRecord;
  const detailLoading = detailQuery.isFetching;

  const policyScopeKey = projectScope.projectId || 'GLOBAL';
  const policyQuery = useAuditPolicyQuery(projectScope.projectId || undefined, activeTab === 'policy');
  const policyMutation = useSaveAuditPolicyMutation();
  const exportMutation = useExportAuditMutation();
  const policyFromServer = normalizePolicy(policyQuery.data);
  const auditPolicy = policyDraft?.scopeKey === policyScopeKey ? policyDraft.value : policyFromServer;
  const effectiveControls = effectiveAuditControls(auditPolicy);
  const policyDirty = policyDraft?.scopeKey === policyScopeKey && auditRetentionDirty(policyFromServer, policyDraft.value);
  const policyLoading = policyQuery.isFetching;
  const policySaving = policyMutation.isPending;
  const auditError = auditsQuery.error instanceof Error ? auditsQuery.error.message : '';
  const policyError = policyQuery.error instanceof Error ? policyQuery.error.message : '';
  const detailError = detailQuery.error instanceof Error ? detailQuery.error.message : '';
  const updateAuditPolicyDraft = (updater: (current: OpsAuditPolicy) => OpsAuditPolicy) => {
    setPolicyDraft({ scopeKey: policyScopeKey, value: updater(auditPolicy) });
  };

  const filteredAudits = useMemo(() => {
    const normalized = keyword.trim().toLowerCase();
    return audits.filter((record) => {
      if (!auditTabMatches(record, activeTab)) return false;
      if (normalized && ![record.target_id, record.action_name, record.operator_name, record.module_name]
        .some((value) => String(value || '').toLowerCase().includes(normalized))) return false;
      return true;
    });
  }, [activeTab, audits, keyword]);

  const openDetail = (record: OpsConfigAuditRecord) => {
    setDetailRecord(record);
  };

  const exportAudit = async (record: OpsConfigAuditRecord) => {
    try {
      const auditId = record.audit_id || String(record.id);
      const exported = await exportMutation.mutateAsync(auditId);
      const blob = new Blob([JSON.stringify(exported || {}, null, 2)], { type: 'application/json;charset=utf-8' });
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement('a');
      anchor.href = url;
      anchor.download = `audit-${record.id}.json`;
      anchor.click();
      URL.revokeObjectURL(url);
      Toast.success('审计记录已导出');
    } catch (error) {
      Toast.error(error instanceof Error ? error.message : '导出审计失败');
    }
  };

  const saveAuditPolicy = async () => {
    try {
      const saved = await policyMutation.mutateAsync(auditPolicySavePayload(
        auditPolicy,
        projectScope.projectId || 'GLOBAL',
        auditRetentionDays(auditPolicy),
      ));
      setPolicyDraft({ scopeKey: policyScopeKey, value: normalizePolicy(saved) });
      Toast.success('审计保留策略已保存');
    } catch (error) {
      Toast.error(error instanceof Error ? error.message : '保存审计保留策略失败');
    }
  };

  const activeTabMeta = auditTabs.find((tab) => tab.key === activeTab) || auditTabs[0];

  return (
    <OpsPageShell selectedKey="audit">
      <HeaderRow>
        <OpsPageHeader
          title="审计治理"
          description="按项目、用户、Agent、风险等级、时间范围和操作类型回看配置变更、Agent 决策、工具调用、变更执行与审批轨迹。"
        />

          <ProjectScopeBar
            projects={projectScope.projects}
            projectId={projectScope.projectId}
            onChange={projectScope.selectProject}
            loading={projectScope.loading}
            onRefresh={() => void auditsQuery.refetch()}
          />

          <AuditTabNav aria-label="审计治理分类">
            {auditTabs.map((tab) => (
              <AuditTabButton
                key={tab.key}
                type="button"
                $active={activeTab === tab.key}
                onClick={() => setActiveTab(tab.key)}
              >
                {tab.label}
              </AuditTabButton>
            ))}
          </AuditTabNav>

          <AuditPanel>
            <Space vertical align="start" style={{ width: '100%' }}>
              <div>
                <Title heading={5} style={{ margin: 0 }}>{activeTabMeta.label}</Title>
                <Text type="tertiary" size="small">{activeTabMeta.description}</Text>
              </div>
            <Toolbar>
              <Space>
                <Select
                  value={moduleFilter || undefined}
                  placeholder="操作类型"
                  showClear
                  style={{ width: 180 }}
                  onChange={(value) => setModuleFilter(String(value || ''))}
                >
                  <Select.Option value="agent-definition">Agent</Select.Option>
                  <Select.Option value="agent-decision">Agent 决策</Select.Option>
                  <Select.Option value="tool-call">工具调用</Select.Option>
                  <Select.Option value="change-package">执行包</Select.Option>
                  <Select.Option value="project">项目设置</Select.Option>
                  <Select.Option value="project-resource">数据连接</Select.Option>
                  <Select.Option value="project-mcp">Agent 工具</Select.Option>
                  <Select.Option value="task-schedule">巡检</Select.Option>
                  <Select.Option value="alert-trigger">告警规则</Select.Option>
                  <Select.Option value="incident">故障</Select.Option>
                  <Select.Option value="mcp-config">MCP</Select.Option>
                  <Select.Option value="skill">Skill</Select.Option>
                </Select>
                <Input
                  value={keyword}
                  placeholder="搜索目标、动作或操作人"
                  style={{ width: 260 }}
                  onChange={setKeyword}
                />
                <Input
                  value={userFilter}
                  placeholder="用户"
                  style={{ width: 160 }}
                  onChange={setUserFilter}
                />
                <Input
                  value={agentFilter}
                  placeholder="Agent / 目标"
                  style={{ width: 180 }}
                  onChange={setAgentFilter}
                />
                <Select
                  value={riskFilter || undefined}
                  placeholder="风险等级"
                  showClear
                  style={{ width: 140 }}
                  onChange={(value) => setRiskFilter(String(value || ''))}
                >
                  <Select.Option value="HIGH">高风险</Select.Option>
                  <Select.Option value="MEDIUM">中风险</Select.Option>
                  <Select.Option value="LOW">低风险</Select.Option>
                </Select>
                <Select
                  value={timeFilter || undefined}
                  placeholder="时间范围"
                  showClear
                  style={{ width: 150 }}
                  onChange={(value) => setTimeFilter(String(value || ''))}
                >
                  <Select.Option value="24h">最近 24 小时</Select.Option>
                  <Select.Option value="7d">最近 7 天</Select.Option>
                </Select>
              </Space>
              <Text type="tertiary">共 {filteredAudits.length} 条项目事件</Text>
            </Toolbar>
            {activeTab === 'policy' ? (
              policyError ? (
                <OpsEmptyState title="加载审计策略失败" description={policyError} />
              ) : <Space vertical align="start" style={{ width: '100%' }}>
                <Text type="tertiary">
                  当前治理范围：{projectScope.selectedProject?.name || auditPolicy.projectId || '平台默认'}。只有已经接入后端执行器的控制项才允许修改；未激活能力不会以“可配置”形式误导用户。
                </Text>
                <OpsResponsiveGrid $min="220px">
                  {effectiveControls.map((control) => (
                    <div key={control.key}>
                      <Space vertical align="start" spacing="tight" style={{ width: '100%' }}>
                        <Space wrap>
                          <Text strong>{control.title}</Text>
                          <Tag color={control.status === 'ENFORCED' || control.status === 'PLATFORM_INVARIANT' ? 'green' : control.status === 'DELEGATED' ? 'blue' : 'grey'}>
                            {control.status}
                          </Tag>
                        </Space>
                        <Text type="tertiary" size="small">{control.summary}</Text>
                        {control.key === 'retention' && (
                          <Input
                            aria-label="Audit retention days"
                            value={String(auditRetentionDays(auditPolicy))}
                            onChange={(value) => updateAuditPolicyDraft((current) => ({
                              ...current,
                              retentionDays: Math.max(7, Math.min(3650, Number(value) || 180)),
                            }))}
                          />
                        )}
                      </Space>
                    </div>
                  ))}
                </OpsResponsiveGrid>
                <Space wrap>
                  <Button
                    theme="solid"
                    disabled={!policyDirty || policyLoading}
                    loading={policySaving}
                    onClick={saveAuditPolicy}
                  >
                    保存保留策略
                  </Button>
                  <Button
                    loading={policyLoading}
                    onClick={() => {
                      setPolicyDraft(null);
                      void policyQuery.refetch();
                    }}
                  >
                    放弃修改并重新加载
                  </Button>
                  <Text type="tertiary" size="small">
                    {policyDirty ? '有未保存的保留期修改' : '当前保留期已与服务端一致'}
                  </Text>
                </Space>
                <OpsAdvancedPreview title="Stored policy snapshot · Advanced">
                  <Text type="tertiary" size="small" style={{ display: 'block', marginBottom: 8 }}>
                    兼容字段仍保留在存储模型中，但只有上方标记 ENFORCED / PLATFORM_INVARIANT 的控制由当前 Audit Policy 直接执行。
                  </Text>
                  <JsonBlock>{JSON.stringify(auditPolicy, null, 2)}</JsonBlock>
                </OpsAdvancedPreview>
                <OpsDangerZone
                  title="危险审计操作"
                  description="撤销、强制关闭、重新执行和回滚必须写入审计并经过权限校验；当前只开放策略持久化，不开放这些危险动作。"
                >
                  <Space wrap>
                    <Button type="danger" disabled>撤销</Button>
                    <Button type="danger" disabled>强制关闭</Button>
                    <Button type="danger" disabled>重新执行</Button>
                    <Button type="danger" disabled>回滚</Button>
                  </Space>
                </OpsDangerZone>
              </Space>
            ) : auditError ? (
              <OpsEmptyState title="加载审计记录失败" description={auditError} />
            ) : (
              <TableScroll>
                <Table
                  rowKey="id"
                  loading={loading}
                  dataSource={filteredAudits}
                  pagination={{ pageSize: 15 }}
                  scroll={{ x: 1100 }}
                  columns={[
                    { title: '类型', dataIndex: 'module_name', width: 130, render: (value) => <Tag>{moduleLabel(value)}</Tag> },
                    { title: '动作', dataIndex: 'action_name', width: 150 },
                    { title: '目标', dataIndex: 'target_id', width: 220 },
                    { title: '风险', dataIndex: 'risk_level', width: 100, render: (value) => <Tag color={value === 'HIGH' ? 'red' : value === 'MEDIUM' ? 'orange' : 'green'}>{value || 'LOW'}</Tag> },
                    { title: '结果', dataIndex: 'result_status', width: 100 },
                    { title: '操作人', dataIndex: 'operator_name', width: 140 },
                    { title: '来源 IP', dataIndex: 'client_ip', width: 130 },
                    { title: '时间', dataIndex: 'create_time', width: 160 },
                    {
                      title: '操作',
                      width: 180,
                      fixed: 'right' as const,
                      render: (_value, record) => (
                        <Space>
                          <Button size="small" icon={<IconEyeOpened />} onClick={() => void openDetail(record)}>查看</Button>
                          <Button size="small" onClick={() => void exportAudit(record)}>导出</Button>
                          <Button size="small" disabled>更多</Button>
                        </Space>
                      ),
                    },
                  ]}
                  empty={<OpsEmptyState title="当前分类暂无审计记录" description="没有可用后端数据时不会硬编码审计记录。" />}
                />
              </TableScroll>
            )}
            </Space>
          </AuditPanel>
      </HeaderRow>

      <Modal title="审计详情" visible={Boolean(detailRecord)} footer={null} width={900} onCancel={() => setDetailRecord(null)}>
        <Space vertical align="start" spacing="medium" style={{ width: '100%' }}>
          {detailLoading && <Text type="tertiary">正在加载完整审计记录...</Text>}
          {detailError && <OpsEmptyState title="加载审计详情失败" description={detailError} />}
          <Text>{moduleLabel(detail?.module_name)} / {detail?.action_name} / {detail?.target_id}</Text>
          <OpsAdvancedPreview title="高级预览：变更前 JSON">
            <JsonBlock>{prettyJson(detail?.before_json)}</JsonBlock>
          </OpsAdvancedPreview>
          <OpsAdvancedPreview title="高级预览：变更后 JSON">
            <JsonBlock>{prettyJson(detail?.after_json)}</JsonBlock>
          </OpsAdvancedPreview>
        </Space>
      </Modal>
    </OpsPageShell>
  );
};
