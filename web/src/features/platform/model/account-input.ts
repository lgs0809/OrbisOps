/** Phone is a login identifier; display name and immutable userId remain separate. */
export const normalizePhone = (value: string): string => {
  const phone = value.trim();
  if (phone.startsWith('+86')) return phone.slice(3).trim();
  if (phone.startsWith('86') && phone.length === 13) return phone.slice(2);
  return phone;
};

export const phoneInputError = (value: string): string | undefined => (
  /^1[3-9][0-9]{9}$/.test(normalizePhone(value)) ? undefined : '请输入有效的中国大陆手机号。'
);

export const newPasswordError = (password: string, label = '初始密码'): string | undefined => {
  if (password.length < 8) return `${label}至少 8 个字符。`;
  if (!/\p{L}/u.test(password) || !/\p{N}/u.test(password)) return `${label}必须同时包含字母和数字。`;
  return undefined;
};

export const displayNameError = (name: string): string | undefined => {
  if (!name.trim()) return '请输入姓名。';
  if (name.trim().length > 50) return '姓名不能超过 50 个字符。';
  return undefined;
};

export const accountStatus = (status?: number): { label: string; color: 'green' | 'grey' | 'red'; canToggle: boolean } => {
  if (status === 1) return { label: '已启用', color: 'green', canToggle: true };
  if (status === 0) return { label: '已停用', color: 'grey', canToggle: true };
  if (status === 2) return { label: '已锁定', color: 'red', canToggle: false };
  return { label: '状态待确认', color: 'grey', canToggle: false };
};
