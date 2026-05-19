import { render, screen, waitFor } from '@testing-library/react';
import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it, vi } from 'vitest';
import { ReadOnlyMarkdown, renderReadOnlyMarkdown } from './ReadOnlyMarkdown';

describe('read-only report rendering', () => {
  it('renders a complete report when dynamic evaluation is forbidden by the deployed CSP', () => {
    const blocked = vi.spyOn(globalThis, 'Function').mockImplementation(() => {
      throw new EvalError('CSP forbids unsafe-eval');
    });
    try {
      const rows = Array.from({ length: 12 }, (_, index) => `| 请求 ${index + 1} | 有证据 |`);
      const html = renderToStaticMarkup(renderReadOnlyMarkdown(['| 请求 | 结果 |', '|---|---|', ...rows].join('\n')));
      expect(html).toContain('请求 12');
      expect(blocked).not.toHaveBeenCalled();
    } finally {
      blocked.mockRestore();
    }
  });

  it('renders evidence tables while treating executable and remote content as untrusted text', async () => {
    const { container } = render(<ReadOnlyMarkdown text={[
      '### 观测结论', '', '| 指标 | 值 |', '|---|---|', '| p95 | 1.375 秒 |', '',
      '{globalThis.__reportExecuted = true}', '', '<script>globalThis.__reportExecuted = true</script>', '',
      '[危险链接](javascript:alert%281%29)', '', '![外部图片](https://example.invalid/private.png)',
    ].join('\n')} />);
    await screen.findByRole('table');
    await waitFor(() => expect(screen.getByText('1.375 秒')).toBeTruthy());
    expect(container.querySelector('script')).toBeNull();
    expect(container.querySelector('img')).toBeNull();
    expect(container.querySelector('a[href^="javascript:"]')).toBeNull();
    expect((globalThis as Record<string, unknown>).__reportExecuted).toBeUndefined();
    expect(screen.getByText('外部图片')).toBeTruthy();
  });
});
