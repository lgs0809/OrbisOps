import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Layout,
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
import { Sidebar, Header, SIDEBAR_COLLAPSED_WIDTH, SIDEBAR_WIDTH } from '../components/layout';
import { opsAdminService, OpsAgentDefinition } from '../services/ops-admin-service';
import { clearAuthSession } from '../services/auth-session';
import { ProjectScopeBar } from '../components/project-scope-bar';
import { useProjectScope } from '../hooks/use-project-scope';
import { generatedId } from '../utils/generated-id';
import { useResponsiveSidebar } from '../hooks/use-responsive-sidebar';
import { AgentReferenceImpact, loadAgentReferenceImpacts } from '../services/ops-agent-reference-impact-service';

const { Content } = Layout;
const { Title } = Typography;

const displayAgentName = (agent: OpsAgentDefinition) => {
  return agent.name || agent.agentId;
};

const displayAgentDescription = (agent: OpsAgentDefinition) => {
  return agent.description || '-';
};

// 样式组件
const AgentListLayout = styled(Layout)`
  min-height: 100vh;
  background: ${theme.colors.bg.secondary};
`;

const MainContent = styled.div<{ $collapsed: boolean }>`
  display: flex;
  flex: 1;
  width: ${(props) =>
    props.$collapsed
      ? `calc(100vw - ${SIDEBAR_COLLAPSED_WIDTH}px)`
      : `calc(100vw - ${SIDEBAR_WIDTH}px)`};
  margin-left: ${(props) => (props.$collapsed ? `${SIDEBAR_COLLAPSED_WIDTH}px` : `${SIDEBAR_WIDTH}px`)};
  transition:
    width ${theme.animation.duration.normal} ${theme.animation.easing.cubic},
    margin-left ${theme.animation.duration.normal} ${theme.animation.easing.cubic};
  min-width: 0;
  overflow-x: hidden;

  @media (max-width: ${theme.breakpoints.md}) {
    width: 100vw;
    margin-left: 0;
  }
`;

const ContentArea = styled(Content)`
  box-sizing: border-box;
  flex: 1;
  width: 100%;
  max-width: 100%;
  padding: ${theme.spacing.lg};
  background: ${theme.colors.bg.secondary};
  overflow-y: auto;
  overflow-x: hidden;
  min-width: 0;

  @media (max-width: 720px) {
    padding: ${theme.spacing.base};
  }
`;

const PageHeader = styled.div`
  box-sizing: border-box;
  margin-bottom: ${theme.spacing.lg};
  width: 100%;
  max-width: 100%;
  margin-left: auto;
  margin-right: auto;
`;

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

interface AgentListPageProps {
  selectedKey?: string;
  onMenuSelect?: (key: string) => void;
}

