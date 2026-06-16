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

export type ChannelCompatibilityCapability =
  | 'INBOUND'
  | 'OUTBOUND'
  | 'DIRECT_MESSAGES'
  | 'GROUP_MESSAGES'
  | 'REPLY_TO_INBOUND'
  | 'INTERACTIVE_ACTIONS'
  | 'MESSAGE_UPDATE'
  | 'ATTACHMENTS'
  | 'PROACTIVE_PUSH';

export interface ChannelCompatibilityRow {
  type: OpsChannelType;
  name: string;
  installed: boolean;
  connectionMode: string;
  capabilities: Record<ChannelCompatibilityCapability, boolean>;
}

export const CHANNEL_ACCESS_POLICY_OPTIONS: Array<{
  value: OpsChannelAccessPolicy;
  label: string;
  description: string;
}> = [
  { value: 'DENY_UNKNOWN', label: '拒绝未知身份', description: '默认拒绝未绑定身份，适合生产环境。' },
  { value: 'PAIRING', label: '首次配对', description: '未绑定身份先收到配对指引，完成绑定后才能进入 Agent Runtime。' },
  { value: 'ALLOWLIST', label: '白名单', description: '仅允许显式绑定或已授权的身份进入 Runtime。' },
  { value: 'OBSERVE_ONLY_UNKNOWN', label: '未知身份仅观察', description: '允许未知身份发起只读排查，不授予变更权限。' },
];

const capabilityDimensions: Array<{
  key: ChannelCapabilityDimension;
  label: string;
  backendCapability: string;
  fallback: string;
}> = [
  { key: 'INBOUND', label: '接收消息', backendCapability: 'INBOUND', fallback: '当前版本暂不支持通过该渠道接收消息。' },
  { key: 'OUTBOUND', label: '发送消息', backendCapability: 'OUTBOUND', fallback: '当前版本暂不支持通过该渠道发送消息。' },
  { key: 'APPROVAL', label: '审批交互', backendCapability: 'INTERACTIVE_ACTIONS', fallback: '该渠道不支持审批交互，请在 OrbisOps 网页中处理。' },
  { key: 'UPDATE', label: '进度更新', backendCapability: 'MESSAGE_UPDATE', fallback: '该渠道不支持编辑原消息，进度会以新消息发送。' },
  { key: 'ATTACHMENTS', label: '文件', backendCapability: 'ATTACHMENTS', fallback: '该渠道暂不支持文件接入。' },
  { key: 'PROACTIVE', label: '主动推送', backendCapability: 'PROACTIVE_PUSH', fallback: '该渠道仅支持在已有会话中回复。' },
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

const compatibilityCapabilities: ChannelCompatibilityCapability[] = [
  'INBOUND',
  'OUTBOUND',
  'DIRECT_MESSAGES',
  'GROUP_MESSAGES',
  'REPLY_TO_INBOUND',
  'INTERACTIVE_ACTIONS',
  'MESSAGE_UPDATE',
  'ATTACHMENTS',
  'PROACTIVE_PUSH',
];

export const buildChannelCompatibilityMatrix = (
  descriptors: OpsChannelProtocolDescriptor[],
): ChannelCompatibilityRow[] => {
  const descriptorByType = new Map(descriptors.map((item) => [item.type, item]));
  return providers.map((provider) => {
    const descriptor = descriptorByType.get(provider.type);
    const supported = new Set(descriptor?.capabilitySet?.capabilities || []);
    return {
      type: provider.type,
      name: provider.name,
      installed: Boolean(descriptor),
      connectionMode: descriptor?.connectionModes?.join(' / ') || '未安装',
      capabilities: Object.fromEntries(
        compatibilityCapabilities.map((capability) => [capability, Boolean(descriptor && supported.has(capability))]),
      ) as Record<ChannelCompatibilityCapability, boolean>,
    };
  });
};

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
        : '当前版本未安装该渠道能力。',
    }));
    return {
      ...provider,
      configured,
      adapterAvailable,
      readiness,
      connectionMode: descriptor?.connectionModes?.join(' / ') || '未安装',
      capabilities,
      actionRequired: !adapterAvailable
        ? '当前版本未安装该渠道。'
        : active
          ? '请检查凭据、连接、收发消息和身份访问状态。'
          : configured
            ? '请先启用该渠道并执行就绪检查。'
            : '请先把该渠道接入 Project。',
    };
  });
};

export const channelReadinessLabel = (state: ChannelReadinessState): string => ({
  READY: '已就绪',
  ACTION_REQUIRED: '需要处理',
  BLOCKED_EXTERNAL: '外部条件阻塞',
  DISABLED: '已停用',
}[state]);
