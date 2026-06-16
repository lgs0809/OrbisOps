import React from 'react';
import styled from 'styled-components';
import {
  Button,
  Input,
  Modal,
  Select,
  Space,
  Tag,
  TextArea,
  Typography,
} from '@douyinfe/semi-ui';

import { theme } from '../../styles/theme';
import { McpTemplateFormState, mcpActionOptions, mcpResourcePresets } from './types';
import { actionsFromText, applyResourcePreset } from './helpers';

const { Option } = Select;

const FormGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: ${theme.spacing.base};
  width: 100%;
  min-width: 0;

  @media (max-width: 720px) {
    grid-template-columns: minmax(0, 1fr);
  }
`;

const Field = styled.div<{ $full?: boolean }>`
  min-width: 0;
  grid-column: ${(props) => (props.$full ? '1 / -1' : 'auto')};
`;

const HelpBox = styled.div`
  grid-column: 1 / -1;
  padding: ${theme.spacing.base};
  border: 1px solid #bfdbfe;
  border-radius: ${theme.borderRadius.base};
  background: #eff6ff;
  color: #1d4ed8;
  line-height: 1.6;
`;

interface Props {
  mode: 'create' | 'edit' | null;
  form: McpTemplateFormState;
  submitting: boolean;
  onChange: (patch: Partial<McpTemplateFormState>) => void;
  onCancel: () => void;
  onSubmit: () => void;
}

export const McpTemplateFormModal: React.FC<Props> = ({
  mode,
  form,
  submitting,
  onChange,
  onCancel,
  onSubmit,
}) => (
  <Modal
    title={mode === 'create' ? '新建 MCP 接入模板' : '编辑 MCP 接入模板'}
    visible={Boolean(mode)}
    onCancel={onCancel}
    width={720}
    style={{ maxWidth: '92vw' }}
    footer={(
      <Space>
        <Button onClick={onCancel}>取消</Button>
        <Button type="primary" loading={submitting} onClick={onSubmit}>保存</Button>
      </Space>
    )}
  >
    <HelpBox>
      先创建模板，再到项目工作空间里基于模板生成项目工具。模板只保存资源类型、动作边界和默认传输方式；真实连接地址和 credentialRef 应在项目工具里配置，不能在模板里写明文密码。
    </HelpBox>
    <FormGrid>
      <Field>
        <Typography.Text strong>模板名称</Typography.Text>
        <Input
          value={form.templateName}
          placeholder="MySQL 只读诊断模板"
          onChange={(templateName) => onChange({ templateName })}
        />
      </Field>
      <Field>
        <Typography.Text strong>资源类型</Typography.Text>
        <Select
          value={form.resourceType}
          onChange={(value) => onChange(applyResourcePreset(String(value || 'custom')))}
          style={{ width: '100%' }}
        >
          {Object.entries(mcpResourcePresets).map(([value, preset]) => (
            <Option key={value} value={value}>{preset.label}</Option>
          ))}
        </Select>
      </Field>
      <Field>
        <Typography.Text strong>传输类型</Typography.Text>
        <Select value={form.transportType} onChange={(value) => onChange({ transportType: String(value) })} style={{ width: '100%' }}>
          <Option value="stdio">stdio</Option>
          <Option value="sse">sse</Option>
          <Option value="streamable-http">streamable-http</Option>
        </Select>
      </Field>
      <Field>
        <Typography.Text strong>风险等级</Typography.Text>
        <Select value={form.riskLevel} onChange={(value) => onChange({ riskLevel: String(value) })} style={{ width: '100%' }}>
          <Option value="LOW">LOW</Option>
          <Option value="MEDIUM">MEDIUM</Option>
          <Option value="HIGH">HIGH</Option>
          <Option value="CRITICAL">CRITICAL</Option>
        </Select>
      </Field>
      <Field>
        <Typography.Text strong>只读</Typography.Text>
        <Select value={form.readOnly} onChange={(value) => onChange({ readOnly: String(value) })} style={{ width: '100%' }}>
          <Option value="true">是</Option>
          <Option value="false">否</Option>
        </Select>
      </Field>
      <Field $full>
        <Typography.Text strong>支持动作</Typography.Text>
        <Select
          multiple
          value={actionsFromText(form.supportedActionsText)}
          placeholder="选择该模板允许的动作"
          style={{ width: '100%' }}
          onChange={(value) => onChange({ supportedActionsText: Array.isArray(value) ? value.join(',') : String(value || '') })}
        >
          {mcpActionOptions.map((action) => (
            <Option key={action} value={action}>{action}</Option>
          ))}
        </Select>
        <Space wrap style={{ marginTop: 8 }}>
          {actionsFromText(form.supportedActionsText).map((action) => <Tag key={action}>{action}</Tag>)}
        </Space>
      </Field>
      <Field $full>
        <Typography.Text strong>描述</Typography.Text>
        <TextArea
          rows={3}
          value={form.description}
          onChange={(description) => onChange({ description })}
        />
      </Field>
      <Field $full>
        <Typography.Text type="tertiary" size="small">
          传输配置由系统根据资源类型生成。真实地址、密钥引用和可见对象在项目工作空间配置，不需要在模板里写 JSON。
        </Typography.Text>
      </Field>
    </FormGrid>
  </Modal>
);
