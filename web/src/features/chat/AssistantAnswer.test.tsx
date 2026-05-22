import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, expect, it } from 'vitest';
import { AssistantAnswer } from './AssistantAnswer';
import { chatPreview } from './chat-presentation';
import { toolAnswerRows, workflowToolAnswer } from './workflow-tool-answer';

afterEach(cleanup);
const receipt = (data: unknown, patch: Record<string, unknown> = {}) => JSON.stringify({
  allowed: true, decision: 'ALLOWED', remoteToolName: 'inspect_state', fullOutputRef: 'db:receipt-1',
  mcpEnvelope: { orbisopsResultVersion: 1, isError: false, normalizedContent: data }, ...patch,
});

it('shows actual returned values and folds the complete audit without claiming business acceptance', () => {
  const text = receipt({ count: 0, healthy: false, currentVersion: null, services: [{ name: 'orders', state: 'DEGRADED' }] });
  const { container } = render(<AssistantAnswer text={text} />);
  expect(screen.getByRole('heading').textContent).toBe('已收到工具回执 · inspect_state');
  for (const value of ['0', 'false', 'null', 'services[0].state', 'DEGRADED']) expect(screen.getByText(value)).toBeTruthy();
  expect(container.querySelector('details')?.open).toBe(false);
  expect(container.querySelector('pre')?.textContent).toBe(text);
  expect(screen.getByText(/仍需结合任务的验收标准判断/)).toBeTruthy();
  expect(chatPreview(text)).toBe('inspect_state · 已收到工具回执');
});

it('makes capped session summaries readable without interpreting incomplete JSON as success', () => {
  expect(chatPreview('{"resultId":"receipt-1","providerType":"MCP","preview":"{...')).toBe('工具回执（打开对话查看）');
  expect(chatPreview('{"truncated":false,"mcpEnvelope":{"content":[],"isError":false,"structuredContent":')).toBe('工具回执（打开对话查看）');
  expect(chatPreview('{"count":6')).toBe('结构化结果（打开对话查看）');
  expect(workflowToolAnswer('{"resultId":"receipt-1","providerType":"MCP","preview":"{...')).toBeUndefined();
});

it('keeps ordinary Markdown and arbitrary JSON out of the protocol renderer', () => {
  for (const text of ['**查询结果**为 420 元。', '{"count":6}', '{"mcpEnvelope":{"orbisopsResultVersion":2,"isError":false,"normalizedContent":{}}}', '{"mcpEnvelope":']) {
    expect(workflowToolAnswer(text)).toBeUndefined();
  }
  render(<AssistantAnswer text="**查询结果**为 420 元。" />);
  expect(screen.getByText('查询结果')).toBeTruthy();
  expect(screen.queryByRole('region', { name: '工具返回数据' })).toBeNull();
});

it('recognizes capped references by their compound receipt identity, without promoting partial data to success', () => {
  const partial = '{"providerId":"local-probe","resultId":"tool-result-1234","providerOutputHash":"' + 'a'.repeat(64) + '","providerResultId":"tool-result-';
  expect(chatPreview(partial)).toBe('工具回执（打开对话查看）');
  expect(workflowToolAnswer(partial)).toBeUndefined();
  expect(chatPreview('{"providerId":"billing","resultId":"order-1","providerOutputHash":"' + 'a'.repeat(64))).toBe('结构化结果（打开对话查看）');
  expect(chatPreview('{"providerId":"local-probe","resultId":"tool-result-1234","providerOutputHash":"invalid')).toBe('结构化结果（打开对话查看）');
  const complete = JSON.stringify({ providerId: 'local-probe', resultId: 'tool-result-1234', providerOutputHash: 'a'.repeat(64), count: 0 });
  expect(workflowToolAnswer(complete)).toBeUndefined();
  expect(chatPreview(complete)).toBe('结构化结果（打开对话查看）');
});

it('presents ordinary structured results as data without claiming a governed tool receipt', () => {
  const data = JSON.stringify({ allowed: false, decision: 'DENIED', count: 0, value: '<script>run()</script>' });
  const { container } = render(<AssistantAnswer text={data} />);
  expect(screen.getByRole('region', { name: '结构化返回结果' })).toBeTruthy();
  expect(screen.queryByRole('region', { name: '工具返回数据' })).toBeNull();
  for (const value of ['false', 'DENIED', '0', '<script>run()</script>']) expect(screen.getByText(value)).toBeTruthy();
  expect(container.querySelector('script,img')).toBeNull();
  expect(container.querySelector('details')?.open).toBe(false);
  expect(container.querySelector('pre')?.textContent).toBe(data);
});

it('keeps capped nested previews neutral while a complete outer receipt uses only its own envelope', () => {
  const nested = receipt({ count: 1 });
  const outer = receipt({ count: 2 }, { preview: nested });
  const capped = JSON.stringify({ preview: outer }).slice(0, 240);
  expect(chatPreview(capped)).toBe('结构化结果（打开对话查看）');
  expect(workflowToolAnswer(capped)).toBeUndefined();
  render(<AssistantAnswer text={outer} />);
  expect(screen.getByText('2')).toBeTruthy();
  expect(screen.queryByText('1')).toBeNull();
});

it('uses the governed envelope rather than an inconsistent outer copy and never executes data as markup', () => {
  const malicious = '<img src="https://example.invalid/private" onerror="alert(1)">';
  const { container } = render(<AssistantAnswer text={receipt({ finding: malicious }, { normalizedContent: { finding: 'HEALTHY' } })} />);
  expect(screen.getByText(malicious)).toBeTruthy();
  expect(screen.queryByText('HEALTHY')).toBeNull();
  expect(container.querySelector('img,script,a')).toBeNull();
});

it('keeps error and denied receipts distinct from successful data returns', () => {
  const failed = receipt({ code: 'TARGET_UNREACHABLE' }, { mcpEnvelope: { orbisopsResultVersion: 1, isError: true, normalizedContent: { code: 'TARGET_UNREACHABLE' } } });
  render(<AssistantAnswer text={failed} />);
  expect(screen.getByRole('heading').textContent).toBe('工具返回错误 · inspect_state');
  expect(chatPreview(failed)).toBe('inspect_state · 工具返回错误');
  expect(workflowToolAnswer(receipt({ secret: 'unchanged' }, { allowed: false, decision: 'DENIED' }))).toBeUndefined();
});

it('bounds large returns and explicitly retains the full receipt for omitted data', () => {
  const data = Array.from({ length: 300 }, (_, index) => ({ index, text: 'x'.repeat(8000) }));
  const view = toolAnswerRows(data);
  expect(view.rows).toHaveLength(40);
  expect(view.omitted).toBe(true);
  expect(view.rows.every(row => row.value.length <= 4097)).toBe(true);
  const text = receipt(data.slice(0, 20));
  const { container } = render(<AssistantAnswer text={text} />);
  expect(screen.getByText(/当前展示为摘要/)).toBeTruthy();
  expect(container.querySelector('pre')?.textContent).toBe(text);
});
