import { describe, expect, it } from 'vitest';

import {
  activeIngestionJobCount,
  buildKnowledgeOverview,
  filterKnowledgeBases,
  isKnowledgeEnabled,
  knowledgeIdOf,
  knowledgeNameOf,
  normalizeRetrievalPolicy,
  validateKnowledgeUploadFile,
  validateRetrievalPolicy,
} from './knowledge-model';

describe('knowledge model', () => {
  const bases = [
    { knowledgeTag: 'ops-public', kbId: 'ops-public', kbName: 'Operations', status: 'ENABLED', chunkCount: 12, documentCount: 3 },
    { knowledgeTag: 'finance-public', kbId: 'finance-public', kbName: 'Finance', status: 'DISABLED', chunkCount: 5, documentCount: 2 },
  ];

  it('normalizes knowledge identity and status across legacy aliases', () => {
    expect(knowledgeIdOf({ ragId: 'legacy' })).toBe('legacy');
    expect(knowledgeNameOf({ ragId: 'legacy', ragName: 'Legacy Name' })).toBe('Legacy Name');
    expect(isKnowledgeEnabled({ statusCode: 1 })).toBe(true);
    expect(isKnowledgeEnabled({ status: 'DISABLED' })).toBe(false);
  });

  it('filters and paginates global knowledge bases without mutating server state', () => {
    const page = filterKnowledgeBases(bases, { ragName: 'Operations', status: 1 }, 1, 10);
    expect(page.total).toBe(1);
    expect(page.items.map(knowledgeIdOf)).toEqual(['ops-public']);
    expect(filterKnowledgeBases(bases, {}, 2, 1).items.map(knowledgeIdOf)).toEqual(['finance-public']);
  });

  it('builds product overview from authoritative query results', () => {
    const overview = buildKnowledgeOverview(
      bases,
      [{ kbId: 'ops-public', chunkId: 'c-1', tag: 'ops-public' }],
      { chunkCount: 17, documentCount: 5, byType: [], bySource: [] },
      [
        { jobId: 'j1', status: 'RUNNING', name: 'a', tag: 'ops-public', fileNames: [], createdAt: '', updatedAt: '' },
        { jobId: 'j2', status: 'SUCCEEDED', name: 'b', tag: 'ops-public', fileNames: [], createdAt: '', updatedAt: '' },
      ],
    );
    expect(overview).toMatchObject({ chunkCount: 17, documentCount: 5, knowledgeCount: 2, enabledCount: 1, activeJobs: 1 });
    expect(activeIngestionJobCount([
      { jobId: 'j1', status: 'PENDING', name: '', tag: '', fileNames: [], createdAt: '', updatedAt: '' },
    ])).toBe(1);
  });

  it('normalizes and validates retrieval policy at the model boundary', () => {
    const normalized = normalizeRetrievalPolicy({ chunkSize: 2400, overlapSize: 120, topK: 8, metadataFilterJson: '{"env":"prod"}' });
    expect(normalized.maxSegmentChars).toBe(2400);
    expect(normalized.hardSplitOverlapChars).toBe(120);
    expect(validateRetrievalPolicy(normalized).payload).toMatchObject({ maxSegmentChars: 2400, hardSplitOverlapChars: 120, topK: 8 });
    expect(validateRetrievalPolicy({ maxSegmentChars: 800 }).error).toContain('1000');
    expect(validateRetrievalPolicy({ maxSegmentChars: 3000, hardSplitOverlapChars: 0, topK: 5, metadataFilterJson: '{bad' }).error).toContain('合法 JSON');
  });

  it('keeps upload constraints in a pure product policy', () => {
    expect(validateKnowledgeUploadFile({ name: 'guide.md', size: 1024 })).toBeNull();
    expect(validateKnowledgeUploadFile({ name: 'guide.pdf', size: 20 * 1024 * 1024 })).toBeNull();
    expect(validateKnowledgeUploadFile({ name: 'guide.docx', size: 1024 })).toContain('Markdown');
    expect(validateKnowledgeUploadFile({ name: 'huge.pdf', size: 20 * 1024 * 1024 + 1 })).toContain('20MB');
  });
});
