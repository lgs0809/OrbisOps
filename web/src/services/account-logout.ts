import { Toast } from '@douyinfe/semi-ui';
import { AdminUserService } from './admin-user-service';
import { userFacingError } from '../utils/user-facing-error';

let pending: { token: string; request: Promise<void> } | undefined;

/** Header and sidebar share one acknowledged logout; a failed request leaves the session available for retry. */
export const logoutAccount = async (onLogout?: () => void): Promise<boolean> => {
  const token = localStorage.getItem('token') || '';
  if (!token) return false;
  if (!pending || pending.token !== token) pending = { token, request: AdminUserService.logout() };
  const request = pending.request;
  try {
    await request;
  } catch (error) {
    Toast.error(userFacingError(error, '注销未完成，请稍后重试。'));
    return false;
  } finally {
    if (pending?.request === request) pending = undefined;
  }
  // A response from a previous session must not sign out a newly authenticated user.
  if (localStorage.getItem('token') !== token) return false;
  onLogout?.();
  return true;
};
