import React, { useMemo, useState } from 'react';
import { Button, Card, Checkbox, Input, Modal, Space, Table, Tabs, TabPane, Tag, TextArea, Toast, Typography } from '@douyinfe/semi-ui';
import { IconEdit, IconPlus, IconRefresh, IconUpload } from '@douyinfe/semi-icons';
import styled from 'styled-components';

import { OpsPageHeader, OpsPageShell } from '../components/ops-layout';
import {
  useGlobalKnowledgeBasesQuery,
  useKnowledgeChunksQuery,
  useKnowledgeIngestionJobsQuery,
  useKnowledgeStatsQuery,
  useKnowledgeUsageProjectsQuery,
  useLoadKnowledgePolicyMutation,
  useProbeRagQualityMutation,
  useSaveGlobalKnowledgeMutation,
  useToggleGlobalKnowledgeMutation,
  useUpdateKnowledgePolicyMutation,
  useUploadKnowledgeDocumentsMutation,
} from '../features/knowledge/api/knowledge-queries';
import type {
  RagKnowledgeBaseSummary,
  RagKnowledgeDocumentRecord,
  RagKnowledgeRetrievalPolicy,
  RagQualityProbeResult,
} from '../services/ai-client-rag-order-admin-service';
import { theme } from '../styles/theme';
import { userFacingError } from '../utils/user-facing-error';

const { Paragraph, Text, Title } = Typography;

const Stack = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${theme.spacing.base};
  min-width: 0;
`;

const Grid = styled.div`
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: ${theme.spacing.base};
  @media (max-width: 960px) { grid-template-columns: 1fr; }
`;

const FormGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: ${theme.spacing.base};
  @media (max-width: 760px) { grid-template-columns: 1fr; }
`;

const Field = styled.div`
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
`;

const WideField = styled(Field)`grid-column: 1 / -1;`;

const kbIdOf = (kb?: RagKnowledgeBaseSummary | null) => String(kb?.kbId || kb?.knowledgeTag || kb?.ragId || '').trim();
const kbNameOf = (kb?: RagKnowledgeBaseSummary | null) => String(kb?.kbName || kb?.ragName || kb?.name || kbIdOf(kb)).trim();
const enabled = (kb?: RagKnowledgeBaseSummary | null) => kb?.status === 1 || kb?.statusCode === 1 || String(kb?.status || '').toUpperCase() === 'ENABLED';

const defaultPolicy = (): RagKnowledgeRetrievalPolicy => ({
  segmentationMode: 'STRUCTURE_FIRST',
  structurePreserved: true,
  maxSegmentChars: 3000,
  hardSplitOverlapChars: 0,
  topK: 5,
  rerankEnabled: false,
  embeddingModelId: '',
  metadataFilterJson: '{}',
});

