import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

describe('Knowledge Management feature boundary', () => {
  it('keeps HTTP/server state behind feature API hooks', () => {
    const source = readFileSync(
      resolve(process.cwd(), 'src/features/knowledge/pages/KnowledgeManagementPage.tsx'),
      'utf8',
    );

    expect(source).not.toContain('aiClientRagOrderAdminService');
    expect(source).not.toContain('useEffect(');
    expect(source).not.toContain('setInterval(');
    expect(source).not.toContain('setDataSource(');
    expect(source).not.toContain('setDocuments(');
    expect(source).not.toContain('setKnowledgeBases(');
    expect(source).toContain('useGlobalKnowledgeBasesQuery()');
    expect(source).toContain('useKnowledgeChunksQuery(selectedKnowledgeId)');
    expect(source).toContain('filterKnowledgeBases(knowledgeBases, appliedFilters, currentPage, pageSize)');
  });

  it('keeps the legacy route module as a compatibility-only export', () => {
    const source = readFileSync(resolve(process.cwd(), 'src/pages/rag-order-management.tsx'), 'utf8').trim();
    expect(source).toBe(
      "export { KnowledgeManagementPage as RagOrderManagement } from '../features/knowledge/pages/KnowledgeManagementPage';",
    );
  });
});
