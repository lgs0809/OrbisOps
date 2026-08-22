import React, { useEffect, useMemo, useState } from 'react';
import { Button, Card, Modal, Select, Space, Spin, TabPane, Tabs, Tag, Toast, Typography } from '@douyinfe/semi-ui';
import { IconBranch, IconSetting } from '@douyinfe/semi-icons';
import { useNavigate, useSearchParams } from 'react-router-dom';
import styled from 'styled-components';

import { OpsPageHeader, OpsPageShell } from '../components/ops-layout';
import { ProjectScopeBar } from '../components/project-scope-bar';
import {
  useProjectWorkspaceCapabilitiesQuery,
  useProjectWorkspaceRuntimeQuery,
} from '../features/projects/api/project-workspace-queries';
import { useProjectScope } from '../hooks/use-project-scope';
import { opsAdminService, type OpsAgentDefinition } from '../services/ops-admin-service';
import { opsProjectService } from '../services/ops-project-service';
import { theme } from '../styles/theme';
import { userFacingError } from '../utils/user-facing-error';

const { Paragraph, Text, Title } = Typography;
const { Option } = Select;

type ProjectView = 'overview' | 'resources' | 'capabilities' | 'members' | 'runtime';

const Grid = styled.div`
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: ${theme.spacing.base};

  @media (max-width: ${theme.breakpoints.lg}) {
    grid-template-columns: 1fr;
  }
`;

const Stack = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${theme.spacing.base};
`;

const ListCard = styled(Card)`
  min-width: 0;
`;

const MemberRow = styled.div`
  display: grid;
  grid-template-columns: minmax(0, 1fr) 160px auto;
  gap: ${theme.spacing.sm};
  align-items: center;
  padding: ${theme.spacing.sm} 0;
  border-bottom: 1px solid ${theme.colors.border.secondary};

  &:last-child {
    border-bottom: 0;
  }

  @media (max-width: ${theme.breakpoints.sm}) {
    grid-template-columns: 1fr;
  }
