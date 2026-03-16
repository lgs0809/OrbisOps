import { BrowserRouter as Router, Navigate, Route, Routes, useLocation } from 'react-router-dom';
import React, { Suspense, useEffect } from 'react';

import { createRoot } from 'react-dom/client';

import { installHttpInterceptor } from './services/http-interceptor';
import { GlobalStyle } from './styles/global';
import { ProjectScopeProvider } from './hooks/use-project-scope';
import { AppQueryProvider } from './providers/app-query-provider';
import { AppErrorBoundary } from './components/common/AppErrorBoundary';
import {
  currentUserRole,
  defaultRouteForCurrentUser,
  isAuthenticated,
  UserRole,
} from './services/auth-session';
import { APP_ROUTES, pageTitleForPath } from './app/navigation/route-metadata';

installHttpInterceptor();

const LoginPage = React.lazy(() => import('./pages/login'));
const ChatWorkspacePage = React.lazy(() =>
  import('./pages/chat-workspace').then((module) => ({ default: module.ChatWorkspacePage })),
);
const OpsDashboardPage = React.lazy(() =>
  import('./pages/ops-dashboard').then((module) => ({ default: module.OpsDashboardPage })),
);
const UserAuditRecordsPage = React.lazy(() =>
  import('./pages/user-audit-records').then((module) => ({ default: module.UserAuditRecordsPage })),
);
const AnalysisTaskCenterPage = React.lazy(() =>
  import('./pages/analysis-task-center').then((module) => ({ default: module.AnalysisTaskCenterPage })),
);
const IncidentCenterPage = React.lazy(() =>
  import('./pages/incident-center').then((module) => ({ default: module.IncidentCenterPage })),
);
const AgentConfigProductPage = React.lazy(() =>
  import('./pages/agent-config-product-page').then((module) => ({ default: module.AgentConfigProductPage })),
);
const AgentListPage = React.lazy(() => import('./pages/agent-list').then((module) => ({ default: module.AgentListPage })));
const SkillManagementPage = React.lazy(() =>
  import('./pages/skill-management').then((module) => ({ default: module.SkillManagementPage })),
);
const ModelApiManagement = React.lazy(() =>
  import('./pages/model-api-management').then((module) => ({ default: module.ModelApiManagement })),
);
const RagOrderManagement = React.lazy(() =>
  import('./pages/rag-order-management').then((module) => ({ default: module.RagOrderManagement })),
);
const ChangeCenterPage = React.lazy(() =>
  import('./pages/change-package-center').then((module) => ({ default: module.ChangePackageCenterPage })),
);
const OpsStatusManagementPage = React.lazy(() =>
  import('./pages/ops-status-management').then((module) => ({ default: module.OpsStatusManagementPage })),
);
const ModelCatalogManagement = React.lazy(() =>
  import('./pages/model-catalog').then((module) => ({ default: module.ModelCatalogManagement })),
);
const McpToolManagement = React.lazy(() =>
  import('./pages/mcp-tool-management').then((module) => ({ default: module.McpToolManagement })),
);
const ProjectProductWorkspacePage = React.lazy(() =>
  import('./pages/project-product-workspace').then((module) => ({ default: module.ProjectProductWorkspacePage })),
);
const ProjectWorkspacePage = React.lazy(() =>
  import('./pages/project-workspace').then((module) => ({ default: module.ProjectWorkspacePage })),
);
const TaskScheduleManagement = React.lazy(() =>
  import('./pages/task-schedule-management').then((module) => ({ default: module.TaskScheduleManagement })),
);
const AlertTriggerManagementPage = React.lazy(() =>
  import('./pages/alert-trigger-management').then((module) => ({ default: module.AlertTriggerManagementPage })),
);
const ExecutionAdapterTemplatesPage = React.lazy(() =>
  import('./pages/execution-adapter-templates').then((module) => ({ default: module.ExecutionAdapterTemplatesPage })),
);
const MemoryManagementPage = React.lazy(() =>
  import('./pages/memory-management').then((module) => ({ default: module.MemoryManagementPage })),
);
const SkillEvolverManagementPage = React.lazy(() =>
  import('./pages/skill-evolver-management').then((module) => ({ default: module.SkillEvolverManagementPage })),
);
const ToolRoutingObservabilityPage = React.lazy(() =>
  import('./pages/tool-routing-observability').then((module) => ({ default: module.ToolRoutingObservabilityPage })),
);
const ChannelManagementPage = React.lazy(() =>
  import('./features/channels/pages/ChannelManagementPage').then((module) => ({ default: module.ChannelManagementPage })),
);

