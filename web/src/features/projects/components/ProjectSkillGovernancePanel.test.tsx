import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { ProjectSkillGovernancePanel } from './ProjectSkillGovernancePanel';
import { opsAdminService } from '../../../services/ops-admin-service';

vi.mock('../../../services/ops-admin-service', () => ({ opsAdminService: { listProjectSkills: vi.fn(), updateProjectSkillStatus: vi.fn(), listProjectSkillVersions: vi.fn() } }));
const skill = (status = 'ENABLED', projectId = 'project-a') => ({ name: 'fixture-method', skillId: 'same-id', projectId, scope: 'PROJECT', version: 2, status });
const response = <T,>(data: T) => ({ code: '0000', info: 'ok', data });
beforeEach(() => {
  vi.mocked(opsAdminService.listProjectSkills).mockReset().mockResolvedValue(response([skill()]));
  vi.mocked(opsAdminService.updateProjectSkillStatus).mockReset();
  vi.mocked(opsAdminService.listProjectSkillVersions).mockReset().mockResolvedValue(response([{ version: 1, content: 'old' }]));
});
afterEach(cleanup);
const mount = (changed = vi.fn().mockResolvedValue(undefined)) => {
  render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })}>
    <ProjectSkillGovernancePanel projectId="project-a" onChanged={changed} />
  </QueryClientProvider>);
  return changed;
};

it('uses the project identity, prevents duplicate clicks and retains the disabled entry for reactivation', async () => {
  let finish: (v: ReturnType<typeof response<ReturnType<typeof skill>>>) => void = () => {};
  vi.mocked(opsAdminService.updateProjectSkillStatus).mockImplementation(() => new Promise((resolve) => { finish = resolve; }));
  const changed = mount();
  fireEvent.click(await screen.findByRole('button', { name: '停用 fixture-method' }));
  await waitFor(() => expect(screen.getByRole('button', { name: '停用 fixture-method' })).toBeDisabled());
  fireEvent.click(screen.getByRole('button', { name: '停用 fixture-method' }));
  expect(opsAdminService.updateProjectSkillStatus).toHaveBeenCalledExactlyOnceWith('project-a', 'same-id', 'DISABLED');
  vi.mocked(opsAdminService.listProjectSkills).mockResolvedValue(response([skill('DISABLED')]));
  finish(response(skill('DISABLED')));
  await screen.findByRole('button', { name: '启用 fixture-method' });
  expect(changed).toHaveBeenCalledTimes(1);
});

it('does not display success or refresh away the error on an HTTP 200 business rejection', async () => {
  vi.mocked(opsAdminService.updateProjectSkillStatus).mockResolvedValue({ code: '0001', info: '项目权限已撤销', data: skill() });
  const changed = mount();
  fireEvent.click(await screen.findByRole('button', { name: '停用 fixture-method' }));
  expect(await screen.findByRole('alert')).toHaveTextContent('项目权限已撤销');
  expect(screen.getByRole('button', { name: '停用 fixture-method' })).toBeEnabled();
  expect(changed).not.toHaveBeenCalled();
});

it('does not expose another project or global same-ID entry as a mutable local Skill', async () => {
  vi.mocked(opsAdminService.listProjectSkills).mockResolvedValue(response([skill('ENABLED', 'project-b')]));
  mount();
  expect(await screen.findByRole('alert')).toHaveTextContent('项目 Skill 作用域不一致');
  expect(screen.queryByRole('button', { name: '停用 fixture-method' })).toBeNull();
  expect(opsAdminService.updateProjectSkillStatus).not.toHaveBeenCalled();
});

it('closes the historical selection when changing projects instead of retargeting it', async () => {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const panel = (projectId: string) => <QueryClientProvider client={client}><ProjectSkillGovernancePanel projectId={projectId} onChanged={vi.fn()} /></QueryClientProvider>;
  const view = render(panel('project-a'));
  fireEvent.click(await screen.findByRole('button', { name: '版本历史 fixture-method' }));
  await screen.findByRole('button', { name: '选择回滚到第 1 版' });
  vi.mocked(opsAdminService.listProjectSkills).mockResolvedValue(response([skill('ENABLED', 'project-b')]));
  view.rerender(panel('project-b'));
  await waitFor(() => expect(screen.queryByRole('button', { name: '选择回滚到第 1 版' })).toBeNull());
  expect(opsAdminService.listProjectSkillVersions).toHaveBeenCalledExactlyOnceWith('project-a', 'same-id');
});
