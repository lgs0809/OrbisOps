export type ProjectWorkspaceAdvancedTab = 'overview' | 'access' | 'knowledge' | 'runtime' | 'data-tools';

export interface ProjectWorkspaceAdvancedTabDefinition {
  key: ProjectWorkspaceAdvancedTab;
  label: string;
  description: string;
  sectionTitles: string[];
}

export const projectWorkspaceAdvancedTabs: ProjectWorkspaceAdvancedTabDefinition[] = [
  {
    key: 'overview',
    label: 'Overview',
    description: 'Readiness, first diagnosis, and project defaults.',
    sectionTitles: ['项目概览', '开始第一次真实诊断', '项目默认能力'],
  },
  {
    key: 'access',
    label: 'Access',
    description: 'Project members and who can use published capabilities.',
    sectionTitles: ['项目成员与使用权限'],
  },
  {
    key: 'knowledge',
    label: 'Knowledge & Skills',
    description: 'Project-specific SOP, skills, and authorized knowledge bases.',
    sectionTitles: ['项目 Skill', '项目知识库'],
  },
  {
    key: 'runtime',
    label: 'Code & Runtime',
    description: 'Source repositories, services, and governed execution targets.',
    sectionTitles: ['代码与服务', '变更执行目标'],
  },
  {
    key: 'data-tools',
    label: 'Data & Tools',
    description: 'Read-only evidence sources and generated project tools.',
    sectionTitles: ['数据连接与工具', '已生成工具'],
  },
];

export const projectWorkspaceTab = (value?: string | null): ProjectWorkspaceAdvancedTab => {
  const normalized = String(value || '').trim();
  return projectWorkspaceAdvancedTabs.some((tab) => tab.key === normalized)
    ? normalized as ProjectWorkspaceAdvancedTab
    : 'overview';
};

export const projectWorkspaceTabDefinition = (key: ProjectWorkspaceAdvancedTab): ProjectWorkspaceAdvancedTabDefinition => (
  projectWorkspaceAdvancedTabs.find((tab) => tab.key === key) || projectWorkspaceAdvancedTabs[0]
);
