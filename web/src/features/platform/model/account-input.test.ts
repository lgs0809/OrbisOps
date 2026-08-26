import { describe, expect, it } from 'vitest';
import { accountStatus, displayNameError, newPasswordError, normalizePhone, phoneInputError } from './account-input';

describe('account form boundary', () => {
  it.each(['13800009001', ' +8613800009001 ', '8613800009001', '+86 13800009001'])('normalizes a supported phone prefix: %s', (value) => {
    expect(normalizePhone(value)).toBe('13800009001');
    expect(phoneInputError(value)).toBeUndefined();
  });
  it.each(['', '12800009001', '1380000900', '138000090011', '138 0000 9001', '+1 13800009001', '138-0000-9001'])('rejects malformed phone without silently changing it: %s', (value) => {
    expect(phoneInputError(value)).toBe('请输入有效的中国大陆手机号。');
  });
  it('accepts a two-character display name and rejects only empty or oversized names', () => {
    expect(displayNameError(' 王晨 ')).toBeUndefined();
    expect(displayNameError(' ')).toBe('请输入姓名。');
    expect(displayNameError('a'.repeat(51))).toBe('姓名不能超过 50 个字符。');
  });
  it('requires a usable initial password without rewriting user input', () => {
    expect(newPasswordError('abcdef1')).toBe('初始密码至少 8 个字符。');
    expect(newPasswordError('abcdefgh')).toBe('初始密码必须同时包含字母和数字。');
    expect(newPasswordError('12345678')).toBe('初始密码必须同时包含字母和数字。');
    expect(newPasswordError('Example1')).toBeUndefined();
  });
  it('distinguishes disabled, enabled, locked and unknown account states', () => {
    expect(accountStatus(0)).toMatchObject({ label: '已停用', canToggle: true });
    expect(accountStatus(1)).toMatchObject({ label: '已启用', canToggle: true });
    expect(accountStatus(2)).toMatchObject({ label: '已锁定', canToggle: false });
    expect(accountStatus(undefined)).toMatchObject({ label: '状态待确认', canToggle: false });
  });
});