const PageLoading: React.FC = () => (
  <div style={{ minHeight: '100vh', display: 'grid', placeItems: 'center', color: '#4b5563' }}>加载中...</div>
);

const ProtectedRoute: React.FC<{ children: React.ReactNode; roles?: UserRole[] }> = ({ children, roles }) => {
  if (!isAuthenticated()) {
    return <Navigate to="/login" replace />;
  }
  if (roles?.length && !roles.includes(currentUserRole())) {
    return <Navigate to={defaultRouteForCurrentUser()} replace />;
  }
  return <>{children}</>;
};

const LoginRedirect: React.FC = () =>
  isAuthenticated() ? <Navigate to={defaultRouteForCurrentUser()} replace /> : <LoginPage />;

const CanonicalRouteRedirect: React.FC<{ to: string }> = ({ to }) => {
  const location = useLocation();
  return <Navigate to={`${to}${location.search}${location.hash}`} replace />;
};

const PageTitleSync: React.FC = () => {
  const location = useLocation();
  useEffect(() => {
    document.title = location.pathname === '/login' ? 'Sign in · OrbisOps' : pageTitleForPath(location.pathname);
  }, [location.pathname]);
  return null;
};

const pageElementByRouteId: Record<string, React.ReactNode> = {
  home: <OpsDashboardPage />,
  incidents: <IncidentCenterPage />,
  diagnosis: <ChatWorkspacePage />,
  changes: <ChangeCenterPage />,
  workflows: <AgentListPage />,
  'workflow-editor': <AgentConfigProductPage />,
  inspections: <TaskScheduleManagement />,
  'alert-triggers': <AlertTriggerManagementPage />,
  projects: <ProjectProductWorkspacePage />,
  'project-advanced': <ProjectWorkspacePage />,
  channels: <ChannelManagementPage />,
  models: <ModelApiManagement />,
  tools: <McpToolManagement />,
  knowledge: <RagOrderManagement />,
  skills: <SkillManagementPage />,
  'execution-targets': <ExecutionAdapterTemplatesPage />,
  governance: <OpsStatusManagementPage />,
  'model-catalog': <ModelCatalogManagement />,
  memory: <MemoryManagementPage />,
  'skill-evolver': <SkillEvolverManagementPage />,
  'tool-routing': <ToolRoutingObservabilityPage />,
  'analysis-tasks': <AnalysisTaskCenterPage />,
  'my-audit': <UserAuditRecordsPage />,
};

const AppRoutes: React.FC = () => (
  <Routes>
    <Route path="/login" element={<LoginRedirect />} />
    {APP_ROUTES.map((route) => {
      const page = pageElementByRouteId[route.id];
      if (!page) {
        throw new Error(`Missing page registration for route: ${route.id}`);
      }
      return (
        <React.Fragment key={route.id}>
          <Route
            path={route.path}
            element={
              <ProtectedRoute roles={route.roles}>
                {page}
              </ProtectedRoute>
            }
          />
          {route.legacyPaths?.map((legacyPath) => (
            <Route key={legacyPath} path={legacyPath} element={<CanonicalRouteRedirect to={route.path} />} />
          ))}
        </React.Fragment>
      );
    })}
    <Route path="/" element={<Navigate to="/login" replace />} />
    <Route path="*" element={<Navigate to="/login" replace />} />
  </Routes>
);

const App: React.FC = () => (
  <>
    <GlobalStyle />
    <AppErrorBoundary>
      <Router>
        <PageTitleSync />
        <AppQueryProvider>
          <ProjectScopeProvider>
            <Suspense fallback={<PageLoading />}>
              <AppRoutes />
            </Suspense>
          </ProjectScopeProvider>
        </AppQueryProvider>
      </Router>
    </AppErrorBoundary>
  </>
);

const app = createRoot(document.getElementById('root')!);

app.render(<App />);
