import React, {
  PropsWithChildren,
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
} from 'react';
import { useQuery } from '@tanstack/react-query';
import { useLocation, useSearchParams } from 'react-router-dom';

import { getStoredUserInfo, isAdminUser, isAuthenticated } from '../services/auth-session';
import { opsAdminService } from '../services/ops-admin-service';
import { OpsProjectWorkspace, opsProjectService } from '../services/ops-project-service';
import { readProjectContextId, writeProjectContextId } from '../utils/project-context';

export interface ProjectScopeState {
  projects: OpsProjectWorkspace[];
  selectedProject?: OpsProjectWorkspace;
  projectId: string;
  loading: boolean;
  error?: string;
  selectProject: (projectId: string) => void;
  reloadProjects: () => Promise<void>;
}

const ProjectScopeContext = createContext<ProjectScopeState | null>(null);

const requestedProjectId = (search: string) => new URLSearchParams(search).get('projectId') || '';

const loadAccessibleProjects = async (): Promise<OpsProjectWorkspace[]> => {
  const response = isAdminUser()
    ? await opsProjectService.snapshot()
    : await opsAdminService.listChatProjects();
  return Array.isArray(response.data) ? response.data : response.data?.projects || [];
};

export const ProjectScopeProvider: React.FC<PropsWithChildren> = ({ children }) => {
  const location = useLocation();
  const [, setSearchParams] = useSearchParams();
  const authenticated = isAuthenticated();
  const roleScope = isAdminUser() ? 'admin' : 'user';
  const storedPrincipal = getStoredUserInfo();
  const principalKey = authenticated ? (storedPrincipal.userId || storedPrincipal.username || 'authenticated-user') : 'anonymous';
  const [projectId, setProjectId] = useState(() => requestedProjectId(location.search) || readProjectContextId());

  const projectQuery = useQuery({
    queryKey: ['project-scope', roleScope, principalKey],
    queryFn: loadAccessibleProjects,
    enabled: authenticated,
  });
  const projects = authenticated ? (projectQuery.data || []) : [];

  const persistSelection = useCallback((nextProjectId: string) => {
    setProjectId(nextProjectId);
    writeProjectContextId(nextProjectId);
  }, []);

  const selectProject = useCallback((nextProjectId: string) => {
    persistSelection(nextProjectId);
    setSearchParams((current) => {
      const next = new URLSearchParams(current);
      if (nextProjectId) next.set('projectId', nextProjectId);
      else next.delete('projectId');
      return next;
    }, { replace: true });
  }, [persistSelection, setSearchParams]);

  useEffect(() => {
    if (!authenticated) {
      if (projectId) persistSelection('');
      return;
    }
    if (projectQuery.isPending || projectQuery.isError) return;

    const fromUrl = requestedProjectId(location.search);
    const remembered = readProjectContextId();
    const preferred = fromUrl || projectId || remembered;
    const nextProjectId = projects.some((project) => project.projectId === preferred)
      ? preferred
      : projects[0]?.projectId || '';
    if (nextProjectId !== projectId) persistSelection(nextProjectId);

    // A copied/deep-linked project must never leave an inaccessible project id in the address bar.
    if (fromUrl && fromUrl !== nextProjectId) {
      setSearchParams((current) => {
        const next = new URLSearchParams(current);
        if (nextProjectId) next.set('projectId', nextProjectId);
        else next.delete('projectId');
        return next;
      }, { replace: true });
    }
  }, [authenticated, location.search, persistSelection, projectId, projectQuery.isError, projectQuery.isPending, projects, setSearchParams]);

  // Browser back/forward changes the URL independently from local state; keep the global context in lockstep.
  useEffect(() => {
    const fromUrl = requestedProjectId(location.search);
    if (!fromUrl || fromUrl === projectId || projects.length === 0) return;
    if (projects.some((project) => project.projectId === fromUrl)) persistSelection(fromUrl);
  }, [location.search, persistSelection, projectId, projects]);

  const selectedProject = useMemo(
    () => projects.find((project) => project.projectId === projectId),
    [projectId, projects],
  );

  const reloadProjects = useCallback(async () => {
    await projectQuery.refetch();
  }, [projectQuery.refetch]);

  const value = useMemo<ProjectScopeState>(() => ({
    projects,
    selectedProject,
    projectId: selectedProject?.projectId || '',
    loading: projectQuery.isFetching,
    error: projectQuery.error instanceof Error ? projectQuery.error.message : projectQuery.error ? '项目列表加载失败' : undefined,
    selectProject,
    reloadProjects,
  }), [projectId, projectQuery.error, projectQuery.isFetching, projects, reloadProjects, selectProject, selectedProject]);

  return React.createElement(ProjectScopeContext.Provider, { value }, children);
};

export const useProjectScope = (): ProjectScopeState => {
  const context = useContext(ProjectScopeContext);
  if (!context) throw new Error('useProjectScope must be used inside ProjectScopeProvider');
  return context;
};
