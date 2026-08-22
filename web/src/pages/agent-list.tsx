import React, { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Table,
  Button,
  Input,
  Space,
  Typography,
  Toast,
  Popconfirm,
  Card,
  Modal,
  Select,
  Tag,
} from '@douyinfe/semi-ui';
import {
  IconSearch,
  IconPlus,
  IconEdit,
  IconDelete,
  IconCopy,
} from '@douyinfe/semi-icons';
import styled from 'styled-components';
import { theme } from '../styles/theme';
import { OpsPageHeader, OpsPageShell } from '../components/ops-layout';
import type { OpsAgentDefinition } from '../services/ops-admin-service';
import {
  useAgentListQuery,
  useCloneAgentMutation,
  useDisableAgentMutation,
} from '../features/agents/api/agent-list-queries';
import { ProjectScopeBar } from '../components/project-scope-bar';
import { useProjectScope } from '../hooks/use-project-scope';
import { generatedId } from '../utils/generated-id';
import { userFacingError } from '../utils/user-facing-error';

const displayAgentName = (agent: OpsAgentDefinition) => {
  return agent.name || agent.agentId;
};

const displayAgentDescription = (agent: OpsAgentDefinition) => {
  return agent.description || '-';
};

const SearchSection = styled(Card)`
  box-sizing: border-box;
  margin-bottom: ${theme.spacing.lg};
  width: 100%;
  max-width: 100%;
  margin-left: auto;
  margin-right: auto;
  min-width: 0;

  .semi-card-body {
    box-sizing: border-box;
    width: 100%;
    min-width: 0;
    padding: ${theme.spacing.lg};
  }
`;

const SearchRow = styled.div`
  display: flex;
  width: 100%;
  min-width: 0;
  align-items: center;
  gap: ${theme.spacing.base};
  flex-wrap: wrap;

  @media (max-width: ${theme.breakpoints.sm}) {
    .semi-input-wrapper {
      width: 100% !important;
      max-width: 100%;
    }
  }
`;

const TableCard = styled(Card)`
  box-sizing: border-box;
  width: 100%;
  max-width: 100%;
  margin-left: auto;
  margin-right: auto;
  min-width: 0;

  .semi-card-body {
    padding: 0;
    overflow-x: auto;
  }

  .semi-table-container,
  .semi-table-body {
    overflow-x: auto;
  }
`;

const ActionButton = styled(Button)`
  margin-right: 0;
`;