export const AgentListPage: React.FC<AgentListPageProps> = ({
  selectedKey = 'agent-list',
  onMenuSelect
}) => {
  const navigate = useNavigate();
  const { collapsed, sidebarCollapsed, toggleSidebar, closeMobileSidebar } = useResponsiveSidebar();
  const projectScope = useProjectScope();

  // 处理侧边栏导航
  const handleNavigation = (path: string) => {
    switch (path) {
      case 'agent-list':
        navigate('/agent-list');
        break;
      case 'agent-config':
        navigate('/agent-config');
        break;
      case 'model-api-management':
        navigate('/model-api-management');
        break;
      case 'rag-order-management':
        navigate('/rag-order-management');
        break;
      case 'model-catalog':
        navigate('/model-catalog');
        break;
      case 'mcp-tool-management':
        navigate('/mcp-tool-management');
        break;
      default:
        navigate(path.startsWith('/') ? path : `/${path}`);
        break;
    }
  };

  // 处理退出登录
  const handleLogout = () => {
    clearAuthSession();
    navigate('/login');
  };
  const [loading, setLoading] = useState(false);
  const [dataSource, setDataSource] = useState<OpsAgentDefinition[]>([]);
  const [searchParams, setSearchParams] = useState<{
    configName?: string;
    pageNum?: number;
    pageSize?: number;
  }>({
    pageNum: 1,
    pageSize: 10
  });
  const [total, setTotal] = useState(0);
  const [referenceImpacts, setReferenceImpacts] = useState<Record<string, AgentReferenceImpact>>({});
  const [cloneSource, setCloneSource] = useState<OpsAgentDefinition | null>(null);
  const [cloneForm, setCloneForm] = useState({ projectId: '', agentId: '', name: '' });

  // 表格列定义
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
      title: '选择方式',
      key: 'invocation',
      width: 120,
      render: () => <Tag color="grey">用户显式选择</Tag>,
    },
    {
      title: '引用影响',
      key: 'references',
      width: 220,
      render: (_: unknown, record: OpsAgentDefinition) => {
        const impact = referenceImpacts[record.agentId || ''];
        if (!impact?.totalCount) return <Tag color="grey">暂无自动化引用</Tag>;
        return (
          <Space wrap spacing={4}>
            <Tag color="blue">巡检 {impact.scheduleCount}</Tag>
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
      title: '描述',
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
            编辑流程
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
            title="确定要停用这个专项 Workflow 吗？"
            content={referenceImpacts[record.agentId || '']?.totalCount
              ? `当前仍被 ${referenceImpacts[record.agentId || ''].scheduleCount} 个巡检、${referenceImpacts[record.agentId || ''].alertCount} 个告警和 ${referenceImpacts[record.agentId || ''].channelCount} 个 Channel 引用，其中 ${referenceImpacts[record.agentId || ''].pinnedVersionCount} 个固定版本引用。配置本身不会自动解绑，后续执行仍会经过各自绑定模式与生命周期校验。`
              : '当前没有自动化引用。停用后已发布版本与审计仍会保留。'}
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

  // 加载数据
  const loadData = async (params?: typeof searchParams, activeProjectId = projectScope.projectId) => {
    if (!activeProjectId) {
      setDataSource([]);
      setTotal(0);
      return;
    }
    setLoading(true);
    try {
      const queryParams = { ...searchParams, ...params };
      const response = await opsAdminService.listAgents(activeProjectId);
      const keyword = (queryParams.configName || '').trim().toLowerCase();
      const filtered = (response.data || []).filter((agent) => {
        if (agent.definitionKind !== 'SPECIALIZED_WORKFLOW') return false;
        const matchName = !keyword || displayAgentName(agent).toLowerCase().includes(keyword) || displayAgentDescription(agent).toLowerCase().includes(keyword);
        return matchName;
      });
      const impacts = await loadAgentReferenceImpacts(
        activeProjectId,
        filtered.map((agent) => agent.agentId || '').filter(Boolean),
      );
      setReferenceImpacts(impacts);
      const pageNum = queryParams.pageNum || 1;
      const pageSize = queryParams.pageSize || 10;
      setDataSource(filtered.slice((pageNum - 1) * pageSize, pageNum * pageSize));
      setTotal(filtered.length);
    } catch (error) {
      Toast.error('加载数据失败');
      console.error('加载数据失败:', error);
    } finally {
      setLoading(false);
    }
  };

  // 搜索
  const handleSearch = () => {
    const params = { ...searchParams, pageNum: 1 };
    setSearchParams(params);
    loadData(params);
  };

  // 重置搜索
  const handleReset = () => {
    const params = { pageNum: 1, pageSize: 10 };
    setSearchParams(params);
    loadData(params);
  };

  // 分页变化
  const handlePageChange = (pageNum: number, pageSize: number) => {
    const params = { ...searchParams, pageNum, pageSize };
    setSearchParams(params);
    loadData(params);
  };

  // 新建
  const handleCreate = () => {
    navigate(`/agent-config?projectId=${encodeURIComponent(projectScope.projectId)}`);
  };

  // 编辑
  const handleEdit = (record: OpsAgentDefinition) => {
    navigate(`/agent-config?projectId=${encodeURIComponent(projectScope.projectId)}&agentId=${encodeURIComponent(record.agentId || '')}`);
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
      Toast.warning('请选择目标项目并填写 Workflow 名称');
      return;
    }
    try {
      setLoading(true);
      const payload = {
        ...cloneForm,
        agentId: cloneForm.agentId.trim() || generatedId('workflow', cloneForm.name),
      };
      const response = await opsAdminService.cloneAgent(cloneSource.agentId, payload);
      if (response.code !== '0000' || !response.data) {
        throw new Error(response.info || '复制失败');
      }
      Toast.success('专项 Workflow 已复制为目标项目的独立草稿');
      setCloneSource(null);
      const createdAgentId = response.data.agentId || payload.agentId;
      if (cloneForm.projectId === projectScope.projectId) {
        await loadData(undefined, projectScope.projectId);
      } else {
        projectScope.selectProject(cloneForm.projectId);
      }
      navigate(`/agent-config?projectId=${encodeURIComponent(cloneForm.projectId)}&agentId=${encodeURIComponent(createdAgentId)}`);
    } catch (error) {
      Toast.error(error instanceof Error ? error.message : '复制失败');
    } finally {
      setLoading(false);
    }
  };

  // 停用
  const handleDelete = async (record: OpsAgentDefinition) => {
    try {
      setLoading(true);
      const response = await opsAdminService.deleteAgent(record.agentId || '');
      if (response.code === '0000' && response.data) {
        Toast.success(`已停用专项 Workflow：${record.name || '未命名'}`);
        // 重新加载数据
        await loadData();
      } else {
        Toast.error('停用配置失败');
      }
    } catch (error) {
      Toast.error('停用配置失败');
      console.error('停用配置失败:', error);
    } finally {
      setLoading(false);
    }
  };

  // 初始化加载数据
  useEffect(() => {
    if (projectScope.projectId) {
      void loadData({ ...searchParams, pageNum: 1 }, projectScope.projectId);
    }
  }, [projectScope.projectId]);

  return (
    <AgentListLayout>
      <Sidebar
        selectedKey={selectedKey}
        onSelect={handleNavigation}
        collapsed={sidebarCollapsed}
        onMobileClose={closeMobileSidebar}
      />
      <MainContent $collapsed={collapsed}>
        <div style={{ flex: 1, display: 'flex', flexDirection: 'column' }}>
          <Header
            onToggleSidebar={toggleSidebar}
            onLogout={handleLogout}
            collapsed={sidebarCollapsed}
          />
          <ContentArea>
            <PageHeader>
              <Title heading={3}>专项 Workflow</Title>
              <Typography.Text type="tertiary">项目主助手默认处理所有请求；这里管理重复、稳定场景下可选的专项执行流程。</Typography.Text>
            </PageHeader>

            <ProjectScopeBar
              projects={projectScope.projects}
              projectId={projectScope.projectId}
              onChange={projectScope.selectProject}
              loading={projectScope.loading}
              onRefresh={() => loadData(undefined, projectScope.projectId)}
              actions={<Button type="primary" icon={<IconPlus />} disabled={!projectScope.projectId} onClick={handleCreate}>新建专项 Workflow</Button>}
            />

            <SearchSection>
              <SearchRow>
                <Input
                  placeholder="请输入名称/描述"
                  value={searchParams.configName || ''}
                  onChange={(value) => setSearchParams(prev => ({ ...prev, configName: value }))}
                  style={{ width: 200 }}
                  prefix={<IconSearch />}
                />
                <Button
                  type="primary"
                  onClick={handleSearch}
                  loading={loading}
                >
                  搜索
                </Button>
                <Button onClick={handleReset}>
                  重置
                </Button>
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
              title="复制专项 Workflow"
              visible={Boolean(cloneSource)}
              onCancel={() => setCloneSource(null)}
              onOk={submitClone}
              okText="创建副本"
            >
              <Space vertical align="start" spacing="medium" style={{ width: '100%' }}>
                <Typography.Text type="tertiary">复制后形成独立 Workflow，后续修改不会影响来源流程。</Typography.Text>
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
          </ContentArea>
        </div>
      </MainContent>
    </AgentListLayout>
  );
};

export default AgentListPage;
