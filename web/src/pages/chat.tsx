import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
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

import { OpsPageShell } from '../components/ops-layout';
import { useProjectScope } from '../hooks/use-project-scope';
import {
  appendLiveRunStep,
  userFacingRunFailure,
  type LiveRunStep,
} from '../features/chat/live-run-trace';
import { getStoredUserInfo } from '../services/auth-session';
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

const ChatWorkspace = styled.div`
  position: relative;
  height: 100dvh;
  min-height: 0;
  display: grid;
  grid-template-columns: 260px minmax(0, 1fr);
  background: #fff;
  overflow: hidden;

  @media (max-width: 820px) {
    grid-template-columns: 1fr;
  }

  @media (max-width: ${theme.breakpoints.md}) {
    height: calc(100dvh - 52px);
  }
`;

const SessionPane = styled.aside<{ $mobileOpen: boolean }>`
  min-width: 0;
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
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 18px;
  border-bottom: 1px solid ${theme.colors.border.tertiary};

  .title {
    min-width: 0;
  }

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
`;

const MessageList = styled.div`
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 32px max(22px, calc((100% - 820px) / 2));
  background: #fff;

  @media (max-width: 820px) {
    min-height: 420px;
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
  grid-template-columns: 30px minmax(0, 1fr);
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
`;

const MessageBody = styled.div<{ $role: string }>`
  min-width: 0;
  padding: ${(props) => (props.$role === 'user' ? '10px 12px' : '2px 0')};
  border-radius: ${(props) => (props.$role === 'user' ? '11px' : '0')};
  background: ${(props) => (props.$role === 'user' ? '#f5f6f8' : 'transparent')};
  color: ${theme.colors.text.primary};
  font-size: 14px;
  line-height: 1.75;
  white-space: pre-wrap;
  word-break: break-word;
`;

