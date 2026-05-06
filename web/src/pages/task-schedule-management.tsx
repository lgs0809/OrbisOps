import React, { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Layout,
  Table,
  Button,
  Checkbox,
  Collapse,
  Input,
  Space,
  Typography,
  Toast,
  Tag,
  Popconfirm,
  Card,
  Select,
  Modal,
  TextArea
} from '@douyinfe/semi-ui';
import {
  IconPlus,
  IconEdit,
  IconDelete,
  IconPlay
} from '@douyinfe/semi-icons';
import styled from 'styled-components';
import { theme } from '../styles/theme';
import { Sidebar, Header } from '../components/layout';
import type {
  TaskExecutionResponseDTO,
  TaskScheduleRequestDTO,
  TaskScheduleResponseDTO,
} from '../services/task-schedule-admin-service';
import type { OpsAgentDefinition } from '../services/ops-admin-service';
import {
  useDeleteTaskScheduleMutation,
  useRunTaskScheduleMutation,
  useSaveTaskScheduleMutation,
  useTaskExecutionsQuery,
  useTaskScheduleOptionsQuery,
  useTaskSchedulesQuery,
  useToggleTaskScheduleMutation,
} from '../features/automations/api/task-schedule-queries';
import { clearAuthSession } from '../services/auth-session';
import { ProjectScopeBar } from '../components/project-scope-bar';
import { useProjectScope } from '../hooks/use-project-scope';
import { TableScroll } from '../components/ops-layout';
import { useResponsiveSidebar } from '../hooks/use-responsive-sidebar';

const { Content } = Layout;
const { Title } = Typography;
const { Option } = Select;

const PageLayout = styled(Layout)`
  min-height: 100vh;
  background: ${theme.colors.bg.secondary};
`;

const MainContent = styled.div<{ $collapsed: boolean }>`
  display: flex;
  flex: 1;
  margin-left: ${props => props.$collapsed ? '80px' : '280px'};
  transition: margin-left ${theme.animation.duration.normal} ${theme.animation.easing.cubic};
  min-width: 0;

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

  @media (max-width: 720px) {
    padding: 0;
  }
`;

const PageContainer = styled.div`
  height: 100%;
  display: flex;
  flex-direction: column;
  width: 100%;
  max-width: 1280px;
  min-width: 0;
  margin: 0 auto;
`;

const PageHeader = styled.div`
  padding: ${theme.spacing.lg};
  border-bottom: 1px solid ${theme.colors.border.secondary};
`;

const Toolbar = styled(Card)`
  margin: ${theme.spacing.lg};

  .semi-card-body {
    padding: ${theme.spacing.lg};
  }
`;

const TableContainer = styled.div`
  flex: 1;
  margin: 0 ${theme.spacing.lg} ${theme.spacing.lg};
`;

const TableCard = styled(Card)`
  .semi-card-body {
    padding: 0;
  }
`;

const FormGrid = styled.div`
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: ${theme.spacing.base};

  @media (max-width: 720px) {
    grid-template-columns: 1fr;
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

const ResultContent = styled.pre`
  max-height: 520px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-word;
  margin: 0;
  padding: ${theme.spacing.lg};
  background: ${theme.colors.bg.secondary};
  border: 1px solid ${theme.colors.border.secondary};
  border-radius: ${theme.borderRadius.base};
  font-size: ${theme.typography.fontSize.sm};
  line-height: 1.7;
`;

const AgentBadge = styled.div`
  display: inline-flex;
  width: fit-content;
  max-width: 100%;
  height: auto;
  min-height: 24px;
  align-items: flex-start;
  padding: 2px 8px;
  border-radius: ${theme.borderRadius.sm};
  background: rgba(37, 99, 235, 0.12);
  color: ${theme.colors.primaryActive};
  white-space: normal;
  overflow-wrap: break-word;
  word-break: normal;
  line-height: 20px;
