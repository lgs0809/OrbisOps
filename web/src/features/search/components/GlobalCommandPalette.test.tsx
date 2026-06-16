import { useState } from 'react';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, expect, it, vi } from 'vitest';

import { GlobalCommandPalette, GlobalCommandPaletteProvider } from './GlobalCommandPalette';

vi.mock('../../../services/auth-session', () => ({ currentUserRole: () => 'admin', isAuthenticated: () => true }));
vi.mock('../../../hooks/use-project-scope', () => ({ useProjectScope: () => ({
  projectId: 'payments', projects: [{ projectId: 'payments', name: 'Payments' }],
}) }));
// JSDOM does not load Semi's CSS exit animation. Check controller state through
// the Modal's visible contract here; the unmodified browser E2E covers its DOM.
vi.mock('@douyinfe/semi-ui', async (importOriginal) => ({
  ...await importOriginal<typeof import('@douyinfe/semi-ui')>(),
  Modal: ({ visible, children }: { visible: boolean; children: import('react').ReactNode }) =>
    visible ? <div role="dialog">{children}</div> : null,
}));

afterEach(cleanup);

it('retains one palette and its query when the routed Sidebar is replaced, with one hotkey listener', async () => {
  const RoutedSidebar = () => {
    const [page, setPage] = useState(0);
    return <><button onClick={() => setPage((value) => value + 1)}>下一页面</button>
      <GlobalCommandPalette key={page} /></>;
  };
  render(<MemoryRouter><GlobalCommandPaletteProvider><RoutedSidebar /></GlobalCommandPaletteProvider></MemoryRouter>);
  fireEvent.click(screen.getByRole('button', { name: '打开全局搜索' }));
  const search = await screen.findByRole('textbox', { name: '搜索页面、操作和 Project' });
  fireEvent.change(search, { target: { value: 'mcp' } });
  fireEvent.click(screen.getByRole('button', { name: '下一页面' }));
  expect(screen.getAllByRole('textbox', { name: '搜索页面、操作和 Project' })).toHaveLength(1);
  expect(screen.getByRole('textbox', { name: '搜索页面、操作和 Project' })).toHaveValue('mcp');
  fireEvent.keyDown(window, { key: 'k', metaKey: true });
  await waitFor(() => expect(screen.queryByRole('textbox', { name: '搜索页面、操作和 Project' })).not.toBeInTheDocument());
  fireEvent.keyDown(window, { key: 'k', metaKey: true });
  expect(await screen.findByRole('textbox', { name: '搜索页面、操作和 Project' })).toHaveValue('mcp');
});
