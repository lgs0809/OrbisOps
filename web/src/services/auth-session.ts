export type UserRole = 'admin' | 'user' | 'unknown';

export interface StoredUserInfo {
  userId?: string;
  username?: string;
  phone?: string | null;
  loginTime?: string;
  token?: string;
  userRole?: string;
  role?: string;
  passwordChangeRequired?: boolean;
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
  if (getStoredUserInfo().passwordChangeRequired) return '/account/password';
  const role = currentUserRole();
  if (role === 'admin') {
    return '/chat';
  }
  if (role === 'user') {
    return '/chat';
  }
  return '/login';
};

export const saveAuthSession = (user: StoredUserInfo): void => {
  if (!user.token || normalizeUserRole(user.userRole || user.role) === 'unknown')
    throw new Error('未返回有效登录会话，请重新登录。');
  localStorage.setItem('token', user.token);
  localStorage.setItem('userInfo', JSON.stringify({ ...user, loginTime: new Date().toISOString() }));
  localStorage.setItem('isLoggedIn', 'true');
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