const MessageMeta = styled.div`
  display: flex;
  align-items: center;
  gap: 7px;
  margin-bottom: 5px;
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
    .semi-select {
      min-width: 118px;
      max-width: 150px;
    }

    .project-select {
      min-width: 130px;
    }
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
  const [selectedWorkflowId, setSelectedWorkflowId] = useState('');
  const [selectedModelId, setSelectedModelId] = useState(() => localStorage.getItem(modelPreferenceKey) || '');
  const [messages, setMessages] = useState<OpsChatMessage[]>([]);
  const [input, setInput] = useState('');
  const [sessionKeyword, setSessionKeyword] = useState('');
  const [sessionPanelOpen, setSessionPanelOpen] = useState(false);
  const [loadingSessions, setLoadingSessions] = useState(false);
  const [loadingMessages, setLoadingMessages] = useState(false);
  const [sending, setSending] = useState(false);
  const [streamingAssistantId, setStreamingAssistantId] = useState('');
  const [liveRunSteps, setLiveRunSteps] = useState<LiveRunStep[]>([]);
  const [lastRunId, setLastRunId] = useState(searchParams.get('runId') || '');
  const abortRef = useRef<AbortController | null>(null);
  const activeRunIdRef = useRef('');
  const messageListRef = useRef<HTMLDivElement | null>(null);

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
    });
  }, []);

  useEffect(() => { scrollToBottom(); }, [messages, liveRunSteps, scrollToBottom]);

  const loadSessions = useCallback(async () => {
    setLoadingSessions(true);
    try {
      const response = await opsAdminService.listChatSessions({ userId: principal.username, limit: 100 });
      const next = (response.data || []).filter((session) => !projectScope.projectId || session.projectId === projectScope.projectId);
      setSessions(next);
      setActiveSessionId((current) => current && next.some((session) => session.sessionId === current) ? current : next[0]?.sessionId || '');
    } catch (error) {
      Toast.error(userFacingError(error, '加载对话列表失败，请稍后重试。'));
    } finally {
      setLoadingSessions(false);
    }
  }, [principal.username, projectScope.projectId]);

  useEffect(() => {
    void opsAdminService.listChatModels()
      .then((response) => {
        const next = response.data || [];
        setModels(next);
        setSelectedModelId((current) => current && next.some((model) => model.modelId === current) ? current : '');
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
    setLoadingMessages(true);
    opsAdminService.listChatMessages(activeSessionId)
      .then((response) => { if (active) setMessages(response.data || []); })
      .catch((error) => { if (active) Toast.error(userFacingError(error, '加载对话内容失败，请稍后重试。')); })
      .finally(() => { if (active) setLoadingMessages(false); });
    return () => { active = false; };
  }, [activeSessionId]);

  useEffect(() => {
    const workflowId = activeSession?.metadata?.executionType === 'WORKFLOW' ? activeSession.agentId || '' : '';
    setSelectedWorkflowId(workflowId);
  }, [activeSession]);

  const createConversation = async (workflowId = selectedWorkflowId) => {
    if (!projectScope.projectId) {
      Toast.warning('请先选择项目。');
      return '';
    }
    try {
      const workflow = workflows.find((item) => item.agentId === workflowId);
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
      await loadSessions();
      return sessionId;
    } catch (error) {
      Toast.error(userFacingError(error, '创建对话失败，请稍后重试。'));
      return '';
    }
  };

  const changeExecution = async (value: unknown) => {
    const workflowId = String(value || '');
    setSelectedWorkflowId(workflowId);
    await createConversation(workflowId);
  };

  const changeModel = (value: unknown) => {
    const modelId = String(value || '');
    setSelectedModelId(modelId);
    if (modelId) localStorage.setItem(modelPreferenceKey, modelId);
    else localStorage.removeItem(modelPreferenceKey);
  };

  const patchAssistant = (messageId: string, updater: (message: OpsChatMessage) => OpsChatMessage) => {
    setMessages((current) => current.map((message) => message.messageId === messageId ? updater(message) : message));
  };

  const send = async () => {
    const query = input.trim();
    if (!query || sending || !projectScope.projectId) return;

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
    setInput('');
    setSending(true);
    setLastRunId('');
    activeRunIdRef.current = '';
    abortRef.current?.abort();
    const controller = new AbortController();
    abortRef.current = controller;

    try {
      await opsAdminService.streamUserChat({
        userId: principal.username,
        sessionId,
        projectId: projectScope.projectId,
        query,
        mode: 'AGENT',
        engine: selectedWorkflow?.engine || 'GRAPH',
        agentDefinitionId: selectedWorkflowId || undefined,
        agentVersion: selectedWorkflow?.version,
        modelId: selectedModelId || undefined,
        enableThinking: true,
        metadata: {
          executionType: selectedWorkflowId ? 'WORKFLOW' : 'DEFAULT_REACT',
          triggerSource: 'CHAT',
          selectedModelId: selectedModelId || undefined,
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
          patchAssistant(localAssistantId, (message) => ({ ...message, content: event.content || message.content }));
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
      const history = await opsAdminService.listChatMessages(sessionId);
      setMessages(history.data || []);
      await loadSessions();
    } catch (error) {
      if (!controller.signal.aborted) {
        patchAssistant(localAssistantId, (message) => ({ ...message, content: message.content || '连接中断。运行可能仍在后台继续，可在工作台查看当前运行。' }));
        Toast.error(userFacingError(error, '流式连接中断，请检查服务状态。'));
      }
    } finally {
      if (abortRef.current === controller) abortRef.current = null;
      setSending(false);
      setStreamingAssistantId('');
      activeRunIdRef.current = '';
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
              <Button block icon={<IconPlus />} theme="solid" type="primary" onClick={() => void createConversation()}>
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
                $active={session.sessionId === activeSessionId}
                onClick={() => {
                  setActiveSessionId(session.sessionId);
                  setSessionPanelOpen(false);
                }}
              >
                <SessionTitle>
                  <IconComment />
                  <Text strong ellipsis={{ showTooltip: true }}>{session.title || '新对话'}</Text>
                </SessionTitle>
                <SessionMeta>{session.metadata?.executionType === 'WORKFLOW' ? session.metadata?.executionName || '工作流' : '默认助手'}</SessionMeta>
                <SessionMeta>{session.lastMessage || session.lastActiveAt || '暂无消息'}</SessionMeta>
              </SessionItem>
            ))}
            {!loadingSessions && filteredSessions.length === 0 && <Text type="tertiary" style={{ display: 'block', padding: 12 }}>还没有对话。</Text>}
          </SessionList>
        </SessionPane>

        <Conversation>
          <ConversationHeader>
            <Space spacing="tight" style={{ minWidth: 0 }}>
              <HistoryToggle theme="borderless" icon={<IconMenu />} aria-label="打开会话列表" onClick={() => setSessionPanelOpen(true)} />
              <div className="title">
                <strong>{activeSession?.title || '新对话'}</strong>
                <span>{projectScope.selectedProject?.name || '未选择项目'}</span>
              </div>
            </Space>
            <Space spacing="tight">
              {sending && <Tag color="blue">生成中</Tag>}
              {lastRunId && <Button size="small" onClick={() => navigate(`/workbench?projectId=${encodeURIComponent(projectScope.projectId)}&runId=${encodeURIComponent(lastRunId)}`)}>查看运行</Button>}
            </Space>
          </ConversationHeader>

          <MessageList ref={messageListRef} data-testid="chat-message-scroll">
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
                <MessageBody $role={message.role}>
                  <MessageMeta>
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
                  <div>{message.content || (message.messageId === streamingAssistantId ? ' ' : '')}</div>
                </MessageBody>
              </MessageRow>
            ))}
          </MessageList>

          <ComposerDock data-testid="chat-composer">
            <Composer>
              <ComposerContext>
                <div data-testid="project-selector" data-project={projectScope.projectId || 'NONE'}>
                  <Select
                    className="project-select"
                    size="small"
                    value={projectScope.projectId || undefined}
                    placeholder="选择项目"
                    aria-label="项目"
                    onChange={(value) => {
                      projectScope.selectProject(String(value || ''));
                      setActiveSessionId('');
                      setSelectedWorkflowId('');
                      setLastRunId('');
                    }}
                  >
                    {projectScope.projects.map((project) => <Option key={project.projectId} value={project.projectId}>{project.name}</Option>)}
                  </Select>
                </div>
                <div data-testid="execution-selector" data-execution={selectedWorkflowId || 'DEFAULT_REACT'}>
                  <Select size="small" aria-label="执行方式" value={selectedWorkflowId} onChange={(value) => void changeExecution(value)}>
                    <Option value="">默认助手</Option>
                    {workflows.map((workflow) => <Option key={workflow.agentId} value={workflow.agentId}>{executionName(workflow)}</Option>)}
                  </Select>
                </div>
                <div data-testid="model-selector" data-model={selectedModelId || 'DEFAULT'}>
                  <Select size="small" aria-label="模型" value={selectedModelId} onChange={changeModel}>
                    <Option value="">默认模型</Option>
                    {models.map((model) => <Option key={model.modelId} value={model.modelId}>{model.modelName || model.modelId}</Option>)}
                  </Select>
                </div>
              </ComposerContext>
              <TextArea
                value={input}
                autosize={{ minRows: 2, maxRows: 8 }}
                placeholder="给 OrbisOps 发消息..."
                disabled={!projectScope.projectId}
                onChange={setInput}
                onKeyDown={(event) => {
                  if (event.key === 'Enter' && !event.shiftKey) {
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
                  <Button theme="solid" type="primary" icon={<IconSend />} disabled={!input.trim() || !projectScope.projectId} onClick={() => void send()}>
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
