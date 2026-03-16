import { describe, expect, it } from 'vitest';

import {
  APP_ROUTES,
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

  it('maps legacy bookmarks to the new product information architecture', () => {
    expect(canonicalPathFor('/dashboard')).toBe('/home');
    expect(canonicalPathFor('/chat')).toBe('/work/diagnosis');
    expect(canonicalPathFor('/executions')).toBe('/work/changes');
    expect(canonicalPathFor('/channel-management')).toBe('/platform/integrations');
    expect(canonicalPathFor('/memory-management')).toBe('/platform/advanced/memory');
  });

  it('derives role-visible navigation from the same route metadata', () => {
    const userPaths = navigationRoutesForRole('user').map((route) => route.path);
    const adminPaths = navigationRoutesForRole('admin').map((route) => route.path);

    expect(userPaths).toEqual(['/home', '/work/incidents', '/work/diagnosis', '/work/changes']);
    expect(adminPaths).toContain('/automations/workflows');
    expect(adminPaths).toContain('/projects');
    expect(adminPaths).toContain('/platform/integrations');
    expect(adminPaths).toContain('/platform/governance');
    expect(adminPaths).toContain('/platform/advanced/memory');
  });

  it('provides breadcrumb and page-title metadata without a second page map', () => {
    expect(breadcrumbsForPath('/platform/integrations')).toEqual(['Platform', 'Integrations / Channels']);
    expect(breadcrumbsForPath('/channel-management')).toEqual(['Platform', 'Integrations / Channels']);
    expect(pageTitleForPath('/work/incidents')).toBe('Incidents · OrbisOps');
  });
});
