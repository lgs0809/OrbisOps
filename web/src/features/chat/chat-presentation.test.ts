import { expect, it } from 'vitest';
import { chatPreview, chatSessionTitle, shouldSendChatKey } from './chat-presentation';

it('turns Markdown previews into readable text without fetching links or rendering HTML', () => {
  expect(chatPreview('**结论：** Redis `缓存` 已恢复。\n\n[查看](https://example.com)')).toBe('结论： Redis 缓存 已恢复。 查看');
  expect(chatPreview('<script>evil()</script>\n\n```json\n{"private":true}\n```')).toBe('');
});
it('uses existing summaries to distinguish unnamed conversations while retaining custom titles', () => {
  expect(chatSessionTitle('新对话', '**订单核查**：不存在死锁')).toBe('订单核查：不存在死锁');
  expect(chatSessionTitle('固定的流程名称', '其他结论')).toBe('固定的流程名称');
  expect(chatSessionTitle('新对话', '')).toBe('新对话');
});
it('never submits Chinese input-method confirmation or Shift+Enter as a message', () => {
  expect(shouldSendChatKey({ key: 'Enter', shiftKey: false })).toBe(true);
  for (const patch of [{ isComposing: true }, { keyCode: 229 }, { shiftKey: true }, { key: 'a' }]) {
    expect(shouldSendChatKey({ key: 'Enter', shiftKey: false, ...patch })).toBe(false);
  }
});
