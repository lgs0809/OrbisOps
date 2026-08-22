import React, { useMemo, useState } from 'react';
import {
  Button,
  Card,
  Input,
  Modal,
  Select,
  Space,
  Table,
  Tag,
  Toast,
  Typography,
} from '@douyinfe/semi-ui';
import {
  IconCopy,
  IconPlus,
  IconRefresh,
  IconSearch,
} from '@douyinfe/semi-icons';
import styled from 'styled-components';
import { OpsPageHeader, OpsPageShell, TableScroll } from '../../components/ops-layout';
import type { OpsMcpTemplate, OpsProjectTool } from '../../services/ops-mcp-template-service';
import {
  useCopyMcpTemplateMutation,
  useMcpGeneratedToolsQuery,
  useMcpTemplatesQuery,
  useSaveMcpTemplateMutation,
  useToggleMcpTemplateMutation,
} from '../../features/tools/api/mcp-template-queries';
import { theme } from '../../styles/theme';
import { McpTemplateBoundaryNote } from './McpTemplateBoundaryNote';
import { McpTemplateFormModal } from './McpTemplateFormModal';
import { McpTemplateTable } from './McpTemplateTable';
import {
  formFromTemplate,
  requestFromForm,
  riskColor,
} from './helpers';
import {
  emptyMcpTemplateForm,
  mcpResourcePresets,
  McpTemplateFormState,
} from './types';

const { Option } = Select;

const PageContainer = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${theme.spacing.base};
  min-width: 0;
  width: 100%;
`;

const SearchCard = styled(Card)`
  min-width: 0;

  .semi-card-body {
    padding: ${theme.spacing.lg};
  }
`;

const SearchRow = styled.div`
  display: flex;
  align-items: center;
  gap: ${theme.spacing.base};
  flex-wrap: wrap;
  min-width: 0;
`;

const TableCard = styled(Card)`
  min-width: 0;

  .semi-card-body {
    padding: 0;
  }
