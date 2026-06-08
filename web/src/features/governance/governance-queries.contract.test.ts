import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

describe('Governance query boundary', () => {
  it('keeps audit list, detail and policy server state behind TanStack Query', () => {
    const source = readFileSync(resolve(process.cwd(), 'src/pages/ops-status-management.tsx'), 'utf8');

    expect(source).not.toContain('opsAdminService');
    expect(source).not.toContain('useEffect(');
    expect(source).not.toContain('useCallback(');
    expect(source).not.toContain('setAudits(');
    expect(source).not.toContain('loadPolicy');
    expect(source).toContain('useGovernanceAuditsQuery(auditFilters, auditScopeReady)');
    expect(source).toContain('Boolean(projectScope.projectId) || projectScope.projects.length === 0');
    expect(source).toContain('useGovernanceAuditDetailQuery(detailAuditId, Boolean(detailRecord))');
    expect(source).toContain("useAuditPolicyQuery(projectScope.projectId || undefined, activeTab === 'policy')");
  });

  it('scopes policy drafts to the current project and invalidates canonical governance queries after save', () => {
    const pageSource = readFileSync(resolve(process.cwd(), 'src/pages/ops-status-management.tsx'), 'utf8');
    const querySource = readFileSync(resolve(process.cwd(), 'src/features/governance/api/governance-queries.ts'), 'utf8');

    expect(pageSource).toContain("const policyScopeKey = projectScope.projectId || 'GLOBAL';");
    expect(pageSource).toContain('policyDraft?.scopeKey === policyScopeKey');
    expect(pageSource).toContain('effectiveAuditControls(auditPolicy)');
    expect(pageSource).toContain('auditPolicySavePayload(');
    expect(pageSource).not.toContain('onChange={(value) => updateAuditPolicyDraft((current) => ({ ...current, replayEnabled:');
    expect(pageSource).not.toContain('onChange={(value) => updateAuditPolicyDraft((current) => ({ ...current, exportApprovalRequired:');
    expect(pageSource).not.toContain('onChange={(value) => updateAuditPolicyDraft((current) => ({ ...current, highRiskConfirmationRequired:');
    expect(querySource).toContain('queryClient.invalidateQueries({ queryKey: governanceQueryKeys.policy(projectId) })');
    expect(querySource).toContain('queryClient.invalidateQueries({ queryKey: governanceQueryKeys.auditLists() })');
    expect(querySource).toContain('useExportAuditMutation');
  });
});
