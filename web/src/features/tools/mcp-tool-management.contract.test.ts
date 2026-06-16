import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

describe('Tools / MCP query boundary', () => {
  it('keeps template catalog and generated-tools server state behind TanStack Query', () => {
    const source = readFileSync(resolve(process.cwd(), 'src/pages/mcp-tool-management/index.tsx'), 'utf8');

    expect(source).not.toContain('useEffect(');
    expect(source).not.toContain('opsMcpTemplateService');
    expect(source).not.toContain('setTemplates(');
    expect(source).not.toContain('fetchTemplates');
    expect(source).not.toContain('setGeneratedTools(');
    expect(source).not.toContain('generatedLoading');
    expect(source).toContain('useMcpTemplatesQuery()');
    expect(source).toContain("useMcpGeneratedToolsQuery(selected?.templateId || '', detailVisible && Boolean(selected))");
    expect(source).toContain('useSaveMcpTemplateMutation()');
    expect(source).toContain('useCopyMcpTemplateMutation()');
    expect(source).toContain('useToggleMcpTemplateMutation()');
  });

  it('invalidates the canonical template list after writes', () => {
    const source = readFileSync(resolve(process.cwd(), 'src/features/tools/api/mcp-template-queries.ts'), 'utf8');

    expect(source).toContain('queryClient.invalidateQueries({ queryKey: mcpTemplateQueryKeys.list() })');
    expect(source).toContain('useMcpGeneratedToolsQuery');
    expect(source).toContain('enabled: Boolean(templateId && enabled)');
  });
});
