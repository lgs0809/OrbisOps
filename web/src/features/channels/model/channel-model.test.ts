import { describe, expect, it } from 'vitest';

import { buildChannelProviderCards } from './channel-model';

describe('channel provider cards', () => {
  it('never presents an unavailable native provider as ready', () => {
    const cards = buildChannelProviderCards([], []);
    expect(cards.find((item) => item.type === 'FEISHU')?.readiness).toBe('BLOCKED_EXTERNAL');
    expect(cards.find((item) => item.type === 'WECOM')?.readiness).toBe('BLOCKED_EXTERNAL');
    expect(cards.find((item) => item.type === 'SLACK')?.readiness).toBe('BLOCKED_EXTERNAL');
    expect(cards.find((item) => item.type === 'DINGTALK')?.readiness).toBe('BLOCKED_EXTERNAL');
  });

  it('treats Slack as an installed native adapter when the backend catalog exposes it', () => {
    const cards = buildChannelProviderCards([
      {
        type: 'SLACK', displayName: 'Slack', supportsInbound: true, supportsOutbound: true,
        connectionModes: ['LONG_CONNECTION'],
        capabilitySet: { capabilities: ['INBOUND', 'OUTBOUND', 'MESSAGE_UPDATE', 'INTERACTIVE_ACTIONS', 'LONG_CONNECTION'] },
      },
    ], []);

    const slack = cards.find((item) => item.type === 'SLACK');
    expect(slack?.adapterAvailable).toBe(true);
    expect(slack?.connectionMode).toBe('LONG_CONNECTION');
    expect(slack?.readiness).toBe('ACTION_REQUIRED');
    expect(slack?.actionRequired).toBe('Connect this provider to a project.');
  });

  it('treats DingTalk as an installed native adapter when the backend catalog exposes card/update capabilities', () => {
    const cards = buildChannelProviderCards([
      {
        type: 'DINGTALK', displayName: 'DingTalk', supportsInbound: true, supportsOutbound: true,
        connectionModes: ['LONG_CONNECTION'],
        capabilitySet: { capabilities: ['INBOUND', 'OUTBOUND', 'MESSAGE_UPDATE', 'INTERACTIVE_ACTIONS', 'LONG_CONNECTION', 'PROACTIVE_PUSH'] },
      },
    ], []);

    const dingTalk = cards.find((item) => item.type === 'DINGTALK');
    expect(dingTalk?.adapterAvailable).toBe(true);
    expect(dingTalk?.connectionMode).toBe('LONG_CONNECTION');
    expect(dingTalk?.readiness).toBe('ACTION_REQUIRED');
    expect(dingTalk?.actionRequired).toBe('Connect this provider to a project.');
  });

  it('derives capability fallback from the backend descriptor instead of provider-name assumptions', () => {
    const cards = buildChannelProviderCards([
      {
        type: 'TELEGRAM', displayName: 'Telegram', supportsInbound: true, supportsOutbound: true,
        connectionModes: ['LONG_CONNECTION'],
        capabilitySet: { capabilities: ['INBOUND', 'OUTBOUND', 'MESSAGE_UPDATE', 'INTERACTIVE_ACTIONS', 'PROACTIVE_PUSH'] },
      },
      {
        type: 'DISCORD', displayName: 'Discord', supportsInbound: true, supportsOutbound: true,
        connectionModes: ['LONG_CONNECTION'],
        capabilitySet: { capabilities: ['INBOUND', 'OUTBOUND', 'MESSAGE_UPDATE', 'INTERACTIVE_ACTIONS', 'PROACTIVE_PUSH'] },
      },
      {
        type: 'QQ', displayName: 'QQ', supportsInbound: true, supportsOutbound: true,
        connectionModes: ['LONG_CONNECTION'],
        capabilitySet: { capabilities: ['INBOUND', 'OUTBOUND', 'INTERACTIVE_ACTIONS', 'PROACTIVE_PUSH'] },
      },
    ], []);

    const telegram = cards.find((item) => item.type === 'TELEGRAM');
    const discord = cards.find((item) => item.type === 'DISCORD');
    const qq = cards.find((item) => item.type === 'QQ');
    expect(telegram?.capabilities.find((item) => item.key === 'APPROVAL')?.supported).toBe(true);
    expect(telegram?.capabilities.find((item) => item.key === 'ATTACHMENTS')?.supported).toBe(false);
    expect(discord?.capabilities.find((item) => item.key === 'UPDATE')?.supported).toBe(true);
    expect(discord?.capabilities.find((item) => item.key === 'ATTACHMENTS')?.supported).toBe(false);
    expect(qq?.capabilities.find((item) => item.key === 'APPROVAL')?.supported).toBe(true);
    expect(qq?.capabilities.find((item) => item.key === 'UPDATE')?.supported).toBe(false);
    expect(qq?.capabilities.find((item) => item.key === 'UPDATE')?.fallback).toContain('new messages');
  });

  it('treats WeChat as an installed secure-webhook adapter without inventing unsupported capabilities', () => {
    const cards = buildChannelProviderCards([
      {
        type: 'WECHAT', displayName: 'WeChat', supportsInbound: true, supportsOutbound: true,
        connectionModes: ['WEBHOOK'],
        capabilitySet: { capabilities: ['INBOUND', 'OUTBOUND', 'DIRECT_MESSAGES', 'WEBHOOK', 'REPLY_TO_INBOUND'] },
      },
    ], []);

    const wechat = cards.find((item) => item.type === 'WECHAT');
    expect(wechat?.adapterAvailable).toBe(true);
    expect(wechat?.connectionMode).toBe('WEBHOOK');
    expect(wechat?.readiness).toBe('ACTION_REQUIRED');
    expect(wechat?.capabilities.find((item) => item.key === 'INBOUND')?.supported).toBe(true);
    expect(wechat?.capabilities.find((item) => item.key === 'OUTBOUND')?.supported).toBe(true);
    expect(wechat?.capabilities.find((item) => item.key === 'APPROVAL')?.supported).toBe(false);
    expect(wechat?.capabilities.find((item) => item.key === 'UPDATE')?.supported).toBe(false);
    expect(wechat?.capabilities.find((item) => item.key === 'ATTACHMENTS')?.supported).toBe(false);
    expect(wechat?.capabilities.find((item) => item.key === 'PROACTIVE')?.supported).toBe(false);
  });

  it('keeps all capability dimensions unavailable when the provider adapter is absent', () => {
    const telegram = buildChannelProviderCards([], []).find((item) => item.type === 'TELEGRAM');
    expect(telegram?.capabilities).toHaveLength(6);
    expect(telegram?.capabilities.every((item) => item.supported === false)).toBe(true);
    expect(telegram?.capabilities[0]?.fallback).toContain('not available');
  });

  it('keeps a configured active provider action-required until readiness is probed', () => {
    const cards = buildChannelProviderCards([
      {
        type: 'GENERIC_WEBHOOK', displayName: 'Generic Webhook', supportsInbound: true, supportsOutbound: true,
        connectionModes: ['WEBHOOK'], capabilitySet: { capabilities: ['INBOUND', 'OUTBOUND'] },
      },
    ], [
      {
        channelId: 'channel-1', projectId: 'project-1', executionType: 'REACT', name: 'Webhook',
        type: 'GENERIC_WEBHOOK', credentialRef: '[credential-ref]', config: {},
        accessPolicy: 'DENY_UNKNOWN', status: 'ACTIVE',
      },
    ]);
    expect(cards.find((item) => item.type === 'GENERIC_WEBHOOK')?.readiness).toBe('ACTION_REQUIRED');
  });
});
