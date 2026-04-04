import type { UserRole } from '../../services/auth-session';

export type AppRouteRole = Exclude<UserRole, 'unknown'>;
export type NavigationSection = 'home' | 'work' | 'automations' | 'projects' | 'platform';
export type NavigationSubgroup = 'integrations' | 'intelligence' | 'execution-governance' | 'advanced';

export const PLATFORM_NAVIGATION_GROUPS: ReadonlyArray<{ key: NavigationSubgroup; label: string; order: number }> = [
  { key: 'integrations', label: 'Integrations', order: 10 },
  { key: 'intelligence', label: 'Intelligence', order: 20 },
  { key: 'execution-governance', label: 'Execution & Governance', order: 30 },
  { key: 'advanced', label: 'Advanced', order: 40 },
];

export type NavigationIcon =
  | 'home'
  | 'incident'
  | 'diagnosis'
  | 'change'
  | 'automation'
  | 'project'
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

/**
 * Product-level route metadata single source of truth.
 *
 * React page elements stay registered in app.tsx so this module remains cheap
 * to consume from navigation, breadcrumbs, page-title and command-palette code.
 */
export const APP_ROUTES: readonly AppRouteMetadata[] = [
  {
    id: 'home',
    path: '/home',
    title: 'Home',
    roles: everyone,
    breadcrumbs: ['Home'],
    legacyPaths: ['/dashboard'],
    navigation: { section: 'home', label: 'Home', order: 10, icon: 'home' },
  },
  {
    id: 'incidents',
    path: '/work/incidents',
    title: 'Incidents',
    roles: everyone,
    breadcrumbs: ['Work', 'Incidents'],
    legacyPaths: ['/incidents'],
    navigation: { section: 'work', label: 'Incidents', order: 10, icon: 'incident' },
  },
  {
    id: 'diagnosis',
    path: '/work/diagnosis',
    title: 'Diagnosis',
    roles: everyone,
    breadcrumbs: ['Work', 'Diagnosis'],
    legacyPaths: ['/chat', '/chat-workspace'],
    navigation: { section: 'work', label: 'Diagnosis', order: 20, icon: 'diagnosis' },
  },
  {
    id: 'changes',
    path: '/work/changes',
    title: 'Changes',
    roles: everyone,
    breadcrumbs: ['Work', 'Changes'],
    legacyPaths: ['/executions', '/my-executions', '/change-center', '/execution-center'],
    navigation: { section: 'work', label: 'Changes', order: 30, icon: 'change' },
  },
  {
    id: 'workflows',
    path: '/automations/workflows',
    title: 'Workflows',
    roles: admin,
    breadcrumbs: ['Automations', 'Workflows'],
    legacyPaths: ['/agent-list'],
    navigation: { section: 'automations', label: 'Workflows', order: 10, icon: 'automation' },
  },
  {
    id: 'workflow-editor',
    path: '/automations/workflows/config',
    title: 'Workflow Builder',
    roles: admin,
    breadcrumbs: ['Automations', 'Workflows', 'Builder'],
    legacyPaths: ['/agent-config'],
  },
  {
    id: 'inspections',
    path: '/automations/inspections',
    title: 'Inspections',
    roles: admin,
    breadcrumbs: ['Automations', 'Inspections'],
    legacyPaths: ['/inspections'],
    navigation: { section: 'automations', label: 'Inspections', order: 20, icon: 'automation' },
  },
  {
    id: 'alert-triggers',
    path: '/automations/alert-triggers',
    title: 'Alert Triggers',
    roles: admin,
    breadcrumbs: ['Automations', 'Alert Triggers'],
    legacyPaths: ['/alerts', '/alert-trigger-management'],
    navigation: { section: 'automations', label: 'Alert Triggers', order: 30, icon: 'automation' },
  },
  {
    id: 'projects',
    path: '/projects',
    title: 'Projects',
    roles: admin,
    breadcrumbs: ['Projects'],
    legacyPaths: ['/project-workspace'],
    navigation: { section: 'projects', label: 'Projects', order: 10, icon: 'project' },
  },
  {
    id: 'project-advanced',
    path: '/projects/advanced',
    title: 'Project Advanced Settings',
    roles: admin,
    breadcrumbs: ['Projects', 'Advanced'],
    legacyPaths: ['/project-workspace/advanced'],
  },
  {
    id: 'platform-settings',
    path: '/platform',
    title: 'Platform Settings',
    roles: admin,
    breadcrumbs: ['Platform'],
    navigation: { section: 'platform', label: 'Overview', order: 0, icon: 'advanced' },
  },
  {
    id: 'channels',
    path: '/platform/integrations',
    title: 'Integrations / Channels',
    roles: admin,
    breadcrumbs: ['Platform', 'Integrations / Channels'],
    legacyPaths: ['/channel-management'],
    navigation: { section: 'platform', subgroup: 'integrations', label: 'Channels', order: 10, icon: 'integration' },
  },
  {
    id: 'models',
    path: '/platform/models',
    title: 'Models',
    roles: admin,
    breadcrumbs: ['Platform', 'Models'],
    legacyPaths: ['/model-api-management'],
    navigation: { section: 'platform', subgroup: 'intelligence', label: 'Models', order: 10, icon: 'model' },
  },
  {
    id: 'tools',
    path: '/platform/tools',
    title: 'Tools / MCP',
    roles: admin,
    breadcrumbs: ['Platform', 'Tools / MCP'],
    legacyPaths: ['/mcp-tool-management'],
    navigation: { section: 'platform', subgroup: 'execution-governance', label: 'Tools / MCP', order: 10, icon: 'tool' },
  },
  {
    id: 'knowledge',
    path: '/platform/knowledge',
    title: 'Knowledge',
    roles: admin,
    breadcrumbs: ['Platform', 'Knowledge'],
    legacyPaths: ['/rag-order-management'],
    navigation: { section: 'platform', subgroup: 'intelligence', label: 'Knowledge', order: 20, icon: 'knowledge' },
  },
  {
    id: 'skills',
    path: '/platform/skills',
    title: 'Skills',
    roles: admin,
    breadcrumbs: ['Platform', 'Skills'],
    legacyPaths: ['/skill-management'],
    navigation: { section: 'platform', subgroup: 'intelligence', label: 'Skills', order: 30, icon: 'skill' },
  },
  {
    id: 'execution-targets',
    path: '/platform/execution-targets',
    title: 'Execution Targets',
    roles: admin,
    breadcrumbs: ['Platform', 'Execution Targets'],
    legacyPaths: ['/execution-adapter-templates'],
    navigation: { section: 'platform', subgroup: 'execution-governance', label: 'Execution Targets', order: 20, icon: 'execution' },
  },
  {
    id: 'governance',
    path: '/platform/governance',
    title: 'Governance',
    roles: admin,
    breadcrumbs: ['Platform', 'Governance'],
    legacyPaths: ['/audit'],
    navigation: { section: 'platform', subgroup: 'execution-governance', label: 'Governance', order: 30, icon: 'governance' },
  },
  {
    id: 'model-catalog',
    path: '/platform/advanced/model-catalog',
    title: 'Model Catalog',
    roles: admin,
    breadcrumbs: ['Platform', 'Advanced', 'Model Catalog'],
    legacyPaths: ['/model-catalog'],
    navigation: { section: 'platform', subgroup: 'advanced', label: 'Model Catalog', order: 10, icon: 'advanced' },
  },
  {
    id: 'memory',
    path: '/platform/advanced/memory',
    title: 'Memory',
    roles: admin,
    breadcrumbs: ['Platform', 'Advanced', 'Memory'],
    legacyPaths: ['/memory-management'],
    navigation: { section: 'platform', subgroup: 'advanced', label: 'Memory', order: 20, icon: 'advanced' },
  },
  {
    id: 'skill-evolver',
    path: '/platform/advanced/skill-evolver',
    title: 'Skill Evolver',
    roles: admin,
    breadcrumbs: ['Platform', 'Advanced', 'Skill Evolver'],
    legacyPaths: ['/skill-evolver-management'],
    navigation: { section: 'platform', subgroup: 'advanced', label: 'Skill Evolver', order: 30, icon: 'advanced' },
  },
  {
    id: 'tool-routing',
    path: '/platform/advanced/tool-routing',
    title: 'Tool Routing Observability',
    roles: admin,
    breadcrumbs: ['Platform', 'Advanced', 'Tool Routing'],
    legacyPaths: ['/tool-routing-observability'],
    navigation: { section: 'platform', subgroup: 'advanced', label: 'Tool Routing', order: 40, icon: 'advanced' },
  },
  {
    id: 'analysis-tasks',
    path: '/platform/advanced/analysis-tasks',
    title: 'Analysis Tasks',
    roles: admin,
    breadcrumbs: ['Platform', 'Advanced', 'Analysis Tasks'],
    legacyPaths: ['/analysis-tasks'],
    navigation: { section: 'platform', subgroup: 'advanced', label: 'Analysis Tasks', order: 50, icon: 'advanced' },
  },
  {
    id: 'my-audit',
    path: '/my-audit',
    title: 'My Audit Records',
    roles: ['user'],
    breadcrumbs: ['My Audit Records'],
  },
] as const;

export const routeById = (id: string): AppRouteMetadata | undefined => APP_ROUTES.find((route) => route.id === id);

export const routeByPath = (path: string): AppRouteMetadata | undefined =>
  APP_ROUTES.find((route) => route.path === path || route.legacyPaths?.includes(path));

export const canonicalPathFor = (path: string): string => routeByPath(path)?.path || path;

export const routesVisibleToRole = (role: UserRole): AppRouteMetadata[] =>
  APP_ROUTES.filter((route) => role !== 'unknown' && route.roles.includes(role as AppRouteRole));

export const navigationRoutesForRole = (role: UserRole): AppRouteMetadata[] =>
  routesVisibleToRole(role).filter((route) => route.navigation);

export const breadcrumbsForPath = (path: string): string[] => routeByPath(path)?.breadcrumbs || [];

export const pageTitleForPath = (path: string): string => {
  const title = routeByPath(path)?.title;
  return title ? `${title} · OrbisOps` : 'OrbisOps';
};
