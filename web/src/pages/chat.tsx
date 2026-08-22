import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Button, Input, Select, Space, Spin, Tag, Toast, Typography } from '@douyinfe/semi-ui';
import { IconComment, IconPlus, IconRefresh, IconSend } from '@douyinfe/semi-icons';
import { useNavigate, useSearchParams } from 'react-router-dom';
import styled from 'styled-components';

import { OpsPageHeader, OpsPageShell, OpsSectionCard } from '../components/ops-layout';
import { useProjectScope } from '../hooks/use-project-scope';
import { getStoredUserInfo } from '../services/auth-session';
import {
  opsAdminService,
  type OpsAgentDefinition,
  type OpsAgentChatResponse,
  type OpsChatMessage,
  type OpsChatSession,
} from '../services/ops-admin-service';
import { theme } from '../styles/theme';
import { userFacingError } from '../utils/user-facing-error';

const { Text, Paragraph } = Typography;
const { Option } = Select;

const Layout = styled.div`
  display: grid;
  grid-template-columns: minmax(230px, 300px) minmax(0, 1fr);
  min-height: 620px;
  border-top: 1px solid ${theme.colors.border.secondary};

  @media (max-width: 900px) {
    grid-template-columns: 1fr;
  }
`;

const SessionPane = styled.aside`
  min-width: 0;
  border-right: 1px solid ${theme.colors.border.secondary};
  background: ${theme.colors.bg.primary};

  @media (max-width: 900px) {
    border-right: 0;
    border-bottom: 1px solid ${theme.colors.border.secondary};
  }
`;

const SessionToolbar = styled.div`
  display: flex;
  gap: ${theme.spacing.sm};
  padding: ${theme.spacing.base};
  border-bottom: 1px solid ${theme.colors.border.secondary};
`;

const SessionList = styled.div`
  max-height: 640px;
  overflow: auto;
`;

const SessionButton = styled.button<{ $active: boolean }>`
  width: 100%;
  padding: 12px ${theme.spacing.base};
  border: 0;
  border-bottom: 1px solid ${theme.colors.border.secondary};
  background: ${(props) => (props.$active ? '#eff6ff' : theme.colors.bg.primary)};
  color: inherit;
  text-align: left;
  cursor: pointer;

  &:hover {
    background: ${theme.colors.bg.secondary};
  }
`;

const ChatPane = styled.section`
  min-width: 0;
  display: flex;
  flex-direction: column;
  min-height: 620px;
`;

const ChatControls = styled.div`
  display: flex;
  align-items: center;
  gap: ${theme.spacing.sm};
  padding: ${theme.spacing.base};
  border-bottom: 1px solid ${theme.colors.border.secondary};
  flex-wrap: wrap;
`;

const MessageList = styled.div`
  flex: 1;
  min-height: 360px;
  max-height: 560px;
  overflow: auto;
  padding: ${theme.spacing.lg};
  background: #f8fafc;
`;

const MessageRow = styled.div<{ $role: string }>`
  display: flex;
  justify-content: ${(props) => (props.$role === 'user' ? 'flex-end' : 'flex-start')};
  margin-bottom: ${theme.spacing.base};
`;

const Bubble = styled.div<{ $role: string }>`
  max-width: min(780px, 88%);
  padding: 12px 14px;
  border: 1px solid ${(props) => (props.$role === 'user' ? '#bfdbfe' : theme.colors.border.secondary)};
  border-radius: ${theme.borderRadius.base};
  background: ${(props) => (props.$role === 'user' ? '#eff6ff' : theme.colors.bg.primary)};
  color: ${theme.colors.text.primary};
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.65;
`;

const Composer = styled.div`
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: ${theme.spacing.sm};
  padding: ${theme.spacing.base};
  border-top: 1px solid ${theme.colors.border.secondary};
  background: ${theme.colors.bg.primary};
`;

const executionName = (workflow?: OpsAgentDefinition) => workflow?.name || workflow?.agentId || '已发布工作流';

const responseRunId = (response?: OpsAgentChatResponse): string => {
  const metadataRunId = String(response?.metadata?.runId || response?.metadata?.analysisRunId || '').trim();
  if (metadataRunId) return metadataRunId;
  const eventRunId = response?.events?.map((event) => event.runId).find(Boolean);
  return eventRunId || '';
};

