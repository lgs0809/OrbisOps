import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, expect, it } from 'vitest';
import { AuthoredDecisionNotice, currentNoChange } from './AuthoredDecisionNotice';
afterEach(cleanup);
const authoredDecision = { planId: 'plan', operation: 'NO_CHANGE', reason: '已有方法覆盖这次只读核验，另一个方法仅允许手动维护。',
  source: 'LLM', model: 'gpt-5.6-terra', currentSource: true };

it('explains a real no-change decision without claiming a publication or asking for another retry', () => {
  render(<AuthoredDecisionNotice job={{ status: 'SKIPPED', authoredDecision }} />);
  expect(screen.getByRole('region', { name: '后台分析结论' })).toBeInTheDocument();
  expect(screen.getByText(/无需变更，原有方法与本次经验保留/)).toBeInTheDocument();
  expect(screen.getByText(authoredDecision.reason)).toBeInTheDocument();
  expect(screen.queryByRole('button')).not.toBeInTheDocument();
});

it('never presents unfinished, failed, superseded or change-producing author output as a current no-change result', () => {
  for (const job of [{ status: 'RUNNING', authoredDecision }, { status: 'FAILED', authoredDecision },
    { status: 'SKIPPED', authoredDecision: { ...authoredDecision, currentSource: false } },
    { status: 'SKIPPED', authoredDecision: { ...authoredDecision, operation: 'CREATE' } }, {}]) {
    expect(currentNoChange(job)).toBeUndefined();
    const view = render(<AuthoredDecisionNotice job={job} />);
    expect(screen.queryByRole('region')).not.toBeInTheDocument();
    view.unmount();
  }
});
