import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

describe('Alert Trigger query boundary', () => {
  it('keeps catalog and project options behind feature queries while preserving auth lifecycle', () => {
    const source = readFileSync(resolve(process.cwd(), 'src/pages/alert-trigger-management.tsx'), 'utf8');

    expect(source.match(/useEffect\(/g)?.length).toBe(1);
    expect(source).not.toContain('opsAdminService');
    expect(source).not.toContain('opsChannelService');
    expect(source).not.toContain('setRules(');
    expect(source).not.toContain('setEvents(');
    expect(source).not.toContain('setIncidents(');
    expect(source).not.toContain('setAgentDefinitions(');
    expect(source).not.toContain('setChannels(');
    expect(source).not.toContain('fetchData');
    expect(source).toContain('useAlertTriggerCatalogQuery()');
    expect(source).toContain('useAlertTriggerProjectOptionsQuery(projectScope.projectId)');
  });

  it('invalidates the canonical catalog after rule writes and tolerates optional project option failures', () => {
    const source = readFileSync(resolve(process.cwd(), 'src/features/alerts/api/alert-trigger-queries.ts'), 'utf8');

    expect(source).toContain('Promise.allSettled');
    expect(source).toContain('incidentLoadFailed');
    expect(source.match(/invalidateQueries\(\{ queryKey: alertTriggerQueryKeys\.catalog\(\) \}\)/g)?.length).toBe(3);
  });
});
