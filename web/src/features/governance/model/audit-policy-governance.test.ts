import { describe, expect, it } from 'vitest';

import {
  auditPolicySavePayload,
  auditRetentionDays,
  auditRetentionDirty,
  effectiveAuditControls,
} from './audit-policy-governance';

describe('audit policy governance model', () => {
  it('exposes only truthful effective control states', () => {
    const controls = effectiveAuditControls({ retentionDays: 90, maskingEnabled: 0 });

    expect(controls.find((item) => item.key === 'retention')).toMatchObject({
      status: 'ENFORCED',
      summary: '90 天 · 服务端可见性边界',
    });
    expect(controls.find((item) => item.key === 'masking')).toMatchObject({ status: 'PLATFORM_INVARIANT' });
    expect(controls.find((item) => item.key === 'export')).toMatchObject({ status: 'NOT_ACTIVATED' });
    expect(controls.find((item) => item.key === 'high-risk')).toMatchObject({ status: 'DELEGATED' });
    expect(controls.find((item) => item.key === 'replay')).toMatchObject({ status: 'UNAVAILABLE' });
  });

  it('forces masking on while preserving dormant compatibility fields', () => {
    const payload = auditPolicySavePayload({
      retentionDays: 30,
      maskingEnabled: false,
      exportApprovalRequired: false,
      highRiskConfirmationRequired: false,
      replayEnabled: false,
      status: 'DISABLED',
    }, 'project-1', 45);

    expect(payload).toMatchObject({
      projectId: 'project-1',
      retentionDays: 45,
      maskingEnabled: true,
      exportApprovalRequired: false,
      highRiskConfirmationRequired: false,
      replayEnabled: false,
      status: 'DISABLED',
    });
  });

  it('bounds retention and only treats retention changes as editable policy dirtiness', () => {
    expect(auditRetentionDays({ retentionDays: 1 })).toBe(7);
    expect(auditRetentionDays({ retention_days: 99999 })).toBe(3650);
    expect(auditRetentionDirty({ retentionDays: 90 }, { retentionDays: 91, replayEnabled: false })).toBe(true);
    expect(auditRetentionDirty({ retentionDays: 90 }, { retentionDays: 90, replayEnabled: false })).toBe(false);
  });
});