`;

const metric = (label: string, value: React.ReactNode, hint: string) => (
  <Card>
    <Text type="tertiary" size="small">{label}</Text>
    <Title heading={4} style={{ margin: '6px 0' }}>{value}</Title>
    <Text type="tertiary" size="small">{hint}</Text>
  </Card>
);

const normalizedMember = (member: any) => ({
  userId: member.userId || member.user_id || '',
  username: member.username || '',
  memberRole: member.memberRole || member.member_role || 'MEMBER',
});

export const ProjectProductWorkspacePage: React.FC = () => {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const scope = useProjectScope();
  const project = scope.selectedProject;
  const projectId = project?.projectId || '';
  const capabilitiesQuery = useProjectWorkspaceCapabilitiesQuery(projectId);
  const runtimeQuery = useProjectWorkspaceRuntimeQuery(projectId);
  const [workflows, setWorkflows] = useState<OpsAgentDefinition[]>([]);
  const [memberModalVisible, setMemberModalVisible] = useState(false);
  const [memberDrafts, setMemberDrafts] = useState<Array<{ userId: string; username: string; memberRole: string }>>([]);
  const [memberSaving, setMemberSaving] = useState(false);

  const requestedView = searchParams.get('view') as ProjectView | null;
  const view: ProjectView = ['overview', 'resources', 'capabilities', 'members', 'runtime'].includes(requestedView || '')
    ? requestedView as ProjectView
    : 'overview';

  useEffect(() => {
    if (!projectId) {
      setWorkflows([]);
      return;
    }
    let active = true;
    opsAdminService.listAgents(projectId)
      .then((response) => {
        if (!active) return;
        setWorkflows((response.data || []).filter((workflow) => String(workflow.lifecycle || '').toUpperCase() === 'PUBLISHED'));
      })
      .catch(() => {
        if (active) setWorkflows([]);
      });
    return () => {
      active = false;
    };
  }, [projectId]);

  const capabilityData = capabilitiesQuery.data;
  const runtimeData = runtimeQuery.data;
  const members = useMemo(() => (capabilityData?.members || []).map(normalizedMember), [capabilityData?.members]);
  const accounts = capabilityData?.accounts || [];
  const projectQuery = projectId ? `?projectId=${encodeURIComponent(projectId)}` : '';

  const setView = (next: string) => {
    setSearchParams((current) => {
      const params = new URLSearchParams(current);
      params.set('view', next);
      return params;
    }, { replace: true });
  };

  const openMembers = () => {
    setMemberDrafts(members);
    setMemberModalVisible(true);
  };

  const addMember = (userId: string) => {
    const account = accounts.find((item) => item.userId === userId);
    if (!account || memberDrafts.some((member) => member.userId === userId)) return;
    setMemberDrafts((current) => [...current, { userId, username: account.username, memberRole: 'MEMBER' }]);
  };

  const saveMembers = async () => {
    if (!projectId) return;
    setMemberSaving(true);
    try {
      await opsProjectService.replaceProjectMembers(projectId, memberDrafts);
      await capabilitiesQuery.refetch();
      setMemberModalVisible(false);
      Toast.success('项目成员已更新。');
    } catch (error) {
      Toast.error(userFacingError(error, '更新项目成员失败，请稍后重试。'));
    } finally {
      setMemberSaving(false);
    }
  };

  return (
    <OpsPageShell selectedKey="projects">
      <OpsPageHeader
        title="项目"
        description="管理项目的资源、能力、成员和运行配置。"
        primaryAction={project?.readyForInvestigation
          ? <Button type="primary" icon={<IconBranch />} onClick={() => navigate(`/chat${projectQuery}`)}>打开对话</Button>
          : undefined}
      />

      <ProjectScopeBar
        projects={scope.projects}
        projectId={scope.projectId}
        loading={scope.loading}
        onChange={scope.selectProject}
        onRefresh={() => void scope.reloadProjects()}
      />

      <Spin spinning={scope.loading || capabilitiesQuery.isFetching || runtimeQuery.isFetching}>
        {!project ? (
          <Card><Text type="tertiary">当前没有可用项目。</Text></Card>
        ) : (
          <Tabs activeKey={view} onChange={setView} type="line">
            <TabPane tab="概览" itemKey="overview">
              <Stack>
                <Card>
                  <Space vertical align="start" style={{ width: '100%' }}>
                    <Space wrap>
                      <Title heading={4} style={{ margin: 0 }}>{project.name}</Title>
                      <Tag color={project.readyForInvestigation ? 'green' : 'orange'}>
                        {project.readyForInvestigation ? '可进行排查' : '配置未完成'}
                      </Tag>
                    </Space>
                    <Paragraph type="tertiary" style={{ margin: 0 }}>{project.description || '暂无项目说明。'}</Paragraph>
                    <Space wrap>
                      <Tag>负责人：{project.owner || '-'}</Tag>
                      {(project.environments || []).map((environment) => <Tag key={environment}>{environment}</Tag>)}
                      <Tag color="blue">默认模型：{project.defaultAgentPublished ? '已就绪' : project.defaultAgentId ? '待发布' : '未配置'}</Tag>
                    </Space>
                  </Space>
                </Card>
                <Grid>
                  {metric('资源', project.resourceCount || 0, '当前项目可用的数据与服务资源')}
                  {metric('MCP 工具', project.generatedMcpCount || 0, '生成或接入的项目工具能力')}
                  {metric('成员', members.length, '已加入当前项目的平台用户')}
                  {metric('技能', project.skillIds?.length || 0, '可供智能执行使用的项目技能')}
                  {metric('知识库', project.knowledgeBaseIds?.length || (project.knowledgeBaseId ? 1 : 0), '已授权给当前项目的知识库')}
                  {metric('已发布工作流', workflows.length, '当前项目可用的可复用工作流')}
                </Grid>
              </Stack>
            </TabPane>

            <TabPane tab="资源" itemKey="resources">
              <Grid>
                {metric('数据资源', project.dataResourceCount || 0, '指标、日志、数据库、API 等证据来源')}
                {metric('代码仓库', project.sourceRepositoryCount || 0, '当前项目边界内的代码仓库')}
                {metric('执行资源', project.executionResourceCount || 0, '允许参与受治理落地的执行目标')}
              </Grid>
              <Card style={{ marginTop: 16 }}>
                <Text type="tertiary">在这里查看项目已连接的数据与执行资源；资源权限始终以当前项目的授权配置为准。</Text>
              </Card>
            </TabPane>

            <TabPane tab="能力" itemKey="capabilities">
              <Grid>
                <ListCard title="默认模型">
                  <Space vertical align="start">
                    <Tag color={project.defaultAgentPublished ? 'green' : 'orange'}>{project.defaultAgentPublished ? '已发布' : '未发布'}</Tag>
                    <Text>{project.defaultAgentId ? '用于开放式排查与对话的默认智能执行能力。' : '尚未配置默认模型。'}</Text>
                  </Space>
                </ListCard>
                <ListCard title={`工具 / MCP (${project.generatedMcpCount || 0})`}>
                  <Text type="tertiary">已授权给当前项目的 MCP 与工具能力。</Text>
                </ListCard>
                <ListCard title={`知识库 (${capabilityData?.knowledgeBases?.length || 0})`}>
                  <Stack>{(capabilityData?.knowledgeBases || []).map((item: any) => <Tag key={item.kbId || item.knowledgeBaseId || item.name}>{item.kbName || item.name || item.kbId}</Tag>)}</Stack>
                </ListCard>
                <ListCard title={`技能 (${capabilityData?.projectSkills?.length || 0})`}>
                  <Stack>{(capabilityData?.projectSkills || []).map((item: any) => <Tag key={item.skillId || item.name}>{item.name || item.skillId}</Tag>)}</Stack>
                </ListCard>
                <ListCard title={`可用工作流 (${workflows.length})`}>
                  <Stack>{workflows.map((workflow: any) => <Tag key={workflow.agentId || workflow.id}>{workflow.name || workflow.agentName || workflow.agentId}</Tag>)}</Stack>
                </ListCard>
              </Grid>
            </TabPane>

            <TabPane tab="成员" itemKey="members">
              <Card title="项目成员" headerExtraContent={<Button onClick={openMembers}>管理成员</Button>}>
                <Stack>
                  {members.map((member) => (
                    <Space key={member.userId || member.username} wrap>
                      <Text strong>{member.username || member.userId}</Text>
                      <Tag>{member.memberRole === 'OWNER' ? '负责人' : member.memberRole === 'ADMIN' ? '项目管理员' : member.memberRole === 'VIEWER' ? '只读成员' : '成员'}</Tag>
                    </Space>
                  ))}
                  {members.length === 0 && <Text type="tertiary">当前项目尚未分配用户。</Text>}
                </Stack>
              </Card>
            </TabPane>

            <TabPane tab="运行配置" itemKey="runtime">
              <Grid>
                <ListCard title={`代码仓库 (${runtimeData?.repositories?.length || 0})`}>
                  <Stack>{(runtimeData?.repositories || []).map((item: any) => <Tag key={item.repositoryId || item.id || item.name}>{item.name || item.repositoryId || item.path}</Tag>)}</Stack>
                </ListCard>
                <ListCard title={`服务 (${runtimeData?.services?.length || 0})`}>
                  <Stack>{(runtimeData?.services || []).map((item: any) => <Tag key={item.serviceId || item.id || item.name}>{item.name || item.serviceId}</Tag>)}</Stack>
                </ListCard>
                <ListCard title={`执行目标 (${runtimeData?.executionResources?.length || 0})`}>
                  <Stack>{(runtimeData?.executionResources || []).map((item: any) => <Tag key={item.resourceId || item.id || item.name}>{item.name || item.resourceId}</Tag>)}</Stack>
                </ListCard>
              </Grid>
              <Card style={{ marginTop: 16 }}>
                <Space>
                  <IconSetting />
                  <Text type="tertiary">这里用于观察运行配置清单；生产变更仍必须经过受控变更包 → 审批 → 落地 → 验证。</Text>
                </Space>
              </Card>
            </TabPane>
          </Tabs>
        )}
      </Spin>

      <Modal
        title="管理项目成员"
        visible={memberModalVisible}
        confirmLoading={memberSaving}
        okText="保存成员"
        cancelText="取消"
        onOk={() => void saveMembers()}
        onCancel={() => setMemberModalVisible(false)}
        width={680}
      >
        <Stack>
          <Text type="tertiary">只能添加已经存在的平台用户；如需创建账号，请前往 设置 → 用户与访问控制。</Text>
          <Select
            placeholder="添加已有平台用户"
            filter
            style={{ width: '100%' }}
            value={undefined}
            onChange={(value) => addMember(String(value || ''))}
            emptyContent="暂无可添加的已启用平台用户。"
          >
            {accounts
              .filter((account) => !memberDrafts.some((member) => member.userId === account.userId))
              .map((account) => <Option key={account.userId} value={account.userId}>{account.username}</Option>)}
          </Select>
          {memberDrafts.map((member) => (
            <MemberRow key={member.userId}>
              <div>
                <Text strong>{member.username}</Text>
                <div><Text type="tertiary">{member.userId}</Text></div>
              </div>
              <Select
                value={member.memberRole}
                onChange={(value) => setMemberDrafts((current) => current.map((item) => item.userId === member.userId ? { ...item, memberRole: String(value || 'MEMBER') } : item))}
              >
                <Option value="OWNER">负责人</Option>
                <Option value="ADMIN">项目管理员</Option>
                <Option value="MEMBER">成员</Option>
                <Option value="VIEWER">只读成员</Option>
              </Select>
              <Button type="danger" onClick={() => setMemberDrafts((current) => current.filter((item) => item.userId !== member.userId))}>移除</Button>
            </MemberRow>
          ))}
        </Stack>
      </Modal>
    </OpsPageShell>
  );
};
