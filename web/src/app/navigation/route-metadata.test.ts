import { describe, expect, it } from 'vitest';

import {
  APP_ROUTES,
  PLATFORM_NAVIGATION_GROUPS,
  breadcrumbsForPath,
  canonicalPathFor,
  navigationRoutesForRole,
  pageTitleForPath,
} from './route-metadata';

describe('route metadata', () => {
  it('keeps canonical and legacy paths globally unique', () => {
    const routeIds = APP_ROUTES.map((route) => route.id);
    const allPaths = APP_ROUTES.flatMap((route) => [route.path, ...(route.legacyPaths || [])]);

    expect(new Set(routeIds).size).toBe(routeIds.length);
    expect(new Set(allPaths).size).toBe(allPaths.length);
  });

  it('maps legacy bookmarks to the current product information architecture', () => {
    expect(canonicalPathFor('/dashboard')).toBe('/home');
    expect(canonicalPathFor('/work/diagnosis')).toBe('/chat');
    expect(canonicalPathFor('/executions')).toBe('/changes');
    expect(canonicalPathFor('/channel-management')).toBe('/settings/channels');
    expect(canonicalPathFor('/memory-management')).toBe('/settings/advanced/memory');
  });

  it('derives the eight first-level product entries from the same route metadata', () => {
    const userPaths = navigationRoutesForRole('user').map((route) => route.path);
    const adminPaths = navigationRoutesForRole('admin').map((route) => route.path);

    expect(userPaths).toEqual(['/home', '/chat', '/workbench', '/changes']);
    expect(adminPaths).toEqual([
      '/home',
      '/chat',
      '/workbench',
      '/workflows',
      '/automations',
      '/projects',
      '/changes',
      '/settings',
    ]);
  });

  it('groups lower-frequency settings without flattening them into first-level navigation', () => {
    expect(PLATFORM_NAVIGATION_GROUPS.map((group) => group.key)).toEqual([
      'integrations',
      'intelligence',
      'execution-governance',
      'advanced',
    ]);
    const subgroupOf = (id: string) => APP_ROUTES.find((route) => route.id === id)?.navigation?.subgroup;
    expect(subgroupOf('channels')).toBe('integrations');
    expect(subgroupOf('models')).toBe('intelligence');
    expect(subgroupOf('knowledge')).toBe('intelligence');
    expect(subgroupOf('tools')).toBe('execution-governance');
    expect(subgroupOf('governance')).toBe('execution-governance');
    expect(subgroupOf('memory')).toBe('advanced');
    expect(navigationRoutesForRole('admin').some((route) => route.id === 'channels')).toBe(false);
  });

  it('provides breadcrumb and page-title metadata without a second page map', () => {
    expect(breadcrumbsForPath('/settings')).toEqual(['设置']);
    expect(breadcrumbsForPath('/platform')).toEqual(['设置']);
    expect(breadcrumbsForPath('/settings/channels')).toEqual(['设置', '渠道']);
    expect(breadcrumbsForPath('/channel-management')).toEqual(['设置', '渠道']);
    expect(pageTitleForPath('/settings')).toBe('设置 · OrbisOps');
    expect(pageTitleForPath('/workbench/incidents')).toBe('工作台 · OrbisOps');
  });
});
