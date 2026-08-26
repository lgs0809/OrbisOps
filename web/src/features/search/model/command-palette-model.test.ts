import { describe, expect, it } from 'vitest';

import { APP_ROUTES } from '../../../app/navigation/route-metadata';
import { buildCommandPaletteItems, commandPaletteDestination, commandPaletteTarget, filterCommandPaletteItems } from './command-palette-model';

describe('global command palette model', () => {
  it('uses route metadata as the navigation source of truth and respects roles', () => {
    const adminItems = buildCommandPaletteItems(APP_ROUTES, 'admin', [{ projectId: 'p1', name: 'Payments' }], 'p1');
    const userItems = buildCommandPaletteItems(APP_ROUTES, 'user', [{ projectId: 'p1', name: 'Payments' }], 'p1');

    expect(adminItems.some((item) => item.id === 'route:channels')).toBe(true);
    expect(adminItems.some((item) => item.id === 'route:models')).toBe(true);
    expect(userItems.some((item) => item.id === 'route:channels')).toBe(false);
    expect(userItems.some((item) => item.id === 'route:chat')).toBe(true);
    expect(userItems.some((item) => item.id === 'route:workbench')).toBe(true);
  });

  it('adds project switch and current-project actions without a second project catalog', () => {
    const items = buildCommandPaletteItems(
      APP_ROUTES,
      'admin',
      [{ projectId: 'p1', name: 'Payments' }, { projectId: 'p2', name: 'Orders' }],
      'p2',
    );

    expect(items.find((item) => item.id === 'project:p2')?.description).toBe('当前 Project');
    expect(items.find((item) => item.id === 'action:open-chat')?.path).toBe('/chat?projectId=p2');
    expect(items.find((item) => item.id === 'action:open-workbench')?.path).toBe('/workbench?projectId=p2');
    expect(items.find((item) => item.id === 'action:project-runtime')?.path).toBe('/projects?projectId=p2&view=runtime');
  });

  it('ranks labels before keyword/description matches and supports legacy route terms', () => {
    const items = buildCommandPaletteItems(APP_ROUTES, 'admin', [], '');

    expect(filterCommandPaletteItems(items, 'models')[0]?.id).toBe('route:models');
    expect(filterCommandPaletteItems(items, 'mcp').some((item) => item.id === 'route:tools')).toBe(true);
    expect(filterCommandPaletteItems(items, 'channel-management')[0]?.id).toBe('route:channels');
  });
});

it('rebinds stale search actions to the current authorized browser project without losing other route parameters', () => {
  expect(commandPaletteTarget('/projects?projectId=payments&view=runtime', 'orders', 'payments', [
    { projectId: 'payments', name: 'Payments' }, { projectId: 'orders', name: 'Orders' },
  ])).toBe('/projects?projectId=orders&view=runtime');
});
it('does not propagate an inaccessible browser or stale action project into navigation', () => {
  expect(commandPaletteTarget('/chat?projectId=foreign', 'foreign', 'payments', [
    { projectId: 'payments', name: 'Payments' },
  ])).toBe('/chat?projectId=payments');
  expect(commandPaletteTarget('/chat?projectId=foreign', 'foreign', 'payments', [])).toBe('/chat');
});

it('uses the activation surface for a project switch while retaining its deep-link parameters', () => {
  const item = { id: 'project:orders', kind: 'project' as const, label: 'Orders', description: '', keywords: [], projectId: 'orders' };
  expect(commandPaletteDestination(item, { pathname: '/settings/tools', search: '?projectId=payments&view=generated' },
    'payments', [{ projectId: 'orders', name: 'Orders' }])).toBe('/settings/tools?projectId=orders&view=generated');
});
it('rejects a stale project command after its identity is removed from the authorized catalog', () => {
  const item = { id: 'project:foreign', kind: 'project' as const, label: 'Foreign', description: '', keywords: [], projectId: 'foreign' };
  expect(commandPaletteDestination(item, { pathname: '/home', search: '' }, 'payments', [
    { projectId: 'payments', name: 'Payments' },
  ])).toBeUndefined();
});
