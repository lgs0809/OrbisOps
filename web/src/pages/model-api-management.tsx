import React, { useMemo, useState } from 'react';
import styled from 'styled-components';
import {
  Button,
  Input,
  Modal,
  Select,
  Space,
  Table,
  Tag,
  Toast,
  Typography,
} from '@douyinfe/semi-ui';
import { IconEdit, IconPlus, IconRefresh, IconSearch } from '@douyinfe/semi-icons';

import {
  JsonBlock,
  OpsAdvancedPreview,
  OpsActionBar,
  OpsActionGroup,
  OpsEmptyState,
  OpsPageHeader,
  OpsPageShell,
  OpsResponsiveGrid,
  OpsSectionCard,
  OpsStatusCard,
  OpsTwoColumnGrid,
  TableScroll,
} from '../components/ops-layout';
import { AiClientApiCreateModal } from '../components/ai-client-api-create-modal';
import { AiClientApiEditModal } from '../components/ai-client-api-edit-modal';
import type { AiClientApiResponseDTO } from '../services/ai-client-api-admin-service';
import type {
  AiClientModelRequestDTO,
  AiClientModelResponseDTO,
  ModelDefaultPolicy,
} from '../services/ai-client-model-admin-service';
import {
  useModelCatalogQuery,
  useProviderHealthCheckMutation,
  useProviderModelSyncMutation,
  useSaveModelMutation,
  useUpdateDefaultModelPolicyMutation,
} from '../features/models/api/model-queries';
import { theme } from '../styles/theme';

const { Text, Title, Paragraph } = Typography;
const { Option } = Select;

type ModelForm = {
  id?: number;
  modelId: string;
  modelName: string;
  modelType: string;
  apiId: string;
  modelUsage: string;
  description: string;
  status: number;
};

type TabKey = 'api' | 'models' | 'health' | 'defaults';

const ProviderList = styled.div`
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: ${theme.spacing.sm};
`;

const ProviderItem = styled.button<{ $active?: boolean }>`
  width: 100%;
  min-width: 0;
  padding: ${theme.spacing.base};
  border: 1px solid ${(props) => (props.$active ? theme.colors.primary : theme.colors.border.secondary)};
  border-radius: ${theme.borderRadius.base};
  background: ${(props) => (props.$active ? '#eff6ff' : theme.colors.bg.primary)};
  text-align: left;
  cursor: pointer;

  &:hover {
    border-color: ${theme.colors.primary};
  }
`;

const ProviderHeader = styled.div`
  display: flex;
  justify-content: space-between;
  gap: ${theme.spacing.sm};
  min-width: 0;
`;

const TabBar = styled.div`
  display: flex;
  gap: ${theme.spacing.sm};
  flex-wrap: wrap;
  margin-bottom: ${theme.spacing.base};
`;

const TabButton = styled.button<{ $active?: boolean }>`
  border: 1px solid ${(props) => (props.$active ? theme.colors.primary : theme.colors.border.secondary)};
  border-radius: ${theme.borderRadius.base};
  background: ${(props) => (props.$active ? '#eff6ff' : theme.colors.bg.primary)};
  color: ${(props) => (props.$active ? theme.colors.primary : theme.colors.text.secondary)};
  padding: 8px 12px;
  cursor: pointer;
`;

const FieldGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(240px, 100%), 1fr));
  gap: ${theme.spacing.base};
