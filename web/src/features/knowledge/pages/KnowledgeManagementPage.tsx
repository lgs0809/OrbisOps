import React, { useMemo, useState } from 'react';
import {
  Table,
  Button,
  Input,
  Space,
  Typography,
  Toast,
  Tag,
  Popconfirm,
  Select,
  Modal,
  Form,
  Upload,
  TextArea,
  InputNumber,
  Switch
} from '@douyinfe/semi-ui';
import {
  IconSearch,
  IconDelete,
  IconRefresh,
  IconUpload,
  IconPlus
} from '@douyinfe/semi-icons';
import styled from 'styled-components';
import { theme } from '../../../styles/theme';
import {
  OpsAdvancedPreview,
  OpsEmptyState,
  OpsPageHeader,
  OpsPageShell,
  OpsSectionCard,
  TableScroll,
} from '../../../components/ops-layout';
import {
  RagKnowledgeDocumentRecord,
  RagKnowledgeBaseUsageProject,
  RagEvalCase,
  RagKnowledgeGap,
  RagKnowledgeRetrievalPolicy,
} from '../../../services/ai-client-rag-order-admin-service';
import { generatedId } from '../../../utils/generated-id';
import { KnowledgeLifecycleFlow } from '../components/KnowledgeLifecycleFlow';
import { KnowledgeOverview } from '../components/KnowledgeOverview';
import {
  useDeleteKnowledgeChunkMutation,
  useDeleteKnowledgeChunksMutation,
  useDeleteRagEvalCaseMutation,
  useGapToEvalCaseMutation,
  useGlobalKnowledgeBasesQuery,
  useKnowledgeChunksQuery,
  useKnowledgeIngestionJobsQuery,
  useKnowledgeStatsQuery,
  useLoadKnowledgeDocumentContentMutation,
  useLoadKnowledgePolicyMutation,
  useKnowledgeUsageProjectsQuery,
  useProbeRagQualityMutation,
  useRagEvalCasesQuery,
  useRagFeedbackQuery,
  useRagKnowledgeGapsQuery,
  useRunRagEvalMutation,
  useSaveGlobalKnowledgeMutation,
  useSaveRagEvalCaseMutation,
  useSubmitRagFeedbackMutation,
  useToggleGlobalKnowledgeMutation,
  useUpdateKnowledgeGapMutation,
  useUpdateKnowledgePolicyMutation,
  useUploadKnowledgeDocumentsMutation,
} from '../api/knowledge-queries';
import {
  ACCEPTED_KNOWLEDGE_FILE_TYPES,
  buildKnowledgeOverview,
  filterKnowledgeBases,
  isKnowledgeEnabled,
  KnowledgeBaseRow,
  KnowledgeQueryFilters,
  knowledgeIdOf,
  knowledgeNameOf,
  normalizeRetrievalPolicy,
  validateKnowledgeUploadFile,
  validateRetrievalPolicy,
} from '../model/knowledge-model';

const { Title } = Typography;
const { Option } = Select;

const RagOrderManagementContainer = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${theme.spacing.lg};
  width: 100%;
  min-width: 0;
`;

const SearchSection = styled(OpsSectionCard)`
  margin-bottom: 0;

  .semi-card-body {
    padding: ${theme.spacing.lg};
  }
`;

const SearchRow = styled.div`
  display: flex;
  align-items: center;
  gap: ${theme.spacing.base};
  flex-wrap: wrap;
  min-width: 0;
`;

const OverviewGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: ${theme.spacing.base};
  min-width: 0;
`;

const OverviewTile = styled.div`
  border: 1px solid ${theme.colors.border.secondary};
  border-radius: ${theme.borderRadius.base};
  background: ${theme.colors.bg.primary};
  padding: ${theme.spacing.base};
`;

const OverviewLabel = styled.div`
  color: ${theme.colors.text.tertiary};
  font-size: ${theme.typography.fontSize.sm};
  margin-bottom: ${theme.spacing.xs};
`;

const OverviewValue = styled.div`
  color: ${theme.colors.text.primary};
  font-size: ${theme.typography.fontSize.xl};
  font-weight: ${theme.typography.fontWeight.semibold};
`;

const HelperText = styled(Typography.Text)`
  display: block;
  margin-top: ${theme.spacing.sm};
  line-height: 1.6;
`;

const TableContainer = styled.div`
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
`;

const DocumentContainer = styled.div`
  min-width: 0;
`;

const TableCard = styled(OpsSectionCard)`
  flex: 1;
  display: flex;
  flex-direction: column;
  margin-bottom: 0;

  .semi-card-body {
    padding: 0;
    flex: 1;
    display: flex;
    flex-direction: column;
  }
`;

const DocumentContent = styled.pre`
  width: 100%;
  max-width: 100%;
  max-height: 520px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-word;
  margin: 0;
  padding: ${theme.spacing.lg};
  background: ${theme.colors.bg.secondary};
  border: 1px solid ${theme.colors.border.secondary};
  border-radius: ${theme.borderRadius.base};
  font-size: ${theme.typography.fontSize.sm};
  line-height: 1.7;
`;

const PolicyFormGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: ${theme.spacing.lg};
  min-width: 0;

  @media (max-width: 720px) {
    grid-template-columns: minmax(0, 1fr);
  }
`;

const PolicyField = styled.div`
  min-width: 0;
`;

const PolicyFieldLabel = styled(Typography.Text)`
  display: block;
  margin-bottom: ${theme.spacing.xs};
  font-weight: ${theme.typography.fontWeight.medium};
