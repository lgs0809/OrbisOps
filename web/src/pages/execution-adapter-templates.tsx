import React, { useMemo, useState } from 'react';
import {
  Button,
  Checkbox,
  Input,
  Modal,
  Select,
  Space,
  Table,
  Tag,
  TextArea,
  Toast,
  Typography,
} from '@douyinfe/semi-ui';
import { IconPlus, IconRefresh } from '@douyinfe/semi-icons';

import {
  OpsAdvancedPreview,
  OpsCapabilityFlow,
  OpsEmptyState,
  JsonBlock,
  OpsPageHeader,
  OpsPageShell,
  OpsSectionCard,
  TableScroll,
} from '../components/ops-layout';
import type { OpsExecutionAdapterTemplate } from '../services/ops-repair-service';
import {
  useCopyExecutionAdapterTemplateMutation,
  useExecutionAdapterTemplateTargetsMutation,
  useExecutionAdapterTemplatesQuery,
  useSaveExecutionAdapterTemplateMutation,
  useToggleExecutionAdapterTemplateMutation,
} from '../features/execution-targets/api/execution-target-queries';
import { generatedId } from '../utils/generated-id';

const { Option } = Select;
const { Paragraph, Text } = Typography;

const adapterOptions = [
  { value: 'local-java-service', label: '本地 Java 制品部署' },
  { value: 'deployment-http', label: '部署平台 HTTP API' },
  { value: 'mysql-controlled', label: 'MySQL 受控执行' },
  { value: 'redis-controlled', label: 'Redis 受控执行' },
  { value: 'rabbitmq-policy', label: 'RabbitMQ Policy 受控执行' },
];

const adapterActionPresets: Record<string, { actions: string[]; riskLevel: string; readOnly: boolean }> = {
  'local-java-service': {
    actions: ['SERVICE_DEPLOY', 'SERVICE_RESTART', 'HEALTH_CHECK'],
    riskLevel: 'HIGH',
    readOnly: false,
  },
  'deployment-http': {
    actions: ['DEPLOYMENT_TRIGGER', 'DEPLOYMENT_STATUS', 'DEPLOYMENT_ROLLBACK'],
    riskLevel: 'HIGH',
    readOnly: false,
  },
  'mysql-controlled': {
    actions: ['MYSQL_CREATE_INDEX', 'MYSQL_UPDATE_LIMITED', 'MYSQL_SET_GLOBAL_VARIABLE'],
    riskLevel: 'CRITICAL',
    readOnly: false,
  },
  'redis-controlled': {
    actions: ['REDIS_UPDATE_TTL', 'REDIS_SET_CONFIG_KEY', 'REDIS_DELETE_LIMITED_KEYS'],
    riskLevel: 'HIGH',
    readOnly: false,
  },
  'rabbitmq-policy': {
    actions: ['RABBITMQ_POLICY_APPLY', 'RABBITMQ_POLICY_REMOVE', 'RABBITMQ_POLICY_VERIFY'],
    riskLevel: 'HIGH',
    readOnly: false,
  },
};

const executionActionOptions = Array.from(
  new Set(Object.values(adapterActionPresets).flatMap((preset) => preset.actions)),
).sort();

const actionsFromText = (value?: string) => String(value || '')
  .split(/[,\n，、]+/)
  .map((item) => item.trim())
  .filter(Boolean);

const emptyForm = {
  adapterTemplateId: '',
  templateName: '',
  adapterType: 'deployment-http',
  supportedActions: '',
  defaultConfig: '{\n  "credentialRef": "ops/<project>/<resource>",\n  "timeoutMs": 30000,\n  "preconditionReadSupported": true,\n  "postCheckReadSupported": true,\n  "rollbackSupported": false,\n  "notes": "真实地址、密钥和资源范围请在项目执行目标中配置"\n}',
  riskLevel: 'HIGH',
  readOnly: false,
  description: '',
  status: 'ENABLED',
};

