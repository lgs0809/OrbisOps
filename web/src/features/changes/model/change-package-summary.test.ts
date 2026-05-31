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
    expect(summary.reasonCode).toBeUndefined();
    expect(summary.statusHint).toContain('等待审批');
    expect(summary.incidentId).toBe('inc-1');
  });

  it('keeps the user objective as the list title and falls back when it is blank', () => {
    const source = { packageId: 'cp-title', status: 'DRAFT', version: 1,
      objective: '核对订单服务', summary: '完整执行报告和验证证据' };
    expect(toChangePackageSummary(source).title).toBe('核对订单服务');
    expect(toChangePackageSummary({ ...source, objective: '  ' }).title).toBe(source.summary);
  });

  it('shows the actual failure instead of an earlier validation success', () => {
    const summary = toChangePackageSummary({packageId: 'failed', status: 'LANDING_FAILED', version: 1,
      reasonCode: 'READY_FOR_REVIEW'});
    expect(summary.reasonCode).toBeUndefined();
    expect(summary.statusHint).toContain('Landing 失败');
    const withFailure = toChangePackageSummary({packageId: 'failed', status: 'LANDING_FAILED', version: 1,
      reasonCode: 'LANDING_POST_CHECK_FAILED'});
    expect(withFailure.statusHint).toContain('Verification 失败');
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