`;

const StructureFlow = styled.div`
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: ${theme.spacing.base};
  padding: ${theme.spacing.lg};
  min-width: 0;

  @media (max-width: 960px) {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  @media (max-width: 640px) {
    grid-template-columns: minmax(0, 1fr);
  }
`;

const StructureStep = styled.div`
  min-width: 0;
  padding: ${theme.spacing.base};
  border: 1px solid ${theme.colors.border.secondary};
  border-radius: ${theme.borderRadius.base};
  background: ${theme.colors.bg.secondary};
`;

const compactChunkId = (value?: string) => {
  if (!value) {
    return '-';
  }
  return value.length > 18 ? `${value.slice(0, 10)}...${value.slice(-6)}` : value;
};

const jobStatusColor = (status?: string) => {
  switch (status) {
    case 'SUCCEEDED':
      return 'green';
    case 'FAILED':
      return 'red';
    case 'RUNNING':
      return 'blue';
    case 'PENDING':
      return 'orange';
    default:
      return 'grey';
  }
};

export const KnowledgeManagementPage: React.FC = () => {
  const [searchText, setSearchText] = useState('');
  const [searchRagName, setSearchRagName] = useState('');
  const [searchKnowledgeTag, setSearchKnowledgeTag] = useState('');
  const [searchStatus, setSearchStatus] = useState<number | undefined>(undefined);
  const [appliedFilters, setAppliedFilters] = useState<KnowledgeQueryFilters>({});
  const [selectedKnowledgeId, setSelectedKnowledgeId] = useState('');
  const [currentPage, setCurrentPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [qualityQuery, setQualityQuery] = useState('');
  const [qualityExpected, setQualityExpected] = useState('');
  const [qualityTopK, setQualityTopK] = useState(8);
  const [documentModalVisible, setDocumentModalVisible] = useState(false);
  const [selectedDocument, setSelectedDocument] = useState<RagKnowledgeDocumentRecord | null>(null);
  const [policyModalVisible, setPolicyModalVisible] = useState(false);
  const [policyKbId, setPolicyKbId] = useState('');
  const [retrievalPolicy, setRetrievalPolicy] = useState<RagKnowledgeRetrievalPolicy | null>(null);
  const [knowledgeModalVisible, setKnowledgeModalVisible] = useState(false);
  const [editingKnowledgeId, setEditingKnowledgeId] = useState('');
  const [knowledgeDraft, setKnowledgeDraft] = useState({
    kbId: '',
    kbName: '',
    description: '',
  });

  // 上传弹窗只保留本地表单状态；上传结果与任务状态由 TanStack Query 管理。
  const [uploadModalVisible, setUploadModalVisible] = useState(false);
  const [uploadFormApi, setUploadFormApi] = useState<any>(null);
  const [fileList, setFileList] = useState<any[]>([]);
  const [uploadKnowledgeId, setUploadKnowledgeId] = useState('');

  const globalBasesQuery = useGlobalKnowledgeBasesQuery();
  const chunksQuery = useKnowledgeChunksQuery(selectedKnowledgeId);
  const statsQuery = useKnowledgeStatsQuery(selectedKnowledgeId);
  const usageQuery = useKnowledgeUsageProjectsQuery(selectedKnowledgeId);
  const jobsQuery = useKnowledgeIngestionJobsQuery();
  const evalCasesQuery = useRagEvalCasesQuery();
  const feedbackQuery = useRagFeedbackQuery(selectedKnowledgeId);
  const gapsQuery = useRagKnowledgeGapsQuery(selectedKnowledgeId);

  const saveKnowledgeMutation = useSaveGlobalKnowledgeMutation();
  const toggleKnowledgeMutation = useToggleGlobalKnowledgeMutation();
  const uploadDocumentsMutation = useUploadKnowledgeDocumentsMutation();
  const deleteChunkMutation = useDeleteKnowledgeChunkMutation();
  const deleteChunksMutation = useDeleteKnowledgeChunksMutation();
  const updatePolicyMutation = useUpdateKnowledgePolicyMutation();
  const loadPolicyMutation = useLoadKnowledgePolicyMutation();
  const loadDocumentContentMutation = useLoadKnowledgeDocumentContentMutation();
  const probeQualityMutation = useProbeRagQualityMutation();
  const saveEvalCaseMutation = useSaveRagEvalCaseMutation();
  const runEvalMutation = useRunRagEvalMutation();
  const deleteEvalCaseMutation = useDeleteRagEvalCaseMutation();
  const submitFeedbackMutation = useSubmitRagFeedbackMutation(selectedKnowledgeId);
  const updateGapMutation = useUpdateKnowledgeGapMutation(selectedKnowledgeId);
  const gapToEvalMutation = useGapToEvalCaseMutation(selectedKnowledgeId);

  const knowledgeBases = globalBasesQuery.data || [];
  const knowledgePage = useMemo(
    () => filterKnowledgeBases(knowledgeBases, appliedFilters, currentPage, pageSize),
    [appliedFilters, currentPage, knowledgeBases, pageSize],
  );
  const dataSource = knowledgePage.items;
  const total = knowledgePage.total;
  const documents = chunksQuery.data || [];
  const documentStats = statsQuery.data || null;
  const ingestionJobs = jobsQuery.data || [];
  const evalCases = evalCasesQuery.data || [];
  const ragFeedbacks = feedbackQuery.data || [];
  const ragGaps = gapsQuery.data || [];
  const usageProjects = usageQuery.data || [];
  const qualityResult = probeQualityMutation.data || null;
  const evalRunResult = runEvalMutation.data || null;

  const loading = globalBasesQuery.isFetching;
  const documentLoading = chunksQuery.isFetching;
  const jobLoading = jobsQuery.isFetching;
  const feedbackLoading = feedbackQuery.isFetching || gapsQuery.isFetching;
  const usageLoading = usageQuery.isFetching;
  const knowledgeSaving = saveKnowledgeMutation.isPending || toggleKnowledgeMutation.isPending;
  const policyLoading = loadPolicyMutation.isPending;
  const policySaving = updatePolicyMutation.isPending;
  const uploadLoading = uploadDocumentsMutation.isPending;
  const qualityLoading = probeQualityMutation.isPending;
  const evalLoading = runEvalMutation.isPending;

  const overview = useMemo(
    () => buildKnowledgeOverview(knowledgeBases, documents, documentStats, ingestionJobs),
    [documentStats, documents, ingestionJobs, knowledgeBases],
  );
  const knowledgeBaseByTag = useMemo(
    () => new Map(knowledgeBases.map((item) => [knowledgeIdOf(item), item])),
    [knowledgeBases],
  );
  const documentCountOf = (record: KnowledgeBaseRow) =>
    record.documentCount ?? knowledgeBaseByTag.get(knowledgeIdOf(record))?.documentCount ?? '-';

  const usedProjectCountOf = (record: KnowledgeBaseRow) => {
    const raw = record as unknown as Record<string, unknown>;
    return typeof raw.projectCount === 'number'
      ? raw.projectCount
      : typeof raw.usedProjectCount === 'number'
      ? raw.usedProjectCount
      : '-';
  };

  const selectedKnowledgeBase = useMemo(
    () => knowledgeBases.find((item) => knowledgeIdOf(item) === selectedKnowledgeId),
    [knowledgeBases, selectedKnowledgeId],
  );

  // 表格列定义
  const columns = [
    {
      title: '知识库名称',
      dataIndex: 'ragName',
      key: 'ragName',
      width: 220,
      render: (_name: string, record: KnowledgeBaseRow) => (
        <Space vertical align="start" spacing={2}>
          <Typography.Text strong>{knowledgeNameOf(record)}</Typography.Text>
          <Typography.Text type="tertiary" size="small">{knowledgeIdOf(record)}</Typography.Text>
        </Space>
      ),
    },
    {
      title: '描述',
      dataIndex: 'knowledgeTag',
      key: 'knowledgeTag',
      render: (_tag: string, record: KnowledgeBaseRow) => (
        <Space vertical align="start" spacing={2}>
          <Typography.Text>{record.description || '未填写说明'}</Typography.Text>
          <Tag color="blue">{knowledgeIdOf(record) || '未标记'}</Tag>
        </Space>
      ),
    },
    {
      title: '文档数量',
      key: 'documentCount',
      width: 110,
      render: (_: unknown, record: KnowledgeBaseRow) => documentCountOf(record),
    },
    {
      title: '启用状态',
      dataIndex: 'status',
      key: 'status',
      width: 110,
      render: (_status: number | string, record: KnowledgeBaseRow) => (
        <Tag color={isKnowledgeEnabled(record) ? 'green' : 'red'}>
          {isKnowledgeEnabled(record) ? '启用' : '停用'}
        </Tag>
      ),
    },
    {
      title: '使用项目数',
      key: 'projectCount',
      width: 120,
      render: (_: unknown, record: KnowledgeBaseRow) => usedProjectCountOf(record),
    },
    {
      title: '更新时间',
      dataIndex: 'updateTime',
      key: 'updateTime',
      width: 180,
      render: (time: string) => time ? new Date(time).toLocaleString() : '-',
    },
    {
      title: '操作',
      key: 'action',
      width: 330,
      fixed: 'right' as const,
      render: (_: any, record: KnowledgeBaseRow) => (
        <Space>
          <Button size="small" theme="borderless" onClick={() => {
            const knowledgeId = knowledgeIdOf(record);
            setSelectedKnowledgeId(knowledgeId);
            setSearchKnowledgeTag(knowledgeId);
          }}>
            查看
          </Button>
          <Button
            size="small"
            theme="borderless"
            onClick={() => handleOpenKnowledgeModal(record)}
          >
            编辑
          </Button>
          <Button size="small" theme="borderless" onClick={() => handleOpenPolicy(knowledgeIdOf(record))}>
            检索策略
          </Button>
          <Button size="small" theme="borderless" onClick={() => setSelectedKnowledgeId(knowledgeIdOf(record))}>
            使用项目
          </Button>
          <Button size="small" theme="borderless" onClick={() => handleToggleKnowledgeStatus(record)}>
            {isKnowledgeEnabled(record) ? '停用' : '启用'}
          </Button>
        </Space>
      ),
    },
  ];

  const usageProjectColumns = [
    {
      title: '项目',
      dataIndex: 'projectName',
      key: 'projectName',
      render: (name: string, record: RagKnowledgeBaseUsageProject) => (
        <Space vertical align="start" spacing={2}>
          <Typography.Text strong>{name || record.projectId}</Typography.Text>
          <Typography.Text type="tertiary" size="small">{record.projectId}</Typography.Text>
        </Space>
      ),
    },
    {
      title: '授权状态',
      dataIndex: 'status',
      key: 'status',
      width: 110,
      render: (status: string) => (
        <Tag color={String(status).toUpperCase() === 'ENABLED' ? 'green' : 'grey'}>
          {String(status).toUpperCase() === 'ENABLED' ? '已启用' : '已停用'}
        </Tag>
      ),
    },
    {
      title: '启用人',
      dataIndex: 'enabledBy',
      key: 'enabledBy',
      width: 140,
      render: (value: string) => value || '-',
    },
    {
      title: '启用时间',
      dataIndex: 'enabledTime',
      key: 'enabledTime',
      width: 190,
      render: (value: string) => value ? new Date(value).toLocaleString() : '-',
    },
  ];

  const documentColumns = [
    {
      title: '文档来源',
      dataIndex: 'displayName',
      key: 'displayName',
      width: 260,
    },
    {
      title: '片段 ID',
      dataIndex: 'fileName',
      key: 'fileName',
      width: 150,
      render: (value: string) => compactChunkId(value),
    },
    {
      title: '场景标签',
      dataIndex: 'tag',
      key: 'tag',
      width: 140,
      render: (tag: string) => <Tag color="blue">{tag}</Tag>,
    },
    {
      title: '类型',
      dataIndex: 'documentType',
      key: 'documentType',
      width: 120,
      render: (value: string) => <Tag color="grey">{value || '-'}</Tag>,
    },
    {
      title: '结构策略',
      dataIndex: 'chunkStrategy',
      key: 'chunkStrategy',
      width: 150,
      render: (value: string) => value || '-',
    },
    {
      title: '片段大小',
      dataIndex: 'size',
      key: 'size',
      width: 100,
      render: (size: number) => `${Math.max(1, Math.ceil((size || 0) / 1024))} KB`,
    },
    {
      title: '更新时间',
      dataIndex: 'updateTime',
      key: 'updateTime',
      width: 180,
      render: (time: string) => time || '-',
    },
    {
      title: '操作',
      key: 'action',
      width: 180,
      render: (_: any, record: RagKnowledgeDocumentRecord) => (
        <Space>
          <Button size="small" theme="light" onClick={() => handlePreviewDocument(record)}>
            查看内容
          </Button>
          <Popconfirm
            title="确定删除这个结构化片段吗？"
            content="只会删除 PgVector 中的该片段，不会操作服务器文件目录。"
            onConfirm={() => handleDeleteChunk(record)}
            okText="确定"
            cancelText="取消"
          >
            <Button size="small" type="danger" theme="borderless" icon={<IconDelete />}>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  const jobColumns = [
    {
      title: '任务 ID',
      dataIndex: 'jobId',
      key: 'jobId',
      width: 180,
      render: (value: string) => compactChunkId(value),
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 110,
      render: (status: string) => <Tag color={jobStatusColor(status)}>{status}</Tag>,
    },
    {
      title: '名称',
      dataIndex: 'name',
      key: 'name',
      width: 180,
    },
    {
      title: '标签',
      dataIndex: 'tag',
      key: 'tag',
      width: 140,
      render: (tag: string) => <Tag color="blue">{tag}</Tag>,
    },
    {
      title: '文件',
      dataIndex: 'fileNames',
      key: 'fileNames',
      render: (fileNames: string[]) => (fileNames || []).join('，') || '-',
    },
    {
      title: '更新时间',
      dataIndex: 'updatedAt',
      key: 'updatedAt',
      width: 180,
    },
    {
      title: '错误',
      dataIndex: 'errorMessage',
      key: 'errorMessage',
      width: 220,
      render: (value: string) => value || '-',
    },
  ];

  // 兼容现有按钮语义：这些函数只协调当前选择与 Query refetch，不再复制服务器状态。
  const fetchDocumentList = async (tag = selectedKnowledgeId) => {
    const kbId = tag.trim();
    if (!kbId) {
      setSelectedKnowledgeId('');
      return;
    }
    if (kbId !== selectedKnowledgeId) {
      setSelectedKnowledgeId(kbId);
      return;
    }
    await chunksQuery.refetch();
  };

  const fetchKnowledgeLifecycle = async (tag = selectedKnowledgeId) => {
    const kbId = tag.trim();
    if (kbId && kbId !== selectedKnowledgeId) {
      setSelectedKnowledgeId(kbId);
      await globalBasesQuery.refetch();
      return;
    }
    await Promise.all([globalBasesQuery.refetch(), statsQuery.refetch()]);
  };

  const fetchUsageProjects = async (kbId = selectedKnowledgeId, silent = false) => {
    const normalized = kbId.trim();
    if (!normalized) {
      if (!silent) Toast.warning('请先选择一个通用知识库');
      return;
    }
    setSearchKnowledgeTag(normalized);
    if (normalized !== selectedKnowledgeId) {
      setSelectedKnowledgeId(normalized);
      return;
    }
    await usageQuery.refetch();
  };

  const handleOpenKnowledgeModal = (record?: KnowledgeBaseRow) => {
    const kbId = record ? knowledgeIdOf(record) : '';
    setEditingKnowledgeId(kbId);
    setKnowledgeDraft({
      kbId,
      kbName: record ? knowledgeNameOf(record) : '',
      description: record?.description || '',
    });
    setKnowledgeModalVisible(true);
  };

  const handleSaveKnowledge = async () => {
    const kbName = knowledgeDraft.kbName.trim();
    if (!kbName) {
      Toast.warning('知识库名称不能为空');
      return;
    }
    const wasEditing = Boolean(editingKnowledgeId);
    const kbId = editingKnowledgeId
      ? knowledgeDraft.kbId.trim()
      : generatedId('knowledge', kbName);
    try {
      await saveKnowledgeMutation.mutateAsync({
        editingKbId: editingKnowledgeId || undefined,
        payload: {
          kbId,
          kbName,
          name: kbName,
          description: knowledgeDraft.description.trim(),
        },
      });
      setKnowledgeModalVisible(false);
      setEditingKnowledgeId('');
      setKnowledgeDraft({ kbId: '', kbName: '', description: '' });
      setSelectedKnowledgeId(kbId);
      setCurrentPage(1);
      Toast.success(wasEditing ? '通用知识库已更新' : '通用知识库已创建');
    } catch (error) {
      Toast.error(error instanceof Error ? error.message : '保存通用知识库失败');
    }
  };

  const handleToggleKnowledgeStatus = async (record: KnowledgeBaseRow) => {
    const kbId = knowledgeIdOf(record);
    const nextStatus = isKnowledgeEnabled(record) ? 'DISABLED' : 'ENABLED';
    if (nextStatus === 'DISABLED' && !window.confirm(`确认停用通用知识库 ${kbId}？已授权项目将无法继续把它作为可用知识库。`)) {
      return;
    }
    try {
      await toggleKnowledgeMutation.mutateAsync({ kbId, status: nextStatus });
      Toast.success(nextStatus === 'ENABLED' ? '通用知识库已启用' : '通用知识库已停用');
    } catch (error) {
      Toast.error(error instanceof Error ? error.message : '更新知识库状态失败');
    }
  };

  const fetchIngestionJobs = async () => {
    const result = await jobsQuery.refetch();
    if (result.error) {
      Toast.error('获取入库任务失败');
    }
  };

  const handleProbeQuality = async () => {
    if (!qualityQuery.trim()) {
      Toast.warning('请输入要验证的检索问题');
      return;
    }
    try {
      const expectedKeywords = qualityExpected
        .split(/[\n,，;；、]+/)
        .map((item) => item.trim())
        .filter(Boolean);
      await probeQualityMutation.mutateAsync({
        query: qualityQuery.trim(),
        knowledgeTag: selectedKnowledgeId || undefined,
        expectedKeywords,
        topK: qualityTopK,
      });
    } catch (error) {
      console.error('RAG 质量探测失败:', error);
      Toast.error('RAG 质量探测失败');
    }
  };

  const fetchRagFeedback = async (tag = selectedKnowledgeId) => {
    const normalized = tag.trim();
    if (normalized !== selectedKnowledgeId) {
      setSelectedKnowledgeId(normalized);
      return;
    }
    await Promise.all([feedbackQuery.refetch(), gapsQuery.refetch()]);
  };

  const handleSubmitRagFeedback = async (useful: boolean, resolved: boolean) => {
    if (!qualityQuery.trim()) {
      Toast.warning('请先输入检索问题');
      return;
    }
    const chunkIds = (qualityResult?.hits || [])
      .map((hit) => String(hit.chunkId || ''))
      .filter(Boolean);
    try {
      await submitFeedbackMutation.mutateAsync({
        query: qualityQuery.trim(),
        answer: qualityResult?.recommendation || '',
        useful,
        resolved,
        sourceType: 'RAG_QUALITY_PROBE',
        sourceId: qualityResult?.query || qualityQuery.trim(),
        knowledgeTag: selectedKnowledgeId || qualityResult?.knowledgeTag,
        chunkIds,
        comment: useful ? '人工确认检索结果有效' : '人工确认检索结果不足，形成知识缺口',
      });
      Toast.success(useful ? '反馈已记录' : '已记录反馈并形成知识缺口');
    } catch (error) {
      console.error('提交 RAG 反馈失败:', error);
      Toast.error('提交 RAG 反馈失败');
    }
  };

  const handleUpdateGapStatus = async (id: number, status: string) => {
    try {
      await updateGapMutation.mutateAsync({ id, status });
      Toast.success('知识缺口状态已更新');
    } catch (error) {
      Toast.error(error instanceof Error ? error.message : '更新知识缺口失败');
    }
  };

  const handleGapToEvalCase = async (id: number) => {
    try {
      await gapToEvalMutation.mutateAsync(id);
      Toast.success('已转为评测用例');
    } catch (error) {
      Toast.error(error instanceof Error ? error.message : '转评测用例失败');
    }
  };

  const handleSaveEvalCase = async () => {
    if (!qualityQuery.trim()) {
      Toast.warning('请输入评测问题');
      return;
    }
    const expectedKeywords = qualityExpected
      .split(/[\n,，;；、]+/)
      .map((item) => item.trim())
      .filter(Boolean);
    try {
      await saveEvalCaseMutation.mutateAsync({
        caseName: qualityQuery.trim().slice(0, 80),
        query: qualityQuery.trim(),
        knowledgeTag: selectedKnowledgeId || undefined,
        expectedKeywords,
        topK: qualityTopK,
        enabled: true,
      });
      Toast.success('评测用例已保存');
    } catch (error) {
      console.error('保存 RAG 评测用例失败:', error);
      Toast.error('保存评测用例失败');
    }
  };

  const handleRunEval = async () => {
    try {
      await runEvalMutation.mutateAsync();
      Toast.success('RAG 离线评测完成');
    } catch (error) {
      console.error('运行 RAG 离线评测失败:', error);
      Toast.error('运行 RAG 离线评测失败');
    }
  };

  const handleDeleteEvalCase = async (id?: number) => {
    if (!id) {
      return;
    }
    try {
      await deleteEvalCaseMutation.mutateAsync(id);
      Toast.success('评测用例已删除');
    } catch (error) {
      console.error('删除 RAG 评测用例失败:', error);
      Toast.error('删除评测用例失败');
    }
  };

  const handleDeleteChunk = async (record: RagKnowledgeDocumentRecord) => {
    const chunkId = record.chunkId || record.fileName;
    const knowledgeBaseId = record.kbId || record.knowledgeTag || record.tag || selectedKnowledgeId;
    if (!chunkId) {
      Toast.warning('当前记录缺少片段 ID，无法删除');
      return;
    }
    if (!knowledgeBaseId) {
      Toast.warning('当前记录缺少知识库标签，无法执行隔离删除');
      return;
    }
    try {
      await deleteChunkMutation.mutateAsync({ kbId: knowledgeBaseId, chunkId });
      Toast.success('结构化片段已删除');
    } catch (error) {
      console.error('删除结构化片段失败:', error);
      Toast.error('删除结构化片段失败');
    }
  };

  const handleDeleteChunksByTag = async () => {
    if (!selectedKnowledgeId) {
      Toast.warning('请先选择知识库');
      return;
    }
    try {
      const result = await deleteChunksMutation.mutateAsync(selectedKnowledgeId);
      Toast.success(`已删除 ${result?.deletedChunks || 0} 个结构化片段`);
    } catch (error) {
      console.error('按知识库清理结构化片段失败:', error);
      Toast.error('按知识库清理结构化片段失败');
    }
  };

  const handleOpenPolicy = async (kbId: string) => {
    if (!kbId) {
      Toast.warning('请先选择知识库');
      return;
    }
    setPolicyKbId(kbId);
    setPolicyModalVisible(true);
    try {
      const policy = await loadPolicyMutation.mutateAsync(kbId);
      setRetrievalPolicy(normalizeRetrievalPolicy(policy));
    } catch (error) {
      console.error('获取知识库策略失败:', error);
      Toast.error('获取知识库策略失败');
      setPolicyModalVisible(false);
      setRetrievalPolicy(null);
    }
  };

  const updatePolicyField = <K extends keyof RagKnowledgeRetrievalPolicy>(
    key: K,
    value: RagKnowledgeRetrievalPolicy[K],
  ) => {
    setRetrievalPolicy((current) => current ? { ...current, [key]: value } : current);
  };

  const handleSavePolicy = async () => {
    if (!policyKbId || !retrievalPolicy) {
      return;
    }
    const validation = validateRetrievalPolicy(retrievalPolicy);
    if (!validation.payload) {
      Toast.warning(validation.error || '检索策略不合法');
      return;
    }
    try {
      const updated = await updatePolicyMutation.mutateAsync({ kbId: policyKbId, payload: validation.payload });
      setRetrievalPolicy(updated || retrievalPolicy);
      setPolicyModalVisible(false);
      Toast.success('结构化解析与检索策略已更新');
    } catch (error) {
      console.error('更新知识库策略失败:', error);
      Toast.error('更新知识库策略失败');
    }
  };

  // 打开上传弹窗
  const handleOpenUploadModal = () => {
    const exactSearchId = searchKnowledgeTag.trim();
    const preferred = selectedKnowledgeId
      || (exactSearchId && knowledgeBases.some((item) => knowledgeIdOf(item) === exactSearchId) ? exactSearchId : '')
      || knowledgeIdOf(knowledgeBases[0]);
    if (!preferred) {
      Toast.warning('请先新建一个知识库，再导入文档');
      return;
    }
    setUploadKnowledgeId(preferred);
    setUploadModalVisible(true);
    if (uploadFormApi) {
      uploadFormApi.reset();
    }
    setFileList([]);
  };

  // 关闭上传弹窗
  const handleCloseUploadModal = () => {
    setUploadModalVisible(false);
    setUploadKnowledgeId('');
    if (uploadFormApi) {
      uploadFormApi.reset();
    }
    setFileList([]);
  };

  // 处理文件上传
  const handleUploadSubmit = async () => {
    try {
      if (!uploadFormApi) {
        Toast.error('表单未初始化');
        return;
      }

      const values = await uploadFormApi.validate();

      if (fileList.length === 0) {
        Toast.error('请选择要上传的文件');
        return;
      }
      if (!uploadKnowledgeId) {
        Toast.error('请选择目标知识库');
        return;
      }

      const files = fileList
        .map((item) => item.fileInstance)
        .filter((file): file is File => file instanceof File);

      if (files.length === 0) {
        Toast.error('文件对象读取失败，请重新选择文件');
        return;
      }

      const job = await uploadDocumentsMutation.mutateAsync({
        kbId: uploadKnowledgeId,
        name: values.name,
        files,
      });
      setSelectedKnowledgeId(uploadKnowledgeId);
      handleCloseUploadModal();
      if (job?.jobId) {
        Toast.success(`入库任务已创建：${job.jobId}`);
      } else {
        Toast.warning('任务已提交，但未返回任务详情');
      }
    } catch (error) {
      console.error('上传知识库文件失败:', error);
      Toast.error('上传失败，请检查网络连接');
    }
  };

  // 文件上传前的处理
  const beforeUpload = ({ file }: any) => {
    const fileInstance = file?.fileInstance || file;
    const validationError = validateKnowledgeUploadFile(fileInstance);
    if (validationError) {
      Toast.error(validationError);
      return false;
    }
    return true;
  };

  // 文件列表变化处理
  const handleFileChange = (options: any) => {
    setFileList(options.fileList || []);
  };



  // 搜索只提交筛选条件；只有精确命中知识库 ID 时才切换详情上下文。
  const handleSearch = () => {
    const filters: KnowledgeQueryFilters = {
      ragId: searchText,
      ragName: searchRagName,
      knowledgeTag: searchKnowledgeTag,
      status: searchStatus,
    };
    setAppliedFilters(filters);
    setCurrentPage(1);
    const exactKnowledgeId = searchKnowledgeTag.trim();
    if (exactKnowledgeId && knowledgeBases.some((item) => knowledgeIdOf(item) === exactKnowledgeId)) {
      setSelectedKnowledgeId(exactKnowledgeId);
    }
  };

  // 重置搜索
  const handleReset = () => {
    setSearchText('');
    setSearchRagName('');
    setSearchKnowledgeTag('');
    setSearchStatus(undefined);
    setAppliedFilters({});
    setSelectedKnowledgeId('');
    setCurrentPage(1);
  };

  const handlePreviewDocument = async (record: RagKnowledgeDocumentRecord) => {
    if (!record.fileName) {
      Toast.warning('当前记录缺少文档标识，无法查看内容');
      return;
    }
    try {
      const document = await loadDocumentContentMutation.mutateAsync(record.fileName);
      setSelectedDocument(document);
      setDocumentModalVisible(true);
    } catch (error) {
      console.error('查询文档内容失败:', error);
      Toast.error('查询文档内容失败');
    }
  };

  // 分页只更新本地视图；全量知识库结果由 Query 缓存复用，不再重新请求。
  const handlePageChange = (page: number, size?: number) => {
    const nextSize = size || pageSize;
    setCurrentPage(page);
    if (nextSize !== pageSize) {
      setPageSize(nextSize);
    }
  };

  return (
    <OpsPageShell selectedKey="rag-order-management">
          <RagOrderManagementContainer>
            <OpsPageHeader
              title="通用知识库"
              description="这里维护跨项目复用的文档、SOP、FAQ、制度和公共排障知识。项目知识库请在项目空间中配置，Agent 运行时只能读取当前项目授权的知识库。"
              primaryAction={(
                <Button type="primary" onClick={() => handleOpenKnowledgeModal()}>
                  新建通用知识库
                </Button>
              )}
              extra={(
                <Button icon={<IconUpload />} onClick={handleOpenUploadModal}>
                  导入文档
                </Button>
              )}
            />

            <KnowledgeLifecycleFlow />

            <KnowledgeOverview overview={overview} />

            <SearchSection>
              <SearchRow>
                <Input
                  placeholder="知识库ID"
                  value={searchText}
                  onChange={setSearchText}
                  style={{ width: 200 }}
                  onEnterPress={handleSearch}
                />
                <Input
                  placeholder="知识库名称"
                  value={searchRagName}
                  onChange={setSearchRagName}
                  style={{ width: 200 }}
                  onEnterPress={handleSearch}
                />
                <Input
                  placeholder="场景标签，如 payment-ops"
                  value={searchKnowledgeTag}
                  onChange={setSearchKnowledgeTag}
                  style={{ width: 200 }}
                  onEnterPress={handleSearch}
                />
                <Select
                  placeholder="选择状态"
                  value={searchStatus}
                  onChange={(value) => setSearchStatus(value as number | undefined)}
                  style={{ width: 120 }}
                >
                  <Option value={1}>启用</Option>
                  <Option value={0}>禁用</Option>
                </Select>
                <Button
                  type="primary"
                  icon={<IconSearch />}
                  onClick={handleSearch}
                >
                  搜索
                </Button>
                <Button
                  icon={<IconRefresh />}
                  onClick={handleReset}
                >
                  重置
                </Button>
                <Button
                  disabled={!selectedKnowledgeId}
                  onClick={() => handleOpenPolicy(selectedKnowledgeId)}
                >
                  编辑策略
                </Button>
                <Button
                  disabled={!selectedKnowledgeBase}
                  loading={knowledgeSaving}
                  onClick={() => selectedKnowledgeBase && handleToggleKnowledgeStatus(selectedKnowledgeBase)}
                >
                  {selectedKnowledgeBase && isKnowledgeEnabled(selectedKnowledgeBase) ? '停用' : '启用'}
                </Button>
                <Button
                  disabled={!selectedKnowledgeId}
                  loading={usageLoading}
                  onClick={() => fetchUsageProjects(selectedKnowledgeId)}
                >
                  查看使用项目
                </Button>
              </SearchRow>
              <HelperText type="tertiary">
                通用知识库不能被 Agent 直接无条件读取。项目需要显式启用通用知识库，Agent 只能读取当前项目授权知识库。
              </HelperText>
            </SearchSection>

            <TableContainer>
              <TableCard>
                <div style={{ padding: theme.spacing.lg, borderBottom: `1px solid ${theme.colors.border.secondary}` }}>
                  <Title heading={5} style={{ margin: 0 }}>知识库列表</Title>
                  <Typography.Text type="tertiary">
                    首屏只展示通用知识库基础信息；结构解析、二次分段、召回、rerank、embedding 和 metadata filter 等参数放到检索策略与高级预览。
                  </Typography.Text>
                </div>
                <TableScroll>
                  <Table
                    columns={columns}
                    dataSource={dataSource}
                    loading={loading}
                    pagination={{
                      currentPage: currentPage,
                      pageSize: pageSize,
                      total: total,
                      showSizeChanger: true,
                      showQuickJumper: true,
                      onChange: handlePageChange
                    }}
                    rowKey="id"
                    scroll={{ x: 980 }}
                    empty={
                      <OpsEmptyState
                        title="暂无通用知识库"
                        description="当前后端没有返回通用知识库记录。可以新建通用知识库或导入文档；页面不会硬编码假知识库。"
                      />
                    }
                  />
                </TableScroll>
              </TableCard>
            </TableContainer>

            <DocumentContainer>
              <TableCard>
                <div style={{ padding: theme.spacing.lg, borderBottom: `1px solid ${theme.colors.border.secondary}` }}>
                  <Space style={{ width: '100%', justifyContent: 'space-between' }}>
                    <div>
                      <Title heading={5} style={{ margin: 0 }}>文档来源</Title>
                      <Typography.Text type="tertiary">
                        按知识标签聚合公共文档来源，展示当前通用知识库的文档数量、片段数量和来源类型。
                      </Typography.Text>
                    </div>
                    <Space>
                      <Button icon={<IconRefresh />} onClick={() => fetchKnowledgeLifecycle()}>
                        刷新统计
                      </Button>
                      <Popconfirm
                        title="确定清理当前标签下的所有结构化片段吗？"
                        content="只删除 PgVector 中 metadata.knowledge 等于当前场景标签的内容。"
                        onConfirm={handleDeleteChunksByTag}
                        okText="确定"
                        cancelText="取消"
                      >
                        <Button type="danger" theme="light" icon={<IconDelete />} disabled={!selectedKnowledgeId}>
                          清理当前标签
                        </Button>
                      </Popconfirm>
                    </Space>
                  </Space>
                </div>
                <OverviewGrid style={{ margin: theme.spacing.lg }}>
                  {(knowledgeBases.length ? knowledgeBases : []).slice(0, 6).map((item) => (
                    <OverviewTile key={item.knowledgeTag}>
                      <OverviewLabel>{item.knowledgeTag}</OverviewLabel>
                      <OverviewValue>{item.chunkCount || 0}</OverviewValue>
                      <Typography.Text type="tertiary">
                        docs: {item.documentCount || 0} / configs: {item.ragOrders?.length || 0}
                      </Typography.Text>
                    </OverviewTile>
                  ))}
                  {!knowledgeBases.length && (
                    <OverviewTile>
                      <OverviewLabel>暂无聚合数据</OverviewLabel>
                      <OverviewValue>-</OverviewValue>
                    </OverviewTile>
                  )}
                </OverviewGrid>
                {documentStats && (
                  <div style={{ padding: `0 ${theme.spacing.lg} ${theme.spacing.lg}` }}>
                    <Space wrap>
                      {(documentStats.byType || []).slice(0, 8).map((item) => (
                        <Tag key={`type-${item.key}`} color="blue">
                          {item.key}: {item.count}
                        </Tag>
                      ))}
                      {(documentStats.bySource || []).slice(0, 8).map((item) => (
                        <Tag key={`source-${item.key}`} color="grey">
                          {item.key}: {item.count}
                        </Tag>
                      ))}
                    </Space>
                  </div>
                )}
              </TableCard>
            </DocumentContainer>

            <DocumentContainer>
              <TableCard>
                <div style={{ padding: theme.spacing.lg, borderBottom: `1px solid ${theme.colors.border.secondary}` }}>
                  <Title heading={5} style={{ margin: 0 }}>使用项目</Title>
                  <Typography.Text type="tertiary">
                    通用知识库需要被项目显式启用后，项目内 Agent 才能读取。当前查看：{selectedKnowledgeId || '请先选择知识库'}。
                  </Typography.Text>
                </div>
                <TableScroll>
                  <Table
                    columns={usageProjectColumns}
                    dataSource={usageProjects}
                    rowKey="projectId"
                    loading={usageLoading}
                    pagination={false}
                    empty={(
                      <OpsEmptyState
                        title={selectedKnowledgeId ? '尚未授权给项目' : '请选择通用知识库'}
                        description={selectedKnowledgeId
                          ? '当前知识库还没有被任何项目显式启用。'
                          : '从知识库列表点击“查看”后，再查看使用项目。'}
                      />
                    )}
                  />
                </TableScroll>
              </TableCard>
            </DocumentContainer>

            <DocumentContainer>
              <TableCard>
                <div style={{ padding: theme.spacing.lg, borderBottom: `1px solid ${theme.colors.border.secondary}` }}>
                  <Space style={{ width: '100%', justifyContent: 'space-between' }} align="start">
                    <div>
                      <Title heading={5} style={{ margin: 0 }}>检索策略</Title>
                      <Typography.Text type="tertiary">
                        这里用于验证 topK、关键词覆盖、rerank 前后效果和 metadata 过滤是否合理；复杂参数默认不铺在首屏。
                      </Typography.Text>
                    </div>
                    <Button type="primary" loading={qualityLoading} onClick={handleProbeQuality}>
                      开始探测
                    </Button>
                  </Space>
                </div>
                <div style={{ padding: theme.spacing.lg }}>
                  <Space vertical align="start" style={{ width: '100%' }}>
                    <TextArea
                      autosize={{ minRows: 3, maxRows: 6 }}
                      placeholder="输入需要验证的检索问题，例如：支付成功但订单状态未更新应该怎么排查？"
                      value={qualityQuery}
                      onChange={setQualityQuery}
                    />
                    <SearchRow>
                      <Input
                        placeholder="期望命中的关键词，逗号分隔"
                        value={qualityExpected}
                        onChange={setQualityExpected}
                        style={{ width: 360 }}
                      />
                      <Select value={qualityTopK} onChange={(value) => setQualityTopK(Number(value) || 8)} style={{ width: 120 }}>
                        <Option value={5}>Top 5</Option>
                        <Option value={8}>Top 8</Option>
                        <Option value={12}>Top 12</Option>
                      </Select>
                      <Button onClick={handleSaveEvalCase}>保存为评测用例</Button>
                      <Button loading={evalLoading} onClick={handleRunEval}>运行离线评测</Button>
                      <Typography.Text type="tertiary">
                        当前知识库：{selectedKnowledgeId || '全部'}
                      </Typography.Text>
                    </SearchRow>
                    {qualityResult && (
                      <Space vertical align="start" style={{ width: '100%' }}>
                        <Space wrap>
                          <Tag color={qualityResult.hitCount > 0 ? 'green' : 'red'}>命中 {qualityResult.hitCount}</Tag>
                          <Tag color={qualityResult.keywordCoverage >= 0.6 ? 'green' : 'orange'}>
                            覆盖率 {(qualityResult.keywordCoverage * 100).toFixed(0)}%
                          </Tag>
                          {!!qualityResult.missingKeywords?.length && (
                            <Tag color="red">缺失 {qualityResult.missingKeywords.join('、')}</Tag>
                          )}
                        </Space>
                        <Typography.Text type="secondary">{qualityResult.recommendation}</Typography.Text>
                        <Space>
                          <Button type="primary" theme="light" onClick={() => handleSubmitRagFeedback(true, true)}>
                            标记有效
                          </Button>
                          <Button type="danger" theme="light" onClick={() => handleSubmitRagFeedback(false, false)}>
                            标记无效并形成缺口
                          </Button>
                        </Space>
                        <Space vertical align="start" style={{ width: '100%' }}>
                          {(qualityResult.hits || []).slice(0, 6).map((hit, index) => (
                            <OverviewTile key={`${hit.chunkId || index}-probe`} style={{ width: '100%' }}>
                              <Space vertical align="start" spacing="tight" style={{ width: '100%' }}>
                                <Typography.Text strong>
                                  {index + 1}. {hit.docName || hit.chunkId || '未命名片段'}
                                </Typography.Text>
                                <Typography.Text type="tertiary">
                                  {hit.knowledgeTag || '-'} / {hit.documentType || '-'} / score={Number(hit.score || 0).toFixed(2)}
                                </Typography.Text>
                                <Typography.Text>{hit.preview || '-'}</Typography.Text>
                              </Space>
                            </OverviewTile>
                          ))}
                        </Space>
                      </Space>
                    )}
                    <OpsAdvancedPreview
                      title="高级预览：评测用例"
                      description="评测用例、关键词和删除操作属于检索策略维护，不作为通用知识库首屏配置。"
                    >
                      <TableScroll>
                        <Table
                          size="small"
                          pagination={{ pageSize: 5 }}
                          dataSource={evalCases}
                          columns={[
                            { title: '用例', dataIndex: 'caseName', width: 220 },
                            { title: '问题', dataIndex: 'query' },
                            { title: '标签', dataIndex: 'knowledgeTag', width: 140 },
                            {
                              title: '关键词',
                              dataIndex: 'expectedKeywords',
                              width: 220,
                              render: (keywords) => (keywords || []).join('、'),
                            },
                            {
                              title: '操作',
                              width: 100,
                              render: (_text, record: RagEvalCase) => (
                                <Popconfirm content="确定删除该评测用例？" onConfirm={() => handleDeleteEvalCase(record.id)}>
                                  <Button type="danger" theme="borderless" icon={<IconDelete />} />
                                </Popconfirm>
                              ),
                            },
                          ]}
                        />
                      </TableScroll>
                    </OpsAdvancedPreview>
                    {evalRunResult && (
                      <Space wrap>
                        <Tag color="blue">用例 {evalRunResult.caseCount}</Tag>
                        <Tag color={evalRunResult.hitRate >= 0.8 ? 'green' : 'orange'}>
                          HitRate {(evalRunResult.hitRate * 100).toFixed(0)}%
                        </Tag>
                        <Tag color={evalRunResult.averageKeywordCoverage >= 0.6 ? 'green' : 'orange'}>
                          Coverage {(evalRunResult.averageKeywordCoverage * 100).toFixed(0)}%
                        </Tag>
                        <Tag color={evalRunResult.mrr >= 0.5 ? 'green' : 'orange'}>
                          MRR {evalRunResult.mrr.toFixed(2)}
                        </Tag>
                      </Space>
                    )}
                    <div style={{ width: '100%', border: `1px solid ${theme.colors.border.secondary}`, borderRadius: theme.borderRadius.base, padding: theme.spacing.base }}>
                      <Space style={{ width: '100%', justifyContent: 'space-between' }}>
                        <div>
                          <Title heading={6} style={{ margin: 0 }}>线上反馈与知识缺口</Title>
                          <Typography.Text type="tertiary">
                            无效反馈会聚合成知识缺口，可转成评测用例，后续补文档或调整结构解析策略后再关闭。
                          </Typography.Text>
                        </div>
                        <Button icon={<IconRefresh />} loading={feedbackLoading} onClick={() => fetchRagFeedback()}>
                          刷新反馈
                        </Button>
                      </Space>
                      <OpsAdvancedPreview title="高级预览：线上反馈与知识缺口">
                        <TableScroll>
                          <Table
                            style={{ marginTop: theme.spacing.base }}
                            size="small"
                            loading={feedbackLoading}
                            pagination={{ pageSize: 5 }}
                            dataSource={ragGaps}
                            rowKey="id"
                            columns={[
                              { title: '缺口问题', dataIndex: 'queryText' },
                              { title: '标签', dataIndex: 'knowledgeTag', width: 140 },
                              {
                                title: '状态',
                                dataIndex: 'status',
                                width: 110,
                                render: (value: string) => <Tag color={value === 'FIXED' ? 'green' : value === 'IGNORED' ? 'grey' : 'orange'}>{value}</Tag>,
                              },
                              { title: '反馈次数', dataIndex: 'feedbackCount', width: 100 },
                              { title: '最近反馈', dataIndex: 'lastFeedbackAt', width: 170 },
                              {
                                title: '操作',
                                width: 260,
                                render: (_text, record: RagKnowledgeGap) => (
                                  <Space>
                                    <Button size="small" onClick={() => handleGapToEvalCase(record.id)}>转评测</Button>
                                    <Button size="small" onClick={() => handleUpdateGapStatus(record.id, 'FIXED')}>标记已修复</Button>
                                    <Button size="small" theme="borderless" onClick={() => handleUpdateGapStatus(record.id, 'IGNORED')}>忽略</Button>
                                  </Space>
                                ),
                              },
                            ]}
                          />
                        </TableScroll>
                        <TableScroll>
                          <Table
                            style={{ marginTop: theme.spacing.base }}
                            size="small"
                            loading={feedbackLoading}
                            pagination={{ pageSize: 5 }}
                            dataSource={ragFeedbacks}
                            rowKey="id"
                            columns={[
                              { title: '反馈问题', dataIndex: 'queryText' },
                              { title: '标签', dataIndex: 'knowledgeTag', width: 140 },
                              {
                                title: '有用',
                                dataIndex: 'useful',
                                width: 90,
                                render: (value: number) => <Tag color={Number(value) === 1 ? 'green' : 'red'}>{Number(value) === 1 ? '是' : '否'}</Tag>,
                              },
                              {
                                title: '解决',
                                dataIndex: 'resolved',
                                width: 90,
                                render: (value: number) => <Tag color={Number(value) === 1 ? 'green' : 'orange'}>{Number(value) === 1 ? '是' : '否'}</Tag>,
                              },
                              { title: '说明', dataIndex: 'commentText' },
                              { title: '时间', dataIndex: 'createTime', width: 170 },
                            ]}
                          />
                        </TableScroll>
                      </OpsAdvancedPreview>
                    </div>
                  </Space>
                </div>
              </TableCard>
            </DocumentContainer>

            <DocumentContainer>
              <TableCard>
                <div style={{ padding: theme.spacing.lg, borderBottom: `1px solid ${theme.colors.border.secondary}` }}>
                  <Space style={{ width: '100%', justifyContent: 'space-between' }}>
                    <div>
                      <Title heading={5} style={{ margin: 0 }}>结构化解析与分段</Title>
                      <Typography.Text type="tertiary">
                        入库先识别文档类型和语义边界，保留 Markdown 标题层级、代码块、图片引用、PDF 页码、段落、图注和表格行范围；只有单个结构块过长时才按长度二次分段。
                      </Typography.Text>
                    </div>
                    <Button icon={<IconRefresh />} loading={jobLoading} onClick={() => fetchIngestionJobs()}>
                      刷新任务
                    </Button>
                  </Space>
                </div>
                <StructureFlow>
                  <StructureStep>
                    <Typography.Text strong>1. 类型识别</Typography.Text>
                    <HelperText type="tertiary">识别 Markdown、PDF、表格、代码、对话、图片或普通文本。</HelperText>
                  </StructureStep>
                  <StructureStep>
                    <Typography.Text strong>2. 结构保留</Typography.Text>
                    <HelperText type="tertiary">按标题、段落、页码、符号、表格行和对话轮次形成语义片段。</HelperText>
                  </StructureStep>
                  <StructureStep>
                    <Typography.Text strong>3. 超长块二次分段</Typography.Text>
                    <HelperText type="tertiary">仅对仍超限的不可分结构块应用长度上限与可选重叠。</HelperText>
                  </StructureStep>
                  <StructureStep>
                    <Typography.Text strong>4. 向量化与入库</Typography.Text>
                    <HelperText type="tertiary">文本或多模态 embedding 与结构 metadata 一并写入 PgVector。</HelperText>
                  </StructureStep>
                </StructureFlow>
                <TableScroll>
                  <Table
                    columns={jobColumns}
                    dataSource={ingestionJobs}
                    loading={jobLoading}
                    rowKey="jobId"
                    pagination={false}
                    scroll={{ x: 1200 }}
                    empty={<OpsEmptyState title="暂无入库任务" description="导入文档后会看到结构解析、向量化和入库任务。" />}
                  />
                </TableScroll>
              </TableCard>
            </DocumentContainer>

            <DocumentContainer>
              <TableCard>
                <div style={{ padding: theme.spacing.lg, borderBottom: `1px solid ${theme.colors.border.secondary}` }}>
                  <Space style={{ width: '100%', justifyContent: 'space-between' }}>
                    <div>
                      <Title heading={5} style={{ margin: 0 }}>高级预览</Title>
                      <Typography.Text type="tertiary">
                        这里展示已入库片段、结构 metadata 和必要的二次分段结果，只用于排查解析效果，不作为普通配置入口。
                      </Typography.Text>
                    </div>
                    <Button icon={<IconRefresh />} disabled={!selectedKnowledgeId} onClick={() => fetchDocumentList()}>刷新片段</Button>
                  </Space>
                </div>
                <OpsAdvancedPreview
                  title="高级预览：已入库片段"
                  description="片段内容、片段 ID、结构路径和二次分段 metadata 默认折叠，避免撑开页面。"
                >
                  <TableScroll>
                    <Table
                      columns={documentColumns}
                      dataSource={documents}
                      loading={documentLoading}
                      rowKey="fileName"
                      pagination={false}
                      scroll={{ x: 1050 }}
                      empty={selectedKnowledgeId
                        ? <OpsEmptyState title="暂无入库片段" description="导入文档并完成解析后，会在这里看到片段。" />
                        : <OpsEmptyState title="请先选择知识库" description="选择一个通用知识库后，再查看已入库片段。" />}
                    />
                  </TableScroll>
                </OpsAdvancedPreview>
              </TableCard>
            </DocumentContainer>

          </RagOrderManagementContainer>

          <Modal
            title={editingKnowledgeId ? '编辑通用知识库' : '新建通用知识库'}
            visible={knowledgeModalVisible}
            width={640}
            confirmLoading={knowledgeSaving}
            okText={editingKnowledgeId ? '保存修改' : '创建知识库'}
            cancelText="取消"
            onOk={handleSaveKnowledge}
            onCancel={() => {
              if (knowledgeSaving) {
                return;
              }
              setKnowledgeModalVisible(false);
              setEditingKnowledgeId('');
              setKnowledgeDraft({ kbId: '', kbName: '', description: '' });
            }}
          >
            <Space vertical align="start" spacing="medium" style={{ width: '100%' }}>
              {editingKnowledgeId && (
                <div style={{ width: '100%' }}>
                  <PolicyFieldLabel>系统编号</PolicyFieldLabel>
                  <Input value={knowledgeDraft.kbId} disabled />
                  <HelperText type="tertiary">系统编号由平台生成，用于文档、策略和项目授权关联。</HelperText>
                </div>
              )}
              <div style={{ width: '100%' }}>
                <PolicyFieldLabel>知识库名称</PolicyFieldLabel>
                <Input
                  value={knowledgeDraft.kbName}
                  placeholder="例如 公共运维 SOP"
                  onChange={(kbName) => setKnowledgeDraft((current) => ({ ...current, kbName }))}
                />
              </div>
              <div style={{ width: '100%' }}>
                <PolicyFieldLabel>说明</PolicyFieldLabel>
                <TextArea
                  value={knowledgeDraft.description}
                  placeholder="说明知识范围、维护责任和适用边界"
                  autosize={{ minRows: 4, maxRows: 8 }}
                  onChange={(description) => setKnowledgeDraft((current) => ({ ...current, description }))}
                />
              </div>
            </Space>
          </Modal>

          {/* 上传文档弹窗 */}
          <Modal
            title="导入通用知识文档"
            visible={uploadModalVisible}
            onOk={handleUploadSubmit}
            onCancel={handleCloseUploadModal}
            confirmLoading={uploadLoading}
            width={600}
            okText="确认上传"
            cancelText="取消"
          >
            <Form
              getFormApi={(api) => setUploadFormApi(api)}
              labelPosition="left"
              labelWidth={100}
              style={{ padding: '20px 0' }}
            >
              <Form.Input
                field="name"
                label="文档名称"
                placeholder="例如：支付系统运维手册"
                rules={[
                  { required: true, message: '请输入知识库名称' },
                  { min: 2, message: '知识库名称至少2个字符' },
                  { max: 50, message: '知识库名称不能超过50个字符' }
                ]}
              />
              <Form.Slot label="目标知识库">
                <Select
                  value={uploadKnowledgeId}
                  style={{ width: '100%' }}
                  placeholder="选择文档要进入的知识库"
                  onChange={(value) => setUploadKnowledgeId(String(value || ''))}
                >
                  {knowledgeBases.map((knowledge) => (
                    <Option key={knowledgeIdOf(knowledge)} value={knowledgeIdOf(knowledge)}>
                      {knowledgeNameOf(knowledge)}
                    </Option>
                  ))}
                </Select>
                <HelperText type="tertiary">文档会归入所选知识库；系统编号不需要手写。</HelperText>
              </Form.Slot>
              <Form.Slot label="上传文件">
                <Upload
                  action=""
                  beforeUpload={beforeUpload}
                  onChange={handleFileChange}
                  fileList={fileList}
                  accept={ACCEPTED_KNOWLEDGE_FILE_TYPES}
                  multiple
                  limit={8}
                  uploadTrigger="custom"
                  showUploadList={true}
                >
                  <Button icon={<IconPlus />} theme="light">
                    选择文件
                  </Button>
                </Upload>
                <HelperText type="tertiary">
                  当前仅支持 Markdown 和 PDF；最多 8 个文件，单文件不超过 20MB；同一批文件会进入同一个知识库名称/标签。
                </HelperText>
              </Form.Slot>
            </Form>
          </Modal>

          <Modal
            title={`结构化解析与检索策略${policyKbId ? ` · ${policyKbId}` : ''}`}
            visible={policyModalVisible}
            onOk={handleSavePolicy}
            onCancel={() => {
              setPolicyModalVisible(false);
              setRetrievalPolicy(null);
            }}
            confirmLoading={policySaving}
            width={760}
            okText="保存策略"
            cancelText="取消"
          >
            {policyLoading || !retrievalPolicy ? (
              <Typography.Text type="tertiary">正在读取真实策略...</Typography.Text>
            ) : (
              <Space vertical align="start" spacing="loose" style={{ width: '100%' }}>
                <OpsSectionCard>
                  <Title heading={6} style={{ marginTop: 0 }}>结构优先，不是固定窗口切片</Title>
                  <Typography.Text type="tertiary">
                    解析器先保留标题、段落、页码、表格、代码符号和对话轮次。长度参数只约束超长结构单元，不会替代结构识别。
                  </Typography.Text>
                  <PolicyFormGrid style={{ marginTop: theme.spacing.lg }}>
                    <PolicyField>
                      <PolicyFieldLabel>分段模式</PolicyFieldLabel>
                      <Input value="STRUCTURE_FIRST（结构优先）" disabled style={{ width: '100%' }} />
                    </PolicyField>
                    <PolicyField>
                      <PolicyFieldLabel>结构单元最大长度</PolicyFieldLabel>
                      <InputNumber
                        value={retrievalPolicy.maxSegmentChars ?? 3000}
                        min={1000}
                        max={12000}
                        step={500}
                        onChange={(value) => updatePolicyField('maxSegmentChars', Number(value))}
                        style={{ width: '100%' }}
                      />
                      <HelperText type="tertiary">仅在完整结构单元超过该值时触发二次分段。</HelperText>
                    </PolicyField>
                    <PolicyField>
                      <PolicyFieldLabel>超长不可分块重叠字符</PolicyFieldLabel>
                      <InputNumber
                        value={retrievalPolicy.hardSplitOverlapChars ?? 0}
                        min={0}
                        max={Math.floor((retrievalPolicy.maxSegmentChars ?? 3000) / 2)}
                        step={50}
                        onChange={(value) => updatePolicyField('hardSplitOverlapChars', Number(value))}
                        style={{ width: '100%' }}
                      />
                      <HelperText type="tertiary">默认 0；只用于单个不可再拆的超长块，不对正常标题段落重复内容。</HelperText>
                    </PolicyField>
                    <PolicyField>
                      <PolicyFieldLabel>召回 Top K</PolicyFieldLabel>
                      <InputNumber
                        value={retrievalPolicy.topK ?? 5}
                        min={1}
                        max={50}
                        onChange={(value) => updatePolicyField('topK', Number(value))}
                        style={{ width: '100%' }}
                      />
                    </PolicyField>
                    <PolicyField>
                      <PolicyFieldLabel>启用专用 Reranker</PolicyFieldLabel>
                      <Switch
                        checked={Boolean(retrievalPolicy.rerankEnabled)}
                        onChange={(checked) => updatePolicyField('rerankEnabled', checked)}
                      />
                    </PolicyField>
                    <PolicyField>
                      <PolicyFieldLabel>Embedding 模型 ID</PolicyFieldLabel>
                      <Input
                        value={retrievalPolicy.embeddingModelId || ''}
                        placeholder="留空时使用平台默认 Embedding 模型"
                        onChange={(value) => updatePolicyField('embeddingModelId', value)}
                      />
                    </PolicyField>
                  </PolicyFormGrid>
                </OpsSectionCard>
                <OpsAdvancedPreview
                  title="高级预览：Metadata Filter"
                  description="只填写真实检索过滤 JSON；保存前会校验 JSON 格式。"
                >
                  <TextArea
                    value={retrievalPolicy.metadataFilterJson || '{}'}
                    autosize={{ minRows: 4, maxRows: 10 }}
                    onChange={(value) => updatePolicyField('metadataFilterJson', value)}
                  />
                </OpsAdvancedPreview>
              </Space>
            )}
          </Modal>

          <Modal
            title={selectedDocument?.displayName || '文档内容'}
            visible={documentModalVisible}
            onCancel={() => setDocumentModalVisible(false)}
            footer={null}
            width={860}
          >
            <OpsAdvancedPreview
              title="高级预览：文档原文"
              description="文档原文只用于检查结构解析与分段结果，默认折叠显示。"
            >
              <DocumentContent>{selectedDocument?.content || '暂无内容'}</DocumentContent>
            </OpsAdvancedPreview>
          </Modal>
    </OpsPageShell>
  );
};
