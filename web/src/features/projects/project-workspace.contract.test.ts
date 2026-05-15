import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

describe('Project Workspace server-state boundary', () => {
  it('reuses global Project Scope and keeps project capability/runtime reads behind Query hooks', () => {
    const source = readFileSync(resolve(process.cwd(), 'src/pages/project-workspace.tsx'), 'utf8');

    expect(source).not.toContain('useEffect(');
    expect(source).not.toContain('const loadSnapshot =');
    expect(source).not.toContain('setProjects(');
    expect(source).not.toContain('setProjectSkillDetails(');
    expect(source).not.toContain('setProjectKnowledgeDetails(');
    expect(source).not.toContain('setProjectMembers(');
    expect(source).not.toContain('setRepositories(');
    expect(source).not.toContain('setProjectServices(');
    expect(source).not.toContain('setExecutionResources(');
    expect(source).toContain('const projectScope = useProjectScope();');
    expect(source).toContain("useProjectWorkspaceCapabilitiesQuery(selectedProject?.projectId || '')");
    expect(source).toContain("useProjectWorkspaceRuntimeQuery(selectedProject?.projectId || '')");
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
