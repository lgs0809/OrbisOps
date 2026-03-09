import React, { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import styled from 'styled-components';
import {
  Button,
  Card,
  Checkbox,
  Collapse,
  Input,
  Layout,
  Modal,
  Select,
  Space,
  Spin,
  Tag,
  TextArea,
  Toast,
  Typography,
} from '@douyinfe/semi-ui';
import {
  IconBranch,
  IconEdit,
  IconEyeOpened,
  IconPlus,
  IconSetting,
} from '@douyinfe/semi-icons';

import { Header, Sidebar } from '../components/layout';
import { ProjectReadinessCard } from '../components/project-readiness-card';
import { ProjectScopeBar } from '../components/project-scope-bar';
import { SetupNav } from '../components/project-setup-nav';
import { SkillPackageEditor } from '../components/skill/SkillPackageEditor';
import { clearAuthSession } from '../services/auth-session';
import { AdminUserResponseDTO, AdminUserService } from '../services/admin-user-service';
import {
  OpsGeneratedMcp,
  OpsDiagnosticScenario,
  OpsProjectMember,
  OpsProjectResource,
  OpsProjectResourcePermission,
  OpsProjectResourceSchemaObject,
  OpsProjectResourceTemplate,
  OpsProjectWorkspace,
  opsProjectService,
} from '../services/ops-project-service';
import {
  OpsAgentDefinition,
  OpsSkillArtifact,
  OpsSkillReference,
  OpsSkillSummary,
  opsAdminService,
} from '../services/ops-admin-service';
import {
  RagKnowledgeBaseSummary,
  aiClientRagOrderAdminService,
} from '../services/ai-client-rag-order-admin-service';
import {
  AiClientToolMcpResponseDTO,
  aiClientToolMcpAdminService,
} from '../services/ai-client-tool-mcp-admin-service';
import {
  OpsExecutionResource,
  OpsProjectService,
  OpsSourceRepository,
  opsRepairService,
} from '../services/ops-repair-service';
import { theme } from '../styles/theme';
import { generatedId } from '../utils/generated-id';
import { useResponsiveSidebar } from '../hooks/use-responsive-sidebar';

const { Content } = Layout;
const { Text, Title, Paragraph } = Typography;
const { Option } = Select;

const mcpTemplateIdForResource = (type?: string) => {
  const normalized = String(type || 'mysql').toLowerCase().replace(/-/g, '_');
  if (normalized === 'postgres' || normalized === 'pgsql') return 'postgresql-readonly-template';
  if (normalized === 'elk' || normalized === 'es') return 'elasticsearch-readonly-template';
  return `${normalized}-readonly-template`;
};

const PageLayout = styled(Layout)`
  min-height: 100vh;
  background: ${theme.colors.bg.secondary};

  @media (max-width: ${theme.breakpoints.md}) {
    width: 100vw;
    margin-left: 0;
  }
`;

const MainContent = styled.div<{ $collapsed: boolean }>`
  display: flex;
  flex: 1;
  margin-left: ${(props) => (props.$collapsed ? '80px' : '280px')};
  transition: margin-left ${theme.animation.duration.normal} ${theme.animation.easing.cubic};
  min-width: 0;
  background: ${theme.colors.bg.secondary};
`;

const MainColumn = styled.div`
  flex: 1;
  min-width: 0;
  min-height: 100vh;
  display: flex;
  flex-direction: column;
`;

const ContentArea = styled(Content)`
  flex: 1;
  padding: ${theme.spacing.lg};
  min-width: 0;
  background: ${theme.colors.bg.secondary};
  overflow-y: auto;

  @media (max-width: 720px) {
    padding: ${theme.spacing.base};
  }
`;

const Workspace = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${theme.spacing.lg};
  min-height: calc(100vh - 120px);
  width: 100%;
  max-width: 1280px;
  margin: 0 auto;
`;

const HeaderBar = styled.div`
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: ${theme.spacing.base};
  flex-wrap: wrap;
  margin-bottom: ${theme.spacing.lg};
  width: 100%;
  max-width: 1280px;
  margin-left: auto;
  margin-right: auto;
`;

const ResourceGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(280px, 100%), 1fr));
  gap: ${theme.spacing.base};
`;

const SummaryGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(220px, 100%), 1fr));
  gap: ${theme.spacing.base};
  margin-top: ${theme.spacing.base};
`;

const SummaryItem = styled.div`
  border: 1px solid ${theme.colors.border.secondary};
  border-radius: ${theme.borderRadius.base};
  background: #f8fafc;
  padding: ${theme.spacing.base};
`;

const ReadinessGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: ${theme.spacing.base};
  margin-top: ${theme.spacing.base};

  @media (max-width: ${theme.breakpoints.md}) {
    grid-template-columns: minmax(0, 1fr);
  }
`;

const ReadinessCard = styled.div<{ $ready: boolean }>`
  border: 1px solid ${theme.colors.border.secondary};
  border-radius: ${theme.borderRadius.base};
  background: ${theme.colors.bg.secondary};
  padding: ${theme.spacing.base};
  min-width: 0;
`;

const ReadinessCheckRow = styled.div`
  display: grid;
  grid-template-columns: 22px minmax(0, 1fr);
  gap: 8px;
  align-items: start;
  padding: 8px 0;
  border-bottom: 1px solid ${theme.colors.border.secondary};

  &:last-child {
    border-bottom: 0;
  }

  .readiness-detail {
    overflow-wrap: anywhere;
  }
`;

const ScenarioGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(260px, 100%), 1fr));
  gap: ${theme.spacing.base};
  margin-top: ${theme.spacing.base};
`;

const ScenarioItem = styled.div<{ $ready: boolean }>`
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 184px;
  padding: ${theme.spacing.base};
  border: 1px solid ${(props) => (props.$ready ? theme.colors.border.primary : theme.colors.border.secondary)};
  border-radius: ${theme.borderRadius.base};
  background: ${(props) => (props.$ready ? theme.colors.bg.primary : theme.colors.bg.secondary)};

  .scenario-description,
  .scenario-reason {
    overflow-wrap: anywhere;
    line-height: 1.6;
  }

  .scenario-action {
    margin-top: auto;
    padding-top: ${theme.spacing.base};
  }
`;

const ResourceTitle = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: ${theme.spacing.base};
  flex-wrap: wrap;
`;

const SchemaList = styled.div`
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: ${theme.spacing.base};
`;

const SchemaRow = styled.div`
  border: 1px solid ${theme.colors.border.secondary};
  border-radius: ${theme.borderRadius.base};
  padding: 10px 12px;
  background: #f8fafc;
`;

const Section = styled(Card)`
  margin-bottom: ${theme.spacing.lg};

  .semi-card-body {
    padding: ${theme.spacing.lg};
  }
`;

const ModalGrid = styled.div`
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: ${theme.spacing.base};

  @media (max-width: 720px) {
    grid-template-columns: 1fr;
  }
`;

const CheckGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: 8px 12px;
  margin-top: 8px;
`;

const FormField = styled.div`
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
`;

const FieldHint = styled.div`
  color: ${theme.colors.text.tertiary};
  font-size: ${theme.typography.fontSize.xs};
  line-height: 1.5;
`;

const JsonBlock = styled.pre`
  background: #0f172a;
  color: #dbeafe;
  padding: 16px;
  border-radius: ${theme.borderRadius.base};
  overflow: auto;
  max-height: 520px;
  font-size: 12px;
  line-height: 1.5;
