import React, { useEffect, useState } from 'react';
import { Banner, Input, Modal, Select, Space, Toast, Typography } from '@douyinfe/semi-ui';

import type { AiClientApiResponseDTO } from '../services/ai-client-api-admin-service';
import {
  aiClientProviderReferenceService,
  type AiClientProviderReferenceRequest,
} from '../services/ai-client-provider-reference-service';
import { userFacingError } from '../utils/user-facing-error';

const { Text } = Typography;
const { Option } = Select;

interface Props {
  visible: boolean;
  editingRecord?: AiClientApiResponseDTO | null;
  onCancel: () => void;
  onSuccess: () => void;
}

interface FormState {
  providerName: string;
  providerType: string;
  baseUrl: string;
  credentialEnvironmentVariable: string;
  completionsPath: string;
  embeddingsPath: string;
  status: number;
}

const emptyForm = (): FormState => ({
  providerName: '',
  providerType: 'OPENAI_COMPATIBLE',
  baseUrl: '',
  credentialEnvironmentVariable: '',
  completionsPath: 'v1/chat/completions',
  embeddingsPath: 'v1/embeddings',
  status: 1,
});

const generateApiId = () => String(Math.floor(Math.random() * 90_000_000) + 10_000_000);
const validEnvironmentName = (value: string) => /^[A-Za-z_][A-Za-z0-9_]*$/.test(value.trim());

