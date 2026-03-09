import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

describe('workflow agent execution modes', () => {
  it('keeps DIRECT, LLM and REACT as the only canonical execution modes', () => {
    const source = readFileSync(resolve(process.cwd(), 'src/pages/agent-config.tsx'), 'utf8');

    expect(source).toContain("{ value: 'direct', label: 'DIRECT");
    expect(source).toContain("{ value: 'llm', label: 'LLM");
    expect(source).toContain("{ value: 'react', label: 'REACT");
    expect(source).not.toContain("{ value: 'review', label: 'REVIEW");
    expect(source).toContain("if (value === 'review') return 'llm';");
    expect(source).toContain('const LLM_ROLE_TEMPLATES = [');
  });
});
