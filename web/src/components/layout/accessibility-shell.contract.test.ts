import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

describe('global accessibility shell contract', () => {
  it('keeps skip navigation, main focus target, labeled navigation, and accessible header triggers', () => {
    const shell = readFileSync(resolve(process.cwd(), 'src/components/ops-layout/OpsPageShell.tsx'), 'utf8');
    const header = readFileSync(resolve(process.cwd(), 'src/components/layout/Header.tsx'), 'utf8');
    const sidebar = readFileSync(resolve(process.cwd(), 'src/components/layout/Sidebar.tsx'), 'utf8');

    expect(shell).toContain('<SkipLink href="#main-content">跳到主要内容</SkipLink>');
    expect(shell).toContain('id="main-content" tabIndex={-1}');
    expect(shell).toContain("event.key === 'Escape'");
    expect(header).toContain('aria-controls="orbisops-sidebar"');
    expect(header).toContain('aria-expanded={!collapsed}');
    expect(header).toContain('打开 ${userInfo.username');
    expect(sidebar).toContain('id="orbisops-sidebar"');
    expect(sidebar).toContain('aria-label="一级导航"');
  });

  it('keeps reduced-motion fallbacks for global shell transitions', () => {
    const shell = readFileSync(resolve(process.cwd(), 'src/components/ops-layout/OpsPageShell.tsx'), 'utf8');
    const sidebar = readFileSync(resolve(process.cwd(), 'src/components/layout/Sidebar.styles.ts'), 'utf8');

    expect(shell).toContain('@media (prefers-reduced-motion: reduce)');
    expect(sidebar).toContain('@media (prefers-reduced-motion: reduce)');
  });

  it('keeps canonical Workflow and scheduled Automation pages on the shared product shell', () => {
    for (const path of ['src/pages/agent-list.tsx', 'src/pages/task-schedule-management.tsx']) {
      const source = readFileSync(resolve(process.cwd(), path), 'utf8');
      expect(source, `${path} must use the shared shell`).toContain('<OpsPageShell');
      expect(source, `${path} must not rebuild Sidebar/Header locally`).not.toContain("from '../components/layout'");
    }
  });
});
