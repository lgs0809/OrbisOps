import { BrowserRouter as Router, Navigate, Route, Routes, useLocation } from 'react-router-dom';
import React, { Suspense, useEffect } from 'react';

import { createRoot } from 'react-dom/client';

import { installHttpInterceptor } from './services/http-interceptor';
import { GlobalStyle } from './styles/global';
import { ProjectScopeProvider } from './hooks/use-project-scope';
import { AppQueryProvider } from './providers/app-query-provider';
import { AppErrorBoundary } from './components/common/AppErrorBoundary';
import { GlobalCommandPaletteProvider } from './features/search/components/GlobalCommandPalette';
import {
  currentUserRole,
  defaultRouteForCurrentUser,
  isAuthenticated,
  UserRole,
} from './services/auth-session';
import { APP_ROUTES, pageTitleForPath } from './app/navigation/route-metadata';

installHttpInterceptor();

const LoginPage = React.lazy(() => import('./pages/login'));
const ChatPage = React.lazy(() =>
  import('./pages/chat').then((module) => ({ default: module.ChatPage })),
);
const OpsDashboardPage = React.lazy(() =>
  import('./pages/ops-dashboard').then((module) => ({ default: module.OpsDashboardPage })),
);
const AgentConfigProductPage = React.lazy(() =>
  import('./pages/agent-config-product-page').then((module) => ({ default: module.AgentConfigProductPage })),
);
const AgentListPage = React.lazy(() => import('./pages/agent-list').then((module) => ({ default: module.AgentListPage })));
const SkillsPage = React.lazy(() =>
  import('./pages/skills').then((module) => ({ default: module.SkillsPage })),
);
const ModelApiManagement = React.lazy(() =>
  import('./pages/model-api-management').then((module) => ({ default: module.ModelApiManagement })),
);
const PlatformSettingsPage = React.lazy(() =>
  import('./features/platform/pages/PlatformSettingsPage').then((module) => ({ default: module.PlatformSettingsPage })),
);
const KnowledgePage = React.lazy(() =>
  import('./pages/knowledge').then((module) => ({ default: module.KnowledgePage })),
);
const ChangeCenterPage = React.lazy(() =>
  import('./pages/change-package-center').then((module) => ({ default: module.ChangePackageCenterPage })),
);
const GovernancePage = React.lazy(() =>
  import('./pages/governance').then((module) => ({ default: module.GovernancePage })),
);
const ModelCatalogManagement = React.lazy(() =>
  import('./pages/model-catalog').then((module) => ({ default: module.ModelCatalogManagement })),
);
const ToolsMcpPage = React.lazy(() =>
  import('./pages/tools-mcp').then((module) => ({ default: module.ToolsMcpPage })),
);
const ProjectProductWorkspacePage = React.lazy(() =>
  import('./pages/project-product-workspace').then((module) => ({ default: module.ProjectProductWorkspacePage })),
);
const AutomationsPage = React.lazy(() =>
  import('./pages/automations').then((module) => ({ default: module.AutomationsPage })),
);
const TaskScheduleManagement = React.lazy(() =>
  import('./pages/task-schedule-management').then((module) => ({ default: module.TaskScheduleManagement })),
);
const AlertTriggersPage = React.lazy(() =>
  import('./pages/alert-triggers').then((module) => ({ default: module.AlertTriggersPage })),
);
const ExecutionTargetsPage = React.lazy(() =>
  import('./pages/execution-targets').then((module) => ({ default: module.ExecutionTargetsPage })),
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
const ChannelsPage = React.lazy(() =>
  import('./pages/channels').then((module) => ({ default: module.ChannelsPage })),
);
const UsersAccessPage = React.lazy(() =>
  import('./features/platform/pages/UsersAccessPage').then((module) => ({ default: module.UsersAccessPage })),
);
const WorkbenchPage = React.lazy(() =>
  import('./pages/workbench').then((module) => ({ default: module.WorkbenchPage })),
);
const IncidentCenterPage = React.lazy(() =>
  import('./pages/incident-center').then((module) => ({ default: module.IncidentCenterPage })),
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
    document.title = location.pathname === '/login' ? '登录 · OrbisOps' : pageTitleForPath(location.pathname);
  }, [location.pathname]);
  return null;
};

const pageElementByRouteId: Record<string, React.ReactNode> = {
  home: <OpsDashboardPage />,
  chat: <ChatPage />,
  workbench: <WorkbenchPage />,
  'workbench-events': <IncidentCenterPage />,
  workflows: <AgentListPage />,
  automations: <AutomationsPage />,
  'automation-schedules': <TaskScheduleManagement />,
  projects: <ProjectProductWorkspacePage />,
  changes: <ChangeCenterPage />,
  settings: <PlatformSettingsPage />,
  'workflow-editor': <AgentConfigProductPage />,
  'alert-triggers': <AlertTriggersPage />,
  'project-advanced': <ProjectProductWorkspacePage />,
  'settings-users': <UsersAccessPage />,
  channels: <ChannelsPage />,
  models: <ModelApiManagement />,
  tools: <ToolsMcpPage />,
  knowledge: <KnowledgePage />,
  skills: <SkillsPage />,
  'execution-targets': <ExecutionTargetsPage />,
  governance: <GovernancePage />,
  'model-catalog': <ModelCatalogManagement />,
  memory: <MemoryManagementPage />,
  'skill-evolver': <SkillEvolverManagementPage />,
  'tool-routing': <ToolRoutingObservabilityPage />,
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
            <GlobalCommandPaletteProvider>
              <Suspense fallback={<PageLoading />}>
                <AppRoutes />
              </Suspense>
            </GlobalCommandPaletteProvider>
          </ProjectScopeProvider>
        </AppQueryProvider>
      </Router>
    </AppErrorBoundary>
  </>
);

const app = createRoot(document.getElementById('root')!);

app.render(<App />);
