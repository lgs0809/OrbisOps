import { describe, expect, it } from 'vitest';

import {
  credentialEnvironmentName,
  defaultConnectionMode,
  encodingAesKeyEnvironmentName,
  providerConfigPayload,
  secondaryCredentialEnvironmentName,
  validateProviderConfig,
  verificationTokenEnvironmentName,
} from './channel-provider-config';

describe('channel provider configuration', () => {
  it('uses provider-specific connection defaults and credential references', () => {
    expect(defaultConnectionMode('GENERIC_WEBHOOK')).toBe('WEBHOOK');
    expect(defaultConnectionMode('FEISHU')).toBe('LONG_CONNECTION');
    expect(defaultConnectionMode('WECOM')).toBe('LONG_CONNECTION');
    expect(defaultConnectionMode('SLACK')).toBe('LONG_CONNECTION');
    expect(defaultConnectionMode('DINGTALK')).toBe('LONG_CONNECTION');
    expect(defaultConnectionMode('TELEGRAM')).toBe('LONG_CONNECTION');
    expect(defaultConnectionMode('DISCORD')).toBe('LONG_CONNECTION');
    expect(defaultConnectionMode('QQ')).toBe('LONG_CONNECTION');
    expect(defaultConnectionMode('WECHAT')).toBe('WEBHOOK');
    expect(credentialEnvironmentName('Production Oncall', 'FEISHU')).toBe('PRODUCTION_ONCALL_FEISHU_APP_CREDENTIAL');
    expect(credentialEnvironmentName('Production Oncall', 'DINGTALK')).toBe('PRODUCTION_ONCALL_DINGTALK_APP_CREDENTIAL');
    expect(credentialEnvironmentName('Production Oncall', 'SLACK')).toBe('PRODUCTION_ONCALL_SLACK_BOT_CREDENTIAL');
    expect(credentialEnvironmentName('Production Oncall', 'TELEGRAM')).toBe('PRODUCTION_ONCALL_TELEGRAM_BOT_CREDENTIAL');
    expect(credentialEnvironmentName('Production Oncall', 'DISCORD')).toBe('PRODUCTION_ONCALL_DISCORD_BOT_CREDENTIAL');
    expect(credentialEnvironmentName('Production Oncall', 'QQ')).toBe('PRODUCTION_ONCALL_QQ_APP_CREDENTIAL');
    expect(credentialEnvironmentName('Production Oncall', 'WECHAT')).toBe('PRODUCTION_ONCALL_WECHAT_APP_SECRET');
    expect(secondaryCredentialEnvironmentName('Production Oncall', 'SLACK')).toBe('PRODUCTION_ONCALL_SLACK_APP_CREDENTIAL');
    expect(verificationTokenEnvironmentName('Production Oncall')).toBe('PRODUCTION_ONCALL_WECHAT_VERIFY_TOKEN');
    expect(encodingAesKeyEnvironmentName('Production Oncall')).toBe('PRODUCTION_ONCALL_WECHAT_ENCODING_AES_KEY');
  });

  it('projects only provider-owned configuration fields', () => {
    expect(providerConfigPayload({
      providerType: 'FEISHU', appId: 'cli-1', botId: '', connectionMode: 'LONG_CONNECTION',
      requireMention: true, respondToMentionAll: false, outboundUrl: 'https://ignored.example', timeoutSeconds: 15,
    })).toEqual({ appId: 'cli-1', connectionMode: 'LONG_CONNECTION', requireMention: true, respondToMentionAll: false });

    expect(providerConfigPayload({
      providerType: 'WECOM', appId: '', botId: 'bot-1', connectionMode: 'LONG_CONNECTION',
      requireMention: false, respondToMentionAll: false, outboundUrl: '', timeoutSeconds: 15,
    })).toEqual({ botId: 'bot-1', connectionMode: 'LONG_CONNECTION' });

    expect(providerConfigPayload({
      providerType: 'SLACK', appId: '', botId: '', connectionMode: 'LONG_CONNECTION',
      requireMention: true, respondToMentionAll: false, outboundUrl: 'https://ignored.example', timeoutSeconds: 15,
    })).toEqual({ connectionMode: 'LONG_CONNECTION', requireMention: true });

    expect(providerConfigPayload({
      providerType: 'DINGTALK', appId: 'ding-client-1', botId: '', corpId: 'corp-1', robotCode: 'robot-1', cardTemplateId: 'template-1.schema', connectionMode: 'LONG_CONNECTION',
      requireMention: true, respondToMentionAll: false, outboundUrl: 'https://ignored.example', timeoutSeconds: 15,
    })).toEqual({ clientId: 'ding-client-1', corpId: 'corp-1', robotCode: 'robot-1', cardTemplateId: 'template-1.schema', connectionMode: 'LONG_CONNECTION' });

    expect(providerConfigPayload({
      providerType: 'TELEGRAM', appId: '', botId: '', botUsername: '@orbisops_bot', connectionMode: 'LONG_CONNECTION',
      requireMention: true, respondToMentionAll: false, outboundUrl: 'https://ignored.example', timeoutSeconds: 15,
    })).toEqual({ botUsername: 'orbisops_bot', connectionMode: 'LONG_CONNECTION', requireMention: true });

    expect(providerConfigPayload({
      providerType: 'DISCORD', appId: '', botId: '', connectionMode: 'LONG_CONNECTION', messageContentIntent: false,
      requireMention: true, respondToMentionAll: false, outboundUrl: 'https://ignored.example', timeoutSeconds: 15,
    })).toEqual({ connectionMode: 'LONG_CONNECTION', requireMention: true, messageContentIntent: false });

    expect(providerConfigPayload({
      providerType: 'QQ', appId: '102000001', botId: '', connectionMode: 'LONG_CONNECTION',
      requireMention: true, respondToMentionAll: false, outboundUrl: 'https://ignored.example', timeoutSeconds: 15,
    })).toEqual({ appId: '102000001', connectionMode: 'LONG_CONNECTION' });

    expect(providerConfigPayload({
      providerType: 'WECHAT', appId: 'wx-test', botId: '', connectionMode: 'WEBHOOK',
      verificationTokenRef: '[REDACTED_SECRET]', encodingAesKeyRef: '[REDACTED_SECRET]',
      requireMention: false, respondToMentionAll: false, outboundUrl: '', timeoutSeconds: 15,
    })).toEqual({
      appId: 'wx-test', connectionMode: 'WEBHOOK', verificationTokenRef: '[REDACTED_SECRET]', encodingAesKeyRef: '[REDACTED_SECRET]',
    });
  });

  it('fails closed on missing native provider identifiers', () => {
    expect(validateProviderConfig({
      providerType: 'FEISHU', appId: '', botId: '', connectionMode: 'LONG_CONNECTION',
      requireMention: true, respondToMentionAll: false, outboundUrl: '', timeoutSeconds: 15,
    })).toContain('App ID');
    expect(validateProviderConfig({
      providerType: 'WECOM', appId: '', botId: '', connectionMode: 'LONG_CONNECTION',
      requireMention: false, respondToMentionAll: false, outboundUrl: '', timeoutSeconds: 15,
    })).toContain('Bot ID');
    expect(validateProviderConfig({
      providerType: 'DINGTALK', appId: '', botId: '', corpId: '', robotCode: '', cardTemplateId: '', connectionMode: 'LONG_CONNECTION',
      requireMention: false, respondToMentionAll: false, outboundUrl: '', timeoutSeconds: 15,
    })).toContain('Client ID');
    expect(validateProviderConfig({
      providerType: 'DINGTALK', appId: 'ding-client-1', botId: '', corpId: 'corp-1', robotCode: 'robot-1', cardTemplateId: '', connectionMode: 'LONG_CONNECTION',
      requireMention: false, respondToMentionAll: false, outboundUrl: '', timeoutSeconds: 15,
    })).toContain('Card Template ID');
    expect(validateProviderConfig({
      providerType: 'TELEGRAM', appId: '', botId: '', botUsername: '', connectionMode: 'LONG_CONNECTION',
      requireMention: true, respondToMentionAll: false, outboundUrl: '', timeoutSeconds: 15,
    })).toContain('Bot Username');
    expect(validateProviderConfig({
      providerType: 'TELEGRAM', appId: '', botId: '', botUsername: 'orbisops_bot', connectionMode: 'WEBHOOK',
      requireMention: false, respondToMentionAll: false, outboundUrl: '', timeoutSeconds: 15,
    })).toContain('long polling');
    expect(validateProviderConfig({
      providerType: 'DISCORD', appId: '', botId: '', connectionMode: 'LONG_CONNECTION', messageContentIntent: false,
      requireMention: false, respondToMentionAll: false, outboundUrl: '', timeoutSeconds: 15,
    })).toContain('MESSAGE_CONTENT');
    expect(validateProviderConfig({
      providerType: 'DISCORD', appId: '', botId: '', connectionMode: 'WEBHOOK', messageContentIntent: true,
      requireMention: false, respondToMentionAll: false, outboundUrl: '', timeoutSeconds: 15,
    })).toContain('Gateway');
    expect(validateProviderConfig({
      providerType: 'QQ', appId: '', botId: '', connectionMode: 'LONG_CONNECTION',
      requireMention: true, respondToMentionAll: false, outboundUrl: '', timeoutSeconds: 15,
    })).toContain('AppID');
    expect(validateProviderConfig({
      providerType: 'QQ', appId: '102000001', botId: '', connectionMode: 'WEBHOOK',
      requireMention: true, respondToMentionAll: false, outboundUrl: '', timeoutSeconds: 15,
    })).toContain('Gateway');
    expect(validateProviderConfig({
      providerType: 'WECHAT', appId: '', botId: '', connectionMode: 'WEBHOOK',
      requireMention: false, respondToMentionAll: false, outboundUrl: '', timeoutSeconds: 15,
    })).toContain('AppID');
    expect(validateProviderConfig({
      providerType: 'WECHAT', appId: 'wx-test', botId: '', connectionMode: 'LONG_CONNECTION',
      requireMention: false, respondToMentionAll: false, outboundUrl: '', timeoutSeconds: 15,
    })).toContain('Secure Webhook');
  });

  it('rejects HYBRID for providers that only advertise native long-connection ingress', () => {
    const cases = [
      { providerType: 'FEISHU' as const, appId: 'cli-1', botId: '' },
      { providerType: 'WECOM' as const, appId: '', botId: 'bot-1' },
      { providerType: 'DINGTALK' as const, appId: 'ding-1', botId: '', corpId: 'corp-1', robotCode: 'robot-1', cardTemplateId: 'template.schema' },
      { providerType: 'SLACK' as const, appId: '', botId: '' },
      { providerType: 'TELEGRAM' as const, appId: '', botId: '', botUsername: 'orbisops_bot' },
      { providerType: 'DISCORD' as const, appId: '', botId: '', messageContentIntent: false },
      { providerType: 'QQ' as const, appId: '102000001', botId: '' },
    ];

    cases.forEach((provider) => {
      expect(validateProviderConfig({
        ...provider,
        connectionMode: 'HYBRID',
        requireMention: true,
        respondToMentionAll: false,
        outboundUrl: '',
        timeoutSeconds: 15,
      })).not.toBeNull();
    });
  });
});
