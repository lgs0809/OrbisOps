import { describe, expect, it } from 'vitest';

import {
  credentialEnvironmentName,
  defaultConnectionMode,
  providerConfigPayload,
  secondaryCredentialEnvironmentName,
  validateProviderConfig,
} from './channel-provider-config';

describe('channel provider configuration', () => {
  it('uses provider-specific connection defaults and credential references', () => {
    expect(defaultConnectionMode('GENERIC_WEBHOOK')).toBe('WEBHOOK');
    expect(defaultConnectionMode('FEISHU')).toBe('LONG_CONNECTION');
    expect(defaultConnectionMode('WECOM')).toBe('LONG_CONNECTION');
    expect(defaultConnectionMode('SLACK')).toBe('LONG_CONNECTION');
    expect(defaultConnectionMode('DINGTALK')).toBe('LONG_CONNECTION');
    expect(credentialEnvironmentName('Production Oncall', 'FEISHU')).toBe('PRODUCTION_ONCALL_FEISHU_APP_CREDENTIAL');
    expect(credentialEnvironmentName('Production Oncall', 'DINGTALK')).toBe('PRODUCTION_ONCALL_DINGTALK_APP_CREDENTIAL');
    expect(credentialEnvironmentName('Production Oncall', 'SLACK')).toBe('PRODUCTION_ONCALL_SLACK_BOT_CREDENTIAL');
    expect(secondaryCredentialEnvironmentName('Production Oncall', 'SLACK')).toBe('PRODUCTION_ONCALL_SLACK_APP_CREDENTIAL');
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
  });
});
