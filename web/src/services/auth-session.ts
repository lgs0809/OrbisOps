export type UserRole = 'admin' | 'user' | 'unknown';

export interface StoredUserInfo {
  userId?: string;
  username?: string;
  loginTime?: string;
  token?: string;
  userRole?: string;
  role?: string;
}

export const normalizeUserRole = (role?: string): UserRole => {
  const normalized = role?.trim().toLowerCase();
  if (normalized === 'admin') {
    return 'admin';
  }
  if (normalized === 'user') {
    return 'user';
  }
  return 'unknown';
};

export const getStoredUserInfo = (): StoredUserInfo => {
  if (typeof localStorage === 'undefined') {
    return {};
  }
  try {
    return JSON.parse(localStorage.getItem('userInfo') || '{}');
  } catch (error) {
    return {};
  }
};

export const currentUserRole = (): UserRole => {
  const userInfo = getStoredUserInfo();
  return normalizeUserRole(userInfo.userRole || userInfo.role);
};

export const isAuthenticated = (): boolean => {
  if (typeof localStorage === 'undefined') {
    return false;
  }
  return Boolean(
    localStorage.getItem('token') &&
      localStorage.getItem('userInfo') &&
      localStorage.getItem('isLoggedIn') &&
      currentUserRole() !== 'unknown',
  );
};

export const isAdminUser = (): boolean => currentUserRole() === 'admin';

export const defaultRouteForCurrentUser = (): string => {
  const role = currentUserRole();
  if (role === 'admin') {
    return '/chat';
  }
  if (role === 'user') {
    return '/chat';
  }
  return '/login';
};

export const clearAuthSession = () => {
  if (typeof localStorage === 'undefined') {
    return;
  }
  localStorage.removeItem('token');
  localStorage.removeItem('userInfo');
  localStorage.removeItem('isLoggedIn');
};

export const roleLabel = (role?: string): string =>
  normalizeUserRole(role) === 'admin' ? '管理员' : normalizeUserRole(role) === 'user' ? '普通用户' : '未授权';
