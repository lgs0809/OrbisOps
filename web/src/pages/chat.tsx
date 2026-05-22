import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  Banner,
  Button,
  Input,
  Select,
  Space,
  Spin,
  Tag,
  TextArea,
  Toast,
  Typography,
} from '@douyinfe/semi-ui';
import {
  IconComment,
  IconMenu,
  IconPlus,
  IconRefresh,
  IconSend,
  IconStop,
} from '@douyinfe/semi-icons';
import { useNavigate, useSearchParams } from 'react-router-dom';
import styled from 'styled-components';

import { OpsPageShell, OpsWorkspaceFrame } from '../components/ops-layout';
import { AssistantAnswer } from '../features/chat/AssistantAnswer';
import { TaskEpisodePanel } from '../features/chat/TaskEpisodePanel';
import { ChatModelSelector } from '../features/chat/ChatModelSelector';
import { useProjectScope } from '../hooks/use-project-scope';
import {
  appendLiveRunStep,
  answerAfterStreamClosed,
  sanitizeVisibleAnswer,
  userFacingRunFailure,
  type LiveRunStep,
} from '../features/chat/live-run-trace';
import { getStoredUserInfo } from '../services/auth-session';
import { chatWorkflowBinding, latestWorkflowForNewChat } from '../features/chat/chat-workflow-binding';
import { followChatRun } from '../features/chat/follow-chat-run';
import { latestChatRun, mergeChatHistory, recoverableChatRun } from '../features/chat/merge-chat-history';
import { chatPreview, chatSessionTitle, shouldSendChatKey } from '../features/chat/chat-presentation';
import { useChatDraft } from '../features/chat/use-chat-draft';
import {
  opsAdminService,
  type OpsAgentDefinition,
  type OpsChatMessage,
  type OpsChatSession,
  type OpsSelectableModel,
} from '../services/ops-admin-service';
import { theme } from '../styles/theme';
import { userFacingError } from '../utils/user-facing-error';

const { Text, Paragraph } = Typography;
const { Option } = Select;

const ChatWorkspace = styled(OpsWorkspaceFrame)`
  position: relative;
  min-height: 0;
  display: grid;
  grid-template-columns: 260px minmax(0, 1fr);
  grid-template-rows: minmax(0, 1fr);
  background: #fff;
  overflow: hidden;

  @media (max-width: 820px) {
    grid-template-columns: 1fr;
  }

`;

const SessionPane = styled.aside<{ $mobileOpen: boolean }>`
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  background: #f6f7f9;
  border-right: 1px solid ${theme.colors.border.secondary};

  @media (max-width: 820px) {
    position: absolute;
    inset: 0 auto 0 0;
    z-index: 30;
    width: min(300px, 86vw);
    transform: translateX(${(props) => (props.$mobileOpen ? '0' : '-105%')});
    visibility: ${(props) => (props.$mobileOpen ? 'visible' : 'hidden')};
    pointer-events: ${(props) => (props.$mobileOpen ? 'auto' : 'none')};
    box-shadow: ${(props) => (props.$mobileOpen ? '18px 0 46px rgb(15 23 42 / 18%)' : 'none')};
    transition: transform ${theme.animation.duration.normal} ${theme.animation.easing.cubic};
  }

  @media (prefers-reduced-motion: reduce) {
    transition: none;
  }
`;

const SessionBackdrop = styled.button<{ $visible: boolean }>`
  display: none;

  @media (max-width: 820px) {
    display: ${(props) => (props.$visible ? 'block' : 'none')};
    position: absolute;
    inset: 0;
    z-index: 29;
    padding: 0;
    border: 0;
    background: rgb(15 23 42 / 28%);
  }
`;

const SessionHeader = styled.div`
  padding: 18px 14px 12px;

  strong {
    display: block;
    margin-bottom: 12px;
    font-size: 16px;
    letter-spacing: -0.02em;
  }
`;

const SessionActions = styled.div`
  display: grid;
  grid-template-columns: minmax(0, 1fr) 34px;
  gap: 7px;
`;

const SearchWrap = styled.div`
  padding: 0 14px 10px;
`;

const SessionList = styled.div`
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 0 8px 12px;
`;

const SessionItem = styled.button<{ $active: boolean }>`
  width: 100%;
  display: block;
  margin: 2px 0;
  padding: 10px 11px;
  border: 0;
  border-radius: 9px;
  background: ${(props) => (props.$active ? '#e6e8ec' : 'transparent')};
  color: inherit;
  text-align: left;
  cursor: pointer;

  &:hover {
    background: ${(props) => (props.$active ? '#e1e3e7' : '#eceef1')};
  }
`;

const SessionTitle = styled.div`
  display: flex;
  align-items: center;
  gap: 7px;
  min-width: 0;
  color: ${theme.colors.text.primary};

  .semi-icon {
    flex-shrink: 0;
    color: #7d8591;
  }
`;

