import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, expect, it } from 'vitest';
import { BackgroundFailureNotice } from './BackgroundFailureNotice';
afterEach(cleanup);
it('shows durable network retry without exposing provider exception text', () => {
  render(<BackgroundFailureNotice job={{ status: 'PENDING', lastError: 'SKILL_MODEL_TRANSPORT_DEFERRED',
    lastFailureCode: 'SKILL_MODEL_TRANSPORT_DEFERRED', nextRunAt: '2026-09-28T15:00:00Z' }} />);
  expect(screen.getByText(/模型连接暂时异常/)).toBeInTheDocument();
  expect(screen.getByText(/下一次重试不早于/)).toBeInTheDocument();
});
it('explains a capacity failure without promising that retries will fix it', () => {
  render(<BackgroundFailureNotice job={{ status: 'PENDING', lastError: 'SKILL_GROUPING_DEFERRED',
    lastFailureCode: 'SKILL_AUTHORING_INPUT_TOO_LARGE', nextRunAt: '2026-09-26T14:03:09Z' }} />);
  expect(screen.getByText(/单纯重试不能缩小输入/)).toBeInTheDocument();
  expect(screen.getByText(/下一次重试不早于/).querySelector('time')).toHaveAttribute('datetime', '2026-09-26T14:03:09.000Z');
});
it('does not show stale failures for a recovered job or leak an unknown raw exception', () => {
  const view = render(<BackgroundFailureNotice job={{ lastError: '', lastFailureCode: 'BACKGROUND_MODEL_TIMEOUT' }} />);
  expect(screen.queryByRole('status')).not.toBeInTheDocument();
  view.rerender(<BackgroundFailureNotice job={{ lastError: 'deferred', lastFailureCode: 'secret-from-provider' }} />);
  expect(screen.queryByText(/secret/)).not.toBeInTheDocument();
});
it('uses the known legacy failure code without claiming a terminal job will retry', () => {
  const view = render(<BackgroundFailureNotice job={{ status: 'FAILED',
    lastError: 'SKILL_AUTHORING_MODEL_INVALID', nextRunAt: '2026-09-30T02:37:48Z' }} />);
  expect(screen.getByText(/模型没有返回有效的分析结果/)).toBeInTheDocument();
  expect(screen.getByText(/本次任务已失败，未在等待自动重试/)).toBeInTheDocument();
  expect(screen.queryByText(/下一次重试不早于|按间隔重试|延后重试/)).not.toBeInTheDocument();
  view.rerender(<BackgroundFailureNotice job={{ status: 'FAILED', lastError: 'secret-from-provider' }} />);
  expect(screen.queryByRole('status')).not.toBeInTheDocument();
});
it('retains the specific durable reason when a resumed worker has only a generic diagnostic', () => {
  const view = render(<BackgroundFailureNotice job={{ status: 'RUNNING',
    lastError: 'SKILL_MODEL_TRANSPORT_DEFERRED', lastFailureCode: 'BACKGROUND_FAILURE_RECORDED' }} />);
  expect(screen.getByText(/模型连接暂时异常/)).toBeInTheDocument();
  expect(screen.queryByText(/本次任务已失败|下一次重试不早于/)).not.toBeInTheDocument();
  view.rerender(<BackgroundFailureNotice job={{ status: 'RUNNING',
    lastError: 'secret-from-provider', lastFailureCode: 'BACKGROUND_FAILURE_RECORDED' }} />);
  expect(screen.getByText(/具体诊断已保存到审计记录/)).toBeInTheDocument();
  expect(screen.queryByText(/secret-from-provider/)).not.toBeInTheDocument();
});
