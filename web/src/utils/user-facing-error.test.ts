import { describe, expect, it } from 'vitest';

import { userFacingDetail, userFacingError } from './user-facing-error';

describe('userFacingError', () => {
  it('keeps concise human-readable application messages', () => {
    expect(userFacingError(new Error('用户名或密码错误。'), '登录失败。')).toBe('用户名或密码错误。');
  });

  it('maps known internal codes to product language', () => {
    expect(userFacingError(new Error('KNOWLEDGE_BASE_ID_REQUIRED'), '加载失败。')).toBe('请先选择知识库。');
  });

  it('hides transport details, URLs and unknown internal codes', () => {
    expect(userFacingError(new Error('HTTP error! status: 500'), '请求失败。')).toBe('请求失败。');
    expect(userFacingError(new Error('请求超时：https://internal.example/api/v1/secret'), '请求失败。')).toBe('请求失败。');
    expect(userFacingError(new Error('SOME_PRIVATE_RUNTIME_FAILURE'), '请求失败。')).toBe('请求失败。');
  });

  it('normalizes raw backend detail strings', () => {
    expect(userFacingDetail('HTTP 503 service unavailable', 'Provider 当前不可用。')).toBe('Provider 当前不可用。');
  });
});
