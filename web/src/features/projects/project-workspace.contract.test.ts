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

  it('keeps shared project data and the canonical project feature boundaries', () => {
    const scopeSource = readFileSync(resolve(process.cwd(), 'src/hooks/use-project-scope.ts'), 'utf8');
    const querySource = readFileSync(resolve(process.cwd(), 'src/features/projects/api/project-workspace-queries.ts'), 'utf8');

    expect(scopeSource).toContain('templates: OpsProjectResourceTemplate[];');
    expect(querySource).toContain('enabled: Boolean(projectId)');
    const catalogSource = readFileSync(resolve(process.cwd(), 'src/features/projects/api/project-skill-catalog.ts'), 'utf8');
    expect(querySource).toContain('client.fetchQuery(projectSkillCatalogOptions(projectId))');
    expect(catalogSource).toContain('opsAdminService.listProjectSkills(projectId)');
    expect(catalogSource).toContain("skill.scope !== 'PROJECT' || skill.projectId !== projectId");
    expect(querySource).toContain('opsRepairService.getRepositoryCapabilities()');
    expect(querySource).toContain('availability: {');
    expect(querySource).toContain('opsRepairService.listExecutionResources(projectId)');
  });
});
