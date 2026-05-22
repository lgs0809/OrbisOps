import { Select } from '@douyinfe/semi-ui';
import type { OpsSelectableModel } from '../../services/ops-admin-service';

interface Props {
  models: OpsSelectableModel[];
  value: string;
  disabled: boolean;
  onChange: (value: string) => void;
}

export function ChatModelSelector({ models, value, disabled, onChange }: Props) {
  const select = (next: unknown) => onChange(String(next || ''));
  return <Select size="small" aria-label="模型" disabled={disabled} value={value}
    // Semi defers controlled onChange until the closing animation finishes.
    // Capture the selection immediately so a following Send uses the chosen model.
    onSelect={select} onChange={select}>
    <Select.Option value="">默认模型</Select.Option>
    {models.map((model) => <Select.Option key={model.modelId} value={model.modelId}>
      {model.modelName || model.modelId}
      {models.filter((item) => item.modelName === model.modelName).length > 1
        ? ` · ${model.description || model.modelId}` : ''}
    </Select.Option>)}
  </Select>;
}
