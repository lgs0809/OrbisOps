#!/usr/bin/env node

import { chromium } from '@playwright/test';
import { mkdir, writeFile } from 'node:fs/promises';
import path from 'node:path';

const baseUrl = process.env.OPS_FRONTEND_URL || 'http://127.0.0.1:3000';
const username = process.env.OPS_E2E_USERNAME || 'admin';
const localOnly = /^https?:\/\/(127\.0\.0\.1|localhost)(:\d+)?(?:\/|$)/.test(baseUrl);
const password = process.env.OPS_E2E_PASSWORD || (localOnly ? String(123456) : '');
const projectId = process.env.OPS_E2E_PROJECT_ID || 'demo-project';
const modelId = process.env.OPS_E2E_MODEL_ID || '9001';
const keepWorkflow = String(process.env.OPS_E2E_KEEP_WORKFLOW || '').toLowerCase() === 'true';

if (!password) {
  throw new Error('OPS_E2E_PASSWORD is required');
}

const stamp = new Date().toISOString().replace(/[:.]/g, '-');
const agentId = `workflow-live-acceptance-${Date.now()}`;
const reportDir = path.resolve('../reports/ops-platform/acceptance', `${stamp}-workflow-live`);
await mkdir(reportDir, { recursive: true });

const browser = await chromium.launch({ channel: 'chrome', headless: true });
const context = await browser.newContext({ viewport: { width: 1600, height: 1000 } });
const page = await context.newPage();
page.setDefaultTimeout(20_000);

const summary = {
  schemaVersion: 'ops-workflow-live-acceptance-v1',
  startedAt: new Date().toISOString(),
  baseUrl,
  projectId,
  agentId,
  status: 'FAIL',
  checks: {},
};

const saveSummary = async () => {
  summary.finishedAt = new Date().toISOString();
  await writeFile(path.join(reportDir, 'summary.json'), JSON.stringify(summary, null, 2));
};

