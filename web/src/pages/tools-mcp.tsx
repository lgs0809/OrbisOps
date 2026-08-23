import React, { useMemo, useState } from 'react';
import { Button, Card, Checkbox, Input, Modal, Select, Space, Table, Tag, TextArea, Toast, Typography } from '@douyinfe/semi-ui';
import { IconCopy, IconEdit, IconPlus, IconRefresh, IconSearch } from '@douyinfe/semi-icons';
import styled from 'styled-components';

import { OpsPageHeader, OpsPageShell } from '../components/ops-layout';
import {
  useCopyMcpTemplateMutation,
  useMcpGeneratedToolsQuery,
  useMcpTemplatesQuery,
  useSaveMcpTemplateMutation,
  useToggleMcpTemplateMutation,
} from '../features/tools/api/mcp-template-queries';
import type { OpsMcpTemplate, OpsMcpTemplateRequest, OpsProjectTool } from '../services/ops-mcp-template-service';
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

type TemplateForm = {
  templateName: string;
  resourceType: string;
  transportType: string;
  supportedActions: string;
  riskLevel: string;
  readOnly: boolean;
  description: string;
  status: string;
  transportConfigJson: string;
};

const emptyForm = (): TemplateForm => ({
  templateName: '',
  resourceType: 'HTTP_API',
  transportType: 'HTTP',
  supportedActions: 'read',
  riskLevel: 'LOW',
  readOnly: true,
  description: '',
  status: 'ENABLED',
  transportConfigJson: '{}',
});

const formFrom = (template: OpsMcpTemplate): TemplateForm => ({
  templateName: template.templateName,
  resourceType: template.resourceType || 'HTTP_API',
  transportType: template.transportType || 'HTTP',
  supportedActions: (template.supportedActions || []).join(', '),
  riskLevel: template.riskLevel || 'LOW',
  readOnly: template.readOnly,
  description: template.description || '',
  status: template.status || 'ENABLED',
  transportConfigJson: JSON.stringify(template.defaultTransportConfig || {}, null, 2),
});

const requestFrom = (form: TemplateForm): OpsMcpTemplateRequest => {
  let defaultTransportConfig: Record<string, unknown> = {};
  try {
    defaultTransportConfig = JSON.parse(form.transportConfigJson || '{}');
  } catch {
    throw new Error('默认传输配置必须是合法 JSON。');
  }
  return {
    templateName: form.templateName.trim(),
    resourceType: form.resourceType,
    transportType: form.transportType,
    supportedActions: form.supportedActions.split(',').map((item) => item.trim()).filter(Boolean),
    riskLevel: form.riskLevel,
    readOnly: form.readOnly,
    description: form.description.trim(),
    status: form.status,
    defaultTransportConfig,
  };
};

const riskColor = (risk?: string) => risk === 'HIGH' ? 'red' : risk === 'MEDIUM' ? 'orange' : 'green';