const SessionMeta = styled.div`
  margin: 4px 0 0 25px;
  overflow: hidden;
  color: ${theme.colors.text.tertiary};
  font-size: 11px;
  line-height: 1.35;
  text-overflow: ellipsis;
  white-space: nowrap;
`;

const Conversation = styled.section`
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  background: #fff;
`;

const HistoryToggle = styled(Button)`
  display: none;

  @media (max-width: 820px) {
    display: inline-flex;
  }
`;

const ConversationHeader = styled.div`
  min-height: 58px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 18px;
  border-bottom: 1px solid ${theme.colors.border.tertiary};

  .title {
    min-width: 0;
    flex: 1;
  }

  .header-identity { display: flex; align-items: center; gap: 6px; min-width: 0; flex: 1; }
  .header-actions { flex-shrink: 0; }

  strong {
    display: block;
    overflow: hidden;
    font-size: 14px;
    font-weight: 650;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  span {
    display: block;
    margin-top: 2px;
    color: ${theme.colors.text.tertiary};
    font-size: 11px;
  }

  @media (max-width: ${theme.breakpoints.sm}) {
    flex-wrap: wrap;
    gap: 6px;
    .header-identity { flex-basis: 100%; }
    .header-actions { margin-left: auto; max-width: 100%; }
  }
`;

const MessageList = styled.div`
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 32px max(22px, calc((100% - 820px) / 2));
  background: #fff;

  @media (max-width: 820px) {
    padding: 24px 16px;
  }
`;

const Welcome = styled.div`
  max-width: 650px;
  margin: 16vh auto 0;

  h2 {
    margin: 0 0 10px;
    color: ${theme.colors.text.primary};
    font-size: clamp(26px, 3vw, 38px);
    font-weight: 650;
    letter-spacing: -0.035em;
  }

  p {
    margin: 0;
    color: ${theme.colors.text.tertiary};
    font-size: 14px;
    line-height: 1.75;
  }
`;

const MessageRow = styled.div<{ $role: string }>`
  display: grid;
  grid-template-columns: ${(props) => props.$role === 'user' ? 'minmax(0, 1fr) 30px' : '30px minmax(0, 1fr)'};
  gap: 11px;
  max-width: 820px;
  margin: 0 auto 26px;
  align-items: flex-start;
`;

const MessageAvatar = styled.div<{ $role: string }>`
  width: 30px;
  height: 30px;
  display: grid;
  place-items: center;
  border-radius: 9px;
  background: ${(props) => (props.$role === 'user' ? '#e9edf5' : '#111318')};
  color: ${(props) => (props.$role === 'user' ? '#48505d' : '#fff')};
  font-size: 11px;
  font-weight: 700;
  grid-column: ${(props) => props.$role === 'user' ? '2' : '1'};
`;

const MessageContent = styled.div<{ $role: string }>`
  min-width: 0;
  max-width: ${(props) => props.$role === 'user' ? 'min(100%, 680px)' : 'none'};
  justify-self: ${(props) => props.$role === 'user' ? 'end' : 'stretch'};
  grid-column: ${(props) => props.$role === 'user' ? '1' : '2'};
`;

const MessageBubble = styled.div<{ $role: string }>`
  padding: ${(props) => (props.$role === 'user' ? '10px 12px' : '2px 0')};
  border-radius: ${(props) => (props.$role === 'user' ? '11px' : '0')};
  border: ${(props) => (props.$role === 'user' ? '1px solid #dfe6f0' : '0')};
  background: ${(props) => (props.$role === 'user' ? '#f4f7fb' : 'transparent')};
  color: ${theme.colors.text.primary};
  font-size: 14px;
  line-height: 1.75;
  white-space: pre-wrap;
  word-break: break-word;
`;

const MessageMeta = styled.div<{ $role: string }>`
  display: flex;
  align-items: center;
  gap: 7px;
  margin-bottom: 5px;
  justify-content: ${(props) => props.$role === 'user' ? 'flex-end' : 'flex-start'};
  color: ${theme.colors.text.tertiary};
  font-size: 11px;
  font-weight: 600;
`;

const StreamingDot = styled.span`
  width: 6px;
  height: 6px;
  display: inline-block;
  border-radius: 50%;
  background: ${theme.colors.primary};
  animation: pulse 1s infinite ease-in-out;

  @keyframes pulse {
    0%, 100% { opacity: 0.3; }
    50% { opacity: 1; }
  }
`;

const RunTrace = styled.div`
  margin: 8px 0 12px;
  padding: 10px 12px;
  border: 1px solid ${theme.colors.border.tertiary};
  border-radius: 10px;
  background: #f8f9fb;
`;

const RunTraceHeader = styled.div`
  margin-bottom: 7px;
  color: ${theme.colors.text.secondary};
  font-size: 12px;
  font-weight: 650;
`;

