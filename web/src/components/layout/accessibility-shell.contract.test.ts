import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

describe('global accessibility shell contract', () => {
  it('keeps skip navigation, main focus target, labeled navigation, and accessible header triggers', () => {
    const shell = readFileSync(resolve(process.cwd(), 'src/components/ops-layout/OpsPageShell.tsx'), 'utf8');
    const header = readFileSync(resolve(process.cwd(), 'src/components/layout/Header.tsx'), 'utf8');
    const sidebar = readFileSync(resolve(process.cwd(), 'src/components/layout/Sidebar.tsx'), 'utf8');

    expect(shell).toContain('<SkipLink href="#main-content">Skip to main content</SkipLink>');
    expect(shell).toContain('id="main-content" tabIndex={-1}');
    expect(shell).toContain("event.key === 'Escape'");
    expect(header).toContain('aria-controls="orbisops-sidebar"');
    expect(header).toContain('aria-expanded={!collapsed}');
    expect(header).toContain('Open user menu for');
    expect(sidebar).toContain('id="orbisops-sidebar"');
    expect(sidebar).toContain('aria-label="Primary navigation"');
  });

  it('keeps reduced-motion fallbacks for global shell transitions', () => {
    const shell = readFileSync(resolve(process.cwd(), 'src/components/ops-layout/OpsPageShell.tsx'), 'utf8');
    const sidebar = readFileSync(resolve(process.cwd(), 'src/components/layout/Sidebar.tsx'), 'utf8');

    expect(shell).toContain('@media (prefers-reduced-motion: reduce)');
    expect(sidebar).toContain('@media (prefers-reduced-motion: reduce)');
  });
});
