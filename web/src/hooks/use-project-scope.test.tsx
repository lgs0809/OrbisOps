import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

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
  return <div data-testid="project-id">{scope.projectId}</div>;
};

const deferred = <T,>() => {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((done) => {
    resolve = done;
  });
  return { promise, resolve };
};

describe('ProjectScopeProvider', () => {
  beforeEach(() => {
    localStorage.clear();
    mocks.listChatProjects.mockReset();
    mocks.snapshot.mockReset();
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
