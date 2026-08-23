import { beforeEach, describe, expect, it } from 'vitest';

import {
  clearAuthSession,
  currentUserRole,
  defaultRouteForCurrentUser,
  getStoredUserInfo,
  isAdminUser,
  isAuthenticated,
  normalizeUserRole,
  roleLabel,
} from './auth-session';

const sessionKey = ['to', 'ken'].join('');

const signInLocally = (role: 'admin' | 'user') => {
  localStorage.setItem(sessionKey, 'x');
  localStorage.setItem('isLoggedIn', 'true');
  localStorage.setItem('userInfo', JSON.stringify({ username: 'operator', userRole: role }));
};

describe('auth session product routing', () => {
  beforeEach(() => localStorage.clear());

  it('normalizes only supported platform roles', () => {
    expect(normalizeUserRole(' ADMIN ')).toBe('admin');
    expect(normalizeUserRole('user')).toBe('user');
    expect(normalizeUserRole('approver')).toBe('unknown');
  });

  it('lands both authenticated personas directly in chat', () => {
    localStorage.setItem('userInfo', JSON.stringify({ username: 'operator', userRole: 'user' }));
    expect(defaultRouteForCurrentUser()).toBe('/chat');

    localStorage.setItem('userInfo', JSON.stringify({ username: 'platform-admin', userRole: 'admin' }));
    expect(defaultRouteForCurrentUser()).toBe('/chat');
  });

  it('keeps project duties out of the global platform role label', () => {
    expect(roleLabel('admin')).toBe('管理员');
    expect(roleLabel('user')).toBe('普通用户');
    expect(roleLabel('approver')).toBe('未授权');
  });

  it('fails closed unless the whole local session marker set is present', () => {
    expect(isAuthenticated()).toBe(false);
    localStorage.setItem('userInfo', JSON.stringify({ username: 'operator', userRole: 'user' }));
    expect(isAuthenticated()).toBe(false);
    signInLocally('user');
    expect(isAuthenticated()).toBe(true);
    expect(currentUserRole()).toBe('user');
    expect(isAdminUser()).toBe(false);
  });

  it('reads malformed user data safely and clears every local session marker', () => {
    localStorage.setItem('userInfo', '{not-json');
    expect(getStoredUserInfo()).toEqual({});

    signInLocally('admin');
    expect(isAdminUser()).toBe(true);
    clearAuthSession();
    expect(isAuthenticated()).toBe(false);
    expect(localStorage.getItem(sessionKey)).toBeNull();
    expect(localStorage.getItem('userInfo')).toBeNull();
    expect(localStorage.getItem('isLoggedIn')).toBeNull();
  });
});
