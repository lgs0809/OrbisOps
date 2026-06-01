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
  label: `Switch project · ${project.name || project.projectId}`,
  description: project.projectId === currentProjectId ? 'Current project' : project.projectId,
  projectId: project.projectId,
  keywords: ['project', 'switch', project.name, project.projectId, project.projectId === currentProjectId ? 'current' : ''],
}));

const actionItems = (role: AppRouteRole, currentProjectId: string): CommandPaletteItem[] => {
  const scoped = currentProjectId ? `?projectId=${encodeURIComponent(currentProjectId)}` : '';
  const actions: CommandPaletteItem[] = [
    {
      id: 'action:start-diagnosis',
      kind: 'action',
      label: 'Start Diagnosis',
      description: currentProjectId ? `Open Diagnosis in ${currentProjectId}` : 'Open Diagnosis',
      path: `/work/diagnosis${scoped}`,
      keywords: ['diagnosis', 'chat', 'investigate', 'react', 'run'],
    },
    {
      id: 'action:open-incidents',
      kind: 'action',
      label: 'Open Incidents',
      description: currentProjectId ? `View incidents in ${currentProjectId}` : 'View incidents',
      path: `/work/incidents${scoped}`,
      keywords: ['incident', 'alert', 'event', 'investigation'],
    },
  ];
  if (role === 'admin' && currentProjectId) {
    actions.push({
      id: 'action:project-advanced',
      kind: 'action',
      label: 'Open Current Project Advanced',
      description: currentProjectId,
      path: `/projects/advanced?projectId=${encodeURIComponent(currentProjectId)}`,
      keywords: ['project', 'advanced', 'resource', 'member', 'runtime', 'skill', 'knowledge'],
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
