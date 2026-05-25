import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { WorkflowDirectActions } from './WorkflowDirectActions';

beforeEach(() => vi.stubGlobal('ResizeObserver', class {
  observe() {}
  unobserve() {}
  disconnect() {}
}));
afterEach(() => { cleanup(); vi.unstubAllGlobals(); });
const actions = [{ mcpId: 'peer', remoteToolName: 'probe', arguments: { testId: 'internal-reference' } }];
const props = { nodeId: 'read', actions, toolNames: new Map([['peer', '隔离观测接入']]), onChange: vi.fn(), onAdvanced: vi.fn() };

it('basic inspection shows action and source without requesting technical input or changing configuration', () => {
  const onAdvanced = vi.fn();
  render(<WorkflowDirectActions {...props} mode="basic" onAdvanced={onAdvanced} />);
  expect(screen.getByText('probe · 隔离观测接入')).toBeVisible();
  expect(screen.queryByRole('textbox')).not.toBeInTheDocument();
  expect(screen.queryByText(/internal-reference/)).not.toBeInTheDocument();
  expect(props.onChange).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole('button', { name: '编辑动作配置' }));
  expect(onAdvanced).toHaveBeenCalledOnce();
});

it('advanced edits preserve the entire action including bindings and budget-related fields', () => {
  const onChange = vi.fn();
  render(<WorkflowDirectActions {...props} mode="advanced" onChange={onChange} />);
  const changed = [{ ...actions[0], argumentBindings: { service: 'input.service' }, outputKey: 'result' }];
  const editor = screen.getByRole('textbox', { name: '高级动作配置 JSON' });
  fireEvent.change(editor, { target: { value: JSON.stringify(changed) } });
  fireEvent.blur(editor);
  expect(onChange).toHaveBeenCalledExactlyOnceWith(changed);
});

it('invalid advanced input does not replace previously configured actions and empty basic state is explicit', () => {
  const onChange = vi.fn();
  const view = render(<WorkflowDirectActions {...props} mode="advanced" onChange={onChange} />);
  const editor = screen.getByRole('textbox', { name: '高级动作配置 JSON' });
  fireEvent.change(editor, { target: { value: '{' } });
  fireEvent.blur(editor);
  expect(onChange).not.toHaveBeenCalled();
  view.rerender(<WorkflowDirectActions {...props} actions={[]} mode="basic" />);
  expect(screen.getByText('尚未配置动作。配置完成后才能执行这个节点。')).toBeVisible();
});
