import React, { useMemo, useState } from 'react';
import { Button, Card, Checkbox, Input, Modal, Select, Space, Table, Tag, TextArea, Toast, Typography } from '@douyinfe/semi-ui';
import { IconCopy, IconEdit, IconPlus, IconRefresh } from '@douyinfe/semi-icons';
import styled from 'styled-components';

import { OpsPageHeader, OpsPageShell } from '../components/ops-layout';
import {
  useCopyExecutionAdapterTemplateMutation,
  useExecutionAdapterTemplateTargetsMutation,
  useExecutionAdapterTemplatesQuery,
  useSaveExecutionAdapterTemplateMutation,
  useToggleExecutionAdapterTemplateMutation,
} from '../features/execution-targets/api/execution-target-queries';
import type { OpsExecutionAdapterTemplate } from '../services/ops-repair-service';
import { theme } from '../styles/theme';
import { generatedId } from '../utils/generated-id';
import { userFacingError } from '../utils/user-facing-error';

const { Paragraph, Text, Title } = Typography;
const { Option } = Select;

const Stack = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${theme.spacing.base};
  min-width: 0;
`;

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

type FormState = {
  adapterTemplateId: string;
  templateName: string;
  adapterType: string;
  supportedActions: string;
  riskLevel: string;
  readOnly: boolean;
  description: string;
  status: string;
  defaultConfigJson: string;
};

const emptyForm = (): FormState => ({
  adapterTemplateId: '',
  templateName: '',
  adapterType: 'deployment-http',
  supportedActions: 'DEPLOYMENT_TRIGGER, DEPLOYMENT_STATUS, DEPLOYMENT_ROLLBACK',
  riskLevel: 'HIGH',
  readOnly: false,
  description: '',
  status: 'ENABLED',
  defaultConfigJson: JSON.stringify({
    credentialRef: 'ops/<project>/<resource>',
    timeoutMs: 30000,
    preconditionReadSupported: true,
    postCheckReadSupported: true,
    rollbackSupported: false,
  }, null, 2),
});

const formFrom = (template: OpsExecutionAdapterTemplate): FormState => ({
  adapterTemplateId: template.adapterTemplateId,
  templateName: template.templateName || template.name || template.adapterTemplateId,
  adapterType: String(template.adapterType || 'deployment-http'),
  supportedActions: (template.supportedActions || []).join(', '),
  riskLevel: String(template.riskLevel || 'HIGH'),
  readOnly: Boolean(template.readOnly),
  description: template.description || '',
  status: String(template.status || 'ENABLED'),
  defaultConfigJson: JSON.stringify(template.defaultConfig || {}, null, 2),
});

const riskColor = (risk?: string) => risk === 'CRITICAL' ? 'red' : risk === 'HIGH' ? 'orange' : risk === 'MEDIUM' ? 'yellow' : 'green';

export const ExecutionTargetsPage: React.FC = () => {
  const templatesQuery = useExecutionAdapterTemplatesQuery();
  const saveMutation = useSaveExecutionAdapterTemplateMutation();
  const copyMutation = useCopyExecutionAdapterTemplateMutation();
  const toggleMutation = useToggleExecutionAdapterTemplateMutation();
  const targetsMutation = useExecutionAdapterTemplateTargetsMutation();
  const [editing, setEditing] = useState<OpsExecutionAdapterTemplate | null>(null);
  const [form, setForm] = useState<FormState>(emptyForm());
  const [formVisible, setFormVisible] = useState(false);
  const [targetsVisible, setTargetsVisible] = useState(false);
  const [targetsTitle, setTargetsTitle] = useState('已生成执行目标');

  const templates = templatesQuery.data || [];
  const enabledCount = useMemo(() => templates.filter((item) => String(item.status).toUpperCase() === 'ENABLED').length, [templates]);

  const openCreate = () => {
    setEditing(null);
    setForm(emptyForm());
    setFormVisible(true);
  };

  const openEdit = (template: OpsExecutionAdapterTemplate) => {
    setEditing(template);
    setForm(formFrom(template));
    setFormVisible(true);
  };

  const save = async () => {
    if (!form.templateName.trim()) {
      Toast.warning('请输入模板名称。');
      return;
    }
    let defaultConfig: Record<string, unknown>;
    try {
      defaultConfig = JSON.parse(form.defaultConfigJson || '{}');
    } catch {
      Toast.error('默认配置必须是合法 JSON。');
      return;
    }
    const payload: Partial<OpsExecutionAdapterTemplate> = {
      adapterTemplateId: form.adapterTemplateId.trim() || generatedId('adapter-template', form.templateName),
      templateName: form.templateName.trim(),
      adapterType: form.adapterType,
      supportedActions: form.supportedActions.split(',').map((item) => item.trim()).filter(Boolean),
      defaultConfig,
      riskLevel: form.riskLevel,
      readOnly: form.readOnly,
      description: form.description.trim(),
      status: form.status,
    };
    try {
      await saveMutation.mutateAsync({ editingId: editing?.adapterTemplateId, payload });
      Toast.success(editing ? '执行目标模板已更新。' : '执行目标模板已创建。');
      setFormVisible(false);
    } catch (error) {
      Toast.error(userFacingError(error, '保存执行目标模板失败，请稍后重试。'));
    }
  };

  const toggle = async (template: OpsExecutionAdapterTemplate) => {
    const next = String(template.status).toUpperCase() === 'ENABLED' ? 'DISABLED' : 'ENABLED';
    try {
      await toggleMutation.mutateAsync({ templateId: template.adapterTemplateId, status: next });
      Toast.success(next === 'ENABLED' ? '模板已启用。' : '模板已停用。');
    } catch (error) {
      Toast.error(userFacingError(error, '更新模板状态失败，请稍后重试。'));
    }
  };

  const copy = async (template: OpsExecutionAdapterTemplate) => {
    try {
      await copyMutation.mutateAsync(template.adapterTemplateId);
      Toast.success('模板已复制。');
    } catch (error) {
      Toast.error(userFacingError(error, '复制模板失败，请稍后重试。'));
    }
  };

  const openTargets = async (template: OpsExecutionAdapterTemplate) => {
    try {
      await targetsMutation.mutateAsync(template.adapterTemplateId);
      setTargetsTitle(`${template.templateName || template.adapterTemplateId} · 已生成执行目标`);
      setTargetsVisible(true);
    } catch (error) {
      Toast.error(userFacingError(error, '加载已生成执行目标失败，请稍后重试。'));
    }
  };

  const columns = [
    {
      title: '模板',
      width: 250,
      render: (_: unknown, template: OpsExecutionAdapterTemplate) => (
        <div>
          <Text strong>{template.templateName || template.name || template.adapterTemplateId}</Text>
          <Text type="tertiary" size="small" style={{ display: 'block' }}>{template.adapterTemplateId}</Text>
        </div>
      ),
    },
    { title: '适配器类型', dataIndex: 'adapterType', width: 170 },
    { title: '支持动作', width: 260, render: (_: unknown, template: OpsExecutionAdapterTemplate) => <Space wrap>{(template.supportedActions || []).slice(0, 3).map((action) => <Tag key={action}>{action}</Tag>)}</Space> },
    { title: '风险', dataIndex: 'riskLevel', width: 100, render: (value: string) => <Tag color={riskColor(value)}>{value}</Tag> },
    { title: '权限边界', width: 110, render: (_: unknown, template: OpsExecutionAdapterTemplate) => <Tag color={template.readOnly ? 'green' : 'orange'}>{template.readOnly ? '只读' : '可变更'}</Tag> },
    { title: '已生成', dataIndex: 'generatedTargetCount', width: 100, render: (value: number) => value || 0 },
    { title: '状态', dataIndex: 'status', width: 110, render: (value: string) => <Tag color={String(value).toUpperCase() === 'ENABLED' ? 'green' : 'grey'}>{String(value).toUpperCase() === 'ENABLED' ? '已启用' : '已停用'}</Tag> },
    {
      title: '操作',
      width: 340,
      render: (_: unknown, template: OpsExecutionAdapterTemplate) => (
        <Space>
          <Button size="small" onClick={() => void openTargets(template)}>生成记录</Button>
          <Button size="small" icon={<IconEdit />} onClick={() => openEdit(template)}>编辑</Button>
          <Button size="small" icon={<IconCopy />} onClick={() => void copy(template)}>复制</Button>
          <Button size="small" type={String(template.status).toUpperCase() === 'ENABLED' ? 'danger' : 'primary'} theme="borderless" onClick={() => void toggle(template)}>
            {String(template.status).toUpperCase() === 'ENABLED' ? '停用' : '启用'}
          </Button>
        </Space>
      ),
    },
  ];

  return (
    <OpsPageShell selectedKey="settings">
      <OpsPageHeader
        title="执行目标"
        description="维护平台支持的受控执行方式。真正的执行目标由具体项目绑定环境和资源范围；涉及生产变更时，仍必须经过变更包、审批、执行验证和回滚保护。"
        extra={(
          <Space>
            <Button icon={<IconRefresh />} loading={templatesQuery.isFetching} onClick={() => void templatesQuery.refetch()}>刷新</Button>
            <Button type="primary" icon={<IconPlus />} onClick={openCreate}>新建模板</Button>
          </Space>
        )}
      />

      <Stack>
        <Card>
          <Title heading={6} style={{ marginTop: 0 }}>执行边界</Title>
          <Paragraph type="tertiary" style={{ marginBottom: 8 }}>
            模板只回答“平台支持哪类受控动作”，不包含生产地址、真实凭据、具体库表范围，也不代表已经获得审批权限。
          </Paragraph>
          <Space wrap>
            <Tag color="blue">模板 {templates.length}</Tag>
            <Tag color="green">已启用 {enabledCount}</Tag>
            <Tag>项目执行目标</Tag>
            <Tag color="orange">变更包</Tag>
            <Tag color="red">审批 → 执行 → 验证</Tag>
          </Space>
        </Card>

        <Card title="执行目标模板">
          <Table
            rowKey="adapterTemplateId"
            dataSource={templates}
            columns={columns}
            loading={templatesQuery.isLoading}
            pagination={false}
            scroll={{ x: 1380 }}
            empty={<Text type="tertiary">暂无执行目标模板。</Text>}
          />
        </Card>
      </Stack>

      <Modal
        title={editing ? '编辑执行目标模板' : '新建执行目标模板'}
        visible={formVisible}
        width={860}
        okText={editing ? '保存修改' : '创建模板'}
        cancelText="取消"
        confirmLoading={saveMutation.isPending}
        onOk={() => void save()}
        onCancel={() => setFormVisible(false)}
      >
        <Paragraph type="tertiary">
          不要在模板中填写真实地址和凭据。具体项目创建执行目标时，再绑定环境、资源范围和凭据引用。
        </Paragraph>
        <FormGrid>
          <Field>
            <Text strong>模板名称</Text>
            <Input value={form.templateName} onChange={(templateName) => setForm({ ...form, templateName })} />
          </Field>
          <Field>
            <Text strong>适配器类型</Text>
            <Select value={form.adapterType} onChange={(value) => setForm({ ...form, adapterType: String(value) })}>
              <Option value="local-java-service">本地 Java 服务部署</Option>
              <Option value="deployment-http">部署平台 HTTP API</Option>
              <Option value="mysql-controlled">MySQL 受控执行</Option>
              <Option value="redis-controlled">Redis 受控执行</Option>
              <Option value="rabbitmq-policy">RabbitMQ Policy 受控执行</Option>
            </Select>
          </Field>
          <Field>
            <Text strong>风险等级</Text>
            <Select value={form.riskLevel} onChange={(value) => setForm({ ...form, riskLevel: String(value) })}>
              <Option value="LOW">低</Option>
              <Option value="MEDIUM">中</Option>
              <Option value="HIGH">高</Option>
              <Option value="CRITICAL">严重</Option>
            </Select>
          </Field>
          <Field>
            <Text strong>状态</Text>
            <Select value={form.status} onChange={(value) => setForm({ ...form, status: String(value) })}>
              <Option value="ENABLED">启用</Option>
              <Option value="DISABLED">停用</Option>
            </Select>
          </Field>
          <WideField>
            <Text strong>支持动作</Text>
            <Input value={form.supportedActions} onChange={(supportedActions) => setForm({ ...form, supportedActions })} />
            <Text type="tertiary" size="small">使用逗号分隔受治理动作标识；Project 策略还可以继续收窄。</Text>
          </WideField>
          <Field>
            <Text strong>权限边界</Text>
            <Checkbox checked={form.readOnly} onChange={(event) => setForm({ ...form, readOnly: Boolean(event.target.checked) })}>只读模板</Checkbox>
          </Field>
          <WideField>
            <Text strong>说明</Text>
            <Input value={form.description} onChange={(description) => setForm({ ...form, description })} />
          </WideField>
          <WideField>
            <Text strong>默认配置</Text>
            <TextArea value={form.defaultConfigJson} onChange={(defaultConfigJson) => setForm({ ...form, defaultConfigJson })} autosize={{ minRows: 7, maxRows: 14 }} />
            <Text type="tertiary" size="small">仅用于高级默认值。credentialRef 是凭据引用，不是明文凭据。</Text>
          </WideField>
        </FormGrid>
      </Modal>

      <Modal title={targetsTitle} visible={targetsVisible} width={900} footer={<Button onClick={() => setTargetsVisible(false)}>关闭</Button>} onCancel={() => setTargetsVisible(false)}>
        <Table
          rowKey="executionTargetId"
          dataSource={targetsMutation.data || []}
          pagination={false}
          empty={<Text type="tertiary">尚未基于该模板生成 Project 执行目标。</Text>}
          columns={[
            { title: 'Project', dataIndex: 'projectId', width: 160 },
            { title: '执行目标', dataIndex: 'executionTargetId', width: 190 },
            { title: '名称', dataIndex: 'targetName', width: 190 },
            { title: 'Worker', dataIndex: 'workerId', width: 180 },
            { title: '状态', dataIndex: 'status', width: 110 },
            { title: '更新时间', dataIndex: 'updatedAt', width: 170 },
          ]}
        />
      </Modal>
    </OpsPageShell>
  );
};
