import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { ProjectSkillVersionHistory } from './ProjectSkillVersionHistory';
import { opsAdminService } from '../../../services/ops-admin-service';

vi.mock('../../../services/ops-admin-service', () => ({ opsAdminService: { listProjectSkillVersions: vi.fn(), rollbackProjectSkillVersion: vi.fn() } }));
const success = (data: any) => ({ code: '0000', info: 'ok', data });
beforeEach(() => {
  vi.mocked(opsAdminService.listProjectSkillVersions).mockReset().mockResolvedValue(success([{ version: 2, content: 'new' }, { version: 1, content: 'old' }]));
  vi.mocked(opsAdminService.rollbackProjectSkillVersion).mockReset();
});
afterEach(cleanup);
const mount = () => {
  const changed = vi.fn().mockResolvedValue(undefined);
  render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })}>
    <ProjectSkillVersionHistory projectId="project-a" skillId="method" name="method" currentVersion={2} onChanged={changed} onClose={vi.fn()} />
  </QueryClientProvider>);
  return changed;
};
it('requires an explicit version choice and confirmation and prevents duplicate in-flight rollback', async () => {
  let resolve: (v: any) => void = () => {};
  vi.mocked(opsAdminService.rollbackProjectSkillVersion).mockImplementation(() => new Promise(r => { resolve = r; }));
  const changed = mount();
  fireEvent.click(await screen.findByRole('button', { name: '选择回滚到第 1 版' }));
  expect(opsAdminService.rollbackProjectSkillVersion).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole('button', { name: '确认回滚方法' }));
  await waitFor(() => expect(screen.getByRole('button', { name: '确认回滚方法' })).toBeDisabled());
  expect(opsAdminService.rollbackProjectSkillVersion).toHaveBeenCalledExactlyOnceWith('project-a', 'method', 1);
  resolve(success({ projectId: 'project-a', skillId: 'method', version: 3 }));
  expect(await screen.findByRole('status')).toHaveTextContent('索引就绪后使用');
  await waitFor(() => expect(changed).toHaveBeenCalledTimes(1));
});
it('keeps a rejected rollback visible without claiming success', async () => {
  vi.mocked(opsAdminService.rollbackProjectSkillVersion).mockResolvedValue({ code: '0001', info: '版本已变化', data: { name: 'method' } });
  const changed = mount();
  fireEvent.click(await screen.findByRole('button', { name: '选择回滚到第 1 版' }));
  fireEvent.click(screen.getByRole('button', { name: '确认回滚方法' }));
  expect(await screen.findByRole('alert')).toHaveTextContent('版本已变化');
  expect(screen.queryByRole('status')).toBeNull();expect(changed).not.toHaveBeenCalled();
});
