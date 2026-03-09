import React, { useCallback, useEffect, useMemo, useState } from 'react';
import styled from 'styled-components';
import { Button, Input, Modal, Select, Space, Table, Tag, TextArea, Toast, Typography } from '@douyinfe/semi-ui';
import { IconPlus, IconRefresh } from '@douyinfe/semi-icons';

import {
  JsonBlock,
  OpsAdvancedPreview,
  OpsEmptyState,
  OpsPageHeader,
  OpsPageShell,
  OpsResponsiveGrid,
  OpsSectionCard,
  TableScroll,
} from '../components/ops-layout';
import { opsAdminService } from '../services/ops-admin-service';
import { theme } from '../styles/theme';

const { Option } = Select;
const { Text } = Typography;

const FilterBar = styled.div`
  display: flex;
  flex-wrap: wrap;
  gap: ${theme.spacing.base};
  align-items: center;
  margin-bottom: ${theme.spacing.base};

  .semi-select,
  .semi-input-wrapper {
    min-width: 180px;
  }
`;

const InlineCode = styled.code`
  padding: 2px 6px;
  border-radius: ${theme.borderRadius.sm};
  background: ${theme.colors.bg.secondary};
  color: ${theme.colors.text.secondary};
`;

const emptyForm = {
  scopeType: 'PROJECT',
  scopeId: '',
  memoryType: 'PROJECT_CONTEXT',
  title: '',
  summary: '',
  content: '',
  keywords: '',
  confidence: 0.8,
};

const userTypes = ['USER_PREFERENCE', 'USER_WORKFLOW', 'USER_DOMAIN_FOCUS'];
const projectTypes = ['PROJECT_CONTEXT', 'PROJECT_CONVENTION', 'PROJECT_GLOSSARY'];

const asText = (value: unknown, fallback = '-') => {
  if (value === undefined || value === null || value === '') return fallback;
  return String(value);
};

const timeText = (record: Record<string, any>) =>
  asText(record.updateTime || record.update_time || record.createTime || record.create_time);