const RunStep = styled.div`
  display: grid;
  grid-template-columns: 8px minmax(0, 1fr);
  gap: 8px;
  align-items: baseline;
  padding: 2px 0;
  color: ${theme.colors.text.secondary};
  font-size: 12px;
  line-height: 1.55;
`;

const RunStepDot = styled.span<{ $status: LiveRunStep['status'] }>`
  width: 6px;
  height: 6px;
  margin-top: 1px;
  border-radius: 50%;
  background: ${(props) => {
    if (props.$status === 'error') return '#d84a4a';
    if (props.$status === 'warning') return '#c98922';
    if (props.$status === 'success') return '#3f8f62';
    return theme.colors.primary;
  }};
`;

const ComposerDock = styled.div`
  flex-shrink: 0;
  padding: 10px max(18px, calc((100% - 840px) / 2)) 20px;
  background: linear-gradient(180deg, rgb(255 255 255 / 65%), #fff 28%);
`;

const Composer = styled.div`
  border: 1px solid #d8dce2;
  border-radius: 15px;
  padding: 9px 10px 8px;
  background: #fff;
  box-shadow: 0 10px 32px rgb(15 23 42 / 9%);

  .semi-input-textarea-wrapper {
    border: 0 !important;
    background: transparent !important;
    box-shadow: none !important;
  }
`;

const ComposerContext = styled.div`
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 0 2px 7px;
  overflow-x: auto;

  .semi-select {
    flex: 0 0 auto;
    min-width: 132px;
    max-width: 190px;
    border-radius: 999px;
    background: #f6f7f8;
  }

  .project-select {
    min-width: 150px;
    max-width: 220px;
  }

  @media (max-width: ${theme.breakpoints.sm}) {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    overflow: visible;

    > div { min-width: 0; }
    > [data-testid='model-selector'] { grid-column: 1 / -1; }
    .semi-select, .project-select { width: 100%; min-width: 0; max-width: none; }
  }
`;

