import { BrowserRouter as Router, Routes, Route, Navigate, useLocation } from 'react-router-dom';
import React, { Suspense } from 'react';

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
  import('./pages/channel-management').then((module) => ({ default: module.ChannelManagementPage })),
);

const PageLoading: React.FC = () => (
  <div style={{ minHeight: '100vh', display: 'grid', placeItems: 'center', color: '#4b5563' }}>加载中...</div>
);

// 路由保护组件
const ProtectedRoute: React.FC<{ children: React.ReactNode; roles?: UserRole[] }> = ({ children, roles }) => {
  if (!isAuthenticated()) {
    return <Navigate to="/login" replace />;
  }
  if (roles?.length && !roles.includes(currentUserRole())) {
    return <Navigate to={defaultRouteForCurrentUser()} replace />;
  }
  return <>{children}</>;
};

// 登录重定向组件
const LoginRedirect: React.FC = () =>
  isAuthenticated() ? <Navigate to={defaultRouteForCurrentUser()} replace /> : <LoginPage />;

const LegacyExecutionRedirect: React.FC = () => {
  const location = useLocation();
  return <Navigate to={`/executions${location.search}`} replace />;
};

const App: React.FC = () => (
  <>
    <GlobalStyle />
    <AppErrorBoundary>
    <Router>
      <AppQueryProvider>
        <ProjectScopeProvider>
          <Suspense fallback={<PageLoading />}>
          <Routes>
        <Route path="/login" element={<LoginRedirect />} />
        <Route
          path="/dashboard"
          element={
            <ProtectedRoute roles={['admin', 'user']}>
              <OpsDashboardPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/chat"
          element={
            <ProtectedRoute roles={['admin', 'user']}>
              <ChatWorkspacePage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/incidents"
          element={
            <ProtectedRoute roles={['admin', 'user']}>
              <IncidentCenterPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/executions"
          element={
            <ProtectedRoute roles={['admin', 'user']}>
              <ChangeCenterPage />
            </ProtectedRoute>
          }
        />
        <Route path="/my-executions" element={<LegacyExecutionRedirect />} />
        <Route
          path="/my-audit"
          element={
            <ProtectedRoute roles={['user']}>
              <UserAuditRecordsPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/analysis-tasks"
          element={
            <ProtectedRoute roles={['admin']}>
              <AnalysisTaskCenterPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/agent-config"
          element={
            <ProtectedRoute roles={['admin']}>
              <AgentConfigProductPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/agent-list"
          element={
            <ProtectedRoute roles={['admin']}>
              <AgentListPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/skill-management"
          element={
            <ProtectedRoute roles={['admin']}>
              <SkillManagementPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/model-api-management"
          element={
            <ProtectedRoute roles={['admin']}>
              <ModelApiManagement />
            </ProtectedRoute>
          }
        />
        <Route
          path="/rag-order-management"
          element={
            <ProtectedRoute roles={['admin']}>
              <RagOrderManagement />
            </ProtectedRoute>
          }
        />
        <Route path="/change-center" element={<LegacyExecutionRedirect />} />
        <Route
          path="/audit"
          element={
            <ProtectedRoute roles={['admin']}>
              <OpsStatusManagementPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/inspections"
          element={
            <ProtectedRoute roles={['admin']}>
              <TaskScheduleManagement />
            </ProtectedRoute>
          }
        />
        <Route
          path="/alerts"
          element={
            <ProtectedRoute roles={['admin']}>
              <AlertTriggerManagementPage />
            </ProtectedRoute>
          }
        />
        <Route path="/alert-trigger-management" element={<Navigate to="/alerts" replace />} />
        <Route path="/chat-workspace" element={<Navigate to="/chat" replace />} />
        <Route path="/execution-center" element={<LegacyExecutionRedirect />} />
        <Route
          path="/model-catalog"
          element={
            <ProtectedRoute roles={['admin']}>
              <ModelCatalogManagement />
            </ProtectedRoute>
          }
        />
        <Route
          path="/mcp-tool-management"
          element={
            <ProtectedRoute roles={['admin']}>
              <McpToolManagement />
            </ProtectedRoute>
          }
        />
        <Route
          path="/execution-adapter-templates"
          element={
            <ProtectedRoute roles={['admin']}>
              <ExecutionAdapterTemplatesPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/memory-management"
          element={
            <ProtectedRoute roles={['admin']}>
              <MemoryManagementPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/skill-evolver-management"
          element={
            <ProtectedRoute roles={['admin']}>
              <SkillEvolverManagementPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/tool-routing-observability"
          element={
            <ProtectedRoute roles={['admin']}>
              <ToolRoutingObservabilityPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/project-workspace"
          element={
            <ProtectedRoute roles={['admin']}>
              <ProjectProductWorkspacePage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/project-workspace/advanced"
          element={
            <ProtectedRoute roles={['admin']}>
              <ProjectWorkspacePage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/channel-management"
          element={
            <ProtectedRoute roles={['admin']}>
              <ChannelManagementPage />
            </ProtectedRoute>
          }
        />
        <Route path="/" element={<Navigate to="/login" replace />} />
        <Route path="*" element={<Navigate to="/login" replace />} />
          </Routes>
          </Suspense>
        </ProjectScopeProvider>
      </AppQueryProvider>
    </Router>
    </AppErrorBoundary>
  </>
);

const app = createRoot(document.getElementById('root')!);

app.render(<App />);
