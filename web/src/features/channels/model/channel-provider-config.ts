import type {
  OpsChannelConnectionMode,
  OpsChannelType,
} from '../../../services/ops-channel-service';

export interface ChannelProviderFormConfig {
  providerType: OpsChannelType;
  appId: string;
  botId: string;
  corpId?: string;
  robotCode?: string;
  cardTemplateId?: string;
  secondaryCredentialEnvironmentVariable?: string;
  connectionMode: OpsChannelConnectionMode;
  requireMention: boolean;
  respondToMentionAll: boolean;
  outboundUrl: string;
  timeoutSeconds: number;
}

export const defaultConnectionMode = (type: OpsChannelType): OpsChannelConnectionMode => (
  type === 'GENERIC_WEBHOOK' ? 'WEBHOOK' : 'LONG_CONNECTION'
);

export const credentialEnvironmentName = (name: string, type: OpsChannelType): string => {
  const base = (name || 'OPS_CHANNEL').toUpperCase().replace(/[^A-Z0-9]+/g, '_').replace(/^_+|_+$/g, '');
  const suffix = type === 'FEISHU'
    ? 'FEISHU_APP_CREDENTIAL'
    : type === 'WECOM'
      ? 'WECOM_BOT_CREDENTIAL'
      : type === 'SLACK'
        ? 'SLACK_BOT_CREDENTIAL'
        : type === 'DINGTALK'
          ? 'DINGTALK_APP_CREDENTIAL'
          : 'WEBHOOK_CREDENTIAL';
  return `${base}_${suffix}`;
};

export const secondaryCredentialEnvironmentName = (name: string, type: OpsChannelType): string => {
  const base = (name || 'OPS_CHANNEL').toUpperCase().replace(/[^A-Z0-9]+/g, '_').replace(/^_+|_+$/g, '');
  return type === 'SLACK' ? `${base}_SLACK_APP_CREDENTIAL` : `${base}_SECONDARY_CREDENTIAL`;
};

export const providerConfigPayload = (config: ChannelProviderFormConfig): Record<string, unknown> => {
  if (config.providerType === 'FEISHU') {
    return {
      appId: config.appId.trim(),
      connectionMode: config.connectionMode,
      requireMention: config.requireMention,
      respondToMentionAll: config.respondToMentionAll,
    };
  }
  if (config.providerType === 'WECOM') {
    return {
      botId: config.botId.trim(),
      connectionMode: config.connectionMode,
    };
  }
  if (config.providerType === 'SLACK') {
    return {
      connectionMode: config.connectionMode,
      requireMention: config.requireMention,
    };
  }
  if (config.providerType === 'DINGTALK') {
    return {
      clientId: config.appId.trim(),
      corpId: (config.corpId || '').trim(),
      robotCode: (config.robotCode || '').trim(),
      cardTemplateId: (config.cardTemplateId || '').trim(),
      connectionMode: config.connectionMode,
    };
  }
  return {
    outboundUrl: config.outboundUrl.trim(),
    timeoutSeconds: Number(config.timeoutSeconds) || 15,
  };
};

export const validateProviderConfig = (config: ChannelProviderFormConfig): string | null => {
  if (config.providerType === 'FEISHU' && !config.appId.trim()) return '请填写 Feishu App ID';
  if (config.providerType === 'WECOM' && !config.botId.trim()) return '请填写 WeCom Bot ID';
  if (config.providerType === 'GENERIC_WEBHOOK' && config.connectionMode !== 'WEBHOOK') return 'Generic Webhook 只支持 Webhook 模式';
  if (config.providerType === 'WECOM' && !['LONG_CONNECTION', 'HYBRID'].includes(config.connectionMode)) return 'WeCom 当前仅支持长连接模式';
  if (config.providerType === 'SLACK' && !['LONG_CONNECTION', 'HYBRID'].includes(config.connectionMode)) return 'Slack 当前仅支持 Socket Mode 长连接';
  if (config.providerType === 'DINGTALK' && !config.appId.trim()) return '请填写 DingTalk Client ID';
  if (config.providerType === 'DINGTALK' && !(config.corpId || '').trim()) return '请填写 DingTalk Corp ID';
  if (config.providerType === 'DINGTALK' && !(config.robotCode || '').trim()) return '请填写 DingTalk Robot Code';
  if (config.providerType === 'DINGTALK' && !(config.cardTemplateId || '').trim()) return '请填写 DingTalk Card Template ID';
  if (config.providerType === 'DINGTALK' && !['LONG_CONNECTION', 'HYBRID'].includes(config.connectionMode)) return 'DingTalk 当前仅支持 Stream 长连接';
  return null;
};