`;

const Field = styled.div`
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
`;

const providerTypeOf = (api: AiClientApiResponseDTO) => {
  if (api.providerType === 'OPENAI_COMPATIBLE') return 'OpenAI Compatible';
  if (api.providerType === 'DASHSCOPE_COMPATIBLE') return 'DashScope Compatible';
  if (api.providerType === 'LOCAL_OPENAI_COMPATIBLE') return 'Local OpenAI Compatible';
  if (api.baseUrl?.includes('openai')) return 'OpenAI Compatible';
  if (api.baseUrl?.includes('dashscope')) return 'DashScope Compatible';
  return 'OpenAI Compatible';
};

const statusTag = (status?: number) => (
  <Tag color={status === 1 ? 'green' : 'red'}>{status === 1 ? '启用' : '停用'}</Tag>
);

const defaultModelForm = (apiId = ''): ModelForm => ({
  modelId: '',
  modelName: '',
  modelType: 'CHAT',
  apiId,
  modelUsage: 'CHAT',
  description: '',
  status: 1,
});

const emptyDefaultPolicy = (): ModelDefaultPolicy => ({
  defaultChatModelId: '',
  defaultEmbeddingModelId: '',
  defaultRerankModelId: '',
  defaultVisionModelId: '',
  status: 'ENABLED',
});

export const ModelApiManagement: React.FC = () => {
  const [selectedProviderId, setSelectedProviderId] = useState('');
  const [keyword, setKeyword] = useState('');
  const [activeTab, setActiveTab] = useState<TabKey>('models');
  const [createProviderVisible, setCreateProviderVisible] = useState(false);
  const [editProviderVisible, setEditProviderVisible] = useState(false);
  const [editingProvider, setEditingProvider] = useState<AiClientApiResponseDTO | null>(null);
  const [modelModalVisible, setModelModalVisible] = useState(false);
  const [modelForm, setModelForm] = useState<ModelForm>(defaultModelForm());
  const [policyDraft, setPolicyDraft] = useState<ModelDefaultPolicy | null>(null);

  const catalogQuery = useModelCatalogQuery(emptyDefaultPolicy);
  const policyMutation = useUpdateDefaultModelPolicyMutation();
  const healthMutation = useProviderHealthCheckMutation();
  const modelSyncMutation = useProviderModelSyncMutation();
  const saveModelMutation = useSaveModelMutation();

  const providers = catalogQuery.data?.providers || [];
  const models = catalogQuery.data?.models || [];
  const defaultPolicy = policyDraft || catalogQuery.data?.defaultPolicy || emptyDefaultPolicy();
  const loading = catalogQuery.isFetching;
  const policySaving = policyMutation.isPending;
  const healthChecking = healthMutation.isPending;
  const healthResult = healthMutation.data || null;
  const modelSyncing = modelSyncMutation.isPending;
  const modelSyncResult = modelSyncMutation.data || null;

  const filteredProviders = useMemo(() => {
    const value = keyword.trim().toLowerCase();
    if (!value) return providers;
    return providers.filter((provider) =>
      [provider.providerName, provider.providerType, provider.apiId, provider.baseUrl, provider.completionsPath, provider.embeddingsPath]
        .filter(Boolean)
        .some((item) => String(item).toLowerCase().includes(value)),
    );
  }, [keyword, providers]);

  const selectedProvider = useMemo(
    () => providers.find((provider) => provider.apiId === selectedProviderId) || filteredProviders[0] || providers[0],
    [filteredProviders, providers, selectedProviderId],
  );

  const providerModels = useMemo(
    () => models.filter((model) => model.apiId === selectedProvider?.apiId),
    [models, selectedProvider?.apiId],
  );

  const selectedHealthResult = healthResult?.apiId === selectedProvider?.apiId ? healthResult : null;

  const modelNameOf = (modelId?: string) =>
    models.find((model) => model.modelId === modelId)?.modelName || modelId || '未设置';

  const modelsForUsage = (usage: string) =>
    models.filter((model) => model.status === 1
      && `${model.modelUsage || ''} ${model.modelType || ''}`.toUpperCase().includes(usage));

  const saveDefaultPolicy = async () => {
    try {
      const saved = await policyMutation.mutateAsync(defaultPolicy);
      setPolicyDraft(saved || defaultPolicy);
      Toast.success('默认模型策略已保存');
    } catch (error) {
      Toast.error(error instanceof Error ? error.message : '保存默认模型策略失败');
    }
  };

  const runProviderHealthCheck = async () => {
    if (!selectedProvider?.apiId) {
      Toast.warning('请先选择 Provider');
      return;
    }
    try {
      const result = await healthMutation.mutateAsync(selectedProvider.apiId);
      setActiveTab('health');
      if (result?.status === 'SUCCESS') {
        Toast.success(`Provider 可用，耗时 ${result.latencyMs ?? '-'}ms`);
      } else {
        Toast.error(result?.errorMessage || 'Provider 健康检测失败');
      }
    } catch (error) {
      Toast.error(error instanceof Error ? error.message : 'Provider 健康检测失败');
    }
  };

  const syncProviderModels = async () => {
    if (!selectedProvider?.apiId) {
      Toast.warning('请先选择 Provider');
      return;
    }
    try {
      const result = await modelSyncMutation.mutateAsync(selectedProvider.apiId);
      Toast.success(`同步完成：新增 ${result?.createdCount ?? 0}，更新 ${result?.updatedCount ?? 0}，跳过 ${result?.skippedCount ?? 0}`);
    } catch (error) {
      Toast.error(error instanceof Error ? error.message : '同步模型目录失败');
    }
  };

  const openModelModal = (model?: AiClientModelResponseDTO) => {
    if (model) {
      setModelForm({
        id: model.id,
        modelId: model.modelId,
        modelName: model.modelName,
        modelType: model.modelType || 'CHAT',
        apiId: model.apiId || selectedProvider?.apiId || '',
        modelUsage: model.modelUsage || 'CHAT',
        description: model.description || '',
        status: model.status,
      });
    } else {
      setModelForm(defaultModelForm(selectedProvider?.apiId || ''));
    }
    setModelModalVisible(true);
  };

  const saveModel = async () => {
    if (!modelForm.modelId.trim() || !modelForm.modelName.trim() || !modelForm.apiId.trim()) {
      Toast.warning('模型 ID、模型名称和 Provider ID 不能为空');
      return;
    }
    const payload: AiClientModelRequestDTO = {
      id: modelForm.id,
      modelId: modelForm.modelId.trim(),
      modelName: modelForm.modelName.trim(),
      modelType: modelForm.modelType,
      apiId: modelForm.apiId,
      modelUsage: modelForm.modelUsage,
      description: modelForm.description,
      status: modelForm.status,
    };
    try {
      await saveModelMutation.mutateAsync({ payload, editing: Boolean(modelForm.id) });
      Toast.success(modelForm.id ? '模型已更新' : '模型已新增');
      setModelModalVisible(false);
    } catch (error) {
      console.error('保存模型失败:', error);
      Toast.error(error instanceof Error ? error.message : '保存模型失败');
    }
  };

  const toggleModelStatus = async (record: AiClientModelResponseDTO) => {
    try {
      await saveModelMutation.mutateAsync({
        editing: true,
        payload: {
          id: record.id,
          modelId: record.modelId,
          modelName: record.modelName,
          modelType: record.modelType,
          apiId: record.apiId,
          modelUsage: record.modelUsage,
          description: record.description,
          status: record.status === 1 ? 0 : 1,
        },
      });
      Toast.success(record.status === 1 ? '模型已停用' : '模型已启用');
    } catch (error) {
      Toast.error(error instanceof Error ? error.message : '更新状态失败');
    }
  };

  const columns = [
    {
      title: '模型',
      dataIndex: 'modelName',
      width: 220,
      render: (_: string, record: AiClientModelResponseDTO) => (
        <div>
          <Text strong>{record.modelName || record.modelId}</Text>
          <Text type="tertiary" size="small" style={{ display: 'block' }}>{record.modelId}</Text>
        </div>
      ),
    },
    { title: 'Provider ID', dataIndex: 'apiId', width: 180 },
    {
      title: '模型类型',
      dataIndex: 'modelType',
      width: 120,
      render: (value: string) => <Tag color="blue">{value || 'CHAT'}</Tag>,
    },
    {
      title: '用途',
      dataIndex: 'modelUsage',
      width: 180,
      render: (value: string) => value || '-',
    },
    {
      title: '能力',
      width: 260,
      render: (_: unknown, record: AiClientModelResponseDTO) => (
        <Space wrap>
          <Tag color="blue">Tools: 需后端探测</Tag>
          <Tag color={record.modelType === 'VISION' ? 'green' : 'grey'}>Vision</Tag>
          <Tag color="grey">JsonMode: 需探测</Tag>
        </Space>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (status: number) => statusTag(status),
    },
    {
      title: '操作',
      width: 180,
      fixed: 'right' as const,
      render: (_: unknown, record: AiClientModelResponseDTO) => (
        <Space>
          <Button size="small" icon={<IconEdit />} onClick={() => openModelModal(record)}>编辑</Button>
          <Button size="small" type={record.status === 1 ? 'danger' : 'primary'} theme="borderless" onClick={() => toggleModelStatus(record)}>
            {record.status === 1 ? '停用' : '启用'}
          </Button>
        </Space>
      ),
    },
  ];

  return (
    <OpsPageShell selectedKey="model-api-management">
      <OpsPageHeader
        title="模型接入"
        description="模型目录必须依附 API Provider。这里统一维护 Provider API、Provider 下模型目录、可用性测试和默认模型策略。"
        primaryAction={(
          <Button theme="solid" icon={<IconPlus />} onClick={() => setCreateProviderVisible(true)}>
            新建 Provider
          </Button>
        )}
        extra={<Button icon={<IconRefresh />} loading={loading} onClick={() => catalogQuery.refetch()}>刷新</Button>}
      />

      <OpsTwoColumnGrid>
        <OpsSectionCard title="API Provider">
          <OpsActionBar>
            <Input
              prefix={<IconSearch />}
              placeholder="搜索 Provider ID / Base URL"
              value={keyword}
              onChange={setKeyword}
            />
          </OpsActionBar>
          <ProviderList>
            {filteredProviders.map((provider) => (
              <ProviderItem
                type="button"
                key={provider.apiId}
                $active={provider.apiId === selectedProvider?.apiId}
                onClick={() => setSelectedProviderId(provider.apiId)}
              >
                <ProviderHeader>
                  <Text strong>{provider.providerName || provider.apiId}</Text>
                  {statusTag(provider.status)}
                </ProviderHeader>
                <Text type="tertiary" size="small" ellipsis={{ showTooltip: true }} style={{ display: 'block', marginTop: 6 }}>
                  {provider.baseUrl}
                </Text>
                <Space wrap style={{ marginTop: 8 }}>
                  <Tag color="blue">{providerTypeOf(provider)}</Tag>
                  <Tag color="grey">模型 {models.filter((model) => model.apiId === provider.apiId).length}</Tag>
                </Space>
              </ProviderItem>
            ))}
            {!filteredProviders.length && (
              <OpsEmptyState title="暂无 Provider" description="先新建 API Provider，再在右侧维护它下面的模型目录。" />
            )}
          </ProviderList>
        </OpsSectionCard>

        <div style={{ minWidth: 0 }}>
          {selectedProvider ? (
            <>
              <OpsSectionCard>
                <ProviderHeader>
                  <div>
                    <Title heading={4} style={{ margin: 0 }}>{selectedProvider.providerName || selectedProvider.apiId}</Title>
                    <Paragraph type="tertiary" style={{ margin: '6px 0 0' }}>{selectedProvider.baseUrl}</Paragraph>
                  </div>
                  <Space wrap>
                    {statusTag(selectedProvider.status)}
                    <Button
                      icon={<IconEdit />}
                      onClick={() => {
                        setEditingProvider(selectedProvider);
                        setEditProviderVisible(true);
                      }}
                    >
                      编辑 Provider
                    </Button>
                    <Button loading={healthChecking} onClick={runProviderHealthCheck}>测试连接</Button>
                  </Space>
                </ProviderHeader>
                <OpsResponsiveGrid $min="180px" style={{ marginTop: 16 }}>
                  <OpsStatusCard label="Provider 类型" value={providerTypeOf(selectedProvider)} />
                  <OpsStatusCard label="模型数量" value={providerModels.length} description="仅统计 providerId/apiId 归属当前 Provider 的模型" />
                  <OpsStatusCard label="Chat 默认" value={modelNameOf(defaultPolicy.defaultChatModelId)} />
                  <OpsStatusCard label="Embedding 默认" value={modelNameOf(defaultPolicy.defaultEmbeddingModelId)} />
                </OpsResponsiveGrid>
              </OpsSectionCard>

              <OpsSectionCard>
                <TabBar>
                  {[
                    ['api', 'API 配置'],
                    ['models', '模型目录'],
                    ['health', '可用性测试'],
                    ['defaults', '默认模型策略'],
                  ].map(([key, label]) => (
                    <TabButton key={key} $active={activeTab === key} onClick={() => setActiveTab(key as TabKey)}>
                      {label}
                    </TabButton>
                  ))}
                </TabBar>

                {activeTab === 'api' && (
                  <OpsResponsiveGrid $min="260px">
                    <OpsStatusCard label="Base URL" value={selectedProvider.baseUrl} />
                    <OpsStatusCard label="对话路径" value={selectedProvider.completionsPath || '-'} />
                    <OpsStatusCard label="嵌入路径" value={selectedProvider.embeddingsPath || '-'} />
                    <OpsStatusCard label="认证方式" value={selectedProvider.apiKey ? 'Bearer/API Key' : '未配置'} />
                  </OpsResponsiveGrid>
                )}

                {activeTab === 'models' && (
                  <>
                    <OpsActionBar>
                      <Text type="tertiary">Provider Models 只展示 `apiId/providerId = {selectedProvider.apiId}` 的模型。</Text>
                      <OpsActionGroup>
                        <Button loading={modelSyncing} onClick={syncProviderModels}>同步模型</Button>
                        <Button theme="solid" icon={<IconPlus />} onClick={() => openModelModal()}>
                          新增模型
                        </Button>
                      </OpsActionGroup>
                    </OpsActionBar>
                    <TableScroll>
                      <Table
                        loading={loading}
                        columns={columns}
                        dataSource={providerModels}
                        rowKey="id"
                        pagination={false}
                        scroll={{ x: 1180 }}
                      />
                    </TableScroll>
                    {modelSyncResult?.apiId === selectedProvider.apiId && (
                      <OpsAdvancedPreview title="模型同步结果">
                        <JsonBlock>{JSON.stringify(modelSyncResult, null, 2)}</JsonBlock>
                      </OpsAdvancedPreview>
                    )}
                  </>
                )}

                {activeTab === 'health' && (
                  <Space vertical align="start" spacing="medium" style={{ width: '100%' }}>
                    <Text type="tertiary">
                      这里会访问 Provider 的标准模型列表端点做真实连通性检测，并记录耗时、HTTP 状态和审计日志；不会触发聊天生成，也不会展示密钥。
                    </Text>
                    <Button theme="solid" loading={healthChecking} onClick={runProviderHealthCheck}>
                      立即检测当前 Provider
                    </Button>
                    {selectedHealthResult ? (
                      <>
                        <OpsResponsiveGrid $min="180px">
                          <OpsStatusCard
                            label="检测状态"
                            value={selectedHealthResult.status === 'SUCCESS' ? '可用' : '失败'}
                            description={selectedHealthResult.errorMessage || 'Provider 返回成功状态'}
                          />
                          <OpsStatusCard label="HTTP 状态" value={selectedHealthResult.httpStatus ?? '-'} />
                          <OpsStatusCard label="耗时" value={`${selectedHealthResult.latencyMs ?? '-'}ms`} />
                          <OpsStatusCard label="检测时间" value={selectedHealthResult.testTime || selectedHealthResult.checkedAt || '-'} />
                        </OpsResponsiveGrid>
                        <OpsAdvancedPreview title="健康检测详情">
                          <JsonBlock>{JSON.stringify(selectedHealthResult, null, 2)}</JsonBlock>
                        </OpsAdvancedPreview>
                      </>
                    ) : (
                      <OpsEmptyState
                        title="尚未检测当前 Provider"
                        description="点击“立即检测当前 Provider”后，会展示真实 HTTP 状态、耗时和错误信息。"
                      />
                    )}
                  </Space>
                )}

                {activeTab === 'defaults' && (
                  <Space vertical align="start" spacing="medium" style={{ width: '100%' }}>
                    <Text type="tertiary">
                      这里保存全局默认策略。项目未覆盖时，运行时按用途选择对应模型；空值表示不设置平台默认。
                    </Text>
                    <OpsResponsiveGrid $min="240px">
                      {([
                        ['defaultChatModelId', '默认 Chat 模型', 'CHAT'],
                        ['defaultEmbeddingModelId', '默认 Embedding 模型', 'EMBED'],
                        ['defaultRerankModelId', '默认 Rerank 模型', 'RERANK'],
                        ['defaultVisionModelId', '默认 Vision 模型', 'VISION'],
                      ] as const).map(([field, label, usage]) => (
                        <Field key={field}>
                          <Text strong>{label}</Text>
                          <Select
                            value={defaultPolicy[field] || ''}
                            showClear
                            filter
                            style={{ width: '100%' }}
                            onChange={(value) => setPolicyDraft((current) => ({
                              ...(current || defaultPolicy),
                              [field]: String(value || ''),
                            }))}
                          >
                            {modelsForUsage(usage).map((model) => (
                              <Option key={model.modelId} value={model.modelId}>
                                {model.modelName} / {model.apiId}
                              </Option>
                            ))}
                          </Select>
                        </Field>
                      ))}
                    </OpsResponsiveGrid>
                    <Button theme="solid" loading={policySaving} onClick={saveDefaultPolicy}>
                      保存默认策略
                    </Button>
                  </Space>
                )}
              </OpsSectionCard>

              <OpsAdvancedPreview>
                <JsonBlock>{JSON.stringify({ provider: selectedProvider, models: providerModels }, null, 2)}</JsonBlock>
              </OpsAdvancedPreview>
            </>
          ) : (
            <OpsEmptyState
              title="还没有模型 Provider"
              description="模型目录不能脱离 Provider 创建。先新建 Provider，再添加 Chat、Embedding、Rerank、Vision 等模型。"
              actionText="新建 Provider"
              onAction={() => setCreateProviderVisible(true)}
            />
          )}
        </div>
      </OpsTwoColumnGrid>

      <AiClientApiCreateModal
        visible={createProviderVisible}
        onCancel={() => setCreateProviderVisible(false)}
        onSuccess={() => {
          setCreateProviderVisible(false);
          void catalogQuery.refetch();
        }}
      />

      <AiClientApiEditModal
        visible={editProviderVisible}
        editingRecord={editingProvider}
        onCancel={() => {
          setEditProviderVisible(false);
          setEditingProvider(null);
        }}
        onSuccess={() => {
          setEditProviderVisible(false);
          setEditingProvider(null);
          void catalogQuery.refetch();
        }}
      />

      <Modal
        title={modelForm.id ? '编辑模型' : '新增模型'}
        visible={modelModalVisible}
        width={720}
        onCancel={() => setModelModalVisible(false)}
        onOk={saveModel}
      >
        <FieldGrid>
          <Field>
            <Text strong>Provider ID</Text>
            <Select value={modelForm.apiId} style={{ width: '100%' }} onChange={(apiId) => setModelForm({ ...modelForm, apiId: String(apiId) })}>
              {providers.map((provider) => (
                <Option key={provider.apiId} value={provider.apiId}>{provider.apiId}</Option>
              ))}
            </Select>
          </Field>
          <Field>
            <Text strong>模型 ID</Text>
            <Input value={modelForm.modelId} onChange={(modelId) => setModelForm({ ...modelForm, modelId })} />
          </Field>
          <Field>
            <Text strong>模型名称</Text>
            <Input value={modelForm.modelName} onChange={(modelName) => setModelForm({ ...modelForm, modelName })} />
          </Field>
          <Field>
            <Text strong>模型类型</Text>
            <Select value={modelForm.modelType} style={{ width: '100%' }} onChange={(modelType) => setModelForm({ ...modelForm, modelType: String(modelType) })}>
              <Option value="CHAT">Chat</Option>
              <Option value="EMBEDDING">Embedding</Option>
              <Option value="RERANK">Rerank</Option>
              <Option value="VISION">Vision</Option>
            </Select>
          </Field>
          <Field>
            <Text strong>默认用途</Text>
            <Select value={modelForm.modelUsage} style={{ width: '100%' }} onChange={(modelUsage) => setModelForm({ ...modelForm, modelUsage: String(modelUsage) })}>
              <Option value="CHAT">Chat</Option>
              <Option value="EMBEDDING">Embedding</Option>
              <Option value="RERANK">Rerank</Option>
              <Option value="VISION">Vision</Option>
            </Select>
          </Field>
          <Field>
            <Text strong>状态</Text>
            <Select value={modelForm.status} style={{ width: '100%' }} onChange={(status) => setModelForm({ ...modelForm, status: Number(status) })}>
              <Option value={1}>启用</Option>
              <Option value={0}>停用</Option>
            </Select>
          </Field>
        </FieldGrid>
        <Field style={{ marginTop: 16 }}>
          <Text strong>说明</Text>
          <Input value={modelForm.description} onChange={(description) => setModelForm({ ...modelForm, description })} />
        </Field>
      </Modal>
    </OpsPageShell>
  );
};
