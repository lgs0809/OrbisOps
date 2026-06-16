import type { UserRole } from '../../services/auth-session';

export type AppRouteRole = Exclude<UserRole, 'unknown'>;
export type NavigationSection = 'primary' | 'platform';
export type NavigationSubgroup = 'integrations' | 'intelligence' | 'execution-governance' | 'advanced';

export const PLATFORM_NAVIGATION_GROUPS: ReadonlyArray<{ key: NavigationSubgroup; label: string; order: number }> = [
  { key: 'integrations', label: '连接', order: 10 },
  { key: 'intelligence', label: '智能能力', order: 20 },
  { key: 'execution-governance', label: '执行与治理', order: 30 },
  { key: 'advanced', label: '高级', order: 40 },
];

export type NavigationIcon =
  | 'home'
  | 'chat'
  | 'workbench'
  | 'workflow'
  | 'automation'
  | 'project'
  | 'change'
  | 'settings'
  | 'integration'
  | 'model'
  | 'tool'
  | 'knowledge'
  | 'skill'
  | 'execution'
  | 'governance'
  | 'advanced';

export interface AppRouteNavigation {
  section: NavigationSection;
  label: string;
  order: number;
  icon?: NavigationIcon;
  subgroup?: NavigationSubgroup;
}

export interface AppRouteMetadata {
  id: string;
  path: string;
  title: string;
  roles: AppRouteRole[];
  breadcrumbs: string[];
  legacyPaths?: string[];
  navigation?: AppRouteNavigation;
}

const admin: AppRouteRole[] = ['admin'];
const everyone: AppRouteRole[] = ['admin', 'user'];

