import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, useLocation } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({
  listChatProjects: vi.fn(),
  snapshot: vi.fn(),
  templates: vi.fn(),
  principal: { username: 'bob', userId: 'u-bob', role: 'user' },
}));

vi.mock('../services/auth-session', () => ({
  getStoredUserInfo: () => mocks.principal,
  isAdminUser: () => mocks.principal.role === 'admin',
  isAuthenticated: () => true,
}));

vi.mock('../services/ops-admin-service', () => ({
  opsAdminService: {
    listChatProjects: mocks.listChatProjects,
  },
}));

vi.mock('../services/ops-project-service', () => ({
  opsProjectService: {
    snapshot: mocks.snapshot,
    templates: mocks.templates,
  },
}));

import { ProjectScopeProvider, useProjectScope } from './use-project-scope';
import { writeProjectContextId } from '../utils/project-context';

const ScopeProbe = () => {
  const scope = useProjectScope();
  const location = useLocation();
  return (
    <>
      <div data-testid="project-id">{scope.projectId}</div>
      <div data-testid="project-pathname">{location.pathname}</div>
      <div data-testid="project-search">{location.search}</div>
      <div data-testid="project-error">{scope.error || ''}</div>
      <div data-testid="template-count">{scope.templates.length}</div>
      <button type="button" onClick={() => void scope.reloadProjects()}>reload catalog</button>
      <button type="button" onClick={() => scope.selectProject('project-two')}>select project two</button>
    </>
  );
};

const deferred = <T,>() => {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((done) => {
    resolve = done;
  });
  return { promise, resolve };
};