export const AgentListPage: React.FC = () => {
  const navigate = useNavigate();
  const projectScope = useProjectScope();
  const [searchParams, setSearchParams] = useState<{
    configName?: string;
    pageNum?: number;
    pageSize?: number;
  }>({
    pageNum: 1,
    pageSize: 10,
  });
  const listQuery = useAgentListQuery(projectScope.projectId);
  const cloneMutation = useCloneAgentMutation();
  const disableMutation = useDisableAgentMutation(projectScope.projectId);
  const referenceImpacts = listQuery.data?.referenceImpacts || {};
  const filteredWorkflows = useMemo(() => {
    const keyword = (searchParams.configName || '').trim().toLowerCase();
    return (listQuery.data?.workflows || []).filter((agent) => (
      !keyword
      || displayAgentName(agent).toLowerCase().includes(keyword)
      || displayAgentDescription(agent).toLowerCase().includes(keyword)
    ));
  }, [listQuery.data?.workflows, searchParams.configName]);
  const pageNum = searchParams.pageNum || 1;
  const pageSize = searchParams.pageSize || 10;
  const dataSource = filteredWorkflows.slice((pageNum - 1) * pageSize, pageNum * pageSize);
  const total = filteredWorkflows.length;
  const loading = listQuery.isLoading || cloneMutation.isPending || disableMutation.isPending;
  const [cloneSource, setCloneSource] = useState<OpsAgentDefinition | null>(null);
  const [cloneForm, setCloneForm] = useState({ projectId: '', agentId: '', name: '' });

  const columns = [
    {
      title: '名称',
      dataIndex: 'name',
      key: 'name',
      width: 150,
      render: (text: string, record: OpsAgentDefinition) => (
        <span style={{ fontWeight: 500 }}>{displayAgentName(record)}</span>
      )
    },
    {
      title: '调用方式',
      key: 'invocation',
      width: 120,
      render: () => <Tag color="grey">显式选择</Tag>,
    },
    {
      title: '引用关系',
      key: 'references',
      width: 220,
      render: (_: unknown, record: OpsAgentDefinition) => {
        const impact = referenceImpacts[record.agentId || ''];
        if (!impact?.totalCount) return <Tag color="grey">暂无自动化引用</Tag>;
        return (
          <Space wrap spacing={4}>
            <Tag color="blue">定时 {impact.scheduleCount}</Tag>
            <Tag color="orange">告警 {impact.alertCount}</Tag>
            <Tag color="green">Channel {impact.channelCount}</Tag>
            {impact.pinnedVersionCount > 0 && <Tag color="grey">固定版本 {impact.pinnedVersionCount}</Tag>}
          </Space>
        );
      },
    },
    {
      title: '流程规模',
      key: 'nodes',
      width: 80,
      render: (_: unknown, record: OpsAgentDefinition) => `${record.nodes?.length || 0} / ${record.edges?.length || 0}`
    },
    {
      title: '状态',
      dataIndex: 'lifecycle',
      width: 90,
      render: (lifecycle: string) => (
        <Tag color={lifecycle === 'PUBLISHED' ? 'green' : lifecycle === 'DISABLED' ? 'grey' : 'blue'}>
          {lifecycle === 'PUBLISHED' ? '已发布' : lifecycle === 'DISABLED' ? '已停用' : '草稿'}
        </Tag>
      ),
    },
    {
      title: '说明',
      dataIndex: 'description',
      key: 'description',
      width: 260,
      render: (_text: string, record: OpsAgentDefinition) => displayAgentDescription(record)
    },
    {
      title: '操作',
      key: 'action',
      width: 210,
      fixed: 'right' as const,
      render: (_: any, record: OpsAgentDefinition) => (
        <Space wrap spacing={4}>
          <ActionButton
            type="tertiary"
            size="small"
            icon={<IconEdit />}
            onClick={() => handleEdit(record)}
          >
            编辑
          </ActionButton>
          <ActionButton
            type="tertiary"
            size="small"
            icon={<IconCopy />}
            onClick={() => openClone(record)}
          >
            复制
          </ActionButton>
          <Popconfirm
            title="停用这个工作流？"
            content={referenceImpacts[record.agentId || '']?.totalCount
              ? `该工作流仍被 ${referenceImpacts[record.agentId || ''].scheduleCount} 个定时自动化、${referenceImpacts[record.agentId || ''].alertCount} 个告警触发器和 ${referenceImpacts[record.agentId || ''].channelCount} 个渠道引用；其中 ${referenceImpacts[record.agentId || ''].pinnedVersionCount} 个引用固定到特定版本。停用工作流不会静默改写这些绑定，每次执行仍按原绑定策略和生命周期校验处理。`
              : '当前没有自动化或渠道引用该工作流。已发布版本和审计历史会继续保留。'}
            onConfirm={() => handleDelete(record)}
          >
            <ActionButton
              type="danger"
              size="small"
              icon={<IconDelete />}
            >
              停用
            </ActionButton>
          </Popconfirm>
        </Space>
      )
    }
  ];

  // 搜索/分页都是已加载项目目录的本地派生，不重复请求后端。
  const handleSearch = () => {
    setSearchParams((current) => ({ ...current, pageNum: 1 }));
  };

  const handleReset = () => {
    setSearchParams({ pageNum: 1, pageSize: 10 });
  };

  const handlePageChange = (nextPageNum: number, nextPageSize: number) => {
    setSearchParams((current) => ({ ...current, pageNum: nextPageNum, pageSize: nextPageSize }));
  };

  // 新建
  const handleCreate = () => {
    navigate(`/workflows/config?projectId=${encodeURIComponent(projectScope.projectId)}`);
  };

  // 编辑
  const handleEdit = (record: OpsAgentDefinition) => {
    navigate(`/workflows/config?projectId=${encodeURIComponent(projectScope.projectId)}&agentId=${encodeURIComponent(record.agentId || '')}`);
  };

  const openClone = (record: OpsAgentDefinition) => {
    setCloneSource(record);
    setCloneForm({
      projectId: projectScope.projectId,
      agentId: '',
      name: `${displayAgentName(record)} 副本`,
    });
  };

  const submitClone = async () => {
    if (!cloneSource?.agentId || !cloneForm.projectId || !cloneForm.name.trim()) {
      Toast.warning('请选择目标项目，并填写工作流名称。');
      return;
    }
    try {
      const payload = {
        ...cloneForm,
        agentId: cloneForm.agentId.trim() || generatedId('workflow', cloneForm.name),
      };
      const created = await cloneMutation.mutateAsync({ sourceAgentId: cloneSource.agentId, ...payload });
      Toast.success('工作流已作为独立草稿复制到目标项目。');
      setCloneSource(null);
      const createdAgentId = created.agentId || payload.agentId;
      if (cloneForm.projectId !== projectScope.projectId) {
        projectScope.selectProject(cloneForm.projectId);
      }
      navigate(`/workflows/config?projectId=${encodeURIComponent(cloneForm.projectId)}&agentId=${encodeURIComponent(createdAgentId)}`);
    } catch (error) {
      Toast.error(userFacingError(error, '复制工作流失败，请稍后重试。'));
    }
  };

  const handleDelete = async (record: OpsAgentDefinition) => {
    try {
      await disableMutation.mutateAsync(record.agentId || '');
      Toast.success(`工作流已停用：${record.name || '未命名'}`);
    } catch (error) {
      Toast.error(userFacingError(error, '停用工作流失败，请稍后重试。'));
      console.error('Unable to disable Workflow:', error);
    }
  };

  return (
    <OpsPageShell selectedKey="workflows">
      <OpsPageHeader
        title="工作流"
        description="创建并发布可复用工作流，用于稳定、可重复的标准执行流程；开放式排查仍使用默认模型。"
      />

      <ProjectScopeBar
        projects={projectScope.projects}
        projectId={projectScope.projectId}
        onChange={projectScope.selectProject}
        loading={projectScope.loading}
        onRefresh={() => listQuery.refetch()}
        actions={<Button type="primary" icon={<IconPlus />} disabled={!projectScope.projectId} onClick={handleCreate}>新建工作流</Button>}
      />

      <SearchSection>
        <SearchRow>
          <Input
            placeholder="搜索名称或说明"
            value={searchParams.configName || ''}
            onChange={(value) => setSearchParams(prev => ({ ...prev, configName: value }))}
            style={{ width: 200 }}
            prefix={<IconSearch />}
          />
          <Button type="primary" onClick={handleSearch} loading={loading}>搜索</Button>
          <Button onClick={handleReset}>重置</Button>
        </SearchRow>
      </SearchSection>

      <TableCard>
        <Table
          columns={columns}
          dataSource={dataSource}
          loading={loading}
          pagination={{
            currentPage: searchParams.pageNum || 1,
            pageSize: searchParams.pageSize || 10,
            total: total,
            showSizeChanger: true,
            showQuickJumper: true,
            onChange: handlePageChange
          }}
          rowKey="agentId"
          scroll={{ x: 820 }}
          size="middle"
        />
      </TableCard>

      <Modal
        title="复制工作流"
        visible={Boolean(cloneSource)}
        onCancel={() => setCloneSource(null)}
        onOk={submitClone}
        okText="创建副本"
        cancelText="取消"
      >
        <Space vertical align="start" spacing="medium" style={{ width: '100%' }}>
          <Typography.Text type="tertiary">副本会成为独立工作流，后续修改不会影响源流程。</Typography.Text>
          <Typography.Text strong>目标项目</Typography.Text>
          <Select
            value={cloneForm.projectId}
            style={{ width: '100%' }}
            onChange={(value) => setCloneForm((current) => ({ ...current, projectId: String(value || '') }))}
          >
            {projectScope.projects.map((project) => (
              <Select.Option key={project.projectId} value={project.projectId}>{project.name}</Select.Option>
            ))}
          </Select>
          <Typography.Text strong>名称</Typography.Text>
          <Input value={cloneForm.name} onChange={(name) => setCloneForm((current) => ({ ...current, name }))} />
        </Space>
      </Modal>
    </OpsPageShell>
  );
};

export default AgentListPage;
