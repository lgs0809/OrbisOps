import React, { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import styled from 'styled-components';
import {
  Breadcrumb,
  Button,
  Input,
  Layout,
  Modal,
  Select,
  Space,
  Spin,
  Table,
  Tag,
  TextArea,
  Toast,
  Typography,
} from '@douyinfe/semi-ui';
import { IconArrowLeft, IconCopy, IconFile, IconRefresh, IconSearch } from '@douyinfe/semi-icons';
import { clearAuthSession } from '../services/auth-session';

import { Header, Sidebar, SIDEBAR_COLLAPSED_WIDTH, SIDEBAR_WIDTH } from '../components/layout';
import { TableScroll } from '../components/ops-layout';
import { SkillPackageEditor } from '../components/skill/SkillPackageEditor';
import { theme } from '../styles/theme';
import {
  OpsSkillDetail,
  OpsSkillArtifact,
  OpsSkillReference,
  OpsSkillSummary,
} from '../services/ops-admin-service';
import {
  useCopyGlobalSkillMutation,
  useCreateGlobalSkillMutation,
  useReloadSkillsMutation,
  useRollbackGlobalSkillMutation,
  useSaveGlobalSkillMutation,
  useSkillCatalogQuery,
  useSkillContextQuery,
  useSkillCopyProjectsQuery,
  useSkillDetailQuery,
  useSkillUsageQuery,
  useSkillVersionsQuery,
  useUpdateGlobalSkillModeMutation,
  useUpdateGlobalSkillStatusMutation,
} from '../features/skills/api/skill-queries';
import { generatedId } from '../utils/generated-id';
import { useResponsiveSidebar } from '../hooks/use-responsive-sidebar';

const { Content } = Layout;
const { Title, Text, Paragraph } = Typography;

const PageLayout = styled(Layout)`
  min-height: 100vh;
  min-width: 0;
  width: 100%;
  background: ${theme.colors.bg.secondary};
  overflow-x: hidden;
`;

const MainContent = styled.div<{ $collapsed: boolean }>`
  height: 100vh;
  min-height: 100vh;
  min-width: 0;
  width: ${(props) =>
    props.$collapsed
      ? `calc(100vw - ${SIDEBAR_COLLAPSED_WIDTH}px)`
      : `calc(100vw - ${SIDEBAR_WIDTH}px)`};
  margin-left: ${(props) => (props.$collapsed ? `${SIDEBAR_COLLAPSED_WIDTH}px` : `${SIDEBAR_WIDTH}px`)};
  display: flex;
  flex: 1;
  flex-direction: column;
  background: ${theme.colors.bg.secondary};
  overflow-x: hidden;
  overflow-y: auto;
  transition:
    width ${theme.animation.duration.normal} ${theme.animation.easing.cubic},
    margin-left ${theme.animation.duration.normal} ${theme.animation.easing.cubic};

  @media (max-width: ${theme.breakpoints.md}) {
    width: 100vw;
    margin-left: 0;
  }
`;

const ContentArea = styled(Content)`
  width: 100%;
  max-width: 1440px;
  min-width: 0;
  margin: 0 auto;
  padding: 24px;
  overflow-x: hidden;

  @media (max-width: 1280px) {
    padding: 16px;
  }
`;

const PageContainer = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${theme.spacing.base};
  min-width: 0;
  width: 100%;
`;

const PageHeader = styled.div`
  display: flex;
  justify-content: space-between;
  gap: ${theme.spacing.lg};
  align-items: flex-start;
  padding: ${theme.spacing.lg};
  border: 1px solid ${theme.colors.border.secondary};
  border-radius: ${theme.borderRadius.base};
  background: ${theme.colors.bg.primary};
  min-width: 0;

  > * {
    min-width: 0;
  }

  @media (max-width: ${theme.breakpoints.sm}) {
    flex-direction: column;
    gap: ${theme.spacing.base};

    > .semi-space {
      width: 100%;
      flex-wrap: wrap;
    }
  }
`;

const WorkspaceGrid = styled.div`
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: ${theme.spacing.base};
  min-width: 0;
`;

const Panel = styled.div`
  border: 1px solid ${theme.colors.border.secondary};
  border-radius: ${theme.borderRadius.base};
  background: ${theme.colors.bg.primary};
  overflow: hidden;
  min-width: 0;
`;

const PanelHeader = styled.div`
  display: flex;
  justify-content: space-between;
  gap: ${theme.spacing.base};
  align-items: center;
  padding: ${theme.spacing.base};
  border-bottom: 1px solid ${theme.colors.border.secondary};
  min-width: 0;
  flex-wrap: wrap;
`;

const DetailActions = styled.div`
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: ${theme.spacing.xs};
  min-width: 0;

  @media (max-width: ${theme.breakpoints.sm}) {
    width: 100%;
    justify-content: flex-start;

    .semi-button {
      flex: 0 1 auto;
    }
  }
`;

const PanelBody = styled.div`
  padding: ${theme.spacing.base};
  min-width: 0;
`;

const Toolbar = styled.div`
  display: flex;
  gap: ${theme.spacing.sm};
  align-items: center;
  padding: ${theme.spacing.base};
  border-bottom: 1px solid ${theme.colors.border.secondary};
  min-width: 0;
  flex-wrap: wrap;
`;

const DetailStack = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${theme.spacing.base};
  min-width: 0;
`;

const MetaGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: ${theme.spacing.base};
  min-width: 0;

  @media (max-width: 760px) {
    grid-template-columns: 1fr;
  }
`;

const MetaBox = styled.div`
  padding: ${theme.spacing.base};
  border: 1px solid ${theme.colors.border.secondary};
  border-radius: ${theme.borderRadius.base};
  background: ${theme.colors.bg.secondary};
  min-width: 0;
`;

const ReferenceList = styled.div`
  display: flex;
  flex-wrap: wrap;
  gap: ${theme.spacing.xs};
`;

const CodeBlock = styled.pre`
  width: 100%;
  max-width: 100%;
  max-height: 420px;
  margin: ${theme.spacing.xs} 0 0;
  padding: ${theme.spacing.base};
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-word;
  border: 1px solid ${theme.colors.border.secondary};
  border-radius: ${theme.borderRadius.base};
  background: ${theme.colors.bg.secondary};
  color: ${theme.colors.text.primary};
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', monospace;
  font-size: 12px;
  line-height: 1.6;
`;

const EmptyDetail = styled.div`
  min-height: 360px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: ${theme.colors.text.tertiary};
`;

const handleNavigationFactory = (navigate: ReturnType<typeof useNavigate>) => (path: string) => {
  switch (path) {
    case 'rag-order-management':
      navigate('/rag-order-management');
      break;
    case 'agent-config':
      navigate('/agent-config');
      break;
    case 'agent-list':
      navigate('/agent-list');
      break;
    case 'skill-management':
      navigate('/skill-management');
      break;
    default:
      navigate(`/${path}`);
      break;
  }
};

const referenceText = (reference: NonNullable<OpsSkillSummary['referencedBy']>[number]) => {
  if (reference.type === 'agent') {
    return `${reference.agentName || reference.agentId}`;
  }
  return `${reference.agentName || reference.agentId}/${reference.targetId || reference.type}`;
};

type SkillScopeMeta = OpsSkillSummary & {
  skillId?: string;
  scope?: string;
  projectId?: string;
};

const skillIdOf = (skill: OpsSkillSummary | OpsSkillDetail | null | undefined) => {
  const meta = skill as SkillScopeMeta | null | undefined;
  return String(meta?.skillId || skill?.name || '');
};

const skillScopeOf = (skill: OpsSkillSummary) => {
  const meta = skill as SkillScopeMeta;
  return String(meta.scope || meta.frontMatter?.scope || '').toUpperCase();
};

const skillProjectIdOf = (skill: OpsSkillSummary) => {
  const meta = skill as SkillScopeMeta;
  return String(meta.projectId || meta.frontMatter?.projectId || '');
};

const hasScopeMetadata = (skill: OpsSkillSummary) => Boolean(skillScopeOf(skill) || skillProjectIdOf(skill));

const isProjectScopedSkill = (skill: OpsSkillSummary) =>
  skillScopeOf(skill) === 'PROJECT' || Boolean(skillProjectIdOf(skill));

const updateModeOf = (skill: OpsSkillSummary | OpsSkillDetail | null | undefined) =>
  String(skill?.updateMode || skill?.update_mode || 'AUTO').toUpperCase();

const boolOf = (value: unknown, fallback = true) => {
  if (typeof value === 'boolean') return value;
  if (typeof value === 'number') return value === 1;
  if (typeof value === 'string') return ['1', 'true', 'yes', 'enabled'].includes(value.toLowerCase());
  return fallback;
};

const autoUpdateOf = (skill: OpsSkillSummary | OpsSkillDetail | null | undefined) =>
  boolOf(skill?.autoUpdateEnabled ?? skill?.auto_update_enabled, true);

const autoMergeOf = (skill: OpsSkillSummary | OpsSkillDetail | null | undefined) =>
  boolOf(skill?.autoMergeEnabled ?? skill?.auto_merge_enabled, true);

const originOf = (skill: OpsSkillSummary | OpsSkillDetail | null | undefined) =>
  String(skill?.origin || skill?.sourceType || 'MANUAL').toUpperCase();

const isCatalogManaged = (skill: OpsSkillSummary | OpsSkillDetail | null | undefined) =>
  skill?.catalogManaged !== false;

const storageLocationLabel = (skill: OpsSkillSummary | OpsSkillDetail | null | undefined) => {
  if (!skill?.name) return '-';
  return `${isCatalogManaged(skill) ? '平台方法库' : '应用内置方法'} / ${skill.name}`;
};

const updateModeLabel = (skill: OpsSkillSummary | OpsSkillDetail | null | undefined) => {
  const mode = updateModeOf(skill);
  if (mode === 'FROZEN') return '已冻结';
  if (mode === 'MANUAL_ONLY') return '仅人工维护';
  return '自动更新';
};

const originLabel = (skill: OpsSkillSummary | OpsSkillDetail | null | undefined) => {
  const origin = originOf(skill);
  if (origin === 'BUILT_IN') return '平台内置';
  if (origin === 'EVOLVED') return '自动沉淀';
  if (origin === 'IMPORTED') return '导入';
  return '人工创建';
};

const statusLabel = (skill: OpsSkillSummary | OpsSkillDetail | null | undefined) => {
  if (!isCatalogManaged(skill)) return '平台内置（只读）';
  const status = String(skill?.status || 'ENABLED').toUpperCase();
  if (['ACTIVE', 'ENABLED'].includes(status)) return '可用';
  if (status === 'FROZEN') return '已冻结';
  if (status === 'PAUSED') return '已暂停';
  if (status === 'DEPRECATED') return '已废弃';
  if (status === 'REJECTED') return '已拒绝';
  return '已停用';
};

const routingValues = (value: string) =>
  value
    .split(/[\n,，|]+/)
    .map((item) => item.trim())
    .filter(Boolean);

const mergeSkillSources = (catalogSkills: OpsSkillSummary[], builtInSkills: OpsSkillSummary[]) => {
  const managed = catalogSkills.map((skill) => ({
    ...skill,
    catalogManaged: true,
    builtIn: false,
  }));
  const managedNames = new Set(managed.map((skill) => String(skill.name || '').trim().toLowerCase()));
  return [
    ...managed,
    ...builtInSkills
      .filter((skill) => !managedNames.has(String(skill.name || '').trim().toLowerCase()))
      .map((skill) => ({
        ...skill,
        catalogManaged: false,
        builtIn: true,
        status: 'ENABLED',
        origin: 'BUILT_IN',
        updateMode: 'MANUAL_ONLY',
        autoUpdateEnabled: false,
        autoMergeEnabled: false,
      })),
  ];
};

export const SkillManagementPage: React.FC = () => {
  const navigate = useNavigate();
  const { collapsed, sidebarCollapsed, toggleSidebar, closeMobileSidebar } = useResponsiveSidebar(false);
  const [sessionReady, setSessionReady] = useState(false);
  const catalogQuery = useSkillCatalogQuery(sessionReady);
  const reloadSkillsMutation = useReloadSkillsMutation();
  const saveSkillMutation = useSaveGlobalSkillMutation();
  const createSkillMutation = useCreateGlobalSkillMutation();
  const updateStatusMutation = useUpdateGlobalSkillStatusMutation();
  const updateModeMutation = useUpdateGlobalSkillModeMutation();
  const rollbackMutation = useRollbackGlobalSkillMutation();
  const copySkillMutation = useCopyGlobalSkillMutation();
  const skills = useMemo(
    () => mergeSkillSources(catalogQuery.data?.catalogSkills || [], catalogQuery.data?.builtInSkills || []),
    [catalogQuery.data],
  );
  const [selectedName, setSelectedName] = useState('');
  const [searchText, setSearchText] = useState('');
  const [editMode, setEditMode] = useState(false);
  const [draftContent, setDraftContent] = useState('');
  const [draftArtifacts, setDraftArtifacts] = useState<OpsSkillArtifact[]>([]);
  const [createVisible, setCreateVisible] = useState(false);
  const [createForm, setCreateForm] = useState({
    skillId: '',
    name: '',
    description: '',
    category: 'GENERAL',
    subcategory: '',
    whenToUse: '',
    whenNotToUse: '',
    keywords: '',
    content: '',
    artifacts: [] as OpsSkillArtifact[],
  });
  const [usageVisible, setUsageVisible] = useState(false);
  const [copyVisible, setCopyVisible] = useState(false);
  const [copyForm, setCopyForm] = useState({
    projectId: '',
    skillId: '',
    name: '',
    description: '',
  });
  const [versionVisible, setVersionVisible] = useState(false);

  const handleNavigation = handleNavigationFactory(navigate);

  const backendReturnsScope = useMemo(() => skills.some(hasScopeMetadata), [skills]);
  const visibleGlobalSkills = useMemo(
    () => (backendReturnsScope ? skills.filter((skill) => !isProjectScopedSkill(skill)) : skills),
    [backendReturnsScope, skills],
  );

  const filteredSkills = useMemo(() => {
    const keyword = searchText.trim().toLowerCase();
    if (!keyword) {
      return visibleGlobalSkills;
    }
    return visibleGlobalSkills.filter((skill) =>
      [skill.name, skill.description, skill.basePath]
        .filter(Boolean)
        .some((value) => String(value).toLowerCase().includes(keyword))
    );
  }, [searchText, visibleGlobalSkills]);

  const effectiveSelectedName = selectedName && visibleGlobalSkills.some((skill) => skillIdOf(skill) === selectedName)
    ? selectedName
    : skillIdOf(visibleGlobalSkills[0]) || '';
  const selectedSkill = visibleGlobalSkills.find((skill) => skillIdOf(skill) === effectiveSelectedName);
  const selectedSourceName = selectedSkill?.name || effectiveSelectedName;
  const detailQuery = useSkillDetailQuery(selectedSkill ? {
    skillId: effectiveSelectedName,
    sourceName: selectedSourceName,
    catalogManaged: isCatalogManaged(selectedSkill),
    summary: selectedSkill,
  } : undefined);
  const detail = detailQuery.data || null;
  const contextQuery = useSkillContextQuery(selectedSourceName, Boolean(detail));
  const usageQuery = useSkillUsageQuery(effectiveSelectedName, usageVisible && Boolean(detail && isCatalogManaged(detail)));
  const versionsQuery = useSkillVersionsQuery(effectiveSelectedName, versionVisible && Boolean(detail && isCatalogManaged(detail)));
  const copyProjectsQuery = useSkillCopyProjectsQuery(copyVisible);
  const projects = copyProjectsQuery.data || [];
  const usageRecords = usageQuery.data || [];
  const skillVersions = versionsQuery.data || [];
  const contextPreview = contextQuery.isError ? 'Skill Context 预览加载失败，请稍后刷新。' : (contextQuery.data || '');
  const loading = catalogQuery.isLoading || reloadSkillsMutation.isPending;
  const detailLoading = detailQuery.isLoading;
  const contextLoading = contextQuery.isLoading || contextQuery.isFetching;
  const saving = saveSkillMutation.isPending
    || createSkillMutation.isPending
    || updateStatusMutation.isPending
    || updateModeMutation.isPending
    || rollbackMutation.isPending;
  const copySaving = copySkillMutation.isPending;
  const usageLoading = usageQuery.isLoading;
  const versionLoading = versionsQuery.isLoading;

  const loadedMarkdown = useMemo(() => detail?.content || detail?.markdown || '', [detail]);
  const loadedArtifacts = useMemo(() => (detail?.artifacts || []).filter((item) => item.path !== 'SKILL.md'), [detail]);
  const hasUnsavedChanges = editMode && (
    draftContent !== loadedMarkdown || JSON.stringify(draftArtifacts) !== JSON.stringify(loadedArtifacts)
  );

  useEffect(() => {
    const token = localStorage.getItem('token');
    const storedUserInfo = localStorage.getItem('userInfo');
    if (!token || !storedUserInfo) {
      Toast.error('请先登录');
      navigate('/login');
      return;
    }
    try {
      JSON.parse(storedUserInfo);
      setSessionReady(true);
    } catch (error) {
      Toast.error('用户信息解析失败');
      navigate('/login');
      return;
    }
  }, [navigate]);

  useEffect(() => {
    if (!detail) {
      if (!detailQuery.isLoading) {
        setEditMode(false);
        setDraftContent('');
        setDraftArtifacts([]);
      }
      return;
    }
    setDraftContent(detail.content || detail.markdown || '');
    setDraftArtifacts((detail.artifacts || []).filter((item) => item.path !== 'SKILL.md'));
    setEditMode(false);
  }, [detail, detailQuery.isLoading, effectiveSelectedName]);

  useEffect(() => {
    if (!copyVisible || copyForm.projectId || !projects[0]?.projectId) return;
    setCopyForm((current) => ({ ...current, projectId: projects[0].projectId }));
  }, [copyForm.projectId, copyVisible, projects]);

  const reloadSkills = async () => {
    try {
      await reloadSkillsMutation.mutateAsync();
      Toast.success('Skill 已刷新');
    } catch (error) {
      console.error('刷新 Skill 失败:', error);
      Toast.error(error instanceof Error ? error.message : '刷新 Skill 失败');
    }
  };

  const copyText = async (text: string, message: string) => {
    await navigator.clipboard.writeText(text);
    Toast.success(message);
  };

  const selectSkill = (name: string) => {
    if (name === effectiveSelectedName) {
      return;
    }
    if (hasUnsavedChanges && !window.confirm('当前 Skill 有未保存修改，切换后会丢失这些修改。确认继续？')) {
      return;
    }
    setSelectedName(name);
  };

  const saveSkill = async () => {
    if (!detail?.name) return;
    try {
      const skillId = skillIdOf(detail);
      await saveSkillMutation.mutateAsync({
        skillId,
        payload: {
          content: draftContent,
          name: detail.name,
          description: detail.description,
          status: detail.status || 'ENABLED',
          artifacts: draftArtifacts,
          evalSuites: draftArtifacts.filter((item) => item.role === 'EVAL').map((item) => item.path),
        },
      });
      setEditMode(false);
      Toast.success('Skill 已保存');
    } catch (error) {
      console.error('保存 Skill 失败:', error);
      Toast.error(error instanceof Error ? error.message : '保存 Skill 失败');
    }
  };

  const createSkill = async () => {
    if (!createForm.name.trim()) {
      Toast.warning('请填写方法名称');
      return;
    }
    if (!createForm.whenToUse.trim()) {
      Toast.warning('请填写适用场景');
      return;
    }
    if (!createForm.whenNotToUse.trim()) {
      Toast.warning('请填写禁用场景');
      return;
    }
    try {
      const result = await createSkillMutation.mutateAsync({
        skillId: createForm.skillId.trim() || generatedId('skill', createForm.name),
        name: createForm.name.trim(),
        description: createForm.description,
        category: createForm.category,
        subcategory: createForm.subcategory.trim(),
        whenToUse: routingValues(createForm.whenToUse),
        whenNotToUse: routingValues(createForm.whenNotToUse),
        keywords: routingValues(createForm.keywords),
        content: createForm.content,
        status: 'ENABLED',
        artifacts: createForm.artifacts,
        evalSuites: createForm.artifacts.filter((item) => item.role === 'EVAL').map((item) => item.path),
      });
      setCreateVisible(false);
      resetCreateForm();
      setSelectedName(skillIdOf(result));
      Toast.success('通用 Skill 已创建');
    } catch (error) {
      Toast.error(error instanceof Error ? error.message : '创建通用 Skill 失败');
    }
  };

  const addBuiltInToCatalog = () => {
    if (!detail || isCatalogManaged(detail)) return;
    const frontMatter = detail.frontMatter || {};
    setCreateForm({
      skillId: generatedId('skill', detail.name),
      name: detail.name,
      description: detail.description || '',
      category: String(frontMatter.category || 'GENERAL'),
      subcategory: String(frontMatter.subcategory || ''),
      whenToUse: Array.isArray(frontMatter.whenToUse) ? frontMatter.whenToUse.join('\n') : '',
      whenNotToUse: Array.isArray(frontMatter.whenNotToUse) ? frontMatter.whenNotToUse.join('\n') : '',
      keywords: Array.isArray(frontMatter.keywords) ? frontMatter.keywords.join(', ') : '',
      content: detail.content || detail.markdown || '',
      artifacts: (detail.artifacts || []).filter((item) => item.path !== 'SKILL.md'),
    });
    setCreateVisible(true);
  };

  const resetCreateForm = () => setCreateForm({
    skillId: '',
    name: '',
    description: '',
    category: 'GENERAL',
    subcategory: '',
    whenToUse: '',
    whenNotToUse: '',
    keywords: '',
    content: '',
    artifacts: [],
  });

  const openCreateSkill = () => {
    resetCreateForm();
    setCreateVisible(true);
  };

  const toggleSkillStatus = async () => {
    if (!detail) return;
    const skillId = skillIdOf(detail);
    const nextStatus = String(detail.status || 'ENABLED').toUpperCase() === 'ENABLED' ? 'DISABLED' : 'ENABLED';
    if (nextStatus === 'DISABLED' && !window.confirm(`确认停用通用 Skill ${skillId}？`)) return;
    try {
      await updateStatusMutation.mutateAsync({ skillId, status: nextStatus });
      Toast.success(nextStatus === 'ENABLED' ? 'Skill 已启用' : 'Skill 已停用');
    } catch (error) {
      Toast.error(error instanceof Error ? error.message : '更新 Skill 状态失败');
    }
  };

  const updateSkillMode = async (updateMode: 'AUTO' | 'MANUAL_ONLY' | 'FROZEN') => {
    if (!detail) return;
    try {
      await updateModeMutation.mutateAsync({
        skillId: skillIdOf(detail),
        payload: {
          updateMode,
          autoUpdateEnabled: updateMode === 'AUTO',
          autoMergeEnabled: updateMode === 'AUTO',
        },
      });
      Toast.success(updateMode === 'FROZEN' ? 'Skill 已冻结' : `Skill 更新策略已切换为 ${updateMode}`);
    } catch (error) {
      Toast.error(error instanceof Error ? error.message : '更新 Skill 更新策略失败');
    }
  };

  const openVersions = () => {
    if (!detail) return;
    setVersionVisible(true);
  };

  const rollbackVersion = async (version: number) => {
    if (!detail || !window.confirm(`确认回滚 ${skillIdOf(detail)} 到版本 ${version}？`)) return;
    try {
      await rollbackMutation.mutateAsync({ skillId: skillIdOf(detail), version });
      setVersionVisible(false);
      Toast.success('Skill 已回滚');
    } catch (error) {
      Toast.error(error instanceof Error ? error.message : '回滚 Skill 版本失败');
    }
  };

  const openUsage = () => {
    if (!detail) return;
    setUsageVisible(true);
  };

  const openCopyToProject = () => {
    if (!detail) return;
    setCopyVisible(true);
    setCopyForm({
      projectId: projects[0]?.projectId || '',
      skillId: '',
      name: detail.name || skillIdOf(detail),
      description: detail.description || '',
    });
  };

  const copyToProject = async () => {
    if (!detail || !copyForm.projectId || !copyForm.name.trim()) {
      Toast.warning('请选择项目并填写项目 Skill 名称');
      return;
    }
    try {
      await copySkillMutation.mutateAsync({
        projectId: copyForm.projectId,
        payload: {
          globalSkillId: skillIdOf(detail),
          skillId: copyForm.skillId.trim() || generatedId('skill', copyForm.name),
          name: copyForm.name.trim(),
          description: copyForm.description.trim(),
        },
      });
      setCopyVisible(false);
      Toast.success('已生成项目 Skill');
    } catch (error) {
      Toast.error(error instanceof Error ? error.message : '复制 Skill 到项目失败');
    }
  };

  const handleLogout = () => {
    clearAuthSession();
    Toast.success('已退出登录');
    navigate('/login');
  };

  const columns = [
    {
      title: 'Skill',
      dataIndex: 'name',
      width: 220,
      render: (name: string, record: OpsSkillSummary) => (
        <Space vertical align="start" spacing={2}>
          <Button
            theme="borderless"
            type="primary"
            onClick={(event) => {
              event.stopPropagation();
              selectSkill(skillIdOf(record));
            }}
            style={{ padding: 0 }}
          >
            {name}
          </Button>
          <Text type="tertiary" size="small" ellipsis={{ showTooltip: true }}>
            {storageLocationLabel(record)}
          </Text>
        </Space>
      ),
    },
    {
      title: '描述',
      dataIndex: 'description',
      render: (description: string) => (
        <Text ellipsis={{ showTooltip: true }}>{description || '-'}</Text>
      ),
    },
    {
      title: '引用',
      width: 120,
      render: (_: unknown, record: OpsSkillSummary) => (
        <Tag color={(record.referencedBy?.length || 0) > 0 ? 'green' : 'grey'}>
          {record.referencedBy?.length || 0} 处
        </Tag>
      ),
    },
    {
      title: '更新策略',
      width: 150,
      render: (_: unknown, record: OpsSkillSummary) => (
        <Tag color={updateModeOf(record) === 'FROZEN' ? 'red' : updateModeOf(record) === 'AUTO' ? 'green' : 'grey'}>
          {isCatalogManaged(record) ? updateModeLabel(record) : '平台内置'}
        </Tag>
      ),
    },
    {
      title: '长度',
      dataIndex: 'contentLength',
      width: 90,
      render: (length: number) => <Text type="tertiary">{length || 0}</Text>,
    },
    {
      title: '操作',
      width: 110,
      fixed: 'right' as const,
      render: (_: unknown, record: OpsSkillSummary) => (
        <Button
          size="small"
          onClick={(event) => {
            event.stopPropagation();
            selectSkill(skillIdOf(record));
          }}
        >
          查看内容
        </Button>
      ),
    },
  ];

  return (
    <PageLayout>
      <Sidebar collapsed={sidebarCollapsed} selectedKey="skill-management" onSelect={handleNavigation} onMobileClose={closeMobileSidebar} />
      <MainContent $collapsed={collapsed} data-ops-scroll-region>
        <ContentArea>
          <Header
            collapsed={sidebarCollapsed}
            onToggleSidebar={toggleSidebar}
            onLogout={handleLogout}
          />
          <PageContainer>
            <PageHeader>
              <div>
                <Breadcrumb style={{ marginBottom: theme.spacing.sm }}>
                  <Breadcrumb.Item onClick={() => navigate('/project-workspace')} style={{ cursor: 'pointer' }}>
                    项目空间
                  </Breadcrumb.Item>
                  <Breadcrumb.Item>资源与接入</Breadcrumb.Item>
                  <Breadcrumb.Item>通用方法库（Skill）</Breadcrumb.Item>
                </Breadcrumb>
                <Title heading={3} style={{ margin: 0 }}>
                  通用方法库（Skill）
                </Title>
                <Paragraph type="tertiary" style={{ margin: `${theme.spacing.sm} 0 0` }}>
                  这里维护跨项目复用的诊断经验、SOP 模板和报告生成能力。项目专属 Skill 请在项目空间中配置。
                </Paragraph>
              </div>
              <Space>
                <Button icon={<IconArrowLeft />} onClick={() => navigate('/project-workspace')}>
                  返回项目空间
                </Button>
                <Button icon={<IconRefresh />} loading={loading} onClick={reloadSkills}>
                  刷新 Skill
                </Button>
              </Space>
            </PageHeader>

            <WorkspaceGrid>
              <Panel>
                <PanelHeader>
                  <Space>
                    <IconFile />
                    <Text strong>已加载通用方法</Text>
                    <Tag color="blue">{visibleGlobalSkills.length}</Tag>
                  </Space>
                  <Button type="primary" onClick={openCreateSkill}>
                    新建通用方法
                  </Button>
                </PanelHeader>
                <Toolbar>
                  <Space vertical align="start" style={{ width: '100%' }}>
                    {!backendReturnsScope && (
                      <Text type="tertiary" size="small">
                        当前后端未返回 scope，默认按通用方法展示。
                      </Text>
                    )}
                    <Input
                      prefix={<IconSearch />}
                      placeholder="搜索名称或描述"
                      value={searchText}
                      onChange={setSearchText}
                      showClear
                    />
                  </Space>
                </Toolbar>
                {catalogQuery.isError && (
                  <EmptyDetail>{catalogQuery.error instanceof Error ? catalogQuery.error.message : '通用方法库加载失败，请刷新后重试。'}</EmptyDetail>
                )}
                <TableScroll>
                  <Table
                    rowKey="name"
                    columns={columns}
                    dataSource={filteredSkills}
                    loading={loading}
                    pagination={false}
                    size="small"
                    onRow={(record) => ({
                      onClick: () => {
                        if (record) {
                          selectSkill(skillIdOf(record));
                        }
                      },
                      style: { cursor: 'pointer', background: record && skillIdOf(record) === effectiveSelectedName ? '#eff6ff' : undefined },
                    })}
                  />
                </TableScroll>
              </Panel>

              <Panel>
                <PanelHeader>
                  <Space>
                    <IconFile />
                    <Text strong>{detail?.name || 'Skill 详情'}</Text>
                    {detail?.name && (
                      <Tag color={isCatalogManaged(detail) ? 'green' : 'blue'}>{statusLabel(detail)}</Tag>
                    )}
                  </Space>
                  {detail?.name && (
                    <DetailActions>
                      {!isCatalogManaged(detail) ? (
                        <Button type="primary" loading={saving} onClick={addBuiltInToCatalog}>
                          加入通用方法库
                        </Button>
                      ) : editMode ? (
                        <>
                          <Button
                            onClick={() => {
                              setDraftContent(loadedMarkdown);
                              setDraftArtifacts(loadedArtifacts);
                              setEditMode(false);
                            }}
                            disabled={saving}
                          >
                            取消
                          </Button>
                          <Button type="primary" loading={saving} onClick={saveSkill}>
                            保存修改
                          </Button>
                        </>
                      ) : (
                        <Button type="primary" onClick={() => setEditMode(true)}>
                          编辑
                        </Button>
                      )}
                      {isCatalogManaged(detail) && (
                        <>
                          <Button onClick={openVersions}>查看版本</Button>
                          <Button disabled={saving} onClick={() => updateSkillMode('AUTO')}>允许自动更新</Button>
                          <Button disabled={saving} onClick={() => updateSkillMode('MANUAL_ONLY')}>仅人工维护</Button>
                          <Button type="danger" theme="borderless" disabled={saving} onClick={() => updateSkillMode('FROZEN')}>冻结内容</Button>
                          <Button type="danger" theme="borderless" loading={saving} onClick={toggleSkillStatus}>
                            {String(detail.status || 'ENABLED').toUpperCase() === 'ENABLED' ? '停用' : '启用'}
                          </Button>
                          <Button onClick={openCopyToProject}>复制到项目</Button>
                          <Button onClick={openUsage}>查看使用项目</Button>
                        </>
                      )}
                      <Button icon={<IconCopy />} onClick={() => copyText(detail.name || '', 'Skill 名称已复制')}>
                        复制名称
                      </Button>
                      <Button
                        icon={<IconCopy />}
                        onClick={() => copyText(draftContent || loadedMarkdown, 'Skill 内容已复制')}
                      >
                        复制内容
                      </Button>
                    </DetailActions>
                  )}
                </PanelHeader>
                <PanelBody>
                  <Spin spinning={detailLoading}>
                    {detailQuery.isError ? (
                      <EmptyDetail>{detailQuery.error instanceof Error ? detailQuery.error.message : 'Skill 详情加载失败，请刷新后重试。'}</EmptyDetail>
                    ) : !detail ? (
                      <EmptyDetail>{effectiveSelectedName ? 'Skill 详情加载中...' : '请选择一个 Skill。'}</EmptyDetail>
                    ) : (
                      <DetailStack>
                        <MetaGrid>
                          <MetaBox>
                            <Text strong>描述</Text>
                            <Paragraph style={{ margin: `${theme.spacing.xs} 0 0` }}>
                              {detail.description || '-'}
                            </Paragraph>
                          </MetaBox>
                          <MetaBox>
                            <Text strong>存储位置</Text>
                            <Paragraph type="tertiary" style={{ margin: `${theme.spacing.xs} 0 0` }}>
                              {storageLocationLabel(detail)}
                            </Paragraph>
                          </MetaBox>
                          <MetaBox>
                            <Text strong>自动进化策略</Text>
                            <Space wrap style={{ marginTop: theme.spacing.xs }}>
                              <Tag color="blue">来源：{originLabel(detail)}</Tag>
                              <Tag color={updateModeOf(detail) === 'FROZEN' ? 'red' : updateModeOf(detail) === 'AUTO' ? 'green' : 'grey'}>
                                更新方式：{isCatalogManaged(detail) ? updateModeLabel(detail) : '只读，需要先加入方法库'}
                              </Tag>
                              <Tag color={autoUpdateOf(detail) ? 'green' : 'grey'}>
                                自动生成新版本：{autoUpdateOf(detail) ? '允许' : '不允许'}
                              </Tag>
                              <Tag color={autoMergeOf(detail) ? 'green' : 'grey'}>
                                自动发布：{autoMergeOf(detail) ? '允许' : '不允许'}
                              </Tag>
                            </Space>
                          </MetaBox>
                          <MetaBox>
                            <Text strong>最近进化</Text>
                            <Paragraph type="tertiary" style={{ margin: `${theme.spacing.xs} 0 0` }}>
                              {detail.lastEvolvedAt || detail.last_evolved_at || '暂无自动进化记录'}
                            </Paragraph>
                          </MetaBox>
                        </MetaGrid>

                        <MetaBox>
                          <Space vertical align="start" style={{ width: '100%' }}>
                            <Text strong>引用位置</Text>
                            <ReferenceList>
                              {(detail.referencedBy || []).length === 0 ? (
                                <Tag color="grey">未被 Agent 显式引用</Tag>
                              ) : (
                                detail.referencedBy?.map((reference, index) => (
                                  <Tag key={`${reference.type}-${reference.agentId}-${reference.targetId}-${index}`} color="green">
                                    {reference.type}: {referenceText(reference)}
                                  </Tag>
                                ))
                              )}
                            </ReferenceList>
                          </Space>
                        </MetaBox>

                        <div>
                          <Text strong>适用范围与运行参数</Text>
                          <CodeBlock>{JSON.stringify(detail.frontMatter || {}, null, 2)}</CodeBlock>
                        </div>

                        <SkillPackageEditor
                          entrypointContent={editMode ? draftContent : loadedMarkdown}
                          artifacts={editMode ? draftArtifacts : loadedArtifacts}
                          readOnly={!editMode || !isCatalogManaged(detail)}
                          disabled={saving}
                          onEntrypointChange={setDraftContent}
                          onArtifactsChange={setDraftArtifacts}
                        />

                        <div>
                          <Text strong>Agent 实际读取的内容</Text>
                          <CodeBlock>{contextLoading ? '内容预览加载中...' : contextPreview}</CodeBlock>
                        </div>
                      </DetailStack>
                    )}
                  </Spin>
                </PanelBody>
              </Panel>
            </WorkspaceGrid>
          </PageContainer>
        </ContentArea>
      </MainContent>
      <Modal
        title="新建通用方法（Skill）"
        visible={createVisible}
        width={960}
        confirmLoading={saving}
        onCancel={() => {
          setCreateVisible(false);
          resetCreateForm();
        }}
        onOk={createSkill}
      >
        <Space vertical align="start" spacing="medium" style={{ width: '100%' }}>
          <Input
            value={createForm.name}
            placeholder="方法名称，例如 故障复盘摘要"
            onChange={(name) => setCreateForm((current) => ({ ...current, name }))}
          />
          <Input
            value={createForm.description}
            placeholder="简要说明这个方法解决什么问题"
            onChange={(description) => setCreateForm((current) => ({ ...current, description }))}
          />
          <Select
            value={createForm.category}
            style={{ width: '100%' }}
            optionList={[
              { label: '文档与演示', value: 'DOCUMENT' },
              { label: '研发与代码', value: 'DEVELOPMENT' },
              { label: '数据与数据库', value: 'DATA' },
              { label: '日志、指标与链路', value: 'OBSERVABILITY' },
              { label: '运维与发布', value: 'OPERATIONS' },
              { label: '通知与协作', value: 'COMMUNICATION' },
              { label: '知识库与检索', value: 'KNOWLEDGE' },
              { label: '通用', value: 'GENERAL' },
            ]}
            onChange={(category) => setCreateForm((current) => ({ ...current, category: String(category) }))}
          />
          <Input
            value={createForm.subcategory}
            placeholder="细分类（可选），例如季度汇报、Java 故障诊断"
            onChange={(subcategory) => setCreateForm((current) => ({ ...current, subcategory }))}
          />
          <TextArea
            value={createForm.whenToUse}
            autosize={{ minRows: 2, maxRows: 5 }}
            placeholder="适用场景（必填）。每行一个，例如：用户需要制作季度汇报或融资路演"
            onChange={(whenToUse) => setCreateForm((current) => ({ ...current, whenToUse }))}
          />
          <TextArea
            value={createForm.whenNotToUse}
            autosize={{ minRows: 2, maxRows: 5 }}
            placeholder="禁用场景（必填）。每行一个，例如：不用于后端接口开发或数据计算"
            onChange={(whenNotToUse) => setCreateForm((current) => ({ ...current, whenNotToUse }))}
          />
          <Input
            value={createForm.keywords}
            placeholder="检索关键词（可选），用逗号分隔"
            onChange={(keywords) => setCreateForm((current) => ({ ...current, keywords }))}
          />
          <SkillPackageEditor
            entrypointContent={createForm.content}
            artifacts={createForm.artifacts}
            disabled={saving}
            onEntrypointChange={(content) => setCreateForm((current) => ({ ...current, content }))}
            onArtifactsChange={(artifacts) => setCreateForm((current) => ({ ...current, artifacts }))}
          />
        </Space>
      </Modal>
      <Modal
        title={`复制到项目${detail?.name ? ` · ${detail.name}` : ''}`}
        visible={copyVisible}
        width={640}
        confirmLoading={copySaving}
        okText="生成项目 Skill"
        cancelText="取消"
        onOk={copyToProject}
        onCancel={() => {
          if (!copySaving) {
            setCopyVisible(false);
          }
        }}
      >
        <Space vertical align="start" spacing="medium" style={{ width: '100%' }}>
          <Text type="tertiary">
            复制会创建独立的项目方法（Skill），后续修改不会影响当前通用方法。
          </Text>
          <Select
            value={copyForm.projectId || undefined}
            placeholder="选择目标项目"
            style={{ width: '100%' }}
            onChange={(projectId) => setCopyForm((current) => ({ ...current, projectId: String(projectId || '') }))}
            loading={copyProjectsQuery.isLoading}
            emptyContent={copyProjectsQuery.isError ? '项目加载失败，请稍后重试' : '暂无可用项目'}
          >
            {projects.map((project) => (
              <Select.Option key={project.projectId} value={project.projectId}>
                {project.name}（{project.projectId}）
              </Select.Option>
            ))}
          </Select>
          <Input
            value={copyForm.name}
            placeholder="项目 Skill 名称"
            onChange={(name) => setCopyForm((current) => ({ ...current, name }))}
          />
          <TextArea
            value={copyForm.description}
            placeholder="项目内适用范围说明"
            autosize={{ minRows: 3, maxRows: 6 }}
            onChange={(description) => setCopyForm((current) => ({ ...current, description }))}
          />
        </Space>
      </Modal>
      <Modal
        title={`使用项目与 Agent${detail?.name ? ` · ${detail.name}` : ''}`}
        visible={usageVisible}
        width={760}
        footer={null}
        onCancel={() => setUsageVisible(false)}
      >
        <TableScroll>
          <Table
            rowKey={(record) => record
              ? `${record.projectId || ''}-${record.agentId || ''}-${record.targetId || record.type}`
              : ''}
            loading={usageLoading}
            dataSource={usageRecords}
            pagination={false}
            columns={[
              {
                title: '项目',
                dataIndex: 'projectId',
                width: 160,
                render: (value: string) => value || '-',
              },
              {
                title: 'Agent',
                dataIndex: 'agentName',
                render: (value: string, record: OpsSkillReference) => value || record.agentId || '-',
              },
              {
                title: '绑定位置',
                key: 'target',
                width: 180,
                render: (_: unknown, record: OpsSkillReference) =>
                  record.type === 'node' ? `节点：${record.targetId || '-'}` : 'Agent 默认能力',
              },
            ]}
            empty={<EmptyDetail>{usageQuery.isError ? '使用位置加载失败，请稍后重试。' : '当前没有 Agent 或节点显式引用该 Skill。'}</EmptyDetail>}
          />
        </TableScroll>
      </Modal>
      <Modal
        title={`Skill 版本${detail?.name ? ` · ${detail.name}` : ''}`}
        visible={versionVisible}
        width={900}
        footer={null}
        onCancel={() => setVersionVisible(false)}
      >
        <TableScroll>
          <Table
            rowKey={(record) => `${record?.skillId || record?.skill_id || ''}-${record?.version || ''}`}
            loading={versionLoading}
            dataSource={skillVersions}
            pagination={{ pageSize: 6 }}
            columns={[
              {
                title: '版本',
                dataIndex: 'version',
                width: 90,
              },
              {
                title: '来源',
                render: (_: unknown, record: Record<string, any>) => (
                  <Space vertical align="start" spacing={2}>
                    <Tag>{record.sourceType || record.source_type || '-'}</Tag>
                    <Text type="tertiary">{record.sourceTraceId || record.source_trace_id || '-'}</Text>
                  </Space>
                ),
              },
              {
                title: '变更说明',
                render: (_: unknown, record: Record<string, any>) => record.changeSummary || record.change_summary || '-',
              },
              {
                title: '创建时间',
                width: 180,
                render: (_: unknown, record: Record<string, any>) => record.createTime || record.create_time || '-',
              },
              {
                title: '操作',
                width: 110,
                render: (_: unknown, record: Record<string, any>) => (
                  <Button
                    size="small"
                    type="danger"
                    theme="borderless"
                    loading={saving}
                    onClick={() => rollbackVersion(Number(record.version))}
                  >
                    回滚
                  </Button>
                ),
              },
            ]}
            empty={<EmptyDetail>{versionsQuery.isError ? '版本记录加载失败，请稍后重试。' : '暂无版本记录。'}</EmptyDetail>}
          />
        </TableScroll>
      </Modal>
    </PageLayout>
  );
};
