import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import styled from 'styled-components';
import {
  Avatar,
  Button,
  Checkbox,
  Input,
  Layout,
  Popconfirm,
  Select,
  Space,
  Spin,
  Tag,
  TextArea,
  Toast,
  Tooltip,
  Typography,
} from '@douyinfe/semi-ui';
import {
  IconBranch,
  IconComment,
  IconDelete,
  IconFile,
  IconPlus,
  IconRefresh,
  IconSend,
  IconSetting,
} from '@douyinfe/semi-icons';
import { clearAuthSession, isAdminUser } from '../services/auth-session';

import { Header, Sidebar, SIDEBAR_COLLAPSED_WIDTH, SIDEBAR_WIDTH } from '../components/layout';
import { theme } from '../styles/theme';
import { useResponsiveSidebar } from '../hooks/use-responsive-sidebar';
import { useProjectScope } from '../hooks/use-project-scope';
import {
  OpsAgentDefinition,
  OpsChatMessage,
  OpsIncident,
  OpsChatSession,
  OpsRuntimeEvent,
  opsAdminService,
} from '../services/ops-admin-service';
import { opsChangePackageService } from '../services/ops-change-package-service';
import type { OpsChangePackage } from '../services/ops-change-package-types';
import { restoreRuntimeEvents } from '../features/chat/chat-history';
import { summarizeChatRun } from '../features/chat/chat-run-summary';
import { sanitizeVisibleAnswer } from '../features/chat/live-run-trace';
import { AssistantAnswer } from '../features/chat/AssistantAnswer';
import { chatPreview } from '../features/chat/chat-presentation';

const { Content } = Layout;
const { Text, Title, Paragraph } = Typography;
const { Option } = Select;
const NO_AGENT_NAME = '项目主助手';

type UserInfo = {
  username: string;
  loginTime: string;
  token: string;
};

type RagSource = {
  docName?: string;
  chunkId?: string;
  chunkContent?: string;
  knowledgeBaseId?: string;
  score?: string;
};

type ChatMessage = {
  id: string;
  role: 'user' | 'assistant' | 'system';
  content: string;
  createdAt: string;
  thinking?: string;
  sources?: RagSource[];
};

const PageLayout = styled(Layout)`
  min-height: 100vh;
  min-width: 0;
  width: 100%;
  background: ${theme.colors.bg.secondary};
  overflow-x: hidden;
`;

const MainContent = styled.div<{ $collapsed: boolean }>`
  min-height: 100vh;
  min-width: 0;
  width: ${(props) =>
    props.$collapsed
      ? `calc(100vw - ${SIDEBAR_COLLAPSED_WIDTH}px)`
      : `calc(100vw - ${SIDEBAR_WIDTH}px)`};
  margin-left: ${(props) => (props.$collapsed ? `${SIDEBAR_COLLAPSED_WIDTH}px` : `${SIDEBAR_WIDTH}px`)};
  display: flex;
  flex: 1;
  flex-direction: column;
  background: ${theme.colors.bg.secondary};
  overflow-x: hidden;
  transition:
    width ${theme.animation.duration.normal} ${theme.animation.easing.cubic},
    margin-left ${theme.animation.duration.normal} ${theme.animation.easing.cubic};

  @media (max-width: ${theme.breakpoints.md}) {
    width: 100vw;
    margin-left: 0;
  }
`;

const ContentArea = styled(Content)`
  width: 100%;
  max-width: 1440px;
  min-width: 0;
  margin: 0 auto;
  padding: 24px;
  overflow-x: hidden;

  @media (max-width: 1280px) {
    padding: 16px;
  }
`;

const Workspace = styled.div`
  display: grid;
  grid-template-columns: minmax(240px, 320px) minmax(0, 1fr);
  gap: ${theme.spacing.base};
  min-height: calc(100vh - 96px);
  min-width: 0;

  @media (max-width: 960px) {
    grid-template-columns: 1fr;
  }
`;

const Panel = styled.div`
  min-width: 0;
  border: 1px solid ${theme.colors.border.secondary};
  border-radius: ${theme.borderRadius.base};
  background: ${theme.colors.bg.primary};
  overflow: hidden;
`;

const PanelHeader = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: ${theme.spacing.sm};
  padding: ${theme.spacing.base};
  border-bottom: 1px solid ${theme.colors.border.secondary};
  min-width: 0;
  flex-wrap: wrap;
`;

const SessionList = styled.div`
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: ${theme.spacing.sm};
  max-height: calc(100vh - 292px);
  overflow: auto;
  min-width: 0;
`;

const SessionItem = styled.div<{ $active?: boolean }>`
  width: 100%;
  border: 1px solid ${(props) => (props.$active ? theme.colors.primary : theme.colors.border.secondary)};
  border-radius: ${theme.borderRadius.base};
  background: ${(props) => (props.$active ? '#eff6ff' : theme.colors.bg.primary)};
  color: ${theme.colors.text.primary};
  padding: 10px;
  text-align: left;
  cursor: pointer;

  &:hover {
    border-color: ${theme.colors.primary};
  }
`;

const ChatPanel = styled(Panel)`
  display: flex;
  flex-direction: column;
  min-height: 0;
`;

const RuntimePanel = styled(Panel)`
  grid-column: 1 / -1;
  min-height: 0;
`;

const ChatHeader = styled(PanelHeader)`
  align-items: flex-start;
`;

const MessageList = styled.div`
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: ${theme.spacing.lg};
  background: #f8fafc;
  min-width: 0;
`;

const MessageRow = styled.div<{ $role: string }>`
  display: flex;
  justify-content: ${(props) => (props.$role === 'user' ? 'flex-end' : 'flex-start')};
  margin-bottom: ${theme.spacing.base};
`;

const MessageBubble = styled.div<{ $role: string }>`
  width: fit-content;
  max-width: min(760px, 86%);
  border: 1px solid ${(props) => (props.$role === 'user' ? '#bfdbfe' : theme.colors.border.secondary)};
  border-radius: ${theme.borderRadius.base};
  background: ${(props) => (props.$role === 'user' ? '#eff6ff' : theme.colors.bg.primary)};
  color: ${theme.colors.text.primary};
  padding: 12px 14px;
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.7;
  box-shadow: ${theme.shadows.sm};
`;

const MessageMeta = styled.div`
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
  color: ${theme.colors.text.tertiary};
  font-size: 12px;
`;

const Composer = styled.div`
  border-top: 1px solid ${theme.colors.border.secondary};
  padding: ${theme.spacing.base};
  background: ${theme.colors.bg.primary};
  min-width: 0;
`;

const EventList = styled.div`
  max-height: 360px;
  overflow: auto;
  padding: ${theme.spacing.base};
  min-width: 0;
`;

const PackageList = styled.div`
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: 10px;
  padding: ${theme.spacing.base};
  border-bottom: 1px solid ${theme.colors.border.secondary};
  min-width: 0;
`;

const PackageCard = styled.div`
  min-width: 0;
  border: 1px solid ${theme.colors.border.secondary};
  border-radius: ${theme.borderRadius.base};
  background: #ffffff;
  padding: 12px;
