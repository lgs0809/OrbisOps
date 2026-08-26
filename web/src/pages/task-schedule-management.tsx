import React, { useMemo, useState } from 'react';
import {
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
import { ProjectScopeBar } from '../components/project-scope-bar';
import { useProjectScope } from '../hooks/use-project-scope';
import { OpsPageHeader, OpsPageShell, TableScroll } from '../components/ops-layout';
import { userFacingDetail, userFacingError } from '../utils/user-facing-error';
const { Option } = Select;

const Toolbar = styled(Card)`
  margin: ${theme.spacing.lg} 0;

  .semi-card-body {
    padding: ${theme.spacing.lg};
  }
`;

const TableContainer = styled.div`
  flex: 1;
  margin-bottom: ${theme.spacing.lg};
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

const FrequencyValue = styled.span`
  white-space: nowrap;
  color: ${theme.colors.text.primary};
  font-family: ${theme.typography.fontFamily};
`;

const StarterSection = styled(Card)`
  margin: ${theme.spacing.lg} 0;

  .semi-card-body {
    padding: ${theme.spacing.lg};
  }
`;

const StarterHeader = styled.div`
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
  flex-wrap: wrap;
`;

const StarterGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;

  @media (max-width: 980px) {
    grid-template-columns: 1fr;
  }
`;

const StarterItem = styled.div`
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 7px;
  padding: 12px;
  border: 1px solid ${theme.colors.border.tertiary};
  border-radius: 10px;
  background: #fafbfc;

  .starter-copy {
    min-height: 48px;
  }
`;

const scheduleFrequencyLabel = (cron?: string) => {
  const labels: Record<string, string> = {
    '0 0/15 * * * ?': '每 15 分钟',
    '0 */15 * * * ?': '每 15 分钟',
    '0 0/30 * * * ?': '每 30 分钟',
    '0 */30 * * * ?': '每 30 分钟',
    '0 0 * * * ?': '每小时',
    '0 0 8 * * ?': '每天 08:00',
    '0 0 9 * * ?': '每天 09:00',
  };
  const value = String(cron || '');
  return labels[value] || (value ? '自定义' : '未配置');
};

const scheduleFrequencyValue = (cron?: string) => {
  const value = String(cron || '');
  const aliases: Record<string, string> = {
    '0 */15 * * * ?': '0 0/15 * * * ?',
    '0 */30 * * * ?': '0 0/30 * * * ?',
  };
  const normalized = aliases[value] || value;
  return ['0 0/15 * * * ?', '0 0/30 * * * ?', '0 0 * * * ?', '0 0 8 * * ?', '0 0 9 * * ?'].includes(normalized)
    ? normalized
    : 'CUSTOM';
};

const analysisFlowName = (agent?: OpsAgentDefinition, fallbackId?: string) => agent?.name || fallbackId || '-';

interface ScheduleStarter {
  name: string;
  description: string;
  cronExpression: string;
  taskParam: string;
  rangeMinutes: number;
  promWindow: string;
}

const SCHEDULE_STARTERS: ScheduleStarter[] = [
  {
    name: '服务健康巡检',
    description: '每 15 分钟快速看看服务是否正常。',
    cronExpression: '0 0/15 * * * ?',
    taskParam: '看看服务是否在线、错误率有没有上升，以及最近有没有明显报错；只读检查，给我简短结论。',
    rangeMinutes: 15,
    promWindow: '5m',
  },
  {
    name: '早间运行摘要',
    description: '每天早上汇总昨晚的运行情况。',
    cronExpression: '0 0 9 * * ?',
    taskParam: '帮我看一下昨晚的服务状态、错误日志和消息队列，有明显异常再展开说明；只读，给我三点结论。',
    rangeMinutes: 60,
    promWindow: '15m',
  },
  {
    name: '队列积压检查',
    description: '每 30 分钟检查消息是否堆积。',
    cronExpression: '0 0/30 * * * ?',
    taskParam: '看看消息队列有没有积压或消费变慢，只读检查；如果发现异常，说明影响范围和还缺什么证据。',
    rangeMinutes: 30,
    promWindow: '5m',
  },
];

const defaultForm: TaskScheduleRequestDTO = {
  projectId: '',
  executionType: 'DEFAULT_REACT',
  agentId: '',
  agentBindingMode: 'LATEST_PUBLISHED',
  taskName: '服务健康巡检',
  description: '按频率检查当前 Project 的服务状态',
  cronExpression: '0 0/15 * * * ?',
  taskParam: '看看服务是否在线、错误率有没有上升，以及最近有没有明显报错；只读检查，给我简短结论。',
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
  const reactSelected = form.executionType === 'DEFAULT_REACT'
    || (!form.executionType && Boolean(defaultReactAgentId) && form.agentId === defaultReactAgentId);

  const openExecutions = (scheduleId?: number) => {
    if (!scheduleId) return;
    setSelectedScheduleId(scheduleId);
    setExecutionModalVisible(true);
  };

  const openCreate = (starter?: ScheduleStarter) => {
    setEditing(null);
    const project = projectScope.selectedProject;
    setForm({
      ...defaultForm,
      taskName: starter?.name || defaultForm.taskName,
      description: starter?.description || defaultForm.description,
      cronExpression: starter?.cronExpression || defaultForm.cronExpression,
      taskParam: starter?.taskParam || defaultForm.taskParam,
      rangeMinutes: starter?.rangeMinutes || defaultForm.rangeMinutes,
      promWindow: starter?.promWindow || defaultForm.promWindow,
      projectId: project?.projectId || '',
      executionType: 'DEFAULT_REACT',
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
      executionType: record.executionType || (record.agentId === defaultReactAgentId ? 'DEFAULT_REACT' : 'WORKFLOW'),
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
        Toast.error('请输入自动化名称。');
        return;
      }
      if (!form.cronExpression?.trim()) {
        Toast.error('请输入运行频率。');
        return;
      }
      if (form.cronExpression.trim().split(/\s+/).length !== 6) {
        Toast.error('自定义频率格式不正确，请检查后重试。');
        return;
      }
      if (!form.projectId) {
        Toast.error('请选择 Project。');
        return;
      }
      if (!form.agentId) {
        Toast.error('当前 Project 没有可用的 默认助手 或已发布 Workflow。');
        return;
      }
      if (!reactSelected && form.agentBindingMode === 'PINNED_VERSION' && !form.agentVersion) {
        Toast.error('固定版本模式必须指定一个已发布 Workflow 版本。');
        return;
      }
      if (form.notifyChannel && (!form.notificationChannelId || !form.notificationTarget?.trim())) {
        Toast.error('启用完成通知时，请选择 Channel 和通知目标。');
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
          Toast.error('启用轻量筛选时，至少配置一个阈值。');
          return;
        }
        if ((form.maxErrorRatePercent ?? 0) < 0
          || (form.maxCpuPercent ?? 0) < 0 || (form.maxCpuPercent ?? 0) > 100
          || (form.maxHeapPercent ?? 0) < 0 || (form.maxHeapPercent ?? 0) > 100
          || (form.minInstanceUpRatio ?? 0) < 0 || (form.minInstanceUpRatio ?? 0) > 1) {
          Toast.error('轻量筛选阈值超出允许范围。');
          return;
        }
      }
      const request = {
        ...form,
        agentId: form.agentId,
        status: form.status ?? 1,
      };
      const saved = await saveScheduleMutation.mutateAsync({ editing: Boolean(editing), request });
      if (!saved) throw new Error('保存失败。');
      Toast.success(editing ? '自动化已更新。' : '自动化已创建。');
      setModalVisible(false);
    } catch (error) {
      console.error('Unable to save Automation:', error);
      Toast.error(userFacingError(error, '保存自动化失败，请稍后重试。'));
    }
  };

  const toggleStatus = async (record: TaskScheduleResponseDTO) => {
    try {
      const nextStatus = record.status === 1 ? 0 : 1;
      const updated = await toggleScheduleMutation.mutateAsync({ id: record.id, status: nextStatus });
      if (!updated) throw new Error('操作失败。');
      Toast.success(nextStatus === 1 ? '自动化已启用。' : '自动化已停用。');
    } catch (error) {
      console.error('Unable to update Automation status:', error);
      Toast.error(userFacingError(error, '更新自动化状态失败，请稍后重试。'));
    }
  };

  const runNow = async (record: TaskScheduleResponseDTO) => {
    try {
      setSelectedScheduleId(record.id);
      setExecutionModalVisible(true);
      await runScheduleMutation.mutateAsync(record.id);
      Toast.success('Run 已启动。');
    } catch (error) {
      console.error('Unable to start Automation:', error);
      Toast.error(userFacingError(error, '启动自动化失败，请稍后重试。'));
    }
  };

  const deleteSchedule = async (record: TaskScheduleResponseDTO) => {
    try {
      const deleted = await deleteScheduleMutation.mutateAsync(record.id);
      if (!deleted) throw new Error('删除失败。');
      Toast.success('自动化已删除。');
    } catch (error) {
      console.error('Unable to delete Automation:', error);
      Toast.error(userFacingError(error, '删除自动化失败，请稍后重试。'));
    }
  };

  const columns = [
    {
      title: '自动化',
      dataIndex: 'taskName',
      width: 190,
    },
    {
      title: '执行方式',
      dataIndex: 'agentId',
      width: 260,
      render: (agentId: string, record: TaskScheduleResponseDTO) => {
        if (record.executionType === 'DEFAULT_REACT' || agentId === projectScope.selectedProject?.defaultAgentId) {
          return <AgentBadge>默认助手</AgentBadge>;
        }
        const agent = workflowDefinitions.find((item) => item.agentId === agentId);
        return <AgentBadge>{analysisFlowName(agent, agentId)}</AgentBadge>;
      },
    },
    {
      title: '运行频率',
      dataIndex: 'cronExpression',
      width: 150,
      render: (value: string) => <FrequencyValue>{scheduleFrequencyLabel(value)}</FrequencyValue>,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (status: number) => <Tag color={status === 1 ? 'green' : 'grey'}>{status === 1 ? '已启用' : '已停用'}</Tag>,
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
          <Button size="small" icon={<IconPlay />} onClick={() => runNow(record)}>立即运行</Button>
          <Button size="small" theme="light" onClick={() => toggleStatus(record)}>
            {record.status === 1 ? '停用' : '启用'}
          </Button>
          <Button size="small" icon={<IconEdit />} onClick={() => openEdit(record)}>编辑</Button>
          <Button size="small" theme="light" onClick={() => openExecutions(record.id)}>Run 记录</Button>
          <Popconfirm
            title="删除这个定时自动化？"
            content="配置刷新后将停止后续调度；已有 Run 历史会继续保留。"
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
        const label = status === 'SUCCESS' ? '成功' : status === 'FAILED' ? '失败' : status === 'RUNNING' ? '运行中' : status === 'WAITING_APPROVAL' ? '等待审批' : status === 'CANCELED' ? '已取消' : status;
        return <Tag color={color}>{label}</Tag>;
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
    <OpsPageShell selectedKey="automations" maxWidth="1280px">
      <OpsPageHeader
        title="定时自动化"
        description="选择一个中文频率运行 默认助手 或已发布 Workflow；每次执行都会形成正式 Run，并进入工作台统一查看。"
      />

      <ProjectScopeBar
        projects={projectScope.projects}
        projectId={projectScope.projectId}
        onChange={projectScope.selectProject}
        loading={projectScope.loading}
        onRefresh={() => { void schedulesQuery.refetch(); void optionsQuery.refetch(); }}
        actions={<Button type="primary" icon={<IconPlus />} disabled={!projectScope.projectId} onClick={() => openCreate()}>新建定时自动化</Button>}
      />

      <Toolbar>
        <Space style={{ width: '100%', justifyContent: 'space-between' }}>
          <Space wrap>
            <Typography.Text strong>定时自动化</Typography.Text>
          </Space>
          <Typography.Text type="tertiary">
            每 30 秒刷新一次；运行频率会用中文显示，方便快速确认。
          </Typography.Text>
        </Space>
      </Toolbar>

      <StarterSection>
        <StarterHeader>
          <Typography.Text strong>常用起步模板</Typography.Text>
          <Typography.Text type="tertiary" size="small">先选一个常用频率，也可以按需选择“自定义”。</Typography.Text>
        </StarterHeader>
        <StarterGrid>
          {SCHEDULE_STARTERS.map((starter) => (
            <StarterItem key={starter.name}>
              <div className="starter-copy">
                <Typography.Text strong>{starter.name}</Typography.Text>
                <Typography.Text type="tertiary" size="small" style={{ display: 'block', marginTop: 3 }}>{starter.description}</Typography.Text>
              </div>
              <Button size="small" disabled={!projectScope.projectId} onClick={() => openCreate(starter)}>使用模板</Button>
            </StarterItem>
          ))}
        </StarterGrid>
      </StarterSection>

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
                  <Typography.Text type="tertiary">暂无定时自动化。</Typography.Text>
                </div>
              }
            />
          </TableScroll>
        </TableCard>
      </TableContainer>

          <Modal
            title={editing ? '编辑定时自动化' : '新建定时自动化'}
            visible={modalVisible}
            onOk={submitForm}
            onCancel={() => setModalVisible(false)}
            okText="保存"
            cancelText="取消"
            width={760}
          >
            <FormGrid>
              <Field>
                <Typography.Text strong>Project</Typography.Text>
                <Input value={projectScope.selectedProject?.name || form.projectId || ''} disabled />
              </Field>
              <Field>
                <Typography.Text strong>自动化名称</Typography.Text>
                <Input value={form.taskName} onChange={(value) => setForm({ ...form, taskName: value })} />
              </Field>
              <Field>
                <Typography.Text strong>执行方式</Typography.Text>
                <Select
                  aria-label="执行方式"
                  value={reactSelected ? '__REACT__' : (form.agentId || '')}
                  filter
                  style={{ width: '100%' }}
                  placeholder="选择 默认助手 或已发布 Workflow"
                  onSelect={(value) => {
                    const selectedValue = String(value || '');
                    if (selectedValue === '__REACT__') {
                      setForm({
                        ...form,
                        executionType: 'DEFAULT_REACT',
                        agentId: defaultReactAgentId,
                        agentBindingMode: 'LATEST_PUBLISHED',
                        agentVersion: undefined,
                      });
                      return;
                    }
                    const selected = workflowDefinitions.find((agent) => agent.agentId === selectedValue);
                    setForm({
                      ...form,
                      executionType: 'WORKFLOW',
                      agentId: selectedValue,
                      agentVersion: form.agentBindingMode === 'PINNED_VERSION' ? selected?.version : undefined,
                    });
                  }}
                >
                  {defaultReactAgentId && <Option value="__REACT__">默认助手</Option>}
                  {workflowDefinitions.map((agent) => (
                    <Option key={agent.agentId} value={agent.agentId}>{analysisFlowName(agent)}</Option>
                  ))}
                </Select>
                <Typography.Text type="tertiary" size="small">
                  默认助手 会动态决定排查步骤；已发布 Workflow 则按固定宏观流程执行。
                </Typography.Text>
              </Field>
              <Field>
                <Typography.Text strong>运行频率</Typography.Text>
                <Select value={scheduleFrequencyValue(form.cronExpression)} onChange={(value) => {
                  const frequency = String(value || '');
                  setForm({ ...form, cronExpression: frequency === 'CUSTOM' ? '' : frequency });
                }}>
                  <Option value="0 0/15 * * * ?">每 15 分钟</Option>
                  <Option value="0 0/30 * * * ?">每 30 分钟</Option>
                  <Option value="0 0 * * * ?">每小时</Option>
                  <Option value="0 0 8 * * ?">每天 08:00</Option>
                  <Option value="0 0 9 * * ?">每天 09:00</Option>
                  <Option value="CUSTOM">自定义</Option>
                </Select>
                <Typography.Text type="tertiary" size="small">
                  常用频率已经翻译成中文；需要特殊安排时选择“自定义”。
                </Typography.Text>
              </Field>
              <FullField>
                <Card title="轻量筛选" bodyStyle={{ padding: 16 }}>
                  <Space vertical align="start" style={{ width: '100%' }}>
                    <Checkbox
                      checked={form.lightweightScreeningEnabled === true}
                      onChange={(event) => setForm({ ...form, lightweightScreeningEnabled: Boolean(event.target.checked) })}
                    >
                      先检查低成本 Prometheus 信号；只有健康状态明确正常时，才跳过更深层的 AI 排查
                    </Checkbox>
                    <Typography.Text type="tertiary" size="small">
                      仅“可访问”绝不等同于健康。关闭筛选、查询失败或结果不明确时，都必须继续进入所选执行绑定。
                    </Typography.Text>
                    {form.lightweightScreeningEnabled && (
                      <FormGrid>
                        <Field>
                          <Typography.Text strong>目标 URI（可选）</Typography.Text>
                          <Input value={form.screeningPrimaryUri || ''} onChange={(value) => setForm({ ...form, screeningPrimaryUri: value })} placeholder="例如 /api/order" />
                        </Field>
                        <Field>
                          <Typography.Text strong>最大 5xx 比例（%）</Typography.Text>
                          <Input value={String(form.maxErrorRatePercent ?? '')} onChange={(value) => setForm({ ...form, maxErrorRatePercent: value === '' ? undefined : Number(value) })} />
                        </Field>
                        <Field>
                          <Typography.Text strong>最大 CPU（%）</Typography.Text>
                          <Input value={String(form.maxCpuPercent ?? '')} onChange={(value) => setForm({ ...form, maxCpuPercent: value === '' ? undefined : Number(value) })} />
                        </Field>
                        <Field>
                          <Typography.Text strong>最大 Heap（%）</Typography.Text>
                          <Input value={String(form.maxHeapPercent ?? '')} onChange={(value) => setForm({ ...form, maxHeapPercent: value === '' ? undefined : Number(value) })} />
                        </Field>
                        <Field>
                          <Typography.Text strong>最小实例存活比例（0-1）</Typography.Text>
                          <Input value={String(form.minInstanceUpRatio ?? '')} onChange={(value) => setForm({ ...form, minInstanceUpRatio: value === '' ? undefined : Number(value) })} />
                        </Field>
                      </FormGrid>
                    )}
                  </Space>
                </Card>
              </FullField>
              <FullField>
                <Collapse keepDOM>
                  <Collapse.Panel itemKey="advanced" header="更多设置（可选）">
                    <FormGrid>
                      {!reactSelected && <Field>
                        <Typography.Text strong>Workflow 版本策略</Typography.Text>
                        <Select value={form.agentBindingMode || 'LATEST_PUBLISHED'} onChange={(value) => {
                          const agentBindingMode = String(value) as 'LATEST_PUBLISHED' | 'PINNED_VERSION';
                          const selected = workflowDefinitions.find((agent) => agent.agentId === form.agentId);
                          setForm({ ...form, agentBindingMode, agentVersion: agentBindingMode === 'PINNED_VERSION' ? selected?.version : undefined });
                        }}>
                          <Option value="LATEST_PUBLISHED">始终使用最新已发布版本</Option>
                          <Option value="PINNED_VERSION">固定当前版本</Option>
                        </Select>
                      </Field>}
                      <Field><Typography.Text strong>自定义</Typography.Text><Input value={form.cronExpression} onChange={(value) => setForm({ ...form, cronExpression: value })} placeholder="输入自定义频率" /></Field>
                      <Field><Typography.Text strong>日志窗口</Typography.Text><Select value={form.rangeMinutes} onChange={(value) => setForm({ ...form, rangeMinutes: value as number })}><Option value={5}>最近 5 分钟</Option><Option value={15}>最近 15 分钟</Option><Option value={30}>最近 30 分钟</Option><Option value={60}>最近 1 小时</Option></Select></Field>
                      <Field><Typography.Text strong>指标窗口</Typography.Text><Select value={form.promWindow} onChange={(value) => setForm({ ...form, promWindow: value as string })}><Option value="1m">1 分钟</Option><Option value="5m">5 分钟</Option><Option value="15m">15 分钟</Option><Option value="30m">30 分钟</Option><Option value="1h">1 小时</Option></Select></Field>
                      <Field><Typography.Text strong>最大分析轮数</Typography.Text><Select value={form.maxRounds} onChange={(value) => setForm({ ...form, maxRounds: value as number })}><Option value={1}>1</Option><Option value={3}>3</Option><Option value={5}>5</Option><Option value={10}>10</Option><Option value={20}>20</Option></Select></Field>
                      <Field><Typography.Text strong>最大子任务轮数</Typography.Text><Select value={form.subAgentMaxIterations} onChange={(value) => setForm({ ...form, subAgentMaxIterations: value as number })}><Option value={1}>1</Option><Option value={3}>3</Option><Option value={5}>5</Option><Option value={10}>10</Option></Select></Field>
                      <Field><Typography.Text strong>步骤超时（秒）</Typography.Text><Input value={String(form.nodeTimeoutSeconds || 120)} onChange={(value) => setForm({ ...form, nodeTimeoutSeconds: Number(value) || 120 })} /></Field>
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
                <Typography.Text strong>执行说明</Typography.Text>
                <TextArea
                  rows={5}
                  value={form.taskParam}
                  onChange={(value) => setForm({ ...form, taskParam: value })}
                  placeholder="描述自动化需要检查什么、数据范围、关注的运维风险以及期望输出。"
                />
              </FullField>
              <FullField>
                <Space>
                  <Checkbox
                    checked={form.includeRecentLogs !== false}
                    onChange={(event) => setForm({ ...form, includeRecentLogs: Boolean(event.target.checked) })}
                  >
                    查询近期日志
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
                    <Typography.Text strong>Channel</Typography.Text>
                    <Select value={form.notificationChannelId || undefined} placeholder="选择已配置的 Project Channel" onChange={(value) => setForm({ ...form, notificationChannelId: String(value || '') })}>
                      {channels.filter((channel) => channel.status === 'ACTIVE').map((channel) => <Option key={channel.channelId} value={channel.channelId}>{channel.name}</Option>)}
                    </Select>
                  </Field>
                  <Field>
                    <Typography.Text strong>通知目标</Typography.Text>
                    <Input value={form.notificationTarget || ''} placeholder="会话、群组或接收者标识" onChange={(value) => setForm({ ...form, notificationTarget: value })} />
                  </Field>
                </>
              )}
            </FormGrid>
          </Modal>

          <Modal
            title="自动化 Run"
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
              empty={<div style={{ padding: 32, textAlign: 'center' }}>暂无 Run。</div>}
            />
          </Modal>

          <Modal
            title="Run 结果"
            visible={detailModalVisible}
            onCancel={() => setDetailModalVisible(false)}
            footer={null}
            width={900}
          >
            <Space vertical align="start" spacing="medium" style={{ width: '100%' }}>
              {selectedExecution?.errorMessage && (
                <Tag color="red">{userFacingDetail(selectedExecution.errorMessage, '本次 Run 执行失败，请查看技术详情或审计记录。')}</Tag>
              )}
              <ResultContent>{selectedExecution?.output || selectedExecution?.input || '暂无输出。'}</ResultContent>
            </Space>
          </Modal>
    </OpsPageShell>
  );
};
