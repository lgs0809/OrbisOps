import React, { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import styled from 'styled-components';
import {
  Button,
  Card,
  Checkbox,
  Collapse,
  Input,
  Layout,
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
import {
  IconAlertTriangle,
  IconDelete,
  IconEdit,
  IconPlus,
  IconRefresh,
} from '@douyinfe/semi-icons';
import { clearAuthSession } from '../services/auth-session';

import { Header, Sidebar } from '../components/layout';
import { ProjectScopeBar } from '../components/project-scope-bar';
import { theme } from '../styles/theme';
import { API_CONFIG } from '../config/api';
import { useProjectScope } from '../hooks/use-project-scope';
import type {
  OpsAgentDefinition,
  OpsAlertTriggerEvent,
  OpsAlertTriggerRule,
} from '../services/ops-admin-service';
import {
  useAlertTriggerCatalogQuery,
  useAlertTriggerProjectOptionsQuery,
  useDeleteAlertTriggerRuleMutation,
  useSaveAlertTriggerRuleMutation,
  useToggleAlertTriggerRuleMutation,
} from '../features/alerts/api/alert-trigger-queries';
import { useResponsiveSidebar } from '../hooks/use-responsive-sidebar';

const { Content } = Layout;
const { Title, Text } = Typography;
const { Option } = Select;

const PageLayout = styled(Layout)`
  min-height: 100vh;
  background: ${theme.colors.bg.secondary};
`;

const MainContent = styled.div<{ $collapsed: boolean }>`
  display: flex;
  flex: 1;
  margin-left: ${(props) => (props.$collapsed ? '80px' : '280px')};
  transition: margin-left ${theme.animation.duration.normal} ${theme.animation.easing.cubic};
  min-width: 0;
  height: 100vh;
  overflow: hidden;

  @media (max-width: ${theme.breakpoints.md}) {
    width: 100vw;
    margin-left: 0;
  }
`;

const ContentArea = styled(Content)`
  flex: 1;
  padding: ${theme.spacing.lg};
  background: ${theme.colors.bg.secondary};
  overflow-y: auto;
  min-width: 0;
`;

const PageContainer = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${theme.spacing.base};
  width: 100%;
  max-width: 1280px;
  min-width: 0;
  margin: 0 auto;
`;

const PageHeader = styled.div`
  padding: ${theme.spacing.lg};
  border: 1px solid ${theme.colors.border.secondary};
  border-radius: ${theme.borderRadius.base};
  background: ${theme.colors.bg.primary};
`;

const FormGrid = styled.div`
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: ${theme.spacing.base};

  @media (max-width: 720px) {
    grid-template-columns: minmax(0, 1fr);
  }
`;

const Field = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${theme.spacing.xs};
`;

const FullField = styled(Field)`
  grid-column: span 2;

  @media (max-width: 720px) {
    grid-column: span 1;
  }
`;

const EndpointBox = styled.div`
  padding: ${theme.spacing.base};
  border: 1px dashed #bfdbfe;
  border-radius: ${theme.borderRadius.base};
  background: #f8fafc;
  color: ${theme.colors.text.secondary};
  word-break: break-all;
`;

const defaultRule: OpsAlertTriggerRule = {
  projectId: '',
  agentDefinitionId: '',
  agentBindingMode: 'LATEST_PUBLISHED',
  ruleName: '生产告警自动分析',
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
    '系统收到生产告警，请自动分析根因、影响面和处置建议。\n告警：${alertName}\n级别：${severity}\n服务：${service}\n摘要：${summary}\n描述：${description}\n标签：${labels}',
};

const analysisFlowName = (agent?: OpsAgentDefinition, fallbackId?: string) => agent?.name || fallbackId || '-';

const labelFilterTextFromJson = (value?: string) => {
  try {
    const parsed = JSON.parse(value || '{}') as Record<string, unknown>;
    return Object.entries(parsed).map(([key, item]) => `${key}=${String(item)}`).join('\n');
  } catch {
    return '';
  }
};

const labelFilterJsonFromText = (value: string) => {
  const result: Record<string, string> = {};
  value.split(/\n+/).map((line) => line.trim()).filter(Boolean).forEach((line) => {
    const separator = line.indexOf('=');
    if (separator <= 0 || separator === line.length - 1) {
      throw new Error(`标签条件格式不正确：${line}。请使用 标签=值`);
    }
    result[line.slice(0, separator).trim()] = line.slice(separator + 1).trim();
  });
  return JSON.stringify(result);
};

export const AlertTriggerManagementPage: React.FC = () => {
  const navigate = useNavigate();
  const { collapsed, sidebarCollapsed, toggleSidebar, closeMobileSidebar } = useResponsiveSidebar(false);
  const projectScope = useProjectScope();
  const catalogQuery = useAlertTriggerCatalogQuery();
  const optionsQuery = useAlertTriggerProjectOptionsQuery(projectScope.projectId);
  const saveRuleMutation = useSaveAlertTriggerRuleMutation();
  const toggleRuleMutation = useToggleAlertTriggerRuleMutation();
  const deleteRuleMutation = useDeleteAlertTriggerRuleMutation();
  const rules = catalogQuery.data?.rules || [];
  const events = catalogQuery.data?.events || [];
  const incidents = optionsQuery.data?.incidents || [];
  const agentDefinitions = optionsQuery.data?.agents || [];
  const channels = optionsQuery.data?.channels || [];
  const loading = catalogQuery.isLoading || optionsQuery.isLoading
    || saveRuleMutation.isPending || toggleRuleMutation.isPending || deleteRuleMutation.isPending;
  const [modalVisible, setModalVisible] = useState(false);
  const [editing, setEditing] = useState<OpsAlertTriggerRule | null>(null);
  const [form, setForm] = useState<OpsAlertTriggerRule>(defaultRule);
  const [labelFilterText, setLabelFilterText] = useState('');
  const defaultReactAgentId = projectScope.selectedProject?.defaultAgentId || '';
  const workflowDefinitions = useMemo(
    () => agentDefinitions.filter((agent) => agent.definitionKind === 'SPECIALIZED_WORKFLOW'),
    [agentDefinitions],
  );
  const reactSelected = Boolean(defaultReactAgentId) && form.agentDefinitionId === defaultReactAgentId;
  const handleNavigation = (path: string) => {
    navigate(path.startsWith('/') ? path : `/${path}`);
  };

  const handleLogout = () => {
    clearAuthSession();
    Toast.success('已退出登录');
    navigate('/login');
  };

  const openCreate = () => {
    const project = projectScope.selectedProject;
    if (!projectScope.projectId || !project) {
      Toast.warning('请先选择项目');
      return;
    }
    setEditing(null);
    setForm({
      ...defaultRule,
      projectId: project.projectId,
      agentDefinitionId: project.defaultAgentId || '',
      agentBindingMode: 'LATEST_PUBLISHED',
      agentVersion: undefined,
    });
    setLabelFilterText('');
    setModalVisible(true);
  };

  const openEdit = (rule: OpsAlertTriggerRule) => {
    setEditing(rule);
    setForm({ ...defaultRule, ...rule });
    setLabelFilterText(labelFilterTextFromJson(rule.matchLabelsJson));
    setModalVisible(true);
  };

  const saveRule = async () => {
    if (!form.ruleName?.trim()) {
      Toast.error('请输入规则名称');
      return;
    }
    if (!form.projectId) {
      Toast.error('请选择业务系统');
      return;
    }
    if (!form.agentDefinitionId) {
      Toast.error('当前项目没有可用 ReAct 或 Workflow');
      return;
    }
    if (!reactSelected && form.agentBindingMode === 'PINNED_VERSION' && !form.agentVersion) {
      Toast.error('固定版本模式需要选择一个已发布 Workflow 版本');
      return;
    }
    if (form.notifyChannel && (!form.notificationChannelId || !form.notificationTarget?.trim())) {
      Toast.error('启用告警通知时，请选择消息渠道并填写通知目标');
      return;
    }
    try {
      const matchLabelsJson = labelFilterJsonFromText(labelFilterText);
      await saveRuleMutation.mutateAsync({
        ...form,
        matchLabelsJson,
        id: editing?.id,
        status: form.status ?? 1,
      });
      Toast.success(editing ? '规则已更新' : '规则已创建');
      setModalVisible(false);
    } catch (error) {
      Toast.error(error instanceof Error ? error.message : '保存失败');
    }
  };

  const toggleRule = async (rule: OpsAlertTriggerRule) => {
    if (!rule.id) return;
    const next = rule.status === 1 ? 0 : 1;
    try {
      await toggleRuleMutation.mutateAsync({ id: rule.id, status: next });
      Toast.success(next === 1 ? '规则已启用' : '规则已停用');
    } catch (error) {
      Toast.error(error instanceof Error ? error.message : '更新规则状态失败');
    }
  };

  const deleteRule = async (rule: OpsAlertTriggerRule) => {
    if (!rule.id) return;
    try {
      await deleteRuleMutation.mutateAsync(rule.id);
      Toast.success('规则已删除');
    } catch (error) {
      Toast.error(error instanceof Error ? error.message : '删除规则失败');
    }
  };

  useEffect(() => {
    const token = localStorage.getItem('token');
    if (!token) {
      navigate('/login');
      return;
    }
  }, []);

  const visibleRules = useMemo(
    () => rules.filter((rule) => rule.projectId === projectScope.projectId),
    [projectScope.projectId, rules],
  );

  const visibleEvents = useMemo(() => {
    const visibleRuleIds = new Set(visibleRules.map((rule) => rule.id).filter(Boolean));
    return events.filter((event) => event.ruleId && visibleRuleIds.has(event.ruleId));
  }, [events, visibleRules]);

  const ruleColumns = [
    {
      title: '规则',
      dataIndex: 'ruleName',
      width: 180,
    },
    {
      title: '匹配',
      width: 280,
      render: (_: unknown, record: OpsAlertTriggerRule) => (
        <Space vertical align="start" spacing="tight">
          <Text size="small">alert: {record.alertNameRegex || '*'}</Text>
          <Text size="small">severity: {record.severityRegex || '*'}</Text>
          <Text size="small">service: {record.serviceRegex || '*'}</Text>
        </Space>
      ),
    },
    {
      title: '通知',
      width: 180,
      render: (_: unknown, record: OpsAlertTriggerRule) => record.notifyChannel
        ? `${channels.find((channel) => channel.channelId === record.notificationChannelId)?.name || 'Channel'} · ${record.notificationTarget || '-'}`
        : <Text type="tertiary">不通知</Text>,
    },
    {
      title: '分析方式',
      width: 220,
      render: (_: unknown, record: OpsAlertTriggerRule) => {
        if (record.agentDefinitionId === projectScope.selectedProject?.defaultAgentId) return <Text>ReAct</Text>;
        const agent = workflowDefinitions.find((item) => item.agentId === record.agentDefinitionId);
        return <Text ellipsis={{ showTooltip: true }}>{analysisFlowName(agent, record.agentDefinitionId)}</Text>;
      },
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (status: number) => <Tag color={status === 1 ? 'green' : 'grey'}>{status === 1 ? '启用' : '停用'}</Tag>,
    },
    {
      title: '更新时间',
      dataIndex: 'updateTime',
      width: 170,
    },
    {
      title: '操作',
      width: 240,
      render: (_: unknown, record: OpsAlertTriggerRule) => (
        <Space>
          <Button size="small" theme="light" onClick={() => toggleRule(record)}>
            {record.status === 1 ? '停用' : '启用'}
          </Button>
          <Button size="small" icon={<IconEdit />} onClick={() => openEdit(record)}>
            编辑
          </Button>
          <Popconfirm title="确认删除这个触发规则？" onConfirm={() => deleteRule(record)}>
            <Button size="small" type="danger" icon={<IconDelete />}>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  const eventColumns = [
    { title: '时间', dataIndex: 'createTime', width: 170 },
    { title: '规则', dataIndex: 'ruleName', width: 160 },
    { title: '告警', dataIndex: 'alertName', width: 160 },
    { title: '级别', dataIndex: 'severity', width: 100, render: (value: string) => <Tag color={value === 'critical' ? 'red' : 'orange'}>{value || '-'}</Tag> },
    { title: '服务', dataIndex: 'serviceName', width: 140 },
    { title: '状态', dataIndex: 'status', width: 110, render: (value: string) => <Tag color={value === 'TRIGGERED' ? 'green' : value === 'DEDUPED' ? 'grey' : 'red'}>{value}</Tag> },
    { title: 'Run 状态', dataIndex: 'runStatus', width: 110, render: (value: string) => (value ? <Tag color={value === 'SUCCEEDED' ? 'green' : value === 'FAILED' ? 'red' : value === 'CANCELED' ? 'grey' : 'blue'}>{value}</Tag> : '-') },
    { title: 'AI 调查', dataIndex: 'runId', width: 150, render: (value: string) => (value ? '已启动' : '待调查') },
    {
      title: '对应 Incident',
      width: 180,
      render: (_: unknown, record: OpsAlertTriggerEvent) => {
        const ruleProjectId = rules.find((rule) => rule.id === record.ruleId)?.projectId;
        const incident = incidents.find((item) => item.projectId === ruleProjectId && item.fingerprint === record.fingerprint);
        return incident ? <Button theme="borderless" onClick={() => navigate(`/incidents?incidentId=${encodeURIComponent(incident.incidentId)}`)}>查看事件</Button> : '-';
      },
    },
    { title: '完成时间', dataIndex: 'completedAt', width: 170 },
    { title: '错误', dataIndex: 'errorMessage', width: 220 },
  ];

  return (
    <PageLayout>
      <Sidebar selectedKey="alerts" onSelect={handleNavigation} collapsed={sidebarCollapsed} onMobileClose={closeMobileSidebar} />
      <MainContent $collapsed={collapsed}>
        <div style={{ display: 'flex', flexDirection: 'column', width: '100%' }}>
          <Header collapsed={sidebarCollapsed} onToggleSidebar={toggleSidebar} onLogout={handleLogout} />
          <ContentArea>
            <PageContainer>
              <PageHeader>
                <Space vertical align="start">
                  <Space wrap>
                    <IconAlertTriangle />
                    <Title heading={3} style={{ margin: 0 }}>告警触发</Title>
                  </Space>
                  <Text type="secondary">按项目维护告警规则。触发后先聚合到 Incident，AI 调查、证据、是否需要人工介入都沿同一事件主线展示。</Text>
                </Space>
              </PageHeader>

              <ProjectScopeBar
                projects={projectScope.projects}
                projectId={projectScope.projectId}
                loading={projectScope.loading}
                onChange={projectScope.selectProject}
                onRefresh={projectScope.reloadProjects}
                actions={<Button type="primary" icon={<IconPlus />} disabled={!projectScope.projectId} onClick={openCreate}>新建触发规则</Button>}
              />

              <Card>
                <Space vertical align="start" spacing="tight">
                  <Text strong>也可以直接告诉 Chat</Text>
                  <Text type="tertiary">
                    例如：“为当前项目新增订单服务 critical 告警规则，命中后分析最近十分钟指标和日志”。系统会生成规则并写审计；修改、停用和删除时会先列出可选规则，不要求记住内部编号。
                  </Text>
                </Space>
              </Card>

              <Collapse keepDOM>
                <Collapse.Panel
                  itemKey="integration"
                  header="接入配置：Alertmanager Webhook"
                >
                  <EndpointBox>
                    <Text strong>Webhook URL</Text>
                    <br />
                    {API_CONFIG.BASE_DOMAIN}/api/v1/admin/ops/alert-triggers/webhook/alertmanager
                    <br />
                    <br />
                    <Text strong>认证方式</Text>
                    <br />
                    生产环境请求需要携带服务令牌 Header：X-Admin-Service-Token。
                    如果规则配置了签名密钥，还需要携带 X-Ops-Alert-Timestamp 与 X-Ops-Alert-Signature。
                  </EndpointBox>
                </Collapse.Panel>
              </Collapse>

              <Card>
                <Space style={{ width: '100%', justifyContent: 'space-between' }}>
                  <Space>
                    <Button icon={<IconRefresh />} onClick={() => { void catalogQuery.refetch(); void optionsQuery.refetch(); }}>刷新</Button>
                  </Space>
                  <Text type="tertiary">去重窗口内同一 rule + fingerprint 只触发一次分析。</Text>
                </Space>
              </Card>

              <Card title="触发规则">
                <Table columns={ruleColumns} dataSource={visibleRules} loading={loading || projectScope.loading} rowKey="id" pagination={false} scroll={{ x: 1130 }} />
              </Card>

              <Card title="最近触发事件">
                <Table columns={eventColumns} dataSource={visibleEvents} loading={loading || projectScope.loading} rowKey="id" pagination={false} scroll={{ x: 1220 }} />
              </Card>
            </PageContainer>

            <Modal
              title={editing ? '编辑告警触发规则' : '新建告警触发规则'}
              visible={modalVisible}
              onOk={saveRule}
              onCancel={() => setModalVisible(false)}
              okText="保存"
              cancelText="取消"
              width={860}
            >
              <FormGrid>
                <Field>
                  <Text strong>业务系统</Text>
                  <Select
                    value={form.projectId || ''}
                    style={{ width: '100%' }}
                    disabled
                    onChange={(value) => {
                      const projectId = String(value || '');
                      const project = projectScope.projects.find((item) => item.projectId === projectId);
                      setForm({
                        ...form,
                        projectId,
                        agentDefinitionId: project?.defaultAgentId || '',
                      });
                    }}
                  >
                    {projectScope.projects.map((project) => (
                      <Option key={project.projectId} value={project.projectId}>
                        {project.name}
                      </Option>
                    ))}
                  </Select>
                </Field>
                <Field>
                  <Text strong>规则名称</Text>
                  <Input value={form.ruleName} onChange={(value) => setForm({ ...form, ruleName: value })} />
                </Field>
                <Field>
                  <Text strong>来源</Text>
                  <Select value={form.sourceType || 'ALERTMANAGER'} onChange={(value) => setForm({ ...form, sourceType: String(value) })}>
                    <Option value="ALERTMANAGER">Alertmanager</Option>
                  </Select>
                </Field>
                <Field>
                  <Text strong>告警名称匹配</Text>
                  <Input value={form.alertNameRegex || ''} placeholder="例如 ErrorRateHigh|InstanceDown" onChange={(value) => setForm({ ...form, alertNameRegex: value })} />
                </Field>
                <Field>
                  <Text strong>告警级别匹配</Text>
                  <Input value={form.severityRegex || ''} placeholder="warning|critical" onChange={(value) => setForm({ ...form, severityRegex: value })} />
                </Field>
                <Field>
                  <Text strong>服务名称匹配</Text>
                  <Input value={form.serviceRegex || ''} placeholder="order|payment|gateway" onChange={(value) => setForm({ ...form, serviceRegex: value })} />
                </Field>
                <Field>
                  <Text strong>运行方式</Text>
                  <Select
                    aria-label="运行方式"
                    value={reactSelected ? '__REACT__' : (form.agentDefinitionId || '')}
                    filter
                    style={{ width: '100%' }}
                    placeholder="选择 ReAct 或已发布 Workflow"
                    onSelect={(value) => {
                      const selectedValue = String(value || '');
                      if (selectedValue === '__REACT__') {
                        setForm({ ...form, agentDefinitionId: defaultReactAgentId, agentBindingMode: 'LATEST_PUBLISHED', agentVersion: undefined });
                        return;
                      }
                      const selected = workflowDefinitions.find((agent) => agent.agentId === selectedValue);
                      setForm({ ...form, agentDefinitionId: selectedValue, agentVersion: form.agentBindingMode === 'PINNED_VERSION' ? selected?.version : undefined });
                    }}
                  >
                    {defaultReactAgentId && <Option value="__REACT__">ReAct</Option>}
                    {workflowDefinitions.map((agent) => (
                      <Option key={agent.agentId} value={agent.agentId}>{analysisFlowName(agent)}</Option>
                    ))}
                  </Select>
                  <Text type="tertiary" size="small">
                    ReAct 动态调查告警；Workflow 用于已经固化的告警处置 SOP。
                  </Text>
                </Field>
                <FullField>
                  <Text strong>问题模板</Text>
                  <TextArea autosize={{ minRows: 6, maxRows: 10 }} value={form.questionTemplate || ''} onChange={(value) => setForm({ ...form, questionTemplate: value })} />
                </FullField>
                <FullField>
                  <Collapse keepDOM>
                    <Collapse.Panel itemKey="advanced" header="高级设置">
                      <FormGrid>
                        {!reactSelected && <Field>
                          <Text strong>Workflow 版本策略</Text>
                          <Select value={form.agentBindingMode || 'LATEST_PUBLISHED'} onChange={(value) => {
                            const agentBindingMode = String(value) as 'LATEST_PUBLISHED' | 'PINNED_VERSION';
                            const selected = workflowDefinitions.find((agent) => agent.agentId === form.agentDefinitionId);
                            setForm({ ...form, agentBindingMode, agentVersion: agentBindingMode === 'PINNED_VERSION' ? selected?.version : undefined });
                          }}><Option value="LATEST_PUBLISHED">使用最新已发布版本</Option><Option value="PINNED_VERSION">固定当前版本</Option></Select>
                        </Field>}
                        <Field><Text strong>去重窗口</Text><Select value={form.dedupWindowSeconds || 900} onChange={(value) => setForm({ ...form, dedupWindowSeconds: Number(value) })}><Option value={300}>5 分钟</Option><Option value={900}>15 分钟</Option><Option value={1800}>30 分钟</Option><Option value={3600}>1 小时</Option></Select></Field>
                        <Field><Text strong>日志窗口</Text><Select value={form.rangeMinutes || 15} onChange={(value) => setForm({ ...form, rangeMinutes: Number(value) })}><Option value={5}>5 分钟</Option><Option value={15}>15 分钟</Option><Option value={30}>30 分钟</Option><Option value={60}>1 小时</Option></Select></Field>
                        <Field><Text strong>指标窗口</Text><Select value={form.promWindow || '5m'} onChange={(value) => setForm({ ...form, promWindow: String(value) })}><Option value="1m">1 分钟</Option><Option value="5m">5 分钟</Option><Option value="15m">15 分钟</Option><Option value="30m">30 分钟</Option><Option value="1h">1 小时</Option></Select></Field>
                        <Field><Text strong>子任务最大轮次</Text><Input value={String(form.subAgentMaxIterations || 3)} onChange={(value) => setForm({ ...form, subAgentMaxIterations: Number(value) || 3 })} /></Field>
                        <Field><Text strong>单步超时秒</Text><Input value={String(form.nodeTimeoutSeconds || 90)} onChange={(value) => setForm({ ...form, nodeTimeoutSeconds: Number(value) || 90 })} /></Field>
                        <FullField><Text strong>附加标签条件</Text><TextArea autosize={{ minRows: 3, maxRows: 6 }} value={labelFilterText} placeholder={'每行一个条件，例如：\nteam=order\ncluster=production'} onChange={setLabelFilterText} /></FullField>
                        <FullField><Text strong>Webhook 签名密钥</Text><Input type="password" value={form.webhookSecret || ''} placeholder="留空表示仅使用服务令牌" onChange={(value) => setForm({ ...form, webhookSecret: value })} /></FullField>
                      </FormGrid>
                    </Collapse.Panel>
                  </Collapse>
                </FullField>
                <FullField>
                  <Space>
                    <Checkbox checked={form.status !== 0} onChange={(event) => setForm({ ...form, status: event.target.checked ? 1 : 0 })}>启用规则</Checkbox>
                    <Checkbox checked={form.includeRecentLogs !== false} onChange={(event) => setForm({ ...form, includeRecentLogs: Boolean(event.target.checked) })}>查询最近日志</Checkbox>
                    <Checkbox checked={form.notifyChannel === true} onChange={(event) => setForm({ ...form, notifyChannel: Boolean(event.target.checked) })}>完成后发送 Channel 通知</Checkbox>
                  </Space>
                </FullField>
                {form.notifyChannel && (
                  <>
                    <Field>
                      <Text strong>消息渠道</Text>
                      <Select value={form.notificationChannelId || undefined} placeholder="选择项目已配置的 Channel" onChange={(value) => setForm({ ...form, notificationChannelId: String(value || '') })}>
                        {channels.filter((channel) => channel.status === 'ACTIVE').map((channel) => <Option key={channel.channelId} value={channel.channelId}>{channel.name}</Option>)}
                      </Select>
                    </Field>
                    <Field>
                      <Text strong>通知目标</Text>
                      <Input value={form.notificationTarget || ''} placeholder="群聊、会话或接收人标识" onChange={(value) => setForm({ ...form, notificationTarget: value })} />
                    </Field>
                  </>
                )}
              </FormGrid>
            </Modal>
          </ContentArea>
        </div>
      </MainContent>
    </PageLayout>
  );
};
