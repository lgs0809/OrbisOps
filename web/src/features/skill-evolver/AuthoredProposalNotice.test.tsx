import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, expect, it } from 'vitest';
import { AuthoredProposalNotice } from './AuthoredProposalNotice';

afterEach(cleanup);
const decision = { planId: 'plan', operation: 'SPLIT_SKILL', reason: '两项职责需要独立检索和维护。',
  source: 'LLM', model: 'gpt-6.1-terra', currentSource: true };

it('shows retained model progress while keeping publication admission separate', () => {
  render(<AuthoredProposalNotice job={{ authoredDecision: decision }} />);
  expect(screen.getByRole('region', { name: '后台维护提案' })).toBeInTheDocument();
  expect(screen.getByText(/模型已提出：拆分方法/)).toHaveTextContent('尚不代表发布通过');
  expect(screen.getByText(decision.reason)).toBeInTheDocument();
  expect(screen.queryByRole('button')).not.toBeInTheDocument();
});

it('never treats stale, unknown, missing or no-change output as a publishable proposal', () => {
  for (const authoredDecision of [undefined, { ...decision, currentSource: false },
    { ...decision, operation: 'NO_CHANGE' }, { ...decision, operation: 'UNKNOWN' },
    { ...decision, planId: '' }, { ...decision, source: 'MANUAL' }]) {
    const view = render(<AuthoredProposalNotice job={{ authoredDecision }} />);
    expect(screen.queryByRole('region')).not.toBeInTheDocument(); view.unmount();
  }
});

it('keeps the model reason visible while a created candidate still awaits publication', () => {
  render(<AuthoredProposalNotice job={{ authoredDecision: decision, authoredPublication: { status: 'PASSED' } }} />);
  expect(screen.getByRole('region', { name: '后台维护提案' })).toBeInTheDocument();
  expect(screen.getByText(decision.reason)).toBeInTheDocument();
});

it('leaves an active or rolled-back publication to the authoritative publication view', () => {
  for (const status of ['ACTIVE', 'ROLLED_BACK']) {
    const view = render(<AuthoredProposalNotice job={{ authoredDecision: decision, authoredPublication: { status } }} />);
    expect(screen.queryByRole('region')).not.toBeInTheDocument(); view.unmount();
  }
});
