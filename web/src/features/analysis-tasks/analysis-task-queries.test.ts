import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

import { hasActiveAnalysisTasks } from './api/analysis-task-queries';

describe('Analysis Tasks query boundary', () => {
  it('polls only while a task can still change without user action', () => {
    expect(hasActiveAnalysisTasks([{ status: 'SUCCEEDED' } as any])).toBe(false);
    expect(hasActiveAnalysisTasks([{ status: 'FAILED' } as any])).toBe(false);
    expect(hasActiveAnalysisTasks([{ status: 'PENDING' } as any])).toBe(true);
    expect(hasActiveAnalysisTasks([{ status: 'RUNNING' } as any])).toBe(true);
    expect(hasActiveAnalysisTasks([{ status: 'WAITING_APPROVAL' } as any])).toBe(true);
    expect(hasActiveAnalysisTasks([{ status: 'RECOVERING' } as any])).toBe(true);
  });

  it('keeps projects, tasks and detail server state out of the page', () => {
    const source = readFileSync(resolve(process.cwd(), 'src/pages/analysis-task-center.tsx'), 'utf8');

    expect(source).not.toContain('opsAdminService');
    expect(source).not.toContain('opsProjectService');
    expect(source).not.toContain('loadTasks');
    expect(source).not.toContain('loadProjects');
    expect(source).not.toContain('setTasks(');
    expect(source).not.toContain('setProjects(');
    expect((source.match(/useEffect\(/g) || [])).toHaveLength(1);
    expect(source).toContain('useAnalysisTasksQuery(scope, effectiveProjectId, source, status)');
    expect(source).toContain('useAnalysisTaskDetailQuery(');
  });
});
