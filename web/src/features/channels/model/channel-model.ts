import type {
  OpsChannel,
  OpsChannelAccessPolicy,
  OpsChannelProtocolDescriptor,
  OpsChannelType,
} from '../../../services/ops-channel-service';

export type ChannelReadinessState = 'READY' | 'ACTION_REQUIRED' | 'BLOCKED_EXTERNAL' | 'DISABLED';

export interface ChannelProviderCardModel {
  type: OpsChannelType;
  name: string;
  phase: 0 | 1 | 2 | 3 | 4;
  configured: boolean;
  adapterAvailable: boolean;
  readiness: ChannelReadinessState;
  connectionMode: string;
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
    return {
      ...provider,
      configured,
      adapterAvailable,
      readiness,
      connectionMode: descriptor?.connectionModes?.join(' / ') || 'Not installed',
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