try {
  await page.goto(`${baseUrl}/login`);
  await page.getByPlaceholder('请输入账号').fill(username);
  await page.getByPlaceholder('请输入密码').fill(password);
  await page.getByRole('button', { name: /登录/ }).click();
  await page.waitForURL((url) => !url.pathname.endsWith('/login'), { timeout: 20_000 });
  summary.checks.login = true;

  const capabilitiesPromise = page.waitForResponse(
    (response) => response.url().includes(`/api/v1/admin/ops-agents/projects/${projectId}/agent-capabilities`)
      && response.request().method() === 'GET',
    { timeout: 20_000 },
  );
  await page.goto(`${baseUrl}/agent-config?projectId=${encodeURIComponent(projectId)}`);
  const capabilitiesPayload = await (await capabilitiesPromise).json();
  const capabilityTools = [
    ...(capabilitiesPayload?.data?.projectTools || []),
    ...(capabilitiesPayload?.data?.enabledSharedTools || []),
  ];
  const readCandidates = capabilityTools.filter((item) => {
    const remoteTools = item.remoteTools || item.transportConfig?.remoteTools || [];
    return remoteTools.some((tool) => tool.readOnly === true && tool.toolName);
  });
  const readTool = readCandidates.find((item) => /openapi/i.test(`${item.resourceType || ''} ${item.mcpId || ''} ${item.mcpName || ''}`))
    || readCandidates.find((item) => /prometheus/i.test(`${item.resourceType || ''} ${item.mcpId || ''} ${item.mcpName || ''}`))
    || readCandidates[0];
  const projectReadMcpId = readTool?.mcpId || readTool?.toolId || '';
  const remoteReadTools = readTool?.remoteTools || readTool?.transportConfig?.remoteTools || [];
  const preferredRemoteTool = remoteReadTools.find((tool) => tool.readOnly === true && /health/i.test(String(tool.toolName || '')))
    || remoteReadTools.find((tool) => tool.readOnly === true && tool.toolName);
  const readToolName = String(preferredRemoteTool?.toolName || '');
  if (!projectReadMcpId || !readToolName) {
    throw new Error(`no executable read-only MCP is authorized for project ${projectId}: ${JSON.stringify(capabilityTools).slice(0, 6000)}`);
  }
  const readToolArguments = {};
  summary.projectReadMcpId = projectReadMcpId;
  summary.readToolName = readToolName;
  summary.checks.projectReadMcp = true;

  const definition = {
    agentId,
    schemaVersion: 1,
    name: 'Workflow 实机验收 · DIRECT/LLM/REACT/Loop',
    projectId,
    engine: 'HYBRID',
    description: '真实浏览器验收：固定指标读取、LLM 判断、ReAct 调查、显式 feedback loop 和最终汇总。',
    instruction: '这是本地产品验收 Workflow。只能读取真实观测数据，不得执行任何生产变更。',
    definitionKind: 'SPECIALIZED_WORKFLOW',
    workflowInvocationMode: 'MANUAL_ONLY',
    workflowAutoSelectEnabled: false,
    modelId,
    startNodeId: 'start',
    defaultMaxMainRounds: 3,
    defaultSubAgentMaxIterations: 3,
    queryRewriteEnabled: false,
    nodes: [
      {
        nodeId: 'start',
        type: 'START',
        agent: 'start',
        description: '输入边界。',
        config: { inputKeys: ['query'] },
      },
      {
        nodeId: 'direct-baseline',
        type: 'AGENT',
        mode: 'direct',
        agent: 'direct-baseline',
        description: '固定调用当前项目真实授权的只读工具。',
        outputKey: 'baseline',
        mcpIds: [projectReadMcpId],
        config: {
          mode: 'direct',
          role: 'general',
          authority: 'OBSERVE_ONLY',
          actions: [
            {
              mcpId: projectReadMcpId,
              remoteToolName: readToolName,
              arguments: readToolArguments,
              outputKey: 'baseline',
            },
          ],
        },
      },
      {
        nodeId: 'review-gate',
        type: 'AGENT',
        mode: 'llm',
        agent: 'review-gate',
        modelId,
        description: '验收用 Review：稳定进入调查分支。',
        instruction: '这是 Workflow 验收。阅读上游 baseline 后只输出 investigate，不要输出其他文字。',
        outputKey: 'selectedRoutes',
        config: {
          mode: 'llm',
          role: 'reviewer',
          contextInputs: ['baseline', 'upstreamOutputs'],
        },
      },
      {
        nodeId: 'gate-router',
        type: 'ROUTER',
        agent: 'gate-router',
        description: '根据 Review 结果确定是否进入 ReAct。',
        outputKey: 'selectedRoutes',
        config: { routeMode: 'single', inputKey: 'selectedRoutes' },
      },
      {
        nodeId: 'react-read',
        type: 'AGENT',
        mode: 'react',
        subEngine: 'AGENTSCOPE',
        agent: 'react-read',
        modelId,
        description: '使用当前项目真实授权的只读 MCP 做一次自主调查。',
        instruction: `必须先调用 ${readToolName}，观察真实结果后给出一句简短结论。不要调用任何写工具。`,
        outputKey: 'reactEvidence',
        mcpIds: [projectReadMcpId],
        config: {
          mode: 'react',
          role: 'data_agent',
          authority: 'OBSERVE_ONLY',
          contextInputs: ['query', 'baseline', 'upstreamOutputs'],
        },
      },
      {
        nodeId: 'loop-review',
        type: 'AGENT',
        mode: 'llm',
        agent: 'loop-review',
        modelId,
        description: '验收用 Review：固定要求一次补查，由 Loop Policy 限制重复次数。',
        instruction: '这是循环边验收。无论上游内容是什么，只输出 needs:followup，不要输出其他文字。',
        outputKey: 'review_decision',
        config: {
          mode: 'llm',
          role: 'reviewer',
          contextInputs: ['reactEvidence', 'upstreamOutputs'],
        },
      },
      {
        nodeId: 'loop-router',
        type: 'ROUTER',
        agent: 'loop-router',
        description: '第一次补查走 feedback，第二次因 maxRounds=1 强制退出。',
        outputKey: 'selectedReviewRoutes',
        config: { routeMode: 'single', inputKey: 'review_decision' },
      },
      {
        nodeId: 'final-report',
        type: 'AGENT',
        mode: 'llm',
        agent: 'final-report',
        modelId,
        description: '汇总两轮真实证据。',
        instruction: '基于上游真实 observation 输出不超过 5 行的验收结论，必须说明是否拿到了实际只读工具结果。',
        outputKey: 'finalReport',
        config: {
          mode: 'llm',
          role: 'reporter',
          contextInputs: ['baseline', 'reactEvidence', 'upstreamOutputs'],
        },
      },
      {
        nodeId: 'end',
        type: 'END',
        agent: 'end',
        description: '输出边界。',
        config: { outputKeys: ['finalReport'] },
      },
    ],
    edges: [
      { from: 'start', to: 'direct-baseline', conditionType: 'always', condition: 'always' },
      { from: 'direct-baseline', to: 'review-gate', conditionType: 'always', condition: 'always' },
      { from: 'review-gate', to: 'gate-router', conditionType: 'always', condition: 'always' },
      { from: 'gate-router', to: 'react-read', conditionType: 'route_match', condition: 'investigate' },
      { from: 'gate-router', to: 'final-report', conditionType: 'default', condition: '__default__' },
      { from: 'react-read', to: 'loop-review', conditionType: 'always', condition: 'always' },
      { from: 'loop-review', to: 'loop-router', conditionType: 'always', condition: 'always' },
      { from: 'loop-router', to: 'react-read', conditionType: 'review_decision', condition: 'needs:followup', feedback: true },
      { from: 'loop-router', to: 'final-report', conditionType: 'default', condition: '__default__' },
      { from: 'final-report', to: 'end', conditionType: 'always', condition: 'always' },
    ],
    loops: [
      {
        loopId: 'read-followup',
        name: '只读工具补查验收',
        nodes: ['react-read', 'loop-review', 'loop-router'],
        feedbackEdges: ['loop-router->react-read'],
        maxRounds: 1,
        timeoutSeconds: 180,
        exitEdge: 'loop-router->final-report',
        countMode: 'router_choice',
      },
    ],
  };

  await page.getByRole('button', { name: '新建' }).click();
  await page.getByRole('button', { name: '高级 JSON' }).click();
  const jsonPanel = page.getByText('高级调试入口：这里编辑的是后端运行时直接消费的 OpsAgentDefinition').locator('..');
  const jsonArea = jsonPanel.locator('textarea');
  await jsonArea.fill(JSON.stringify(definition, null, 2));
  await page.getByRole('button', { name: '应用 JSON' }).click();
  await page.getByText('JSON 已应用到画布').waitFor();
  await page.getByText('9 个节点 / 10 条边').waitFor();
  summary.checks.uiDefinitionApplied = true;

  const publishNetwork = [];
  const capturePublishResponse = async (response) => {
    if (!response.url().includes('/api/v1/admin/ops-agents')) return;
    let body = '';
    try { body = (await response.text()).slice(0, 6000); } catch { body = ''; }
    publishNetwork.push({ url: response.url(), status: response.status(), body });
  };
  page.on('response', capturePublishResponse);
  const publishResponsePromise = page.waitForResponse(
    (response) => response.url().includes(`/api/v1/admin/ops-agents/${agentId}/versions/`)
      && response.url().endsWith('/publish'),
    { timeout: 30_000 },
  );
  await page.locator('button').filter({ hasText: /^发布$/ }).last().click();
  await publishResponsePromise;
  const publishedToast = page.getByText('专项 Workflow 已保存并发布');
  await publishedToast.waitFor({ timeout: 10_000 }).catch(() => {});
  summary.publishNetwork = publishNetwork;
  summary.publishMessages = await page.locator('[class*="toast"]').allInnerTexts().catch(() => []);
  if (!(await publishedToast.isVisible().catch(() => false))) {
    throw new Error(`publish did not complete; network=${JSON.stringify(publishNetwork)}; toast=${JSON.stringify(summary.publishMessages)}`);
  }
  page.off('response', capturePublishResponse);
  summary.checks.published = true;

  await page.getByRole('button', { name: '测试', exact: true }).last().click();
  await page.locator('textarea:visible').last().fill('执行真实 Workflow 验收：调用项目只读工具并完成一次补查循环。');
  await page.getByRole('button', { name: '运行当前 Workflow' }).click();
  await page.getByText('测试运行完成').waitFor({ timeout: 180_000 });

  const outputBlock = page.getByText('输出', { exact: true }).locator('..').locator('pre, div').last();
  const eventBlock = page.getByText('事件', { exact: true }).locator('..').locator('pre, div').last();
  const outputText = (await outputBlock.innerText()).trim();
  const eventText = (await eventBlock.innerText()).trim();
  await writeFile(path.join(reportDir, 'output.txt'), outputText);
  await writeFile(path.join(reportDir, 'events.txt'), eventText);
  await page.screenshot({ path: path.join(reportDir, 'workflow-after-run.png'), fullPage: true });

  const reactMentions = (eventText.match(/react-read/g) || []).length;
  const directMentions = (eventText.match(/direct-baseline/g) || []).length;
  const toolStarted = eventText.includes(readToolName);
  const hasSuccess = /SUCCEEDED|成功|完成/.test(eventText);

  summary.checks.testRunCompleted = true;
  summary.checks.directNodeObserved = directMentions > 0;
  summary.checks.readToolObserved = toolStarted;
  summary.checks.reactNodeObserved = reactMentions > 0;
  summary.checks.feedbackLoopObserved = reactMentions >= 2;
  summary.checks.finalOutputNonEmpty = outputText.length > 0;
  summary.checks.successEventObserved = hasSuccess;
  summary.outputPreview = outputText.slice(0, 2000);
  summary.eventPreview = eventText.slice(-6000);

  const required = [
    'login',
    'projectReadMcp',
    'uiDefinitionApplied',
    'published',
    'testRunCompleted',
    'directNodeObserved',
    'readToolObserved',
    'reactNodeObserved',
    'feedbackLoopObserved',
    'finalOutputNonEmpty',
    'successEventObserved',
  ];
  const failed = required.filter((key) => !summary.checks[key]);
  summary.failedChecks = failed;
  summary.status = failed.length === 0 ? 'PASS' : 'FAIL';

  if (!keepWorkflow) {
    await page.getByRole('button', { name: '删除', exact: true }).click();
    const confirm = page.getByRole('button', { name: /确认|确定/ }).last();
    if (await confirm.isVisible().catch(() => false)) await confirm.click();
    await page.waitForTimeout(500);
    summary.cleanupAttempted = true;
  }

  await saveSummary();
  console.log(JSON.stringify(summary, null, 2));
  if (summary.status !== 'PASS') process.exitCode = 1;
} catch (error) {
  summary.failureReason = error instanceof Error ? error.stack || error.message : String(error);
  summary.failurePageText = (await page.locator('body').innerText().catch(() => '')).slice(-10000);
  await page.screenshot({ path: path.join(reportDir, 'failure.png'), fullPage: true }).catch(() => {});
  await saveSummary();
  console.error(JSON.stringify(summary, null, 2));
  process.exitCode = 1;
} finally {
  await context.close().catch(() => {});
  await browser.close().catch(() => {});
}