const riskColor = (risk?: string) => {
  if (risk === 'CRITICAL') return 'red';
  if (risk === 'HIGH') return 'orange';
  if (risk === 'MEDIUM') return 'yellow';
  return 'green';
};

export const ExecutionAdapterTemplatesPage: React.FC = () => {
  const [formVisible, setFormVisible] = useState(false);
  const [editing, setEditing] = useState<OpsExecutionAdapterTemplate | null>(null);
  const [form, setForm] = useState({ ...emptyForm });
  const [targetsVisible, setTargetsVisible] = useState(false);
  const [targetTitle, setTargetTitle] = useState('生成记录');

  const templatesQuery = useExecutionAdapterTemplatesQuery();
  const saveTemplateMutation = useSaveExecutionAdapterTemplateMutation();
  const copyTemplateMutation = useCopyExecutionAdapterTemplateMutation();
  const toggleTemplateMutation = useToggleExecutionAdapterTemplateMutation();
  const targetsMutation = useExecutionAdapterTemplateTargetsMutation();

  const templates = templatesQuery.data || [];
  const loading = templatesQuery.isFetching;
  const saving = saveTemplateMutation.isPending;
  const generatedTargets = targetsMutation.data || [];
  const error = templatesQuery.error instanceof Error ? templatesQuery.error.message : '';

  const enabledCount = useMemo(
    () => templates.filter((item) => String(item.status).toUpperCase() === 'ENABLED').length,
    [templates],
  );

  const openCreate = () => {
    setEditing(null);
    setForm({ ...emptyForm });
    setFormVisible(true);
  };

  const openEdit = (template: OpsExecutionAdapterTemplate) => {
    setEditing(template);
    setForm({
      adapterTemplateId: template.adapterTemplateId,
      templateName: template.templateName || template.name || template.adapterTemplateId,
      adapterType: String(template.adapterType || 'deployment-http'),
      supportedActions: (template.supportedActions || []).join(', '),
      defaultConfig: JSON.stringify(template.defaultConfig || {}, null, 2),
      riskLevel: String(template.riskLevel || 'HIGH'),
      readOnly: Boolean(template.readOnly),
      description: template.description || '',
      status: String(template.status || 'ENABLED'),
    });
    setFormVisible(true);
  };

  const submitTemplate = async () => {
    try {
      const defaultConfig = JSON.parse(form.defaultConfig || '{}');
      const payload = {
        adapterTemplateId: form.adapterTemplateId.trim() || generatedId('adapter-template', form.templateName),
        templateName: form.templateName.trim(),
        adapterType: form.adapterType,
        supportedActions: form.supportedActions
          .split(/[,\n，、]+/)
          .map((item) => item.trim())
          .filter(Boolean),
        defaultConfig,
        riskLevel: form.riskLevel,
        readOnly: form.readOnly,
        description: form.description,
        status: form.status,
      };
      await saveTemplateMutation.mutateAsync({
        editingId: editing?.adapterTemplateId,
        payload,
      });
      Toast.success(editing ? '模板已更新' : '模板已创建');
      setFormVisible(false);
    } catch (err) {
      Toast.error(err instanceof Error ? err.message : '保存执行目标模板失败');
    }
  };

  const copyTemplate = async (template: OpsExecutionAdapterTemplate) => {
    try {
      await copyTemplateMutation.mutateAsync(template.adapterTemplateId);
      Toast.success('模板已复制');
    } catch (err) {
      Toast.error(err instanceof Error ? err.message : '复制模板失败');
    }
  };

  const toggleStatus = async (template: OpsExecutionAdapterTemplate) => {
    try {
      const nextStatus = String(template.status).toUpperCase() === 'ENABLED' ? 'DISABLED' : 'ENABLED';
      await toggleTemplateMutation.mutateAsync({
        templateId: template.adapterTemplateId,
        status: nextStatus,
      });
      Toast.success(nextStatus === 'ENABLED' ? '模板已启用' : '模板已停用');
    } catch (err) {
      Toast.error(err instanceof Error ? err.message : '更新模板状态失败');
    }
  };

  const viewTargets = async (template: OpsExecutionAdapterTemplate) => {
    try {
      await targetsMutation.mutateAsync(template.adapterTemplateId);
      setTargetTitle(`${template.templateName || template.adapterTemplateId} 的生成记录`);
      setTargetsVisible(true);
    } catch (err) {
      Toast.error(err instanceof Error ? err.message : '查询生成记录失败');
    }
  };

  const columns = [
    {
      title: '模板名称',
      dataIndex: 'templateName',
      render: (_: string, record: OpsExecutionAdapterTemplate) => (
        <Space vertical align="start" spacing={2}>
          <Text strong>{record.templateName || record.name || record.adapterTemplateId}</Text>
          <Text type="tertiary">{record.adapterTemplateId}</Text>
        </Space>
      ),
    },
    {
      title: '适配器类型',
      dataIndex: 'adapterType',
      width: 170,
      render: (value: string) => <Tag color="blue">{value}</Tag>,
    },
    {
      title: '支持动作',
      dataIndex: 'supportedActions',
      width: 260,
      render: (actions: string[]) => (
        <Space wrap>
          {(actions || []).slice(0, 3).map((action) => <Tag key={action}>{action}</Tag>)}
          {(actions || []).length > 3 && <Tag>+{actions.length - 3}</Tag>}
        </Space>
      ),
    },
    {
      title: '风险',
      dataIndex: 'riskLevel',
      width: 100,
      render: (risk: string) => <Tag color={riskColor(risk)}>{risk || '-'}</Tag>,
    },
    {
      title: '只读',
      dataIndex: 'readOnly',
      width: 90,
      render: (readOnly: boolean) => <Tag color={readOnly ? 'green' : 'red'}>{readOnly ? '是' : '否'}</Tag>,
    },
    {
      title: '生成目标',
      dataIndex: 'generatedTargetCount',
      width: 100,
      render: (count: number) => count || 0,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (status: string) => (
        <Tag color={String(status).toUpperCase() === 'ENABLED' ? 'green' : 'grey'}>{status}</Tag>
      ),
    },
    {
      title: '操作',
      key: 'action',
      width: 260,
      fixed: 'right' as const,
      render: (_: unknown, record: OpsExecutionAdapterTemplate) => (
        <Space>
          <Button size="small" onClick={() => viewTargets(record)}>生成记录</Button>
          <Button size="small" onClick={() => openEdit(record)}>编辑</Button>
          <Button size="small" onClick={() => copyTemplate(record)}>复制</Button>
          <Button size="small" onClick={() => toggleStatus(record)}>
            {String(record.status).toUpperCase() === 'ENABLED' ? '停用' : '启用'}
          </Button>
        </Space>
      ),
    },
  ];

  return (
    <OpsPageShell selectedKey="execution-adapter-templates">
      <OpsPageHeader
        title="执行目标模板"
        description="这里维护的是“执行目标蓝图”。真实可执行目标必须在项目里创建，绑定环境、资源范围和 credentialRef 后，才能进入执行中心审批。"
        primaryAction={<Button theme="solid" icon={<IconPlus />} onClick={openCreate}>新建模板</Button>}
        extra={(
          <Button icon={<IconRefresh />} onClick={() => templatesQuery.refetch()} loading={loading}>
            刷新
          </Button>
        )}
      />

      <OpsSectionCard title="执行链路边界">
        <OpsCapabilityFlow
          items={[
            { title: '执行目标模板', description: `${templates.length} 个模板，${enabledCount} 个启用`, status: 'current' },
            { title: '项目执行目标', description: '项目内真实资源、环境和 credentialRef', status: 'pending' },
            { title: '执行中心审批', description: 'Dry Run、审批和风险确认', status: 'pending' },
            { title: '受控落地执行', description: '按 approved snapshot 执行、验证、回滚和审计', status: 'pending' },
          ]}
        />
      </OpsSectionCard>

      <OpsSectionCard title="这个页面该怎么用">
        <Space vertical align="start" spacing="medium" style={{ width: '100%' }}>
          <Paragraph type="tertiary" style={{ margin: 0 }}>
            模板只回答“平台支持哪类动作”。例如 MySQL 受控执行模板描述了可创建索引、限量更新等动作边界；它不包含生产地址、账号或具体库表。
          </Paragraph>
          <Space wrap>
            <Tag color="blue">1. 新建或编辑模板</Tag>
            <Tag color="blue">2. 在项目空间生成执行目标</Tag>
            <Tag color="orange">3. Agent 生成 ChangePackage</Tag>
            <Tag color="red">4. 人工审批后 LandingRuntime 执行</Tag>
          </Space>
          <Text type="tertiary">
            “生成记录”展示哪些项目基于该模板创建过执行目标；不是立即执行按钮。真正的生产动作只会出现在执行中心审批后的 LandingRuntime。
          </Text>
        </Space>
      </OpsSectionCard>

      <OpsSectionCard title="模板列表">
        <Paragraph type="tertiary">
          模板只定义动作类型、默认约束和风险边界；Agent 不能绑定模板，只能绑定当前项目生成并授权的执行目标。
        </Paragraph>
        {error && <OpsEmptyState title="加载失败" description={error} />}
        <TableScroll>
          <Table
            rowKey="adapterTemplateId"
            dataSource={templates}
            columns={columns}
            loading={loading}
            pagination={false}
            scroll={{ x: 980 }}
            empty={<OpsEmptyState title="暂无执行目标模板" description="可以新建模板，或检查后端模板表是否已初始化。" />}
            expandedRowRender={(record?: OpsExecutionAdapterTemplate) => record ? (
              <OpsAdvancedPreview
                title="模板默认传输 / 执行配置"
                defaultOpen={false}
              >
                <JsonBlock>
                  {JSON.stringify({
                    defaultConfig: record.defaultConfig || {},
                    generatedTargets: record.generatedTargets || [],
                  }, null, 2)}
                </JsonBlock>
              </OpsAdvancedPreview>
            ) : null}
          />
        </TableScroll>
      </OpsSectionCard>

      <Modal
        title={editing ? '编辑执行目标模板' : '新建执行目标模板'}
        visible={formVisible}
        onCancel={() => setFormVisible(false)}
        onOk={submitTemplate}
        confirmLoading={saving}
        width={760}
      >
        <Space vertical align="start" style={{ width: '100%' }}>
          <Paragraph type="tertiary">
            模板只是“生成项目执行目标”的蓝图。真实资源地址、credentialRef、环境和项目权限必须在项目执行目标中填写；这里不要写明文密码或生产 Token。
          </Paragraph>
          <div style={{ width: '100%' }}>
            <Text strong>模板名称</Text>
            <Input
              value={form.templateName}
              onChange={(templateName) => setForm({ ...form, templateName })}
              placeholder="MySQL 受控执行模板"
              style={{ marginTop: 6 }}
            />
          </div>
          <div style={{ width: '100%' }}>
            <Text strong>适配器类型</Text>
            <Select
              value={form.adapterType}
              style={{ width: '100%', marginTop: 6 }}
              onChange={(adapterType) => {
                const nextType = String(adapterType);
                const preset = adapterActionPresets[nextType];
                setForm({
                  ...form,
                  adapterType: nextType,
                  supportedActions: preset?.actions.join(', ') || form.supportedActions,
                  riskLevel: preset?.riskLevel || form.riskLevel,
                  readOnly: preset?.readOnly ?? form.readOnly,
                });
              }}
            >
              {adapterOptions.map((option) => (
                <Option key={option.value} value={option.value}>{option.label}</Option>
              ))}
            </Select>
          </div>
          <div style={{ width: '100%' }}>
            <Text strong>风险等级</Text>
            <Select
              value={form.riskLevel}
              style={{ width: '100%', marginTop: 6 }}
              onChange={(riskLevel) => setForm({ ...form, riskLevel: String(riskLevel) })}
            >
              <Option value="LOW">LOW</Option>
              <Option value="MEDIUM">MEDIUM</Option>
              <Option value="HIGH">HIGH</Option>
              <Option value="CRITICAL">CRITICAL</Option>
            </Select>
          </div>
          <div style={{ width: '100%' }}>
            <Text strong>支持动作</Text>
            <Select
              multiple
              value={actionsFromText(form.supportedActions)}
              onChange={(value) => setForm({
                ...form,
                supportedActions: Array.isArray(value) ? value.join(', ') : String(value || ''),
              })}
              placeholder="选择这个模板允许生成的动作"
              style={{ width: '100%', marginTop: 6 }}
              renderSelectedItem={(optionNode: any) => optionNode.label}
            >
              {executionActionOptions.map((action) => (
                <Option key={action} value={action}>{action}</Option>
              ))}
            </Select>
            <Paragraph type="tertiary" style={{ marginTop: 6 }}>
              动作是平台内置枚举，不是随便填的字符串。切换适配器类型会自动带出推荐动作。
            </Paragraph>
          </div>
          <div style={{ width: '100%' }}>
            <Text strong>说明</Text>
            <Input
              value={form.description}
              onChange={(description) => setForm({ ...form, description })}
              placeholder="说明模板边界、适用资源和安全要求"
              style={{ marginTop: 6 }}
            />
          </div>
          <Space style={{ marginBottom: 12 }}>
            <Checkbox
              checked={form.readOnly}
              onChange={(event) => setForm({ ...form, readOnly: Boolean(event.target.checked) })}
            >
              只读模板
            </Checkbox>
            <Select
              value={form.status}
              style={{ width: 140 }}
              onChange={(status) => setForm({ ...form, status: String(status) })}
            >
              <Option value="ENABLED">启用</Option>
              <Option value="DISABLED">停用</Option>
            </Select>
          </Space>
          <OpsAdvancedPreview
            title="高级：模板默认配置"
            description="普通使用不需要改这里。它只作为项目生成执行目标时的初始值；credentialRef 是密钥引用，不是密钥明文。"
          >
            <TextArea
                value={form.defaultConfig}
                autosize={{ minRows: 8, maxRows: 16 }}
                style={{ fontFamily: 'monospace' }}
                onChange={(defaultConfig) => setForm({ ...form, defaultConfig })}
              />
          </OpsAdvancedPreview>
        </Space>
      </Modal>

      <Modal
        title={targetTitle}
        visible={targetsVisible}
        onCancel={() => setTargetsVisible(false)}
        footer={null}
        width={860}
      >
        <TableScroll>
          <Table
            rowKey="executionTargetId"
            dataSource={generatedTargets}
            pagination={false}
            scroll={{ x: 900 }}
            empty={<OpsEmptyState title="暂无生成记录" description="项目基于该模板生成执行目标后会出现在这里。" />}
            columns={[
              { title: '项目', dataIndex: 'projectId', width: 160 },
              { title: '执行目标', dataIndex: 'executionTargetId', width: 180 },
              { title: '名称', dataIndex: 'targetName', width: 180 },
              { title: '执行连接', dataIndex: 'workerId', width: 180 },
              { title: '状态', dataIndex: 'status', width: 100 },
              { title: '更新时间', dataIndex: 'updatedAt', width: 160 },
            ]}
          />
        </TableScroll>
      </Modal>
    </OpsPageShell>
  );
};
