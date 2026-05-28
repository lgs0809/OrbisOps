import React, { useEffect, useMemo, useState } from 'react';
import { Button, Input, Modal, Select, Space, Spin, TabPane, Tabs, Tag, Toast, Typography } from '@douyinfe/semi-ui';
import { IconBranch, IconFolder, IconPlus, IconRefresh, IconSetting } from '@douyinfe/semi-icons';
import { useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import styled from 'styled-components';

import { OpsPageShell, OpsWorkspaceFrame } from '../components/ops-layout';
import {
  type ProjectWorkspaceCapabilityData,
  useProjectWorkspaceCapabilitiesQuery,
  useProjectWorkspaceSnapshotQuery,
  useProjectWorkspaceRuntimeQuery,
} from '../features/projects/api/project-workspace-queries';
import { useProjectScope } from '../hooks/use-project-scope';
import { opsAdminService, type OpsAgentDefinition } from '../services/ops-admin-service';
import { opsProjectService, type OpsProjectWorkspace } from '../services/ops-project-service';
import { theme } from '../styles/theme';
import { userFacingError } from '../utils/user-facing-error';
import { getStoredUserInfo, isAdminUser } from '../services/auth-session';
import { ProjectSkillGovernancePanel } from '../features/projects/components/ProjectSkillGovernancePanel';

const { Text } = Typography;
const { Option } = Select;

import { projectProductViewFor } from '../features/projects/model/project-product-navigation';

const Workspace = styled(OpsWorkspaceFrame)`
  min-height: 0;
  display: grid;
  grid-template-columns: 238px minmax(0, 1fr);
  border: 1px solid ${theme.colors.border.secondary};
  border-radius: 14px;
  background: #fff;
  overflow: hidden;

  @media (max-width: 900px) {
    grid-template-columns: 1fr;
  }

`;

const ProjectRail = styled.aside`
  min-width: 0;
  display: flex;
  flex-direction: column;
  background: #f7f8fa;
  border-right: 1px solid ${theme.colors.border.secondary};

  @media (max-width: 900px) {
    max-height: 260px;
    border-right: 0;
    border-bottom: 1px solid ${theme.colors.border.secondary};
  }
`;

const RailHeader = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 17px 12px 10px 14px;

  h1 {
    margin: 0;
    font-size: 16px;
    font-weight: 650;
    letter-spacing: -0.02em;
  }
`;

const ProjectList = styled.div`
  min-height: 0;
  flex: 1;
  overflow: auto;
  padding: 4px 8px 14px;
`;

const ProjectButton = styled.button<{ $active: boolean }>`
  width: 100%;
  display: grid;
  grid-template-columns: 30px minmax(0, 1fr);
  gap: 9px;
  align-items: center;
  padding: 9px 8px;
  margin: 2px 0;
  border: 0;
  border-radius: 9px;
  background: ${(props) => (props.$active ? '#e5e8ec' : 'transparent')};
  color: inherit;
  text-align: left;
  cursor: pointer;

  &:hover { background: ${(props) => (props.$active ? '#e0e3e7' : '#eceef1')}; }

  .icon {
    width: 30px;
    height: 30px;
    display: grid;
    place-items: center;
    border-radius: 8px;
    background: ${(props) => (props.$active ? '#111318' : '#e6e9ee')};
    color: ${(props) => (props.$active ? '#fff' : '#66707d')};
  }

  strong {
    display: block;
    overflow: hidden;
    font-size: 12px;
    font-weight: 600;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  span {
    display: block;
    margin-top: 2px;
    color: ${theme.colors.text.tertiary};
    font-size: 12px;
  }
`;

const ProjectMain = styled.section`
  min-width: 0;
  min-height: 0;
  overflow: auto;
  background: #fff;
`;

const ProjectHeader = styled.header`
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 18px;
  align-items: start;
  padding: 22px 24px 16px;
  border-bottom: 1px solid ${theme.colors.border.tertiary};

  @media (max-width: ${theme.breakpoints.md}) {
    grid-template-columns: 1fr;
  }
`;

const ProjectIdentity = styled.div`
  min-width: 0;

  .eyebrow {
    display: block;
    margin-bottom: 5px;
    color: ${theme.colors.text.tertiary};
    font-size: 12px;
    font-weight: 650;
    letter-spacing: 0.08em;
    text-transform: uppercase;
  }

  h2 {
    margin: 0;
    color: ${theme.colors.text.primary};
    font-size: 25px;
    font-weight: 650;
    letter-spacing: -0.03em;
  }

  p {
    max-width: 760px;
    margin: 7px 0 0;
    color: ${theme.colors.text.tertiary};
    font-size: 12px;
    line-height: 1.6;
  }
`;

const ProjectMeta = styled.div`
  display: flex;
  gap: 6px;
  margin-top: 12px;
  flex-wrap: wrap;
`;

const TabsWrap = styled.div`
  .semi-tabs-bar {
    margin: 0;
    padding: 0 24px;
    background: #fff;
  }

  .semi-tabs-content {
    padding: 0;
  }
`;

const Body = styled.div`
  padding: 22px 24px 32px;
`;

const StatGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;

  @media (max-width: ${theme.breakpoints.lg}) { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  @media (max-width: ${theme.breakpoints.sm}) { grid-template-columns: 1fr; }
`;

const Stat = styled.div`
  min-width: 0;
  padding: 15px;
  border: 1px solid ${theme.colors.border.secondary};
  border-radius: 11px;
  background: #fff;

  .label { color: ${theme.colors.text.tertiary}; font-size: 12px; font-weight: 600; }
  .value { display: block; margin-top: 7px; font-size: 24px; font-weight: 650; line-height: 1; }
  .hint { display: block; margin-top: 7px; color: ${theme.colors.text.tertiary}; font-size: 12px; line-height: 1.5; }
`;

const OverviewLead = styled.section`
  margin-bottom: 16px;
  padding: 18px;
  border-radius: 12px;
  background: #f7f8fa;

  strong { font-size: 13px; font-weight: 650; }
  p { margin: 5px 0 0; color: ${theme.colors.text.tertiary}; font-size: 12px; line-height: 1.6; }
`;

const CapabilityGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;

  @media (max-width: ${theme.breakpoints.md}) { grid-template-columns: 1fr; }
`;

const CapabilityPanel = styled.section`
  min-width: 0;
  min-height: 150px;
  padding: 15px;
  border: 1px solid ${theme.colors.border.secondary};
  border-radius: 11px;
  background: #fff;

  h3 { margin: 0 0 10px; font-size: 12px; font-weight: 650; }
`;

const Stack = styled.div`
  display: flex;
  flex-direction: column;
  gap: 8px;
`;

const MemberPanel = styled.section`
  border: 1px solid ${theme.colors.border.secondary};
  border-radius: 11px;
  overflow: hidden;
`;

const MemberHeader = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  padding: 13px 15px;
  border-bottom: 1px solid ${theme.colors.border.tertiary};
  strong { font-size: 12px; font-weight: 650; }
`;

const MemberItem = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 15px;
  border-bottom: 1px solid ${theme.colors.border.tertiary};
  &:last-child { border-bottom: 0; }
`;

const MemberRow = styled.div`
  display: grid;
  grid-template-columns: minmax(0, 1fr) 160px auto;
  gap: 8px;
  align-items: center;
  padding: 8px 0;
  border-bottom: 1px solid ${theme.colors.border.secondary};
  &:last-child { border-bottom: 0; }
  @media (max-width: ${theme.breakpoints.sm}) { grid-template-columns: 1fr; }
`;

const metric = (label: string, value: React.ReactNode, hint: string) => (
  <Stat><span className="label">{label}</span><strong className="value">{value}</strong><span className="hint">{hint}</span></Stat>
);

const normalizedMember = (member: any) => ({
  userId: member.userId || member.user_id || '',
  username: member.username || '',
  memberRole: member.memberRole || member.member_role || 'MEMBER',
});

const capabilityId = (item: any) => String(item?.skillId || item?.id || item?.name || '').trim();

const effectiveProjectSkills = (
  project: OpsProjectWorkspace,
  capabilityData?: ProjectWorkspaceCapabilityData,
) => {
  const projected = [...(project.projectSkills || []), ...(project.enabledGlobalSkills || [])];
  if (projected.length > 0) return projected;
  return capabilityData?.projectSkills || [];
};

const projectRoleOptions = [
  { value: 'OWNER', label: '负责人' },
  { value: 'APPROVER', label: '审批人' },
  { value: 'REVIEWER', label: '审核人' },
  { value: 'OPERATOR', label: '操作员' },
  { value: 'MAINTAINER', label: '维护者' },
  { value: 'OPS_LEAD', label: '运维负责人' },
  { value: 'ADMIN', label: '项目管理员' },
  { value: 'BREAK_GLASS_ADMIN', label: '紧急管理员' },
  { value: 'MEMBER', label: '成员' },
  { value: 'VIEWER', label: '只读成员' },
] as const;

const roleLabel = (role: string) => projectRoleOptions.find((item) => item.value === role)?.label || '成员';

const runtimeCount = (enabled: boolean | undefined, count: number) => {
  if (enabled === false) return '未启用';
  if (enabled === undefined) return '-';
  return String(count);
};

export const ProjectProductWorkspacePage: React.FC = () => {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const scope = useProjectScope();
  const location = useLocation();
  const snapshotQuery = useProjectWorkspaceSnapshotQuery();
  const projectId = scope.projectId;
  const project = snapshotQuery.data?.projects.find((item) => item.projectId === projectId);
  const reloadProjects = async () => {
    await scope.reloadProjects();
    await snapshotQuery.refetch();
  };
  const capabilitiesQuery = useProjectWorkspaceCapabilitiesQuery(projectId);
  const runtimeQuery = useProjectWorkspaceRuntimeQuery(projectId);
  const [workflows, setWorkflows] = useState<OpsAgentDefinition[]>([]);
  const [memberModalVisible, setMemberModalVisible] = useState(false);
  const [memberDrafts, setMemberDrafts] = useState<Array<{ userId: string; username: string; memberRole: string }>>([]);
  const [memberSaving, setMemberSaving] = useState(false);
  const [createVisible, setCreateVisible] = useState(false);
  const [createSaving, setCreateSaving] = useState(false);
  const [projectName, setProjectName] = useState('');
  const [projectKey, setProjectKey] = useState('');

  const view = projectProductViewFor(searchParams.get('view'), location.pathname);

  useEffect(() => {
    if (!projectId) { setWorkflows([]); return; }
    let active = true;
    opsAdminService.listAgents(projectId)
      .then((response) => { if (active) setWorkflows((response.data || []).filter((workflow) => String(workflow.lifecycle || '').toUpperCase() === 'PUBLISHED')); })
      .catch(() => { if (active) setWorkflows([]); });
    return () => { active = false; };
  }, [projectId]);

  const capabilityData = capabilitiesQuery.data;
  const runtimeData = runtimeQuery.data;
  const projectSkills = useMemo(
    () => effectiveProjectSkills(project || ({} as OpsProjectWorkspace), capabilityData),
    [capabilityData, project],
  );
  const projectSkillCount = useMemo(
    () => new Set(projectSkills.map(capabilityId).filter(Boolean)).size,
    [projectSkills],
  );
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

  const openMembers = () => { setMemberDrafts(members); setMemberModalVisible(true); };
  const createProject = async () => {
    if (!projectName.trim() || !/^[a-z][a-z0-9-]{2,63}$/.test(projectKey.trim())) {
      Toast.error('请填写项目名称，以及 3–64 位小写字母、数字或连字符组成的项目标识（以字母开头）。');
      return;
    }
    if (scope.projects.some((item) => item.projectId === projectKey.trim())) {
      Toast.error('项目标识已存在，请使用其他标识。');
      return;
    }
    setCreateSaving(true);
    try {
      const response = await opsProjectService.createProject({
        name: projectName.trim(), projectId: projectKey.trim(), owner: getStoredUserInfo().username,
        environments: ['dev', 'test'],
      });
      await reloadProjects();
      if (response.data?.projectId) scope.selectProject(response.data.projectId);
      setCreateVisible(false);
      setProjectName('');
      setProjectKey('');
      Toast.success('项目已创建，可以配置资源和成员。');
    } catch (error) {
      Toast.error(userFacingError(error, '创建项目失败，请稍后重试。'));
    } finally {
      setCreateSaving(false);
    }
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
    <OpsPageShell selectedKey="projects" maxWidth="none" padding="0">
      <Workspace>
        <ProjectRail>
          <RailHeader>
            <h1>项目</h1>
            <Space spacing={2}>
              {isAdminUser() && <Button theme="borderless" size="small" icon={<IconPlus />} aria-label="创建项目" onClick={() => setCreateVisible(true)} />}
              <Button theme="borderless" size="small" icon={<IconRefresh />} aria-label="刷新项目" onClick={() => void reloadProjects()} />
            </Space>
          </RailHeader>
          <ProjectList>
            {scope.projects.map((item) => (
              <ProjectButton key={item.projectId} type="button" $active={item.projectId === scope.projectId} onClick={() => scope.selectProject(item.projectId)}>
                <span className="icon"><IconFolder /></span>
                <span>
                  <strong>{item.name}</strong>
                  <span>{snapshotQuery.isError ? '详情暂时不可用' : !snapshotQuery.data ? '正在读取详情…' : snapshotQuery.data.projects.find((entry) => entry.projectId === item.projectId)?.readyForInvestigation ? '可进行排查' : '配置未完成'}</span>
                </span>
              </ProjectButton>
            ))}
            {!scope.loading && scope.projects.length === 0 && <Text type="tertiary" style={{ display: 'block', padding: 12 }}>当前没有可用项目。</Text>}
          </ProjectList>
        </ProjectRail>

        <ProjectMain>
          <Spin spinning={scope.loading || snapshotQuery.isFetching || capabilitiesQuery.isFetching || runtimeQuery.isFetching}>
            {!project ? (
              <div role={snapshotQuery.isError ? 'alert' : 'status'} style={{ padding: 32 }}>
                <Text type="tertiary">{snapshotQuery.isError ? '项目详情暂时无法读取，请重试。' : snapshotQuery.isPending ? '正在读取项目详情…' : '选择一个项目查看资源与运行配置。'}</Text>
                {snapshotQuery.isError && <Button onClick={() => void snapshotQuery.refetch()}>重新加载项目</Button>}
              </div>
            ) : (
              <>
                <ProjectHeader>
                  <ProjectIdentity>
                    <span className="eyebrow">PROJECT SPACE</span>
                    <h2>{project.name}</h2>
                    <p>{project.description || '暂无项目说明。'}</p>
                    <ProjectMeta>
                      <Tag color={project.readyForInvestigation ? 'green' : 'orange'}>{project.readyForInvestigation ? '可进行排查' : '配置未完成'}</Tag>
                      <Tag>负责人：{project.owner || '-'}</Tag>
                      {(project.environments || []).map((environment) => <Tag key={environment}>{environment}</Tag>)}
                      <Tag color="blue">默认助手：{project.defaultAgentPublished ? '已就绪' : project.defaultAgentId ? '待发布' : '未配置'}</Tag>
                    </ProjectMeta>
                  </ProjectIdentity>
                  {project.readyForInvestigation && <Button theme="solid" type="primary" icon={<IconBranch />} onClick={() => navigate(`/chat${projectQuery}`)}>打开对话</Button>}
                </ProjectHeader>

                <TabsWrap>
                  <Tabs activeKey={view} onChange={setView} type="line">
                    <TabPane tab="概览" itemKey="overview">
                      <Body>
                        <OverviewLead><strong>项目决定 Chat 和工作流能看到、能使用什么</strong><p>资源、模型、知识库、技能、工具、成员和执行目标都按项目隔离。进入一个项目后，对话与工作流只会使用这个项目已经连接并允许使用的能力。</p></OverviewLead>
                        <StatGrid>
                          {metric('资源', project.resourceCount || 0, '数据与服务资源')}
                          {metric('MCP 工具', project.generatedMcpCount || 0, '生成或接入的工具能力')}
                          {metric('成员', members.length, '项目内平台用户')}
                          {metric('技能', projectSkillCount, '智能执行可用技能')}
                          {metric('知识库', project.knowledgeBaseIds?.length || (project.knowledgeBaseId ? 1 : 0), '项目已授权知识库')}
                          {metric('已发布工作流', workflows.length, '可复用执行流程')}
                        </StatGrid>
                      </Body>
                    </TabPane>

                    <TabPane tab="资源" itemKey="resources">
                      <Body>
                        <StatGrid>
                          {metric('数据资源', project.dataResourceCount || 0, '指标、日志、数据库、API 等证据来源')}
                          {metric('代码仓库', project.sourceRepositoryCount || 0, '项目边界内代码仓库')}
                          {metric('执行资源', project.executionResourceCount || 0, '受治理落地目标')}
                        </StatGrid>
                        <OverviewLead style={{ marginTop: 14, marginBottom: 0 }}><strong>资源授权按项目生效</strong><p>这里只汇总当前项目已连接的资源，具体访问范围继续由项目权限与工具策略限制。</p></OverviewLead>
                      </Body>
                    </TabPane>

                    <TabPane tab="能力" itemKey="capabilities">
                      <Body>
                        <CapabilityGrid>
                          <CapabilityPanel><h3>默认助手</h3><Stack><Tag color={project.defaultAgentPublished ? 'green' : 'orange'}>{project.defaultAgentPublished ? '已就绪' : '未就绪'}</Tag><Text type="tertiary">{project.defaultAgentId ? 'Chat 默认使用它处理开放式问题和排查任务。' : '尚未配置默认助手。'}</Text></Stack></CapabilityPanel>
                          <CapabilityPanel><h3>工具 / MCP · {project.generatedMcpCount || 0}</h3><Text type="tertiary">Chat 和工作流在当前项目中可以调用的工具。</Text></CapabilityPanel>
                          <CapabilityPanel><h3>知识库 · {capabilityData?.knowledgeBases?.length || 0}</h3><Text type="tertiary" style={{ display: 'block', marginBottom: 8 }}>当前项目可检索的知识库。</Text><Space wrap>{(capabilityData?.knowledgeBases || []).map((item: any) => <Tag key={item.kbId || item.knowledgeBaseId || item.name}>{item.kbName || item.name || item.kbId}</Tag>)}</Space></CapabilityPanel>
                          <CapabilityPanel><h3>技能 · {projectSkillCount}</h3><Text type="tertiary" style={{ display: 'block', marginBottom: 8 }}>默认助手和工作流可以按需加载的技能。</Text><Space wrap>{projectSkills.map((item: any) => <Tag key={capabilityId(item)}>{item.name || item.skillName || item.skillId}</Tag>)}</Space>
                            {isAdminUser() && <ProjectSkillGovernancePanel key={projectId} projectId={projectId} onChanged={scope.reloadProjects} />}
                          </CapabilityPanel>
                          <CapabilityPanel><h3>可用工作流 · {workflows.length}</h3><Text type="tertiary" style={{ display: 'block', marginBottom: 8 }}>可以从 Chat 或自动化入口启动的已发布工作流。</Text><Space wrap>{workflows.map((workflow: any) => <Tag key={workflow.agentId || workflow.id}>{workflow.name || workflow.agentName || workflow.agentId}</Tag>)}</Space></CapabilityPanel>
                        </CapabilityGrid>
                      </Body>
                    </TabPane>

                    <TabPane tab="成员" itemKey="members">
                      <Body>
                        <MemberPanel>
                          <MemberHeader><strong>项目成员</strong><Button size="small" onClick={openMembers}>管理成员</Button></MemberHeader>
                          {members.map((member) => <MemberItem key={member.userId || member.username}><Text strong>{member.username || member.userId}</Text><Tag>{roleLabel(member.memberRole)}</Tag></MemberItem>)}
                          {members.length === 0 && <Text type="tertiary" style={{ display: 'block', padding: 16 }}>当前项目尚未分配用户。</Text>}
                        </MemberPanel>
                      </Body>
                    </TabPane>

                    <TabPane tab="运行配置" itemKey="runtime">
                      <Body>
                        <CapabilityGrid>
                          <CapabilityPanel>
                            <h3>代码仓库 · {runtimeCount(runtimeData?.availability?.repositories, runtimeData?.repositories?.length || 0)}</h3>
                            {runtimeData?.availability?.repositories === false
                              ? <Text type="tertiary">代码仓库能力当前未启用，启用后才会展示项目仓库。</Text>
                              : <Space wrap>{(runtimeData?.repositories || []).map((item: any) => <Tag key={item.repositoryId || item.id || item.name}>{item.name || item.repositoryId || item.path}</Tag>)}</Space>}
                          </CapabilityPanel>
                          <CapabilityPanel>
                            <h3>服务 · {runtimeCount(runtimeData?.availability?.services, runtimeData?.services?.length || 0)}</h3>
                            {runtimeData?.availability?.services === false
                              ? <Text type="tertiary">服务目录能力当前未启用，启用后才会展示项目服务。</Text>
                              : <Space wrap>{(runtimeData?.services || []).map((item: any) => <Tag key={item.serviceId || item.id || item.name}>{item.name || item.serviceId}</Tag>)}</Space>}
                          </CapabilityPanel>
                          <CapabilityPanel>
                            <h3>执行目标 · {runtimeCount(runtimeData?.availability?.executionResources, runtimeData?.executionResources?.length || 0)}</h3>
                            {runtimeData?.availability?.executionResources === false
                              ? <Text type="tertiary">执行目标能力当前未启用，生产操作仍需通过受控变更流程。</Text>
                              : <Space wrap>{(runtimeData?.executionResources || []).map((item: any) => <Tag key={item.resourceId || item.id || item.name}>{item.name || item.resourceId}</Tag>)}</Space>}
                          </CapabilityPanel>
                        </CapabilityGrid>
                        <OverviewLead style={{ marginTop: 14, marginBottom: 0 }}><Space><IconSetting /><div><strong>生产操作继续走受控变更</strong><p>运行配置只是能力清单；生产变更仍必须经过受控变更包、审批、落地与验证。</p></div></Space></OverviewLead>
                      </Body>
                    </TabPane>
                  </Tabs>
                </TabsWrap>
              </>
            )}
          </Spin>
        </ProjectMain>
      </Workspace>

      <Modal title="创建项目" visible={createVisible} confirmLoading={createSaving} okText="创建项目" cancelText="取消"
        onOk={() => void createProject()} onCancel={() => setCreateVisible(false)}>
        <Stack>
          <label>项目名称<Input aria-label="项目名称" value={projectName} onChange={setProjectName} maxLength={80} /></label>
          <label>项目标识<Input aria-label="项目标识" value={projectKey} onChange={setProjectKey} maxLength={64} placeholder="例如 order-service" /></label>
          <Text type="tertiary">项目标识用于资源关联；创建后请在项目中配置资源、能力和成员。</Text>
        </Stack>
      </Modal>

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
          <Select placeholder="添加已有平台用户" filter style={{ width: '100%' }} value={undefined} onChange={(value) => addMember(String(value || ''))} emptyContent="暂无可添加的已启用平台用户。">
            {accounts.filter((account) => !memberDrafts.some((member) => member.userId === account.userId)).map((account) => <Option key={account.userId} value={account.userId}>{account.username}</Option>)}
          </Select>
          {memberDrafts.map((member) => (
            <MemberRow key={member.userId}>
              <div><Text strong>{member.username}</Text><div><Text type="tertiary">{member.userId}</Text></div></div>
              <Select value={member.memberRole} onChange={(value) => setMemberDrafts((current) => current.map((item) => item.userId === member.userId ? { ...item, memberRole: String(value || 'MEMBER') } : item))}>
                {projectRoleOptions.map((option) => <Option key={option.value} value={option.value}>{option.label}</Option>)}
              </Select>
              <Button type="danger" onClick={() => setMemberDrafts((current) => current.filter((item) => item.userId !== member.userId))}>移除</Button>
            </MemberRow>
          ))}
        </Stack>
      </Modal>
    </OpsPageShell>
  );
};