export const ToolsMcpPage: React.FC = () => {
  const templatesQuery = useMcpTemplatesQuery();
  const saveMutation = useSaveMcpTemplateMutation();
  const copyMutation = useCopyMcpTemplateMutation();
  const toggleMutation = useToggleMcpTemplateMutation();
  const [keyword, setKeyword] = useState('');
  const [resourceType, setResourceType] = useState('');
  const [status, setStatus] = useState('');
  const [editing, setEditing] = useState<OpsMcpTemplate | null>(null);
  const [form, setForm] = useState<TemplateForm>(emptyForm());
  const [formVisible, setFormVisible] = useState(false);
  const [detail, setDetail] = useState<OpsMcpTemplate | null>(null);
  const generatedToolsQuery = useMcpGeneratedToolsQuery(detail?.templateId || '', Boolean(detail));

  const templates = templatesQuery.data || [];
  const filtered = useMemo(() => templates.filter((template) => {
    const query = keyword.trim().toLowerCase();
    const textMatch = !query || [template.templateId, template.templateName, template.description, template.resourceType]
      .filter(Boolean)
      .some((value) => String(value).toLowerCase().includes(query));
    return textMatch
      && (!resourceType || template.resourceType === resourceType)
      && (!status || template.status === status);
  }), [keyword, resourceType, status, templates]);

  const resourceTypes = useMemo(() => Array.from(new Set(templates.map((template) => template.resourceType).filter(Boolean))), [templates]);

  const openCreate = () => {
    setEditing(null);
    setForm(emptyForm());
    setFormVisible(true);
  };

  const openEdit = (template: OpsMcpTemplate) => {
    setEditing(template);
    setForm(formFrom(template));
    setFormVisible(true);
  };

  const save = async () => {
    if (!form.templateName.trim()) {
      Toast.warning('请输入模板名称。');
      return;
    }
    try {
      await saveMutation.mutateAsync({ templateId: editing?.templateId, request: requestFrom(form) });
      Toast.success(editing ? 'MCP 模板已更新。' : 'MCP 模板已创建。');
      setFormVisible(false);
    } catch (error) {
      Toast.error(userFacingError(error, '保存 MCP 模板失败，请稍后重试。'));
    }
  };

  const toggle = async (template: OpsMcpTemplate) => {
    const next = template.status === 'ENABLED' ? 'DISABLED' : 'ENABLED';
    try {
      await toggleMutation.mutateAsync({ templateId: template.templateId, status: next });
      Toast.success(next === 'ENABLED' ? '模板已启用。' : '模板已停用。');
    } catch (error) {
      Toast.error(userFacingError(error, '更新 MCP 模板状态失败，请稍后重试。'));
    }
  };

  const copy = async (template: OpsMcpTemplate) => {
    try {
      await copyMutation.mutateAsync(template.templateId);
      Toast.success('模板已复制。');
    } catch (error) {
      Toast.error(userFacingError(error, '复制 MCP 模板失败，请稍后重试。'));
    }
  };

  const columns = [
    {
      title: '模板',
      width: 260,
      render: (_: unknown, template: OpsMcpTemplate) => (
        <div>
          <Text strong>{template.templateName}</Text>
          <Text type="tertiary" size="small" style={{ display: 'block' }}>{template.templateId}</Text>
        </div>
      ),
    },
    { title: '资源类型', dataIndex: 'resourceType', width: 160 },
    { title: '传输方式', dataIndex: 'transportType', width: 130 },
    { title: '风险', dataIndex: 'riskLevel', width: 100, render: (value: string) => <Tag color={riskColor(value)}>{value || 'LOW'}</Tag> },
    { title: '权限边界', width: 120, render: (_: unknown, template: OpsMcpTemplate) => <Tag color={template.readOnly ? 'green' : 'orange'}>{template.readOnly ? '只读' : '可变更'}</Tag> },
    { title: '状态', dataIndex: 'status', width: 110, render: (value: string) => <Tag color={value === 'ENABLED' ? 'green' : 'grey'}>{value === 'ENABLED' ? '已启用' : '已停用'}</Tag> },
    {
      title: '操作',
      width: 320,
      render: (_: unknown, template: OpsMcpTemplate) => (
        <Space>
          <Button size="small" onClick={() => setDetail(template)}>查看</Button>
          <Button size="small" icon={<IconEdit />} onClick={() => openEdit(template)}>编辑</Button>
          <Button size="small" icon={<IconCopy />} loading={copyMutation.isPending} onClick={() => void copy(template)}>复制</Button>
          <Button size="small" type={template.status === 'ENABLED' ? 'danger' : 'primary'} theme="borderless" loading={toggleMutation.isPending} onClick={() => void toggle(template)}>
            {template.status === 'ENABLED' ? '停用' : '启用'}
          </Button>
        </Space>
      ),
    },
  ];

  return (
    <OpsPageShell selectedKey="settings">
      <OpsPageHeader
        title="工具 / MCP"
        description="在这里统一维护工具接入方式。接入模板本身不会自动开放给所有项目；项目完成资源绑定后，Chat 和工作流才能在自己的权限范围内使用对应工具。"
        extra={(
          <Space>
            <Button icon={<IconRefresh />} loading={templatesQuery.isFetching} onClick={() => void templatesQuery.refetch()}>刷新</Button>
            <Button type="primary" icon={<IconPlus />} onClick={openCreate}>新建模板</Button>
          </Space>
        )}
      />

      <Stack>
        <Card>
          <Title heading={6} style={{ marginTop: 0 }}>接入模板和项目可用工具是两回事</Title>
          <Paragraph type="tertiary" style={{ marginBottom: 0 }}>
            模板只负责保存一种可复用的接入方式。真正使用时，还需要由具体项目绑定实际资源、凭据和允许范围，项目里的 Chat 与工作流才会看到对应工具。
          </Paragraph>
        </Card>

        <Card>
          <Space wrap>
            <Input prefix={<IconSearch />} placeholder="搜索模板" value={keyword} onChange={setKeyword} style={{ width: 280 }} />
            <Select placeholder="资源类型" value={resourceType || undefined} showClear onChange={(value) => setResourceType(String(value || ''))} style={{ width: 180 }}>
              {resourceTypes.map((type) => <Option key={type} value={type}>{type}</Option>)}
            </Select>
            <Select placeholder="状态" value={status || undefined} showClear onChange={(value) => setStatus(String(value || ''))} style={{ width: 150 }}>
              <Option value="ENABLED">已启用</Option>
              <Option value="DISABLED">已停用</Option>
            </Select>
          </Space>
        </Card>

        <Card title="MCP 模板">
          <Table
            rowKey="templateId"
            columns={columns}
            dataSource={filtered}
            loading={templatesQuery.isLoading}
            pagination={false}
            scroll={{ x: 1250 }}
            empty={<Text type="tertiary">没有符合当前筛选条件的 MCP 模板。</Text>}
          />
        </Card>
      </Stack>

      <Modal
        title={editing ? '编辑 MCP 模板' : '新建 MCP 模板'}
        visible={formVisible}
        width={860}
        okText={editing ? '保存修改' : '创建模板'}
        cancelText="取消"
        confirmLoading={saveMutation.isPending}
        onOk={() => void save()}
        onCancel={() => setFormVisible(false)}
      >
        <FormGrid>
          <Field>
            <Text strong>模板名称</Text>
            <Input value={form.templateName} onChange={(templateName) => setForm({ ...form, templateName })} />
          </Field>
          <Field>
            <Text strong>资源类型</Text>
            <Input value={form.resourceType} onChange={(resourceTypeValue) => setForm({ ...form, resourceType: resourceTypeValue.toUpperCase() })} placeholder="MYSQL / HTTP_API / PROMETHEUS" />
          </Field>
          <Field>
            <Text strong>传输方式</Text>
            <Select value={form.transportType} onChange={(value) => setForm({ ...form, transportType: String(value) })}>
              <Option value="HTTP">HTTP</Option>
              <Option value="SSE">SSE</Option>
              <Option value="STDIO">STDIO</Option>
              <Option value="STREAMABLE_HTTP">Streamable HTTP</Option>
            </Select>
          </Field>
          <Field>
            <Text strong>风险等级</Text>
            <Select value={form.riskLevel} onChange={(value) => setForm({ ...form, riskLevel: String(value) })}>
              <Option value="LOW">低</Option>
              <Option value="MEDIUM">中</Option>
              <Option value="HIGH">高</Option>
            </Select>
          </Field>
          <WideField>
            <Text strong>支持动作</Text>
            <Input value={form.supportedActions} onChange={(supportedActions) => setForm({ ...form, supportedActions })} placeholder="read, query, inspect" />
            <Text type="tertiary" size="small">使用逗号分隔；Project 级策略还可以继续收窄可用动作。</Text>
          </WideField>
          <Field>
            <Text strong>状态</Text>
            <Select value={form.status} onChange={(value) => setForm({ ...form, status: String(value) })}>
              <Option value="ENABLED">启用</Option>
              <Option value="DISABLED">停用</Option>
            </Select>
          </Field>
          <Field>
            <Text strong>权限边界</Text>
            <Checkbox checked={form.readOnly} onChange={(event) => setForm({ ...form, readOnly: Boolean(event.target.checked) })}>默认只读</Checkbox>
          </Field>
          <WideField>
            <Text strong>说明</Text>
            <TextArea value={form.description} onChange={(description) => setForm({ ...form, description })} autosize={{ minRows: 3, maxRows: 6 }} />
          </WideField>
          <WideField>
            <Text strong>默认传输配置</Text>
            <TextArea value={form.transportConfigJson} onChange={(transportConfigJson) => setForm({ ...form, transportConfigJson })} autosize={{ minRows: 5, maxRows: 10 }} />
            <Text type="tertiary" size="small">仅用于高级默认值。真实凭据应使用部署环境或 Project 凭据引用，不要写入这里的 JSON。</Text>
          </WideField>
        </FormGrid>
      </Modal>

      <Modal
        title={detail ? `MCP 模板详情 · ${detail.templateName}` : 'MCP 模板详情'}
        visible={Boolean(detail)}
        width={900}
        footer={<Button onClick={() => setDetail(null)}>关闭</Button>}
        onCancel={() => setDetail(null)}
      >
        {detail && (
          <Stack>
            <Space wrap>
              <Tag color="blue">{detail.resourceType}</Tag>
              <Tag>{detail.transportType}</Tag>
              <Tag color={riskColor(detail.riskLevel)}>{detail.riskLevel}</Tag>
              <Tag color={detail.readOnly ? 'green' : 'orange'}>{detail.readOnly ? '只读' : '可变更'}</Tag>
              <Tag color={detail.status === 'ENABLED' ? 'green' : 'grey'}>{detail.status}</Tag>
            </Space>
            <Paragraph>{detail.description || '暂无说明。'}</Paragraph>
            <Card title="支持动作"><Space wrap>{(detail.supportedActions || []).map((action) => <Tag key={action}>{action}</Tag>)}</Space></Card>
            <Card title="已生成的 Project 工具">
              <Table
                rowKey={(record?: OpsProjectTool) => record?.toolId || record?.mcpId || ''}
                dataSource={generatedToolsQuery.data || detail.generatedTools || []}
                loading={generatedToolsQuery.isFetching}
                pagination={false}
                empty={<Text type="tertiary">尚未基于该模板生成 Project 工具。</Text>}
                columns={[
                  { title: 'Project', dataIndex: 'projectId', width: 160 },
                  { title: '工具 ID', width: 200, render: (_: unknown, record: OpsProjectTool) => record.toolId || record.mcpId },
                  { title: '工具名称', width: 220, render: (_: unknown, record: OpsProjectTool) => record.toolName || record.mcpName },
                  { title: '资源', dataIndex: 'resourceId', width: 180 },
                  { title: '状态', dataIndex: 'status', width: 110 },
                ]}
              />
            </Card>
          </Stack>
        )}
      </Modal>
    </OpsPageShell>
  );
};
