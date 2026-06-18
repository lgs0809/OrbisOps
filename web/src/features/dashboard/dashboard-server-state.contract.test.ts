import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

describe('Dashboard and personal audit query boundaries', () => {
  it('keeps dashboard overview server state out of the page', () => {
    const source = readFileSync(resolve(process.cwd(), 'src/pages/ops-dashboard.tsx'), 'utf8');

    expect(source).not.toContain('useEffect(');
    expect(source).not.toContain('opsAdminService');
    expect(source).not.toContain('opsUserService');
    expect(source).not.toContain('setAdminOverview(');
    expect(source).not.toContain('setUserOverview(');
    expect(source).toContain('useDashboardOverviewQuery(admin)');
    const querySource = readFileSync(resolve(process.cwd(), 'src/features/dashboard/api/dashboard-queries.ts'), 'utf8');
    expect(querySource).toContain('opsAdminService.productMetrics()');
    expect(querySource).toContain('productMetrics');
  });

  it('keeps my-audits server state out of the page', () => {
    const source = readFileSync(resolve(process.cwd(), 'src/pages/user-audit-records.tsx'), 'utf8');

    expect(source).not.toContain('useEffect(');
    expect(source).not.toContain('opsUserService');
    expect(source).not.toContain('setAudits(');
    expect(source).toContain('useMyAuditsQuery()');
  });
});
