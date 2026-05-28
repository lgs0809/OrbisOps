import { describe, expect, it } from 'vitest';

import {
  projectWorkspaceAdvancedTabs,
  projectWorkspaceTab,
  projectWorkspaceTabDefinition,
} from './project-workspace-tabs';

describe('project workspace advanced tabs', () => {
  it('groups advanced configuration into stable product feature domains', () => {
    expect(projectWorkspaceAdvancedTabs.map((tab) => tab.key)).toEqual([
      'overview', 'access', 'knowledge', 'runtime', 'data-tools',
    ]);
    expect(projectWorkspaceTabDefinition('knowledge').sectionTitles).toEqual(['项目 Skill', '项目知识库']);
    expect(projectWorkspaceTabDefinition('runtime').sectionTitles).toEqual(['代码与服务', '变更执行目标']);
  });

  it('fails closed to overview for unknown deep links', () => {
    expect(projectWorkspaceTab('knowledge')).toBe('knowledge');
    expect(projectWorkspaceTab('not-a-tab')).toBe('overview');
    expect(projectWorkspaceTab()).toBe('overview');
  });
});
