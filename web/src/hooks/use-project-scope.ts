import React, {
  PropsWithChildren,
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
} from 'react';
import { useQuery } from '@tanstack/react-query';
import { useLocation, useNavigate, useSearchParams } from 'react-router-dom';

import { getStoredUserInfo, isAdminUser, isAuthenticated } from '../services/auth-session';
import { opsAdminService } from '../services/ops-admin-service';
import { OpsProjectResourceTemplate, OpsProjectWorkspace, opsProjectService } from '../services/ops-project-service';
import { readProjectContextId, writeProjectContextId } from '../utils/project-context';

export interface ProjectScopeState {
  projects: OpsProjectWorkspace[];
  templates: OpsProjectResourceTemplate[];
  selectedProject?: OpsProjectWorkspace;
  projectId: string;
  loading: boolean;
  error?: string;
  selectProject: (projectId: string) => void;
  reloadProjects: () => Promise<void>;
}

const ProjectScopeContext = createContext<ProjectScopeState | null>(null);

const requestedProjectId = (search: string) => new URLSearchParams(search).get('projectId') || '';

interface AccessibleProjectSnapshot {
  projects: OpsProjectWorkspace[];
  templates: OpsProjectResourceTemplate[];
}

const loadAccessibleProjects = async (): Promise<AccessibleProjectSnapshot> => {
  if (isAdminUser()) {
    const response = await opsProjectService.snapshot();
    return {
      projects: response.data?.projects || [],
      templates: response.data?.templates || [],
    };
  }
  const response = await opsAdminService.listChatProjects();
  return {
    projects: Array.isArray(response.data) ? response.data : [],
    templates: [],
  };
};

export const ProjectScopeProvider: React.FC<PropsWithChildren> = ({ children }) => {
  const location = useLocation();
  const navigate = useNavigate();
  const [, setSearchParams] = useSearchParams();
  const authenticated = isAuthenticated();
  const roleScope = isAdminUser() ? 'admin' : 'user';
  const storedPrincipal = getStoredUserInfo();
  const principalKey = authenticated ? (storedPrincipal.userId || storedPrincipal.username || 'authenticated-user') : 'anonymous';

  const projectQuery = useQuery({
    queryKey: ['project-scope', roleScope, principalKey],
    queryFn: loadAccessibleProjects,
    enabled: authenticated,
  });
  const projects = authenticated ? (projectQuery.data?.projects || []) : [];
  const templates = authenticated ? (projectQuery.data?.templates || []) : [];

  const fromUrl = requestedProjectId(location.search);
  const rememberedProjectId = readProjectContextId();
  const projectId = useMemo(() => {
    if (!authenticated || projectQuery.isPending || projectQuery.isError || projects.length === 0) return '';
    if (fromUrl && projects.some((project) => project.projectId === fromUrl)) return fromUrl;
    if (rememberedProjectId && projects.some((project) => project.projectId === rememberedProjectId)) {
      return rememberedProjectId;
    }
    return projects[0]?.projectId || '';
  }, [authenticated, fromUrl, projectQuery.isError, projectQuery.isPending, projects, rememberedProjectId]);

  const selectProject = useCallback((nextProjectId: string) => {
    writeProjectContextId(nextProjectId);
    const next = new URLSearchParams(location.search);
    if (nextProjectId) next.set('projectId', nextProjectId);
    else next.delete('projectId');
    const search = next.toString();
    navigate({ pathname: location.pathname, search: search ? `?${search}` : '' }, { replace: true });
  }, [location.pathname, location.search, navigate]);

  useEffect(() => {
    if (!authenticated) {
      if (readProjectContextId()) writeProjectContextId('');
      return;
    }
    if (projectQuery.isPending || projectQuery.isError) return;

    if (projectId && readProjectContextId() !== projectId) writeProjectContextId(projectId);

    // A copied/deep-linked project must never leave an inaccessible project id in the address bar.
    if (fromUrl && fromUrl !== projectId) {
      setSearchParams((current) => {
        const next = new URLSearchParams(current);
        if (projectId) next.set('projectId', projectId);
        else next.delete('projectId');
        return next;
      }, { replace: true });
    }
  }, [authenticated, fromUrl, projectId, projectQuery.isError, projectQuery.isPending, setSearchParams]);

  const selectedProject = useMemo(
    () => projects.find((project) => project.projectId === projectId),
    [projectId, projects],
  );

  const reloadProjects = useCallback(async () => {
    await projectQuery.refetch();
  }, [projectQuery.refetch]);

  const value = useMemo<ProjectScopeState>(() => ({
    projects,
    templates,
    selectedProject,
    projectId: selectedProject?.projectId || '',
    loading: projectQuery.isFetching,
    error: projectQuery.error instanceof Error ? projectQuery.error.message : projectQuery.error ? '项目列表加载失败' : undefined,
    selectProject,
    reloadProjects,
  }), [projectId, projectQuery.error, projectQuery.isFetching, projects, reloadProjects, selectProject, selectedProject, templates]);

  return React.createElement(ProjectScopeContext.Provider, { value }, children);
};

export const useProjectScope = (): ProjectScopeState => {
  const context = useContext(ProjectScopeContext);
  if (!context) throw new Error('useProjectScope must be used inside ProjectScopeProvider');
  return context;
};
