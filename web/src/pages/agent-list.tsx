import React, { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Button,
  Input,
  Modal,
  Popconfirm,
  Select,
  Space,
  Tag,
  Toast,
  Typography,
} from '@douyinfe/semi-ui';
import {
  IconBranch,
  IconCopy,
  IconDelete,
  IconEdit,
  IconPlus,
  IconRefresh,
  IconSearch,
} from '@douyinfe/semi-icons';
import styled from 'styled-components';

import { OpsPageShell } from '../components/ops-layout';
import {
  useAgentListQuery,
  useCloneAgentMutation,
  useDisableAgentMutation,
} from '../features/agents/api/agent-list-queries';
import { useProjectScope } from '../hooks/use-project-scope';
import type { OpsAgentDefinition } from '../services/ops-admin-service';
import { theme } from '../styles/theme';
import { generatedId } from '../utils/generated-id';
import { userFacingError } from '../utils/user-facing-error';
import { WorkflowManualTestButton } from '../features/agents/components/WorkflowManualTestButton';

const { Text } = Typography;

const displayAgentName = (agent: OpsAgentDefinition) => agent.name || agent.agentId;
const displayAgentDescription = (agent: OpsAgentDefinition) => agent.description || '暂无说明';

const LibraryHeader = styled.header`
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 18px;
  align-items: end;
  padding: 22px 0 20px;

  h1 {
    margin: 0;
    color: ${theme.colors.text.primary};
    font-size: 30px;
    font-weight: 650;
    letter-spacing: -0.04em;
  }

  p {
    max-width: 720px;
    margin: 8px 0 0;
    color: ${theme.colors.text.tertiary};
    font-size: 12px;
    line-height: 1.65;
  }

  @media (max-width: ${theme.breakpoints.md}) {
    grid-template-columns: 1fr;
    align-items: start;
  }
`;

const ContextBar = styled.div`
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border: 1px solid ${theme.colors.border.secondary};
  border-radius: 11px;
  background: #fff;
  flex-wrap: wrap;

  .semi-select { min-width: 220px; }
`;

const Toolbar = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin: 18px 0;
  flex-wrap: wrap;
`;

const SearchGroup = styled.div`
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;

  @media (max-width: ${theme.breakpoints.sm}) {
    width: 100%;

    .semi-input-wrapper {
      width: 100% !important;
    }
  }
`;

const WorkflowGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;

  @media (max-width: 1280px) {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  @media (max-width: ${theme.breakpoints.md}) {
    grid-template-columns: 1fr;
  }
`;

const WorkflowCard = styled.article`
  min-width: 0;
  min-height: 220px;
  display: flex;
  flex-direction: column;
  padding: 18px;
  border: 1px solid ${theme.colors.border.secondary};
  border-radius: 13px;
  background: #fff;
  transition: border-color ${theme.animation.duration.fast} ease, box-shadow ${theme.animation.duration.fast} ease, transform ${theme.animation.duration.fast} ease;

  &:hover {
    border-color: #d4d7dc;
    box-shadow: ${theme.shadows.card};
    transform: translateY(-1px);
  }
`;

const CardTop = styled.div`
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
`;

const WorkflowIdentity = styled.button`
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 11px;
  padding: 0;
  border: 0;
  background: transparent;
  color: inherit;
  text-align: left;
  cursor: pointer;

  &:focus-visible {
    outline: 2px solid ${theme.colors.primary};
    outline-offset: 3px;
    border-radius: 8px;
  }
`;

const WorkflowIcon = styled.div`
  width: 38px;
  height: 38px;
  flex-shrink: 0;
  display: grid;
  place-items: center;
  border-radius: 10px;
  background: #eef2ff;
  color: #4f46e5;

  .semi-icon {
    font-size: 19px;
  }
`;

const WorkflowName = styled.div`
  min-width: 0;

  strong {
    display: block;
    overflow: hidden;
    color: ${theme.colors.text.primary};
    font-size: 14px;
    font-weight: 650;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  span {
    display: block;
    margin-top: 3px;
    color: ${theme.colors.text.tertiary};
    font-size: 12px;
  }
`;

const Description = styled.p`
  flex: 1;
  margin: 16px 0 14px;
  color: ${theme.colors.text.secondary};
  font-size: 13px;
  line-height: 1.65;
`;

const ReferenceRow = styled.div`
  min-height: 28px;
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
`;

const Footer = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  flex-wrap: wrap;
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px solid ${theme.colors.border.tertiary};
`;

const Scale = styled(Text)`
  && {
    color: ${theme.colors.text.tertiary};
    font-size: 12px;
  }
