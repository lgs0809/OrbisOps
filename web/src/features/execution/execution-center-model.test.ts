import { describe, expect, it } from 'vitest';

import type { OpsChangePackage } from '../../services/ops-change-package-types';
import { packageCapability, packageInQueue, packageNeedsActorAction, packagesForQueue } from './execution-center-model';

const pkg = (status: string, capabilities: Record<string, boolean> = {}, productQueue?: OpsChangePackage['productQueue']): OpsChangePackage => ({
  packageId: `cp-${status}`,
  projectId: 'project-1',
  packageType: 'MCP',
  status,
  version: 1,
  capabilities,
  productQueue,
});

describe('execution center model', () => {
  it('prefers authoritative nested capabilities over compatibility fields', () => {
    const record = { ...pkg('REVIEWING', { canApprove: false }), canApprove: true };
    expect(packageCapability(record, 'canApprove')).toBe(false);
  });

  it('classifies landing states into the running queue', () => {
    expect(packageInQueue(pkg('APPROVED'), 'running')).toBe(true);
    expect(packageInQueue(pkg('LANDING'), 'running')).toBe(true);
    expect(packageInQueue(pkg('LANDING_RUNNING'), 'running')).toBe(true);
  });

  it('derives actor work from backend capabilities rather than global UI role', () => {
    expect(packageNeedsActorAction(pkg('REVIEWING', { canApprove: true }))).toBe(true);
    expect(packageNeedsActorAction(pkg('REVIEWING', { canApprove: false, canReject: false }))).toBe(false);
    expect(packageNeedsActorAction(pkg('APPROVED', { canLand: true }))).toBe(true);
  });

  it('filters my-work queues but keeps project history visible', () => {
    const packages = [
      pkg('REVIEWING', { canApprove: true }),
      { ...pkg('REVIEWING', { canApprove: false }), packageId: 'cp-other' },
      pkg('LANDED', {}),
    ];

    expect(packagesForQueue(packages, 'pending', { platformAdmin: false, mineOnly: true })).toHaveLength(1);
    expect(packagesForQueue(packages, 'history', { platformAdmin: false, mineOnly: true })).toHaveLength(1);
    expect(packagesForQueue(packages, 'pending', { platformAdmin: true, mineOnly: true })).toHaveLength(2);
  });
});
