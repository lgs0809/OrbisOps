import { describe, expect, it } from 'vitest';

import { toChangePackageSummary } from './change-package-summary';

describe('change package summary', () => {
  it('normalizes full ChangePackage fields into the shared view model', () => {
    const summary = toChangePackageSummary({
      packageId: 'cp-1',
      projectId: 'p-1',
      incidentId: 'inc-1',
      status: 'REVIEWING',
      version: 3,
      summary: 'Restart payment service',
      riskLevel: 'HIGH',
      targetEnvironment: 'prod',
      reasonCode: 'READY_FOR_REVIEW',
      updateTime: '2026-08-17T00:00:00Z',
    });

    expect(summary.title).toBe('Restart payment service');
    expect(summary.statusLabel).toBe('等待审批');
    expect(summary.riskLabel).toBe('高');
    expect(summary.reasonLabel).toBe('校验通过，可以提交审批');
    expect(summary.incidentId).toBe('inc-1');
  });

  it('supports incident change refs without inventing missing fields', () => {
    const summary = toChangePackageSummary({
      packageId: 'cp-2',
      status: 'LANDED',
      version: 2,
      packageHash: 'hash-2',
      landingRunId: 'landing-2',
    });

    expect(summary.title).toBe('cp-2');
    expect(summary.statusLabel).toBe('已 Landing');
    expect(summary.packageHash).toBe('hash-2');
    expect(summary.landingRunId).toBe('landing-2');
    expect(summary.targetEnvironment).toBeUndefined();
  });
});