export const MemoryManagementPage: React.FC = () => {
  const [loading, setLoading] = useState(false);
  const [memories, setMemories] = useState<Record<string, any>[]>([]);
  const [scopeType, setScopeType] = useState('PROJECT');
  const [scopeId, setScopeId] = useState('');
  const [memoryType, setMemoryType] = useState('');
  const [status, setStatus] = useState('ACTIVE');
  const [keyword, setKeyword] = useState('');
  const [selected, setSelected] = useState<Record<string, any> | null>(null);
  const [formVisible, setFormVisible] = useState(false);
  const [editing, setEditing] = useState<Record<string, any> | null>(null);
  const [form, setForm] = useState({ ...emptyForm });

  const availableTypes = useMemo(() => (form.scopeType === 'USER' ? userTypes : projectTypes), [form.scopeType]);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const response = await opsAdminService.listContextMemories({
        scopeType,
        scopeId,
        memoryType,
        status,
        keyword,
        limit: 100,
      });
      if (response.code !== '0000') {
        throw new Error(response.info || '查询记忆失败');
      }
      setMemories(response.data || []);
    } catch (error) {
      Toast.error(error instanceof Error ? error.message : '查询记忆失败');
    } finally {
      setLoading(false);
    }
  }, [keyword, memoryType, scopeId, scopeType, status]);

  useEffect(() => {
    load();
  }, [load]);

  const openCreate = () => {
    setEditing(null);
    setForm({ ...emptyForm, scopeType, scopeId, memoryType: scopeType === 'USER' ? 'USER_PREFERENCE' : 'PROJECT_CONTEXT' });
    setFormVisible(true);
  };

  const openEdit = (record: Record<string, any>) => {
    setEditing(record);
    setForm({
      scopeType: asText(record.scopeType || record.scope_type, 'PROJECT'),
      scopeId: asText(record.scopeId || record.scope_id, ''),
      memoryType: asText(record.memoryType || record.memory_type, 'PROJECT_CONTEXT'),
      title: asText(record.title, ''),
      summary: asText(record.summary, ''),
      content: asText(record.content, ''),
      keywords: asText(record.keywords, ''),
      confidence: Number(record.confidence ?? 0.8),
    });
    setFormVisible(true);
  };

  const submit = async () => {
    try {
      const payload = {
        ...form,
        confidence: Number(form.confidence || 0.8),
      };
      const response = editing
        ? await opsAdminService.updateContextMemory(asText(editing.memoryId || editing.memory_id), payload)
        : await opsAdminService.createContextMemory(payload);
      if (response.code !== '0000') {
        throw new Error(response.info || '保存记忆失败');
      }
      Toast.success(editing ? '记忆已更新' : '记忆已创建');
      setFormVisible(false);
      load();
    } catch (error) {
      Toast.error(error instanceof Error ? error.message : '保存记忆失败');
    }
  };

  const archive = async (record: Record<string, any>) => {
    try {
      const response = await opsAdminService.updateContextMemoryStatus(
        asText(record.memoryId || record.memory_id),
        'ARCHIVED',
      );
      if (response.code !== '0000') {
        throw new Error(response.info || '归档记忆失败');
      }
      Toast.success('记忆已归档');
      load();
    } catch (error) {
      Toast.error(error instanceof Error ? error.message : '归档记忆失败');
    }
  };

  const columns = [
    {
      title: '记忆',
      dataIndex: 'title',
      render: (_: string, record: Record<string, any>) => (
        <Space vertical align="start" spacing={2}>
          <Text strong>{asText(record.title)}</Text>
          <Text type="tertiary">{asText(record.memoryId || record.memory_id)}</Text>
        </Space>
      ),
    },
    {
      title: '范围',
      width: 160,
      render: (_: unknown, record: Record<string, any>) => (
        <Space vertical align="start" spacing={2}>
          <Tag color={asText(record.scopeType || record.scope_type) === 'USER' ? 'blue' : 'green'}>
            {asText(record.scopeType || record.scope_type)}
          </Tag>
          <Text type="tertiary">{asText(record.scopeId || record.scope_id)}</Text>
        </Space>
      ),
    },
    {
      title: '类型',
      dataIndex: 'memoryType',
      width: 190,
      render: (_: string, record: Record<string, any>) => <Tag>{asText(record.memoryType || record.memory_type)}</Tag>,
    },
    {
      title: '摘要',
      dataIndex: 'summary',
      render: (value: string) => <Text>{asText(value)}</Text>,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 110,
      render: (value: string) => <Tag color={value === 'ACTIVE' ? 'green' : 'grey'}>{asText(value)}</Tag>,
    },
    {
      title: '更新时间',
      width: 170,
      render: (_: unknown, record: Record<string, any>) => timeText(record),
    },
    {
      title: '操作',
      width: 190,
      render: (_: unknown, record: Record<string, any>) => (
        <Space>
          <Button size="small" theme="borderless" onClick={() => setSelected(record)}>查看</Button>
          <Button size="small" theme="borderless" onClick={() => openEdit(record)}>编辑</Button>
          <Button size="small" type="danger" theme="borderless" onClick={() => archive(record)}>归档</Button>
        </Space>
      ),
    },
  ];

  return (
    <OpsPageShell selectedKey="memory-management">
      <OpsPageHeader
        title="记忆管理"
        description="Memory 只保存用户和项目上下文，不保存排障 SOP、硬策略或工具执行细节。运维方法进入 Skill，正式知识进入 RAG，执行过程进入 Trace。"
        primaryAction={<Button theme="solid" icon={<IconPlus />} onClick={openCreate}>新增记忆</Button>}
        extra={<Button icon={<IconRefresh />} loading={loading} onClick={load}>刷新</Button>}
      />

      <OpsResponsiveGrid $min="220px">
        <OpsSectionCard title="Task Context">
          <Text type="tertiary">当前对话任务的目标、已知事实、待办动作由后端自动记录，可从对话详情或执行包详情进入查看。</Text>
          <Text type="tertiary">用于单次任务，不作为长期经验。</Text>
        </OpsSectionCard>
        <OpsSectionCard title="Context Memory">
          <Text type="tertiary">只允许 USER / PROJECT 六类强分类，无法归类的内容不入库。</Text>
          <Space wrap>
            {userTypes.concat(projectTypes).map((item) => <Tag key={item}>{item}</Tag>)}
          </Space>
        </OpsSectionCard>
      </OpsResponsiveGrid>

      <OpsSectionCard title="上下文记忆">
        <FilterBar>
          <Select value={scopeType} onChange={(value) => setScopeType(String(value))}>
            <Option value="PROJECT">PROJECT</Option>
            <Option value="USER">USER</Option>
          </Select>
          <Input placeholder="scopeId，可为空" value={scopeId} onChange={setScopeId} />
          <Select placeholder="memory type" value={memoryType} onChange={(value) => setMemoryType(String(value || ''))} style={{ minWidth: 220 }}>
            <Option value="">全部类型</Option>
            {(scopeType === 'USER' ? userTypes : projectTypes).map((item) => <Option value={item} key={item}>{item}</Option>)}
          </Select>
          <Select value={status} onChange={(value) => setStatus(String(value || ''))}>
            <Option value="">全部状态</Option>
            <Option value="ACTIVE">ACTIVE</Option>
            <Option value="ARCHIVED">ARCHIVED</Option>
          </Select>
          <Input placeholder="关键词" value={keyword} onChange={setKeyword} />
          <Button onClick={load} loading={loading}>查询</Button>
        </FilterBar>
        {memories.length === 0 && !loading ? (
          <OpsEmptyState title="暂无记忆" description="当前筛选条件下没有 Context Memory。可以等待模型抽取，也可以由管理员手工补充项目术语或用户偏好。" />
        ) : (
          <TableScroll>
            <Table columns={columns} dataSource={memories} loading={loading} rowKey={(record) => asText(record?.memoryId || record?.memory_id || record?.id)} pagination={{ pageSize: 10 }} />
          </TableScroll>
        )}
      </OpsSectionCard>

      <OpsAdvancedPreview title="选中记忆来源和原始字段" description="用于审计与排查，默认折叠。">
        {selected ? (
          <JsonBlock>{JSON.stringify(selected, null, 2)}</JsonBlock>
        ) : (
          <Text type="tertiary">点击列表中的 <InlineCode>查看</InlineCode> 后展示。</Text>
        )}
      </OpsAdvancedPreview>

      <Modal
        title={editing ? '编辑记忆' : '新增记忆'}
        visible={formVisible}
        onCancel={() => setFormVisible(false)}
        onOk={submit}
        okText="保存"
      >
        <Space vertical align="start" style={{ width: '100%' }}>
          <Select value={form.scopeType} onChange={(value) => {
            const nextScope = String(value);
            setForm((current) => ({
              ...current,
              scopeType: nextScope,
              memoryType: nextScope === 'USER' ? 'USER_PREFERENCE' : 'PROJECT_CONTEXT',
            }));
          }}>
            <Option value="PROJECT">PROJECT</Option>
            <Option value="USER">USER</Option>
          </Select>
          <Input placeholder="scopeId" value={form.scopeId} onChange={(value) => setForm((current) => ({ ...current, scopeId: value }))} />
          <Select value={form.memoryType} onChange={(value) => setForm((current) => ({ ...current, memoryType: String(value) }))}>
            {availableTypes.map((item) => <Option value={item} key={item}>{item}</Option>)}
          </Select>
          <Input placeholder="标题" value={form.title} onChange={(value) => setForm((current) => ({ ...current, title: value }))} />
          <Input placeholder="摘要" value={form.summary} onChange={(value) => setForm((current) => ({ ...current, summary: value }))} />
          <TextArea placeholder="内容" autosize rows={4} value={form.content} onChange={(value) => setForm((current) => ({ ...current, content: value }))} />
          <Input placeholder="关键词，逗号分隔" value={form.keywords} onChange={(value) => setForm((current) => ({ ...current, keywords: value }))} />
        </Space>
      </Modal>
    </OpsPageShell>
  );
};

export default MemoryManagementPage;
