import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, expect, it, vi } from 'vitest';
import { ChatModelSelector } from './ChatModelSelector';

afterEach(cleanup);

it('reports the chosen binding before the dropdown closing animation completes', () => {
  const change = vi.fn();
  render(<ChatModelSelector value="" disabled={false} onChange={change} models={[
    {modelId: 'normal', modelName: 'gpt-5.6-luna', description: '常规连接'},
    {modelId: 'recovery', modelName: 'gpt-5.6-luna', description: '恢复验收'},
  ]} />);
  fireEvent.click(screen.getByRole('combobox'));
  // Real Semi control and real motion; no animation completion or timer advance.
  fireEvent.click(screen.getByText('gpt-5.6-luna · 恢复验收', {exact: false}));
  expect(change).toHaveBeenCalledWith('recovery');
  expect(change).not.toHaveBeenCalledWith('normal');
});