describe('ProjectScopeProvider', () => {
  afterEach(() => cleanup());

  beforeEach(() => {
    localStorage.clear();
    mocks.listChatProjects.mockReset();
    mocks.snapshot.mockReset();
    mocks.templates.mockReset().mockResolvedValue({ data: [] });
    mocks.principal = { username: 'bob', userId: 'u-bob', role: 'user' };
  });

  it('switches project through URL and remembered context without racing back to the previous selection', async () => {
    mocks.listChatProjects.mockResolvedValue({
      data: [
        { projectId: 'project-one', name: 'Project one' },
        { projectId: 'project-two', name: 'Project two' },
      ],
    });
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });

    render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={['/executions?projectId=project-one']}>
          <ProjectScopeProvider>
            <ScopeProbe />
          </ProjectScopeProvider>
        </MemoryRouter>
      </QueryClientProvider>,
    );

    await waitFor(() => expect(screen.getByTestId('project-id')).toHaveTextContent('project-one'));
    fireEvent.click(screen.getByRole('button', { name: 'select project two' }));

    await waitFor(() => {
      expect(screen.getByTestId('project-id')).toHaveTextContent('project-two');
      expect(screen.getByTestId('project-pathname')).toHaveTextContent('/executions');
      expect(screen.getByTestId('project-search')).toHaveTextContent('?projectId=project-two');
    });
    expect(localStorage.getItem('ops:selected-project-id')).toBe('project-two');
  });

  it('does not expose a remembered project until the current account catalog validates it', async () => {
    writeProjectContextId('project-from-previous-account');
    const projectCatalog = deferred<any>();
    mocks.listChatProjects.mockReturnValue(projectCatalog.promise);
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });

    render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={['/executions']}>
          <ProjectScopeProvider>
            <ScopeProbe />
          </ProjectScopeProvider>
        </MemoryRouter>
      </QueryClientProvider>,
    );

    expect(screen.getByTestId('project-id')).toHaveTextContent('');

    projectCatalog.resolve({
      data: [{ projectId: 'project-bob', name: 'Bob project' }],
    });

    await waitFor(() => {
      expect(screen.getByTestId('project-id')).toHaveTextContent('project-bob');
    });
  });

  it('keeps the last validated identity on a failed refetch, then applies actual revocation on recovery', async () => {
    mocks.listChatProjects.mockResolvedValueOnce({ data: [{ projectId: 'project-one', name: 'One' }] })
      .mockRejectedValueOnce(new Error('temporary connection failure'))
      .mockResolvedValueOnce({ data: [{ projectId: 'project-two', name: 'Two' }] });
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(<QueryClientProvider client={queryClient}><MemoryRouter initialEntries={['/chat?projectId=project-one']}>
      <ProjectScopeProvider><ScopeProbe /></ProjectScopeProvider>
    </MemoryRouter></QueryClientProvider>);
    await waitFor(() => expect(screen.getByTestId('project-id')).toHaveTextContent('project-one'));
    fireEvent.click(screen.getByRole('button', { name: 'reload catalog' }));
    await waitFor(() => expect(screen.getByTestId('project-error')).toHaveTextContent('temporary connection failure'));
    expect(screen.getByTestId('project-id')).toHaveTextContent('project-one');
    expect(screen.getByTestId('project-search')).toHaveTextContent('project-one');
    fireEvent.click(screen.getByRole('button', { name: 'reload catalog' }));
    await waitFor(() => expect(screen.getByTestId('project-id')).toHaveTextContent('project-two'));
    expect(screen.getByTestId('project-error')).toHaveTextContent('');
    expect(screen.getByTestId('project-search')).toHaveTextContent('project-two');
  });

  it('does not reuse the former principal catalog after switching accounts', async () => {
    mocks.listChatProjects.mockResolvedValueOnce({ data: [{ projectId: 'project-one', name: 'One' }] });
    const nextCatalog = deferred<any>();
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const view = () => <QueryClientProvider client={queryClient}><MemoryRouter initialEntries={['/chat?projectId=project-one']}>
      <ProjectScopeProvider><ScopeProbe /></ProjectScopeProvider>
    </MemoryRouter></QueryClientProvider>;
    const mounted = render(view());
    await waitFor(() => expect(screen.getByTestId('project-id')).toHaveTextContent('project-one'));
    mocks.principal = { username: 'alice', userId: 'u-alice', role: 'user' };
    mocks.listChatProjects.mockReturnValueOnce(nextCatalog.promise);
    mounted.rerender(view());
    expect(screen.getByTestId('project-id')).toHaveTextContent('');
    nextCatalog.resolve({ data: [{ projectId: 'project-alice', name: 'Alice' }] });
    await waitFor(() => expect(screen.getByTestId('project-id')).toHaveTextContent('project-alice'));
  });

  it('uses the permission catalog for admin scope without projecting every resource snapshot', async () => {
    mocks.principal = { username: 'admin', userId: 'u-admin', role: 'admin' };
    mocks.listChatProjects.mockResolvedValue({ data: [{ projectId: 'project-one', name: 'One' }] });
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(<QueryClientProvider client={queryClient}><MemoryRouter><ProjectScopeProvider><ScopeProbe />
    </ProjectScopeProvider></MemoryRouter></QueryClientProvider>);
    await waitFor(() => expect(screen.getByTestId('project-id')).toHaveTextContent('project-one'));
    expect(mocks.listChatProjects).toHaveBeenCalledWith('admin');
    expect(mocks.snapshot).not.toHaveBeenCalled();
  });

  it('loads admin templates independently without letting template failure erase authorized scope', async () => {
    mocks.principal = { username: 'admin', userId: 'u-admin', role: 'admin' };
    mocks.listChatProjects.mockResolvedValue({ data: [{ projectId: 'project-one', name: 'One' }] });
    mocks.templates.mockRejectedValueOnce(new Error('template service unavailable'));
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(<QueryClientProvider client={queryClient}><MemoryRouter><ProjectScopeProvider><ScopeProbe />
    </ProjectScopeProvider></MemoryRouter></QueryClientProvider>);
    await waitFor(() => expect(mocks.templates).toHaveBeenCalledTimes(1));
    await waitFor(() => expect(screen.getByTestId('project-id')).toHaveTextContent('project-one'));
    expect(screen.getByTestId('project-error').textContent).toBe('');
    expect(screen.getByTestId('template-count')).toHaveTextContent('0');
    mocks.templates.mockResolvedValueOnce({ data: [{ templateId: 'template-one' }] });
    await queryClient.refetchQueries({ queryKey: ['project-scope-templates', 'admin', 'u-admin'] });
    await waitFor(() => expect(screen.getByTestId('template-count')).toHaveTextContent('1'));
    expect(screen.getByTestId('project-id')).toHaveTextContent('project-one');
  });
});
