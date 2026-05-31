import { describe, expect, it } from 'vitest';

import type { OpsChangePackage } from '../../../services/ops-change-package-types';
import { approvalCockpitView, operationSafetyGaps } from './approval-cockpit-model';

const base = (): OpsChangePackage => ({
  packageId: 'pkg-1', projectId: 'project-1', packageType: 'MCP_CHANGE', status: 'REVIEWING', version: 3,
  objective: 'restore checkout availability', diffSummary: 'restart checkout deployment',
  targetEnvironment: 'prod', serviceId: 'checkout', riskLevel: 'HIGH',
  targetScopeJson: JSON.stringify({ namespace: 'shop', deployment: 'checkout' }),
  evidenceJson: JSON.stringify({ facts: ['error rate high'] }), trustedProofRefs: [{ resultId: 'r-1' }],
  rollbackStepsJson: JSON.stringify(['restore previous deployment']),
  verificationCriteriaJson: JSON.stringify(['error rate < 1%']),
});

describe('approvalCockpitView', () => {
  it('does not let a package placeholder hide missing per-operation recovery or dropped checks', () => {
    const record = base();
    record.rollbackStepsJson = JSON.stringify({ required: true, status: 'MANUAL_REQUIRED' });
    record.mcpSteps = [{ toolName: 'apply', writesTargetResource: true,
      postCheck: { expectedValues: { version: 'v2' } },
      additionalChecks: [{ toolName: 'orders', expectedValues: { errorCount: 0 } }],
    }];
    expect(operationSafetyGaps(record)).toHaveLength(4);
    record.mcpSteps = [{ toolName: 'apply', writesTargetResource: true,
      postCheck: { expectedValues: { version: 'v2' }, additionalChecks: [{ expectedValues: { errorCount: 0 } }] },
      rollbackPlan: { summary: 'stop and retain receipts' }, rollbackPrecondition: { version: 'v2' },
      manualFallback: { summary: 'review a separate recovery package' },
    }];
    expect(operationSafetyGaps(record)).toEqual([]);
  });
  it('always projects the eight human approval questions from authoritative package fields', () => {
    const view = approvalCockpitView(base());
    expect(view.why).toContain('checkout');
    expect(view.what).toContain('restart');
    expect(view.where).toContain('prod');
    expect(view.evidence).toContain('trusted proof');
    expect(view.risk).toBe('HIGH');
    expect(view.blastRadius).toContain('checkout');
    expect(view.rollback).toContain('previous deployment');
    expect(view.verify).toContain('error rate');
  });

  it('shows production scope when test validation comes before the write', () => {
    const record = base();
    record.serviceId = undefined;
    record.targetScopeJson = '{}';
    record.mcpSteps = [
      { operationId: 'validate', resourceScope: 'service://orders/test', effectType: 'VALIDATE_ONLY', writesTargetResource: false },
      { operationId: 'apply', resourceScope: 'service://orders/prod', effectType: 'MUTATE_TARGET_RESOURCE', writesTargetResource: true },
    ];
    record.approvalBoundaryJson = JSON.stringify({ resourceScope: 'service://orders/prod' });
    const view = approvalCockpitView(record);
    expect(view.where).toBe('prod · service://orders/prod');
    expect(view.blastRadius).toBe('service://orders/prod');
  });

  it('shows every executable postcondition instead of a generic verification placeholder', () => {
    const record = base();
    record.mcpSteps = [{ writesTargetResource: true, postCheck: {
      toolName: 'read_configuration', expectedValues: { version: 'v2' },
      additionalChecks: [{ toolName: 'check_orders', expectedValues: { errorCount: 0, requestCount: 20 } }],
    } }];
    const view = approvalCockpitView(record);
    expect(view.verify).toContain('version: v2');
    expect(view.verify).toContain('errorCount: 0');
    expect(view.verify).toContain('requestCount: 20');
  });

  it('does not fabricate evidence or rollback readiness when fields are absent', () => {
    const record = base();
    record.evidenceJson = undefined;
    record.trustedProofRefs = [];
    record.rollbackStepsJson = undefined;
    record.preflightResultJson = undefined;
    record.dryRunResultJson = undefined;
    record.ciResultJson = undefined;
    record.testProofHash = undefined;
    const view = approvalCockpitView(record);
    expect(view.evidence).toBe('未记录可核验证据');
    expect(view.rollback).toBe('未记录回滚计划');
  });

  it('derives readable approval fields from structured MCP operations', () => {
    const record = base();
    record.diffSummary = undefined;
    record.targetScopeJson = '{}';
    record.serviceId = undefined;
    record.mcpSteps = [{
      operationId: 'extend-ttl',
      toolName: 'redis_update_ttl',
      resourceScope: 'redis://dev/0',
      arguments: { key: 'checkout:acceptance:ttl-test', ttlSeconds: 3600 },
    }];
    const view = approvalCockpitView(record);
    expect(view.what).toContain('redis_update_ttl');
    expect(view.what).toContain('ttlSeconds=3600');
    expect(view.where).toContain('redis://dev/0');
    expect(view.blastRadius).toContain('redis://dev/0');
  });
});
