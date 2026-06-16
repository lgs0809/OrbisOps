import type { AppRouteMetadata, AppRouteRole } from '../../../app/navigation/route-metadata';

export type CommandPaletteItemKind = 'route' | 'project' | 'action';

export interface CommandPaletteProject {
  projectId: string;
  name: string;
}

export interface CommandPaletteItem {
  id: string;
  kind: CommandPaletteItemKind;
  label: string;
  description: string;
  keywords: string[];
  path?: string;
  projectId?: string;
}

const normalize = (value: string) => value.trim().toLocaleLowerCase();

const routeItems = (routes: readonly AppRouteMetadata[], role: AppRouteRole): CommandPaletteItem[] => routes
  .filter((route) => route.navigation && route.roles.includes(role))
  .map((route) => ({
    id: `route:${route.id}`,
    kind: 'route' as const,
    label: route.navigation?.label || route.title,
    description: route.breadcrumbs.join(' / '),
    path: route.path,
    keywords: [
      route.title,
      route.id,
      route.navigation?.section || '',
      route.navigation?.subgroup || '',
      ...route.breadcrumbs,
      ...(route.legacyPaths || []),
    ].filter(Boolean),
  }));

const projectItems = (projects: CommandPaletteProject[], currentProjectId: string): CommandPaletteItem[] => projects.map((project) => ({
  id: `project:${project.projectId}`,
  kind: 'project' as const,
  label: `切换 Project · ${project.name || project.projectId}`,
  description: project.projectId === currentProjectId ? '当前 Project' : project.projectId,
  projectId: project.projectId,
  keywords: ['project', 'switch', '切换', '项目', project.name, project.projectId, project.projectId === currentProjectId ? 'current' : ''],
}));

const actionItems = (role: AppRouteRole, currentProjectId: string): CommandPaletteItem[] => {
  const scoped = currentProjectId ? `?projectId=${encodeURIComponent(currentProjectId)}` : '';
  const actions: CommandPaletteItem[] = [
    {
      id: 'action:open-chat',
      kind: 'action',
      label: '打开对话',
      description: currentProjectId ? `在 ${currentProjectId} 中与 OrbisOps 对话` : '打开对话',
      path: `/chat${scoped}`,
      keywords: ['chat', 'diagnosis', 'investigate', 'react', 'workflow'],
    },
    {
      id: 'action:open-workbench',
      kind: 'action',
      label: '打开工作台',
      description: currentProjectId ? `查看 ${currentProjectId} 中的 Run` : '查看 Run',
      path: `/workbench${scoped}`,
      keywords: ['workbench', 'run', 'incident', 'alert', 'automation', 'execution'],
    },
  ];
  if (role === 'admin' && currentProjectId) {
    actions.push({
      id: 'action:project-runtime',
      kind: 'action',
      label: '打开当前 Project Runtime',
      description: currentProjectId,
      path: `/projects?projectId=${encodeURIComponent(currentProjectId)}&view=runtime`,
      keywords: ['project', 'resource', 'member', 'runtime', 'skill', 'knowledge', 'capability'],
    });
  }
  return actions;
};

export const buildCommandPaletteItems = (
  routes: readonly AppRouteMetadata[],
  role: AppRouteRole,
  projects: CommandPaletteProject[],
  currentProjectId: string,
): CommandPaletteItem[] => [
  ...actionItems(role, currentProjectId),
  ...routeItems(routes, role),
  ...projectItems(projects, currentProjectId),
];

export const filterCommandPaletteItems = (
  items: CommandPaletteItem[],
  query: string,
  limit = 14,
): CommandPaletteItem[] => {
  const needle = normalize(query);
  if (!needle) return items.slice(0, limit);
  return items
    .map((item, index) => {
      const label = normalize(item.label);
      const description = normalize(item.description);
      const keywords = item.keywords.map(normalize);
      const exactLabel = label === needle;
      const labelPrefix = label.startsWith(needle);
      const labelContains = label.includes(needle);
      const keywordPrefix = keywords.some((keyword) => keyword.startsWith(needle));
      const keywordContains = keywords.some((keyword) => keyword.includes(needle));
      const descriptionContains = description.includes(needle);
      const score = exactLabel ? 100 : labelPrefix ? 80 : labelContains ? 60 : keywordPrefix ? 50 : keywordContains ? 40 : descriptionContains ? 20 : 0;
      return { item, index, score };
    })
    .filter(({ score }) => score > 0)
    .sort((left, right) => right.score - left.score || left.index - right.index)
    .slice(0, limit)
    .map(({ item }) => item);
};

/** Rebind a queued navigation at activation; browser history can commit before
 * React finishes rendering the palette created under the previous project. */
export const commandPaletteTarget = (
  path: string,
  requestedProjectId: string,
  currentProjectId: string,
  projects: readonly CommandPaletteProject[],
): string => {
  const permitted = (id: string) => Boolean(id && projects.some((project) => project.projectId === id));
  const projectId = permitted(requestedProjectId) ? requestedProjectId : permitted(currentProjectId) ? currentProjectId : '';
  const target = new URL(path, 'http://orbisops.local');
  if (projectId) target.searchParams.set('projectId', projectId);
  else target.searchParams.delete('projectId');
  return `${target.pathname}${target.search}${target.hash}`;
};

export const commandPaletteDestination = (
  item: CommandPaletteItem | undefined,
  location: { pathname: string; search: string },
  currentProjectId: string,
  projects: readonly CommandPaletteProject[],
): string | undefined => {
  if (!item) return undefined;
  if (item.kind === 'project') {
    if (!item.projectId || !projects.some((project) => project.projectId === item.projectId)) return undefined;
    const target = new URL(`${location.pathname}${location.search}`, 'http://orbisops.local');
    target.searchParams.set('projectId', item.projectId);
    return `${target.pathname}${target.search}`;
  }
  return item.path ? commandPaletteTarget(item.path, new URLSearchParams(location.search).get('projectId') || '',
    currentProjectId, projects) : undefined;
};