`;

const CronValue = styled.code`
  white-space: nowrap;
  color: ${theme.colors.text.primary};
  font-family: ${theme.typography.fontFamily};
`;

const scheduleFrequencyLabel = (cron?: string) => {
  const labels: Record<string, string> = {
    '0 0/15 * * * ?': '每 15 分钟',
    '0 */15 * * * ?': '每 15 分钟',
    '0 0/30 * * * ?': '每 30 分钟',
    '0 */30 * * * ?': '每 30 分钟',
    '0 0 * * * ?': '每小时',
    '0 0 9 * * ?': '每天 09:00',
  };
  return labels[String(cron || '')] || String(cron || '未设置');
};

const scheduleFrequencyValue = (cron?: string) => {
  const value = String(cron || '');
  const aliases: Record<string, string> = {
    '0 */15 * * * ?': '0 0/15 * * * ?',
    '0 */30 * * * ?': '0 0/30 * * * ?',
  };
  const normalized = aliases[value] || value;
  return ['0 0/15 * * * ?', '0 0/30 * * * ?', '0 0 * * * ?', '0 0 9 * * ?'].includes(normalized)
    ? normalized
    : 'CUSTOM';
};

const analysisFlowName = (agent?: OpsAgentDefinition, fallbackId?: string) => agent?.name || fallbackId || '-';

const defaultForm: TaskScheduleRequestDTO = {
  projectId: '',
  agentId: '',
  agentBindingMode: 'LATEST_PUBLISHED',
  taskName: '项目运行状态巡检',
  description: '按周期运行选定的项目级 Agent',
  cronExpression: '0 0/15 * * * ?',
  taskParam: '重点关注接口错误率、实例在线、ERROR/WARN 日志、锁单/结算/退单风险。',
  status: 1,
  rangeMinutes: 15,
  promWindow: '5m',
  includeRecentLogs: true,
  maxRounds: 3,
  subAgentMaxIterations: 3,
  nodeTimeoutSeconds: 120,
  maxEvidenceItems: 12,
  notifyChannel: false,
  notificationChannelId: '',
  notificationTarget: '',
  lightweightScreeningEnabled: false,
  screeningSourceType: 'PROMETHEUS',
  screeningPrimaryUri: '',
  maxErrorRatePercent: 1,
  maxCpuPercent: 85,
  maxHeapPercent: 85,
  minInstanceUpRatio: 1,
};

export const TaskScheduleManagement: React.FC = () => {
  const navigate = useNavigate();
  const { collapsed, sidebarCollapsed, toggleSidebar, closeMobileSidebar } = useResponsiveSidebar();
  const projectScope = useProjectScope();
  const schedulesQuery = useTaskSchedulesQuery(projectScope.projectId);
  const optionsQuery = useTaskScheduleOptionsQuery(projectScope.projectId);
  const saveScheduleMutation = useSaveTaskScheduleMutation(projectScope.projectId);
  const toggleScheduleMutation = useToggleTaskScheduleMutation(projectScope.projectId);
  const deleteScheduleMutation = useDeleteTaskScheduleMutation(projectScope.projectId);
  const runScheduleMutation = useRunTaskScheduleMutation(projectScope.projectId);
  const [modalVisible, setModalVisible] = useState(false);
  const [executionModalVisible, setExecutionModalVisible] = useState(false);
  const [selectedScheduleId, setSelectedScheduleId] = useState<number | undefined>();
  const executionsQuery = useTaskExecutionsQuery(
    projectScope.projectId,
    selectedScheduleId,
    executionModalVisible,
  );
  const [detailModalVisible, setDetailModalVisible] = useState(false);
  const [editing, setEditing] = useState<TaskScheduleResponseDTO | null>(null);
  const [selectedExecution, setSelectedExecution] = useState<TaskExecutionResponseDTO | null>(null);
  const [form, setForm] = useState<TaskScheduleRequestDTO>(defaultForm);
  const schedules = schedulesQuery.data || [];
  const executions = executionsQuery.data || [];
  const agentDefinitions = optionsQuery.data?.agents || [];
  const channels = optionsQuery.data?.channels || [];
  const loading = schedulesQuery.isLoading
    || saveScheduleMutation.isPending
    || toggleScheduleMutation.isPending
    || deleteScheduleMutation.isPending
    || runScheduleMutation.isPending;
  const defaultReactAgentId = projectScope.selectedProject?.defaultAgentId || '';
  const workflowDefinitions = useMemo(
    () => agentDefinitions.filter((agent) => agent.definitionKind === 'SPECIALIZED_WORKFLOW'),
    [agentDefinitions],
  );
  const reactSelected = Boolean(defaultReactAgentId) && form.agentId === defaultReactAgentId;

  const handleLogout = () => {
    clearAuthSession();
    Toast.success('已退出登录');
    navigate('/login');
  };

  const handleNavigation = (path: string) => {
    switch (path) {
      case 'agent-list':
        navigate('/agent-list');
        break;
      case 'agent-config':
        navigate('/agent-config');
        break;
      case 'rag-order-management':
        navigate('/rag-order-management');
        break;
      case 'inspections':
        navigate('/inspections');
        break;
      case 'mcp-tool-management':
        navigate('/mcp-tool-management');
        break;
      default:
        navigate(`/${path}`);
        break;
    }
  };

  const openExecutions = (scheduleId?: number) => {
    if (!scheduleId) return;
    setSelectedScheduleId(scheduleId);
    setExecutionModalVisible(true);
  };

  const openCreate = () => {
    setEditing(null);
    const project = projectScope.selectedProject;
    setForm({
      ...defaultForm,
      projectId: project?.projectId || '',
      agentId: project?.defaultAgentId || '',
      agentBindingMode: 'LATEST_PUBLISHED',
      agentVersion: undefined,
    });
    setModalVisible(true);
  };

  const openEdit = (record: TaskScheduleResponseDTO) => {
    setEditing(record);
    setForm({
      id: record.id,
      projectId: record.projectId,
      agentId: record.agentId || '',
      agentBindingMode: record.agentBindingMode || 'LATEST_PUBLISHED',
      agentVersion: record.agentVersion,
      taskName: record.taskName,
      description: record.description,
      cronExpression: record.cronExpression,
      taskParam: record.taskParam,
      status: record.status,
      rangeMinutes: record.rangeMinutes || 15,
      promWindow: record.promWindow || '5m',
      includeRecentLogs: record.includeRecentLogs !== false,
      maxRounds: record.maxRounds || 3,
      subAgentMaxIterations: record.subAgentMaxIterations || 3,
      nodeTimeoutSeconds: record.nodeTimeoutSeconds || 120,
      maxEvidenceItems: record.maxEvidenceItems || 12,
      notifyChannel: record.notifyChannel === true,
      notificationChannelId: record.notificationChannelId || '',
      notificationTarget: record.notificationTarget || '',
      lightweightScreeningEnabled: record.lightweightScreeningEnabled === true,
      screeningSourceType: record.screeningSourceType || 'PROMETHEUS',
      screeningPrimaryUri: record.screeningPrimaryUri || '',
      maxErrorRatePercent: record.maxErrorRatePercent ?? 1,
      maxCpuPercent: record.maxCpuPercent ?? 85,
      maxHeapPercent: record.maxHeapPercent ?? 85,
      minInstanceUpRatio: record.minInstanceUpRatio ?? 1,
    });
    setModalVisible(true);
  };

  const submitForm = async () => {
    try {
      if (!form.taskName?.trim()) {
        Toast.error('请输入任务名称');
        return;
      }
      if (!form.cronExpression?.trim()) {
        Toast.error('请输入 Cron 表达式');
        return;
      }
      if (form.cronExpression.trim().split(/\s+/).length !== 6) {
        Toast.error('Cron 表达式应包含 6 段，例如：0 0/15 * * * ?');
        return;
      }
      if (!form.projectId) {
        Toast.error('请选择业务系统');
        return;
      }
      if (!form.agentId) {
        Toast.error('当前项目没有可用 ReAct 或 Workflow');
        return;
      }
      if (!reactSelected && form.agentBindingMode === 'PINNED_VERSION' && !form.agentVersion) {
        Toast.error('固定版本模式需要选择一个已发布 Workflow 版本');
        return;
      }
      if (form.notifyChannel && (!form.notificationChannelId || !form.notificationTarget?.trim())) {
        Toast.error('启用完成通知时，请选择消息渠道并填写通知目标');
        return;
      }
      if (form.lightweightScreeningEnabled) {
        const thresholds = [
          form.maxErrorRatePercent,
          form.maxCpuPercent,
          form.maxHeapPercent,
          form.minInstanceUpRatio,
        ];
        if (thresholds.every((value) => value === undefined || Number.isNaN(value))) {
          Toast.error('启用轻量筛查时至少配置一个业务阈值');
          return;
        }
        if ((form.maxErrorRatePercent ?? 0) < 0
          || (form.maxCpuPercent ?? 0) < 0 || (form.maxCpuPercent ?? 0) > 100
          || (form.maxHeapPercent ?? 0) < 0 || (form.maxHeapPercent ?? 0) > 100
          || (form.minInstanceUpRatio ?? 0) < 0 || (form.minInstanceUpRatio ?? 0) > 1) {
          Toast.error('轻量筛查阈值范围不合法');
          return;
        }
      }
      const request = {
        ...form,
        agentId: form.agentId,
        status: form.status ?? 1,
      };
      const saved = await saveScheduleMutation.mutateAsync({ editing: Boolean(editing), request });
      if (!saved) throw new Error('保存失败');
      Toast.success(editing ? '更新成功' : '创建成功');
      setModalVisible(false);
    } catch (error) {
      console.error('保存定时任务失败:', error);
      Toast.error(error instanceof Error ? error.message : '保存失败');
    }
  };

  const toggleStatus = async (record: TaskScheduleResponseDTO) => {
    try {
      const nextStatus = record.status === 1 ? 0 : 1;
      const updated = await toggleScheduleMutation.mutateAsync({ id: record.id, status: nextStatus });
      if (!updated) throw new Error('操作失败');
      Toast.success(nextStatus === 1 ? '已启用' : '已停用');
    } catch (error) {
      console.error('更新任务状态失败:', error);
      Toast.error(error instanceof Error ? error.message : '更新任务状态失败');
    }
  };

  const runNow = async (record: TaskScheduleResponseDTO) => {
    try {
      setSelectedScheduleId(record.id);
      setExecutionModalVisible(true);
      await runScheduleMutation.mutateAsync(record.id);
      Toast.success('已开始执行');
    } catch (error) {
      console.error('立即执行失败:', error);
      Toast.error(error instanceof Error ? error.message : '立即执行失败');
    }
  };

  const deleteSchedule = async (record: TaskScheduleResponseDTO) => {
    try {
      const deleted = await deleteScheduleMutation.mutateAsync(record.id);
      if (!deleted) throw new Error('删除失败');
      Toast.success('删除成功');
    } catch (error) {
      console.error('删除定时任务失败:', error);
      Toast.error(error instanceof Error ? error.message : '删除定时任务失败');
    }
  };

  const columns = [
    {
      title: '任务名称',
      dataIndex: 'taskName',
      width: 190,
    },
    {
      title: '分析方式',
      dataIndex: 'agentId',
      width: 260,
      render: (agentId: string) => {
        if (agentId === projectScope.selectedProject?.defaultAgentId) return <AgentBadge>ReAct</AgentBadge>;
        const agent = workflowDefinitions.find((item) => item.agentId === agentId);
        return <AgentBadge>{analysisFlowName(agent, agentId)}</AgentBadge>;
      },
    },
    {
      title: '执行频率',
      dataIndex: 'cronExpression',
      width: 150,
      render: (value: string) => <CronValue>{scheduleFrequencyLabel(value)}</CronValue>,
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
      key: 'action',
      width: 330,
      fixed: 'right' as const,
      render: (_: any, record: TaskScheduleResponseDTO) => (
        <Space>
          <Button size="small" icon={<IconPlay />} onClick={() => runNow(record)}>立即执行</Button>
          <Button size="small" theme="light" onClick={() => toggleStatus(record)}>
            {record.status === 1 ? '停用' : '启用'}
          </Button>
          <Button size="small" icon={<IconEdit />} onClick={() => openEdit(record)}>编辑</Button>
          <Button size="small" theme="light" onClick={() => openExecutions(record.id)}>记录</Button>
          <Popconfirm
            title="确定删除这个定时任务吗？"
            content="删除后调度会在下一次刷新时停止。"
            onConfirm={() => deleteSchedule(record)}
          >
            <Button size="small" type="danger" icon={<IconDelete />}>删除</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  const executionColumns = [
    {
      title: '开始时间',
      dataIndex: 'startedAt',
      width: 170,
    },
    {
      title: '触发方式',
      dataIndex: 'triggerType',
      width: 100,
      render: (triggerType: string) => triggerType === 'MANUAL' ? '手动' : '定时',
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (status: string) => {
        const color = status === 'SUCCESS' ? 'green' : status === 'FAILED' ? 'red' : 'blue';
        return <Tag color={color}>{status}</Tag>;
      },
    },
    {
      title: '结束时间',
      dataIndex: 'endedAt',
      width: 170,
    },
    {
      title: '操作',
      key: 'action',
      width: 100,
      render: (_: any, record: TaskExecutionResponseDTO) => (
        <Button
          size="small"
          theme="light"
          onClick={() => {
            setSelectedExecution(record);
            setDetailModalVisible(true);
          }}
        >
          查看结果
        </Button>
      ),
    },
  ];

  return (
    <PageLayout>
      <Sidebar
        collapsed={sidebarCollapsed}
        selectedKey="inspections"
        onSelect={handleNavigation}
        onMobileClose={closeMobileSidebar}
      />
      <MainContent $collapsed={collapsed}>
        <ContentArea>
          <Header
            collapsed={sidebarCollapsed}
            onToggleSidebar={toggleSidebar}
            onLogout={handleLogout}
          />
          <PageContainer>
            <PageHeader>
              <Title heading={3} style={{ margin: 0 }}>巡检</Title>
              <Typography.Text type="tertiary">
                为当前项目选择分析方式，按周期执行巡检要求并保留证据和结果。
              </Typography.Text>
            </PageHeader>

            <div style={{ margin: `${theme.spacing.lg} ${theme.spacing.lg} 0` }}>
              <ProjectScopeBar
                projects={projectScope.projects}
                projectId={projectScope.projectId}
                onChange={projectScope.selectProject}
                loading={projectScope.loading}
                onRefresh={() => { void schedulesQuery.refetch(); void optionsQuery.refetch(); }}
                actions={<Button type="primary" icon={<IconPlus />} disabled={!projectScope.projectId} onClick={openCreate}>新建巡检</Button>}
              />
            </div>

            <Toolbar>
              <Space style={{ width: '100%', justifyContent: 'space-between' }}>
                <Space wrap>
                  <Typography.Text strong>巡检任务</Typography.Text>
                </Space>
                <Typography.Text type="tertiary">
                  调度配置每 30 秒自动刷新；启用后按设定频率执行。
                </Typography.Text>
              </Space>
            </Toolbar>

            <TableContainer>
              <TableCard>
                <TableScroll $minWidth="1360px">
                  <Table
                    columns={columns}
                    dataSource={schedules}
                    loading={loading}
                    rowKey="id"
                    pagination={false}
                    empty={
                      <div style={{ padding: 40, textAlign: 'center' }}>
                        <Typography.Text type="tertiary">暂无巡检任务</Typography.Text>
                      </div>
                    }
                  />
                </TableScroll>
              </TableCard>
            </TableContainer>
          </PageContainer>

          <Modal
            title={editing ? '编辑巡检' : '新建巡检'}
            visible={modalVisible}
            onOk={submitForm}
            onCancel={() => setModalVisible(false)}
            okText="保存"
            cancelText="取消"
            width={760}
          >
            <FormGrid>
              <Field>
                <Typography.Text strong>所属项目</Typography.Text>
                <Input value={projectScope.selectedProject?.name || form.projectId || ''} disabled />
              </Field>
              <Field>
                <Typography.Text strong>任务名称</Typography.Text>
                <Input value={form.taskName} onChange={(value) => setForm({ ...form, taskName: value })} />
              </Field>
              <Field>
                <Typography.Text strong>运行方式</Typography.Text>
                <Select
                  aria-label="运行方式"
                  value={reactSelected ? '__REACT__' : (form.agentId || '')}
                  filter
                  style={{ width: '100%' }}
                  placeholder="选择 ReAct 或已发布 Workflow"
                  onSelect={(value) => {
                    const selectedValue = String(value || '');
                    if (selectedValue === '__REACT__') {
                      setForm({ ...form, agentId: defaultReactAgentId, agentBindingMode: 'LATEST_PUBLISHED', agentVersion: undefined });
                      return;
                    }
                    const selected = workflowDefinitions.find((agent) => agent.agentId === selectedValue);
                    setForm({ ...form, agentId: selectedValue, agentVersion: form.agentBindingMode === 'PINNED_VERSION' ? selected?.version : undefined });
                  }}
                >
                  {defaultReactAgentId && <Option value="__REACT__">ReAct</Option>}
                  {workflowDefinitions.map((agent) => (
                    <Option key={agent.agentId} value={agent.agentId}>{analysisFlowName(agent)}</Option>
                  ))}
                </Select>
                <Typography.Text type="tertiary" size="small">
                  ReAct 动态决定调查步骤；Workflow 严格执行管理员已发布的固定 SOP。
                </Typography.Text>
              </Field>
              <Field>
                <Typography.Text strong>执行频率</Typography.Text>
                <Select value={scheduleFrequencyValue(form.cronExpression)} onChange={(value) => {
                  const frequency = String(value || '');
                  setForm({ ...form, cronExpression: frequency === 'CUSTOM' ? '' : frequency });
                }}>
                  <Option value="0 0/15 * * * ?">每 15 分钟</Option>
                  <Option value="0 0/30 * * * ?">每 30 分钟</Option>
                  <Option value="0 0 * * * ?">每小时</Option>
                  <Option value="0 0 9 * * ?">每天 09:00</Option>
                  <Option value="CUSTOM">自定义</Option>
                </Select>
              </Field>
              <FullField>
                <Card title="轻量筛查" bodyStyle={{ padding: 16 }}>
                  <Space vertical align="start" style={{ width: '100%' }}>
                    <Checkbox
                      checked={form.lightweightScreeningEnabled === true}
                      onChange={(event) => setForm({ ...form, lightweightScreeningEnabled: Boolean(event.target.checked) })}
                    >
                      先用 Prometheus 真实指标做低成本判定；只有明确正常才跳过深度 AI 调查
                    </Checkbox>
                    <Typography.Text type="tertiary" size="small">
                      数据源“能访问”不会被当成业务正常。未开启、查询失败或结果无法判定时都会进入深度调查。
                    </Typography.Text>
                    {form.lightweightScreeningEnabled && (
                      <FormGrid>
                        <Field>
                          <Typography.Text strong>目标 URI（可选）</Typography.Text>
                          <Input value={form.screeningPrimaryUri || ''} onChange={(value) => setForm({ ...form, screeningPrimaryUri: value })} placeholder="例如 /api/order" />
                        </Field>
                        <Field>
                          <Typography.Text strong>5xx 错误率上限（%）</Typography.Text>
                          <Input value={String(form.maxErrorRatePercent ?? '')} onChange={(value) => setForm({ ...form, maxErrorRatePercent: value === '' ? undefined : Number(value) })} />
                        </Field>
                        <Field>
                          <Typography.Text strong>CPU 上限（%）</Typography.Text>
                          <Input value={String(form.maxCpuPercent ?? '')} onChange={(value) => setForm({ ...form, maxCpuPercent: value === '' ? undefined : Number(value) })} />
                        </Field>
                        <Field>
                          <Typography.Text strong>Heap 上限（%）</Typography.Text>
                          <Input value={String(form.maxHeapPercent ?? '')} onChange={(value) => setForm({ ...form, maxHeapPercent: value === '' ? undefined : Number(value) })} />
                        </Field>
                        <Field>
                          <Typography.Text strong>实例在线率下限（0-1）</Typography.Text>
                          <Input value={String(form.minInstanceUpRatio ?? '')} onChange={(value) => setForm({ ...form, minInstanceUpRatio: value === '' ? undefined : Number(value) })} />
                        </Field>
                      </FormGrid>
                    )}
                  </Space>
                </Card>
              </FullField>
              <FullField>
                <Collapse keepDOM>
                  <Collapse.Panel itemKey="advanced" header="高级设置">
                    <FormGrid>
                      {!reactSelected && <Field>
                        <Typography.Text strong>Workflow 版本策略</Typography.Text>
                        <Select value={form.agentBindingMode || 'LATEST_PUBLISHED'} onChange={(value) => {
                          const agentBindingMode = String(value) as 'LATEST_PUBLISHED' | 'PINNED_VERSION';
                          const selected = workflowDefinitions.find((agent) => agent.agentId === form.agentId);
                          setForm({ ...form, agentBindingMode, agentVersion: agentBindingMode === 'PINNED_VERSION' ? selected?.version : undefined });
                        }}>
                          <Option value="LATEST_PUBLISHED">使用最新已发布版本</Option>
                          <Option value="PINNED_VERSION">固定当前版本</Option>
                        </Select>
                      </Field>}
                      <Field><Typography.Text strong>Cron 表达式</Typography.Text><Input value={form.cronExpression} onChange={(value) => setForm({ ...form, cronExpression: value })} /></Field>
                      <Field><Typography.Text strong>日志窗口</Typography.Text><Select value={form.rangeMinutes} onChange={(value) => setForm({ ...form, rangeMinutes: value as number })}><Option value={5}>最近 5 分钟</Option><Option value={15}>最近 15 分钟</Option><Option value={30}>最近 30 分钟</Option><Option value={60}>最近 1 小时</Option></Select></Field>
                      <Field><Typography.Text strong>指标窗口</Typography.Text><Select value={form.promWindow} onChange={(value) => setForm({ ...form, promWindow: value as string })}><Option value="1m">1 分钟</Option><Option value="5m">5 分钟</Option><Option value="15m">15 分钟</Option><Option value="30m">30 分钟</Option><Option value="1h">1 小时</Option></Select></Field>
                      <Field><Typography.Text strong>最大分析轮次</Typography.Text><Select value={form.maxRounds} onChange={(value) => setForm({ ...form, maxRounds: value as number })}><Option value={1}>1</Option><Option value={3}>3</Option><Option value={5}>5</Option><Option value={10}>10</Option><Option value={20}>20</Option></Select></Field>
                      <Field><Typography.Text strong>子任务最大轮次</Typography.Text><Select value={form.subAgentMaxIterations} onChange={(value) => setForm({ ...form, subAgentMaxIterations: value as number })}><Option value={1}>1</Option><Option value={3}>3</Option><Option value={5}>5</Option><Option value={10}>10</Option></Select></Field>
                      <Field><Typography.Text strong>单步超时秒</Typography.Text><Input value={String(form.nodeTimeoutSeconds || 120)} onChange={(value) => setForm({ ...form, nodeTimeoutSeconds: Number(value) || 120 })} /></Field>
                      <Field><Typography.Text strong>最大证据条数</Typography.Text><Input value={String(form.maxEvidenceItems || 12)} onChange={(value) => setForm({ ...form, maxEvidenceItems: Number(value) || 12 })} /></Field>
                    </FormGrid>
                  </Collapse.Panel>
                </Collapse>
              </FullField>
              <Field>
                <Typography.Text strong>状态</Typography.Text>
                <Select value={form.status} onChange={(value) => setForm({ ...form, status: value as number })}>
                  <Option value={1}>启用</Option>
                  <Option value={0}>停用</Option>
                </Select>
              </Field>
              <FullField>
                <Typography.Text strong>执行要求</Typography.Text>
                <TextArea
                  rows={5}
                  value={form.taskParam}
                  onChange={(value) => setForm({ ...form, taskParam: value })}
                  placeholder="写清楚周期任务要关注的问题、数据范围、业务风险或输出格式。"
                />
              </FullField>
              <FullField>
                <Space>
                  <Checkbox
                    checked={form.includeRecentLogs !== false}
                    onChange={(event) => setForm({ ...form, includeRecentLogs: Boolean(event.target.checked) })}
                  >
                    查询最近日志
                  </Checkbox>
                  <Checkbox
                    checked={form.notifyChannel === true}
                    onChange={(event) => setForm({ ...form, notifyChannel: Boolean(event.target.checked) })}
                  >
                    完成后发送 Channel 通知
                  </Checkbox>
                </Space>
              </FullField>
              {form.notifyChannel && (
                <>
                  <Field>
                    <Typography.Text strong>消息渠道</Typography.Text>
                    <Select value={form.notificationChannelId || undefined} placeholder="选择项目已配置的 Channel" onChange={(value) => setForm({ ...form, notificationChannelId: String(value || '') })}>
                      {channels.filter((channel) => channel.status === 'ACTIVE').map((channel) => <Option key={channel.channelId} value={channel.channelId}>{channel.name}</Option>)}
                    </Select>
                  </Field>
                  <Field>
                    <Typography.Text strong>通知目标</Typography.Text>
                    <Input value={form.notificationTarget || ''} placeholder="群聊、会话或接收人标识" onChange={(value) => setForm({ ...form, notificationTarget: value })} />
                  </Field>
                </>
              )}
            </FormGrid>
          </Modal>

          <Modal
            title="执行记录"
            visible={executionModalVisible}
            onCancel={() => setExecutionModalVisible(false)}
            footer={null}
            width={860}
          >
            <Table
              columns={executionColumns}
              dataSource={executions}
              rowKey="id"
              pagination={false}
              empty={<div style={{ padding: 32, textAlign: 'center' }}>暂无执行记录</div>}
            />
          </Modal>

          <Modal
            title="任务结果"
            visible={detailModalVisible}
            onCancel={() => setDetailModalVisible(false)}
            footer={null}
            width={900}
          >
            <Space vertical align="start" spacing="medium" style={{ width: '100%' }}>
              {selectedExecution?.errorMessage && (
                <Tag color="red">{selectedExecution.errorMessage}</Tag>
              )}
              <ResultContent>{selectedExecution?.output || selectedExecution?.input || '暂无输出'}</ResultContent>
            </Space>
          </Modal>
        </ContentArea>
      </MainContent>
    </PageLayout>
  );
};
