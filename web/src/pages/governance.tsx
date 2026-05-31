import React, { useMemo, useState } from 'react';
import { Button, Card, Input, Modal, Select, Space, Table, Tag, Toast, Typography } from '@douyinfe/semi-ui';
import { IconEyeOpened, IconRefresh } from '@douyinfe/semi-icons';
import styled from 'styled-components';

import { OpsAdvancedPreview, OpsPageHeader, OpsPageShell } from '../components/ops-layout';
import { ProjectScopeBar } from '../components/project-scope-bar';
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
import { useProjectScope } from '../hooks/use-project-scope';
import type { OpsAuditPolicy, OpsConfigAuditRecord } from '../services/ops-admin-service';
import { theme } from '../styles/theme';
import { userFacingError } from '../utils/user-facing-error';

const { Paragraph, Text, Title } = Typography;
const { Option } = Select;

const Stack = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${theme.spacing.base};
  min-width: 0;
`;

const FilterRow = styled.div`
  display: flex;
  flex-wrap: wrap;
  gap: ${theme.spacing.sm};
  align-items: center;
`;

type AuditCategory = 'all' | 'agent' | 'tool' | 'change' | 'approval';

const moduleLabel = (module?: string) => ({
  project: '项目设置',
  'project-resource': '数据连接',
  'project-mcp': 'Project 工具',
  'agent-definition': 'Workflow / Agent 定义',
  'agent-decision': 'Agent 决策',
  'tool-call': '工具调用',
  'change-package': 'ChangePackage',
  'change-plan': '变更历史',
  'task-schedule': '定时自动化',
  'alert-trigger': '告警自动化',
  incident: 'Incident',
  'mcp-config': 'MCP 配置',
  skill: 'Skill',
}[module || ''] || module || '-');

const categoryMatch = (record: OpsConfigAuditRecord, category: AuditCategory) => {
  if (category === 'all') return true;
  const moduleName = String(record.module_name || '').toLowerCase();
  const actionName = String(record.action_name || '').toLowerCase();
  if (category === 'agent') return moduleName.includes('agent');
  if (category === 'tool') return ['tool', 'mcp', 'rag', 'skill'].some((value) => moduleName.includes(value));
  if (category === 'change') return moduleName.includes('change') || moduleName.includes('incident');
  return /approve|reject|role|permission/.test(`${moduleName} ${actionName}`);
};

const normalizePolicy = (policy?: OpsAuditPolicy): OpsAuditPolicy => ({
  projectId: policy?.projectId || policy?.project_id || 'GLOBAL',
  retentionDays: Number(policy?.retentionDays ?? policy?.retention_days ?? 180),
  maskingEnabled: true,
  exportApprovalRequired: policy?.exportApprovalRequired ?? policy?.export_approval_required ?? true,
  highRiskConfirmationRequired: policy?.highRiskConfirmationRequired ?? policy?.high_risk_confirmation_required ?? true,
  replayEnabled: policy?.replayEnabled ?? policy?.replay_enabled ?? false,
  status: policy?.status || 'ENABLED',
  updateTime: policy?.updateTime || policy?.update_time,
  persistence: policy?.persistence,
});

const pretty = (value?: string) => {
  if (!value) return '无';
  try {
    return JSON.stringify(JSON.parse(value), null, 2);
  } catch {
    return value;
  }
};

export const GovernancePage: React.FC = () => {
  const projectScope = useProjectScope();
  const [category, setCategory] = useState<AuditCategory>('all');
  const [moduleFilter, setModuleFilter] = useState('');
  const [userFilter, setUserFilter] = useState('');
  const [riskFilter, setRiskFilter] = useState('');
  const [timeFilter, setTimeFilter] = useState('');
  const [keyword, setKeyword] = useState('');
  const [detailRecord, setDetailRecord] = useState<OpsConfigAuditRecord | null>(null);
  const [policyDraft, setPolicyDraft] = useState<{ scopeKey: string; value: OpsAuditPolicy } | null>(null);
  const [policyVisible, setPolicyVisible] = useState(false);

  const filters = useMemo(() => ({
    projectId: projectScope.projectId || undefined,
    userId: userFilter || undefined,
    module: moduleFilter || undefined,
    riskLevel: riskFilter || undefined,
    timeFilter: timeFilter || undefined,
  }), [moduleFilter, projectScope.projectId, riskFilter, timeFilter, userFilter]);
  const auditsQuery = useGovernanceAuditsQuery(filters, !projectScope.loading);
  const audits = auditsQuery.data || [];
  const visibleAudits = useMemo(() => {
    const query = keyword.trim().toLowerCase();
    return audits.filter((record) => categoryMatch(record, category))
      .filter((record) => !query || [record.target_id, record.action_name, record.operator_name, record.module_name]
        .some((value) => String(value || '').toLowerCase().includes(query)));
  }, [audits, category, keyword]);

  const detailAuditId = detailRecord ? (detailRecord.audit_id || String(detailRecord.id)) : '';
  const detailQuery = useGovernanceAuditDetailQuery(detailAuditId, Boolean(detailRecord));
  const detail = detailQuery.data || detailRecord;
  const policyScopeKey = projectScope.projectId || 'GLOBAL';
  const policyQuery = useAuditPolicyQuery(projectScope.projectId || undefined, policyVisible);
  const serverPolicy = normalizePolicy(policyQuery.data);
  const policy = policyDraft?.scopeKey === policyScopeKey ? policyDraft.value : serverPolicy;
  const policyDirty = policyDraft?.scopeKey === policyScopeKey && auditRetentionDirty(serverPolicy, policyDraft.value);
  const savePolicyMutation = useSaveAuditPolicyMutation();
  const exportMutation = useExportAuditMutation();

  const updateRetention = (value: string) => {
    const days = Math.max(7, Math.min(3650, Number(value) || 180));
    setPolicyDraft({ scopeKey: policyScopeKey, value: { ...policy, retentionDays: days } });
  };

  const savePolicy = async () => {
    try {
      const saved = await savePolicyMutation.mutateAsync(auditPolicySavePayload(policy, policyScopeKey, auditRetentionDays(policy)));
      setPolicyDraft({ scopeKey: policyScopeKey, value: normalizePolicy(saved) });
      Toast.success('审计保留策略已保存。');
    } catch (error) {
      Toast.error(userFacingError(error, '保存审计保留策略失败，请稍后重试。'));
    }
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
      Toast.success('审计记录已导出。');
    } catch (error) {
      Toast.error(userFacingError(error, '导出审计记录失败，请稍后重试。'));
    }
  };

  const columns = [
    { title: '类型', dataIndex: 'module_name', width: 170, render: (value: string) => <Tag>{moduleLabel(value)}</Tag> },
    { title: '动作', dataIndex: 'action_name', width: 170 },
    { title: '目标', dataIndex: 'target_id', width: 220 },
    { title: '风险', dataIndex: 'risk_level', width: 100, render: (value: string) => <Tag color={value === 'HIGH' ? 'red' : value === 'MEDIUM' ? 'orange' : 'green'}>{value || 'LOW'}</Tag> },
    { title: '结果', dataIndex: 'result_status', width: 110 },
    { title: '操作人', dataIndex: 'operator_name', width: 150 },
    { title: '来源 IP', dataIndex: 'client_ip', width: 140 },
    { title: '时间', dataIndex: 'create_time', width: 180 },
    {
      title: '操作',
      width: 190,
      render: (_: unknown, record: OpsConfigAuditRecord) => (
        <Space>
          <Button size="small" icon={<IconEyeOpened />} onClick={() => setDetailRecord(record)}>查看</Button>
          <Button size="small" loading={exportMutation.isPending} onClick={() => void exportAudit(record)}>导出</Button>
        </Space>
      ),
    },
  ];

  return (
    <OpsPageShell selectedKey="settings">
      <OpsPageHeader
        title="治理与审计"
        description="按 Project 查看配置变更、Agent 决策、工具调用、变更执行和审批等审计证据。"
        extra={(
          <Space>
            <Button onClick={() => setPolicyVisible(true)}>审计策略</Button>
            <Button icon={<IconRefresh />} loading={auditsQuery.isFetching} onClick={() => void auditsQuery.refetch()}>刷新</Button>
          </Space>
        )}
      />

      <Stack>
        <ProjectScopeBar
          projects={projectScope.projects}
          projectId={projectScope.projectId}
          onChange={projectScope.selectProject}
          loading={projectScope.loading}
          onRefresh={projectScope.reloadProjects}
        />

        <Card>
          <Title heading={6} style={{ marginTop: 0 }}>审计分类</Title>
          <Space wrap>
            {([
              ['all', '操作审计'],
              ['agent', 'Agent 决策'],
              ['tool', '工具调用'],
              ['change', '变更执行'],
              ['approval', '审批与访问控制'],
            ] as Array<[AuditCategory, string]>).map(([key, label]) => (
              <Button key={key} type={category === key ? 'primary' : 'tertiary'} theme={category === key ? 'solid' : 'light'} onClick={() => setCategory(key)}>{label}</Button>
            ))}
          </Space>
        </Card>

        <Card>
          <FilterRow>
            <Select placeholder="模块" value={moduleFilter || undefined} showClear onChange={(value) => setModuleFilter(String(value || ''))} style={{ width: 180 }}>
              <Option value="agent-definition">Workflow / Agent</Option>
              <Option value="agent-decision">Agent 决策</Option>
              <Option value="tool-call">工具调用</Option>
              <Option value="change-package">ChangePackage</Option>
              <Option value="project">项目设置</Option>
              <Option value="project-resource">数据连接</Option>
              <Option value="project-mcp">Project 工具</Option>
              <Option value="task-schedule">定时自动化</Option>
              <Option value="alert-trigger">告警自动化</Option>
              <Option value="mcp-config">MCP</Option>
              <Option value="skill">Skill</Option>
            </Select>
            <Input placeholder="搜索目标、动作或操作人" value={keyword} onChange={setKeyword} style={{ width: 280 }} />
            <Input placeholder="用户 ID" value={userFilter} onChange={setUserFilter} style={{ width: 160 }} />
            <Select placeholder="风险" value={riskFilter || undefined} showClear onChange={(value) => setRiskFilter(String(value || ''))} style={{ width: 130 }}>
              <Option value="HIGH">高</Option>
              <Option value="MEDIUM">中</Option>
              <Option value="LOW">低</Option>
            </Select>
            <Select placeholder="时间范围" value={timeFilter || undefined} showClear onChange={(value) => setTimeFilter(String(value || ''))} style={{ width: 150 }}>
              <Option value="24h">最近 24 小时</Option>
              <Option value="7d">最近 7 天</Option>
            </Select>
            <Text type="tertiary">共 {visibleAudits.length} 条</Text>
          </FilterRow>
        </Card>

        <Card title="审计记录">
          <Table
            rowKey="id"
            columns={columns}
            dataSource={visibleAudits}
            loading={auditsQuery.isLoading}
            pagination={{ pageSize: 15 }}
            scroll={{ x: 1380 }}
            empty={<Text type="tertiary">当前范围和筛选条件下暂无审计记录。</Text>}
          />
        </Card>
      </Stack>

      <Modal title="审计详情" visible={Boolean(detailRecord)} width={900} footer={<Button onClick={() => setDetailRecord(null)}>关闭</Button>} onCancel={() => setDetailRecord(null)}>
        {detail && (
          <Stack>
            <Space wrap>
              <Tag>{moduleLabel(detail.module_name)}</Tag>
              <Tag color={detail.risk_level === 'HIGH' ? 'red' : detail.risk_level === 'MEDIUM' ? 'orange' : 'green'}>{detail.risk_level || 'LOW'}</Tag>
              <Text>{detail.action_name}</Text>
              <Text type="tertiary">{detail.target_id}</Text>
            </Space>
            {detailQuery.isFetching && <Text type="tertiary">正在加载完整审计记录…</Text>}
            <OpsAdvancedPreview title="变更前 JSON"><pre style={{ whiteSpace: 'pre-wrap', margin: 0 }}>{pretty(detail.before_json)}</pre></OpsAdvancedPreview>
            <OpsAdvancedPreview title="变更后 JSON"><pre style={{ whiteSpace: 'pre-wrap', margin: 0 }}>{pretty(detail.after_json)}</pre></OpsAdvancedPreview>
          </Stack>
        )}
      </Modal>

      <Modal
        title="审计策略"
        visible={policyVisible}
        width={760}
        onCancel={() => setPolicyVisible(false)}
        footer={(
          <Space>
            <Button onClick={() => { setPolicyDraft(null); void policyQuery.refetch(); }}>重新加载</Button>
            <Button type="primary" disabled={!policyDirty} loading={savePolicyMutation.isPending} onClick={() => void savePolicy()}>保存保留策略</Button>
          </Space>
        )}
      >
        <Stack>
          <Paragraph type="tertiary">
            当前范围：{projectScope.selectedProject?.name || policyScopeKey}。只有已经具备服务端执行点的治理项才允许在这里修改。
          </Paragraph>
          {effectiveAuditControls(policy).map((control) => (
            <Card key={control.key}>
              <Space vertical align="start" spacing="tight">
                <Space wrap><Text strong>{control.title}</Text><Tag color={control.status === 'ENFORCED' || control.status === 'PLATFORM_INVARIANT' ? 'green' : control.status === 'DELEGATED' ? 'blue' : 'grey'}>{control.status}</Tag></Space>
                <Text type="tertiary" size="small">{control.summary}</Text>
                {control.key === 'retention' && <Input aria-label="审计保留天数" value={String(auditRetentionDays(policy))} onChange={updateRetention} />}
              </Space>
            </Card>
          ))}
          <Card>
            <Title heading={6} style={{ marginTop: 0 }}>危险审计操作仍不可用</Title>
            <Paragraph type="tertiary" style={{ marginBottom: 0 }}>
              撤销、强制关闭、重放和回滚不能从审计策略直接触发。生产权限仍由正常 RBAC 与 ChangePackage 生命周期控制。
            </Paragraph>
          </Card>
        </Stack>
      </Modal>
    </OpsPageShell>
  );
};
