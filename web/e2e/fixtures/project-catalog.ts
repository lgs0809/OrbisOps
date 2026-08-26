import type { Route } from '@playwright/test';

// Scope identities are permission-filtered catalog reads. Full admin workspace
// projections are a separate endpoint and retain their own request counters.
export const fulfillAccessibleProjects = async (
  route: Route,
  projects: readonly { projectId: string; name: string; [key: string]: unknown }[],
): Promise<boolean> => {
  const path = new URL(route.request().url()).pathname;
  if (!['/api/v1/agent/chat/catalog/projects', '/api/v1/user/chat/catalog/projects'].includes(path)) return false;
  if (route.request().method() !== 'GET') throw new Error('Project catalog must be read-only');
  await route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({ code: '0000', info: 'success', data: projects }),
  });
  return true;
};