`;

const EmptyGrid = styled.div`
  padding: 64px 20px;
  border: 1px dashed ${theme.colors.border.primary};
  border-radius: 13px;
  background: #fafbfc;
  color: ${theme.colors.text.tertiary};
  text-align: center;
`;

const PaginationBar = styled.div`
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 16px;
  color: ${theme.colors.text.tertiary};
  font-size: 12px;
`;

export const AgentListPage: React.FC = () => {
  const navigate = useNavigate();
  const projectScope = useProjectScope();
  const [searchParams, setSearchParams] = useState<{
    configName?: string;
    pageNum?: number;
    pageSize?: number;
    lifecycle?: string;
  }>({ pageNum: 1, pageSize: 12 });
  const listQuery = useAgentListQuery(projectScope.projectId);
  const cloneMutation = useCloneAgentMutation();
  const disableMutation = useDisableAgentMutation(projectScope.projectId);
  const referenceImpacts = listQuery.data?.referenceImpacts || {};
  const filteredWorkflows = useMemo(() => {
    const keyword = (searchParams.configName || '').trim().toLowerCase();
    return (listQuery.data?.workflows || []).filter((agent) => (
      (!searchParams.lifecycle || agent.lifecycle === searchParams.lifecycle) && (!keyword
      || displayAgentName(agent).toLowerCase().includes(keyword)
      || displayAgentDescription(agent).toLowerCase().includes(keyword))
    ));
  }, [listQuery.data?.workflows, searchParams.configName, searchParams.lifecycle]);
  const pageSize = searchParams.pageSize || 12;
  const total = filteredWorkflows.length;
  const pageCount = Math.max(1, Math.ceil(total / pageSize));
  const pageNum = Math.min(searchParams.pageNum || 1, pageCount);
  const dataSource = filteredWorkflows.slice((pageNum - 1) * pageSize, pageNum * pageSize);
  const listLoading = projectScope.loading || listQuery.isLoading;
  const loading = listLoading || cloneMutation.isPending || disableMutation.isPending;
  const [cloneSource, setCloneSource] = useState<OpsAgentDefinition | null>(null);
  const [cloneForm, setCloneForm] = useState({ projectId: '', agentId: '', name: '' });

  // 搜索/分页都是已加载项目目录的本地派生，不重复请求后端。
  const handleSearch = () => {
    setSearchParams((current) => ({ ...current, pageNum: 1 }));
  };

  const handleReset = () => {
    setSearchParams({ pageNum: 1, pageSize: 12 });
  };

  const handlePageChange = (nextPageNum: number, nextPageSize = pageSize) => {
    setSearchParams((current) => ({ ...current, pageNum: nextPageNum, pageSize: nextPageSize }));
  };

  const handleCreate = () => {
    navigate(`/workflows/config?projectId=${encodeURIComponent(projectScope.projectId)}`);
  };

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
    }
  };

  return (
    <OpsPageShell selectedKey="workflows">
      <LibraryHeader>
        <div>
          <h1>工作流</h1>
          <p>把稳定、可重复的运维过程做成显式可选、可发布、可复用的流程资产。开放式排查仍然直接使用默认助手。</p>
        </div>
        <Button type="primary" icon={<IconPlus />} disabled={!projectScope.projectId} onClick={handleCreate}>新建工作流</Button>
      </LibraryHeader>

      <ContextBar>
        <Text type="tertiary">项目</Text>
        <Select
          value={projectScope.projectId || undefined}
          placeholder="选择项目"
          loading={projectScope.loading}
          onChange={(value) => projectScope.selectProject(String(value || ''))}
        >
          {projectScope.projects.map((project) => <Select.Option key={project.projectId} value={project.projectId}>{project.name}</Select.Option>)}
        </Select>
        <Button icon={<IconRefresh />} onClick={() => void listQuery.refetch()}>刷新</Button>
        <Text type="tertiary">{projectScope.selectedProject?.description || '选择项目后查看工作流资产。'}</Text>
      </ContextBar>

      <Toolbar>
        <SearchGroup>
          <Input
            placeholder="搜索名称或说明"
            value={searchParams.configName || ''}
            onChange={(value) => setSearchParams((current) => ({ ...current, configName: value, pageNum: 1 }))}
            style={{ width: 260 }}
            prefix={<IconSearch />}
            showClear
          />
          <Select aria-label="工作流发布状态" value={searchParams.lifecycle || ''} onChange={value => setSearchParams(current => ({ ...current, lifecycle: String(value || ''), pageNum: 1 }))} style={{ width: 150 }}>
            <Select.Option value="">全部状态</Select.Option>
            <Select.Option value="PUBLISHED">已发布可使用</Select.Option>
            <Select.Option value="DRAFT">待完善草稿</Select.Option>
            <Select.Option value="DISABLED">已停用</Select.Option>
          </Select>
          <Button type="primary" onClick={handleSearch} loading={loading}>搜索</Button>
          <Button onClick={handleReset}>重置</Button>
        </SearchGroup>
        <Text type="tertiary" role={listLoading ? 'status' : undefined}>{listLoading ? '正在读取工作流…' : `${total} 个工作流`}</Text>
      </Toolbar>

      {listQuery.isError ? <EmptyGrid role="alert">工作流暂时无法读取。<Button onClick={() => void listQuery.refetch()}>重新加载工作流</Button></EmptyGrid> : dataSource.length === 0 && !loading ? (
        <EmptyGrid>当前项目还没有匹配的工作流。可以新建一个，或清除搜索条件。</EmptyGrid>
      ) : (
        <WorkflowGrid>
          {dataSource.map((record) => {
            const impact = referenceImpacts[record.agentId || ''];
            const lifecycle = String(record.lifecycle || '').toUpperCase();
            return (
              <WorkflowCard key={record.agentId}>
                <CardTop>
                  <WorkflowIdentity type="button" onClick={() => handleEdit(record)}>
                    <WorkflowIcon><IconBranch /></WorkflowIcon>
                    <WorkflowName>
                      <strong>{displayAgentName(record)}</strong>
                      <span>v{record.version || 1} · {record.nodes?.length || 0} 个节点 / {record.edges?.length || 0} 条连接</span>
                    </WorkflowName>
                  </WorkflowIdentity>
                  <Tag color={lifecycle === 'PUBLISHED' ? 'green' : lifecycle === 'DISABLED' ? 'grey' : 'blue'}>
                    {lifecycle === 'PUBLISHED' ? '已发布' : lifecycle === 'DISABLED' ? '已停用' : '草稿'}
                  </Tag>
                </CardTop>

                <Description>{displayAgentDescription(record)}</Description>

                <ReferenceRow>
                  {!impact?.totalCount ? (
                    <Tag color="grey">暂无自动化引用</Tag>
                  ) : (
                    <>
                      {impact.scheduleCount > 0 && <Tag color="blue">定时 {impact.scheduleCount}</Tag>}
                      {impact.alertCount > 0 && <Tag color="orange">告警 {impact.alertCount}</Tag>}
                      {impact.channelCount > 0 && <Tag color="green">渠道 {impact.channelCount}</Tag>}
                      {impact.pinnedVersionCount > 0 && <Tag color="grey">固定版本 {impact.pinnedVersionCount}</Tag>}
                    </>
                  )}
                </ReferenceRow>

                <Footer>
                  <Scale>可复用流程资产</Scale>
                  <Space spacing={4} wrap>
                    {lifecycle === 'PUBLISHED' && <WorkflowManualTestButton projectId={projectScope.projectId} definition={record} compact onNavigate={navigate} />}
                    <Button type="tertiary" size="small" icon={<IconEdit />} onClick={() => handleEdit(record)}>编辑</Button>
                    <Button type="tertiary" size="small" icon={<IconCopy />} onClick={() => openClone(record)}>复制</Button>
                    <Popconfirm
                      title="停用这个工作流？"
                      content={impact?.totalCount
                        ? `该工作流仍被 ${impact.scheduleCount} 个定时自动化、${impact.alertCount} 个告警触发器和 ${impact.channelCount} 个渠道引用；停用不会静默改写这些绑定。`
                        : '当前没有自动化或渠道引用该工作流。已发布版本和审计历史会继续保留。'}
                      onConfirm={() => handleDelete(record)}
                    >
                      <Button type="danger" size="small" icon={<IconDelete />}>停用</Button>
                    </Popconfirm>
                  </Space>
                </Footer>
              </WorkflowCard>
            );
          })}
        </WorkflowGrid>
      )}

      {total > pageSize && (
        <PaginationBar>
          <Button size="small" disabled={pageNum <= 1} onClick={() => handlePageChange(pageNum - 1)}>上一页</Button>
          <span>{pageNum} / {pageCount}</span>
          <Button size="small" disabled={pageNum >= pageCount} onClick={() => handlePageChange(pageNum + 1)}>下一页</Button>
        </PaginationBar>
      )}

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
