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