`;

export const McpToolManagement: React.FC = () => {
  const templatesQuery = useMcpTemplatesQuery();
  const saveTemplateMutation = useSaveMcpTemplateMutation();
  const copyTemplateMutation = useCopyMcpTemplateMutation();
  const toggleTemplateMutation = useToggleMcpTemplateMutation();
  const templates = templatesQuery.data || [];
  const [searchText, setSearchText] = useState('');
  const [resourceType, setResourceType] = useState('');
  const [status, setStatus] = useState('');
  const [modalMode, setModalMode] = useState<'create' | 'edit' | null>(null);
  const [form, setForm] = useState<McpTemplateFormState>(emptyMcpTemplateForm);
  const [selected, setSelected] = useState<OpsMcpTemplate | null>(null);
  const [detailVisible, setDetailVisible] = useState(false);
  const generatedToolsQuery = useMcpGeneratedToolsQuery(selected?.templateId || '', detailVisible && Boolean(selected));

  const filteredTemplates = useMemo(() => templates.filter((item) => {
    const keyword = searchText.trim().toLowerCase();
    const textMatched = !keyword
      || item.templateId.toLowerCase().includes(keyword)
      || item.templateName.toLowerCase().includes(keyword)
      || (item.description || '').toLowerCase().includes(keyword);
    const typeMatched = !resourceType || item.resourceType === resourceType;
    const statusMatched = !status || item.status === status;
    return textMatched && typeMatched && statusMatched;
  }), [templates, searchText, resourceType, status]);

  const openCreate = () => {
    setForm(emptyMcpTemplateForm);
    setModalMode('create');
  };

  const openEdit = (template: OpsMcpTemplate) => {
    setForm(formFromTemplate(template));
    setModalMode('edit');
  };

  const openDetail = (template: OpsMcpTemplate) => {
    setSelected(template);
    setDetailVisible(true);
  };

  const submitForm = async () => {
    if (!form.templateName.trim()) {
      Toast.error('Template name is required.');
      return;
    }
    try {
      const request = requestFromForm(form);
      await saveTemplateMutation.mutateAsync({
        templateId: modalMode === 'edit' ? form.templateId : undefined,
        request,
      });
      Toast.success(modalMode === 'create' ? '模板已创建' : '模板已更新');
      setModalMode(null);
    } catch (error) {
      Toast.error(error instanceof Error ? error.message : '保存 MCP 接入模板失败');
    }
  };

  const copySelected = async () => {
    if (!selected) return;
    try {
      await copyTemplateMutation.mutateAsync(selected.templateId);
      Toast.success('模板已复制');
      setDetailVisible(false);
    } catch (error) {
      Toast.error(error instanceof Error ? error.message : '复制 MCP 接入模板失败');
    }
  };

  const toggleSelectedStatus = async () => {
    if (!selected) return;
    const nextStatus = selected.status === 'ENABLED' ? 'DISABLED' : 'ENABLED';
    try {
      await toggleTemplateMutation.mutateAsync({ templateId: selected.templateId, status: nextStatus });
      Toast.success(nextStatus === 'ENABLED' ? '模板已启用' : '模板已停用');
      setDetailVisible(false);
    } catch (error) {
      Toast.error(error instanceof Error ? error.message : '更新 MCP 接入模板状态失败');
    }
  };

  return (
    <OpsPageShell selectedKey="mcp-tool-management">
      <PageContainer>
        <OpsPageHeader
          title="MCP 接入模板"
          description="这里维护的是可复用模板，不能被 Agent 直接调用。项目需要基于模板 + 数据连接 + 权限策略生成项目工具。"
          primaryAction={(
            <Button type="primary" theme="solid" icon={<IconPlus />} onClick={openCreate}>
              新建模板
            </Button>
          )}
        />

        <McpTemplateBoundaryNote />

        <SearchCard>
          <SearchRow>
            <Input
              placeholder="搜索模板名称 / 资源类型 / 描述"
              value={searchText}
              onChange={setSearchText}
              style={{ width: 260 }}
              prefix={<IconSearch />}
            />
            <Select placeholder="资源类型" value={resourceType} onChange={(value) => setResourceType(String(value || ''))} style={{ width: 160 }}>
              <Option value="">全部</Option>
              {Object.entries(mcpResourcePresets).map(([value, preset]) => (
                <Option key={value} value={value}>{preset.label}</Option>
              ))}
            </Select>
            <Select placeholder="状态" value={status} onChange={(value) => setStatus(String(value || ''))} style={{ width: 130 }}>
              <Option value="">全部</Option>
              <Option value="ENABLED">启用</Option>
              <Option value="DISABLED">停用</Option>
            </Select>
            <Button icon={<IconRefresh />} onClick={() => templatesQuery.refetch()} loading={templatesQuery.isFetching}>
              刷新
            </Button>
          </SearchRow>
        </SearchCard>

        <TableCard>
          <McpTemplateTable
            templates={filteredTemplates}
            loading={templatesQuery.isLoading}
            onView={openDetail}
            onEdit={openEdit}
          />
        </TableCard>

        <McpTemplateFormModal
          mode={modalMode}
          form={form}
          submitting={saveTemplateMutation.isPending}
          onChange={(patch) => setForm((current) => ({ ...current, ...patch }))}
          onCancel={() => setModalMode(null)}
          onSubmit={submitForm}
        />

        <Modal
          title={`MCP 接入模板详情${selected ? ` - ${selected.templateName}` : ''}`}
          visible={detailVisible}
          onCancel={() => setDetailVisible(false)}
          width={860}
          style={{ maxWidth: '94vw' }}
          footer={(
            <Space>
              <Button onClick={() => setDetailVisible(false)}>关闭</Button>
              <Button icon={<IconCopy />} loading={copyTemplateMutation.isPending} onClick={copySelected}>复制模板</Button>
              <Button type={selected?.status === 'ENABLED' ? 'danger' : 'primary'} loading={toggleTemplateMutation.isPending} onClick={toggleSelectedStatus}>
                {selected?.status === 'ENABLED' ? '停用模板' : '启用模板'}
              </Button>
            </Space>
          )}
        >
          {selected && (
            <Space vertical align="start" spacing="medium" style={{ width: '100%' }}>
              <Space wrap>
                <Tag color="blue">{selected.resourceType}</Tag>
                <Tag color={riskColor(selected.riskLevel)}>{selected.riskLevel}</Tag>
                <Tag color={selected.readOnly ? 'green' : 'orange'}>{selected.readOnly ? '只读' : '非只读'}</Tag>
                <Tag color={selected.status === 'ENABLED' ? 'green' : 'grey'}>{selected.status}</Tag>
              </Space>
              <Typography.Paragraph>{selected.description || '暂无描述'}</Typography.Paragraph>
              <Card title="接入说明" style={{ width: '100%' }}>
                <Space vertical align="start" spacing="tight" style={{ width: '100%' }}>
                  <Typography.Text>
                    {mcpResourcePresets[selected.resourceType]?.label || selected.resourceType || '自定义组件'}
                    {' '}模板只定义“可以接入什么类型的工具”和“默认风险边界”。具体连接信息、凭据引用和可见资源需要在项目资源绑定里配置。
                  </Typography.Text>
                  <Typography.Text type="secondary">
                    系统会根据资源类型生成内部接入配置；普通用户不需要填写 serverTemplate、toolId 或 JSON。
                  </Typography.Text>
                  <Space wrap>
                    {(selected.supportedActions || []).map((action) => <Tag key={action}>{action}</Tag>)}
                  </Space>
                </Space>
              </Card>
              <Card title="生成记录" style={{ width: '100%' }}>
                <TableScroll>
                  <Table
                    size="small"
                    loading={generatedToolsQuery.isLoading}
                    rowKey="toolId"
                    dataSource={generatedToolsQuery.data || selected.generatedTools || []}
                    pagination={false}
                    columns={[
                      { title: '项目', dataIndex: 'projectId', width: 120 },
                      { title: '工具 ID', dataIndex: 'toolId', width: 180, render: (_: string, record: OpsProjectTool) => record.toolId || record.mcpId },
                      { title: '工具名称', dataIndex: 'toolName', width: 180, render: (_: string, record: OpsProjectTool) => record.toolName || record.mcpName },
                      { title: '资源', dataIndex: 'resourceId', width: 160 },
                      { title: '状态', dataIndex: 'status', width: 90 },
                    ]}
                    empty={<Typography.Text type="tertiary">暂无项目生成工具</Typography.Text>}
                  />
                </TableScroll>
              </Card>
            </Space>
          )}
        </Modal>
      </PageContainer>
    </OpsPageShell>
  );
};
