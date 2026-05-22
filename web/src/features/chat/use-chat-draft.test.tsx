import { act, renderHook } from '@testing-library/react';
import { expect, it } from 'vitest';
import { useChatDraft } from './use-chat-draft';

it('restores separate drafts when switching conversations, projects or accounts', () => {
  const view = renderHook(({ scope }) => useChatDraft(scope), { initialProps: { scope: 'owner:project-a:session-a' } });
  act(() => view.result.current.setInput('检查订单服务'));
  view.rerender({ scope: 'owner:project-a:session-b' });
  expect(view.result.current.input).toBe('');
  act(() => view.result.current.setInput('检查缓存服务'));
  for (const scope of ['owner:project-b:session-a', 'member:project-a:session-a']) {
    view.rerender({ scope });
    expect(view.result.current.input).toBe('');
  }
  view.rerender({ scope: 'owner:project-a:session-a' });
  expect(view.result.current.input).toBe('检查订单服务');
  view.rerender({ scope: 'owner:project-a:session-b' });
  expect(view.result.current.input).toBe('检查缓存服务');
  view.unmount();
  expect(renderHook(() => useChatDraft('owner:project-a:session-a')).result.current.input).toBe('');
});

it('does not restore already-submitted text from the initial new-conversation draft', () => {
  const view = renderHook(({ scope }) => useChatDraft(scope), { initialProps: { scope: 'new' } });
  act(() => view.result.current.setInput('只读核查服务'));
  view.rerender({ scope: 'created-session' });
  act(() => { view.result.current.discardDraft('new'); view.result.current.setInput(''); });
  view.rerender({ scope: 'new' });
  expect(view.result.current.input).toBe('');
});
