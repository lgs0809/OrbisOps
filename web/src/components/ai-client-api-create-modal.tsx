import React, { useState } from 'react';
import {
  Modal,
  Input,
  Select,
  Button,
  Toast,
  Space
} from '@douyinfe/semi-ui';
import {
  aiClientApiAdminService,
  AiClientApiRequestDTO
} from '../services/ai-client-api-admin-service';

interface AiClientApiCreateModalProps {
  visible: boolean;
  onCancel: () => void;
  onSuccess: () => void;
}

interface FormData {
  providerName: string;
  providerType: string;
  baseUrl: string;
  apiKey: string;
  completionsPath: string;
  embeddingsPath: string;
  status: number;
}

interface FormErrors {
  providerName?: string;
  baseUrl?: string;
  apiKey?: string;
  completionsPath?: string;
  embeddingsPath?: string;
}

export const AiClientApiCreateModal: React.FC<AiClientApiCreateModalProps> = ({
  visible,
  onCancel,
  onSuccess
}) => {
  const [loading, setLoading] = useState(false);
  const [formData, setFormData] = useState<FormData>({
    providerName: '',
    providerType: 'OPENAI_COMPATIBLE',
    baseUrl: '',
    apiKey: '',
    completionsPath: 'v1/chat/completions',
    embeddingsPath: 'v1/embeddings',
    status: 1
  });
  const [errors, setErrors] = useState<FormErrors>({});

  // 生成8位数字API ID
  const generateApiId = (): string => {
    const min = 10000000; // 8位数字的最小值
    const max = 99999999; // 8位数字的最大值
    return Math.floor(Math.random() * (max - min + 1) + min).toString();
  };

  // 表单验证
  const validateForm = (): boolean => {
    const newErrors: FormErrors = {};

    if (!formData.providerName.trim()) {
      newErrors.providerName = '请输入 Provider 名称';
    }

    if (!formData.baseUrl.trim()) {
      newErrors.baseUrl = '请输入基础URL';
    } else if (!/^https?:\/\/.+/.test(formData.baseUrl.trim())) {
      newErrors.baseUrl = '请输入有效的URL格式（以http://或https://开头）';
    }

    if (!formData.apiKey.trim()) {
      newErrors.apiKey = '请输入API密钥';
    } else if (formData.apiKey.trim().length < 10) {
      newErrors.apiKey = 'API密钥长度至少10个字符';
    }

    if (!formData.completionsPath.trim()) {
      newErrors.completionsPath = '请输入对话路径';
    }

    if (!formData.embeddingsPath.trim()) {
      newErrors.embeddingsPath = '请输入嵌入路径';
    }

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  // 处理表单提交
  const handleSubmit = async () => {
    if (!validateForm()) {
      return;
    }

    setLoading(true);
    try {
      const request: AiClientApiRequestDTO = {
        apiId: generateApiId(),
        providerName: formData.providerName.trim(),
        providerType: formData.providerType,
        baseUrl: formData.baseUrl.trim(),
        apiKey: formData.apiKey.trim(),
        completionsPath: formData.completionsPath.trim(),
        embeddingsPath: formData.embeddingsPath.trim(),
        status: formData.status
      };

      const result = await aiClientApiAdminService.createAiClientApi(request);

      if (result.code === '0000' && result.data) {
        Toast.success('模型 API 创建成功');
        handleReset();
        onSuccess();
      } else {
        throw new Error(result.info || '创建失败');
      }
    } catch (error) {
      console.error('创建模型 API 失败:', error);
      Toast.error('创建失败，请检查网络连接或稍后重试');
    } finally {
      setLoading(false);
    }
  };

  // 重置表单
  const handleReset = () => {
    setFormData({
      providerName: '',
      providerType: 'OPENAI_COMPATIBLE',
      baseUrl: '',
      apiKey: '',
      completionsPath: 'v1/chat/completions',
      embeddingsPath: 'v1/embeddings',
      status: 1
    });
    setErrors({});
  };

  // 处理取消
  const handleCancel = () => {
    handleReset();
    onCancel();
  };

  return (
    <Modal
      title="新增模型 API"
      visible={visible}
      onCancel={handleCancel}
      footer={null}
      width={600}
      maskClosable={false}
    >
      <div style={{ padding: '20px 0' }}>
        <div style={{ marginBottom: '16px' }}>
          <div style={{ marginBottom: '8px', display: 'flex', alignItems: 'center' }}>
            <span style={{ width: '120px', textAlign: 'right', marginRight: '12px' }}>
              Provider 名称<span style={{ color: 'red' }}>*</span>:
            </span>
            <Input
               placeholder="例如：OpenAI 中转站 / 本地 Qwen 服务"
               value={formData.providerName}
               onChange={(value: string) => setFormData(prev => ({ ...prev, providerName: value }))}
               style={{ flex: 1 }}
             />
          </div>
          {errors.providerName && (
            <div style={{ marginLeft: '132px', color: 'red', fontSize: '12px' }}>
              {errors.providerName}
            </div>
          )}
        </div>

        <div style={{ marginBottom: '16px' }}>
          <div style={{ display: 'flex', alignItems: 'center' }}>
            <span style={{ width: '120px', textAlign: 'right', marginRight: '12px' }}>
              Provider 类型<span style={{ color: 'red' }}>*</span>:
            </span>
            <Select
              value={formData.providerType}
              onChange={(value) => setFormData(prev => ({ ...prev, providerType: String(value || 'OPENAI_COMPATIBLE') }))}
              style={{ flex: 1 }}
            >
              <Select.Option value="OPENAI_COMPATIBLE">OpenAI Compatible</Select.Option>
              <Select.Option value="DASHSCOPE_COMPATIBLE">DashScope Compatible</Select.Option>
              <Select.Option value="LOCAL_OPENAI_COMPATIBLE">Local OpenAI Compatible</Select.Option>
            </Select>
          </div>
        </div>

        <div style={{ marginBottom: '16px' }}>
          <div style={{ marginBottom: '8px', display: 'flex', alignItems: 'center' }}>
            <span style={{ width: '120px', textAlign: 'right', marginRight: '12px' }}>
              基础URL<span style={{ color: 'red' }}>*</span>:
            </span>
            <Input
               placeholder="请输入基础URL，如：https://api.openai.com"
               value={formData.baseUrl}
               onChange={(value: string) => setFormData(prev => ({ ...prev, baseUrl: value }))}
               style={{ flex: 1 }}
             />
          </div>
          {errors.baseUrl && (
            <div style={{ marginLeft: '132px', color: 'red', fontSize: '12px' }}>
              {errors.baseUrl}
            </div>
          )}
        </div>

        <div style={{ marginBottom: '16px' }}>
          <div style={{ marginBottom: '8px', display: 'flex', alignItems: 'center' }}>
            <span style={{ width: '120px', textAlign: 'right', marginRight: '12px' }}>
              API密钥<span style={{ color: 'red' }}>*</span>:
            </span>
            <Input
               placeholder="请输入API密钥"
               value={formData.apiKey}
               onChange={(value: string) => setFormData(prev => ({ ...prev, apiKey: value }))}
               style={{ flex: 1 }}
               type="password"
             />
          </div>
          {errors.apiKey && (
            <div style={{ marginLeft: '132px', color: 'red', fontSize: '12px' }}>
              {errors.apiKey}
            </div>
          )}
        </div>

        <div style={{ marginBottom: '16px' }}>
          <div style={{ marginBottom: '8px', display: 'flex', alignItems: 'center' }}>
            <span style={{ width: '120px', textAlign: 'right', marginRight: '12px' }}>
              对话路径<span style={{ color: 'red' }}>*</span>:
            </span>
            <Input
               placeholder="对话补全路径"
               value={formData.completionsPath}
               onChange={(value: string) => setFormData(prev => ({ ...prev, completionsPath: value }))}
               style={{ flex: 1 }}
             />
          </div>
          {errors.completionsPath && (
            <div style={{ marginLeft: '132px', color: 'red', fontSize: '12px' }}>
              {errors.completionsPath}
            </div>
          )}
        </div>

        <div style={{ marginBottom: '16px' }}>
          <div style={{ marginBottom: '8px', display: 'flex', alignItems: 'center' }}>
            <span style={{ width: '120px', textAlign: 'right', marginRight: '12px' }}>
              嵌入路径<span style={{ color: 'red' }}>*</span>:
            </span>
            <Input
               placeholder="嵌入向量路径"
               value={formData.embeddingsPath}
               onChange={(value: string) => setFormData(prev => ({ ...prev, embeddingsPath: value }))}
               style={{ flex: 1 }}
             />
          </div>
          {errors.embeddingsPath && (
            <div style={{ marginLeft: '132px', color: 'red', fontSize: '12px' }}>
              {errors.embeddingsPath}
            </div>
          )}
        </div>

        <div style={{ marginBottom: '16px' }}>
          <div style={{ display: 'flex', alignItems: 'center' }}>
            <span style={{ width: '120px', textAlign: 'right', marginRight: '12px' }}>
              状态<span style={{ color: 'red' }}>*</span>:
            </span>
            <Select
              placeholder="请选择状态"
              value={formData.status}
              onChange={(value) => setFormData(prev => ({ ...prev, status: value as number }))}
              style={{ flex: 1 }}
            >
              <Select.Option value={1}>启用</Select.Option>
              <Select.Option value={0}>禁用</Select.Option>
            </Select>
          </div>
        </div>

        <div style={{ textAlign: 'right', marginTop: '20px' }}>
          <Space>
            <Button onClick={handleCancel}>
              取消
            </Button>
            <Button
              type="primary"
              onClick={handleSubmit}
              loading={loading}
            >
              保存
            </Button>
          </Space>
        </div>
      </div>
    </Modal>
  );
};
