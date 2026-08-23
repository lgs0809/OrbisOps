import React, { useMemo, useState } from 'react';
import {
  Button,
  Card,
  Checkbox,
  Input,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
  TextArea,
  Toast,
  Typography,
} from '@douyinfe/semi-ui';
import { IconArrowLeft, IconDelete, IconEdit, IconPlus, IconRefresh } from '@douyinfe/semi-icons';
import { useNavigate } from 'react-router-dom';
import styled from 'styled-components';

import { OpsPageHeader, OpsPageShell } from '../components/ops-layout';
import { ProjectScopeBar } from '../components/project-scope-bar';
import {
  useAlertTriggerCatalogQuery,
  useAlertTriggerProjectOptionsQuery,
  useDeleteAlertTriggerRuleMutation,
  useSaveAlertTriggerRuleMutation,
  useToggleAlertTriggerRuleMutation,
} from '../features/alerts/api/alert-trigger-queries';
import { useProjectScope } from '../hooks/use-project-scope';
import type { OpsAgentDefinition, OpsAlertTriggerRule } from '../services/ops-admin-service';
import { theme } from '../styles/theme';
import { userFacingError } from '../utils/user-facing-error';

const { Paragraph, Text, Title } = Typography;
const { Option } = Select;

const FormGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: ${theme.spacing.base};

  @media (max-width: 760px) {
    grid-template-columns: 1fr;
  }
`;

const Field = styled.div`
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
`;

const WideField = styled(Field)`
  grid-column: 1 / -1;
`;

const SectionStack = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${theme.spacing.base};
`;

const defaultRule = (projectId = '', defaultAgentId = ''): OpsAlertTriggerRule => ({
  projectId,
  agentDefinitionId: defaultAgentId,
  agentBindingMode: 'LATEST_PUBLISHED',
  ruleName: '生产告警排查',
  status: 1,
  sourceType: 'ALERTMANAGER',
  severityRegex: 'warning|critical',
  matchLabelsJson: '{}',
  rangeMinutes: 15,
  promWindow: '5m',
  includeRecentLogs: true,
  notifyChannel: false,
  subAgentMaxIterations: 3,
  nodeTimeoutSeconds: 90,
  maxEvidenceItems: 8,
  dedupWindowSeconds: 900,
  questionTemplate:
    '排查这条生产告警，基于证据判断根因、影响、已知事实、未知项和建议的下一步动作。\n告警: ${alertName}\n级别: ${severity}\n服务: ${service}\n摘要: ${summary}\n描述: ${description}\n标签: ${labels}',
});

const workflowName = (workflow?: OpsAgentDefinition, fallback?: string) => workflow?.name || fallback || '-';

const isPublishedWorkflow = (workflow: OpsAgentDefinition) =>
  workflow.definitionKind === 'SPECIALIZED_WORKFLOW' && String(workflow.lifecycle || '').toUpperCase() === 'PUBLISHED';

