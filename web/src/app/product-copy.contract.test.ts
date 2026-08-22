import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

const productSurfaces = [
  'src/components/layout/Header.tsx',
  'src/components/ops-layout/OpsAdvancedPreview.tsx',
  'src/components/ops-layout/OpsStatusBadge.tsx',
  'src/components/ops-layout/OpsAuthorityBadge.tsx',
  'src/components/ops-layout/OpsRiskBadge.tsx',
  'src/features/changes/components/ApprovalChannelDispatch.tsx',
  'src/features/execution/components/WorkflowApprovalPanel.tsx',
  'src/features/platform/pages/PlatformSettingsPage.tsx',
  'src/features/platform/pages/UsersAccessPage.tsx',
  'src/pages/login.tsx',
  'src/pages/ops-dashboard.tsx',
  'src/pages/chat.tsx',
  'src/pages/workbench.tsx',
  'src/pages/agent-list.tsx',
  'src/pages/automations.tsx',
  'src/pages/task-schedule-management.tsx',
  'src/pages/project-product-workspace.tsx',
  'src/pages/change-package-center.tsx',
  'src/pages/alert-triggers.tsx',
  'src/pages/channels.tsx',
  'src/pages/model-api-management.tsx',
  'src/pages/tools-mcp.tsx',
  'src/pages/knowledge.tsx',
  'src/pages/skills.tsx',
  'src/pages/execution-targets.tsx',
  'src/pages/governance.tsx',
  'src/pages/memory-management.tsx',
  'src/pages/skill-evolver-management.tsx',
  'src/pages/tool-routing-observability.tsx',
  'src/pages/workflow-builder.tsx',
];

const forbiddenVisibleFragments = [
  '生产控制已启用',
  'Runtime 已连接',
  '需后端探测',
  'JsonMode：需探测',
  'Advanced preview',
  'Approval request',
  'Approve & Resume',
  'Send Approval to Channel',
  'Connect this provider to a project.',
  '能力事实直接来自后端 Provider Adapter Catalog',
  '平台能力目录 ≠ Project 授权。',
  '当前构建未安装',
  'HTTP error! status:',
];

describe('canonical product copy boundary', () => {
  it('does not expose retired engineering copy or static fake status claims', () => {
    for (const relativePath of productSurfaces) {
      const source = readFileSync(resolve(process.cwd(), relativePath), 'utf8');
      for (const fragment of forbiddenVisibleFragments) {
        expect(source, `${relativePath} contains retired product copy: ${fragment}`).not.toContain(fragment);
      }
    }
  });

  it('does not echo raw caught exception messages from canonical product surfaces', () => {
    for (const relativePath of productSurfaces) {
      const source = readFileSync(resolve(process.cwd(), relativePath), 'utf8');
      expect(source, `${relativePath} directly exposes error.message`).not.toContain('error instanceof Error ? error.message');
    }
  });
});
