import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { MaintenancePanel } from './MaintenancePanel';
import { opsAdminService } from '../../services/ops-admin-service';
vi.mock('../../services/ops-admin-service', () => ({ opsAdminService: { listSkillMaintenance: vi.fn(), keepInactiveSkill: vi.fn() } }));
const record = { check_id: 'synthetic-check', project_id: 'project-a', skill_id: 'method-a', base_version: 2,
  kind: 'INACTIVITY_REVIEW', status: 'REVIEW_REQUIRED' };
const rangeRect = Object.getOwnPropertyDescriptor(Range.prototype, 'getBoundingClientRect');
beforeEach(() => {
  vi.stubGlobal('ResizeObserver', class { observe() {} unobserve() {} disconnect() {} });
  Object.defineProperty(Range.prototype, 'getBoundingClientRect', { configurable: true, value: () => new DOMRect() });
  vi.mocked(opsAdminService.listSkillMaintenance).mockReset().mockResolvedValue({ code: '0000', info: 'ok', data: [record] });
  vi.mocked(opsAdminService.keepInactiveSkill).mockReset().mockResolvedValue({ code: '0000', info: 'ok', data: { status: 'REVIEWED_KEEP' } });
});
afterEach(() => {
  cleanup(); vi.unstubAllGlobals();
  if (rangeRect) Object.defineProperty(Range.prototype, 'getBoundingClientRect', rangeRect);
  else Reflect.deleteProperty(Range.prototype, 'getBoundingClientRect');
});
const open = () => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><MaintenancePanel projectId="project-a" /></QueryClientProvider>);
it('does not disable an inactive method and requires a reason for recording the exact review', async () => {
  open(); fireEvent.click(await screen.findByRole('button', { name: '检查后保留' }));
  expect(await screen.findByRole('button', { name: '确认继续保留方法' })).toBeDisabled();
  fireEvent.change(screen.getByLabelText('方法维护检查结论'), { target: { value: '仍用于季末维护' } });
  expect(opsAdminService.keepInactiveSkill).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole('button', { name: '确认继续保留方法' }));
  await waitFor(() => expect(opsAdminService.keepInactiveSkill).toHaveBeenCalledWith('synthetic-check', 'project-a', '仍用于季末维护'));
  expect(screen.queryByRole('button', { name: '删除' })).not.toBeInTheDocument();
});
it('does not allow inactivity acknowledgement to approve a rejected behavior-changing compression', async () => {
  vi.mocked(opsAdminService.listSkillMaintenance).mockResolvedValue({ code: '0000', info: 'ok', data: [{ ...record, kind: 'COMPRESS_CHECK', reason: 'BEHAVIOR_CHANGE_REQUIRES_SOURCE_QUALIFIED_PATCH' }] });
  open(); await screen.findByText('精简可能改变方法含义，已保留原文。后续修改仍需满足成功来源要求。');
  expect(screen.queryByRole('button', { name: '检查后保留' })).not.toBeInTheDocument();
});