export const AlertTriggersPage: React.FC = () => {
  const navigate = useNavigate();
  const projectScope = useProjectScope();
  const catalogQuery = useAlertTriggerCatalogQuery();
  const optionsQuery = useAlertTriggerProjectOptionsQuery(projectScope.projectId);
  const saveMutation = useSaveAlertTriggerRuleMutation();
  const toggleMutation = useToggleAlertTriggerRuleMutation();
  const deleteMutation = useDeleteAlertTriggerRuleMutation();

  const rules = catalogQuery.data?.rules || [];
  const events = catalogQuery.data?.events || [];
  const workflows = useMemo(
    () => (optionsQuery.data?.agents || []).filter(isPublishedWorkflow),
    [optionsQuery.data?.agents],
  );
  const channels = optionsQuery.data?.channels || [];
  const defaultReactAgentId = projectScope.selectedProject?.defaultAgentId || '';
  const visibleRules = useMemo(
    () => rules.filter((rule) => rule.projectId === projectScope.projectId),
    [rules, projectScope.projectId],
  );
  const visibleRuleIds = useMemo(() => new Set(visibleRules.map((rule) => rule.id).filter(Boolean)), [visibleRules]);
  const visibleEvents = useMemo(
    () => events.filter((event) => event.ruleId && visibleRuleIds.has(event.ruleId)),
    [events, visibleRuleIds],
  );

  const [modalVisible, setModalVisible] = useState(false);
  const [editing, setEditing] = useState<OpsAlertTriggerRule | null>(null);
  const [form, setForm] = useState<OpsAlertTriggerRule>(() => defaultRule());

  const loading = catalogQuery.isLoading || optionsQuery.isLoading || saveMutation.isPending || toggleMutation.isPending || deleteMutation.isPending;
  const executionValue = form.agentDefinitionId === defaultReactAgentId && defaultReactAgentId
    ? '__DEFAULT_REACT__'
    : (form.agentDefinitionId || '');

  const openCreate = () => {
    if (!projectScope.projectId) {
      Toast.warning('请先选择 Project。');
      return;
    }
    setEditing(null);
    setForm(defaultRule(projectScope.projectId, defaultReactAgentId));
    setModalVisible(true);
  };

  const openEdit = (rule: OpsAlertTriggerRule) => {
    setEditing(rule);
    setForm({
      ...defaultRule(rule.projectId || projectScope.projectId, defaultReactAgentId),
      ...rule,
    });
    setModalVisible(true);
  };

  const save = async () => {
    if (!form.ruleName.trim()) {
      Toast.error('请输入规则名称。');
      return;
    }
    if (!form.projectId) {
      Toast.error('请选择 Project。');
      return;
    }
    if (!form.agentDefinitionId) {
      Toast.error('请选择 默认助手 或已发布 Workflow。');
      return;
    }
    if (form.agentDefinitionId !== defaultReactAgentId && form.agentBindingMode === 'PINNED_VERSION' && !form.agentVersion) {
      Toast.error('固定版本 Workflow 必须指定已发布版本。');
      return;
    }
    if (form.notifyChannel && (!form.notificationChannelId || !form.notificationTarget?.trim())) {
      Toast.error('请选择通知 Channel 和通知目标。');
      return;
    }
    try {
      await saveMutation.mutateAsync({ ...form, id: editing?.id, status: form.status ?? 1 });
      Toast.success(editing ? '告警自动化已更新。' : '告警自动化已创建。');
      setModalVisible(false);
    } catch (error) {
      Toast.error(userFacingError(error, '保存告警自动化失败，请稍后重试。'));
    }
  };

  const toggle = async (rule: OpsAlertTriggerRule) => {
    if (!rule.id) return;
    const nextStatus = rule.status === 1 ? 0 : 1;
    try {
      await toggleMutation.mutateAsync({ id: rule.id, status: nextStatus });
      Toast.success(nextStatus === 1 ? '告警自动化已启用。' : '告警自动化已停用。');
    } catch (error) {
      Toast.error(userFacingError(error, '更新告警自动化状态失败，请稍后重试。'));
    }
  };

  const remove = async (rule: OpsAlertTriggerRule) => {
    if (!rule.id) return;
    try {
      await deleteMutation.mutateAsync(rule.id);
      Toast.success('告警自动化已删除。');
    } catch (error) {
      Toast.error(userFacingError(error, '删除告警自动化失败，请稍后重试。'));
    }
  };

  const ruleColumns = [
    { title: '规则', dataIndex: 'ruleName', width: 190 },
    {
      title: '匹配条件',
      width: 290,
      render: (_: unknown, rule: OpsAlertTriggerRule) => (
        <Space vertical align="start" spacing="tight">
          <Text size="small">alert: {rule.alertNameRegex || '*'}</Text>
          <Text size="small">severity: {rule.severityRegex || '*'}</Text>
          <Text size="small">service: {rule.serviceRegex || '*'}</Text>
        </Space>
      ),
    },
    {
      title: '执行方式',
      width: 230,
      render: (_: unknown, rule: OpsAlertTriggerRule) => {
        if (rule.agentDefinitionId === defaultReactAgentId) return <Tag color="green">默认助手</Tag>;
        const workflow = workflows.find((item) => item.agentId === rule.agentDefinitionId);
        return <Space wrap><Tag color="blue">Workflow</Tag><Text>{workflowName(workflow, rule.agentDefinitionId)}</Text></Space>;
      },
    },
    {
      title: '状态',
      width: 100,
      render: (_: unknown, rule: OpsAlertTriggerRule) => <Tag color={rule.status === 1 ? 'green' : 'grey'}>{rule.status === 1 ? '已启用' : '已停用'}</Tag>,
    },
    { title: '更新时间', dataIndex: 'updateTime', width: 180 },
    {
      title: '操作',
      width: 250,
      render: (_: unknown, rule: OpsAlertTriggerRule) => (
        <Space>
          <Button size="small" onClick={() => void toggle(rule)}>{rule.status === 1 ? '停用' : '启用'}</Button>
          <Button size="small" icon={<IconEdit />} onClick={() => openEdit(rule)}>编辑</Button>
          <Popconfirm title="删除这个告警自动化？" onConfirm={() => void remove(rule)}>
            <Button size="small" type="danger" icon={<IconDelete />}>删除</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  const eventColumns = [
    { title: '时间', dataIndex: 'createTime', width: 180 },
    { title: '规则', dataIndex: 'ruleName', width: 180 },
    { title: '告警', dataIndex: 'alertName', width: 170 },
    { title: '级别', dataIndex: 'severity', width: 110, render: (value: string) => <Tag color={value === 'critical' ? 'red' : 'orange'}>{value || '-'}</Tag> },
    { title: '服务', dataIndex: 'serviceName', width: 150 },
    { title: '状态', dataIndex: 'status', width: 120 },
    { title: 'Run', dataIndex: 'runId', width: 160, render: (runId: string) => runId ? <Button theme="borderless" onClick={() => navigate(`/workbench?projectId=${encodeURIComponent(projectScope.projectId)}&runId=${encodeURIComponent(runId)}`)}>打开 Run</Button> : '-' },
    { title: '错误', dataIndex: 'errorMessage', width: 220 },
  ];

  return (
    <OpsPageShell selectedKey="automations">
      <OpsPageHeader
        title="告警触发器"
        description="将告警规则绑定到 Project，并在触发后自动启动默认智能执行或已发布 Workflow。"
        extra={(
          <Space wrap>
            <Button icon={<IconArrowLeft />} onClick={() => navigate(`/automations${projectScope.projectId ? `?projectId=${encodeURIComponent(projectScope.projectId)}` : ''}`)}>返回自动化</Button>
            <Button icon={<IconRefresh />} onClick={() => { void catalogQuery.refetch(); void optionsQuery.refetch(); }} loading={loading}>刷新</Button>
            <Button type="primary" icon={<IconPlus />} disabled={!projectScope.projectId} onClick={openCreate}>新建告警自动化</Button>
          </Space>
        )}
      />

      <SectionStack>
        <ProjectScopeBar
          projects={projectScope.projects}
          projectId={projectScope.projectId}
          loading={projectScope.loading}
          onChange={projectScope.selectProject}
          onRefresh={projectScope.reloadProjects}
        />

        <Card>
          <Title heading={5} style={{ marginTop: 0 }}>Alertmanager 接入边界</Title>
          <Paragraph type="tertiary" style={{ marginBottom: 0 }}>
            告警投递本身只负责传输。规则负责选择 Project 和执行绑定，最终 Run 仍由同一 Runtime 治理并进入工作台。Provider 认证和签名材料属于部署配置，不是可编辑的规则数据。
          </Paragraph>
        </Card>

        <Card title="告警自动化">
          <Table columns={ruleColumns} dataSource={visibleRules} loading={loading || projectScope.loading} rowKey="id" pagination={false} scroll={{ x: 1180 }} empty={<Text type="tertiary">当前 Project 暂无告警自动化。</Text>} />
        </Card>

        <Card title="最近由告警触发的 Run">
          <Table columns={eventColumns} dataSource={visibleEvents} loading={loading || projectScope.loading} rowKey="id" pagination={false} scroll={{ x: 1200 }} empty={<Text type="tertiary">当前 Project 暂无由告警触发的 Run。</Text>} />
        </Card>
      </SectionStack>

      <Modal
        title={editing ? '编辑告警自动化' : '新建告警自动化'}
        visible={modalVisible}
        onOk={() => void save()}
        onCancel={() => setModalVisible(false)}
        okText="保存"
        cancelText="取消"
        width={880}
      >
        <FormGrid>
          <Field>
            <Text strong>Project</Text>
            <Input value={projectScope.selectedProject?.name || form.projectId || ''} disabled />
          </Field>
          <Field>
            <Text strong>规则名称</Text>
            <Input value={form.ruleName} onChange={(ruleName) => setForm({ ...form, ruleName })} />
          </Field>
          <Field>
            <Text strong>告警名称正则</Text>
            <Input value={form.alertNameRegex || ''} placeholder="ErrorRateHigh|InstanceDown" onChange={(alertNameRegex) => setForm({ ...form, alertNameRegex })} />
          </Field>
          <Field>
            <Text strong>告警级别正则</Text>
            <Input value={form.severityRegex || ''} placeholder="warning|critical" onChange={(severityRegex) => setForm({ ...form, severityRegex })} />
          </Field>
          <Field>
            <Text strong>服务名称正则</Text>
            <Input value={form.serviceRegex || ''} placeholder="orders|payments|gateway" onChange={(serviceRegex) => setForm({ ...form, serviceRegex })} />
          </Field>
          <Field>
            <Text strong>执行方式</Text>
            <Select
              aria-label="执行方式"
              value={executionValue || undefined}
              placeholder="默认助手 或已发布 Workflow"
              onChange={(value) => {
                const selected = String(value || '');
                if (selected === '__DEFAULT_REACT__') {
                  setForm({ ...form, agentDefinitionId: defaultReactAgentId, agentBindingMode: 'LATEST_PUBLISHED', agentVersion: undefined });
                  return;
                }
                const workflow = workflows.find((item) => item.agentId === selected);
                setForm({
                  ...form,
                  agentDefinitionId: selected,
                  agentBindingMode: 'LATEST_PUBLISHED',
                  agentVersion: workflow?.version,
                });
              }}
            >
              {defaultReactAgentId && <Option value="__DEFAULT_REACT__">默认助手</Option>}
              {workflows.map((workflow) => <Option key={workflow.agentId} value={workflow.agentId}>Workflow · {workflowName(workflow)}</Option>)}
            </Select>
          </Field>

          {form.agentDefinitionId && form.agentDefinitionId !== defaultReactAgentId && (
            <Field>
              <Text strong>Workflow 版本策略</Text>
              <Select
                value={form.agentBindingMode || 'LATEST_PUBLISHED'}
                onChange={(value) => {
                  const agentBindingMode = String(value) as 'LATEST_PUBLISHED' | 'PINNED_VERSION';
                  const workflow = workflows.find((item) => item.agentId === form.agentDefinitionId);
                  setForm({ ...form, agentBindingMode, agentVersion: agentBindingMode === 'PINNED_VERSION' ? workflow?.version : undefined });
                }}
              >
                <Option value="LATEST_PUBLISHED">始终使用最新已发布版本</Option>
                <Option value="PINNED_VERSION">固定当前版本</Option>
              </Select>
            </Field>
          )}

          <Field>
            <Text strong>证据时间范围</Text>
            <Select value={form.rangeMinutes || 15} onChange={(value) => setForm({ ...form, rangeMinutes: Number(value) })}>
              <Option value={5}>5 分钟</Option>
              <Option value={15}>15 分钟</Option>
              <Option value={30}>30 分钟</Option>
              <Option value={60}>1 小时</Option>
            </Select>
          </Field>
          <Field>
            <Text strong>指标窗口</Text>
            <Select value={form.promWindow || '5m'} onChange={(value) => setForm({ ...form, promWindow: String(value) })}>
              <Option value="1m">1 分钟</Option>
              <Option value="5m">5 分钟</Option>
              <Option value="15m">15 分钟</Option>
              <Option value="30m">30 分钟</Option>
              <Option value="1h">1 小时</Option>
            </Select>
          </Field>
          <Field>
            <Text strong>去重窗口</Text>
            <Select value={form.dedupWindowSeconds || 900} onChange={(value) => setForm({ ...form, dedupWindowSeconds: Number(value) })}>
              <Option value={300}>5 分钟</Option>
              <Option value={900}>15 分钟</Option>
              <Option value={1800}>30 分钟</Option>
              <Option value={3600}>1 小时</Option>
            </Select>
          </Field>
          <WideField>
            <Text strong>问题模板</Text>
            <TextArea autosize={{ minRows: 6, maxRows: 12 }} value={form.questionTemplate || ''} onChange={(questionTemplate) => setForm({ ...form, questionTemplate })} />
          </WideField>
          <WideField>
            <Text strong>标签匹配 JSON</Text>
            <TextArea autosize={{ minRows: 3, maxRows: 6 }} value={form.matchLabelsJson || '{}'} onChange={(matchLabelsJson) => setForm({ ...form, matchLabelsJson })} />
          </WideField>
          <WideField>
            <Space wrap>
              <Checkbox checked={form.status !== 0} onChange={(event) => setForm({ ...form, status: event.target.checked ? 1 : 0 })}>启用</Checkbox>
              <Checkbox checked={form.includeRecentLogs !== false} onChange={(event) => setForm({ ...form, includeRecentLogs: Boolean(event.target.checked) })}>包含近期日志</Checkbox>
              <Checkbox checked={form.notifyChannel === true} onChange={(event) => setForm({ ...form, notifyChannel: Boolean(event.target.checked) })}>完成后发送通知</Checkbox>
            </Space>
          </WideField>
          {form.notifyChannel && (
            <>
              <Field>
                <Text strong>通知 Channel</Text>
                <Select value={form.notificationChannelId || undefined} onChange={(value) => setForm({ ...form, notificationChannelId: String(value || '') })}>
                  {channels.filter((channel) => channel.status === 'ACTIVE').map((channel) => <Option key={channel.channelId} value={channel.channelId}>{channel.name}</Option>)}
                </Select>
              </Field>
              <Field>
                <Text strong>通知目标</Text>
                <Input value={form.notificationTarget || ''} placeholder="会话、群组或接收者标识" onChange={(notificationTarget) => setForm({ ...form, notificationTarget })} />
              </Field>
            </>
          )}
        </FormGrid>
      </Modal>
    </OpsPageShell>
  );
};
