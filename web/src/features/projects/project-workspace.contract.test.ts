import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

describe('Project Workspace canonical boundary', () => {
  it('keeps the legacy module as a thin compatibility shim over the canonical Project page', () => {
    const legacySource = readFileSync(resolve(process.cwd(), 'src/pages/project-workspace.tsx'), 'utf8');
    const source = readFileSync(resolve(process.cwd(), 'src/pages/project-product-workspace.tsx'), 'utf8');

    expect(legacySource.trim()).toBe("export { ProjectProductWorkspacePage as ProjectWorkspacePage } from './project-product-workspace';");
    expect(source).toContain('const scope = useProjectScope();');
    expect(source).toContain('useProjectWorkspaceCapabilitiesQuery(projectId)');
    expect(source).toContain('useProjectWorkspaceRuntimeQuery(projectId)');
    expect(source).not.toContain('/absolute/path/');
    expect(source).not.toContain('createPlatformAccount');
    expect(source).not.toContain('newAccountForm');
  });

  it('keeps the shared project snapshot cache responsible for projects and templates', () => {
    const scopeSource = readFileSync(resolve(process.cwd(), 'src/hooks/use-project-scope.ts'), 'utf8');
    const querySource = readFileSync(resolve(process.cwd(), 'src/features/projects/api/project-workspace-queries.ts'), 'utf8');

    expect(scopeSource).toContain('templates: OpsProjectResourceTemplate[];');
    expect(scopeSource).toContain('templates: response.data?.templates || []');
    expect(querySource).toContain('enabled: Boolean(projectId)');
    expect(querySource).toContain('opsAdminService.listProjectSkills(projectId)');
    expect(querySource).toContain('opsRepairService.listExecutionResources(projectId)');
  });
});
