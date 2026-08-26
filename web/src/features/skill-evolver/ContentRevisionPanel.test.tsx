import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { ContentRevisionPanel } from './ContentRevisionPanel';
import { opsAdminService } from '../../services/ops-admin-service';
vi.mock('../../services/ops-admin-service', () => ({ opsAdminService: { skillContentRevisions: vi.fn() } }));
beforeEach(() => { vi.mocked(opsAdminService.skillContentRevisions).mockReset(); });
afterEach(cleanup);
const open = () => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
  <ContentRevisionPanel candidateId="candidate" projectId="project-a" />
</QueryClientProvider>);
it('shows retained reasons in ordinary language and a final failure after two completed corrections', async () => {
  vi.mocked(opsAdminService.skillContentRevisions).mockResolvedValue({ code: '0000', info: 'ok', data: {
    candidateStatus: 'POLICY_REJECTED', maxRounds: 2, revisions: [
      { round: 1, status: 'APPLIED', reviewReasons: ['POLICY_CONTENT_REVIEW:必须保留原方法的权限范围。'] },
      { round: 2, status: 'REJECTED', reviewReasons: ['POLICY_CONTENT_REVIEW:证据仍不足以扩大范围。'] },
    ] } });
  open(); await screen.findByText('必须保留原方法的权限范围。');
  expect(screen.getByText(/两轮修订后仍未通过/)).toBeInTheDocument();
  expect(screen.queryByText(/POLICY_CONTENT_REVIEW:/)).not.toBeInTheDocument();
  expect(opsAdminService.skillContentRevisions).toHaveBeenCalledWith('candidate', 'project-a');
});
it('keeps an interrupted model call pending and never describes it as a completed failed correction', async () => {
  vi.mocked(opsAdminService.skillContentRevisions).mockResolvedValue({ code: '0000', info: 'ok', data: {
    candidateStatus: 'POLICY_REJECTED', revisions: [{ round: 1, status: 'PENDING', reviewReasons: ['需要保留成立条件。'] }] } });
  open(); await screen.findByText('第 1 轮：已保存进度，等待自动重试');
  expect(screen.queryByText(/两轮修订后仍未通过/)).not.toBeInTheDocument();
});
it('does not present an unavailable revision history as an empty successful history', async () => {
  vi.mocked(opsAdminService.skillContentRevisions).mockRejectedValue(new Error('network unavailable'));
  open(); await screen.findByText(/network unavailable|修订记录暂时无法读取/);
  expect(screen.queryByText('按审查意见自动修订')).not.toBeInTheDocument();
});
