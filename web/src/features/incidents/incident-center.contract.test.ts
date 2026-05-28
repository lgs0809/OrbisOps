import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

describe('Incident Center query boundary', () => {
  it('keeps incident list, detail and members behind TanStack Query', () => {
    const source = readFileSync(resolve(process.cwd(), 'src/pages/incident-center.tsx'), 'utf8');

    expect(source).not.toContain('useEffect(');
    expect(source).not.toContain('opsAdminService');
    expect(source).not.toContain('opsProjectService');
    expect(source).not.toContain('setIncidents(');
    expect(source).not.toContain('setDetail(');
    expect(source).not.toContain('setMembers(');
    expect(source).not.toContain('const load =');
    expect(source).not.toContain('await openDetail(');
    expect(source).toContain('useProjectScope()');
    expect(source).toContain('useIncidentsQuery(projectId, incidentScope)');
    expect(source).toContain('useIncidentDetailQuery(selectedIncidentId, incidentScope)');
    expect(source).toContain('useIncidentMembersQuery(');
    expect(source).toContain('useIncidentCommandMutation(incidentScope)');
  });

  it('keeps detail and members on demand and invalidates list plus changed detail after commands', () => {
    const source = readFileSync(resolve(process.cwd(), 'src/features/incidents/api/incident-queries.ts'), 'utf8');

    expect(source).toContain('enabled: Boolean(incidentId)');
    expect(source).toContain('enabled: Boolean(projectId && enabled)');
    expect(source).toContain('queryClient.invalidateQueries({ queryKey: incidentQueryKeys.lists() })');
    expect(source).toContain('queryClient.invalidateQueries({ queryKey: incidentQueryKeys.detail(command.incidentId, scope) })');
  });
});
