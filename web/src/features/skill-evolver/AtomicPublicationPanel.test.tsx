import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { AtomicPublicationPanel } from './AtomicPublicationPanel';
import { opsAdminService } from '../../services/ops-admin-service';
vi.mock('../../services/ops-admin-service', () => ({ opsAdminService: { listAtomicSkillPublications: vi.fn(), rollbackAtomicSkillPublication: vi.fn() } }));
const record = { candidateId: 'synthetic-candidate', projectId: 'project-a', operation: 'SPLIT_SKILL', status: 'ACTIVE', releaseStatus: 'ACTIVE',
  plan: { sources: [{ skillId: 'original', version: 2 }], targets: [{ name: '正常样本检查' }, { name: '样本不足检查' }] } };
const rangeRect = Object.getOwnPropertyDescriptor(Range.prototype, 'getBoundingClientRect');
beforeEach(() => {
  vi.stubGlobal('ResizeObserver', class { observe() {} unobserve() {} disconnect() {} });
  // jsdom has no layout engine; Semi Typography measures a Range to ellipsize titles.
  Object.defineProperty(Range.prototype, 'getBoundingClientRect', { configurable: true, value: () => new DOMRect() });
  vi.mocked(opsAdminService.listAtomicSkillPublications).mockReset().mockResolvedValue({ code: '0000', info: 'ok', data: [record] });
  vi.mocked(opsAdminService.rollbackAtomicSkillPublication).mockReset().mockResolvedValue({ code: '0000', info: 'ok', data: { status: 'ROLLED_BACK' } });
});
afterEach(() => {
  cleanup(); vi.unstubAllGlobals();
  if (rangeRect) Object.defineProperty(Range.prototype, 'getBoundingClientRect', rangeRect);
  else Reflect.deleteProperty(Range.prototype, 'getBoundingClientRect');
});
const open = () => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><AtomicPublicationPanel projectId="project-a" /></QueryClientProvider>);

it('shows replacement names and requires a reason before rolling back the exact group', async () => {
  open(); await screen.findByText('新方法：正常样本检查、样本不足检查');
  fireEvent.click(screen.getByRole('button', { name: '整组回滚' }));
  expect(await screen.findByRole('button', { name: '确认整组回滚' })).toBeDisabled();
  fireEvent.change(screen.getByLabelText('方法发布回滚原因'), { target: { value: '分支边界需要修正' } });
  expect(opsAdminService.rollbackAtomicSkillPublication).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole('button', { name: '确认整组回滚' }));
  await waitFor(() => expect(opsAdminService.rollbackAtomicSkillPublication).toHaveBeenCalledWith('synthetic-candidate', 'project-a', '分支边界需要修正'));
});

it('does not present an already rolled back group as active or offer another mutation', async () => {
  vi.mocked(opsAdminService.listAtomicSkillPublications).mockResolvedValue({ code: '0000', info: 'ok', data: [{ ...record, status: 'ROLLED_BACK', releaseStatus: 'ROLLED_BACK', rollbackReason: '保留原方法' }] });
  open(); await screen.findByText('已整组回滚');
  expect(screen.getByText('回滚原因：保留原方法')).toBeInTheDocument();
  expect(screen.queryByRole('button', { name: '整组回滚' })).not.toBeInTheDocument();
});