`;

const typeColor = (type: string) => {
  if (type === 'mysql' || type === 'postgresql') return 'blue';
  if (type === 'redis') return 'red';
  if (type === 'elasticsearch') return 'orange';
  if (type === 'prometheus') return 'green';
  return 'grey';
};

const statusColor = (status: string) => (status === 'SCANNED' ? 'green' : 'orange');

const schemaSourceLabel = (source?: string) => (source === 'live' ? '真实扫描' : '预览样例');

const credentialLabel = (resource: OpsProjectResource) => {
  if (!resource.credential?.configured) {
    return '凭据未配置';
  }
  const username = resource.credential.username || '已配置';
  const passwordState = resource.credential.passwordMasked ? '密码已配置' : '未配置密码';
  return `凭据：${username} / ${passwordState}`;
};

const multiObjectPermissionFallback = (type?: string) => {
  if (type === 'mysql' || type === 'postgresql') {
    return {
      label: '允许跨表 JOIN',
      description: '开启后可在可见表之间执行 JOIN 或多表查询；关闭时 MCP 只能执行单表查询。',
    };
  }
  if (type === 'redis') {
    return {
      label: '允许多 Key Pattern 关联',
      description: '开启后可一次关联多个 key pattern 做排查；关闭时 MCP 单次只访问一个 key pattern。',
    };
  }
  if (type === 'elasticsearch') {
    return {
      label: '允许跨索引查询',
      description: '开启后可一次查询多个可见 index；关闭时 MCP 单次只查询一个 index。',
    };
  }
  if (type === 'prometheus') {
    return {
      label: '允许多指标关联',
      description: '开启后可在一次诊断里关联多个 metric；关闭时 MCP 单次只查询一个 metric。',
    };
  }
  return {
    label: '允许多对象查询',
    description: '开启后 MCP 可一次访问多个可见对象；关闭时单次只访问一个对象。',
  };
};

const multiObjectPermissionMeta = (
  resource: OpsProjectResource,
  template?: OpsProjectResourceTemplate,
) => resource.permission?.multiObjectPermission || template?.multiObjectPermission || multiObjectPermissionFallback(resource.type);

const toStringList = (value: unknown): string[] => {
  if (Array.isArray(value)) {
    return value.map((item) => String(item)).filter(Boolean);
  }
  if (typeof value === 'string' && value.trim()) {
    return [value.trim()];
  }
  return [];
};

const schemaObjectMeta = (item: OpsProjectResourceSchemaObject) => {
  const candidates = [
    item.comment,
    item.dataType,
    toStringList(item.columns).join(', '),
    toStringList(item.fields).join(', '),
    toStringList(item.labels).join(', '),
    toStringList(item.indexes).join(', '),
  ];
  return candidates.find((value) => value && String(value).trim()) || '暂无字段信息';
};

const toJson = (value: unknown) => JSON.stringify(value, null, 2);

const skillValueOf = (skill: OpsSkillSummary) => {
  const meta = skill as OpsSkillSummary & { skillId?: string };
  return meta.skillId || meta.name || '';
};

const skillScopeOf = (skill?: OpsSkillSummary) =>
  String((skill as (OpsSkillSummary & { scope?: string }) | undefined)?.scope || skill?.frontMatter?.scope || '').toUpperCase();

const skillProjectIdOf = (skill?: OpsSkillSummary) =>
  String((skill as (OpsSkillSummary & { projectId?: string }) | undefined)?.projectId || skill?.frontMatter?.projectId || '');

const projectSkillSourceLabel = (skill: OpsSkillSummary | undefined, projectId: string) => {
  if (!skill) {
    return '引用失效，请重新绑定';
  }
  if (skillScopeOf(skill) === 'PROJECT' || skillProjectIdOf(skill) === projectId) {
    return '项目 Skill';
  }
  return '通用 Skill 引用';
};

const isProjectOwnedSkill = (skill: OpsSkillSummary | undefined, projectId: string) =>
  Boolean(skill && (skillScopeOf(skill) === 'PROJECT' || skillProjectIdOf(skill) === projectId));

const projectKnowledgeFieldEntries = (project?: OpsProjectWorkspace) => {
  if (!project) return [];
  return Object.entries(project as unknown as Record<string, unknown>)
    .filter(([key]) => /knowledge|rag|kb/i.test(key));
};

const projectKnowledgeIds = (project?: OpsProjectWorkspace) => {
  const ids = new Set<string>();
  projectKnowledgeFieldEntries(project).forEach(([, value]) => {
    if (Array.isArray(value)) {
      value.forEach((item) => {
        if (!item) return;
        if (typeof item === 'object') {
          const raw = item as Record<string, unknown>;
          const id = raw.kbId || raw.knowledgeTag || raw.ragId || raw.id;
          if (id) ids.add(String(id));
          return;
        }
        ids.add(String(item));
      });
      return;
    }
    if (value && typeof value === 'object') {
      const raw = value as Record<string, unknown>;
      const id = raw.kbId || raw.knowledgeTag || raw.ragId || raw.id;
      if (id) ids.add(String(id));
      return;
    }
    if (typeof value === 'string' && value.trim()) {
      ids.add(value.trim());
    }
  });
  return Array.from(ids);
};

export const ProjectWorkspacePage: React.FC = () => {
  const navigate = useNavigate();
  const { collapsed, sidebarCollapsed, toggleSidebar, closeMobileSidebar } = useResponsiveSidebar();
  const [loading, setLoading] = useState(false);
  const [projects, setProjects] = useState<OpsProjectWorkspace[]>([]);
  const [templates, setTemplates] = useState<OpsProjectResourceTemplate[]>([]);
  const [selectedProjectId, setSelectedProjectId] = useState('');
  const [projectModalVisible, setProjectModalVisible] = useState(false);
  const [projectSettingsVisible, setProjectSettingsVisible] = useState(false);
  const [resourceModalVisible, setResourceModalVisible] = useState(false);
  const [resourceEditModalVisible, setResourceEditModalVisible] = useState(false);
  const [permissionModalVisible, setPermissionModalVisible] = useState(false);
  const [previewVisible, setPreviewVisible] = useState(false);
  const [previewData, setPreviewData] = useState<unknown>(null);
  const [selectedResource, setSelectedResource] = useState<OpsProjectResource | null>(null);
  const [permissionDraft, setPermissionDraft] = useState<OpsProjectResourcePermission | null>(null);
  const [projectForm, setProjectForm] = useState({ name: '', projectId: '', owner: 'ops', description: '' });
  const [agentOptions, setAgentOptions] = useState<OpsAgentDefinition[]>([]);
  const [skillOptions, setSkillOptions] = useState<OpsSkillSummary[]>([]);
  const [projectSkillDetails, setProjectSkillDetails] = useState<OpsSkillSummary[]>([]);
  const [projectSkillUsageVisible, setProjectSkillUsageVisible] = useState(false);
  const [projectSkillUsageLoading, setProjectSkillUsageLoading] = useState(false);
  const [projectSkillUsageRecords, setProjectSkillUsageRecords] = useState<OpsSkillReference[]>([]);
  const [projectSkillUsageTitle, setProjectSkillUsageTitle] = useState('');
  const [projectKnowledgeDetails, setProjectKnowledgeDetails] = useState<RagKnowledgeBaseSummary[]>([]);
  const [knowledgeOptions, setKnowledgeOptions] = useState<RagKnowledgeBaseSummary[]>([]);
  const [sharedMcpOptions, setSharedMcpOptions] = useState<AiClientToolMcpResponseDTO[]>([]);
  const [repositories, setRepositories] = useState<OpsSourceRepository[]>([]);
  const [projectServices, setProjectServices] = useState<OpsProjectService[]>([]);
  const [executionResources, setExecutionResources] = useState<OpsExecutionResource[]>([]);
  const [repositoryModalVisible, setRepositoryModalVisible] = useState(false);
  const [serviceModalVisible, setServiceModalVisible] = useState(false);
  const [executionResourceModalVisible, setExecutionResourceModalVisible] = useState(false);
  const [repositoryForm, setRepositoryForm] = useState({
    repositoryId: '',
    name: '',
    localPath: '',
    defaultRevision: 'HEAD',
  });
  const [serviceForm, setServiceForm] = useState({
    serviceId: '',
    name: '',
    repositoryId: '',
    modulePath: '.',
    buildProfile: 'MAVEN_VERIFY',
    artifactPath: '',
    deploymentResourceId: '',
    healthUrl: '',
    smokeUrls: '',
  });
  const [executionResourceForm, setExecutionResourceForm] = useState({
    resourceId: '',
    name: '',
    workerId: 'project-local-worker',
    adapter: 'local-java-service',
    environments: 'dev',
    configuration: toJson({
      allowedArtifactRoot: '/absolute/path/to/artifacts',
      releaseRoot: '/absolute/path/to/releases',
      services: {
        'service-id': {
          healthUrl: 'http://127.0.0.1:8080/actuator/health',
          healthTimeoutMs: 90000,
          targets: [{
            artifactTarget: '/absolute/path/to/runtime/app.jar',
            pidFile: '/absolute/path/to/runtime/app.pid',
            logFile: '/absolute/path/to/logs/app.log',
            javaBinary: 'java',
            fixedArgs: ['--server.port=8080'],
            environment: {},
          }],
        },
      },
    }),
    status: 'ENABLED',
  });
  const [projectSettings, setProjectSettings] = useState({
    name: '',
    description: '',
    owner: '',
    knowledgeBaseId: '',
    defaultAgentId: '',
    skillIds: [] as string[],
    sharedMcpIds: [] as string[],
  });
  const [resourceForm, setResourceForm] = useState({
    type: 'mysql',
    name: '',
    environment: 'prod',
    endpoint: '',
    username: '',
    passwordRef: '',
  });
  const [resourceEditForm, setResourceEditForm] = useState({
    resourceId: '',
    name: '',
    environment: 'prod',
    endpoint: '',
    username: '',
    passwordRef: '',
  });
  const [projectSkillModalMode, setProjectSkillModalMode] = useState<'create' | 'copy' | 'edit' | null>(null);
  const [projectSkillForm, setProjectSkillForm] = useState({
    skillId: '',
    globalSkillId: '',
    name: '',
    description: '',
    content: '',
    artifacts: [] as OpsSkillArtifact[],
  });
  const [projectKnowledgeModalVisible, setProjectKnowledgeModalVisible] = useState(false);
  const [editingProjectKnowledgeId, setEditingProjectKnowledgeId] = useState('');
  const [projectKnowledgeForm, setProjectKnowledgeForm] = useState({
    kbId: '',
    kbName: '',
    description: '',
  });
  const [projectDocumentModalVisible, setProjectDocumentModalVisible] = useState(false);
  const [projectDocumentForm, setProjectDocumentForm] = useState({
    kbId: '',
    name: '',
    files: [] as File[],
  });
  const [capabilitySaving, setCapabilitySaving] = useState(false);
  const [projectMembers, setProjectMembers] = useState<OpsProjectMember[]>([]);
  const [accountOptions, setAccountOptions] = useState<AdminUserResponseDTO[]>([]);
  const [memberModalVisible, setMemberModalVisible] = useState(false);
  const [memberDrafts, setMemberDrafts] = useState<Array<{
    userId: string;
    username: string;
    memberRole: string;
  }>>([]);
  const [newAccountForm, setNewAccountForm] = useState({
    username: '',
    password: '',
    userRole: 'user',
  });

  const selectedProject = useMemo(
    () => projects.find((item) => item.projectId === selectedProjectId) || projects[0],
    [projects, selectedProjectId],
  );
  const skillOptionMap = useMemo(() => {
    const entries = [...skillOptions, ...projectSkillDetails].flatMap((skill) => {
      const value = skillValueOf(skill);
      return value ? [[value, skill] as const] : [];
    });
    return new Map(entries);
  }, [projectSkillDetails, skillOptions]);
  const selectedProjectSkillIds = useMemo(() => {
    const ids = new Set<string>();
    (selectedProject?.skillIds || []).forEach((skillId) => {
      if (skillId) ids.add(String(skillId));
    });
    projectSkillDetails.forEach((skill) => {
      const skillId = skillValueOf(skill);
      if (skillId) ids.add(skillId);
    });
    return Array.from(ids);
  }, [projectSkillDetails, selectedProject?.skillIds]);
  const selectedProjectKnowledgeIds = useMemo(
    () => {
      const ids = new Set(projectKnowledgeIds(selectedProject));
      projectKnowledgeDetails.forEach((knowledge) => {
        const id = knowledge.kbId || knowledge.knowledgeTag || knowledge.ragId;
        if (id) ids.add(String(id));
      });
      return Array.from(ids);
    },
    [projectKnowledgeDetails, selectedProject],
  );
  const selectedProjectKnowledgeFields = useMemo(
    () => projectKnowledgeFieldEntries(selectedProject),
    [selectedProject],
  );

  const loadSnapshot = async () => {
    setLoading(true);
    try {
      const response = await opsProjectService.snapshot();
      const nextProjects = response.data?.projects || [];
      setProjects(nextProjects);
      setTemplates(response.data?.templates || []);
      if (!selectedProjectId && nextProjects.length) {
        setSelectedProjectId(nextProjects[0].projectId);
      }
    } catch (error) {
      Toast.error('加载业务系统空间失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadSnapshot();
  }, []);

  useEffect(() => {
    if (!selectedProject?.projectId) {
      setProjectSkillDetails([]);
      setProjectKnowledgeDetails([]);
      setProjectMembers([]);
      return;
    }
    let canceled = false;
    Promise.all([
      opsAdminService.listProjectSkills(selectedProject.projectId),
      opsAdminService.listGlobalSkills(),
      aiClientRagOrderAdminService.listAuthorizedKnowledgeBases(selectedProject.projectId),
      opsProjectService.listProjectMembers(selectedProject.projectId),
      AdminUserService.queryEnabledUsers(),
    ])
      .then(([skillResponse, globalSkillResponse, knowledgeResponse, memberResponse, accountResponse]) => {
        if (!canceled) {
          setProjectSkillDetails(skillResponse.data || []);
          setSkillOptions(globalSkillResponse.data || []);
          setProjectKnowledgeDetails(knowledgeResponse.data || []);
          setProjectMembers(memberResponse.data || []);
          setAccountOptions(accountResponse || []);
        }
      })
      .catch(() => {
        if (!canceled) {
          setProjectSkillDetails([]);
          setSkillOptions([]);
          setProjectKnowledgeDetails([]);
          setProjectMembers([]);
        }
      });
    return () => {
      canceled = true;
    };
  }, [selectedProject?.projectId]);

  useEffect(() => {
    if (!selectedProjectId) {
      setRepositories([]);
      setProjectServices([]);
      setExecutionResources([]);
      return;
    }
    Promise.all([
      opsRepairService.listRepositories(selectedProjectId),
      opsRepairService.listServices(selectedProjectId),
      opsRepairService.listExecutionResources(selectedProjectId),
    ]).then(([repositoryResponse, serviceResponse, executionResourceResponse]) => {
      setRepositories(repositoryResponse.data || []);
      setProjectServices(serviceResponse.data || []);
      setExecutionResources(executionResourceResponse.data || []);
    }).catch(() => {
      setRepositories([]);
      setProjectServices([]);
      setExecutionResources([]);
    });
  }, [selectedProjectId]);

  const handleNavigation = (path: string) => {
    navigate(path.startsWith('/') ? path : `/${path}`);
  };

  const handleLogout = () => {
    clearAuthSession();
    navigate('/login');
  };

  const createProject = async () => {
    try {
      const payload = {
        ...projectForm,
        projectId: projectForm.projectId || generatedId('project', projectForm.name),
      };
      const response = await opsProjectService.createProject(payload);
      await loadSnapshot();
      setSelectedProjectId(response.data.projectId);
      setProjectModalVisible(false);
      setProjectForm({ name: '', projectId: '', owner: 'ops', description: '' });
      Toast.success('业务系统已创建');
    } catch (error) {
      Toast.error('创建业务系统失败');
    }
  };

  const openProjectSettings = async () => {
    if (!selectedProject) return;
    try {
      const [agents, skills, knowledgeBases, sharedMcps] = await Promise.all([
        opsAdminService.listAgents(selectedProject.projectId),
        opsAdminService.listGlobalSkills(),
        Promise.all([
          aiClientRagOrderAdminService.listAuthorizedKnowledgeBases(selectedProject.projectId),
          aiClientRagOrderAdminService.listGlobalKnowledgeBases(),
        ]),
        aiClientToolMcpAdminService.queryEnabledAiClientToolMcps(),
      ]);
      setAgentOptions(agents.data || []);
      setSkillOptions(skills.data || []);
      const knowledgeMap = new Map<string, RagKnowledgeBaseSummary>();
      [...(knowledgeBases[0].data || []), ...(knowledgeBases[1].data || [])].forEach((knowledge) => {
        const id = knowledge.kbId || knowledge.knowledgeTag || knowledge.ragId;
        if (id) knowledgeMap.set(String(id), knowledge);
      });
      setKnowledgeOptions(Array.from(knowledgeMap.values()));
      setSharedMcpOptions(sharedMcps.data || []);
      setProjectSettings({
        name: selectedProject.name,
        description: selectedProject.description,
        owner: selectedProject.owner,
        knowledgeBaseId: selectedProject.knowledgeBaseId || '',
        defaultAgentId: selectedProject.defaultAgentId || '',
        skillIds: selectedProjectSkillIds,
        sharedMcpIds: selectedProject.sharedMcpIds || [],
      });
      setProjectSettingsVisible(true);
    } catch (error) {
      Toast.error('加载业务系统能力配置失败');
    }
  };

  const saveProjectSettings = async () => {
    if (!selectedProject) return;
    try {
      if (projectSettings.knowledgeBaseId && !selectedProjectKnowledgeIds.includes(projectSettings.knowledgeBaseId)) {
        await aiClientRagOrderAdminService.enableGlobalKnowledgeBaseForProject(
          selectedProject.projectId,
          projectSettings.knowledgeBaseId,
        );
      }
      const response = await opsProjectService.updateProject({
        projectId: selectedProject.projectId,
        ...projectSettings,
      });
      setProjects((items) => items.map((item) => (
        item.projectId === response.data.projectId ? response.data : item
      )));
      setProjectSettingsVisible(false);
      Toast.success('业务系统能力配置已更新');
    } catch (error) {
      Toast.error('更新业务系统能力配置失败');
    }
  };

  const ensureDefaultAgent = async () => {
    if (!selectedProject) return;
    setCapabilitySaving(true);
    try {
      const response = await opsProjectService.ensureDefaultAgent(selectedProject.projectId);
      if (response.code !== '0000') {
        throw new Error(response.info || '修复默认 Agent 失败');
      }
      setProjects((items) => items.map((item) => (
        item.projectId === response.data.projectId ? response.data : item
      )));
      Toast.success('默认 Agent 已创建并发布，可以开始新会话');
    } catch (error) {
      Toast.error(error instanceof Error ? error.message : '修复默认 Agent 失败');
    } finally {
      setCapabilitySaving(false);
    }
  };

  const reloadProjectSkills = async () => {
    if (!selectedProject) return;
    const response = await opsAdminService.listProjectSkills(selectedProject.projectId);
    setProjectSkillDetails(response.data || []);
  };

  const reloadProjectKnowledgeBases = async () => {
    if (!selectedProject) return;
    const response = await aiClientRagOrderAdminService.listAuthorizedKnowledgeBases(selectedProject.projectId);
    setProjectKnowledgeDetails(response.data || []);
  };

  const openCreateProjectSkill = () => {
    setProjectSkillForm({ skillId: '', globalSkillId: '', name: '', description: '', content: '', artifacts: [] });
    setProjectSkillModalMode('create');
  };

  const openCopyProjectSkill = async () => {
    try {
      const response = await opsAdminService.listGlobalSkills();
      const globals = response.data || [];
      setSkillOptions(globals);
      setProjectSkillForm({
        skillId: '',
        globalSkillId: skillValueOf(globals[0]) || '',
        name: '',
        description: '',
        content: '',
        artifacts: [],
      });
      setProjectSkillModalMode('copy');
    } catch (error) {
      Toast.error('加载通用 Skill 失败');
    }
  };

  const openEditProjectSkill = async (skill: OpsSkillSummary | undefined) => {
    if (!selectedProject || !skill || !isProjectOwnedSkill(skill, selectedProject.projectId)) {
      Toast.warning('只能编辑当前项目自有 Skill，通用 Skill 引用需要先复制到项目');
      return;
    }
    const skillId = skillValueOf(skill);
    if (!skillId) {
      Toast.warning('方法编号为空，无法编辑');
      return;
    }
    setCapabilitySaving(true);
    try {
      const response = await opsAdminService.getProjectSkill(selectedProject.projectId, skillId);
      if (response.code !== '0000') {
        throw new Error(response.info || '加载项目 Skill 失败');
      }
      const detail = response.data || skill;
      const artifactResponse = await opsAdminService.listProjectSkillArtifacts(selectedProject.projectId, skillId);
      if (artifactResponse.code !== '0000') {
        throw new Error(artifactResponse.info || '加载项目 Skill 文件失败');
      }
      setProjectSkillForm({
        skillId,
        globalSkillId: '',
        name: detail.name || detail.skillName || skillId,
        description: detail.description || '',
        content: detail.content || detail.markdown || detail.xml || '',
        artifacts: (artifactResponse.data || []).filter((item) => item.path !== 'SKILL.md'),
      });
      setProjectSkillModalMode('edit');
    } catch (error) {
      Toast.error(error instanceof Error ? error.message : '加载项目 Skill 失败');
    } finally {
      setCapabilitySaving(false);
    }
  };

  const saveProjectSkill = async () => {
    if (!selectedProject) return;
    if (!projectSkillModalMode) return;
    if ((projectSkillModalMode === 'create' || projectSkillModalMode === 'edit') && !projectSkillForm.name.trim()) {
      Toast.warning('Skill 名称不能为空');
      return;
    }
    if (projectSkillModalMode === 'copy' && !projectSkillForm.globalSkillId) {
      Toast.warning('请选择通用 Skill');
      return;
    }
    setCapabilitySaving(true);
    try {
      const response = projectSkillModalMode === 'edit'
        ? await opsAdminService.updateProjectSkill(selectedProject.projectId, projectSkillForm.skillId.trim(), {
          name: projectSkillForm.name.trim(),
          description: projectSkillForm.description,
          content: projectSkillForm.content,
          artifacts: projectSkillForm.artifacts,
          evalSuites: projectSkillForm.artifacts.filter((item) => item.role === 'EVAL').map((item) => item.path),
        })
        : projectSkillModalMode === 'copy'
          ? await opsAdminService.copyGlobalSkillToProject(selectedProject.projectId, {
          globalSkillId: projectSkillForm.globalSkillId,
          skillId: projectSkillForm.skillId.trim() || generatedId('skill', projectSkillForm.name || projectSkillForm.globalSkillId),
          name: projectSkillForm.name.trim() || undefined,
          description: projectSkillForm.description || undefined,
        })
          : await opsAdminService.createProjectSkill(selectedProject.projectId, {
          skillId: projectSkillForm.skillId.trim() || generatedId('skill', projectSkillForm.name),
          name: projectSkillForm.name.trim(),
          description: projectSkillForm.description,
          content: projectSkillForm.content,
          status: 'ENABLED',
          artifacts: projectSkillForm.artifacts,
          evalSuites: projectSkillForm.artifacts.filter((item) => item.role === 'EVAL').map((item) => item.path),
        });
      if (response.code !== '0000') {
        throw new Error(response.info || '保存项目 Skill 失败');
      }
      await reloadProjectSkills();
      setProjectSkillModalMode(null);
      Toast.success(projectSkillModalMode === 'edit'
        ? '项目 Skill 已更新'
        : projectSkillModalMode === 'copy'
          ? '通用 Skill 已复制到项目'
          : '项目 Skill 已创建');
    } catch (error) {
      Toast.error(error instanceof Error ? error.message : '保存项目 Skill 失败');
    } finally {
      setCapabilitySaving(false);
    }
  };

  const updateProjectSkillStatus = async (skill: OpsSkillSummary | undefined, nextStatus: 'ENABLED' | 'DISABLED') => {
    if (!selectedProject || !skill || !isProjectOwnedSkill(skill, selectedProject.projectId)) {
      Toast.warning('只能启停当前项目自有 Skill，通用 Skill 引用不在这里修改');
      return;
    }
    const skillId = skillValueOf(skill);
    if (!skillId) {
      Toast.warning('方法编号为空，无法更新状态');
      return;
    }
    if (nextStatus === 'DISABLED' && !window.confirm(`确认停用项目 Skill ${skillId}？绑定该 Skill 的 Agent 将无法继续加载它。`)) {
      return;
    }
    setCapabilitySaving(true);
    try {
      const response = await opsAdminService.updateProjectSkillStatus(selectedProject.projectId, skillId, nextStatus);
      if (response.code !== '0000') {
        throw new Error(response.info || '更新项目 Skill 状态失败');
      }
      await reloadProjectSkills();
      Toast.success(nextStatus === 'ENABLED' ? '项目 Skill 已启用' : '项目 Skill 已停用');
    } catch (error) {
      Toast.error(error instanceof Error ? error.message : '更新项目 Skill 状态失败');
    } finally {
      setCapabilitySaving(false);
    }
  };

  const openProjectSkillUsage = async (skill: OpsSkillSummary | undefined) => {
    if (!selectedProject || !skill) {
      return;
    }
    const skillId = skillValueOf(skill);
    if (!skillId) {
      Toast.warning('方法编号为空，无法查询使用记录');
      return;
    }
    setProjectSkillUsageTitle(skill.name || skill.skillName || skillId);
    setProjectSkillUsageVisible(true);
    setProjectSkillUsageLoading(true);
    try {
      const response = await opsAdminService.listProjectSkillUsage(selectedProject.projectId, skillId);
      if (response.code !== '0000') {
        throw new Error(response.info || '查询项目 Skill 使用记录失败');
      }
      setProjectSkillUsageRecords(response.data || []);
    } catch (error) {
      setProjectSkillUsageRecords([]);
      Toast.error(error instanceof Error ? error.message : '查询项目 Skill 使用记录失败');
    } finally {
      setProjectSkillUsageLoading(false);
    }
  };

  const openCreateProjectKnowledge = () => {
    setEditingProjectKnowledgeId('');
    setProjectKnowledgeForm({ kbId: '', kbName: '', description: '' });
    setProjectKnowledgeModalVisible(true);
  };

  const openEditProjectKnowledge = (knowledge: RagKnowledgeBaseSummary) => {
    const kbId = String(knowledge.kbId || knowledge.knowledgeTag || knowledge.ragId || '');
    setEditingProjectKnowledgeId(kbId);
    setProjectKnowledgeForm({
      kbId,
      kbName: String(knowledge.kbName || knowledge.ragName || knowledge.name || kbId),
      description: knowledge.description || '',
    });
    setProjectKnowledgeModalVisible(true);
  };

  const saveProjectKnowledgeBase = async () => {
    if (!selectedProject || !projectKnowledgeForm.kbName.trim()) {
      Toast.warning('知识库名称不能为空');
      return;
    }
    setCapabilitySaving(true);
    try {
      const kbId = projectKnowledgeForm.kbId.trim() || generatedId('kb', projectKnowledgeForm.kbName);
      const payload = {
        kbId,
        knowledgeTag: kbId,
        kbName: projectKnowledgeForm.kbName.trim(),
        description: projectKnowledgeForm.description,
        status: 'ENABLED',
      };
      const response = editingProjectKnowledgeId
        ? await aiClientRagOrderAdminService.updateProjectKnowledgeBase(
          selectedProject.projectId,
          editingProjectKnowledgeId,
          payload,
        )
        : await aiClientRagOrderAdminService.createProjectKnowledgeBase(
          selectedProject.projectId,
          payload,
        );
      if (response.code !== '0000') {
        throw new Error(response.info || '保存项目知识库失败');
      }
      await reloadProjectKnowledgeBases();
      setProjectKnowledgeModalVisible(false);
      setEditingProjectKnowledgeId('');
      setProjectKnowledgeForm({ kbId: '', kbName: '', description: '' });
      Toast.success(editingProjectKnowledgeId ? '项目知识库已更新' : '项目知识库已创建');
    } catch (error) {
      Toast.error(error instanceof Error ? error.message : '保存项目知识库失败');
    } finally {
      setCapabilitySaving(false);
    }
  };

  const toggleProjectKnowledgeStatus = async (knowledge: RagKnowledgeBaseSummary) => {
    if (!selectedProject) return;
    const kbId = String(knowledge.kbId || knowledge.knowledgeTag || knowledge.ragId || '');
    const enabled = String(knowledge.status || '').toUpperCase() === 'ENABLED' || knowledge.status === 1;
    const nextStatus = enabled ? 'DISABLED' : 'ENABLED';
    if (nextStatus === 'DISABLED' && !window.confirm(`确认停用项目知识库 ${kbId}？绑定该知识库的 Agent 将无法继续读取。`)) {
      return;
    }
    setCapabilitySaving(true);
    try {
      const response = await aiClientRagOrderAdminService.updateProjectKnowledgeBaseStatus(
        selectedProject.projectId,
        kbId,
        nextStatus,
      );
      if (response.code !== '0000') {
        throw new Error(response.info || '更新项目知识库状态失败');
      }
      await reloadProjectKnowledgeBases();
      Toast.success(nextStatus === 'ENABLED' ? '项目知识库已启用' : '项目知识库已停用');
    } catch (error) {
      Toast.error(error instanceof Error ? error.message : '更新项目知识库状态失败');
    } finally {
      setCapabilitySaving(false);
    }
  };

  const uploadProjectDocuments = async () => {
    if (!selectedProject || !projectDocumentForm.kbId || !projectDocumentForm.name.trim()
      || !projectDocumentForm.files.length) {
      Toast.warning('请选择知识库、填写文档批次名称并选择文件');
      return;
    }
    setCapabilitySaving(true);
    try {
      const response = await aiClientRagOrderAdminService.uploadProjectKnowledgeDocuments(
        selectedProject.projectId,
        projectDocumentForm.kbId,
        projectDocumentForm.name.trim(),
        projectDocumentForm.files,
      );
      if (response.code !== '0000') {
        throw new Error(response.info || '导入项目文档失败');
      }
      setProjectDocumentModalVisible(false);
      setProjectDocumentForm({ kbId: '', name: '', files: [] });
      Toast.success(`文档入库任务已创建：${response.data?.jobId || ''}`);
    } catch (error) {
      Toast.error(error instanceof Error ? error.message : '导入项目文档失败');
    } finally {
      setCapabilitySaving(false);
    }
  };

  const openMemberManagement = () => {
    setMemberDrafts(projectMembers.map((member) => ({
      userId: String(member.userId || member.user_id || ''),
      username: String(member.username || ''),
      memberRole: String(member.memberRole || member.member_role || 'MEMBER'),
    })));
    setNewAccountForm({ username: '', password: '', userRole: 'user' });
    setMemberModalVisible(true);
  };

  const selectMemberAccount = (index: number, accountKey: string) => {
    const account = accountOptions.find((item) => `${item.userId || ''}::${item.username}` === accountKey);
    if (!account) return;
    setMemberDrafts((current) => current.map((item, itemIndex) => (
      itemIndex === index
        ? { ...item, userId: account.userId || '', username: account.username || '' }
        : item
    )));
  };

  const createPlatformAccount = async () => {
    if (!newAccountForm.username.trim() || !newAccountForm.password.trim()) {
      Toast.warning('请填写账号和初始密码');
      return;
    }
    setCapabilitySaving(true);
    try {
      await AdminUserService.createUser({
        userId: generatedId('user', newAccountForm.username),
        username: newAccountForm.username.trim(),
        password: newAccountForm.password,
        userRole: newAccountForm.userRole,
        status: 1,
      });
      const accounts = await AdminUserService.queryEnabledUsers();
      setAccountOptions(accounts);
      const created = accounts.find((item) => item.username === newAccountForm.username.trim());
      if (created) {
        setMemberDrafts((current) => [
          ...current,
          { userId: created.userId || '', username: created.username, memberRole: 'MEMBER' },
        ]);
      }
      setNewAccountForm({ username: '', password: '', userRole: 'user' });
      Toast.success('平台账号已创建，并加入成员候选');
    } catch (error) {
      Toast.error(error instanceof Error ? error.message : '创建平台账号失败');
    } finally {
      setCapabilitySaving(false);
    }
  };

  const saveProjectMembers = async () => {
    if (!selectedProject) return;
    setCapabilitySaving(true);
    try {
      const response = await opsProjectService.replaceProjectMembers(
        selectedProject.projectId,
        memberDrafts.map((member) => ({
          userId: member.userId.trim() || undefined,
          username: member.username.trim() || undefined,
          memberRole: member.memberRole,
        })),
      );
      if (response.code !== '0000') {
        throw new Error(response.info || '保存项目成员失败');
      }
      setProjectMembers(response.data || []);
      setMemberModalVisible(false);
      Toast.success('项目成员授权已更新');
    } catch (error) {
      Toast.error(error instanceof Error ? error.message : '保存项目成员失败');
    } finally {
      setCapabilitySaving(false);
    }
  };

  const addResource = async () => {
    if (!selectedProject) return;
    try {
      const response = await opsProjectService.addResource({
        ...resourceForm,
        projectId: selectedProject.projectId,
      });
      setProjects((items) => items.map((item) => (item.projectId === response.data.projectId ? response.data : item)));
      setResourceModalVisible(false);
      setResourceForm({ type: 'mysql', name: '', environment: 'prod', endpoint: '', username: '', passwordRef: '' });
      Toast.success('资源已扫描并加入业务系统');
    } catch (error) {
      Toast.error('添加资源失败');
    }
  };

  const openResourceEdit = (resource: OpsProjectResource) => {
    setSelectedResource(resource);
    setResourceEditForm({
      resourceId: resource.resourceId,
      name: resource.name,
      environment: resource.environment,
      endpoint: resource.endpoint,
      username: resource.credential?.username || '',
      passwordRef: resource.credential?.passwordRef || '',
    });
    setResourceEditModalVisible(true);
  };

  const saveResource = async () => {
    if (!selectedProject || !selectedResource) return;
    try {
      const response = await opsProjectService.updateResource({
        ...resourceEditForm,
        projectId: selectedProject.projectId,
        resourceId: selectedResource.resourceId,
      });
      setProjects((items) => items.map((item) => (item.projectId === response.data.projectId ? response.data : item)));
      setResourceEditModalVisible(false);
      Toast.success('资源配置已更新');
    } catch (error) {
      Toast.error('更新资源配置失败');
    }
  };

  const openPermission = (resource: OpsProjectResource) => {
    setSelectedResource(resource);
    setPermissionDraft({
      ...resource.permission,
      actions: [...(resource.permission?.actions || [])],
      objects: [...(resource.permission?.objects || [])],
    });
    setPermissionModalVisible(true);
  };

  const savePermission = async () => {
    if (!selectedProject || !selectedResource || !permissionDraft) return;
    try {
      const response = await opsProjectService.updatePermission({
        projectId: selectedProject.projectId,
        resourceId: selectedResource.resourceId,
        permission: permissionDraft,
      });
      setProjects((items) => items.map((item) => (item.projectId === response.data.projectId ? response.data : item)));
      setPermissionModalVisible(false);
      Toast.success('权限策略已更新');
    } catch (error) {
      Toast.error('更新权限策略失败');
    }
  };

  const generateMcp = async (resource: OpsProjectResource) => {
    if (!selectedProject) return;
    try {
      const response = await opsProjectService.generateProjectTool(selectedProject.projectId, {
        templateId: mcpTemplateIdForResource(resource.type),
        resourceId: resource.resourceId,
        toolName: `${resource.name || resource.type} readonly MCP`,
        allowedActions: resource.permission?.actions || [],
        permissionPolicy: resource.permission || {},
        riskLevel: 'LOW',
        readOnly: true,
        profile: 'readonly',
      });
      if (response.code !== '0000') {
        throw new Error(response.info || '生成项目工具失败');
      }
      await loadSnapshot();
      Toast.success('项目工具已生成');
    } catch (error) {
      Toast.error(error instanceof Error ? error.message : '生成项目工具失败');
    }
  };

  const toggleDraftValue = (field: 'actions' | 'objects', value: string, checked: boolean) => {
    if (!permissionDraft) return;
    const values = new Set(permissionDraft[field] || []);
    if (checked) {
      values.add(value);
    } else {
      values.delete(value);
    }
    setPermissionDraft({ ...permissionDraft, [field]: Array.from(values) });
  };

  const registerRepository = async () => {
    if (!selectedProject) return;
    try {
      await opsRepairService.registerRepository({
        ...repositoryForm,
        repositoryId: repositoryForm.repositoryId.trim() || generatedId('repo', repositoryForm.name || repositoryForm.localPath),
        projectId: selectedProject.projectId,
      });
      const response = await opsRepairService.listRepositories(selectedProject.projectId);
      setRepositories(response.data || []);
      setRepositoryModalVisible(false);
      setRepositoryForm({ repositoryId: '', name: '', localPath: '', defaultRevision: 'HEAD' });
      Toast.success('代码仓库已登记');
    } catch (error) {
      Toast.error(error instanceof Error ? error.message : '登记代码仓库失败');
    }
  };

  const saveProjectService = async () => {
    if (!selectedProject) return;
    try {
      await opsRepairService.saveService({
        ...serviceForm,
        serviceId: serviceForm.serviceId.trim() || generatedId('service', serviceForm.name),
        projectId: selectedProject.projectId,
        smokeUrls: serviceForm.smokeUrls.split(',').map((item) => item.trim()).filter(Boolean),
      });
      const response = await opsRepairService.listServices(selectedProject.projectId);
      setProjectServices(response.data || []);
      setServiceModalVisible(false);
      setServiceForm({
        serviceId: '',
        name: '',
        repositoryId: repositories[0]?.repositoryId || '',
        modulePath: '.',
        buildProfile: 'MAVEN_VERIFY',
        artifactPath: '',
        deploymentResourceId: '',
        healthUrl: '',
        smokeUrls: '',
      });
      Toast.success('服务目录已保存');
    } catch (error) {
      Toast.error(error instanceof Error ? error.message : '保存服务目录失败');
    }
  };

  const saveExecutionResource = async () => {
    if (!selectedProject) return;
    try {
      const configuration = JSON.parse(executionResourceForm.configuration || '{}');
      const resourceId = executionResourceForm.resourceId.trim() || generatedId('target', executionResourceForm.name || executionResourceForm.adapter);
      await opsRepairService.saveExecutionResource({
        ...executionResourceForm,
        resourceId,
        workerId: executionResourceForm.workerId.trim() || generatedId('worker', `${selectedProject.projectId}-${executionResourceForm.name || executionResourceForm.adapter}`),
        projectId: selectedProject.projectId,
        environments: executionResourceForm.environments
          .split(',')
          .map((item) => item.trim())
          .filter(Boolean),
        configuration,
      });
      const response = await opsRepairService.listExecutionResources(selectedProject.projectId);
      setExecutionResources(response.data || []);
      setExecutionResourceModalVisible(false);
      Toast.success('执行资源已保存，Worker 将自动拉取最新配置');
    } catch (error) {
      Toast.error(error instanceof Error ? error.message : '保存执行资源失败');
    }
  };

  const openExecutionResource = (resource?: OpsExecutionResource) => {
    if (resource) {
      setExecutionResourceForm({
        resourceId: resource.resourceId,
        name: resource.name,
        workerId: resource.workerId,
        adapter: resource.adapter,
        environments: (resource.environments || []).join(','),
        configuration: toJson(resource.configuration || {}),
        status: resource.status,
      });
    } else {
      setExecutionResourceForm({
        resourceId: '',
        name: '',
        workerId: 'project-local-worker',
        adapter: 'local-java-service',
        environments: 'dev',
        configuration: toJson({
          allowedArtifactRoot: '/absolute/path/to/artifacts',
          releaseRoot: '/absolute/path/to/releases',
          services: {
            'service-id': {
              healthUrl: 'http://127.0.0.1:8080/actuator/health',
              healthTimeoutMs: 90000,
              targets: [{
                artifactTarget: '/absolute/path/to/runtime/app.jar',
                pidFile: '/absolute/path/to/runtime/app.pid',
                logFile: '/absolute/path/to/logs/app.log',
                javaBinary: 'java',
                fixedArgs: ['--server.port=8080'],
                environment: {},
              }],
            },
          },
        }),
        status: 'ENABLED',
      });
    }
    setExecutionResourceModalVisible(true);
  };

  const selectedTemplate = templates.find((item) => item.type === resourceForm.type);

  const startDiagnosticScenario = (scenario: OpsDiagnosticScenario) => {
    if (!selectedProject || !scenario.ready) {
      return;
    }
    const params = new URLSearchParams({
      projectId: selectedProject.projectId,
      scenarioId: scenario.scenarioId,
    });
    navigate(`/chat?${params.toString()}`);
  };

  const configureDiagnosticScenario = (scenario: OpsDiagnosticScenario) => {
    if (!selectedProject?.defaultAgentPublished) {
      void ensureDefaultAgent();
      return;
    }
    if (scenario.scenarioId === 'KNOWLEDGE_QA') {
      void openProjectSettings();
      return;
    }
    if (scenario.scenarioId === 'CODE_INVESTIGATION') {
      setRepositoryModalVisible(true);
      return;
    }
    setResourceModalVisible(true);
  };

  const scrollProjectSetupSection = (title?: string) => {
    if (!title) {
      const firstCard = document.querySelector<HTMLElement>('.semi-spin-children > div > .semi-card');
      firstCard?.scrollIntoView({ behavior: 'smooth', block: 'start' });
      return;
    }
    const candidates = Array.from(
      document.querySelectorAll<HTMLElement>('.semi-card-header-title, .semi-typography'),
    );
    const titleNode = candidates.find((node) => node.textContent?.trim() === title);
    const card = titleNode?.closest<HTMLElement>('.semi-card');
    if (card) {
      card.scrollIntoView({ behavior: 'smooth', block: 'start' });
      return;
    }
    const firstCard = document.querySelector<HTMLElement>('.semi-spin-children > div > .semi-card');
    firstCard?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  };

  return (
    <PageLayout>
      <Sidebar selectedKey="project-workspace" onSelect={handleNavigation} collapsed={sidebarCollapsed} onMobileClose={closeMobileSidebar} />
      <MainContent $collapsed={collapsed}>
        <MainColumn>
          <Header
            onToggleSidebar={toggleSidebar}
            onLogout={handleLogout}
          />
          <ContentArea>
            <HeaderBar>
              <div>
                <Title heading={3} style={{ margin: 0 }}>项目</Title>
                <Paragraph type="tertiary" style={{ marginTop: 6 }}>
                  项目是平台的数据与权限边界。Agent、巡检、变更和审计均在选定项目内工作。
                </Paragraph>
              </div>
            </HeaderBar>

            <ProjectScopeBar
              projects={projects}
              projectId={selectedProject?.projectId || selectedProjectId}
              loading={loading}
              onChange={setSelectedProjectId}
              onRefresh={loadSnapshot}
              actions={(
                <Button theme="solid" icon={<IconPlus />} onClick={() => setProjectModalVisible(true)}>
                  新建项目
                </Button>
              )}
            />

            <Spin spinning={loading}>
              <Workspace>
                {selectedProject && (
                  <>
                      <SetupNav aria-label="项目接入配置导航">
                        <Text strong>接入步骤</Text>
                        <Button theme="borderless" size="small" onClick={() => scrollProjectSetupSection()}>概览</Button>
                        <Button theme="borderless" size="small" onClick={() => scrollProjectSetupSection('开始第一次真实诊断')}>真实诊断</Button>
                        <Button theme="borderless" size="small" onClick={() => scrollProjectSetupSection('项目默认能力')}>默认能力</Button>
                        <Button theme="borderless" size="small" onClick={() => scrollProjectSetupSection('项目成员与使用权限')}>成员</Button>
                        <Button theme="borderless" size="small" onClick={() => scrollProjectSetupSection('项目 Skill')}>知识与 Skill</Button>
                        <Button theme="borderless" size="small" onClick={() => scrollProjectSetupSection('代码与服务')}>代码与服务</Button>
                        <Button theme="borderless" size="small" onClick={() => scrollProjectSetupSection('变更执行目标')}>生产处置</Button>
                        <Button theme="borderless" size="small" onClick={() => scrollProjectSetupSection('数据连接与工具')}>数据与工具</Button>
                      </SetupNav>
                      <Section>
                        <ResourceTitle>
                          <div>
                            <Title heading={4} style={{ margin: 0 }}>{selectedProject.name}</Title>
                            <Text type="tertiary">
                              负责人：{selectedProject.owner} · 环境：{toStringList(selectedProject.environments).join(' / ') || '-'}
                            </Text>
                            <div style={{ marginTop: 8 }}>
                              <Space wrap>
                                <Tag color={selectedProject.readyForInvestigation ? 'green' : 'orange'}>
                                  {selectedProject.readyForInvestigation
                                    ? '可开始排障'
                                    : selectedProject.readinessReason === 'DEFAULT_AGENT_NOT_PUBLISHED'
                                      ? '默认 Agent 未发布'
                                      : '待完善接入'}
                                </Tag>
                                <Tag color="blue">默认 Agent：{selectedProject.defaultAgentId || '未设置'}</Tag>
                                <Tag color="green">知识库：{selectedProject.knowledgeBaseId || '-'}</Tag>
                              </Space>
                            </div>
                            <div style={{ marginTop: 8 }}>
                              <Space wrap>
                                {(selectedProject.onboarding || []).map((step) => (
                                  <Tag key={step.key} color={step.completed ? 'green' : 'grey'}>
                                    {step.label}
                                  </Tag>
                                ))}
                              </Space>
                            </div>
                          </div>
                          <Space>
                            <Button onClick={() => navigate(`/agent-list?projectId=${encodeURIComponent(selectedProject.projectId)}`)}>Agent</Button>
                            <Button onClick={() => navigate(`/inspections?projectId=${encodeURIComponent(selectedProject.projectId)}`)}>巡检</Button>
                            <Button onClick={() => navigate(`/alerts?projectId=${encodeURIComponent(selectedProject.projectId)}`)}>告警</Button>
                            <Button onClick={() => navigate(`/executions?projectId=${encodeURIComponent(selectedProject.projectId)}`)}>执行中心</Button>
                            <Button onClick={() => navigate(`/audit?projectId=${encodeURIComponent(selectedProject.projectId)}`)}>审计</Button>
                          </Space>
                        </ResourceTitle>
                        <ReadinessGrid>
                          <ReadinessCard $ready={Boolean(selectedProject.diagnosisReadiness?.ready)}>
                            <Text strong>首次价值路径</Text>
                            {(selectedProject.onboarding || []).map((step) => (
                              <ReadinessCheckRow key={step.key}>
                                <Text>{step.completed ? '✓' : '×'}</Text>
                                <Text>{step.label}</Text>
                              </ReadinessCheckRow>
                            ))}
                            <Paragraph type="tertiary" style={{ marginBottom: 0 }}>
                              首次诊断前不要求配置 Skill Evolver、Landing 或复杂 Workflow。
                            </Paragraph>
                          </ReadinessCard>
                          <ProjectReadinessCard
                            title="诊断能力"
                            readiness={selectedProject.diagnosisReadiness}
                            fallbackAction="完善诊断能力"
                            onAction={() => selectedProject.defaultAgentPublished ? (selectedProject.diagnosisReadiness?.ready ? navigate('/chat') : setResourceModalVisible(true)) : void ensureDefaultAgent()}
                          />
                          <ProjectReadinessCard
                            title="生产处置能力"
                            readiness={selectedProject.remediationReadiness}
                            fallbackAction="配置生产处置能力"
                            onAction={() => selectedProject.executionResourceCount ? navigate('/executions') : openExecutionResource()}
                          />
                        </ReadinessGrid>
                        <SummaryGrid>
                          <SummaryItem>
                            <Text type="tertiary" size="small">数据连接</Text>
                            <Title heading={4} style={{ margin: '4px 0 0' }}>{selectedProject.resourceCount}</Title>
                          </SummaryItem>
                          <SummaryItem>
                            <Text type="tertiary" size="small">已生成工具</Text>
                            <Title heading={4} style={{ margin: '4px 0 0' }}>{selectedProject.generatedMcpCount}</Title>
                          </SummaryItem>
                          <SummaryItem>
                            <Text type="tertiary" size="small">项目 Skill</Text>
                            <Title heading={4} style={{ margin: '4px 0 0' }}>{selectedProject.skillIds?.length || 0}</Title>
                          </SummaryItem>
                          <SummaryItem>
                            <Text type="tertiary" size="small">代码服务</Text>
                            <Title heading={4} style={{ margin: '4px 0 0' }}>{projectServices.length}</Title>
                          </SummaryItem>
                        </SummaryGrid>
                      </Section>

                      <Section title="开始第一次真实诊断">
                        <ResourceTitle>
                          <div style={{ minWidth: 0 }}>
                            <Text strong>选择当前项目已经具备证据来源的场景</Text>
                            <div>
                              <Text type="tertiary" size="small">
                                平台只开放已接入的数据能力。未接入的场景会说明缺少什么，不会给出一个无法运行的入口。
                              </Text>
                            </div>
                          </div>
                          {selectedProject.readyForInvestigation && (
                            <Tag color="green">已可开始诊断</Tag>
                          )}
                        </ResourceTitle>
                        <ScenarioGrid>
                          {(selectedProject.diagnosticScenarios || []).map((scenario) => (
                            <ScenarioItem key={scenario.scenarioId} $ready={scenario.ready} data-testid={`diagnostic-scenario-${scenario.scenarioId}`}>
                              <Space wrap style={{ justifyContent: 'space-between', width: '100%' }}>
                                <Text strong>{scenario.name}</Text>
                                {scenario.scenarioId === selectedProject.recommendedScenarioId && scenario.ready
                                  ? <Tag color="blue">推荐</Tag>
                                  : <Tag color={scenario.ready ? 'green' : 'grey'}>{scenario.ready ? '可用' : '未接入'}</Tag>}
                              </Space>
                              <Text className="scenario-description" type="secondary" style={{ marginTop: 10 }}>
                                {scenario.description}
                              </Text>
                              {!scenario.ready && (
                                <Text className="scenario-reason" type="tertiary" size="small" style={{ marginTop: 8 }}>
                                  还需：{scenario.unavailableReason || '完善项目能力'}
                                </Text>
                              )}
                              <div className="scenario-action">
                                {scenario.ready ? (
                                  <Button theme="solid" onClick={() => startDiagnosticScenario(scenario)}>
                                    开始诊断
                                  </Button>
                                ) : (
                                  <Button onClick={() => configureDiagnosticScenario(scenario)}>
                                    {selectedProject.defaultAgentPublished ? '完善接入' : '发布默认 Agent'}
                                  </Button>
                                )}
                              </div>
                            </ScenarioItem>
                          ))}
                          {!selectedProject.diagnosticScenarios?.length && (
                            <ScenarioItem $ready={false}>
                              <Text strong>暂未生成诊断入口</Text>
                              <Text type="tertiary" style={{ marginTop: 8 }}>
                                刷新项目后仍未出现时，请先发布默认 Agent 并接入至少一种证据来源。
                              </Text>
                            </ScenarioItem>
                          )}
                        </ScenarioGrid>
                      </Section>

                      <Section title="项目默认能力">
                        <ResourceTitle>
                          <div>
                            <Text strong>默认 Agent、知识库和可用能力</Text>
                            <div>
                              <Text type="tertiary" size="small">
                                平台默认运维 Agent 会继承这里的能力；画布 Agent 仍可在节点上单独选择 MCP、RAG 和 Skill。
                              </Text>
                            </div>
                          </div>
                          <Space>
                            {!selectedProject.defaultAgentPublished && (
                              <Button loading={capabilitySaving} onClick={ensureDefaultAgent}>
                                修复默认 Agent
                              </Button>
                            )}
                            <Button icon={<IconSetting />} onClick={openProjectSettings}>
                              编辑默认能力
                            </Button>
                          </Space>
                        </ResourceTitle>
                        <SummaryGrid>
                          <SummaryItem>
                            <Text type="tertiary" size="small">默认 Agent</Text>
                            <Paragraph style={{ margin: '6px 0 0' }}>{selectedProject.defaultAgentId || '未设置'}</Paragraph>
                          </SummaryItem>
                          <SummaryItem>
                            <Text type="tertiary" size="small">默认知识库</Text>
                            <Paragraph style={{ margin: '6px 0 0' }}>{selectedProject.knowledgeBaseId || '未绑定'}</Paragraph>
                          </SummaryItem>
                          <SummaryItem>
                            <Text type="tertiary" size="small">项目 Skill</Text>
                            <Paragraph style={{ margin: '6px 0 0' }}>{selectedProject.skillIds?.join(', ') || '未选择'}</Paragraph>
                          </SummaryItem>
                          <SummaryItem>
                            <Text type="tertiary" size="small">通用 MCP</Text>
                            <Paragraph style={{ margin: '6px 0 0' }}>{selectedProject.sharedMcpIds?.join(', ') || '未绑定'}</Paragraph>
                          </SummaryItem>
                        </SummaryGrid>
                      </Section>

                      <Section title="项目成员与使用权限">
                        <ResourceTitle>
                          <div>
                            <Text strong>谁可以使用当前项目的 Agent</Text>
                            <div>
                              <Text type="tertiary" size="small">
                                项目负责人默认可访问；这里的显式成员可以在普通用户工作台看到项目，并使用项目下已发布 Agent。
                              </Text>
                            </div>
                          </div>
                          <Button icon={<IconEdit />} onClick={openMemberManagement}>
                            管理成员
                          </Button>
                        </ResourceTitle>
                        <ResourceGrid style={{ marginTop: theme.spacing.base }}>
                          {projectMembers.map((member) => {
                            const memberKey = String(member.memberKey || member.member_key || member.userId || member.user_id || member.username || '');
                            return (
                              <Card key={memberKey}>
                                <Space vertical align="start">
                                  <Text strong>{member.username || member.userId || member.user_id || memberKey}</Text>
                                  <Text type="tertiary" size="small">
                                    用户 ID：{member.userId || member.user_id || '-'}
                                  </Text>
                                  <Tag color="blue">{member.memberRole || member.member_role || 'MEMBER'}</Tag>
                                </Space>
                              </Card>
                            );
                          })}
                          {!projectMembers.length && (
                            <Card>
                              <Space vertical align="start">
                                <Text strong>暂无显式项目成员</Text>
                                <Text type="tertiary" size="small">
                                  当前只有项目负责人可访问。普通用户需要被加入项目后才能看到项目和 Agent。
                                </Text>
                              </Space>
                            </Card>
                          )}
                        </ResourceGrid>
                      </Section>

                      <Section title="项目 Skill">
                        <ResourceTitle>
                          <div>
                            <Text strong>项目专属经验与 SOP</Text>
                            <div>
                              <Text type="tertiary" size="small">
                                项目 Skill 用于保存当前项目专属的业务经验、表结构说明、SOP、发布规范和回滚规则。通用 Skill 可复制到项目后再定制。
                              </Text>
                            </div>
                          </div>
                          <Space wrap>
                            <Button onClick={openCreateProjectSkill}>新建项目 Skill</Button>
                            <Button onClick={() => void openCopyProjectSkill()}>从通用 Skill 复制</Button>
                            <Button disabled>从 SOP 生成</Button>
                            <Button disabled>从事故复盘沉淀</Button>
                            <Button onClick={() => navigate(`/agent-list?projectId=${encodeURIComponent(selectedProject.projectId)}`)}>
                              绑定 Agent
                            </Button>
                          </Space>
                        </ResourceTitle>
                        <ResourceGrid style={{ marginTop: theme.spacing.base }}>
                          {selectedProjectSkillIds.map((skillId) => {
                            const skill = skillOptionMap.get(skillId);
                            const projectOwned = isProjectOwnedSkill(skill, selectedProject.projectId);
                            const currentStatus = String(skill?.status || 'ENABLED').toUpperCase();
                            const nextStatus = currentStatus === 'DISABLED' ? 'ENABLED' : 'DISABLED';
                            return (
                              <Card key={skillId}>
                                <Space vertical align="start" style={{ width: '100%' }}>
                                  <ResourceTitle>
                                    <Text strong>{skill?.name || skillId}</Text>
                                    <Tag color="green">已绑定</Tag>
                                  </ResourceTitle>
                                  <Text type="secondary" size="small">
                                    ID：{skillId}
                                  </Text>
                                  <Text type="tertiary" size="small">
                                    来源：{projectSkillSourceLabel(skill, selectedProject.projectId)}
                                  </Text>
                                  {skill?.status && (
                                    <Text type="tertiary" size="small">
                                      状态：{skill.status}
                                    </Text>
                                  )}
                                  <Space wrap>
                                    {projectOwned && (
                                      <Button
                                        size="small"
                                        onClick={() => void openEditProjectSkill(skill)}
                                      >
                                        编辑
                                      </Button>
                                    )}
                                    {projectOwned && (
                                      <Button
                                        size="small"
                                        loading={capabilitySaving}
                                        onClick={() => void updateProjectSkillStatus(skill, nextStatus as 'ENABLED' | 'DISABLED')}
                                      >
                                        {nextStatus === 'ENABLED' ? '启用' : '停用'}
                                      </Button>
                                    )}
                                    <Button
                                      size="small"
                                      onClick={() => navigate(`/agent-list?projectId=${encodeURIComponent(selectedProject.projectId)}`)}
                                    >
                                      绑定 Agent
                                    </Button>
                                    <Button
                                      size="small"
                                      onClick={() => void openProjectSkillUsage(skill || ({ name: skillId, skillId } as OpsSkillSummary))}
                                    >
                                      查看使用记录
                                    </Button>
                                    <Button
                                      size="small"
                                      onClick={() => {
                                        setPreviewData({
                                          projectId: selectedProject.projectId,
                                          skillId,
                                          source: projectSkillSourceLabel(skill, selectedProject.projectId),
                                          detail: skill || null,
                                        });
                                        setPreviewVisible(true);
                                      }}
                                    >
                                      高级预览
                                    </Button>
                                  </Space>
                                </Space>
                              </Card>
                            );
                          })}
                          {selectedProjectSkillIds.length === 0 && (
                            <Card>
                              <Space vertical align="start">
                                <Text strong>暂无项目 Skill</Text>
                                <Text type="tertiary" size="small">
                                  可先从通用 Skill 复制，或后续在项目内沉淀 SOP 和事故复盘经验。
                                </Text>
                              </Space>
                            </Card>
                          )}
                        </ResourceGrid>
                      </Section>

                      <Section title="项目知识库">
                        <ResourceTitle>
                          <div>
                            <Text strong>项目专属文档与授权知识范围</Text>
                            <div>
                              <Text type="tertiary" size="small">
                                项目知识库保存当前项目专属的架构文档、表结构、服务说明、发布规范、事故复盘和业务 FAQ。项目可选择启用通用知识库，但 Agent 运行时只读取当前项目授权范围。
                              </Text>
                            </div>
                          </div>
                          <Space wrap>
                            <Button onClick={openCreateProjectKnowledge}>新建项目知识库</Button>
                            <Button onClick={openProjectSettings}>从通用知识库启用</Button>
                            <Button
                              disabled={!selectedProjectKnowledgeIds.length}
                              onClick={() => {
                                setProjectDocumentForm({
                                  kbId: selectedProjectKnowledgeIds[0] || '',
                                  name: '',
                                  files: [],
                                });
                                setProjectDocumentModalVisible(true);
                              }}
                            >
                              导入项目文档
                            </Button>
                            <Button onClick={() => navigate(`/agent-list?projectId=${encodeURIComponent(selectedProject.projectId)}`)}>
                              绑定 Agent
                            </Button>
                            <Button
                              onClick={() => {
                                setPreviewData({
                                  projectId: selectedProject.projectId,
                                  knowledgeFields: Object.fromEntries(selectedProjectKnowledgeFields),
                                });
                                setPreviewVisible(true);
                              }}
                            >
                              高级预览
                            </Button>
                          </Space>
                        </ResourceTitle>
                        <ResourceGrid style={{ marginTop: theme.spacing.base }}>
          {selectedProjectKnowledgeIds.map((knowledgeId) => {
            const knowledge = projectKnowledgeDetails.find((item) =>
              (item.kbId || item.knowledgeTag || item.ragId) === knowledgeId);
            const isProjectKnowledge = Boolean(
              knowledge
              && (String(knowledge.scope || '').toUpperCase() === 'PROJECT'
                || knowledge.projectId === selectedProject.projectId),
            );
            const enabled = String(knowledge?.status || '').toUpperCase() === 'ENABLED'
              || knowledge?.status === 1;
            return (
                            <Card key={knowledgeId}>
                              <Space vertical align="start" style={{ width: '100%' }}>
                                <ResourceTitle>
                                  <Text strong>{knowledge?.kbName || knowledgeId}</Text>
                                  <Tag color={enabled ? 'green' : 'grey'}>
                                    {isProjectKnowledge ? (enabled ? '项目知识库 · 启用' : '项目知识库 · 停用') : '已授权'}
                                  </Tag>
                                </ResourceTitle>
                                <Text type="tertiary" size="small">
                                  来源字段：{selectedProjectKnowledgeFields
                                    .filter(([, value]) => value === knowledgeId || (Array.isArray(value) && value.map(String).includes(knowledgeId)))
                                    .map(([key]) => key)
                                    .join(', ') || 'knowledge / rag / kb'}
                                </Text>
                                <Space wrap>
                                  <Button
                                    size="small"
                                    onClick={() => navigate(`/agent-list?projectId=${encodeURIComponent(selectedProject.projectId)}`)}
                                  >
                                    绑定 Agent
                                  </Button>
                                  {isProjectKnowledge && knowledge && (
                                    <Button size="small" onClick={() => openEditProjectKnowledge(knowledge)}>
                                      编辑
                                    </Button>
                                  )}
                                  {isProjectKnowledge && knowledge && (
                                    <Button
                                      size="small"
                                      type={enabled ? 'danger' : 'primary'}
                                      theme="borderless"
                                      onClick={() => toggleProjectKnowledgeStatus(knowledge)}
                                    >
                                      {enabled ? '停用' : '启用'}
                                    </Button>
                                  )}
                                  <Button
                                    size="small"
                                    onClick={() => {
                                      setPreviewData({
                                        projectId: selectedProject.projectId,
                                        knowledgeId,
                                        knowledgeFields: Object.fromEntries(selectedProjectKnowledgeFields),
                                      });
                                      setPreviewVisible(true);
                                    }}
                                  >
                                    高级预览
                                  </Button>
                                </Space>
                              </Space>
                            </Card>
            );
          })}
                          {!selectedProjectKnowledgeIds.length && (
                            <Card>
                              <Space vertical align="start">
                                <Text strong>暂无项目知识库或通用知识库授权。</Text>
                                <Text type="tertiary" size="small">
                                  可以新建项目知识库，或从项目默认能力中显式启用通用知识库。
                                </Text>
                              </Space>
                            </Card>
                          )}
                        </ResourceGrid>
                      </Section>

                      <Section title="代码与服务">
                        <ResourceTitle>
                          <div>
                            <Text strong>修复范围与构建入口</Text>
                            <div>
                              <Text type="tertiary" size="small">
                                仓库限定代码边界；服务目录限定模块、构建命令、制品和健康检查。
                              </Text>
                            </div>
                          </div>
                          <Space>
                            <Button icon={<IconPlus />} onClick={() => setRepositoryModalVisible(true)}>
                              登记仓库
                            </Button>
                            <Button
                              theme="solid"
                              icon={<IconPlus />}
                              disabled={!repositories.length}
                              onClick={() => {
                                setServiceForm((current) => ({
                                  ...current,
                                  repositoryId: current.repositoryId || repositories[0]?.repositoryId || '',
                                }));
                                setServiceModalVisible(true);
                              }}
                            >
                              添加服务
                            </Button>
                          </Space>
                        </ResourceTitle>
                        <ResourceGrid style={{ marginTop: 16 }}>
                          {repositories.map((repository) => (
                            <Card key={repository.repositoryId}>
                              <Text strong>{repository.name}</Text>
                              <Paragraph type="tertiary">{repository.localPath}</Paragraph>
                              <Space wrap>
                                <Tag color="blue">{repository.repositoryId}</Tag>
                                <Tag>{repository.defaultCommitSha?.slice(0, 12)}</Tag>
                                <Tag color="green">{repository.status}</Tag>
                              </Space>
                            </Card>
                          ))}
                          {projectServices.map((service) => (
                            <Card key={service.serviceId}>
                              <ResourceTitle>
                                <div>
                                  <Text strong>{service.name}</Text>
                                  <Paragraph type="tertiary">
                                    {service.modulePath} · {service.buildProfile}
                                  </Paragraph>
                                </div>
                                <Button
                                  icon={<IconEdit />}
                                  onClick={() => {
                                    setServiceForm({
                                      serviceId: service.serviceId,
                                      name: service.name,
                                      repositoryId: service.repositoryId,
                                      modulePath: service.modulePath,
                                      buildProfile: service.buildProfile,
                                      artifactPath: service.artifactPath || '',
                                      deploymentResourceId: service.deploymentResourceId || '',
                                      healthUrl: service.healthUrl || '',
                                      smokeUrls: (service.smokeUrls || []).join(','),
                                    });
                                    setServiceModalVisible(true);
                                  }}
                                >
                                  编辑
                                </Button>
                              </ResourceTitle>
                              <Space wrap>
                                <Tag color="blue">{service.serviceId}</Tag>
                                <Tag>{service.repositoryId}</Tag>
                                <Tag color={service.deploymentResourceId ? 'green' : 'grey'}>
                                  {service.deploymentResourceId ? '可部署' : '仅验证'}
                                </Tag>
                              </Space>
                            </Card>
                          ))}
                          {!repositories.length && !projectServices.length && (
                            <Text type="tertiary">
                              尚未登记代码仓库和服务，Agent 只能分析运行态数据，不能验证代码修复。
                            </Text>
                          )}
                        </ResourceGrid>
                      </Section>

                      <Section title="变更执行目标">
                        <ResourceTitle>
                          <div>
                            <Text strong>审批通过后允许受控执行服务操作的目标</Text>
                            <div>
                              <Text type="tertiary" size="small">
                                这里只承载写操作和部署动作。它不会作为 Agent 查询数据的工具，也不会绕过审批链路。
                              </Text>
                            </div>
                          </div>
                          <Button theme="solid" icon={<IconPlus />} onClick={() => openExecutionResource()}>
                            添加执行目标
                          </Button>
                        </ResourceTitle>
                        <ResourceGrid style={{ marginTop: 16 }}>
                          {executionResources.map((resource) => (
                            <Card key={resource.resourceId}>
                              <ResourceTitle>
                                <div>
                                  <Text strong>{resource.name}</Text>
                                  <div><Text type="tertiary" size="small">{resource.resourceId}</Text></div>
                                </div>
                                <Tag color={resource.status === 'ENABLED' ? 'green' : 'grey'}>
                                  {resource.status}
                                </Tag>
                              </ResourceTitle>
                              <Paragraph type="tertiary">
                                {resource.adapter} · 执行连接 {resource.workerId}
                              </Paragraph>
                              <Space wrap>
                                {(resource.environments || []).map((environment) => (
                                  <Tag key={environment}>{environment}</Tag>
                                ))}
                              </Space>
                              <Button
                                icon={<IconEdit />}
                                style={{ marginTop: 14 }}
                                onClick={() => openExecutionResource(resource)}
                              >
                                编辑配置
                              </Button>
                            </Card>
                          ))}
                          {!executionResources.length && (
                            <Text type="tertiary">
                              尚未配置执行目标。项目仍可诊断和生成方案，但审批后不能自动执行变更。
                            </Text>
                          )}
                        </ResourceGrid>
                      </Section>

                      <Section title="数据连接与工具">
                        <ResourceTitle style={{ marginBottom: 16 }}>
                          <div>
                            <Text strong>Agent 可查询的数据源</Text>
                            <div><Text type="tertiary" size="small">连接保存地址与凭据；可见对象和允许动作决定生成的工具能访问哪些数据。</Text></div>
                          </div>
                          <Button theme="solid" icon={<IconPlus />} onClick={() => setResourceModalVisible(true)}>添加连接</Button>
                        </ResourceTitle>
                        <ResourceGrid>
                          {(selectedProject.resources || []).map((resource) => (
                            <Card key={resource.resourceId} shadows="hover">
                              <ResourceTitle>
                                <Space>
                                  <IconBranch />
                                  <div>
                                    <Text strong>{resource.name}</Text>
                                    <div>
                                      <Tag color={typeColor(resource.type)}>{resource.typeName}</Tag>
                                      <Tag>{resource.environment}</Tag>
                                    </div>
                                  </div>
                                </Space>
                                <Tag color={statusColor(resource.status)}>{resource.status}</Tag>
                              </ResourceTitle>
                              <Paragraph type="tertiary" style={{ marginTop: 12 }}>{resource.endpoint}</Paragraph>
                              <Space wrap style={{ marginBottom: 10 }}>
                                <Tag color={resource.credential?.configured ? 'green' : 'grey'}>
                                  {credentialLabel(resource)}
                                </Tag>
                                <Tag color={resource.schema?.source === 'live' ? 'blue' : 'grey'}>
                                  {schemaSourceLabel(resource.schema?.source)}
                                </Tag>
                              </Space>
                              {resource.schema?.message && (
                                <Paragraph type="tertiary" style={{ marginTop: 0, marginBottom: 10 }}>
                                  {resource.schema.message}
                                </Paragraph>
                              )}
                              <Space wrap>
                                {(resource.permission?.actions || []).map((action) => (
                                  <Tag key={action}>{action}</Tag>
                                ))}
                              </Space>
                              <SchemaList>
                                {(resource.schema?.objects || []).slice(0, 3).map((item) => (
                                  <SchemaRow key={item.name}>
                                    <Text strong>{item.name}</Text>
                                    <div>
                                      <Text type="tertiary" size="small">
                                        {schemaObjectMeta(item)}
                                      </Text>
                                    </div>
                                  </SchemaRow>
                                ))}
                              </SchemaList>
                              <Space style={{ marginTop: 14 }}>
                                <Button icon={<IconEdit />} onClick={() => openResourceEdit(resource)}>编辑</Button>
                                <Button icon={<IconSetting />} onClick={() => openPermission(resource)}>权限</Button>
                                <Button theme="solid" onClick={() => generateMcp(resource)}>生成项目工具</Button>
                              </Space>
                            </Card>
                          ))}
                        </ResourceGrid>
                      </Section>

                      <Section title="已生成工具">
                        <ResourceGrid>
                          {(selectedProject.generatedMcps || []).map((mcp: OpsGeneratedMcp) => (
                            <Card key={mcp.mcpId}>
                              <ResourceTitle>
                                <div>
                                  <Text strong>{mcp.toolName || mcp.mcpName}</Text>
                                  <div><Text type="tertiary" size="small">{mcp.toolId || mcp.mcpId}</Text></div>
                                </div>
                                <Space>
                                  {mcp.templateId && <Tag color="blue">{mcp.templateId}</Tag>}
                                  <Tag color={mcp.status === 'ENABLED' ? 'green' : 'orange'}>{mcp.status}</Tag>
                                </Space>
                              </ResourceTitle>
                              <Paragraph type="tertiary">
                                {mcp.resourceType} · {mcp.transportType} · 权限以平台审核策略为准 · 超时 {mcp.requestTimeout}s
                              </Paragraph>
                              <Button
                                icon={<IconEyeOpened />}
                                onClick={() => {
                                  setPreviewData(mcp);
                                  setPreviewVisible(true);
                                }}
                              >
                                查看配置
                              </Button>
                            </Card>
                          ))}
                          {(!selectedProject.generatedMcps || selectedProject.generatedMcps.length === 0) && (
                            <Text type="tertiary">还没有项目工具。先添加数据连接、限制可见对象和动作，再生成工具。</Text>
                          )}
                        </ResourceGrid>
                      </Section>
                  </>
                )}
              </Workspace>
            </Spin>
          </ContentArea>
        </MainColumn>
      </MainContent>

      <Modal
        title="登记代码仓库"
        visible={repositoryModalVisible}
        onCancel={() => setRepositoryModalVisible(false)}
        onOk={registerRepository}
      >
        <Space vertical align="start" style={{ width: '100%' }}>
          <ModalGrid>
            <Input
              placeholder="仓库名称"
              value={repositoryForm.name}
              onChange={(name) => setRepositoryForm({ ...repositoryForm, name })}
            />
          </ModalGrid>
          <Input
            placeholder="本机 Git 仓库绝对路径"
            value={repositoryForm.localPath}
            onChange={(localPath) => setRepositoryForm({ ...repositoryForm, localPath })}
          />
          <Input
            placeholder="默认 revision"
            value={repositoryForm.defaultRevision}
            onChange={(defaultRevision) => setRepositoryForm({ ...repositoryForm, defaultRevision })}
          />
          <Paragraph type="tertiary">
            平台仅允许访问服务端配置的本地根目录，并在运行时固定到 40 位 commit SHA。
          </Paragraph>
        </Space>
      </Modal>

      <Modal
        title="添加项目服务"
        visible={serviceModalVisible}
        width={760}
        onCancel={() => setServiceModalVisible(false)}
        onOk={saveProjectService}
      >
        <Space vertical align="start" style={{ width: '100%' }}>
          <ModalGrid>
            <FormField>
              <Text strong>服务名称</Text>
              <Input
                placeholder="例如 支付交易服务"
                value={serviceForm.name}
                onChange={(name) => setServiceForm({ ...serviceForm, name })}
              />
              <FieldHint>给人看的名称，会出现在代码服务卡片和变更提案中。</FieldHint>
            </FormField>
          </ModalGrid>
          <ModalGrid>
            <FormField>
              <Text strong>代码仓库</Text>
              <Select
                value={serviceForm.repositoryId}
                style={{ width: '100%' }}
                onChange={(repositoryId) => setServiceForm({ ...serviceForm, repositoryId: String(repositoryId) })}
              >
                {repositories.map((repository) => (
                  <Option key={repository.repositoryId} value={repository.repositoryId}>{repository.name}</Option>
                ))}
              </Select>
              <FieldHint>服务所属仓库。请先登记仓库，再添加服务。</FieldHint>
            </FormField>
            <FormField>
              <Text strong>构建方式</Text>
              <Select
                value={serviceForm.buildProfile}
                style={{ width: '100%' }}
                onChange={(buildProfile) => setServiceForm({ ...serviceForm, buildProfile: String(buildProfile) })}
              >
                <Option value="MAVEN_VERIFY">Maven test + package</Option>
                <Option value="NPM_TEST_BUILD">npm test</Option>
                <Option value="MAKE_CI">make ci</Option>
              </Select>
              <FieldHint>Agent 生成修复方案后，用这个预设流程验证代码是否可构建。</FieldHint>
            </FormField>
          </ModalGrid>
          <ModalGrid>
            <FormField>
              <Text strong>模块相对路径</Text>
              <Input
                placeholder="例如 apps/payment-service"
                value={serviceForm.modulePath}
                onChange={(modulePath) => setServiceForm({ ...serviceForm, modulePath })}
              />
              <FieldHint>相对于仓库根目录；单模块项目可填 `.`。</FieldHint>
            </FormField>
            <FormField>
              <Text strong>制品相对路径</Text>
              <Input
                placeholder="例如 target/payment-service.jar"
                value={serviceForm.artifactPath}
                onChange={(artifactPath) => setServiceForm({ ...serviceForm, artifactPath })}
              />
              <FieldHint>构建后产物路径；只做代码验证、不自动部署时可以留空。</FieldHint>
            </FormField>
          </ModalGrid>
          <FormField style={{ width: '100%' }}>
            <Text strong>执行目标</Text>
            <Select
              value={serviceForm.deploymentResourceId}
              style={{ width: '100%' }}
              placeholder="选择执行资源；留空表示只做代码验证"
              onChange={(deploymentResourceId) => setServiceForm({
                ...serviceForm,
                deploymentResourceId: String(deploymentResourceId || ''),
              })}
            >
              <Option value="">仅验证，不自动部署</Option>
              {executionResources.map((resource) => (
                <Option key={resource.resourceId} value={resource.resourceId}>
                  {resource.name} · 执行连接 {resource.workerId}
                </Option>
              ))}
            </Select>
            <FieldHint>审批通过后用于发布、重启或受控执行；留空时 Agent 只能验证修复方案，不会自动部署。</FieldHint>
          </FormField>
          <FormField style={{ width: '100%' }}>
            <Text strong>健康检查 URL</Text>
            <Input
              placeholder="例如 http://127.0.0.1:8091/actuator/health"
              value={serviceForm.healthUrl}
              onChange={(healthUrl) => setServiceForm({ ...serviceForm, healthUrl })}
            />
            <FieldHint>变更执行后用于判断服务是否恢复。</FieldHint>
          </FormField>
          <FormField style={{ width: '100%' }}>
            <Text strong>冒烟 URL</Text>
            <Input
              placeholder="多个 URL 用逗号分隔"
              value={serviceForm.smokeUrls}
              onChange={(smokeUrls) => setServiceForm({ ...serviceForm, smokeUrls })}
            />
            <FieldHint>变更执行后用于验证关键业务接口是否可用。</FieldHint>
          </FormField>
        </Space>
      </Modal>

      <Modal
        title="变更执行目标"
        visible={executionResourceModalVisible}
        width={820}
        onCancel={() => setExecutionResourceModalVisible(false)}
        onOk={saveExecutionResource}
      >
        <Space vertical align="start" style={{ width: '100%' }}>
          <ModalGrid>
            <FormField>
              <Text strong>目标名称</Text>
              <Input
                placeholder="例如 支付生产服务运行目标"
                value={executionResourceForm.name}
                onChange={(name) => setExecutionResourceForm({ ...executionResourceForm, name })}
              />
              <FieldHint>给审批人看的名称，用来判断这个目标会影响哪个环境或服务。</FieldHint>
            </FormField>
          </ModalGrid>
          <ModalGrid>
            <FormField>
              <Text strong>适用环境</Text>
              <Input
                placeholder="例如 prod，多个用逗号分隔"
                value={executionResourceForm.environments}
                onChange={(environments) => setExecutionResourceForm({
                  ...executionResourceForm,
                  environments,
                })}
              />
              <FieldHint>用于限制这个执行目标只出现在指定环境的变更流程里。</FieldHint>
            </FormField>
          </ModalGrid>
          <ModalGrid>
            <FormField>
              <Text strong>目标类型</Text>
              <Select
                value={executionResourceForm.adapter}
                style={{ width: '100%' }}
                onChange={(adapter) => setExecutionResourceForm({
                  ...executionResourceForm,
                  adapter: String(adapter),
                  configuration: String(adapter) === 'deployment-http'
                    ? toJson({ baseUrl: 'https://deployment.example.com', tokenFile: '' })
                    : String(adapter) === 'mysql-controlled'
                      ? toJson({
                          endpoint: 'mysql://127.0.0.1:3306/database',
                          sourceResourceId: 'project-mysql-prod',
                          username: 'ops-worker',
                          passwordFile: '/run/secrets/mysql-password',
                          idempotencyTable: 'database.ai_ops_worker_idempotency',
                          allowedObjects: ['database.table_name'],
                          allowedActions: [
                            'MYSQL_CREATE_INDEX',
                            'MYSQL_DROP_INDEX',
                            'MYSQL_UPDATE_LIMITED',
                            'MYSQL_SET_GLOBAL_VARIABLE',
                          ],
                          allowedVariables: [
                            'max_connections',
                            'max_execution_time',
                            'innodb_lock_wait_timeout',
                          ],
                          maxAffectedRows: 100,
                          connectTimeoutMs: 5000,
                          queryTimeoutMs: 15000,
                          ssl: false,
                        })
                      : String(adapter) === 'redis-controlled'
                        ? toJson({
                            endpoint: 'redis://127.0.0.1:6379/0',
                            sourceResourceId: 'project-redis-prod',
                            username: '',
                            passwordFile: '/run/secrets/redis-password',
                            allowedPatterns: ['project:*'],
                            allowedActions: ['REDIS_DELETE_KEYS', 'REDIS_UPDATE_TTL', 'REDIS_CONFIG_SET'],
                            allowedConfigKeys: [
                              'maxmemory-policy',
                              'slowlog-log-slower-than',
                              'slowlog-max-len',
                            ],
                            idempotencyPrefix: '__ops_change__:',
                            maxKeys: 100,
                            maxSnapshotBytes: 10485760,
                            snapshotRetentionSeconds: 86400,
                            connectTimeoutMs: 5000,
                            commandTimeoutMs: 15000,
                          })
                        : String(adapter) === 'rabbitmq-policy'
                          ? toJson({
                              baseUrl: 'http://127.0.0.1:15672',
                              sourceResourceId: 'project-rabbitmq-prod',
                              username: 'ops-worker',
                              passwordFile: '/run/secrets/rabbitmq-password',
                              allowedVhosts: ['/'],
                              allowedPolicyPrefixes: ['ops-'],
                              allowedDefinitionKeys: [
                                'message-ttl',
                                'max-length',
                                'max-length-bytes',
                                'overflow',
                                'dead-letter-exchange',
                                'dead-letter-routing-key',
                                'queue-mode',
                                'consumer-timeout',
                                'delivery-limit',
                              ],
                              maxAffectedObjects: 100,
                              requestTimeoutMs: 15000,
                            })
                      : executionResourceForm.configuration,
                })}
              >
                <Option value="local-java-service">本地 Java 制品部署</Option>
                <Option value="deployment-http">部署平台 HTTP API</Option>
                <Option value="mysql-controlled">MySQL 受控执行</Option>
                <Option value="redis-controlled">Redis 受控执行</Option>
                <Option value="rabbitmq-policy">RabbitMQ Policy 受控执行</Option>
              </Select>
              <FieldHint>决定 Worker 支持哪类受控动作，不是 Agent 查询数据的 MCP。</FieldHint>
            </FormField>
            <FormField>
              <Text strong>状态</Text>
              <Select
                value={executionResourceForm.status}
                style={{ width: '100%' }}
                onChange={(status) => setExecutionResourceForm({
                  ...executionResourceForm,
                  status: String(status),
                })}
              >
                <Option value="ENABLED">启用</Option>
                <Option value="DISABLED">停用</Option>
              </Select>
              <FieldHint>停用后不会出现在新的变更执行选择中。</FieldHint>
            </FormField>
          </ModalGrid>
          <Collapse keepDOM style={{ width: '100%' }}>
            <Collapse.Panel itemKey="adapter-config" header="高级配置：受控适配器 JSON">
              <TextArea
                value={executionResourceForm.configuration}
                autosize={{ minRows: 14, maxRows: 24 }}
                style={{ marginTop: 8, fontFamily: 'monospace' }}
                onChange={(configuration) => setExecutionResourceForm({
                  ...executionResourceForm,
                  configuration,
                })}
              />
              <FieldHint style={{ marginTop: 8 }}>
                这里限制 Worker 可操作的服务、数据对象和动作。平台不接受任意 Shell 命令，也不保存明文密码。
              </FieldHint>
            </Collapse.Panel>
          </Collapse>
          <Paragraph type="tertiary">
            配置限定 Worker 可操作的服务或数据对象，不接受 Shell 命令，也不保存明文密码。
            Worker 使用 HMAC 认证并按 Worker ID 动态获取配置。
          </Paragraph>
        </Space>
      </Modal>

      <Modal
        title="新建业务系统"
        visible={projectModalVisible}
        onCancel={() => setProjectModalVisible(false)}
        onOk={createProject}
      >
        <Space vertical align="start" style={{ width: '100%' }}>
          <Input placeholder="业务系统名称" value={projectForm.name} onChange={(name) => setProjectForm({ ...projectForm, name })} />
          <Input placeholder="负责人" value={projectForm.owner} onChange={(owner) => setProjectForm({ ...projectForm, owner })} />
          <Input placeholder="描述" value={projectForm.description} onChange={(description) => setProjectForm({ ...projectForm, description })} />
        </Space>
      </Modal>

      <Modal
        title="项目默认配置"
        visible={projectSettingsVisible}
        width={960}
        onCancel={() => setProjectSettingsVisible(false)}
        onOk={saveProjectSettings}
      >
        <Space vertical align="start" style={{ width: '100%' }}>
          <ModalGrid>
            <Input
              placeholder="业务系统名称"
              value={projectSettings.name}
              onChange={(name) => setProjectSettings({ ...projectSettings, name })}
            />
            <Input
              placeholder="负责人"
              value={projectSettings.owner}
              onChange={(owner) => setProjectSettings({ ...projectSettings, owner })}
            />
          </ModalGrid>
          <Input
            placeholder="业务系统说明"
            value={projectSettings.description}
            onChange={(description) => setProjectSettings({ ...projectSettings, description })}
          />
          <ModalGrid>
            <Select
              value={projectSettings.knowledgeBaseId}
              filter
              style={{ width: '100%' }}
              placeholder="选择项目默认知识库"
              onChange={(value) => setProjectSettings({ ...projectSettings, knowledgeBaseId: String(value || '') })}
            >
              <Option value="">不绑定知识库</Option>
              {knowledgeOptions.map((knowledge) => (
                <Option key={knowledge.kbId || knowledge.knowledgeTag} value={knowledge.kbId || knowledge.knowledgeTag}>
                  {knowledge.kbName || knowledge.name || knowledge.knowledgeTag}
                </Option>
              ))}
            </Select>
            <Select
              value={projectSettings.defaultAgentId}
              style={{ width: '100%' }}
              placeholder="项目默认 Agent"
              onChange={(value) => setProjectSettings({ ...projectSettings, defaultAgentId: String(value || '') })}
            >
              {agentOptions.map((agent) => (
                <Option key={agent.agentId} value={agent.agentId}>
                  {agent.name || agent.agentId}
                </Option>
              ))}
            </Select>
          </ModalGrid>
          <div style={{ width: '100%' }}>
            <Text strong>项目可用 Skill</Text>
            <Select
              multiple
              filter
              value={projectSettings.skillIds}
              style={{ width: '100%', marginTop: 8 }}
              placeholder="选择允许该项目 Agent 使用的 Skill"
              onChange={(value) => setProjectSettings({
                ...projectSettings,
                skillIds: Array.isArray(value) ? value.map(String) : [],
              })}
            >
              {skillOptions.map((skill) => (
                <Option key={skill.name} value={skill.name}>
                  {skill.name}
                </Option>
              ))}
            </Select>
          </div>
          <div style={{ width: '100%' }}>
            <Text strong>项目绑定的通用 MCP</Text>
            <Select
              multiple
              filter
              value={projectSettings.sharedMcpIds}
              style={{ width: '100%', marginTop: 8 }}
              placeholder="选择允许该项目使用的手动 MCP"
              onChange={(value) => setProjectSettings({
                ...projectSettings,
                sharedMcpIds: Array.isArray(value) ? value.map(String) : [],
              })}
            >
              {sharedMcpOptions.map((mcp) => (
                <Option key={mcp.mcpId} value={mcp.mcpId}>
                  {mcp.mcpName || mcp.mcpId}
                </Option>
              ))}
            </Select>
          </div>
          <Paragraph type="tertiary">
            平台通用运维 Agent 会自动继承这里配置的 Skill、项目 MCP、通用 MCP 和默认知识库；画布创建的项目专属 Agent 仍按节点显式绑定能力。
          </Paragraph>
        </Space>
      </Modal>

      <Modal
        title="添加资源"
        visible={resourceModalVisible}
        onCancel={() => setResourceModalVisible(false)}
        onOk={addResource}
      >
        <Space vertical align="start" style={{ width: '100%' }}>
          <ModalGrid>
            <FormField>
              <Text strong>资源类型</Text>
              <Select value={resourceForm.type} onChange={(type) => setResourceForm({ ...resourceForm, type: String(type), endpoint: '' })}>
                {templates.map((template) => (
                  <Option key={template.type} value={template.type}>{template.name}</Option>
                ))}
              </Select>
              <FieldHint>决定平台如何扫描对象并生成 MCP 工具能力。</FieldHint>
            </FormField>
            <FormField>
              <Text strong>环境</Text>
              <Select value={resourceForm.environment} onChange={(environment) => setResourceForm({ ...resourceForm, environment: String(environment) })}>
                {(selectedProject?.environments || ['dev', 'test', 'prod']).map((env) => (
                  <Option key={env} value={env}>{env}</Option>
                ))}
              </Select>
              <FieldHint>用于区分 dev、test、prod 等环境资源。</FieldHint>
            </FormField>
          </ModalGrid>
          <FormField style={{ width: '100%' }}>
            <Text strong>资源名称</Text>
            <Input placeholder="例如 支付生产 MySQL" value={resourceForm.name} onChange={(name) => setResourceForm({ ...resourceForm, name })} />
            <FieldHint>给人看的资源名称，会显示在数据连接和 MCP 工具卡片里。</FieldHint>
          </FormField>
          <FormField style={{ width: '100%' }}>
            <Text strong>连接地址</Text>
            <Input placeholder="例如 mysql://host:3306/db" value={resourceForm.endpoint} onChange={(endpoint) => setResourceForm({ ...resourceForm, endpoint })} />
            <FieldHint>连接地址可后续编辑。保存后平台会尝试扫描可见对象。</FieldHint>
          </FormField>
          <ModalGrid>
            <FormField>
              <Text strong>用户名</Text>
              <Input placeholder="例如 readonly_user" value={resourceForm.username} onChange={(username) => setResourceForm({ ...resourceForm, username })} />
              <FieldHint>建议使用只读或受限账号。</FieldHint>
            </FormField>
            <FormField>
              <Text strong>凭据引用</Text>
              <Input
                placeholder="例如 ${env:MYSQL_PASSWORD}"
                value={resourceForm.passwordRef}
                onChange={(passwordRef) => setResourceForm({ ...resourceForm, passwordRef })}
              />
              <FieldHint>平台保存 Secret 引用，不保存明文密码。</FieldHint>
            </FormField>
          </ModalGrid>
          {selectedTemplate && (
            <Paragraph type="tertiary">
              {selectedTemplate.name} 会扫描 {selectedTemplate.schemaHint}，默认生成只读诊断权限；平台只保存环境变量引用，不保存明文凭据。
            </Paragraph>
          )}
        </Space>
      </Modal>

      <Modal
        title="编辑资源连接"
        visible={resourceEditModalVisible}
        onCancel={() => setResourceEditModalVisible(false)}
        onOk={saveResource}
      >
        <Space vertical align="start" style={{ width: '100%' }}>
          <ModalGrid>
            <FormField>
              <Text strong>资源名称</Text>
              <Input placeholder="例如 支付生产 MySQL" value={resourceEditForm.name} onChange={(name) => setResourceEditForm({ ...resourceEditForm, name })} />
            </FormField>
            <FormField>
              <Text strong>环境</Text>
              <Select value={resourceEditForm.environment} onChange={(environment) => setResourceEditForm({ ...resourceEditForm, environment: String(environment) })}>
                {(selectedProject?.environments || ['dev', 'test', 'prod']).map((env) => (
                  <Option key={env} value={env}>{env}</Option>
                ))}
              </Select>
            </FormField>
          </ModalGrid>
          <FormField style={{ width: '100%' }}>
            <Text strong>连接地址</Text>
            <Input placeholder="例如 mysql://host:3306/db" value={resourceEditForm.endpoint} onChange={(endpoint) => setResourceEditForm({ ...resourceEditForm, endpoint })} />
            <FieldHint>保存后会重新生成扫描视图和权限对象列表。</FieldHint>
          </FormField>
          <ModalGrid>
            <FormField>
              <Text strong>用户名</Text>
              <Input placeholder="例如 readonly_user" value={resourceEditForm.username} onChange={(username) => setResourceEditForm({ ...resourceEditForm, username })} />
            </FormField>
            <FormField>
              <Text strong>凭据引用</Text>
              <Input
                placeholder="例如 ${env:MYSQL_PASSWORD}"
                value={resourceEditForm.passwordRef}
                onChange={(passwordRef) => setResourceEditForm({ ...resourceEditForm, passwordRef })}
              />
              <FieldHint>平台保存 Secret 引用，不保存明文密码。</FieldHint>
            </FormField>
          </ModalGrid>
          <Paragraph type="tertiary">
            保存后会重新生成当前资源的扫描视图和权限对象列表；实际凭据仅在运行时从环境变量解析。
          </Paragraph>
        </Space>
      </Modal>

      <Modal
        title="资源权限策略"
        visible={permissionModalVisible}
        width={720}
        onCancel={() => setPermissionModalVisible(false)}
        onOk={savePermission}
      >
        {selectedResource && permissionDraft && (
          <Space vertical align="start" style={{ width: '100%' }}>
            <Text strong>{selectedResource.name}</Text>
            <div style={{ width: '100%' }}>
              <Text>允许动作</Text>
              <CheckGrid>
                {(templates.find((item) => item.type === selectedResource.type)?.actions || []).map((action) => (
                  <Checkbox
                    key={action}
                    checked={permissionDraft.actions.includes(action)}
                    onChange={(event) => toggleDraftValue('actions', action, Boolean(event.target.checked))}
                  >
                    {action}
                  </Checkbox>
                ))}
              </CheckGrid>
            </div>
            <div style={{ width: '100%' }}>
              <Text>可见对象</Text>
              <CheckGrid>
                {(selectedResource.schema?.objects || []).map((item) => (
                  <Checkbox
                    key={item.name}
                    checked={permissionDraft.objects.includes(item.name)}
                    onChange={(event) => toggleDraftValue('objects', item.name, Boolean(event.target.checked))}
                  >
                    {item.name}
                  </Checkbox>
                ))}
              </CheckGrid>
            </div>
            <ModalGrid>
              <Input
                value={String(permissionDraft.maxRows || 100)}
                prefix="最大行数"
                onChange={(value) => setPermissionDraft({ ...permissionDraft, maxRows: Number(value) || 100 })}
              />
              <Input
                value={String(permissionDraft.timeoutSeconds || 15)}
                prefix="超时秒"
                onChange={(value) => setPermissionDraft({ ...permissionDraft, timeoutSeconds: Number(value) || 15 })}
              />
            </ModalGrid>
            {(() => {
              const template = templates.find((item) => item.type === selectedResource.type);
              const meta = multiObjectPermissionMeta(selectedResource, template);
              return (
                <div>
                  <Checkbox
                    checked={Boolean(permissionDraft.allowJoin)}
                    onChange={(event) => setPermissionDraft({ ...permissionDraft, allowJoin: Boolean(event.target.checked) })}
                  >
                    {meta.label}
                  </Checkbox>
                  <div style={{ marginTop: 4 }}>
                    <Text type="tertiary" size="small">{meta.description}</Text>
                  </div>
                </div>
              );
            })()}
          </Space>
        )}
      </Modal>

      <Modal
        title={projectSkillModalMode === 'edit'
          ? '编辑项目 Skill'
          : projectSkillModalMode === 'copy'
            ? '从通用 Skill 复制'
            : '新建项目 Skill'}
        visible={projectSkillModalMode !== null}
        width={720}
        confirmLoading={capabilitySaving}
        onCancel={() => setProjectSkillModalMode(null)}
        onOk={saveProjectSkill}
      >
        <Space vertical align="start" spacing="medium" style={{ width: '100%' }}>
          {projectSkillModalMode === 'copy' && (
            <FormField style={{ width: '100%' }}>
              <Text strong>通用 Skill</Text>
              <Select
                value={projectSkillForm.globalSkillId}
                filter
                style={{ width: '100%' }}
                onChange={(globalSkillId) => setProjectSkillForm((current) => ({
                  ...current,
                  globalSkillId: String(globalSkillId || ''),
                }))}
              >
                {skillOptions.map((skill) => (
                  <Option key={skillValueOf(skill)} value={skillValueOf(skill)}>
                    {skill.name}
                  </Option>
                ))}
              </Select>
            </FormField>
          )}
          <ModalGrid>
            {projectSkillModalMode === 'edit' && (
              <FormField>
                <Text strong>系统 ID</Text>
                <Input value={projectSkillForm.skillId} disabled />
              </FormField>
            )}
            <FormField>
              <Text strong>名称</Text>
              <Input
                value={projectSkillForm.name}
                placeholder={projectSkillModalMode === 'copy' ? '留空沿用通用 Skill 名称' : 'Skill 名称'}
                onChange={(name) => setProjectSkillForm((current) => ({ ...current, name }))}
              />
            </FormField>
          </ModalGrid>
          <FormField style={{ width: '100%' }}>
            <Text strong>说明</Text>
            <Input
              value={projectSkillForm.description}
              placeholder="说明项目内适用场景和边界"
              onChange={(description) => setProjectSkillForm((current) => ({ ...current, description }))}
            />
          </FormField>
          {projectSkillModalMode !== 'copy' && (
            <FormField style={{ width: '100%' }}>
              <SkillPackageEditor
                entrypointContent={projectSkillForm.content}
                artifacts={projectSkillForm.artifacts}
                disabled={capabilitySaving}
                onEntrypointChange={(content) => setProjectSkillForm((current) => ({ ...current, content }))}
                onArtifactsChange={(artifacts) => setProjectSkillForm((current) => ({ ...current, artifacts }))}
              />
            </FormField>
          )}
        </Space>
      </Modal>

      <Modal
        title={`项目 Skill 使用记录${projectSkillUsageTitle ? ` · ${projectSkillUsageTitle}` : ''}`}
        visible={projectSkillUsageVisible}
        width={760}
        footer={null}
        onCancel={() => setProjectSkillUsageVisible(false)}
      >
        <Spin spinning={projectSkillUsageLoading}>
          <Space vertical align="start" spacing="medium" style={{ width: '100%' }}>
            {projectSkillUsageRecords.length === 0 && !projectSkillUsageLoading && (
              <Card style={{ width: '100%' }}>
                <Space vertical align="start">
                  <Text strong>暂无使用记录</Text>
                  <Text type="tertiary" size="small">
                    当前没有 Agent 或节点显式引用该项目 Skill。
                  </Text>
                </Space>
              </Card>
            )}
            {projectSkillUsageRecords.map((record) => (
              <Card
                key={`${record.projectId || selectedProject?.projectId || ''}-${record.agentId || ''}-${record.targetId || record.type}`}
                style={{ width: '100%' }}
              >
                <Space vertical align="start" spacing="tight" style={{ width: '100%' }}>
                  <ResourceTitle>
                    <Text strong>{record.agentName || record.agentId || '未知 Agent'}</Text>
                    <Tag color={record.type === 'node' ? 'blue' : 'green'}>
                      {record.type === 'node' ? '节点引用' : 'Agent 默认能力'}
                    </Tag>
                  </ResourceTitle>
                  <Text type="secondary" size="small">项目：{record.projectId || selectedProject?.projectId || '-'}</Text>
                  <Text type="tertiary" size="small">位置：{record.type === 'node' ? `节点 ${record.targetId || '-'}` : 'Agent 默认能力'}</Text>
                </Space>
              </Card>
            ))}
          </Space>
        </Spin>
      </Modal>

      <Modal
        title="管理项目成员"
        visible={memberModalVisible}
        width={760}
        confirmLoading={capabilitySaving}
        onCancel={() => setMemberModalVisible(false)}
        onOk={saveProjectMembers}
      >
        <Space vertical align="start" spacing="medium" style={{ width: '100%' }}>
          <Paragraph type="tertiary">
            先选择平台账号，再分配项目角色。用户编号由账号系统生成，不需要手写；保存后会替换当前项目的显式成员授权。
          </Paragraph>
          {memberDrafts.map((member, index) => (
            <Card key={`${index}-${member.userId}-${member.username}`} style={{ width: '100%' }}>
              <ModalGrid>
                <FormField>
                  <Text strong>平台账号</Text>
                  <Select
                    value={member.username ? `${member.userId || ''}::${member.username}` : undefined}
                    placeholder="选择已有账号"
                    filter
                    style={{ width: '100%' }}
                    onChange={(value) => selectMemberAccount(index, String(value || ''))}
                    emptyContent="暂无可选账号，请先在下方创建"
                  >
                    {accountOptions.map((account) => (
                      <Option key={`${account.userId || ''}::${account.username}`} value={`${account.userId || ''}::${account.username}`}>
                        {account.username} · {account.userRole === 'admin' ? '管理员' : '普通用户'}
                      </Option>
                    ))}
                  </Select>
                  <FieldHint>{member.username ? `系统用户编号：${member.userId || '未返回'}` : '项目成员必须来自平台账号。'}</FieldHint>
                </FormField>
                <FormField>
                  <Text strong>项目角色</Text>
                  <Select
                    value={member.memberRole}
                    style={{ width: '100%' }}
                    onChange={(memberRole) => setMemberDrafts((current) => current.map((item, itemIndex) => (
                      itemIndex === index ? { ...item, memberRole: String(memberRole) } : item
                    )))}
                  >
                    <Option value="MEMBER">成员</Option>
                    <Option value="APPROVER">审批人</Option>
                    <Option value="VERIFIER">验证人</Option>
                  </Select>
                </FormField>
                <FormField>
                  <Text strong>操作</Text>
                  <Button
                    type="danger"
                    theme="borderless"
                    onClick={() => setMemberDrafts((current) => current.filter((_, itemIndex) => itemIndex !== index))}
                  >
                    移除
                  </Button>
                </FormField>
              </ModalGrid>
            </Card>
          ))}
          <Button
            icon={<IconPlus />}
            onClick={() => setMemberDrafts((current) => [
              ...current,
              { userId: '', username: '', memberRole: 'MEMBER' },
            ])}
          >
            添加成员
          </Button>
          <Card title="没有账号？先创建平台账号" style={{ width: '100%' }}>
            <ModalGrid>
              <FormField>
                <Text strong>账号</Text>
                <Input
                  value={newAccountForm.username}
                  placeholder="例如 ops_user"
                  onChange={(username) => setNewAccountForm((current) => ({ ...current, username }))}
                />
              </FormField>
              <FormField>
                <Text strong>初始密码</Text>
                <Input
                  type="password"
                  value={newAccountForm.password}
                  placeholder="保存后请让用户尽快修改"
                  onChange={(password) => setNewAccountForm((current) => ({ ...current, password }))}
                />
              </FormField>
              <FormField>
                <Text strong>平台角色</Text>
                <Select
                  value={newAccountForm.userRole}
                  style={{ width: '100%' }}
                  onChange={(userRole) => setNewAccountForm((current) => ({ ...current, userRole: String(userRole || 'user') }))}
                >
                  <Option value="user">普通用户</Option>
                  <Option value="admin">管理员</Option>
                </Select>
              </FormField>
              <FormField>
                <Text strong>操作</Text>
                <Button onClick={createPlatformAccount} loading={capabilitySaving}>创建账号</Button>
              </FormField>
            </ModalGrid>
          </Card>
        </Space>
      </Modal>

      <Modal
        title={editingProjectKnowledgeId ? '编辑项目知识库' : '新建项目知识库'}
        visible={projectKnowledgeModalVisible}
        width={680}
        confirmLoading={capabilitySaving}
        onCancel={() => {
          setProjectKnowledgeModalVisible(false);
          setEditingProjectKnowledgeId('');
        }}
        onOk={saveProjectKnowledgeBase}
      >
        <Space vertical align="start" spacing="medium" style={{ width: '100%' }}>
          <ModalGrid>
            {editingProjectKnowledgeId && (
              <FormField>
                <Text strong>系统编号</Text>
                <Input value={projectKnowledgeForm.kbId} disabled />
              </FormField>
            )}
            <FormField>
              <Text strong>知识库名称</Text>
              <Input
                value={projectKnowledgeForm.kbName}
                placeholder="服务架构与发布规范"
                onChange={(kbName) => setProjectKnowledgeForm((current) => ({ ...current, kbName }))}
              />
            </FormField>
          </ModalGrid>
          <FormField style={{ width: '100%' }}>
            <Text strong>说明</Text>
            <TextArea
              value={projectKnowledgeForm.description}
              autosize={{ minRows: 4, maxRows: 8 }}
              placeholder="说明文档范围、维护责任和适用 Agent"
              onChange={(description) => setProjectKnowledgeForm((current) => ({ ...current, description }))}
            />
          </FormField>
        </Space>
      </Modal>

      <Modal
        title="导入项目文档"
        visible={projectDocumentModalVisible}
        width={680}
        confirmLoading={capabilitySaving}
        onCancel={() => setProjectDocumentModalVisible(false)}
        onOk={uploadProjectDocuments}
      >
        <Space vertical align="start" spacing="medium" style={{ width: '100%' }}>
          <FormField style={{ width: '100%' }}>
            <Text strong>目标知识库</Text>
            <Select
              value={projectDocumentForm.kbId}
              style={{ width: '100%' }}
              onChange={(kbId) => setProjectDocumentForm((current) => ({
                ...current,
                kbId: String(kbId || ''),
              }))}
            >
              {selectedProjectKnowledgeIds.map((knowledgeId) => (
                <Option key={knowledgeId} value={knowledgeId}>
                  {projectKnowledgeDetails.find((item) => (item.kbId || item.knowledgeTag) === knowledgeId)?.kbName || knowledgeId}
                </Option>
              ))}
            </Select>
          </FormField>
          <FormField style={{ width: '100%' }}>
            <Text strong>文档批次名称</Text>
            <Input
              value={projectDocumentForm.name}
              placeholder="例如 2026-06 发布规范"
              onChange={(name) => setProjectDocumentForm((current) => ({ ...current, name }))}
            />
          </FormField>
          <FormField style={{ width: '100%' }}>
            <Text strong>文件</Text>
            <input
              type="file"
              multiple
              accept=".md,.markdown,.pdf,.txt"
              onChange={(event) => setProjectDocumentForm((current) => ({
                ...current,
                files: Array.from(event.target.files || []),
              }))}
            />
            <FieldHint>
              当前走真实异步解析与结构优先分段流程；任务创建成功不代表向量化已经完成，请在知识库页面查看入库状态。
            </FieldHint>
          </FormField>
        </Space>
      </Modal>

      <Modal
        title="MCP 生成配置"
        visible={previewVisible}
        width={820}
        footer={null}
        onCancel={() => setPreviewVisible(false)}
      >
        <JsonBlock>{toJson(previewData)}</JsonBlock>
      </Modal>
    </PageLayout>
  );
};