/** Product route metadata. Only `primary` entries render in the first-level sidebar. */
export const APP_ROUTES: readonly AppRouteMetadata[] = [
  {
    id: 'home',
    path: '/home',
    title: '首页',
    roles: everyone,
    breadcrumbs: ['首页'],
    legacyPaths: ['/dashboard'],
    navigation: { section: 'primary', label: '首页', order: 20, icon: 'home' },
  },
  {
    id: 'chat',
    path: '/chat',
    title: '对话',
    roles: everyone,
    breadcrumbs: ['对话'],
    legacyPaths: ['/work/diagnosis', '/chat-workspace'],
    navigation: { section: 'primary', label: '对话', order: 10, icon: 'chat' },
  },
  {
    id: 'workbench',
    path: '/workbench',
    title: '工作台',
    roles: everyone,
    breadcrumbs: ['工作台'],
    legacyPaths: ['/operations-workbench', '/work/incidents', '/workbench/analysis-tasks', '/platform/advanced/analysis-tasks', '/analysis-tasks', '/workbench/incidents', '/incidents', '/my-audit'],
    navigation: { section: 'primary', label: '工作台', order: 30, icon: 'workbench' },
  },
  {
    id: 'workbench-events',
    path: '/workbench/events',
    title: '事件中心',
    roles: everyone,
    breadcrumbs: ['工作台', '事件中心'],
  },
  {
    id: 'workflows',
    path: '/workflows',
    title: '工作流',
    roles: admin,
    breadcrumbs: ['工作流'],
    legacyPaths: ['/automations/workflows', '/agent-list'],
    navigation: { section: 'primary', label: '工作流', order: 40, icon: 'workflow' },
  },
  {
    id: 'automations',
    path: '/automations',
    title: '自动化',
    roles: admin,
    breadcrumbs: ['自动化'],
    navigation: { section: 'primary', label: '自动化', order: 50, icon: 'automation' },
  },
  {
    id: 'projects',
    path: '/projects',
    title: '项目',
    roles: admin,
    breadcrumbs: ['项目'],
    legacyPaths: ['/project-workspace'],
    navigation: { section: 'primary', label: '项目', order: 60, icon: 'project' },
  },
  {
    id: 'changes',
    path: '/changes',
    title: '变更',
    roles: everyone,
    breadcrumbs: ['变更'],
    legacyPaths: ['/work/changes', '/executions', '/my-executions', '/change-center', '/execution-center'],
    navigation: { section: 'primary', label: '变更', order: 70, icon: 'change' },
  },
  {
    id: 'settings',
    path: '/settings',
    title: '设置',
    roles: admin,
    breadcrumbs: ['设置'],
    legacyPaths: ['/platform'],
    navigation: { section: 'primary', label: '设置', order: 80, icon: 'settings' },
  },

  {
    id: 'workflow-editor',
    path: '/workflows/config',
    title: 'Workflow 编辑器',
    roles: admin,
    breadcrumbs: ['工作流', '编辑器'],
    legacyPaths: ['/automations/workflows/config', '/agent-config'],
  },
  {
    id: 'automation-schedules',
    path: '/automations/schedules',
    title: '定时自动化',
    roles: admin,
    breadcrumbs: ['自动化', '定时自动化'],
    legacyPaths: ['/automations/inspections', '/inspections'],
  },
  {
    id: 'alert-triggers',
    path: '/automations/alert-triggers',
    title: '告警触发器',
    roles: admin,
    breadcrumbs: ['自动化', '告警触发器'],
    legacyPaths: ['/alerts', '/alert-trigger-management'],
  },
  {
    id: 'project-advanced',
    path: '/projects/advanced',
    title: '项目运行时与高级配置',
    roles: admin,
    breadcrumbs: ['项目', '运行时与高级配置'],
    legacyPaths: ['/project-workspace/advanced'],
  },
  {
    id: 'settings-users',
    path: '/settings/users-access',
    title: '用户与访问控制',
    roles: admin,
    breadcrumbs: ['设置', '用户与访问控制'],
  },
  {
    id: 'channels',
    path: '/settings/channels',
    title: '渠道',
    roles: admin,
    breadcrumbs: ['设置', '渠道'],
    legacyPaths: ['/platform/integrations', '/channel-management'],
    navigation: { section: 'platform', subgroup: 'integrations', label: '渠道', order: 10, icon: 'integration' },
  },
  {
    id: 'models',
    path: '/settings/models',
    title: '模型',
    roles: admin,
    breadcrumbs: ['设置', '模型'],
    legacyPaths: ['/platform/models', '/model-api-management'],
    navigation: { section: 'platform', subgroup: 'intelligence', label: '模型', order: 10, icon: 'model' },
  },
  {
    id: 'tools',
    path: '/settings/tools',
    title: '工具 / MCP',
    roles: admin,
    breadcrumbs: ['设置', '工具 / MCP'],
    legacyPaths: ['/platform/tools', '/mcp-tool-management'],
    navigation: { section: 'platform', subgroup: 'execution-governance', label: '工具 / MCP', order: 10, icon: 'tool' },
  },
  {
    id: 'knowledge',
    path: '/settings/knowledge',
    title: '知识库',
    roles: admin,
    breadcrumbs: ['设置', '知识库'],
    legacyPaths: ['/platform/knowledge', '/rag-order-management'],
    navigation: { section: 'platform', subgroup: 'intelligence', label: '知识库', order: 20, icon: 'knowledge' },
  },
  {
    id: 'skills',
    path: '/settings/skills',
    title: 'Skill',
    roles: admin,
    breadcrumbs: ['设置', 'Skill'],
    legacyPaths: ['/platform/skills', '/skill-management'],
    navigation: { section: 'platform', subgroup: 'intelligence', label: 'Skill', order: 30, icon: 'skill' },
  },
  {
    id: 'execution-targets',
    path: '/settings/execution-targets',
    title: '执行目标',
    roles: admin,
    breadcrumbs: ['设置', '执行目标'],
    legacyPaths: ['/platform/execution-targets', '/execution-adapter-templates'],
    navigation: { section: 'platform', subgroup: 'execution-governance', label: '执行目标', order: 20, icon: 'execution' },
  },
  {
    id: 'governance',
    path: '/settings/governance',
    title: '治理与审计',
    roles: admin,
    breadcrumbs: ['设置', '治理与审计'],
    legacyPaths: ['/platform/governance', '/audit'],
    navigation: { section: 'platform', subgroup: 'execution-governance', label: '治理与审计', order: 30, icon: 'governance' },
  },
  {
    id: 'model-catalog',
    path: '/settings/advanced/model-catalog',
    title: '模型目录',
    roles: admin,
    breadcrumbs: ['设置', '高级', '模型目录'],
    legacyPaths: ['/platform/advanced/model-catalog', '/model-catalog'],
    navigation: { section: 'platform', subgroup: 'advanced', label: '模型目录', order: 10, icon: 'advanced' },
  },
  {
    id: 'memory',
    path: '/settings/advanced/memory',
    title: '记忆',
    roles: admin,
    breadcrumbs: ['设置', '高级', '记忆'],
    legacyPaths: ['/platform/advanced/memory', '/memory-management'],
    navigation: { section: 'platform', subgroup: 'advanced', label: '记忆', order: 20, icon: 'advanced' },
  },
  {
    id: 'skill-evolver',
    path: '/settings/advanced/skill-evolver',
    title: 'Skill 进化',
    roles: admin,
    breadcrumbs: ['设置', '高级', 'Skill 进化'],
    legacyPaths: ['/platform/advanced/skill-evolver', '/skill-evolver-management'],
    navigation: { section: 'platform', subgroup: 'advanced', label: 'Skill 进化', order: 30, icon: 'advanced' },
  },
  {
    id: 'tool-routing',
    path: '/settings/advanced/tool-routing',
    title: '工具路由观测',
    roles: admin,
    breadcrumbs: ['设置', '高级', '工具路由观测'],
    legacyPaths: ['/platform/advanced/tool-routing', '/tool-routing-observability'],
    navigation: { section: 'platform', subgroup: 'advanced', label: '工具路由观测', order: 40, icon: 'advanced' },
  },
] as const;

export const routeById = (id: string): AppRouteMetadata | undefined => APP_ROUTES.find((route) => route.id === id);

export const routeByPath = (path: string): AppRouteMetadata | undefined =>
  APP_ROUTES.find((route) => route.path === path || route.legacyPaths?.includes(path));

export const canonicalPathFor = (path: string): string => routeByPath(path)?.path || path;

export const routesVisibleToRole = (role: UserRole): AppRouteMetadata[] =>
  APP_ROUTES.filter((route) => role !== 'unknown' && route.roles.includes(role as AppRouteRole));

export const navigationRoutesForRole = (role: UserRole): AppRouteMetadata[] =>
  routesVisibleToRole(role).filter((route) => route.navigation?.section === 'primary');

export const breadcrumbsForPath = (path: string): string[] => routeByPath(path)?.breadcrumbs || [];

export const pageTitleForPath = (path: string): string => {
  const title = routeByPath(path)?.title;
  return title ? `${title} · OrbisOps` : 'OrbisOps';
};