`;

const EventItem = styled.div<{ $status?: string }>`
  border: 1px solid
    ${(props) =>
      props.$status === 'FAILED' || props.$status === 'ERROR'
        ? theme.colors.error
        : props.$status === 'RUNNING'
        ? theme.colors.primary
        : props.$status === 'SUCCEEDED'
        ? theme.colors.success
        : theme.colors.border.secondary};
  border-radius: ${theme.borderRadius.base};
  padding: 10px;
  margin-bottom: 8px;
  background: ${theme.colors.bg.primary};
  min-width: 0;
`;

const TraceDetails = styled.details`
  width: 100%;
  min-width: 0;

  summary {
    cursor: pointer;
    color: ${theme.colors.text.tertiary};
    font-size: 12px;
  }
`;

const EventPayload = styled.pre`
  margin: 6px 0 0;
  width: 100%;
  max-width: 100%;
  max-height: 220px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-word;
  border: 1px solid ${theme.colors.border.secondary};
  border-radius: ${theme.borderRadius.sm};
  background: ${theme.colors.bg.secondary};
  padding: 8px;
  color: ${theme.colors.text.secondary};
  font-size: 12px;
  line-height: 1.55;
`;

const SourceBox = styled.div`
  margin-top: 10px;
  border: 1px solid #fde68a;
  border-radius: ${theme.borderRadius.base};
  background: #fffbeb;
  overflow: hidden;
`;

const SourceHeader = styled.div`
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 7px 10px;
  border-bottom: 1px solid #fde68a;
  color: #92400e;
  font-weight: ${theme.typography.fontWeight.semibold};
`;

const SourceItem = styled.div`
  padding: 8px 10px;
  border-bottom: 1px solid #fde68a;
  color: #78350f;
  font-size: 12px;
  line-height: 1.55;

  &:last-child {
    border-bottom: none;
  }
`;

const EmptyState = styled.div`
  height: 100%;
  min-height: 220px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: ${theme.colors.text.tertiary};
`;

const WarningBanner = styled.div`
  margin: ${theme.spacing.base} ${theme.spacing.base} 0;
  padding: 10px 12px;
  border: 1px solid #fde68a;
  border-radius: ${theme.borderRadius.base};
  background: #fffbeb;
  color: #92400e;
  line-height: 1.5;
