import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, expect, it } from 'vitest';
import { SavedExperiencePanel } from './SavedExperiencePanel';

afterEach(cleanup);
it('shows the saved method without implying publication and escapes model text', () => {
  const view = render(<SavedExperiencePanel experience={{ currentSource: true, method: {
    goal: '<img src="https://example.com/pixel" onerror="alert(1)">',
    conditions: ['只读'], steps: ['核对版本'], acceptance: ['证据不足不判通过'], toolCategories: ['版本查询'],
  } }} />);
  expect(screen.getByText(/是否形成 Skill/)).toBeInTheDocument();
  expect(screen.getByText('证据不足不判通过')).toBeInTheDocument();
  expect(screen.getAllByRole('list')).toHaveLength(4);
  expect(view.container.querySelector('img')).toBeNull();
});
it('clearly marks a superseded source instead of presenting it as current', () => {
  render(<SavedExperiencePanel experience={{ currentSource: false, method: { goal: '历史方法' } }} />);
  expect(screen.getByText(/验收已失效/)).toBeInTheDocument();
  expect(screen.queryByText(/本次任务经验已保存/)).not.toBeInTheDocument();
});
