import { describe, expect, it } from 'vitest';

import type { OpsChangePackage } from '../../../services/ops-change-package-types';
import { approvalCockpitView } from './approval-cockpit-model';

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
});
