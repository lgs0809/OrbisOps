import {
  OpsMcpTemplate,
  OpsMcpTemplateRequest,
} from '../../services/ops-mcp-template-service';
import {
  McpTemplateFormState,
  mcpResourcePresets,
} from './types';
import { generatedId } from '../../utils/generated-id';

export const actionsFromText = (text: string) => text
  .split(',')
  .map((item) => item.trim())
  .filter(Boolean);

export const defaultTransportConfigFor = (resourceType: string) => ({
  serverTemplate: `${resourceType || 'custom'}-policy-mcp`,
  generatedBy: 'template-form',
});

export const applyResourcePreset = (resourceType: string): Partial<McpTemplateFormState> => {
  const preset = mcpResourcePresets[resourceType] || mcpResourcePresets.custom;
  return {
    resourceType,
    supportedActionsText: preset.actions.join(','),
    readOnly: preset.readOnly,
    riskLevel: preset.riskLevel,
  };
};

export const formFromTemplate = (template: OpsMcpTemplate): McpTemplateFormState => ({
  templateId: template.templateId,
  templateName: template.templateName,
  resourceType: template.resourceType || 'mysql',
  transportType: template.transportType || 'stdio',
  supportedActionsText: (template.supportedActions || []).join(','),
  riskLevel: template.riskLevel || 'LOW',
  readOnly: template.readOnly ? 'true' : 'false',
  description: template.description || '',
  status: template.status || 'ENABLED',
});

export const requestFromForm = (form: McpTemplateFormState): OpsMcpTemplateRequest => {
  const defaultTransportConfig = defaultTransportConfigFor(form.resourceType);
  return {
    templateId: form.templateId.trim() || generatedId('mcp-template', form.templateName),
    templateName: form.templateName.trim(),
    resourceType: form.resourceType,
    transportType: form.transportType,
    supportedActions: actionsFromText(form.supportedActionsText),
    riskLevel: form.riskLevel,
    readOnly: form.readOnly === 'true',
    description: form.description.trim(),
    status: form.status,
    defaultTransportConfig,
  };
};

export const riskColor = (risk?: string) => {
  if (risk === 'CRITICAL' || risk === 'HIGH') return 'red';
  if (risk === 'MEDIUM') return 'orange';
  if (risk === 'LOW') return 'green';
  return 'grey';
};
