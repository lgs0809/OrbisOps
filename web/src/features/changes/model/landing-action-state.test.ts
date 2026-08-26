import { describe, expect, it } from 'vitest';
import type { OpsChangePackage, OpsLandingOperationRun } from '../../../services/ops-change-package-types';
import { landingActionState } from './landing-action-state';

const record = (overrides: Partial<OpsChangePackage> = {}): OpsChangePackage => ({
  packageId: 'cp-current', projectId: 'project-a', packageType: 'MCP', status: 'LANDING_FAILED', version: 1,
  landingRunId: 'lr-current', approvedVersion: 1, approvedPackageHash: 'approved-hash', ...overrides,
});
const operation = (overrides: Partial<OpsLandingOperationRun> = {}): OpsLandingOperationRun => ({
  operationRunId: 'operation-current', packageId: 'cp-current', projectId: 'project-a',
  landingRunId: 'lr-current', approvedVersion: 1, approvedPackageHash: 'approved-hash',
  status: 'PENDING', factStatus: 'NONE', ...overrides,
});

describe('Landing action from current durable facts', () => {
  it('allows the first approved execution without inventing an old operation', () => {
    expect(landingActionState(record({ status: 'APPROVED' }), []).canSubmit).toBe(true);
  });
  it.each(['COMPLETED', 'UNKNOWN'])('current %s facts override a stale APPROVED package read', (factStatus) => {
    expect(landingActionState(record({ status: 'APPROVED' }), [operation({ factStatus })]).canSubmit).toBe(false);
  });
  it('does not use a previous approved version to block a new approved plan', () => {
    expect(landingActionState(record({ status: 'APPROVED', approvedVersion: 2, approvedPackageHash: 'new-hash' }),
      [operation({ factStatus: 'COMPLETED', status: 'SUCCEEDED' })]).canSubmit).toBe(true);
  });
  it('blocks a stale approved read when any matching current operation has begun', () => {
    expect(landingActionState(record({ status: 'APPROVED' }),
      [operation(), operation({ operationRunId: 'second', status: 'EXECUTING' })]).canSubmit).toBe(false);
  });
  it('allows server rechecking only when all current operations are proven pending', () => {
    expect(landingActionState(record(), [operation(), operation({ operationRunId: 'second' })]).canSubmit).toBe(true);
  });
  it.each(['SUCCEEDED', 'EXECUTING', 'FAILED', 'POST_CHECKING'])('blocks retry with %s execution facts', (status) => {
    expect(landingActionState(record(), [operation(), operation({ status, factStatus: 'COMPLETED' })]).canSubmit).toBe(false);
  });
  it('blocks unknown facts and absent readback instead of assuming no dispatch', () => {
    expect(landingActionState(record(), [operation({ factStatus: 'UNKNOWN' })]).canSubmit).toBe(false);
    expect(landingActionState(record(), []).canSubmit).toBe(false);
    expect(landingActionState(record({ landingRunId: '' }), [operation()]).canSubmit).toBe(false);
  });
  it.each([
    { projectId: 'foreign' }, { packageId: 'foreign' }, { approvedVersion: 2 }, { approvedPackageHash: 'stale' },
  ])('rejects a mismatched current identity %j', (overrides) => {
    expect(landingActionState(record(), [operation(overrides)]).canSubmit).toBe(false);
  });
  it('does not use another run to prove the current run was unexecuted', () => {
    expect(landingActionState(record(), [operation({ landingRunId: 'lr-old' })]).canSubmit).toBe(false);
    expect(landingActionState(record(), [operation({ landingRunId: 'lr-old', status: 'SUCCEEDED' }), operation()]).canSubmit).toBe(true);
  });
  it.each(['LANDING_RUNNING', 'LANDED', 'NEEDS_REPLAN', 'REVIEWING'])('does not submit %s packages', (status) => {
    expect(landingActionState(record({ status }), [operation()]).canSubmit).toBe(false);
  });
});
