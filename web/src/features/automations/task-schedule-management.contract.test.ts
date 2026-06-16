import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

describe('Task Schedule query boundary', () => {
  it('keeps schedules, options and execution history behind TanStack Query', () => {
    const source = readFileSync(resolve(process.cwd(), 'src/pages/task-schedule-management.tsx'), 'utf8');

    expect(source).not.toContain('useEffect(');
    expect(source).not.toContain('taskScheduleAdminService');
    expect(source).not.toContain('opsAdminService');
    expect(source).not.toContain('opsChannelService');
    expect(source).not.toContain('setSchedules(');
    expect(source).not.toContain('setExecutions(');
    expect(source).not.toContain('fetchSchedules');
    expect(source).not.toContain('fetchAgents');
    expect(source).not.toContain('fetchExecutions');
    expect(source).toContain('useTaskSchedulesQuery(projectScope.projectId)');
    expect(source).toContain('useTaskScheduleOptionsQuery(projectScope.projectId)');
    expect(source).toContain('useTaskExecutionsQuery(');
  });

  it('keeps execution history on demand and writes invalidating project-scoped keys', () => {
    const source = readFileSync(resolve(process.cwd(), 'src/features/automations/api/task-schedule-queries.ts'), 'utf8');

    expect(source).toContain('enabled: Boolean(projectId && scheduleId && enabled)');
    expect(source.match(/invalidateQueries\(\{ queryKey: taskScheduleQueryKeys\.list\(projectId\) \}\)/g)?.length).toBe(3);
    expect(source).toContain('taskScheduleQueryKeys.executions(projectId, scheduleId)');
    expect(source).toContain('}, 1200);');
  });
});
