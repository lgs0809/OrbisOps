import { afterEach, describe, expect, it, vi } from 'vitest';

import { FirstTimeSetupService } from './first-time-setup-service';

const jsonResponse = (status: number, body: unknown) => new Response(JSON.stringify(body), {
  status,
  headers: { 'Content-Type': 'application/json' },
});

describe('FirstTimeSetupService', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('surfaces the backend validation reason for a rejected setup request', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(400, {
      code: '0002',
      info: '密码必须同时包含字母和数字',
      data: null,
    })));

    await expect(FirstTimeSetupService.createFirstAdministrator({
      username: 'admin',
      password: 'abcdefgh',
    })).rejects.toThrow('密码必须同时包含字母和数字');
  });

  it('returns the authenticated first administrator after successful setup', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(200, {
      code: '0000',
      info: '成功',
      data: {
        userId: 'user-1',
        username: 'admin',
        userRole: 'admin',
        token: '[REDACTED_SECRET]',
      },
    })));

    await expect(FirstTimeSetupService.createFirstAdministrator({
      username: 'admin',
      password: 'admin1234',
    })).resolves.toMatchObject({
      username: 'admin',
      token: '[REDACTED_SECRET]',
    });
  });
});
