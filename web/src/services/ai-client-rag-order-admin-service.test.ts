import { afterEach, describe, expect, it, vi } from 'vitest';

import { AiClientRagOrderAdminService } from './ai-client-rag-order-admin-service';

const okResponse = (data: unknown) => new Response(JSON.stringify({ code: '0000', info: 'ok', data }), {
  status: 200,
  headers: { 'Content-Type': 'application/json' },
});

describe('AiClientRagOrderAdminService knowledge boundaries', () => {
  afterEach(() => {
    localStorage.clear();
    vi.unstubAllGlobals();
  });

  it('does not request chunks without a knowledge base id', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const service = new AiClientRagOrderAdminService();

    await expect(service.listGlobalKnowledgeChunks('   ')).rejects.toThrow('KNOWLEDGE_BASE_ID_REQUIRED');
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('uses the normalized knowledge base id for chunk requests', async () => {
    const fetchMock = vi.fn().mockResolvedValue(okResponse([]));
    vi.stubGlobal('fetch', fetchMock);
    const service = new AiClientRagOrderAdminService();

    await service.listGlobalKnowledgeChunks(' kb/demo ', 200);

    const [url] = fetchMock.mock.calls[0];
    expect(String(url)).toContain('/knowledge-bases/global/kb%2Fdemo/chunks?limit=200');
  });
});
