import type {
  AiClientRagOrderResponseDTO,
  RagDocumentStats,
  RagIngestionJobRecord,
  RagKnowledgeBaseSummary,
  RagKnowledgeDocumentRecord,
  RagKnowledgeRetrievalPolicy,
} from '../../../services/ai-client-rag-order-admin-service';

export type KnowledgeQueryFilters = {
  ragId?: string;
  ragName?: string;
  knowledgeTag?: string;
  status?: number;
};

export type KnowledgeBaseRow = RagKnowledgeBaseSummary
  & Omit<Partial<AiClientRagOrderResponseDTO>, 'status'>
  & { status?: number | string };

export type KnowledgeOverview = {
  chunkCount: number;
  documentCount: number | string;
  knowledgeCount: number;
  enabledCount: number;
  tagCount: number;
  activeJobs: number;
};

export type KnowledgePage = {
  items: KnowledgeBaseRow[];
  total: number;
};

export type RetrievalPolicyDraft = RagKnowledgeRetrievalPolicy & {
  segmentationMode: string;
  structurePreserved: boolean;
  maxSegmentChars: number;
  hardSplitOverlapChars: number;
  topK: number;
  metadataFilterJson: string;
};

export type RetrievalPolicyPayload = {
  maxSegmentChars: number;
  hardSplitOverlapChars: number;
  topK: number;
  rerankEnabled: boolean;
  embeddingModelId: string;
  metadataFilterJson: string;
};

export const SUPPORTED_KNOWLEDGE_EXTENSIONS = ['.md', '.markdown', '.pdf'] as const;
export const ACCEPTED_KNOWLEDGE_FILE_TYPES = SUPPORTED_KNOWLEDGE_EXTENSIONS.join(',');

export const knowledgeIdOf = (record?: Partial<KnowledgeBaseRow> | null): string => (
  String(record?.kbId || record?.knowledgeTag || record?.ragId || '').trim()
);

export const knowledgeNameOf = (record?: Partial<KnowledgeBaseRow> | null): string => (
  String(record?.kbName || record?.ragName || record?.name || knowledgeIdOf(record)).trim()
);

export const isKnowledgeEnabled = (record?: Partial<KnowledgeBaseRow> | null): boolean => (
  record?.status === 1
  || record?.statusCode === 1
  || String(record?.status || '').toUpperCase() === 'ENABLED'
);

export const filterKnowledgeBases = (
  rows: RagKnowledgeBaseSummary[],
  filters: KnowledgeQueryFilters,
  page: number,
  pageSize: number,
): KnowledgePage => {
  const ragId = String(filters.ragId || '').trim();
  const ragName = String(filters.ragName || '').trim();
  const knowledgeTag = String(filters.knowledgeTag || '').trim();
  const normalizedPage = Math.max(1, Number(page) || 1);
  const normalizedSize = Math.max(1, Number(pageSize) || 10);
  const filtered = (rows || []).filter((item) => {
    const id = knowledgeIdOf(item);
    const name = knowledgeNameOf(item);
    const status = isKnowledgeEnabled(item) ? 1 : 0;
    return (!ragId || id.includes(ragId))
      && (!ragName || name.includes(ragName))
      && (!knowledgeTag || id.includes(knowledgeTag))
      && (filters.status === undefined || status === filters.status);
  });
  const start = (normalizedPage - 1) * normalizedSize;
  return {
    items: filtered.slice(start, start + normalizedSize),
    total: filtered.length,
  };
};

export const buildKnowledgeOverview = (
  rows: KnowledgeBaseRow[],
  documents: RagKnowledgeDocumentRecord[],
  documentStats: RagDocumentStats | null | undefined,
  ingestionJobs: RagIngestionJobRecord[],
): KnowledgeOverview => {
  const tags = new Set<string>();
  documents.forEach((document) => {
    const tag = String(document.tag || document.knowledgeTag || document.kbId || '').trim();
    if (tag) tags.add(tag);
  });
  rows.forEach((item) => {
    const tag = knowledgeIdOf(item);
    if (tag) tags.add(tag);
  });
  return {
    chunkCount: documentStats?.chunkCount ?? documents.length,
    documentCount: documentStats?.documentCount ?? '-',
    knowledgeCount: rows.length,
    enabledCount: rows.filter(isKnowledgeEnabled).length,
    tagCount: tags.size,
    activeJobs: ingestionJobs.filter((item) => item.status === 'PENDING' || item.status === 'RUNNING').length,
  };
};

export const activeIngestionJobCount = (jobs: RagIngestionJobRecord[] = []): number => (
  jobs.filter((job) => job.status === 'PENDING' || job.status === 'RUNNING').length
);

export const normalizeRetrievalPolicy = (
  policy: RagKnowledgeRetrievalPolicy | null | undefined,
): RetrievalPolicyDraft => ({
  ...(policy || {}),
  segmentationMode: policy?.segmentationMode || 'STRUCTURE_FIRST',
  structurePreserved: policy?.structurePreserved !== false,
  maxSegmentChars: Number(policy?.maxSegmentChars ?? policy?.chunkSize ?? 3000),
  hardSplitOverlapChars: Number(policy?.hardSplitOverlapChars ?? policy?.overlapSize ?? 0),
  topK: Number(policy?.topK ?? 5),
  metadataFilterJson: policy?.metadataFilterJson || '{}',
});

export const validateRetrievalPolicy = (
  policy: RagKnowledgeRetrievalPolicy | null | undefined,
): { payload?: RetrievalPolicyPayload; error?: string } => {
  if (!policy) return { error: '检索策略不能为空' };
  const maxSegmentChars = Number(policy.maxSegmentChars ?? policy.chunkSize ?? 3000);
  const hardSplitOverlapChars = Number(policy.hardSplitOverlapChars ?? policy.overlapSize ?? 0);
  const topK = Number(policy.topK ?? 5);
  if (maxSegmentChars < 1000 || maxSegmentChars > 12000) {
    return { error: '结构单元最大长度必须在 1000 到 12000 字符之间' };
  }
  if (hardSplitOverlapChars < 0 || hardSplitOverlapChars > Math.floor(maxSegmentChars / 2)) {
    return { error: '超长块重叠字符数不能超过结构单元最大长度的一半' };
  }
  if (topK < 1 || topK > 50) {
    return { error: 'Top K 必须在 1 到 50 之间' };
  }
  const metadataFilterJson = policy.metadataFilterJson || '{}';
  try {
    JSON.parse(metadataFilterJson);
  } catch {
    return { error: 'Metadata Filter 必须是合法 JSON' };
  }
  return {
    payload: {
      maxSegmentChars,
      hardSplitOverlapChars,
      topK,
      rerankEnabled: Boolean(policy.rerankEnabled),
      embeddingModelId: policy.embeddingModelId || '',
      metadataFilterJson,
    },
  };
};

export const validateKnowledgeUploadFile = (
  file: Pick<File, 'name' | 'size'> | { name?: string; size?: number } | null | undefined,
): string | null => {
  if (!file?.name) return '文件对象无效';
  const fileName = file.name.toLowerCase();
  if (!SUPPORTED_KNOWLEDGE_EXTENSIONS.some((extension) => fileName.endsWith(extension))) {
    return '当前仅支持 Markdown 和 PDF 文件';
  }
  if (Number(file.size || 0) > 20 * 1024 * 1024) {
    return '单个文件大小不能超过20MB';
  }
  return null;
};
