import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, useLocation } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({
  listChatProjects: vi.fn(),
  snapshot: vi.fn(),
}));

vi.mock('../services/auth-session', () => ({
  getStoredUserInfo: () => ({ username: 'bob', userId: 'u-bob', role: 'user' }),
  isAdminUser: () => false,
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
      <div data-testid="project-search">{location.search}</div>
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
});
