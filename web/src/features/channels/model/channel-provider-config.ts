import type {
  OpsChannelConnectionMode,
  OpsChannelType,
} from '../../../services/ops-channel-service';

export interface ChannelProviderFormConfig {
  providerType: OpsChannelType;
  appId: string;
  botId: string;
  botUsername?: string;
  messageContentIntent?: boolean;
  corpId?: string;
  robotCode?: string;
  cardTemplateId?: string;
  secondaryCredentialEnvironmentVariable?: string;
  verificationTokenRef?: string;
  encodingAesKeyRef?: string;
  connectionMode: OpsChannelConnectionMode;
  requireMention: boolean;
  respondToMentionAll: boolean;
  outboundUrl: string;
  timeoutSeconds: number;
}

export const defaultConnectionMode = (type: OpsChannelType): OpsChannelConnectionMode => (
  type === 'GENERIC_WEBHOOK' || type === 'WECHAT' ? 'WEBHOOK' : 'LONG_CONNECTION'
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
          : type === 'TELEGRAM'
            ? 'TELEGRAM_BOT_CREDENTIAL'
            : type === 'DISCORD'
              ? 'DISCORD_BOT_CREDENTIAL'
              : type === 'QQ'
                ? 'QQ_APP_CREDENTIAL'
                : type === 'WECHAT'
                  ? 'WECHAT_APP_SECRET'
                  : 'WEBHOOK_CREDENTIAL';
  return `${base}_${suffix}`;
};

export const secondaryCredentialEnvironmentName = (name: string, type: OpsChannelType): string => {
  const base = (name || 'OPS_CHANNEL').toUpperCase().replace(/[^A-Z0-9]+/g, '_').replace(/^_+|_+$/g, '');
  return type === 'SLACK' ? `${base}_SLACK_APP_CREDENTIAL` : `${base}_SECONDARY_CREDENTIAL`;
};

export const verificationTokenEnvironmentName = (name: string): string => {
  const base = (name || 'OPS_CHANNEL').toUpperCase().replace(/[^A-Z0-9]+/g, '_').replace(/^_+|_+$/g, '');
  return `${base}_WECHAT_VERIFY_TOKEN`;
};

export const encodingAesKeyEnvironmentName = (name: string): string => {
  const base = (name || 'OPS_CHANNEL').toUpperCase().replace(/[^A-Z0-9]+/g, '_').replace(/^_+|_+$/g, '');
  return `${base}_WECHAT_ENCODING_AES_KEY`;
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
  if (config.providerType === 'TELEGRAM') {
    return {
      botUsername: (config.botUsername || '').trim().replace(/^@+/, ''),
      connectionMode: config.connectionMode,
      requireMention: config.requireMention,
    };
  }
  if (config.providerType === 'DISCORD') {
    return {
      connectionMode: config.connectionMode,
      requireMention: config.requireMention,
      messageContentIntent: Boolean(config.messageContentIntent),
    };
  }
  if (config.providerType === 'QQ') {
    return {
      appId: config.appId.trim(),
      connectionMode: config.connectionMode,
    };
  }
  if (config.providerType === 'WECHAT') {
    return {
      appId: config.appId.trim(),
      connectionMode: config.connectionMode,
      verificationTokenRef: (config.verificationTokenRef || '').trim(),
      encodingAesKeyRef: (config.encodingAesKeyRef || '').trim(),
    };
  }
  return {
    outboundUrl: config.outboundUrl.trim(),
    timeoutSeconds: Number(config.timeoutSeconds) || 15,
  };
};

export const validateProviderConfig = (config: ChannelProviderFormConfig): string | null => {
  if (config.providerType === 'FEISHU' && !config.appId.trim()) return '请填写 Feishu App ID';
  if (config.providerType === 'FEISHU' && config.connectionMode !== 'LONG_CONNECTION') return 'Feishu 当前仅支持 WebSocket 长连接';
  if (config.providerType === 'WECOM' && !config.botId.trim()) return '请填写 WeCom Bot ID';
  if (config.providerType === 'GENERIC_WEBHOOK' && config.connectionMode !== 'WEBHOOK') return 'Generic Webhook 只支持 Webhook 模式';
  if (config.providerType === 'WECOM' && config.connectionMode !== 'LONG_CONNECTION') return 'WeCom 当前仅支持长连接模式';
  if (config.providerType === 'SLACK' && config.connectionMode !== 'LONG_CONNECTION') return 'Slack 当前仅支持 Socket Mode 长连接';
  if (config.providerType === 'DINGTALK' && !config.appId.trim()) return '请填写 DingTalk Client ID';
  if (config.providerType === 'DINGTALK' && !(config.corpId || '').trim()) return '请填写 DingTalk Corp ID';
  if (config.providerType === 'DINGTALK' && !(config.robotCode || '').trim()) return '请填写 DingTalk Robot Code';
  if (config.providerType === 'DINGTALK' && !(config.cardTemplateId || '').trim()) return '请填写 DingTalk Card Template ID';
  if (config.providerType === 'DINGTALK' && config.connectionMode !== 'LONG_CONNECTION') return 'DingTalk 当前仅支持 Stream 长连接';
  if (config.providerType === 'TELEGRAM' && config.connectionMode !== 'LONG_CONNECTION') return 'Telegram 当前仅支持 Bot API long polling';
  if (config.providerType === 'TELEGRAM' && config.requireMention && !(config.botUsername || '').trim()) return '群聊要求 @Bot 时请填写 Telegram Bot Username';
  if (config.providerType === 'DISCORD' && config.connectionMode !== 'LONG_CONNECTION') return 'Discord 当前仅支持 Gateway 长连接';
  if (config.providerType === 'DISCORD' && !config.requireMention && !config.messageContentIntent) return '不要求 @Bot 时必须启用 Discord MESSAGE_CONTENT privileged intent';
  if (config.providerType === 'QQ' && !config.appId.trim()) return '请填写 QQ Bot AppID';
  if (config.providerType === 'QQ' && config.connectionMode !== 'LONG_CONNECTION') return 'QQ 当前仅支持 Gateway 长连接';
  if (config.providerType === 'WECHAT' && !config.appId.trim()) return '请填写 WeChat Official Account AppID';
  if (config.providerType === 'WECHAT' && config.connectionMode !== 'WEBHOOK') return 'WeChat Official Account 当前仅支持 Secure Webhook';
  return null;
};
