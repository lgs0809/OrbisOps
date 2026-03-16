import { describe, expect, it } from 'vitest';

import {
  agentBuilderMode,
  agentBuilderModeDefinition,
  agentBuilderPanelForMode,
  isAgentBuilderAdvanced,
} from './agent-builder-mode';

describe('agent builder mode policy', () => {
  it('defaults unknown values to basic and keeps advanced explicit', () => {
    expect(agentBuilderMode(undefined)).toBe('basic');
    expect(agentBuilderMode('anything')).toBe('basic');
    expect(agentBuilderMode('advanced')).toBe('advanced');
  });

  it('keeps low-level panels out of basic mode', () => {
    expect(agentBuilderModeDefinition('basic').panels).toEqual(['node', 'test']);
    expect(agentBuilderPanelForMode('basic', 'edge')).toBe('node');
    expect(agentBuilderPanelForMode('basic', 'json')).toBe('node');
    expect(agentBuilderPanelForMode('advanced', 'edge')).toBe('edge');
    expect(agentBuilderPanelForMode('advanced', 'json')).toBe('json');
  });

  it('exposes a single advanced-mode predicate for page policy', () => {
    expect(isAgentBuilderAdvanced('basic')).toBe(false);
    expect(isAgentBuilderAdvanced('advanced')).toBe(true);
  });
});