export const AiClientProviderReferenceModal: React.FC<Props> = ({ visible, editingRecord, onCancel, onSuccess }) => {
  const [form, setForm] = useState<FormState>(emptyForm());
  const [loading, setLoading] = useState(false);
  const [legacyCredential, setLegacyCredential] = useState(false);
  const editing = Boolean(editingRecord);

  useEffect(() => {
    if (!visible) return;
    if (!editingRecord) {
      setForm(emptyForm());
      setLegacyCredential(false);
      return;
    }
    setForm({
      providerName: editingRecord.providerName || editingRecord.apiId || '',
      providerType: editingRecord.providerType || 'OPENAI_COMPATIBLE',
      baseUrl: editingRecord.baseUrl || '',
      credentialEnvironmentVariable: '',
      completionsPath: editingRecord.completionsPath || 'v1/chat/completions',
      embeddingsPath: editingRecord.embeddingsPath || 'v1/embeddings',
      status: editingRecord.status,
    });
    setLegacyCredential(false);
    void aiClientProviderReferenceService.metadata(editingRecord.apiId)
      .then((metadata) => {
        setForm((current) => ({ ...current, credentialEnvironmentVariable: metadata.credentialEnvironmentVariable || '' }));
        setLegacyCredential(metadata.legacyStoredCredential);
      })
      .catch(() => {
        setLegacyCredential(false);
      });
  }, [editingRecord, visible]);

  const submit = async () => {
    if (!form.providerName.trim()) {
      Toast.warning('请输入 Provider 名称。');
      return;
    }
    if (!/^https?:\/\/.+/.test(form.baseUrl.trim())) {
      Toast.warning('Base URL 必须以 http:// 或 https:// 开头。');
      return;
    }
    if (!validEnvironmentName(form.credentialEnvironmentVariable)) {
      Toast.warning('凭据环境变量名只能包含字母、数字和下划线，并且不能以数字开头。');
      return;
    }
    if (!form.completionsPath.trim() || !form.embeddingsPath.trim()) {
      Toast.warning('Completions 与 Embeddings 路径不能为空。');
      return;
    }

    const payload: AiClientProviderReferenceRequest = {
      id: editingRecord?.id,
      apiId: editingRecord?.apiId || generateApiId(),
      providerName: form.providerName.trim(),
      providerType: form.providerType,
      baseUrl: form.baseUrl.trim(),
      credentialEnvironmentVariable: form.credentialEnvironmentVariable.trim().toUpperCase(),
      completionsPath: form.completionsPath.trim(),
      embeddingsPath: form.embeddingsPath.trim(),
      status: form.status,
    };

    setLoading(true);
    try {
      const changed = editing
        ? await aiClientProviderReferenceService.update(payload)
        : await aiClientProviderReferenceService.create(payload);
      if (!changed) throw new Error('Provider 配置没有发生变化。');
      Toast.success(editing ? 'Provider 已更新。' : 'Provider 已创建。');
      onSuccess();
    } catch (error) {
      Toast.error(userFacingError(error, editing ? '更新 Provider 失败，请稍后重试。' : '创建 Provider 失败，请稍后重试。'));
    } finally {
      setLoading(false);
    }
  };

  return (
    <Modal
      title={editing ? '编辑 Provider' : '新建 Provider'}
      visible={visible}
      width={720}
      maskClosable={false}
      confirmLoading={loading}
      okText={editing ? '保存修改' : '创建 Provider'}
      cancelText="取消"
      onOk={() => void submit()}
      onCancel={onCancel}
    >
      <Space vertical align="start" spacing="medium" style={{ width: '100%' }}>
        {legacyCredential && (
          <Banner
            type="warning"
            description="该 Provider 仍在使用历史数据库凭据。保存后会迁移为部署环境变量引用，不再由浏览器或数据库保存真实凭据。"
          />
        )}
        {editing && (
          <div style={{ width: '100%' }}>
            <Text strong>API ID</Text>
            <Input value={editingRecord?.apiId || ''} disabled />
          </div>
        )}
        <div style={{ width: '100%' }}>
          <Text strong>Provider 名称</Text>
          <Input
            aria-label="Provider 名称"
            placeholder="OpenAI 网关 / 本地 Qwen 服务"
            value={form.providerName}
            onChange={(providerName) => setForm({ ...form, providerName })}
          />
        </div>
        <div style={{ width: '100%' }}>
          <Text strong>Provider 类型</Text>
          <Select value={form.providerType} style={{ width: '100%' }} onChange={(value) => setForm({ ...form, providerType: String(value) })}>
            <Option value="OPENAI_COMPATIBLE">OpenAI Compatible</Option>
            <Option value="DASHSCOPE_COMPATIBLE">DashScope Compatible</Option>
            <Option value="LOCAL_OPENAI_COMPATIBLE">Local OpenAI Compatible</Option>
          </Select>
        </div>
        <div style={{ width: '100%' }}>
          <Text strong>Base URL</Text>
          <Input aria-label="Base URL" placeholder="https://api.example.com" value={form.baseUrl} onChange={(baseUrl) => setForm({ ...form, baseUrl })} />
        </div>
        <div style={{ width: '100%' }}>
          <Text strong>凭据环境变量</Text>
          <Input
            aria-label="凭据环境变量"
            placeholder="OPENAI_API_KEY"
            value={form.credentialEnvironmentVariable}
            onChange={(credentialEnvironmentVariable) => setForm({ ...form, credentialEnvironmentVariable })}
          />
          <Text type="tertiary" size="small">
            OrbisOps 只保存环境变量引用。真实凭据需要配置在后端部署环境中，浏览器不会接收或保存真实凭据。
          </Text>
        </div>
        <div style={{ width: '100%' }}>
          <Text strong>Completions 路径</Text>
          <Input value={form.completionsPath} onChange={(completionsPath) => setForm({ ...form, completionsPath })} />
        </div>
        <div style={{ width: '100%' }}>
          <Text strong>Embeddings 路径</Text>
          <Input value={form.embeddingsPath} onChange={(embeddingsPath) => setForm({ ...form, embeddingsPath })} />
        </div>
        <div style={{ width: '100%' }}>
          <Text strong>状态</Text>
          <Select value={form.status} style={{ width: '100%' }} onChange={(value) => setForm({ ...form, status: Number(value) })}>
            <Option value={1}>启用</Option>
            <Option value={0}>停用</Option>
          </Select>
        </div>
      </Space>
    </Modal>
  );
};
