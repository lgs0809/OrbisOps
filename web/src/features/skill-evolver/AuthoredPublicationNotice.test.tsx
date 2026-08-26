import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, expect, it } from 'vitest';
import { AuthoredPublicationNotice } from './AuthoredPublicationNotice';
afterEach(cleanup);

it('shows the closure of an unpublished proposal independently of a failed learning job', () => {
  render(<AuthoredPublicationNotice publication={{ status: 'ROLLED_BACK', operation: 'MERGE_SKILLS', releasedVersion: 0, reason: 'BASELINE_STALE' }} />);
  expect(screen.getByText(/提案已关闭（未发布）/)).toBeInTheDocument();
  expect(screen.getByText(/原文与失败记录仍保留/)).toBeInTheDocument();
  expect(screen.queryByRole('button')).not.toBeInTheDocument();
});

it('distinguishes a published rollback and does not invent publication for an absent or unknown state', () => {
  const view = render(<AuthoredPublicationNotice />);
  expect(screen.queryByRole('region')).not.toBeInTheDocument();
  view.rerender(<AuthoredPublicationNotice publication={{ status: 'ROLLED_BACK', operation: 'CREATE', releasedVersion: 1, reason: '' }} />);
  expect(screen.getByText(/方法已回滚/)).toBeInTheDocument();
  expect(screen.queryByText(/未发布/)).not.toBeInTheDocument();
  view.rerender(<AuthoredPublicationNotice publication={{ status: 'unexpected-private-value', operation: 'CREATE', releasedVersion: 0, reason: 'provider-secret' }} />);
  expect(screen.getByText(/发布状态待核对/)).toBeInTheDocument();
  expect(screen.queryByText(/private|secret/)).not.toBeInTheDocument();
});
