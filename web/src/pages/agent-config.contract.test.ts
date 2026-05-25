import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

describe('workflow agent execution modes', () => {
  it('keeps DIRECT, LLM and REACT as the only canonical execution modes', () => {
    const source = readFileSync(resolve(process.cwd(), 'src/pages/agent-config.tsx'), 'utf8');

    expect(source).toContain("{ value: 'direct', label: 'DIRECT");
    expect(source).toContain("{ value: 'llm', label: 'LLM");
    expect(source).toContain("{ value: 'react', label: 'REACT");
    expect(source).not.toContain("{ value: 'review', label: 'REVIEW");
    expect(source).toContain("if (value === 'review') return 'llm';");
    expect(source).toContain('const LLM_ROLE_TEMPLATES = [');
  });

  it('keeps shared libraries and project capabilities behind Query while editor hydration stays local', () => {
    const source = readFileSync(resolve(process.cwd(), 'src/pages/agent-config.tsx'), 'utf8');
    const productSource = readFileSync(resolve(process.cwd(), 'src/pages/agent-config-product-page.tsx'), 'utf8');
    const querySource = readFileSync(resolve(process.cwd(), 'src/features/agents/api/agent-builder-queries.ts'), 'utf8');

    expect(source).not.toContain('refreshLibraries');
    expect(source).not.toContain('setMcpOptions(');
    expect(source).not.toContain('setProjects(');
    expect(source).not.toContain('setKnowledgeBases(');
    expect(source).not.toContain('setAgentCapabilities(');
    expect(source).not.toContain('setModelOptions(');
    expect(source).not.toContain('setSkillOptions(');
    expect(source).toContain('useAgentBuilderLibrariesQuery(Boolean(userInfo))');
    expect(source).toContain("useAgentBuilderCapabilitiesQuery(definition.projectId || '', Boolean(userInfo))");
    expect(source).toContain('const projectScope = useProjectScope();');
    expect(productSource).not.toContain('useEffect(');
    expect(productSource).not.toContain('loadAgentReferenceImpacts(');
    expect(productSource).toContain('useAgentReferenceImpactQuery(projectId, agentId)');
    expect(querySource).toContain('enabled: Boolean(projectId && enabled)');
  });
});