`;

const RunSummaryBar = styled.div<{ $stage: string }>`
  margin: ${theme.spacing.base} ${theme.spacing.base} 0;
  padding: 12px 14px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: ${theme.spacing.base};
  border: 1px solid ${(props) => props.$stage === 'blocked' ? '#fecaca' : props.$stage === 'action' ? '#fde68a' : '#bfdbfe'};
  border-radius: ${theme.borderRadius.base};
  background: ${(props) => props.$stage === 'blocked' ? '#fef2f2' : props.$stage === 'action' ? '#fffbeb' : '#eff6ff'};
  color: ${theme.colors.text.primary};

  .run-copy {
    min-width: 0;
  }

  .run-title {
    display: flex;
    align-items: center;
    gap: 8px;
    font-weight: ${theme.typography.fontWeight.semibold};
  }

  .run-detail {
    margin-top: 3px;
    color: ${theme.colors.text.secondary};
    font-size: ${theme.typography.fontSize.sm};
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  @media (max-width: ${theme.breakpoints.sm}) {
    align-items: flex-start;
    flex-direction: column;

    .run-detail {
      white-space: normal;
    }
  }
`;

const nowText = () => new Date().toLocaleString();
const messageId = () => `msg_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`;

const toChatMessage = (message: OpsChatMessage): ChatMessage => ({
  id: message.messageId || messageId(),
  role: message.role === 'user' ? 'user' : message.role === 'system' ? 'system' : 'assistant',
  content: sanitizeVisibleAnswer(message.content),
  createdAt: message.createdAt || nowText(),
});

const eventTitle = (event: OpsRuntimeEvent) =>
  event.nodeId || event.agent || event.source || event.eventType || '-';

const eventColor = (status?: string) => {
  if (status === 'FAILED' || status === 'ERROR') return 'red';
  if (status === 'RUNNING') return 'blue';
  if (status === 'SUCCEEDED') return 'green';
  if (status === 'NOT_FOUND') return 'orange';
  return 'grey';
};

const eventMetricText = (event: OpsRuntimeEvent) => {
  const payload = event.payload || {};
  const parts: string[] = [];
  if (payload.requestToFirstTokenMs !== undefined) parts.push(`端到端 TTFT ${payload.requestToFirstTokenMs}ms`);
  if (payload.modelTtftMs !== undefined) parts.push(`模型 TTFT ${payload.modelTtftMs}ms`);
  if (payload.durationMs !== undefined) parts.push(`耗时 ${payload.durationMs}ms`);
  if (payload.documentCount !== undefined) parts.push(`命中文档 ${payload.documentCount}`);
  if (payload.streamingOptimized !== undefined) parts.push(payload.streamingOptimized ? '流式轻量检索' : '完整检索');
  if (payload.rerankEnabled !== undefined) parts.push(payload.rerankEnabled ? 'rerank 开启' : 'rerank 跳过');
  return parts.join(' · ');
};

const shortTraceText = (value: unknown, max = 1600) => {
  if (value === undefined || value === null || value === '') {
    return '';
  }
  const text = typeof value === 'string' ? value : JSON.stringify(value, null, 2);
  return text.length > max ? `${text.slice(0, max)}...` : text;
};

const eventTraceDetail = (event: OpsRuntimeEvent) => {
  const payload = event.payload || {};
  const lines: string[] = [];
  if (event.eventType?.startsWith('TOOL_CALL_')) {
    lines.push(`toolKind: ${shortTraceText(payload.toolKind || 'tool', 80)}`);
    lines.push(`toolName: ${shortTraceText(payload.toolName || event.agent || '-', 120)}`);
    if (payload.skillNames) lines.push(`skillNames: ${shortTraceText(payload.skillNames, 300)}`);
    if (payload.input !== undefined) lines.push(`input:\n${shortTraceText(payload.input)}`);
    if (payload.output !== undefined) lines.push(`output:\n${shortTraceText(payload.output)}`);
    if (payload.error !== undefined) lines.push(`error:\n${shortTraceText(payload.error, 800)}`);
    return lines.filter(Boolean).join('\n');
  }
  if (event.eventType?.startsWith('MODEL_CALL_')) {
    if (payload.modelId) lines.push(`modelId: ${shortTraceText(payload.modelId, 120)}`);
    if (payload.agentName || event.agent) lines.push(`agent: ${shortTraceText(payload.agentName || event.agent, 120)}`);
    if (payload.promptChars !== undefined) lines.push(`promptChars: ${payload.promptChars}`);
    if (payload.systemPrompt !== undefined) lines.push(`systemPrompt:\n${shortTraceText(payload.systemPrompt)}`);
    if (payload.userPrompt !== undefined) lines.push(`userPrompt:\n${shortTraceText(payload.userPrompt)}`);
    if (payload.output !== undefined) lines.push(`output:\n${shortTraceText(payload.output)}`);
    if (payload.error !== undefined) lines.push(`error:\n${shortTraceText(payload.error, 800)}`);
    return lines.filter(Boolean).join('\n');
  }
  if (event.eventType?.startsWith('QUERY_REWRITE_')) {
    if (payload.originalQuestion !== undefined) lines.push(`originalQuestion:\n${shortTraceText(payload.originalQuestion)}`);
    if (payload.rewrittenQuestion !== undefined) lines.push(`rewrittenQuestion:\n${shortTraceText(payload.rewrittenQuestion)}`);
    if (payload.reason !== undefined) lines.push(`reason:\n${shortTraceText(payload.reason, 800)}`);
    if (payload.resolvedReferences !== undefined) lines.push(`resolvedReferences:\n${shortTraceText(payload.resolvedReferences, 800)}`);
    return lines.filter(Boolean).join('\n');
  }
  if (event.eventType === 'SOURCE_QUERY_FINISHED') {
    if (payload.sourceType) lines.push(`sourceType: ${shortTraceText(payload.sourceType, 120)}`);
    if (payload.source) lines.push(`source: ${shortTraceText(payload.source, 160)}`);
    if (payload.resultId) lines.push(`resultId: ${shortTraceText(payload.resultId, 200)}`);
    if (payload.outputHash) lines.push(`outputHash: ${shortTraceText(payload.outputHash, 200)}`);
    if (payload.evidenceId) lines.push(`evidenceId: ${shortTraceText(payload.evidenceId, 200)}`);
    if (payload.fullOutputRef) lines.push(`fullOutputRef: ${shortTraceText(payload.fullOutputRef, 240)}`);
    if (payload.verified !== undefined) lines.push(`verified: ${String(payload.verified)}`);
    if (payload.structuredSummary !== undefined) lines.push(`structuredSummary:\n${shortTraceText(payload.structuredSummary)}`);
    return lines.filter(Boolean).join('\n');
  }
  return '';
};

export const ChatWorkspacePage: React.FC = () => {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const { collapsed, sidebarCollapsed, toggleSidebar, closeMobileSidebar } = useResponsiveSidebar();
  const projectScope = useProjectScope();
  const projects = projectScope.projects;
  const selectedProjectId = projectScope.projectId;
  const setSelectedProjectId = projectScope.selectProject;
  const [userInfo, setUserInfo] = useState<UserInfo | null>(null);
  const [incidents, setIncidents] = useState<OpsIncident[]>([]);
  const [selectedIncidentId, setSelectedIncidentId] = useState(searchParams.get('incidentId') || '');
  const [agents, setAgents] = useState<OpsAgentDefinition[]>([]);
  const [selectedAgentId, setSelectedAgentId] = useState<string>('');
  const [sessions, setSessions] = useState<OpsChatSession[]>([]);
  const [sessionSearch, setSessionSearch] = useState('');
  const [favoritesOnly, setFavoritesOnly] = useState(false);
  const [activeSessionId, setActiveSessionId] = useState<string>('');
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [runtimeEvents, setRuntimeEvents] = useState<OpsRuntimeEvent[]>([]);
  const [changePackages, setChangePackages] = useState<OpsChangePackage[]>([]);
  const [packageBusy, setPackageBusy] = useState(false);
  const [input, setInput] = useState('');
  const [modelStatus, setModelStatus] = useState<Record<string, any> | null>(null);
  const [loading, setLoading] = useState(true);
  const [sending, setSending] = useState(false);
  const [runtimeExpanded, setRuntimeExpanded] = useState(false);
  const [visibleSessionCount, setVisibleSessionCount] = useState(20);
  const [lastQuery, setLastQuery] = useState('');
  const abortRef = useRef<AbortController | null>(null);
  const activeRunIdRef = useRef<string>('');
  const selectedExecutionRef = useRef<string>('');
  const linkedIncidentRunsRef = useRef<Set<string>>(new Set());
  const replanPrefillAppliedRef = useRef(false);
  const scenarioPrefillAppliedRef = useRef(false);
  const sessionHistoryRequestRef = useRef(0);
  const messageListRef = useRef<HTMLDivElement | null>(null);

  const selectedProject = useMemo(
    () => projects.find((project) => project.projectId === selectedProjectId),
    [projects, selectedProjectId],
  );

  const runSummary = useMemo(
    () => summarizeChatRun(runtimeEvents, sending, changePackages.length),
    [changePackages.length, runtimeEvents, sending],
  );

  const activeSession = useMemo(
    () => sessions.find((session) => session.sessionId === activeSessionId),
    [activeSessionId, sessions],
  );

  const sessionExecutionAgentId = useCallback((session?: OpsChatSession) => {
    if (!session) return '';
    if (session.metadata?.executionType === 'WORKFLOW') return session.agentId || '';
    if (agents.some((agent) => agent.agentId === session.agentId)) return session.agentId || '';
    return '';
  }, [agents]);

  const currentAgentId = activeSession
    ? sessionExecutionAgentId(activeSession)
    : selectedAgentId;

  const currentAgent = useMemo(
    () => agents.find((agent) => agent.agentId === currentAgentId),
    [agents, currentAgentId],
  );

  const agentDisplayName = useCallback((agentId?: string, metadata?: Record<string, any>) => {
    if (!agentId) {
      return NO_AGENT_NAME;
    }
    const agent = agents.find((item) => item.agentId === agentId);
    const metadataName = typeof metadata?.agentName === 'string' ? metadata.agentName : '';
    return agent?.name || metadataName || agentId;
  }, [agents]);

  const specializedWorkflowSelected = Boolean(currentAgentId);

  const currentAgentName = useMemo(
    () => specializedWorkflowSelected
      ? currentAgent?.name || agentDisplayName(currentAgentId, activeSession?.metadata)
      : 'ReAct',
    [activeSession?.metadata, agentDisplayName, currentAgent?.name, currentAgentId, specializedWorkflowSelected],
  );

  const currentUserName = userInfo?.username || 'web-user';

  const latestSources = useMemo(
    () => messages.flatMap((message) => message.sources || []).slice(-8),
    [messages],
  );

  const visibleSessions = useMemo(
    () => sessions.slice(0, visibleSessionCount),
    [sessions, visibleSessionCount],
  );

  const loadSessions = useCallback(async (_agentId = selectedAgentId) => {
    try {
      const response = await opsAdminService.listChatSessions({
        userId: userInfo?.username || 'web-user',
        keyword: sessionSearch.trim() || undefined,
        favorite: favoritesOnly ? true : undefined,
        limit: 80,
      });
      setSessions((response.data || [])
        .filter((session) => !selectedProjectId || session.projectId === selectedProjectId)
        .map((session) => ({
          ...session,
          lastMessage: sanitizeVisibleAnswer(session.lastMessage),
        })));
      setVisibleSessionCount(20);
    } catch (error) {
      Toast.error('加载会话列表失败');
      setSessions([]);
    }
  }, [favoritesOnly, selectedAgentId, selectedProjectId, sessionSearch, userInfo?.username]);

  const loadAgents = useCallback(async (projectId: string) => {
    try {
      const response = await opsAdminService.listChatAgents(projectId);
      const nextAgents = (response.data || []).filter((agent) => agent.definitionKind === 'SPECIALIZED_WORKFLOW');
      setAgents(nextAgents);
      setSelectedAgentId((current) => {
        if (!current || nextAgents.some((agent) => agent.agentId === current)) {
          selectedExecutionRef.current = current;
          return current;
        }
        selectedExecutionRef.current = '';
        return '';
      });
    } catch (error) {
      setAgents([]);
    }
  }, []);

  const loadProjects = useCallback(async () => {
    await projectScope.reloadProjects();
  }, [projectScope.reloadProjects]);

  const loadModelStatus = useCallback(async () => {
    try {
      const response = await opsAdminService.chatModelStatus();
      setModelStatus(response.data || null);
    } catch (error) {
      setModelStatus(null);
    }
  }, []);

  const loadChangePackages = useCallback(async (sessionId = activeSessionId) => {
    if (!sessionId || !selectedProjectId) {
      setChangePackages([]);
      return;
    }
    try {
      const response = await opsChangePackageService.list({
        scope: 'user',
        projectId: selectedProjectId,
        sessionId,
        limit: 20,
      });
      setChangePackages(response.data || []);
    } catch (error) {
      setChangePackages([]);
    }
  }, [activeSessionId, selectedProjectId]);

  const loadIncidents = useCallback(async (projectId = selectedProjectId) => {
    if (!projectId) {
      setIncidents([]);
      return;
    }
    try {
      const response = await opsAdminService.listIncidents({
        projectId,
        limit: 100,
        scope: isAdminUser() ? 'admin' : 'user',
      });
      setIncidents((response.data || []).filter((item) => !['RESOLVED', 'CLOSED'].includes(String(item.status || '').toUpperCase())));
    } catch (error) {
      setIncidents([]);
    }
  }, [selectedProjectId]);

  useEffect(() => {
    const token = localStorage.getItem('token');
    const storedUserInfo = localStorage.getItem('userInfo');
    if (!token || !storedUserInfo) {
      navigate('/login');
      return;
    }
    try {
      setUserInfo(JSON.parse(storedUserInfo));
    } catch (error) {
      navigate('/login');
    }
  }, [navigate]);

  useEffect(() => {
    if (!userInfo) return;
    setLoading(true);
    Promise.all([loadProjects(), loadModelStatus()]).finally(() => setLoading(false));
  }, [loadModelStatus, loadProjects, userInfo]);

  useEffect(() => {
    if (projects.length === 0) return;
    const incidentId = searchParams.get('incidentId') || '';
    const requestedProjectId = searchParams.get('projectId') || '';
    if (!incidentId || !requestedProjectId) return;
    if (projects.some((project) => project.projectId === requestedProjectId)) {
      setSelectedProjectId(requestedProjectId);
      setSelectedIncidentId(incidentId);
    }
  }, [projects, searchParams]);

  useEffect(() => {
    if (!userInfo || !selectedProjectId) return;
    void loadIncidents(selectedProjectId);
  }, [loadIncidents, selectedProjectId, userInfo]);

  useEffect(() => {
    if (replanPrefillAppliedRef.current || projects.length === 0) return;
    const packageId = searchParams.get('replanPackageId');
    if (!packageId) return;
    const requestedProjectId = searchParams.get('projectId') || '';
    if (requestedProjectId && projects.some((project) => project.projectId === requestedProjectId)) {
      setSelectedProjectId(requestedProjectId);
    }
    const reason = searchParams.get('replanReason') || 'LandingRuntime 要求重新规划';
    setInput(`请重新调查 ChangePackage ${packageId} 的失败情况并生成新的可审核版本。\n失败反馈：${reason}\n要求：重新获取当前证据并运行必要验证，不要复用旧审批或旧 proof；如果方案方向需要变化，请明确说明原因。`);
    replanPrefillAppliedRef.current = true;
    setSearchParams({}, { replace: true });
  }, [projects, searchParams, setSearchParams]);

  useEffect(() => {
    if (scenarioPrefillAppliedRef.current || projects.length === 0 || searchParams.get('replanPackageId')) return;
    const requestedProjectId = searchParams.get('projectId') || '';
    const scenarioId = searchParams.get('scenarioId') || '';
    if (!requestedProjectId || !scenarioId) return;
    const project = projects.find((item) => item.projectId === requestedProjectId);
    const scenario = project?.diagnosticScenarios?.find((item) => item.scenarioId === scenarioId);
    if (!project || !scenario?.ready) {
      Toast.warning('该诊断场景尚未接入所需能力，请先在项目页完善接入');
      scenarioPrefillAppliedRef.current = true;
      setSearchParams({}, { replace: true });
      return;
    }
    setSelectedProjectId(project.projectId);
    setInput(scenario.promptTemplate);
    scenarioPrefillAppliedRef.current = true;
    setSearchParams({}, { replace: true });
  }, [projects, searchParams, setSearchParams]);

  useEffect(() => {
    if (!userInfo || !selectedProjectId) return;
    loadAgents(selectedProjectId);
  }, [loadAgents, selectedProjectId, userInfo]);

  useEffect(() => {
    if (!userInfo) return;
    const timer = window.setTimeout(() => {
      loadSessions(selectedAgentId);
    }, 250);
    return () => window.clearTimeout(timer);
  }, [loadSessions, selectedAgentId, userInfo]);

  useEffect(() => {
    if (!userInfo) return;
    sessionHistoryRequestRef.current += 1;
    setActiveSessionId('');
    setMessages([]);
    setRuntimeEvents([]);
    setChangePackages([]);
  }, [selectedAgentId, selectedProjectId, userInfo]);

  useEffect(() => {
    if (!activeSessionId || !selectedProjectId) {
      setChangePackages([]);
      return;
    }
    loadChangePackages(activeSessionId);
  }, [activeSessionId, loadChangePackages, selectedProjectId]);

  useEffect(() => {
    const el = messageListRef.current;
    if (el) {
      el.scrollTop = el.scrollHeight;
    }
  }, [messages, runtimeEvents]);

  const handleNavigation = (path: string) => {
    navigate(path.startsWith('/') ? path : `/${path}`);
  };

  const handleLogout = () => {
    clearAuthSession();
    navigate('/login');
  };

  const createSessionForExecution = async (executionAgentId: string) => {
    if (!selectedProjectId) {
      Toast.warning('请先选择业务系统');
      return;
    }
    const workflow = agents.find((agent) => agent.agentId === executionAgentId);
    sessionHistoryRequestRef.current += 1;
    setMessages([]);
    setRuntimeEvents([]);
    try {
      const response = await opsAdminService.createChatSession({
        userId: userInfo?.username || 'web-user',
        projectId: selectedProjectId,
        agentId: executionAgentId || undefined,
        title: '新会话',
        mode: 'AGENT',
        engine: workflow?.engine || 'GRAPH',
        metadata: {
          executionType: executionAgentId ? 'WORKFLOW' : 'REACT',
          agentName: workflow?.name || 'ReAct',
        },
      });
      selectedExecutionRef.current = executionAgentId;
      setSelectedAgentId(executionAgentId);
      setActiveSessionId(response.data);
      await loadSessions(executionAgentId);
    } catch (error) {
      Toast.error('新建会话失败');
    }
  };

  const handleNewSession = async () => createSessionForExecution(selectedExecutionRef.current);

  const handleExecutionSelectionChange = async (value: unknown) => {
    const nextAgentId = String(value || '');
    const activeExecutionAgentId = sessionExecutionAgentId(activeSession);
    selectedExecutionRef.current = nextAgentId;
    setSelectedAgentId(nextAgentId);
    if (nextAgentId !== activeExecutionAgentId) {
      await createSessionForExecution(nextAgentId);
    }
  };

  const handleSelectSession = async (session: OpsChatSession) => {
    const requestId = ++sessionHistoryRequestRef.current;
    const executionAgentId = sessionExecutionAgentId(session);
    selectedExecutionRef.current = executionAgentId;
    setSelectedAgentId(executionAgentId);
    setActiveSessionId(session.sessionId);
    setRuntimeEvents([]);
    const [messagesResult, eventsResult] = await Promise.allSettled([
      opsAdminService.listChatMessages(session.sessionId),
      opsAdminService.listChatEvents(session.sessionId),
    ]);
    if (requestId !== sessionHistoryRequestRef.current) return;

    if (messagesResult.status === 'fulfilled') {
      setMessages((messagesResult.value.data || []).map(toChatMessage));
    } else {
      Toast.error('加载会话消息失败');
      setMessages([]);
    }
    if (eventsResult.status === 'fulfilled') {
      setRuntimeEvents(restoreRuntimeEvents(eventsResult.value.data));
    } else {
      Toast.warning('运行详情加载失败，历史消息仍可查看');
      setRuntimeEvents([]);
    }
  };

  const handleDeleteSession = async (sessionId: string) => {
    try {
      await opsAdminService.deleteChatSession(sessionId);
      if (activeSessionId === sessionId) {
        sessionHistoryRequestRef.current += 1;
        setActiveSessionId('');
        setMessages([]);
        setRuntimeEvents([]);
      }
      await loadSessions(selectedAgentId);
      Toast.success('会话已删除');
    } catch (error) {
      Toast.error('删除会话失败');
    }
  };

  const handleToggleFavorite = async (session: OpsChatSession) => {
    try {
      const metadata = { ...(session.metadata || {}), favorite: !session.metadata?.favorite };
      if (!session.stateVersion) {
        throw new Error('会话版本缺失，请刷新后重试');
      }
      const response = await opsAdminService.updateChatSession(session.sessionId, {
        metadata,
        expectedStateVersion: session.stateVersion,
      });
      setSessions((current) =>
        current.map((item) => item.sessionId === session.sessionId
          ? (response.data || { ...item, metadata, stateVersion: session.stateVersion! + 1 })
          : item),
      );
    } catch (error) {
      Toast.error('更新会话收藏失败');
    }
  };

  const handleExportMessages = () => {
    if (!messages.length) {
      Toast.warning('当前会话没有可导出的消息');
      return;
    }
    const content = messages
      .map((message) => `## ${messageActorName(message)} · ${message.createdAt}\n\n${message.content || ''}`)
      .join('\n\n');
    const blob = new Blob([content], { type: 'text/markdown;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = `${activeSessionId || 'chat-session'}.md`;
    anchor.click();
    URL.revokeObjectURL(url);
  };

  const handleCreateIncident = async () => {
    if (!selectedProjectId) {
      Toast.warning('请先选择业务系统');
      return;
    }
    try {
      const response = await opsAdminService.createIncident({
        projectId: selectedProjectId,
        title: activeSession?.title || `人工诊断 · ${new Date().toLocaleString()}`,
        sourceType: 'CHAT',
        serviceName: selectedProject?.name || selectedProjectId,
        summary: '从 AI 对话创建，等待基于真实数据源形成 Evidence 与 Diagnosis。',
      }, isAdminUser() ? 'admin' : 'user');
      if (response.data?.incidentId) {
        setSelectedIncidentId(response.data.incidentId);
        await loadIncidents(selectedProjectId);
        Toast.success('已创建并关联事件');
      }
    } catch (error) {
      Toast.error(error instanceof Error ? error.message : '创建事件失败');
    }
  };

  const appendAssistant = (assistantId: string, patch: Partial<ChatMessage> | ((message: ChatMessage) => ChatMessage)) => {
    setMessages((current) =>
      current.map((message) => {
        if (message.id !== assistantId) return message;
        return typeof patch === 'function' ? patch(message) : { ...message, ...patch };
      }),
    );
  };

  const handleSend = async (overrideQuery?: string) => {
    const query = (overrideQuery ?? input).trim();
    if (!query || sending) {
      return;
    }
    if (!selectedProjectId) {
      Toast.warning('请先选择业务系统');
      return;
    }
    if (modelStatus && modelStatus.chatAvailable === false) {
      Toast.info('模型服务当前不可用，仍会提交给后端路由；轻量问题可直接回复，复杂运维任务会由后端返回明确失败原因。');
    }
    if (!overrideQuery) {
      setInput('');
    }
    setLastQuery(query);
    setSending(true);
    setRuntimeEvents([]);
    activeRunIdRef.current = '';
    abortRef.current?.abort();
    const controller = new AbortController();
    abortRef.current = controller;

    const sessionId = activeSessionId || undefined;

    const assistantId = messageId();
    setMessages((current) => [
      ...current,
      { id: messageId(), role: 'user', content: query, createdAt: nowText() },
      { id: assistantId, role: 'assistant', content: '', createdAt: nowText(), sources: [] },
    ]);

    try {
      await opsAdminService.streamUserChat(
        {
          userId: userInfo?.username || 'web-user',
          projectId: selectedProjectId,
          sessionId,
          query,
          mode: 'AGENT',
          engine: currentAgent?.engine || undefined,
          agentDefinitionId: specializedWorkflowSelected
            ? currentAgentId || undefined
            : undefined,
          enableThinking: true,
          metadata: {
            incidentId: selectedIncidentId || undefined,
            agentName: currentAgentName,
            assistantRouteChoice: specializedWorkflowSelected
              ? 'EXPLICIT_SPECIALIZED_WORKFLOW'
              : 'DEFAULT_MAIN_ASSISTANT',
            projectName: selectedProject?.name || selectedProjectId,
          },
        },
        (event) => {
          const eventRunId = event.runId || (typeof event.payload?.runId === 'string' ? event.payload.runId : '');
          if (eventRunId) {
            activeRunIdRef.current = eventRunId;
            const linkKey = selectedIncidentId ? `${selectedIncidentId}:${eventRunId}` : '';
            if (linkKey && !linkedIncidentRunsRef.current.has(linkKey)) {
              linkedIncidentRunsRef.current.add(linkKey);
              void opsAdminService.linkIncidentRun(
                selectedIncidentId,
                eventRunId,
                'AI 对话运行自动关联 Incident',
                isAdminUser() ? 'admin' : 'user',
              ).catch(() => {
                linkedIncidentRunsRef.current.delete(linkKey);
              });
            }
          }
          if (event.sessionId && !activeSessionId) {
            setActiveSessionId(event.sessionId);
          }
          if (event.eventType === 'TEXT_DELTA' && event.content) {
            appendAssistant(assistantId, (message) => ({ ...message, content: message.content + event.content }));
          } else if (event.eventType === 'THINKING' && event.content) {
            appendAssistant(assistantId, (message) => ({ ...message, thinking: `${message.thinking || ''}${event.content}` }));
          } else if (event.eventType === 'FINAL_OUTPUT' && event.content) {
            appendAssistant(assistantId, (message) => ({
              ...message,
              content: sanitizeVisibleAnswer(event.content) || message.content,
            }));
          } else if (event.eventType === 'RAG_RETRIEVE') {
            const sources = Array.isArray(event.payload?.sources) ? (event.payload?.sources as RagSource[]) : [];
            if (sources.length) {
              appendAssistant(assistantId, (message) => ({ ...message, sources: [...(message.sources || []), ...sources] }));
            }
          } else if (event.eventType === 'ERROR') {
            appendAssistant(assistantId, { content: event.summary || '对话失败，请检查模型服务或项目工具连接。' });
          }
          setRuntimeEvents((current) => [...current, event]);
        },
        controller.signal,
      );
      await loadSessions(selectedAgentId);
      if (activeSessionId) {
        await loadChangePackages(activeSessionId);
      }
    } catch (error) {
      if (!controller.signal.aborted) {
        appendAssistant(assistantId, { content: '对话请求失败，请稍后重试或联系管理员检查模型服务。' });
        Toast.error('对话失败');
      }
    } finally {
      setSending(false);
      activeRunIdRef.current = '';
    }
  };

  const handleStopRun = async () => {
    const runId = activeRunIdRef.current;
    if (runId) {
      try {
        await opsAdminService.cancelUserChatRun(runId);
      } catch (error) {
        Toast.warning('后端取消请求未确认，已先中断本地事件流');
      }
    }
    abortRef.current?.abort();
    abortRef.current = null;
    setSending(false);
    setMessages((current) => {
      const next = [...current];
      const assistantIndex = [...next].reverse().findIndex((message) => message.role === 'assistant');
      if (assistantIndex < 0) return next;
      const index = next.length - 1 - assistantIndex;
      const currentMessage = next[index];
      if (currentMessage.content) return next;
      next[index] = {
        ...currentMessage,
        content: runId
          ? `已停止当前运行。已向后端发送取消信号：${runId}。`
          : '已停止当前运行。尚未收到后端 runId，已先中断本地事件流。',
      };
      return next;
    });
    setRuntimeEvents((current) => [
      ...current,
      {
        eventType: 'USER_STOPPED',
        runId,
        status: 'CANCELED',
        summary: runId
          ? `用户停止当前运行，已向后端发送取消信号：${runId}。`
          : '用户停止当前运行，但尚未收到后端 runId。',
        timestamp: nowText(),
      },
    ]);
    activeRunIdRef.current = '';
  };

  const handleCreateChangePackage = async () => {
    if (!activeSessionId) {
      Toast.warning('请先创建或选择一个会话');
      return;
    }
    if (!selectedProjectId) {
      Toast.warning('请先选择业务系统');
      return;
    }
    setPackageBusy(true);
    try {
      const objective = lastQuery || messages.filter((message) => message.role === 'user').slice(-1)[0]?.content || activeSession?.title || '处理当前运维问题';
      const response = await opsChangePackageService.createFromSession(activeSessionId, {
        projectId: selectedProjectId,
        objective,
        question: objective,
        preparationGraphId: undefined,
        maxPreparationIterations: 2,
      });
      Toast.success(`已生成处置方案：${response.data?.packageId || ''}`);
      await loadChangePackages(activeSessionId);
    } catch (error) {
      Toast.error('生成处置方案失败');
    } finally {
      setPackageBusy(false);
    }
  };

  const renderPackage = (pkg: OpsChangePackage) => {
    const statusText = pkg.status === 'NEEDS_REPLAN'
      ? '需要重新生成方案'
      : pkg.status === 'APPROVED'
        ? '待执行'
        : pkg.status === 'LANDING_RUNNING'
          ? '执行中'
          : pkg.status === 'LANDED' || pkg.status === 'CLOSED'
            ? '已完成'
            : pkg.status === 'LANDING_FAILED'
              ? '执行失败'
              : '待确认';
    const actionLabel = pkg.status === 'REVIEWING'
      ? '去执行中心审批'
      : pkg.status === 'APPROVED'
        ? '去执行中心执行'
        : pkg.status === 'NEEDS_REPLAN'
          ? '查看并重新规划'
          : '查看执行详情';
    const executionPath = '/executions';
    const executionParams = new URLSearchParams({
      projectId: pkg.projectId || selectedProjectId,
      packageId: pkg.packageId,
    });
    return (
      <PackageCard key={pkg.packageId}>
        <Space vertical align="start" spacing="tight" style={{ width: '100%' }}>
          <Space style={{ width: '100%', justifyContent: 'space-between' }}>
            <Text strong ellipsis={{ showTooltip: true }} style={{ maxWidth: 180 }}>
              {pkg.objective || pkg.packageId}
            </Text>
            <Tag color={pkg.status === 'APPROVED' ? 'green' : pkg.status === 'NEEDS_REPLAN' ? 'orange' : 'blue'}>
              {statusText}
            </Tag>
          </Space>
          <Text type="tertiary" size="small">
            方案版本 v{pkg.version} · 风险 {pkg.riskLevel || 'MEDIUM'} · {pkg.packageType === 'NO_ACTION_REQUIRED' ? '无需执行' : '处置方案'}
          </Text>
          <Text size="small">{pkg.summary || '等待 Preparation Agent 输出摘要。'}</Text>
          <Space wrap>
            <Button size="small" type="primary" onClick={() => navigate(`${executionPath}?${executionParams.toString()}`)}>
              {actionLabel}
            </Button>
          </Space>
        </Space>
      </PackageCard>
    );
  };

  const messageActorName = (message: ChatMessage) => {
    if (message.role === 'user') {
      return currentUserName;
    }
    if (message.role === 'system') {
      return '系统';
    }
    return currentAgentName;
  };

  const messageActorInitial = (message: ChatMessage) => {
    const name = messageActorName(message).trim();
    if (name) {
      return name.slice(0, 1).toUpperCase();
    }
    return message.role === 'assistant' ? 'A' : 'U';
  };

  const messageActorColor = (message: ChatMessage) => {
    if (message.role === 'user') return 'blue';
    if (message.role === 'system') return 'grey';
    return 'green';
  };

  const renderMessage = (message: ChatMessage) => (
    <MessageRow key={message.id} $role={message.role}>
      <MessageBubble $role={message.role}>
        <MessageMeta>
          <Avatar size="extra-extra-small" color={messageActorColor(message)}>
            {messageActorInitial(message)}
          </Avatar>
          <span>{messageActorName(message)}</span>
          <span>{message.createdAt}</span>
        </MessageMeta>
        {message.thinking && (
          <Paragraph style={{ marginBottom: 8, color: theme.colors.text.tertiary }}>
            思考：{message.thinking}
          </Paragraph>
        )}
        {message.role === 'assistant' && message.content
          ? <AssistantAnswer text={message.content} />
          : message.content || (message.role === 'assistant' ? '正在生成...' : '')}
        {!!message.sources?.length && (
          <SourceBox>
            <SourceHeader>
              <IconFile />
              RAG 引用来源 ({message.sources.length})
            </SourceHeader>
            {message.sources.map((source, index) => (
              <SourceItem key={`${source.chunkId || source.docName || index}-${index}`}>
                <Space vertical align="start" spacing="tight" style={{ width: '100%' }}>
                  <Text strong>{index + 1}. {source.docName || source.chunkId || '未命名文档'}</Text>
                  <Text type="tertiary" size="small">
                    {source.knowledgeBaseId || '-'} {source.score ? `score=${source.score}` : ''}
                  </Text>
                  <Text size="small">{source.chunkContent || '-'}</Text>
                </Space>
              </SourceItem>
            ))}
          </SourceBox>
        )}
      </MessageBubble>
    </MessageRow>
  );

  if (!userInfo) {
    return null;
  }

  return (
    <PageLayout>
      <Sidebar selectedKey="chat" onSelect={handleNavigation} collapsed={sidebarCollapsed} onMobileClose={closeMobileSidebar} />
      <MainContent $collapsed={collapsed}>
        <div style={{ display: 'flex', flexDirection: 'column', width: '100%', minWidth: 0, overflowX: 'hidden' }}>
          <Header onToggleSidebar={toggleSidebar} onLogout={handleLogout} collapsed={sidebarCollapsed} />
          <ContentArea>
            <Spin spinning={loading}>
              <Workspace>
                <Panel>
                  <PanelHeader>
                    <Space>
                      <IconComment />
                      <Text strong>会话</Text>
                    </Space>
                    <Space>
                      <Tooltip content="刷新会话">
                        <Button aria-label="刷新会话" icon={<IconRefresh />} onClick={() => loadSessions(selectedAgentId)} />
                      </Tooltip>
                      <Tooltip content="新建会话">
                        <Button aria-label="新建会话" type="primary" icon={<IconPlus />} onClick={handleNewSession} />
                      </Tooltip>
                    </Space>
                  </PanelHeader>
                  <div style={{ padding: theme.spacing.base, borderBottom: `1px solid ${theme.colors.border.secondary}` }}>
                    <Space vertical align="start" style={{ width: '100%' }}>
                      <Text type="secondary">业务系统</Text>
                      <Select
                        value={selectedProjectId}
                        style={{ width: '100%' }}
                        placeholder="先选择业务系统"
                        onChange={(value) => {
                          setSelectedProjectId(String(value || ''));
                          selectedExecutionRef.current = '';
                          setSelectedAgentId('');
                          setSelectedIncidentId('');
                        }}
                      >
                        {projects.map((project) => (
                          <Option key={project.projectId} value={project.projectId}>
                            {project.name}
                          </Option>
                        ))}
                      </Select>
                      <Text type="secondary">当前事件（可选）</Text>
                      <Space style={{ width: '100%' }}>
                        <Select
                          value={selectedIncidentId}
                          style={{ flex: 1 }}
                          placeholder="不关联事件"
                          onChange={(value) => setSelectedIncidentId(String(value || ''))}
                        >
                          <Option value="">不关联事件</Option>
                          {incidents.map((incident) => (
                            <Option key={incident.incidentId} value={incident.incidentId}>
                              {incident.title} · {incident.status}
                            </Option>
                          ))}
                        </Select>
                        <Button onClick={() => void handleCreateIncident()} disabled={!selectedProjectId}>新建</Button>
                      </Space>
                      <Text type="secondary">诊断方式</Text>
                      <div data-testid="execution-selector" data-execution={selectedAgentId || 'REACT'} style={{ width: '100%' }}>
                      <Select
                        aria-label="运行方式"
                        value={selectedAgentId}
                        style={{ width: '100%' }}
                        onSelect={(value) => void handleExecutionSelectionChange(value)}
                      >
                        <Option value="">ReAct</Option>
                        {agents.map((agent) => (
                          <Option key={agent.agentId} value={agent.agentId}>
                            {agent.name || '未命名 Workflow'}
                          </Option>
                        ))}
                      </Select>
                      </div>
                      <Text type="tertiary" size="small">
                        ReAct 会动态决定下一步；选择已发布 Workflow 时严格按固定流程执行。
                      </Text>
                      <Input
                        placeholder="搜索会话标题、Agent、历史消息"
                        value={sessionSearch}
                        onChange={setSessionSearch}
                      />
                      <Checkbox checked={favoritesOnly} onChange={(event) => setFavoritesOnly(Boolean(event.target.checked))}>
                        只看收藏
                      </Checkbox>
                    </Space>
                  </div>
                  <SessionList>
                    {visibleSessions.map((session) => (
                      <SessionItem
                        key={session.sessionId}
                        role="button"
                        tabIndex={0}
                        $active={session.sessionId === activeSessionId}
                        onClick={() => handleSelectSession(session)}
                        onKeyDown={(event) => {
                          if (event.key === 'Enter' || event.key === ' ') {
                            event.preventDefault();
                            handleSelectSession(session);
                          }
                        }}
                      >
                        <Space vertical align="start" spacing="tight" style={{ width: '100%' }}>
                          <Space style={{ width: '100%', justifyContent: 'space-between' }}>
                            <Text strong ellipsis={{ showTooltip: true }} style={{ maxWidth: 178 }}>
                              {session.title || session.sessionId}
                            </Text>
                            <Space>
                              <Button
                                size="small"
                                type={session.metadata?.favorite ? 'warning' : 'tertiary'}
                                onClick={(event) => {
                                  event.stopPropagation();
                                  handleToggleFavorite(session);
                                }}
                              >
                                收藏
                              </Button>
                              <Popconfirm title="确认删除该会话？" onConfirm={() => handleDeleteSession(session.sessionId)}>
                                <Button
                                  aria-label="删除会话"
                                  size="small"
                                  type="tertiary"
                                  icon={<IconDelete />}
                                  onClick={(event) => event.stopPropagation()}
                                />
                              </Popconfirm>
                            </Space>
                          </Space>
                          <Text type="tertiary" size="small">
                            {session.lastActiveAt || session.createdAt || '-'}
                          </Text>
                          <Text type="tertiary" size="small" ellipsis={{ showTooltip: true }}>
                            {chatPreview(session.lastMessage) || `${session.messageCount || 0} 条消息`}
                          </Text>
                          <Space>
                            <Tag color={session.metadata?.executionType === 'WORKFLOW' ? 'purple' : 'blue'}>
                              {session.metadata?.executionType === 'WORKFLOW'
                                ? agentDisplayName(session.agentId, session.metadata)
                                : session.agentId && session.agentId !== selectedProject?.defaultAgentId
                                  ? agentDisplayName(session.agentId, session.metadata)
                                  : 'ReAct'}
                            </Tag>
                            <Tag color="grey">{session.mode || 'MULTI_TURN'}</Tag>
                          </Space>
                        </Space>
                      </SessionItem>
                    ))}
                    {visibleSessions.length < sessions.length && (
                      <Button
                        block
                        type="tertiary"
                        onClick={() => setVisibleSessionCount((current) => current + 20)}
                      >
                        加载更多会话（剩余 {sessions.length - visibleSessions.length} 条）
                      </Button>
                    )}
                    {!visibleSessions.length && <EmptyState>暂无会话。</EmptyState>}
                  </SessionList>
                </Panel>

                <ChatPanel>
                  <ChatHeader>
                    <Space vertical align="start" spacing="tight">
                      <Title heading={4} style={{ margin: 0 }}>
                        项目主助手
                      </Title>
                      <Text type="secondary">
                        当前项目：{selectedProject?.name || '未选择'}。主助手负责理解需求、分发固定动作和恢复失败；生产变更仍必须经过 ChangePackage 审批。
                      </Text>
                    </Space>
                    <Space>
                      <Tag color={specializedWorkflowSelected ? 'purple' : 'blue'}>
                        {specializedWorkflowSelected
                          ? currentAgentName
                          : 'ReAct'}
                      </Tag>
                      {activeSessionId && <Tag color="green">{activeSessionId.slice(0, 18)}</Tag>}
                      <Button icon={<IconFile />} disabled={!messages.length} onClick={handleExportMessages}>
                        导出
                      </Button>
                    </Space>
                  </ChatHeader>
                  {modelStatus && modelStatus.chatAvailable === false && (
                    <WarningBanner>
                      当前执行方式的模型服务不可用，请联系管理员处理。
                    </WarningBanner>
                  )}
                  <RunSummaryBar $stage={runSummary.stage} role="status" aria-live="polite">
                    <div className="run-copy">
                      <div className="run-title">
                        <span>{sending ? '●' : '○'}</span>
                        <span>{runSummary.label}</span>
                        {runSummary.evidenceCount > 0 && <Tag color="blue">可信查询 {runSummary.evidenceCount}</Tag>}
                      </div>
                      <div className="run-detail">{runSummary.detail}</div>
                    </div>
                    <Button size="small" onClick={() => setRuntimeExpanded((current) => !current)}>
                      {runtimeExpanded ? '收起运行详情' : '查看运行详情'}
                    </Button>
                  </RunSummaryBar>
                  <MessageList ref={messageListRef}>
                    {messages.length ? messages.map(renderMessage) : (
                      <EmptyState>
                        <Space vertical align="center">
                          <IconBranch size="extra-large" />
                          <Text type="tertiary">选择会话或直接输入问题开始对话。</Text>
                        </Space>
                      </EmptyState>
                    )}
                  </MessageList>
                  <Composer>
                    <Space vertical align="start" style={{ width: '100%' }}>
                      <Space style={{ width: '100%', justifyContent: 'space-between', alignItems: 'center' }}>
                        <span />
                        <Space>
                          <Button aria-label="重试上一条消息" disabled={!lastQuery || sending} onClick={() => handleSend(lastQuery)}>
                            重试
                          </Button>
                          <Button
                            aria-label="停止当前运行"
                            disabled={!sending}
                            onClick={handleStopRun}
                          >
                            停止
                          </Button>
                        </Space>
                      </Space>
                      <TextArea
                        autosize={{ minRows: 3, maxRows: 8 }}
                        value={input}
                        placeholder="直接描述问题或任务；主助手会判断是回答、查询、固定操作还是启动受控运维流程..."
                        onChange={setInput}
                        onEnterPress={(event) => {
                          if (!event.shiftKey) {
                            event.preventDefault();
                            handleSend();
                          }
                        }}
                      />
                      <Space style={{ width: '100%', justifyContent: 'space-between' }}>
                        <Text type="tertiary" size="small">Enter 发送，Shift + Enter 换行。</Text>
                        <Button aria-label="发送消息" type="primary" icon={<IconSend />} loading={sending} onClick={() => handleSend()}>
                          发送
                        </Button>
                      </Space>
                    </Space>
                  </Composer>
                </ChatPanel>

                <RuntimePanel>
                  <PanelHeader>
                    <Space>
                      <IconSetting />
                      <Text strong>执行状态</Text>
                    </Space>
                    <Space>
                      <Button size="small" loading={packageBusy} disabled={!activeSessionId} onClick={handleCreateChangePackage}>
                        生成处置方案
                      </Button>
                      <Tag color="blue">{runtimeEvents.length}</Tag>
                    </Space>
                  </PanelHeader>
                  {runtimeExpanded && (
                  <>
                  <PackageList>
                    {changePackages.length ? changePackages.map(renderPackage) : (
                      <EmptyState>当前会话还没有处置方案。诊断后可生成待审批方案。</EmptyState>
                    )}
                  </PackageList>
                  <EventList>
                    <Space vertical align="start" style={{ width: '100%' }}>
                      {runtimeEvents.map((event, index) => {
                        const traceDetail = eventTraceDetail(event);
                        return (
                          <EventItem key={`${event.eventType}-${index}-${event.timestamp}`} $status={event.status}>
                            <Space vertical align="start" spacing="tight" style={{ width: '100%' }}>
                              <Space>
                                <Tag color={eventColor(event.status)}>{event.status || event.eventType}</Tag>
                                <Text strong>{eventTitle(event)}</Text>
                              </Space>
                              <Text type="secondary" size="small">{event.summary || event.content || '-'}</Text>
                              {eventMetricText(event) && <Text type="tertiary" size="small">{eventMetricText(event)}</Text>}
                              {traceDetail && (
                                <TraceDetails>
                                  <summary>查看调试 Trace</summary>
                                  <EventPayload>{traceDetail}</EventPayload>
                                </TraceDetails>
                              )}
                              {event.timestamp && <Text type="tertiary" size="small">{event.timestamp}</Text>}
                            </Space>
                          </EventItem>
                        );
                      })}
                      {!runtimeEvents.length && <EmptyState>运行后展示节点、工具、RAG 和最终输出事件。</EmptyState>}
                    </Space>
                    {!!latestSources.length && (
                      <SourceBox>
                        <SourceHeader>
                          <IconFile />
                          最近 RAG 来源
                        </SourceHeader>
                        {latestSources.map((source, index) => (
                          <SourceItem key={`${source.chunkId || source.docName || index}-right`}>
                            <Text strong>{source.docName || source.chunkId || '未命名来源'}</Text>
                            <br />
                            <Text size="small">{source.chunkContent || '-'}</Text>
                          </SourceItem>
                        ))}
                      </SourceBox>
                    )}
                  </EventList>
                  </>
                  )}
                </RuntimePanel>
              </Workspace>
            </Spin>
          </ContentArea>
        </div>
      </MainContent>
    </PageLayout>
  );
};