const ComposerFooter = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 4px 2px 0;
`;

const executionName = (workflow?: OpsAgentDefinition) => workflow?.name || workflow?.agentId || '已发布工作流';
const modelPreferenceKey = 'orbisops.chat.modelId';

export const ChatPage: React.FC = () => {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const projectScope = useProjectScope();
  const principal = getStoredUserInfo();
  const [workflows, setWorkflows] = useState<OpsAgentDefinition[]>([]);
  const [models, setModels] = useState<OpsSelectableModel[]>([]);
  const [sessions, setSessions] = useState<OpsChatSession[]>([]);
  const [activeSessionId, setActiveSessionId] = useState(searchParams.get('sessionId') || '');
  const [episodePanelOpen, setEpisodePanelOpen] = useState(false);
  const [selectedWorkflowId, setSelectedWorkflowId] = useState('');
  const [selectedModelId, setSelectedModelId] = useState(() => localStorage.getItem(modelPreferenceKey) || '');
  const selectedModelIdRef = useRef(selectedModelId);
  const [messages, setMessages] = useState<OpsChatMessage[]>([]);
  const draftScope = JSON.stringify([principal.username, projectScope.projectId, activeSessionId]);
  const { input, setInput, discardDraft } = useChatDraft(draftScope);
  const [sessionKeyword, setSessionKeyword] = useState('');
  const [sessionPanelOpen, setSessionPanelOpen] = useState(false);
  const [loadingSessions, setLoadingSessions] = useState(false);
  const [loadingMessages, setLoadingMessages] = useState(false);
  const [sending, setSending] = useState(false);
  const [creatingConversation, setCreatingConversation] = useState(false);
  const creatingConversationRef = useRef(false);
  const [streamingAssistantId, setStreamingAssistantId] = useState('');
  const [liveRunSteps, setLiveRunSteps] = useState<LiveRunStep[]>([]);
  const [lastRunId, setLastRunId] = useState(searchParams.get('runId') || '');
  const abortRef = useRef<AbortController | null>(null);
  const activeRunIdRef = useRef('');
  const messageListRef = useRef<HTMLDivElement | null>(null);
  const stickToLatestRef = useRef(true);
  const [showLatest, setShowLatest] = useState(false);

  const activeSession = useMemo(
    () => sessions.find((session) => session.sessionId === activeSessionId),
    [activeSessionId, sessions],
  );
  const selectedWorkflow = useMemo(
    () => workflows.find((workflow) => workflow.agentId === selectedWorkflowId),
    [selectedWorkflowId, workflows],
  );
  const filteredSessions = useMemo(() => {
    const keyword = sessionKeyword.trim().toLowerCase();
    if (!keyword) return sessions;
    return sessions.filter((session) => [session.title, session.lastMessage, session.metadata?.executionName]
      .filter(Boolean)
      .some((value) => String(value).toLowerCase().includes(keyword)));
  }, [sessionKeyword, sessions]);

  const scrollToBottom = useCallback(() => {
    requestAnimationFrame(() => {
      if (messageListRef.current) messageListRef.current.scrollTop = messageListRef.current.scrollHeight;
      stickToLatestRef.current = true;
      setShowLatest(false);
    });
  }, []);

  useEffect(() => {
    if (stickToLatestRef.current) scrollToBottom();
  }, [messages, liveRunSteps, scrollToBottom]);
  useEffect(() => { scrollToBottom(); }, [activeSessionId, scrollToBottom]);

  const sessionRequestEpoch = useRef(0);
  const loadSessions = useCallback(async () => {
    const requestEpoch = ++sessionRequestEpoch.current;
    if (!projectScope.projectId) {
      setSessions([]);
      setActiveSessionId('');
      setLoadingSessions(false);
      return;
    }
    setLoadingSessions(true);
    try {
      const response = await opsAdminService.listChatSessions({ userId: principal.username, limit: 100 });
      if (requestEpoch !== sessionRequestEpoch.current) return;
      const next = (response.data || [])
        .filter((session) => session.projectId === projectScope.projectId)
        .map((session) => ({
          ...session,
          lastMessage: sanitizeVisibleAnswer(session.lastMessage),
        }));
      setSessions(next);
      setActiveSessionId((current) => current && next.some((session) => session.sessionId === current) ? current : next[0]?.sessionId || '');
    } catch (error) {
      if (requestEpoch !== sessionRequestEpoch.current) return;
      Toast.error(userFacingError(error, '加载对话列表失败，请稍后重试。'));
    } finally {
      if (requestEpoch === sessionRequestEpoch.current) setLoadingSessions(false);
    }
  }, [principal.username, projectScope.projectId]);

  useEffect(() => {
    void opsAdminService.listChatModels()
      .then((response) => {
        const next = response.data || [];
        setModels(next);
        const current = selectedModelIdRef.current;
        if (current && !next.some((model) => model.modelId === current)) {
          selectedModelIdRef.current = '';
          setSelectedModelId('');
          localStorage.removeItem(modelPreferenceKey);
        }
      })
      .catch(() => setModels([]));
  }, []);

  useEffect(() => {
    if (!projectScope.projectId) {
      setWorkflows([]);
      return;
    }
    let active = true;
    opsAdminService.listChatAgents(projectScope.projectId)
      .then((response) => {
        if (!active) return;
        setWorkflows((response.data || []).filter((workflow) => workflow.definitionKind === 'SPECIALIZED_WORKFLOW' && workflow.lifecycle === 'PUBLISHED'));
      })
      .catch(() => { if (active) setWorkflows([]); });
    return () => { active = false; };
  }, [projectScope.projectId]);

  useEffect(() => { void loadSessions(); }, [loadSessions]);

  useEffect(() => {
    if (!activeSessionId) {
      setMessages([]);
      return;
    }
    let active = true;
    let recovery: AbortController | undefined;
    setLoadingMessages(true);
    opsAdminService.listChatMessages(activeSessionId)
      .then(async (response) => {
        if (!active) return;
        const history = (response.data || []).map((message) => ({
          ...message, content: sanitizeVisibleAnswer(message.content),
        }));
        setMessages((current) => mergeChatHistory(current, history, activeSessionId, true));
        if (!abortRef.current) setLastRunId(latestChatRun(history, activeSessionId));
        const runId = recoverableChatRun(history, activeSessionId);
        // An existing stream owns its turn. A reload only follows the persisted run; it never resubmits.
        if (!runId || abortRef.current) return;
        const controller = new AbortController();
        recovery = controller;
        abortRef.current = controller;
        activeRunIdRef.current = runId;
        setLastRunId(runId);
        setSending(true);
        setLoadingMessages(false);
        const messageId = `local-restored-${runId}`;
        setStreamingAssistantId(messageId);
        setMessages((current) => [...current.filter((item) => item.messageId !== messageId), {
          messageId, sessionId: activeSessionId, role: 'assistant',
          content: '正在同步后台任务，获得最终结果后会自动更新。', metadata: { runId },
        }]);
        try {
          const settled = await followChatRun(async () => {
            const result = await opsAdminService.getUserChatRun(runId, projectScope.projectId, controller.signal);
            if (result.code !== '0000' || !result.data) throw new Error('读取运行状态失败');
            return result.data;
          }, (snapshot) => {
            if (!active) return;
            let notice = sanitizeVisibleAnswer(snapshot.response?.content);
            if (!notice && snapshot.status === 'FAILED') notice = userFacingRunFailure({
              eventType: 'RUN_FAILED', payload: { reasonCode: snapshot.error_message },
            });
            if (!notice && snapshot.status === 'CANCELED') notice = '任务已取消，请在运行详情核对已有工具回执。';
            if (!notice && snapshot.status === 'WAITING_APPROVAL') notice = '任务正在等待审批，请打开“查看运行”处理。';
            if (!notice && snapshot.status === 'RECOVERY_REVIEW_REQUIRED') notice = '任务需要核对已有操作结果，请打开“查看运行”。';
            if (notice) patchAssistant(messageId, (message) => ({ ...message, content: notice }));
          }, controller.signal);
          if (!active || controller.signal.aborted) return;
          if (!settled) patchAssistant(messageId, (message) => ({ ...message,
            content: '等待时间较长，后台任务未被取消，请打开“查看运行”核对最新状态。',
          }));
          const persisted = await opsAdminService.listChatMessages(activeSessionId);
          if (!active || controller.signal.aborted) return;
          setMessages((current) => mergeChatHistory(current, (persisted.data || []).map((message) => ({
            ...message, content: sanitizeVisibleAnswer(message.content),
          })), activeSessionId));
        } catch (error) {
          if (active && !controller.signal.aborted) patchAssistant(messageId, (message) => ({ ...message,
            content: '暂时无法同步后台任务，请打开“查看运行”核对状态；本次没有重复提交。',
          }));
        } finally {
          if (abortRef.current === controller) {
            abortRef.current = null;
            activeRunIdRef.current = '';
            setSending(false);
            setStreamingAssistantId('');
          }
        }
      })
      .catch((error) => { if (active) Toast.error(userFacingError(error, '加载对话内容失败，请稍后重试。')); })
      .finally(() => { if (active) setLoadingMessages(false); });
    return () => {
      active = false;
      recovery?.abort();
      if (recovery && abortRef.current === recovery) {
        abortRef.current = null;
        activeRunIdRef.current = '';
        setSending(false);
        setStreamingAssistantId('');
      }
    };
  }, [activeSessionId, projectScope.projectId]);

  useEffect(() => {
    const workflowId = activeSession?.metadata?.executionType === 'WORKFLOW' ? activeSession.agentId || '' : '';
    setSelectedWorkflowId(workflowId);
  }, [activeSession]);

  const createConversation = async (workflowId = selectedWorkflowId) => {
    if (creatingConversationRef.current || sending) return '';
    if (projectScope.error) {
      Toast.warning('项目目录暂时不可用，请刷新项目列表后再开始新任务。');
      return '';
    }
    if (!projectScope.projectId) {
      Toast.warning('请先选择项目。');
      return '';
    }
    creatingConversationRef.current = true;
    setCreatingConversation(true);
    try {
      const workflow = workflowId
        ? await latestWorkflowForNewChat(workflowId, () => opsAdminService.listChatAgents(projectScope.projectId))
        : undefined;
      if (workflow) setWorkflows((items) => [...items.filter((item) => item.agentId !== workflowId), workflow]);
      const response = await opsAdminService.createChatSession({
        userId: principal.username,
        projectId: projectScope.projectId,
        agentId: workflowId || undefined,
        agentVersion: workflow?.version,
        title: '新对话',
        mode: 'AGENT',
        engine: workflow?.engine || 'GRAPH',
        metadata: {
          executionType: workflowId ? 'WORKFLOW' : 'DEFAULT_REACT',
          executionName: workflowId ? executionName(workflow) : '默认助手',
        },
      });
      const sessionId = response.data;
      setActiveSessionId(sessionId);
      setMessages([]);
      setLastRunId('');
      setSearchParams({ projectId: projectScope.projectId, sessionId }, { replace: true });
      await loadSessions();
      return sessionId;
    } catch (error) {
      Toast.error(userFacingError(error, '创建对话失败，请稍后重试。'));
      return '';
    } finally {
      creatingConversationRef.current = false;
      setCreatingConversation(false);
    }
  };

  const changeExecution = async (value: unknown) => {
    const workflowId = String(value || '');
    const sessionId = await createConversation(workflowId);
    if (sessionId) setSelectedWorkflowId(workflowId);
  };

  const changeModel = (value: unknown) => {
    const modelId = String(value || '');
    selectedModelIdRef.current = modelId;
    setSelectedModelId(modelId);
    if (modelId) localStorage.setItem(modelPreferenceKey, modelId);
    else localStorage.removeItem(modelPreferenceKey);
  };

  const patchAssistant = (messageId: string, updater: (message: OpsChatMessage) => OpsChatMessage) => {
    setMessages((current) => current.map((message) => message.messageId === messageId ? updater(message) : message));
  };

  const send = async () => {
    const query = input.trim();
    if (!query || sending || creatingConversationRef.current || loadingMessages || loadingSessions || !projectScope.projectId || projectScope.error) return;
    const modelId = selectedModelIdRef.current;

    let sessionId = activeSessionId;
    if (!sessionId) sessionId = await createConversation(selectedWorkflowId);
    if (!sessionId) return;

    const localUserId = `local-user-${Date.now()}`;
    const localAssistantId = `local-assistant-${Date.now()}`;
    const now = new Date().toISOString();
    setMessages((current) => [
      ...current,
      { messageId: localUserId, sessionId, userId: principal.username, role: 'user', content: query, createdAt: now },
      { messageId: localAssistantId, sessionId, role: 'assistant', content: '', createdAt: now },
    ]);
    setStreamingAssistantId(localAssistantId);
    setLiveRunSteps([]);
    discardDraft(draftScope);
    setInput('');
    scrollToBottom();
    setSending(true);
    setLastRunId('');
    activeRunIdRef.current = '';
    abortRef.current?.abort();
    const controller = new AbortController();
    abortRef.current = controller;

    try {
      const binding = chatWorkflowBinding(activeSession, selectedWorkflow);
      try {
        await opsAdminService.streamUserChat({
          userId: principal.username,
          sessionId,
          projectId: projectScope.projectId,
          query,
          mode: 'AGENT',
          ...binding,
          modelId: modelId || undefined,
          enableThinking: true,
          metadata: {
            executionType: binding.agentDefinitionId ? 'WORKFLOW' : 'DEFAULT_REACT',
            triggerSource: 'CHAT',
            selectedModelId: modelId || undefined,
          },
        }, (event) => {
          setLiveRunSteps((current) => appendLiveRunStep(current, event));
          const eventRunId = event.runId || (typeof event.payload?.runId === 'string' ? event.payload.runId : '');
          if (eventRunId) {
            activeRunIdRef.current = eventRunId;
            setLastRunId(eventRunId);
            setSearchParams((current) => {
              const next = new URLSearchParams(current);
              next.set('projectId', projectScope.projectId);
              next.set('sessionId', sessionId);
              next.set('runId', eventRunId);
              return next;
            }, { replace: true });
          }
          if (event.eventType === 'TEXT_DELTA' && event.content) {
            patchAssistant(localAssistantId, (message) => ({ ...message, content: `${message.content || ''}${event.content}` }));
          } else if (event.eventType === 'FINAL_OUTPUT' && event.content) {
            patchAssistant(localAssistantId, (message) => ({
              ...message,
              content: sanitizeVisibleAnswer(event.content) || message.content,
            }));
          } else if (event.eventType === 'REACT_FAILED'
            || event.eventType === 'RUN_FAILED'
            || event.eventType === 'MAIN_AGENT_ACTION_FAILED') {
            patchAssistant(localAssistantId, (message) => ({
              ...message,
              content: userFacingRunFailure(event),
            }));
          } else if (event.eventType === 'ERROR') {
            patchAssistant(localAssistantId, (message) => ({ ...message, content: event.summary || '对话执行失败，请检查模型或项目连接。' }));
          }
        }, controller.signal);
      } catch (error) {
        if (controller.signal.aborted || !activeRunIdRef.current) throw error;
        // Once accepted, reconnect by reading the same run; never submit the query again.
      }
      const followedRunId = activeRunIdRef.current;
      if (followedRunId && !controller.signal.aborted) {
        const settledRun = await followChatRun(async () => {
          const result = await opsAdminService.getUserChatRun(followedRunId, projectScope.projectId, controller.signal);
          if (result.code !== '0000' || !result.data) throw new Error('读取运行状态失败');
          return result.data;
        }, (snapshot) => {
          const answer = sanitizeVisibleAnswer(snapshot.response?.content);
          let notice = answer;
          if (!notice && snapshot.status === 'FAILED') notice = userFacingRunFailure({
            eventType: 'RUN_FAILED', payload: { reasonCode: snapshot.error_message },
          });
          if (!notice && snapshot.status === 'CANCELED') notice = '任务已取消，请在运行详情核对已有工具回执。';
          if (!notice && snapshot.status === 'WAITING_APPROVAL') notice = '任务正在等待审批，请打开“查看运行”处理。';
          if (!notice && snapshot.status === 'RECOVERY_REVIEW_REQUIRED') notice = '任务需要核对已有操作结果，请打开“查看运行”。';
          patchAssistant(localAssistantId, (message) => ({ ...message,
            content: notice || message.content || '正在同步后台任务，获得最终结果后会自动更新。',
          }));
        }, controller.signal);
        if (!settledRun && !controller.signal.aborted) {
          patchAssistant(localAssistantId, (message) => ({ ...message,
            content: '等待时间较长，已停止页面自动查询。后台任务未被取消，请打开“查看运行”核对最新状态。',
          }));
        }
      }
      if (controller.signal.aborted || abortRef.current !== controller) return;
      patchAssistant(localAssistantId, (message) => ({
        ...message, content: answerAfterStreamClosed(message.content),
      }));
      const history = await opsAdminService.listChatMessages(sessionId);
      if (controller.signal.aborted || abortRef.current !== controller) return;
      const persisted = (history.data || []).map((message) => ({
        ...message,
        content: sanitizeVisibleAnswer(message.content),
      }));
      setMessages((current) => mergeChatHistory(current, persisted, sessionId));
      await loadSessions();
    } catch (error) {
      if (!controller.signal.aborted) {
        patchAssistant(localAssistantId, (message) => ({ ...message, content: message.content || '连接中断。运行可能仍在后台继续，可在工作台查看当前运行。' }));
        Toast.error(userFacingError(error, '流式连接中断，请检查服务状态。'));
      }
    } finally {
      if (abortRef.current === controller) {
        abortRef.current = null;
        setSending(false);
        setStreamingAssistantId('');
        activeRunIdRef.current = '';
      }
    }
  };

  const stop = async () => {
    const runId = activeRunIdRef.current;
    if (runId) {
      try {
        await opsAdminService.cancelUserChatRun(runId);
      } catch {
        Toast.warning('后端取消未确认，已先停止本地流式连接。');
      }
    }
    abortRef.current?.abort();
    abortRef.current = null;
    setSending(false);
    setStreamingAssistantId('');
    activeRunIdRef.current = '';
  };

  return (
    <OpsPageShell selectedKey="chat" maxWidth="none" padding="0">
      <ChatWorkspace>
        <SessionBackdrop type="button" aria-label="关闭会话列表" $visible={sessionPanelOpen} onClick={() => setSessionPanelOpen(false)} />
        <SessionPane $mobileOpen={sessionPanelOpen}>
          <SessionHeader>
            <strong>对话</strong>
            <SessionActions>
              <Button block icon={<IconPlus />} theme="solid" type="primary" loading={creatingConversation} disabled={sending} onClick={() => void createConversation()}>
                新对话
              </Button>
              <Button icon={<IconRefresh />} loading={loadingSessions} onClick={() => void loadSessions()} aria-label="刷新对话" />
            </SessionActions>
          </SessionHeader>
          <SearchWrap>
            <Input value={sessionKeyword} onChange={setSessionKeyword} placeholder="搜索对话" showClear />
          </SearchWrap>
          <SessionList>
            {filteredSessions.map((session) => (
              <SessionItem
                key={session.sessionId}
                type="button"
                disabled={sending || creatingConversation}
                $active={session.sessionId === activeSessionId}
                aria-current={session.sessionId === activeSessionId ? 'true' : undefined}
                onClick={() => {
                  setActiveSessionId(session.sessionId);
                  setLastRunId('');
                  setSearchParams({ projectId: projectScope.projectId, sessionId: session.sessionId }, { replace: true });
                  setSessionPanelOpen(false);
                }}
              >
                <SessionTitle>
                  <IconComment />
                  <Text strong ellipsis={{ showTooltip: true }}>{chatSessionTitle(session.title, session.lastMessage)}</Text>
                </SessionTitle>
                <SessionMeta>{session.metadata?.executionType === 'WORKFLOW' ? session.metadata?.executionName || '工作流' : '默认助手'}</SessionMeta>
                <SessionMeta>{chatPreview(session.lastMessage) || session.lastActiveAt || '暂无消息'}</SessionMeta>
              </SessionItem>
            ))}
            {!loadingSessions && filteredSessions.length === 0 && <Text type="tertiary" style={{ display: 'block', padding: 12 }}>{sessionKeyword.trim() ? '没有匹配的对话，请调整搜索条件。' : '还没有对话，直接输入问题即可开始。'}</Text>}
          </SessionList>
        </SessionPane>

        <Conversation>
          <ConversationHeader>
            <div className="header-identity">
              <HistoryToggle theme="borderless" icon={<IconMenu />} aria-label="打开会话列表" onClick={() => setSessionPanelOpen(true)} />
              <div className="title">
                <strong>{chatSessionTitle(activeSession?.title, activeSession?.lastMessage)}</strong>
                <span>{projectScope.selectedProject?.name || '未选择项目'}</span>
              </div>
            </div>
            <Space className="header-actions" spacing="tight" wrap>
              {sending && <Tag color="blue">生成中</Tag>}
              {activeSessionId && <Button size="small" onClick={() => setEpisodePanelOpen(true)}>任务分段</Button>}
              {lastRunId && <Button size="small" onClick={() => navigate(`/workbench?projectId=${encodeURIComponent(projectScope.projectId)}&runId=${encodeURIComponent(lastRunId)}`)}>查看运行</Button>}
            </Space>
          </ConversationHeader>

          <TaskEpisodePanel projectId={projectScope.projectId} sessionId={activeSessionId}
            visible={episodePanelOpen} onClose={() => setEpisodePanelOpen(false)} />

          <MessageList ref={messageListRef} data-testid="chat-message-scroll" onScroll={() => {
            const area = messageListRef.current;
            if (!area) return;
            const atLatest = area.scrollHeight - area.clientHeight - area.scrollTop < 120;
            stickToLatestRef.current = atLatest;
            setShowLatest(!atLatest);
          }}>
            {loadingMessages && <Spin />}
            {!loadingMessages && messages.length === 0 && (
              <Welcome>
                <h2>今天需要处理什么？</h2>
                <Paragraph>直接描述现象、问题或目标。默认助手会在当前项目边界内使用模型和工具；重复、确定性的流程可以切换到已发布工作流。</Paragraph>
              </Welcome>
            )}
            {messages.map((message) => (
              <MessageRow key={message.messageId} $role={message.role}>
                <MessageAvatar $role={message.role}>{message.role === 'user' ? (principal.username?.[0]?.toUpperCase() || 'U') : 'O'}</MessageAvatar>
                <MessageContent $role={message.role}>
                  <MessageMeta $role={message.role}>
                    <span>{message.role === 'user' ? principal.username || '你' : 'OrbisOps'}</span>
                    {message.messageId === streamingAssistantId && <><StreamingDot />正在处理</>}
                  </MessageMeta>
                  {message.messageId === streamingAssistantId && liveRunSteps.length > 0 && (
                    <RunTrace data-testid="chat-run-trace" aria-label="实时运行过程">
                      <RunTraceHeader>运行过程</RunTraceHeader>
                      {liveRunSteps.map((step) => (
                        <RunStep key={step.key} data-testid="chat-run-step">
                          <RunStepDot $status={step.status} />
                          <span>{step.label}</span>
                        </RunStep>
                      ))}
                    </RunTrace>
                  )}
                  <MessageBubble $role={message.role}>
                    {message.role === 'assistant' && message.content
                      ? <AssistantAnswer text={message.content} />
                      : message.content || (message.messageId === streamingAssistantId ? ' ' : '')}
                  </MessageBubble>
                </MessageContent>
              </MessageRow>
            ))}
          </MessageList>

          <ComposerDock data-testid="chat-composer">
            {showLatest && <div style={{ display: 'flex', justifyContent: 'center', marginBottom: 8 }}><Button size="small" onClick={scrollToBottom}>回到最新消息</Button></div>}
            <Composer>
              <ComposerContext>
                <div data-testid="project-selector" data-project={projectScope.projectId || 'NONE'}>
                  <Select
                    className="project-select"
                    size="small"
                    value={projectScope.projectId || undefined}
                    placeholder="选择项目"
                    aria-label="项目"
                    disabled={sending || creatingConversation}
                    onChange={(value) => {
                      projectScope.selectProject(String(value || ''));
                      setActiveSessionId('');
                      setSelectedWorkflowId('');
                      setLastRunId('');
                    }}
                  >
                    {projectScope.projects.map((project) => <Option key={project.projectId} value={project.projectId}>{project.name}</Option>)}
                  </Select>
                  <Button
                    size="small"
                    aria-label="刷新项目列表"
                    icon={<IconRefresh />}
                    loading={projectScope.loading}
                    disabled={sending || creatingConversation}
                    onClick={() => void projectScope.reloadProjects()}
                  />
                </div>
                <div data-testid="execution-selector" data-execution={selectedWorkflowId || 'DEFAULT_REACT'}>
                  <Select size="small" aria-label="执行方式" disabled={sending || creatingConversation} value={selectedWorkflowId} onChange={(value) => void changeExecution(value)}>
                    <Option value="">默认助手</Option>
                    {workflows.map((workflow) => <Option key={workflow.agentId} value={workflow.agentId}>{executionName(workflow)}</Option>)}
                  </Select>
                </div>
                <div data-testid="model-selector" data-model={selectedModelId || 'DEFAULT'}>
                  <ChatModelSelector models={models} disabled={sending || creatingConversation}
                    value={selectedModelId} onChange={changeModel} />
                </div>
              </ComposerContext>
              {projectScope.error && <Banner type="warning" description="项目目录暂时不可用。当前项目身份和已有运行已保留；刷新项目列表后才能开始新任务。" closeIcon={null} />}
              <TextArea
                value={input}
                autosize={{ minRows: 2, maxRows: 8 }}
                placeholder="给 OrbisOps 发消息..."
                aria-label="发送给 OrbisOps 的消息"
                disabled={!projectScope.projectId || Boolean(projectScope.error)}
                onChange={setInput}
                onKeyDown={(event) => {
                  if (shouldSendChatKey({ key: event.key, shiftKey: event.shiftKey, isComposing: event.nativeEvent.isComposing, keyCode: event.keyCode })) {
                    event.preventDefault();
                    void send();
                  }
                }}
              />
              <ComposerFooter>
                <Text type="tertiary" size="small">Enter 发送 · Shift + Enter 换行</Text>
                {sending ? (
                  <Button icon={<IconStop />} onClick={() => void stop()}>停止</Button>
                ) : (
                  <Button theme="solid" type="primary" icon={<IconSend />} disabled={!input.trim() || !projectScope.projectId || Boolean(projectScope.error) || creatingConversation || loadingMessages || loadingSessions} onClick={() => void send()}>
                    发送
                  </Button>
                )}
              </ComposerFooter>
            </Composer>
          </ComposerDock>
        </Conversation>
      </ChatWorkspace>
    </OpsPageShell>
  );
};
