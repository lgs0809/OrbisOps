import { describe, expect, it } from 'vitest';
import { APP_ROUTES } from '../../../app/navigation/route-metadata';
import { platformSettingsGroups, settingsCategoryFrom } from './settings-navigation';

describe('settings directory navigation', () => {
  it('keeps bookmark and history categories within the existing platform groups', () => {
    expect(settingsCategoryFrom('intelligence')).toBe('intelligence');
    expect(settingsCategoryFrom('advanced')).toBe('advanced');
    expect(settingsCategoryFrom('unknown')).toBe('account');
    expect(settingsCategoryFrom(null)).toBe('account');
  });
  it('uses each authorized platform route once without inventing destinations', () => {
    const directory = platformSettingsGroups.flatMap((group) => group.routes);
    const registered = APP_ROUTES.filter((route) => route.navigation?.section === 'platform');
    expect(directory.map((route) => route.path).sort()).toEqual(registered.map((route) => route.path).sort());
    expect(new Set(directory.map((route) => route.id)).size).toBe(directory.length);
    expect(directory.every((route) => route.roles.length === 1 && route.roles[0] === 'admin')).toBe(true);
  });
});
