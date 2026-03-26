import type {
  OpsChannel,
  OpsChannelAccessPolicy,
  OpsChannelProtocolDescriptor,
  OpsChannelType,
} from '../../../services/ops-channel-service';

export type ChannelReadinessState = 'READY' | 'ACTION_REQUIRED' | 'BLOCKED_EXTERNAL' | 'DISABLED';

export type ChannelCapabilityDimension = 'INBOUND' | 'OUTBOUND' | 'APPROVAL' | 'UPDATE' | 'ATTACHMENTS' | 'PROACTIVE';

export interface ChannelCapabilityView {
  key: ChannelCapabilityDimension;
  label: string;
  supported: boolean;
  fallback: string;
}

export interface ChannelProviderCardModel {
  type: OpsChannelType;
  name: string;
  phase: 0 | 1 | 2 | 3 | 4;
  configured: boolean;
  adapterAvailable: boolean;
  readiness: ChannelReadinessState;
  connectionMode: string;
  capabilities: ChannelCapabilityView[];
  actionRequired?: string;
}

export const CHANNEL_ACCESS_POLICY_OPTIONS: Array<{
  value: OpsChannelAccessPolicy;
  label: string;
  description: string;
}> = [
  { value: 'DENY_UNKNOWN', label: 'Deny unknown', description: '未绑定发送人默认拒绝，适合生产环境。' },
  { value: 'PAIRING', label: 'Pairing', description: '未绑定发送人只收到绑定提示，不进入 Agent Runtime。' },
  { value: 'ALLOWLIST', label: 'Allowlist', description: '仅显式绑定或授权身份允许进入 Runtime。' },
  { value: 'OBSERVE_ONLY_UNKNOWN', label: 'Observe only unknown', description: '未知发送人可调查，但 Runtime 被硬降权为只读。' },
];

const capabilityDimensions: Array<{
  key: ChannelCapabilityDimension;
  label: string;
  backendCapability: string;
  fallback: string;
}> = [
  { key: 'INBOUND', label: 'Inbound', backendCapability: 'INBOUND', fallback: 'This provider cannot receive messages in the current build.' },
  { key: 'OUTBOUND', label: 'Outbound', backendCapability: 'OUTBOUND', fallback: 'This provider cannot deliver outbound messages in the current build.' },
  { key: 'APPROVAL', label: 'Approval', backendCapability: 'INTERACTIVE_ACTIONS', fallback: 'Approval actions fall back to the OrbisOps web approval surface.' },
  { key: 'UPDATE', label: 'Live update', backendCapability: 'MESSAGE_UPDATE', fallback: 'Progress is sent as new messages instead of editing the original message.' },
  { key: 'ATTACHMENTS', label: 'Files', backendCapability: 'ATTACHMENTS', fallback: 'File ingestion is unavailable for this provider.' },
  { key: 'PROACTIVE', label: 'Proactive', backendCapability: 'PROACTIVE_PUSH', fallback: 'Only reply-scoped delivery is available.' },
];

const providers: Array<{ type: OpsChannelType; name: string; phase: 0 | 1 | 2 | 3 | 4 }> = [
  { type: 'FEISHU', name: 'Feishu / Lark', phase: 1 },
  { type: 'WECOM', name: 'WeCom', phase: 1 },
  { type: 'DINGTALK', name: 'DingTalk', phase: 2 },
  { type: 'SLACK', name: 'Slack', phase: 2 },
  { type: 'TELEGRAM', name: 'Telegram', phase: 3 },
  { type: 'DISCORD', name: 'Discord', phase: 3 },
  { type: 'QQ', name: 'QQ', phase: 3 },
  { type: 'WECHAT', name: 'WeChat', phase: 4 },
  { type: 'GENERIC_WEBHOOK', name: 'Generic Webhook', phase: 0 },
];

export const buildChannelProviderCards = (
  descriptors: OpsChannelProtocolDescriptor[],
  channels: OpsChannel[],
): ChannelProviderCardModel[] => {
  const descriptorByType = new Map(descriptors.map((item) => [item.type, item]));
  return providers.map((provider) => {
    const descriptor = descriptorByType.get(provider.type);
    const configuredChannels = channels.filter((item) => item.type === provider.type);
    const active = configuredChannels.find((item) => item.status === 'ACTIVE');
    const configured = configuredChannels.length > 0;
    const adapterAvailable = Boolean(descriptor);
    const readiness: ChannelReadinessState = !adapterAvailable
      ? 'BLOCKED_EXTERNAL'
      : active
        ? 'ACTION_REQUIRED'
        : configured
          ? 'DISABLED'
          : 'ACTION_REQUIRED';
    const actualCapabilities = new Set(descriptor?.capabilitySet?.capabilities || []);
    const capabilities = capabilityDimensions.map((dimension) => ({
      key: dimension.key,
      label: dimension.label,
      supported: adapterAvailable && actualCapabilities.has(dimension.backendCapability),
      fallback: adapterAvailable
        ? dimension.fallback
        : 'Provider adapter is not available in the current build.',
    }));
    return {
      ...provider,
      configured,
      adapterAvailable,
      readiness,
      connectionMode: descriptor?.connectionModes?.join(' / ') || 'Not installed',
      capabilities,
      actionRequired: !adapterAvailable
        ? `Provider adapter is not available in the current build (Phase ${provider.phase}).`
        : active
          ? 'Run readiness checks for credentials, connection, inbound, outbound, and identity/access.'
          : configured
            ? 'Enable the provider and run readiness checks.'
            : 'Connect this provider to a project.',
    };
  });
};

export const channelReadinessLabel = (state: ChannelReadinessState): string => ({
  READY: 'Ready',
  ACTION_REQUIRED: 'Action required',
  BLOCKED_EXTERNAL: 'Blocked external',
  DISABLED: 'Disabled',
}[state]);