export const ChatPage: React.FC = () => {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const projectScope = useProjectScope();
  const principal = getStoredUserInfo();
  const [workflows, setWorkflows] = useState<OpsAgentDefinition[]>([]);
  const [sessions, setSessions] = useState<OpsChatSession[]>([]);
  const [activeSessionId, setActiveSessionId] = useState(searchParams.get('sessionId') || '');
  const [selectedWorkflowId, setSelectedWorkflowId] = useState('');
  const [messages, setMessages] = useState<OpsChatMessage[]>([]);
  const [input, setInput] = useState('');
  const [loadingSessions, setLoadingSessions] = useState(false);
  const [loadingMessages, setLoadingMessages] = useState(false);
  const [sending, setSending] = useState(false);
  const [lastRunId, setLastRunId] = useState(searchParams.get('runId') || '');

  const activeSession = useMemo(
    () => sessions.find((session) => session.sessionId === activeSessionId),
    [activeSessionId, sessions],
  );

  const selectedWorkflow = useMemo(
    () => workflows.find((workflow) => workflow.agentId === selectedWorkflowId),
    [selectedWorkflowId, workflows],
  );

  const loadSessions = useCallback(async () => {
    setLoadingSessions(true);
    try {
      const response = await opsAdminService.listChatSessions({
        userId: principal.username,
        limit: 100,
      });
      const next = (response.data || []).filter((session) => !projectScope.projectId || session.projectId === projectScope.projectId);
      setSessions(next);
      setActiveSessionId((current) => {
        if (current && next.some((session) => session.sessionId === current)) return current;
        return next[0]?.sessionId || '';
      });
    } catch (error) {
      Toast.error(userFacingError(error, '加载对话列表失败，请稍后重试。'));
    } finally {
      setLoadingSessions(false);
    }
  }, [principal.username, projectScope.projectId]);

  useEffect(() => {
    if (!projectScope.projectId) {
      setWorkflows([]);
      return;
    }
    let active = true;
    opsAdminService.listChatAgents(projectScope.projectId)
      .then((response) => {
        if (!active) return;
        setWorkflows((response.data || []).filter((workflow) => (
          workflow.definitionKind === 'SPECIALIZED_WORKFLOW'
          && workflow.lifecycle === 'PUBLISHED'
        )));
      })
      .catch(() => {
        if (active) setWorkflows([]);
      });
    return () => {
      active = false;
    };
  }, [projectScope.projectId]);

  useEffect(() => {
    void loadSessions();
  }, [loadSessions]);

  useEffect(() => {
    if (!activeSessionId) {
      setMessages([]);
      return;
    }
    let active = true;
    setLoadingMessages(true);
    opsAdminService.listChatMessages(activeSessionId)
      .then((response) => {
        if (active) setMessages(response.data || []);
      })
      .catch((error) => {
        if (active) Toast.error(userFacingError(error, '加载对话内容失败，请稍后重试。'));
      })
      .finally(() => {
        if (active) setLoadingMessages(false);
      });
    return () => {
      active = false;
    };
  }, [activeSessionId]);

  useEffect(() => {
    const workflowId = activeSession?.metadata?.executionType === 'WORKFLOW' ? activeSession.agentId || '' : '';
    setSelectedWorkflowId(workflowId);
  }, [activeSession]);

  const createConversation = async (workflowId = selectedWorkflowId) => {
    if (!projectScope.projectId) {
      Toast.warning('请先选择 Project。');
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
          executionName: workflowId ? executionName(workflow) : '默认模型',
        },
      });
      const sessionId = response.data;
      setActiveSessionId(sessionId);
      setMessages([]);
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

  const send = async () => {
    const query = input.trim();
    if (!query || sending || !projectScope.projectId) return;
    setSending(true);
    try {
      let sessionId = activeSessionId;
      if (!sessionId) sessionId = await createConversation(selectedWorkflowId);
      if (!sessionId) return;

      const optimistic: OpsChatMessage = {
        messageId: `local-${Date.now()}`,
        sessionId,
        userId: principal.username,
        role: 'user',
        content: query,
        createdAt: new Date().toISOString(),
      };
      setMessages((current) => [...current, optimistic]);
      setInput('');

      const response = await opsAdminService.sendChatMessage(sessionId, {
        userId: principal.username,
        sessionId,
        projectId: projectScope.projectId,
        query,
        mode: 'AGENT',
        agentDefinitionId: selectedWorkflowId || undefined,
        agentVersion: selectedWorkflow?.version,
        metadata: {
          executionType: selectedWorkflowId ? 'WORKFLOW' : 'DEFAULT_REACT',
          triggerSource: 'CHAT',
        },
      });

      const runId = responseRunId(response.data);
      if (runId) {
        setLastRunId(runId);
        setSearchParams((current) => {
          const next = new URLSearchParams(current);
          next.set('projectId', projectScope.projectId);
          next.set('sessionId', sessionId);
          next.set('runId', runId);
          return next;
        }, { replace: true });
      }
      const history = await opsAdminService.listChatMessages(sessionId);
      setMessages(history.data || []);
      await loadSessions();
    } catch (error) {
      Toast.error(userFacingError(error, '发送消息失败，请稍后重试。'));
    } finally {
      setSending(false);
    }
  };

  return (
    <OpsPageShell selectedKey="chat">
      <OpsPageHeader
        title="对话"
        description="可以直接与默认模型对话，也可以显式运行已发布工作流。正式执行仍会以运行记录的形式进入工作台。"
        extra={lastRunId ? <Button onClick={() => navigate(`/workbench?projectId=${encodeURIComponent(projectScope.projectId)}&runId=${encodeURIComponent(lastRunId)}`)}>打开当前运行</Button> : undefined}
      />

      <OpsSectionCard title="对话">
        <Layout>
          <SessionPane>
            <SessionToolbar>
              <Button icon={<IconPlus />} theme="solid" type="primary" onClick={() => void createConversation()}>新建</Button>
              <Button icon={<IconRefresh />} loading={loadingSessions} onClick={() => void loadSessions()} />
            </SessionToolbar>
            <SessionList>
              {sessions.map((session) => (
                <SessionButton
                  key={session.sessionId}
                  type="button"
                  $active={session.sessionId === activeSessionId}
                  onClick={() => setActiveSessionId(session.sessionId)}
                >
                  <Space vertical align="start" spacing="tight" style={{ width: '100%' }}>
                    <Space wrap>
                      <IconComment />
                      <Text strong ellipsis={{ showTooltip: true }}>{session.title || '对话'}</Text>
                    </Space>
                    <Tag>{session.metadata?.executionType === 'WORKFLOW' ? '工作流' : '默认模型'}</Tag>
                    <Text type="tertiary" size="small">{session.lastMessage || session.lastActiveAt || session.sessionId}</Text>
                  </Space>
                </SessionButton>
              ))}
              {!loadingSessions && sessions.length === 0 && <Text type="tertiary" style={{ display: 'block', padding: 16 }}>还没有对话。</Text>}
            </SessionList>
          </SessionPane>

          <ChatPane>
            <ChatControls>
              <Select
                value={projectScope.projectId || undefined}
                placeholder="选择 Project"
                style={{ minWidth: 220 }}
                onChange={(value) => {
                  projectScope.selectProject(String(value || ''));
                  setActiveSessionId('');
                  setSelectedWorkflowId('');
                  setLastRunId('');
                }}
              >
                {projectScope.projects.map((project) => <Option key={project.projectId} value={project.projectId}>{project.name}</Option>)}
              </Select>
              <div data-testid="execution-selector" data-execution={selectedWorkflowId || 'DEFAULT_REACT'}>
                <Select
                  aria-label="执行方式"
                  value={selectedWorkflowId}
                  style={{ minWidth: 240 }}
                  onChange={(value) => void changeExecution(value)}
                >
                  <Option value="">默认模型</Option>
                  {workflows.map((workflow) => <Option key={workflow.agentId} value={workflow.agentId}>{executionName(workflow)}</Option>)}
                </Select>
              </div>
              <Text type="tertiary">执行方式仅对当前对话生效。</Text>
            </ChatControls>

            <MessageList>
              {loadingMessages && <Spin />}
              {!loadingMessages && messages.length === 0 && (
                <Paragraph type="tertiary">可以直接提问、排查问题、查询项目资源，也可以手动运行已发布工作流。</Paragraph>
              )}
              {messages.map((message) => (
                <MessageRow key={message.messageId} $role={message.role}>
                  <Bubble $role={message.role}>
                    <Text type="tertiary" size="small">{message.role === 'user' ? principal.username || '你' : 'OrbisOps'}</Text>
                    <div>{message.content}</div>
                  </Bubble>
                </MessageRow>
              ))}
            </MessageList>

            <Composer>
              <Input
                value={input}
                placeholder="给 OrbisOps 发消息..."
                disabled={!projectScope.projectId || sending}
                onChange={setInput}
                onKeyDown={(event) => {
                  if (event.key === 'Enter' && !event.shiftKey) {
                    event.preventDefault();
                    void send();
                  }
                }}
              />
              <Button theme="solid" type="primary" icon={<IconSend />} loading={sending} disabled={!input.trim() || !projectScope.projectId} onClick={() => void send()}>
                发送
              </Button>
            </Composer>
          </ChatPane>
        </Layout>
      </OpsSectionCard>
    </OpsPageShell>
  );
};