export const KnowledgePage: React.FC = () => {
  const basesQuery = useGlobalKnowledgeBasesQuery();
  const jobsQuery = useKnowledgeIngestionJobsQuery();
  const saveMutation = useSaveGlobalKnowledgeMutation();
  const toggleMutation = useToggleGlobalKnowledgeMutation();
  const uploadMutation = useUploadKnowledgeDocumentsMutation();
  const loadPolicyMutation = useLoadKnowledgePolicyMutation();
  const savePolicyMutation = useUpdateKnowledgePolicyMutation();
  const qualityMutation = useProbeRagQualityMutation();

  const bases = basesQuery.data || [];
  const [keyword, setKeyword] = useState('');
  const [selectedId, setSelectedId] = useState('');
  const selected = bases.find((kb) => kbIdOf(kb) === selectedId) || null;
  const kbId = kbIdOf(selected);
  const statsQuery = useKnowledgeStatsQuery(kbId);
  const chunksQuery = useKnowledgeChunksQuery(kbId);
  const usageQuery = useKnowledgeUsageProjectsQuery(kbId);

  const [formVisible, setFormVisible] = useState(false);
  const [editing, setEditing] = useState<RagKnowledgeBaseSummary | null>(null);
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [uploadVisible, setUploadVisible] = useState(false);
  const [files, setFiles] = useState<File[]>([]);
  const [policyVisible, setPolicyVisible] = useState(false);
  const [policy, setPolicy] = useState<RagKnowledgeRetrievalPolicy>(defaultPolicy());
  const [qualityQuery, setQualityQuery] = useState('');
  const [qualityKeywords, setQualityKeywords] = useState('');

  const filtered = useMemo(() => {
    const query = keyword.trim().toLowerCase();
    if (!query) return bases;
    return bases.filter((kb) => [kbIdOf(kb), kbNameOf(kb), kb.description]
      .some((value) => String(value || '').toLowerCase().includes(query)));
  }, [bases, keyword]);

  const openCreate = () => {
    setEditing(null);
    setName('');
    setDescription('');
    setFormVisible(true);
  };

  const openEdit = (kb: RagKnowledgeBaseSummary) => {
    setEditing(kb);
    setName(kbNameOf(kb));
    setDescription(kb.description || '');
    setFormVisible(true);
  };

  const saveKb = async () => {
    if (!name.trim()) {
      Toast.warning('请输入知识库名称。');
      return;
    }
    try {
      const payload: Partial<RagKnowledgeBaseSummary> = {
        kbName: name.trim(),
        name: name.trim(),
        description: description.trim(),
        scope: 'GLOBAL',
        status: editing ? editing.status : 'ENABLED',
      };
      const saved = await saveMutation.mutateAsync({ editingKbId: editing ? kbIdOf(editing) : undefined, payload });
      Toast.success(editing ? '知识库已更新。' : '知识库已创建。');
      setFormVisible(false);
      const nextId = kbIdOf(saved || editing);
      if (nextId) setSelectedId(nextId);
    } catch (error) {
      Toast.error(userFacingError(error, '保存知识库失败，请稍后重试。'));
    }
  };

  const toggleKb = async (kb: RagKnowledgeBaseSummary) => {
    try {
      await toggleMutation.mutateAsync({ kbId: kbIdOf(kb), status: enabled(kb) ? 'DISABLED' : 'ENABLED' });
      Toast.success(enabled(kb) ? '知识库已停用。' : '知识库已启用。');
    } catch (error) {
      Toast.error(userFacingError(error, '更新知识库状态失败，请稍后重试。'));
    }
  };

  const upload = async () => {
    if (!kbId || files.length === 0) {
      Toast.warning('请至少选择一个 Markdown 或 PDF 文件。');
      return;
    }
    const invalid = files.find((file) => !/\.(md|markdown|pdf)$/i.test(file.name) || file.size > 20 * 1024 * 1024);
    if (invalid) {
      Toast.error(`${invalid.name}：仅支持不超过 20MB 的 Markdown/PDF 文件。`);
      return;
    }
    if (files.length > 8) {
      Toast.error('每批最多上传 8 个文件。');
      return;
    }
    try {
      await uploadMutation.mutateAsync({ kbId, name: kbNameOf(selected), files });
      Toast.success('文档已进入入库队列。');
      setFiles([]);
      setUploadVisible(false);
    } catch (error) {
      Toast.error(userFacingError(error, '上传文档失败，请稍后重试。'));
    }
  };

  const openPolicy = async () => {
    if (!kbId) return;
    setPolicyVisible(true);
    try {
      const loaded = await loadPolicyMutation.mutateAsync(kbId);
      setPolicy({ ...defaultPolicy(), ...(loaded || {}) });
    } catch (error) {
      Toast.error(userFacingError(error, '加载检索策略失败，请稍后重试。'));
    }
  };

  const savePolicy = async () => {
    if (!kbId) return;
    const maxSegmentChars = Number(policy.maxSegmentChars ?? policy.chunkSize ?? 3000);
    const hardSplitOverlapChars = Number(policy.hardSplitOverlapChars ?? policy.overlapSize ?? 0);
    const topK = Number(policy.topK ?? 5);
    if (maxSegmentChars < 1000 || maxSegmentChars > 12000) {
      Toast.error('最大分段长度必须在 1,000 到 12,000 字符之间。');
      return;
    }
    if (hardSplitOverlapChars < 0 || hardSplitOverlapChars > Math.floor(maxSegmentChars / 2)) {
      Toast.error('强制切分重叠长度不能超过最大分段长度的一半。');
      return;
    }
    if (topK < 1 || topK > 50) {
      Toast.error('Top K 必须在 1 到 50 之间。');
      return;
    }
    try {
      JSON.parse(policy.metadataFilterJson || '{}');
    } catch {
      Toast.error('元数据过滤条件必须是有效 JSON。');
      return;
    }
    try {
      await savePolicyMutation.mutateAsync({
        kbId,
        payload: {
          maxSegmentChars,
          hardSplitOverlapChars,
          topK,
          rerankEnabled: Boolean(policy.rerankEnabled),
          embeddingModelId: policy.embeddingModelId || '',
          metadataFilterJson: policy.metadataFilterJson || '{}',
        },
      });
      Toast.success('检索策略已保存。');
      setPolicyVisible(false);
    } catch (error) {
      Toast.error(userFacingError(error, '保存检索策略失败，请稍后重试。'));
    }
  };

  const runQualityProbe = async () => {
    if (!qualityQuery.trim()) {
      Toast.warning('请输入测试查询。');
      return;
    }
    try {
      await qualityMutation.mutateAsync({
        query: qualityQuery.trim(),
        knowledgeTag: kbId || undefined,
        expectedKeywords: qualityKeywords.split(',').map((item) => item.trim()).filter(Boolean),
        topK: Number(policy.topK || 5),
      });
    } catch (error) {
      Toast.error(userFacingError(error, '执行检索质量探针失败，请稍后重试。'));
    }
  };

  const baseColumns = [
    {
      title: '知识库',
      width: 280,
      render: (_: unknown, kb: RagKnowledgeBaseSummary) => (
        <div>
          <Text strong>{kbNameOf(kb)}</Text>
          <Text type="tertiary" size="small" style={{ display: 'block' }}>{kbIdOf(kb)}</Text>
        </div>
      ),
    },
    { title: '文档', dataIndex: 'documentCount', width: 100 },
    { title: 'Chunk', dataIndex: 'chunkCount', width: 100 },
    { title: 'Project', width: 100, render: (_: unknown, kb: RagKnowledgeBaseSummary) => kb.usedProjectCount ?? kb.projectCount ?? 0 },
    { title: '状态', width: 110, render: (_: unknown, kb: RagKnowledgeBaseSummary) => <Tag color={enabled(kb) ? 'green' : 'grey'}>{enabled(kb) ? '已启用' : '已停用'}</Tag> },
    {
      title: '操作',
      width: 250,
      render: (_: unknown, kb: RagKnowledgeBaseSummary) => (
        <Space>
          <Button size="small" onClick={() => setSelectedId(kbIdOf(kb))}>打开</Button>
          <Button size="small" icon={<IconEdit />} onClick={() => openEdit(kb)}>编辑</Button>
          <Button size="small" type={enabled(kb) ? 'danger' : 'primary'} theme="borderless" onClick={() => void toggleKb(kb)}>{enabled(kb) ? '停用' : '启用'}</Button>
        </Space>
      ),
    },
  ];

  const stats = statsQuery.data;
  const jobs = jobsQuery.data || [];
  const quality = qualityMutation.data as RagQualityProbeResult | undefined;

  return (
    <OpsPageShell selectedKey="settings">
      <OpsPageHeader
        title="知识库"
        description="统一管理可复用的知识库。知识库接入平台后，由具体项目决定是否使用；只有当前项目可用的知识库，Chat 和工作流才会进行检索。"
        extra={(
          <Space>
            <Button icon={<IconRefresh />} onClick={() => { void basesQuery.refetch(); void jobsQuery.refetch(); }}>刷新</Button>
            <Button type="primary" icon={<IconPlus />} onClick={openCreate}>新建知识库</Button>
          </Space>
        )}
      />

      <Stack>
        <Card>
          <Paragraph type="tertiary" style={{ margin: 0 }}>
            这里负责知识库本身的创建、入库和维护。项目是否使用某个知识库，由项目自己的能力配置决定；没有加入当前项目的知识库不会出现在它的 Chat 或工作流中。
          </Paragraph>
        </Card>

        <Card>
          <Input placeholder="搜索知识库" value={keyword} onChange={setKeyword} style={{ maxWidth: 360 }} />
        </Card>

        <Card title="知识库">
          <Table rowKey={(record?: RagKnowledgeBaseSummary) => kbIdOf(record)} columns={baseColumns} dataSource={filtered} loading={basesQuery.isLoading} pagination={false} empty={<Text type="tertiary">暂无知识库。</Text>} />
        </Card>

        {selected && (
          <Card>
            <Space vertical align="start" spacing="medium" style={{ width: '100%' }}>
              <Space wrap style={{ justifyContent: 'space-between', width: '100%' }}>
                <div>
                  <Title heading={5} style={{ margin: 0 }}>{kbNameOf(selected)}</Title>
                  <Text type="tertiary">{kbId}</Text>
                </div>
                <Space>
                  <Button icon={<IconUpload />} onClick={() => setUploadVisible(true)}>导入文档</Button>
                  <Button onClick={() => void openPolicy()}>检索策略</Button>
                </Space>
              </Space>

              <Grid>
                <Card><Text type="tertiary">文档</Text><Title heading={4}>{stats?.documentCount ?? selected.documentCount ?? 0}</Title></Card>
                <Card><Text type="tertiary">Chunk</Text><Title heading={4}>{stats?.chunkCount ?? selected.chunkCount ?? 0}</Title></Card>
                <Card><Text type="tertiary">已授权 Project</Text><Title heading={4}>{usageQuery.data?.length ?? selected.usedProjectCount ?? 0}</Title></Card>
              </Grid>

              <Tabs type="line">
                <TabPane tab="Chunk" itemKey="chunks">
                  <Table
                    rowKey={(record?: RagKnowledgeDocumentRecord) => String(record?.chunkId || record?.documentId || record?.fileName || '')}
                    dataSource={chunksQuery.data || []}
                    loading={chunksQuery.isFetching}
                    pagination={{ pageSize: 10 }}
                    empty={<Text type="tertiary">暂无已入库 Chunk。</Text>}
                    columns={[
                      { title: '文档', width: 220, render: (_: unknown, record: RagKnowledgeDocumentRecord) => record.displayName || record.fileName || record.source || '-' },
                      { title: '类型', dataIndex: 'documentType', width: 120 },
                      { title: 'Chunk', dataIndex: 'chunkIndex', width: 90 },
                      { title: '解析', dataIndex: 'parseStatus', width: 110 },
                      { title: 'Embedding', dataIndex: 'embeddingStatus', width: 110 },
                      { title: '更新时间', dataIndex: 'updateTime', width: 170 },
                    ]}
                  />
                </TabPane>
                <TabPane tab="Project 授权" itemKey="usage">
                  <Table
                    rowKey="projectId"
                    dataSource={usageQuery.data || []}
                    loading={usageQuery.isFetching}
                    pagination={false}
                    empty={<Text type="tertiary">暂无 Project 授权使用该知识库。</Text>}
                    columns={[
                      { title: 'Project', width: 220, render: (_: unknown, record: any) => record.projectName || record.projectId },
                      { title: '状态', dataIndex: 'status', width: 120 },
                      { title: '授权人', dataIndex: 'enabledBy', width: 160 },
                      { title: '更新时间', dataIndex: 'updateTime', width: 170 },
                    ]}
                  />
                </TabPane>
                <TabPane tab="入库任务" itemKey="jobs">
                  <Table
                    rowKey="jobId"
                    dataSource={jobs.filter((job) => job.tag === kbId || job.name === kbNameOf(selected))}
                    pagination={false}
                    empty={<Text type="tertiary">当前知识库暂无入库任务记录。</Text>}
                    columns={[
                      { title: '任务', dataIndex: 'jobId', width: 180 },
                      { title: '状态', dataIndex: 'status', width: 120 },
                      { title: '文件数', width: 100, render: (_: unknown, record: any) => record.fileNames?.length || 0 },
                      { title: '错误', dataIndex: 'errorMessage', width: 260 },
                      { title: '更新时间', dataIndex: 'updatedAt', width: 170 },
                    ]}
                  />
                </TabPane>
                <TabPane tab="质量探针" itemKey="quality">
                  <Stack>
                    <Input placeholder="输入检索测试问题" value={qualityQuery} onChange={setQualityQuery} />
                    <Input placeholder="期望关键词，使用英文逗号分隔" value={qualityKeywords} onChange={setQualityKeywords} />
                    <Button type="primary" loading={qualityMutation.isPending} onClick={() => void runQualityProbe()}>运行质量探针</Button>
                    {quality && (
                      <Grid>
                        <Card><Text type="tertiary">命中数</Text><Title heading={4}>{quality.hitCount}</Title></Card>
                        <Card><Text type="tertiary">关键词覆盖率</Text><Title heading={4}>{Math.round((quality.keywordCoverage || 0) * 100)}%</Title></Card>
                        <Card><Text type="tertiary">Top K</Text><Title heading={4}>{quality.topK}</Title></Card>
                      </Grid>
                    )}
                    {quality?.recommendation && <Text>{quality.recommendation}</Text>}
                  </Stack>
                </TabPane>
              </Tabs>
            </Space>
          </Card>
        )}
      </Stack>

      <Modal title={editing ? '编辑知识库' : '新建知识库'} visible={formVisible} onCancel={() => setFormVisible(false)} onOk={() => void saveKb()} confirmLoading={saveMutation.isPending} okText={editing ? '保存修改' : '创建'} cancelText="取消">
        <Stack>
          <Field><Text strong>名称</Text><Input value={name} onChange={setName} /></Field>
          <Field><Text strong>说明</Text><TextArea value={description} onChange={setDescription} autosize={{ minRows: 3, maxRows: 6 }} /></Field>
        </Stack>
      </Modal>

      <Modal title="导入文档" visible={uploadVisible} onCancel={() => setUploadVisible(false)} onOk={() => void upload()} confirmLoading={uploadMutation.isPending} okText="开始入库" cancelText="取消" width={700}>
        <Stack>
          <Paragraph type="tertiary">仅支持 Markdown 和 PDF。每批最多 8 个文件，单文件最大 20MB。入库时优先保留文档结构，仅对过长的结构单元执行强制切分。</Paragraph>
          <input type="file" multiple accept=".md,.markdown,.pdf" onChange={(event) => setFiles(Array.from(event.target.files || []))} />
          <Space wrap>{files.map((file) => <Tag key={`${file.name}-${file.size}`}>{file.name}</Tag>)}</Space>
        </Stack>
      </Modal>

      <Modal title="检索策略" visible={policyVisible} onCancel={() => setPolicyVisible(false)} onOk={() => void savePolicy()} confirmLoading={savePolicyMutation.isPending || loadPolicyMutation.isPending} okText="保存策略" cancelText="取消" width={760}>
        <FormGrid>
          <Field><Text strong>最大分段字符数</Text><Input value={String(policy.maxSegmentChars ?? policy.chunkSize ?? 3000)} onChange={(value) => setPolicy({ ...policy, maxSegmentChars: Number(value) })} /></Field>
          <Field><Text strong>强制切分重叠长度</Text><Input value={String(policy.hardSplitOverlapChars ?? policy.overlapSize ?? 0)} onChange={(value) => setPolicy({ ...policy, hardSplitOverlapChars: Number(value) })} /></Field>
          <Field><Text strong>Top K</Text><Input value={String(policy.topK ?? 5)} onChange={(value) => setPolicy({ ...policy, topK: Number(value) })} /></Field>
          <Field><Text strong>Embedding 模型 ID</Text><Input value={policy.embeddingModelId || ''} onChange={(embeddingModelId) => setPolicy({ ...policy, embeddingModelId })} /></Field>
          <Field><Text strong>Rerank</Text><Checkbox checked={Boolean(policy.rerankEnabled)} onChange={(event) => setPolicy({ ...policy, rerankEnabled: Boolean(event.target.checked) })}>启用 Rerank</Checkbox></Field>
          <WideField><Text strong>元数据过滤 JSON</Text><TextArea value={policy.metadataFilterJson || '{}'} onChange={(metadataFilterJson) => setPolicy({ ...policy, metadataFilterJson })} autosize={{ minRows: 5, maxRows: 10 }} /></WideField>
        </FormGrid>
      </Modal>
    </OpsPageShell>
  );
};
