import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { LandingPostcheckVerifier } from './LandingPostcheckVerifier';
import { opsChangePackageService } from '../../../services/ops-change-package-service';
vi.mock('../../../services/ops-change-package-service', () => ({ opsChangePackageService: { verifyLanding: vi.fn() } }));
beforeEach(() => vi.clearAllMocks());
afterEach(cleanup);
const show = () => render(<QueryClientProvider client={new QueryClient({defaultOptions:{mutations:{retry:false}}})}><LandingPostcheckVerifier packageId="cp-1" scope="user"/></QueryClientProvider>);
it('reports a persisted failure as failure even when the HTTP request succeeded', async () => {
  vi.mocked(opsChangePackageService.verifyLanding).mockResolvedValue({code:'0000',info:'OK',data:{passed:false}});
  show(); fireEvent.click(screen.getByRole('button',{name:'复核落地结果'}));
  await screen.findByText('后置检查未通过，失败证据已保存。');
  expect(opsChangePackageService.verifyLanding).toHaveBeenCalledWith('cp-1','user');
  expect(opsChangePackageService.verifyLanding).toHaveBeenCalledTimes(1);
});
it('shows success only for an explicitly passed proof', async () => {
  vi.mocked(opsChangePackageService.verifyLanding).mockResolvedValue({code:'0000',info:'OK',data:{passed:true}});
  show(); fireEvent.click(screen.getByRole('button',{name:'复核落地结果'}));
  await screen.findByText('目标已通过全部已批准的后置检查，证据已保存。');
});
it('does not convert service errors into successful verification', async () => {
  vi.mocked(opsChangePackageService.verifyLanding).mockResolvedValue({code:'5000',info:'无法完成复核',data:{}});
  show(); fireEvent.click(screen.getByRole('button',{name:'复核落地结果'}));
  await screen.findByRole('status');
  expect(screen.queryByText('目标已通过全部已批准的后置检查，证据已保存。')).not.toBeInTheDocument();
});
