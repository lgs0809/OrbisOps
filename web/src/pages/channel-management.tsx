import React, { useEffect, useMemo, useRef, useState } from 'react';
import { Button, Checkbox, Input, Modal, Select, Space, Table, Tag, Toast, Typography } from '@douyinfe/semi-ui';
import { IconHistory, IconPlus, IconRefresh, IconSend } from '@douyinfe/semi-icons';

import { OpsPageHeader, OpsPageShell, OpsSectionCard, OpsEmptyState, TableScroll } from '../components/ops-layout';
import { ProjectScopeBar } from '../components/project-scope-bar';
import { useProjectScope } from '../hooks/use-project-scope';
import { OpsAgentDefinition, opsAdminService } from '../services/ops-admin-service';
import { OpsChannel, OpsChannelIdentity, OpsChannelMessageRecord, OpsChannelOutboxItem, opsChannelService } from '../services/ops-channel-service';
import { AdminUserResponseDTO, AdminUserService } from '../services/admin-user-service';
import { OpsProjectMember, opsProjectService } from '../services/ops-project-service';
import { API_CONFIG } from '../config/api';
import { agentDisplayName } from '../utils/agent-capabilities';

const { Paragraph, Text } = Typography;

const emptyForm = {
  name: '', executionType: 'NONE' as 'NONE' | 'REACT' | 'WORKFLOW', workflowId: '',
  workflowVersionPolicy: 'LATEST_PUBLISHED' as 'LATEST_PUBLISHED' | 'PINNED_VERSION',
  workflowVersion: undefined as number | undefined,
  secretEnvironmentVariable: '', outboundUrl: '', timeoutSeconds: 15, status: 'ACTIVE',
};

const environmentName = (name: string) => `${name || 'OPS_CHANNEL'}_WEBHOOK_SECRET`
  .toUpperCase().replace(/[^A-Z0-9]+/g, '_').replace(/^_+|_+$/g, '');

export const ChannelManagementPage: React.FC = () => {
  const scope = useProjectScope();
  const [channels, setChannels] = useState<OpsChannel[]>([]);
  const [agents, setAgents] = useState<OpsAgentDefinition[]>([]);
  const [loading, setLoading] = useState(false);
  const [visible, setVisible] = useState(false);
  const [editing, setEditing] = useState<OpsChannel | null>(null);
  const [form, setFormState] = useState({ ...emptyForm });
  const formRef = useRef({ ...emptyForm });
  const setForm = (next: typeof emptyForm) => {
    formRef.current = next;
    setFormState(next);
  };
  const [testChannel, setTestChannel] = useState<OpsChannel | null>(null);
  const [testTarget, setTestTarget] = useState('');
  const [testContent, setTestContent] = useState('这是一条 Channel 连通性测试消息。');
  const [sending, setSending] = useState(false);
  const [messageChannel, setMessageChannel] = useState<OpsChannel | null>(null);
  const [messages, setMessages] = useState<OpsChannelMessageRecord[]>([]);
  const [messagesLoading, setMessagesLoading] = useState(false);
  const [recoveryMessage, setRecoveryMessage] = useState<OpsChannelMessageRecord | null>(null);
  const [recoveryConfirmed, setRecoveryConfirmed] = useState(false);
  const [recoveryLoading, setRecoveryLoading] = useState(false);
  const [identityChannel, setIdentityChannel] = useState<OpsChannel | null>(null);
  const [identities, setIdentities] = useState<OpsChannelIdentity[]>([]);
  const [identityMessages, setIdentityMessages] = useState<OpsChannelMessageRecord[]>([]);
  const [identityUsers, setIdentityUsers] = useState<AdminUserResponseDTO[]>([]);
  const [identityMembers, setIdentityMembers] = useState<OpsProjectMember[]>([]);
  const [identityLoading, setIdentityLoading] = useState(false);
  const [identitySaving, setIdentitySaving] = useState(false);
  const [editingIdentity, setEditingIdentity] = useState<OpsChannelIdentity | null>(null);
  const [identityForm, setIdentityForm] = useState({ externalSenderId: '', platformUserId: '', status: 'ACTIVE' as 'ACTIVE' | 'DISABLED' });
  const [outboxVisible, setOutboxVisible] = useState(false);
  const [outbox, setOutbox] = useState<OpsChannelOutboxItem[]>([]);
  const [outboxLoading, setOutboxLoading] = useState(false);

  const webhookBase = `${API_CONFIG.BASE_DOMAIN}/api/v1/channels`;
  const activeCount = useMemo(() => channels.filter((item) => item.status === 'ACTIVE').length, [channels]);

  const load = async () => {
    if (!scope.projectId) return;
    setLoading(true);
    try {
      const [channelResponse, agentResponse] = await Promise.all([
        opsChannelService.list(scope.projectId), opsAdminService.listAgents(scope.projectId),
      ]);
      setChannels(channelResponse.data || []);
      setAgents((agentResponse.data || []).filter((agent) => agent.definitionKind === 'SPECIALIZED_WORKFLOW'));
    } catch (error) {
      Toast.error(error instanceof Error ? error.message : '加载渠道失败');
    } finally { setLoading(false); }
  };

  useEffect(() => { void load(); }, [scope.projectId]);

  const openCreate = () => { setEditing(null); setForm({ ...emptyForm }); setVisible(true); };
  const openEdit = (channel: OpsChannel) => {
    setEditing(channel);
    setForm({
      name: channel.name,
      executionType: channel.executionType || 'NONE',
      workflowId: channel.workflowId || '',
      workflowVersionPolicy: channel.workflowVersionPolicy || 'LATEST_PUBLISHED',
      workflowVersion: channel.workflowVersion,
      secretEnvironmentVariable: channel.credentialRef.replace(/^\$\{env:|\}$/g, ''),
      outboundUrl: channel.config?.outboundUrl || '',
      timeoutSeconds: Number(channel.config?.timeoutSeconds || 15),
      status: channel.status,
    });
    setVisible(true);
  };

  const save = async () => {
    const snapshot = formRef.current;
    if (!scope.projectId || !snapshot.name.trim()) {
      Toast.warning('请填写渠道名称'); return;
    }
    if (snapshot.executionType === 'WORKFLOW' && !snapshot.workflowId) {
      Toast.warning('请选择入站 Workflow'); return;
    }
    if (snapshot.executionType === 'WORKFLOW' && snapshot.workflowVersionPolicy === 'PINNED_VERSION' && !snapshot.workflowVersion) {
      Toast.warning('固定版本模式需要选择一个已发布 Workflow 版本'); return;
    }
    const envName = (snapshot.secretEnvironmentVariable.trim() || environmentName(snapshot.name)).toUpperCase();
    if (!/^[A-Z_][A-Z0-9_]*$/.test(envName)) {
      Toast.warning('密钥环境变量只能包含大写字母、数字和下划线'); return;
    }
    const payload = {
      projectId: scope.projectId,
      name: snapshot.name.trim(),
      executionType: snapshot.executionType,
      workflowId: snapshot.executionType === 'WORKFLOW' ? snapshot.workflowId : undefined,
      workflowVersionPolicy: snapshot.executionType === 'WORKFLOW' ? snapshot.workflowVersionPolicy : undefined,
      workflowVersion: snapshot.executionType === 'WORKFLOW' && snapshot.workflowVersionPolicy === 'PINNED_VERSION' ? snapshot.workflowVersion : undefined,
      type: 'GENERIC_WEBHOOK',
      credentialRef: `\${env:${envName}}`,
      status: snapshot.status,
      config: { outboundUrl: snapshot.outboundUrl.trim(), timeoutSeconds: Number(snapshot.timeoutSeconds) || 15 },
    };
    try {
      if (editing) await opsChannelService.update(scope.projectId, editing.channelId, payload);
      else await opsChannelService.create(payload);
      Toast.success(editing ? '渠道已更新' : '渠道已创建');
      setVisible(false); await load();
    } catch (error) { Toast.error(error instanceof Error ? error.message : '保存渠道失败'); }
  };

  const sendTest = async () => {
    if (!scope.projectId || !testChannel || !testTarget.trim() || !testContent.trim()) {
      Toast.warning('请填写通知目标和测试内容'); return;
    }
    setSending(true);
    try {
      const result = await opsChannelService.send(scope.projectId, testChannel.channelId, testTarget.trim(), testContent.trim());
      if (result.data?.delivered !== true) throw new Error('渠道没有配置回复地址，未执行真实投递');
      Toast.success('测试消息已真实投递'); setTestChannel(null);
    } catch (error) { Toast.error(error instanceof Error ? error.message : '测试投递失败'); }
    finally { setSending(false); }
  };

  const openMessages = async (channel: OpsChannel) => {
    if (!scope.projectId) return;
    setMessageChannel(channel); setMessagesLoading(true);
    try { setMessages((await opsChannelService.messages(scope.projectId, channel.channelId, 100)).data || []); }
    catch (error) { Toast.error(error instanceof Error ? error.message : '加载消息记录失败'); }
    finally { setMessagesLoading(false); }
  };

  const recoverInbound = async () => {
    if (!scope.projectId || !messageChannel || !recoveryMessage?.externalMessageId || !recoveryConfirmed) return;
    setRecoveryLoading(true);
    try {
      await opsChannelService.requeueInboundRecovery(
        scope.projectId, messageChannel.channelId, recoveryMessage.externalMessageId,
      );
      Toast.success('消息已重新进入处理队列');
      setRecoveryMessage(null);
      setRecoveryConfirmed(false);
      await openMessages(messageChannel);
    } catch (error) { Toast.error(error instanceof Error ? error.message : '重新处理失败'); }
    finally { setRecoveryLoading(false); }
  };

  const cancelInboundRecovery = async (item: OpsChannelMessageRecord) => {
    if (!scope.projectId || !messageChannel || !item.externalMessageId) return;
    try {
      await opsChannelService.cancelInboundRecovery(scope.projectId, messageChannel.channelId, item.externalMessageId);
      Toast.success('已停止该消息，不会再次处理');
      await openMessages(messageChannel);
    } catch (error) { Toast.error(error instanceof Error ? error.message : '停止处理失败'); }
  };

  const loadIdentityData = async (channel: OpsChannel) => {
    if (!scope.projectId) return;
    setIdentityLoading(true);
    try {
      const [identityResponse, messageResponse, users, memberResponse] = await Promise.all([
        opsChannelService.identities(scope.projectId, channel.channelId),
        opsChannelService.messages(scope.projectId, channel.channelId, 200),
        AdminUserService.queryEnabledUsers(),
        opsProjectService.listProjectMembers(scope.projectId),
      ]);
      setIdentities(identityResponse.data || []);
      setIdentityMessages(messageResponse.data || []);
      setIdentityUsers(users || []);
      setIdentityMembers(memberResponse.data || []);
    } catch (error) {
      Toast.error(error instanceof Error ? error.message : '加载身份绑定失败');
    } finally { setIdentityLoading(false); }
  };

  const openIdentities = async (channel: OpsChannel) => {
    setIdentityChannel(channel);
    setEditingIdentity(null);
    setIdentityForm({ externalSenderId: '', platformUserId: '', status: 'ACTIVE' });
    await loadIdentityData(channel);
  };

  const saveIdentity = async () => {
    if (!scope.projectId || !identityChannel || !identityForm.externalSenderId || !identityForm.platformUserId) {
      Toast.warning('请选择外部发送人和平台用户'); return;
    }
    const user = identityUsers.find((item) => item.userId === identityForm.platformUserId);
    if (!user?.userId || !user.username) { Toast.error('所选平台用户已不可用，请刷新后重试'); return; }
    setIdentitySaving(true);
    try {
      await opsChannelService.bindIdentity(scope.projectId, identityChannel.channelId, {
        externalSenderId: identityForm.externalSenderId,
        platformUserId: user.userId,
        username: user.username,
        status: identityForm.status,
        expectedVersion: editingIdentity?.version,
      });
      Toast.success(editingIdentity ? '身份绑定已更新' : '身份绑定已创建');
      setEditingIdentity(null);
      setIdentityForm({ externalSenderId: '', platformUserId: '', status: 'ACTIVE' });
      await loadIdentityData(identityChannel);
    } catch (error) { Toast.error(error instanceof Error ? error.message : '保存身份绑定失败'); }
    finally { setIdentitySaving(false); }
  };

  const projectUserOptions = useMemo(() => {
    const enabledKeys = new Set<string>();
    identityMembers.filter((member) => (member.status || 'ENABLED') === 'ENABLED').forEach((member) => {
      if (member.userId || member.user_id) enabledKeys.add(String(member.userId || member.user_id));
      if (member.username) enabledKeys.add(member.username);
    });
    const owner = scope.selectedProject?.owner || '';
    return identityUsers.filter((user) => Boolean(user.userId)
      && (enabledKeys.has(String(user.userId)) || enabledKeys.has(user.username)
        || owner === user.userId || owner === user.username));
  }, [identityMembers, identityUsers, scope.selectedProject?.owner]);

  const senderOptions = useMemo(() => Array.from(new Set([
    ...identityMessages.filter((message) => message.direction === 'INBOUND').map((message) => message.senderId || ''),
    ...identities.map((identity) => identity.externalSenderId),
  ].filter(Boolean))), [identities, identityMessages]);

  const loadOutbox = async () => {
    if (!scope.projectId) return;
    setOutboxLoading(true);
    try { setOutbox((await opsChannelService.outbox(scope.projectId)).data || []); }
    catch (error) { Toast.error(error instanceof Error ? error.message : '加载投递队列失败'); }
    finally { setOutboxLoading(false); }
  };

  const openOutbox = async () => { setOutboxVisible(true); await loadOutbox(); };
  const processOutbox = async () => {
    setOutboxLoading(true);
    try {
      const result = await opsChannelService.processOutbox();
      Toast.info(`本次处理 ${Number(result.data?.processed || 0)} 条，成功 ${Number(result.data?.success || 0)} 条`);
      await loadOutbox();
    } catch (error) { Toast.error(error instanceof Error ? error.message : '处理投递队列失败'); }
    finally { setOutboxLoading(false); }
  };
  const updateOutbox = async (item: OpsChannelOutboxItem, action: 'requeue' | 'cancel') => {
    if (!scope.projectId) return;
    try {
      if (action === 'requeue') await opsChannelService.requeueOutbox(scope.projectId, item.id);
      else await opsChannelService.cancelOutbox(scope.projectId, item.id);
      Toast.success(action === 'requeue' ? '已重新进入投递队列' : '已停止继续投递');
      await loadOutbox();
    } catch (error) { Toast.error(error instanceof Error ? error.message : '更新投递任务失败'); }
  };

  const columns = [
    { title: '渠道', dataIndex: 'name', render: (_: unknown, item: OpsChannel) => (
      <Space vertical align="start" spacing={2}><Text strong>{item.name}</Text><Text type="tertiary">通用签名 Webhook</Text></Space>
    ) },
    { title: '入站处理', width: 210, render: (_: unknown, item: OpsChannel) => item.executionType === 'WORKFLOW'
      ? <Tag color="purple">{agentDisplayName(agents.find((a) => a.agentId === item.workflowId) || { agentId: item.workflowId } as OpsAgentDefinition)}</Tag>
      : item.executionType === 'REACT' ? <Tag color="blue">ReAct</Tag> : <Tag color="grey">不处理入站</Tag> },
    { title: 'Workflow 版本', width: 170, render: (_: unknown, item: OpsChannel) => item.executionType !== 'WORKFLOW' ? '-'
      : item.workflowVersionPolicy === 'PINNED_VERSION'
        ? <Tag color="blue">固定 v{item.workflowVersion || '?'}</Tag>
        : <Tag color="green">最新发布版</Tag> },
    { title: '状态', dataIndex: 'status', render: (value: string) => <Tag color={value === 'ACTIVE' ? 'green' : 'grey'}>{value === 'ACTIVE' ? '运行中' : '已停用'}</Tag> },
    { title: '接收地址', render: (_: unknown, item: OpsChannel) => <Text copyable ellipsis={{ showTooltip: true }}>{`${webhookBase}/${item.channelId}/webhook`}</Text> },
    { title: '操作', width: 340, render: (_: unknown, item: OpsChannel) => <Space wrap>
      <Button icon={<IconSend />} disabled={!item.config?.outboundUrl || item.status !== 'ACTIVE'} onClick={() => { setTestChannel(item); setTestTarget(''); }}>测试</Button>
      <Button icon={<IconHistory />} onClick={() => void openMessages(item)}>消息</Button>
      <Button onClick={() => void openIdentities(item)}>身份绑定</Button>
      <Button onClick={() => openEdit(item)}>编辑</Button>
    </Space> },
  ];

  return (
    <OpsPageShell selectedKey="channel-management">
      <OpsPageHeader title="消息渠道" description="把企业消息入口接到同一受控 Agent 主链路。渠道只负责收发消息，不改变 Tool Policy、审批或 Landing 边界。" />
      <ProjectScopeBar projects={scope.projects} projectId={scope.projectId} onChange={scope.selectProject}
        loading={scope.loading} onRefresh={scope.reloadProjects}
        actions={<Space wrap><Button icon={<IconHistory />} onClick={() => void openOutbox()} disabled={!scope.projectId}>投递队列</Button><Button theme="solid" icon={<IconPlus />} onClick={openCreate} disabled={!scope.projectId}>新建渠道</Button></Space>} />
      <OpsSectionCard title={`渠道列表 · ${activeCount} 个运行中`} actions={[<Button key="refresh" icon={<IconRefresh />} onClick={load}>刷新</Button>]}>
        <Paragraph type="tertiary">Channel 是统一消息传输层：可只用于通知，也可把入站消息交给 ReAct 或指定 Workflow。外部系统使用时间戳和 HMAC-SHA256 签名调用接收地址；平台按“渠道 + 会话 + 发送人”复用会话并做消息去重。</Paragraph>
        <TableScroll><Table rowKey="channelId" columns={columns} dataSource={channels} loading={loading} pagination={false}
          scroll={{ x: 1220 }} empty={<OpsEmptyState title="暂无消息渠道" description="新建通用 Webhook 后，可由企业微信机器人、中间网关或其他消息系统按契约接入。" />} /></TableScroll>
      </OpsSectionCard>
      <Modal title={editing ? '编辑消息渠道' : '新建消息渠道'} visible={visible} onCancel={() => setVisible(false)} onOk={save} width={680}>
        <Space vertical align="start" style={{ width: '100%' }}>
          <div style={{ width: '100%' }}><Text strong>渠道名称</Text><Input value={form.name} placeholder="生产告警群" onChange={(name) => setForm({ ...form, name })} /></div>
          <div data-testid="channel-inbound-execution" data-workflow-count={agents.length} style={{ width: '100%' }}><Text strong>入站处理</Text><Select aria-label="入站处理" value={form.executionType} style={{ width: '100%' }} onSelect={(value) => {
            const executionType = String(value) as 'NONE' | 'REACT' | 'WORKFLOW';
            setForm({ ...form, executionType, workflowId: executionType === 'WORKFLOW' ? form.workflowId : '', workflowVersion: undefined });
          }}><Select.Option value="NONE">不处理入站（仅用于通知 / 主动发送）</Select.Option><Select.Option value="REACT">ReAct</Select.Option><Select.Option value="WORKFLOW">Workflow</Select.Option></Select><Paragraph type="tertiary">Channel 只负责消息收发；入站消息可以交给项目 ReAct，也可以显式绑定一个已发布 Workflow。</Paragraph></div>
          {form.executionType === 'WORKFLOW' && <>
            <div data-testid="channel-workflow-selector" style={{ width: '100%' }}><Text strong>Workflow</Text><Select value={form.workflowId || undefined} placeholder="选择已发布 Workflow" style={{ width: '100%' }} onChange={(workflowId) => {
              const selected = agents.find((agent) => agent.agentId === String(workflowId));
              setForm({ ...form, workflowId: String(workflowId), workflowVersion: form.workflowVersionPolicy === 'PINNED_VERSION' ? selected?.version : undefined });
            }}>{agents.map((agent) => <Select.Option key={agent.agentId} value={agent.agentId}>{agentDisplayName(agent)}</Select.Option>)}</Select></div>
            <div style={{ width: '100%' }}><Text strong>Workflow 版本策略</Text><Select value={form.workflowVersionPolicy} style={{ width: '100%' }} onChange={(value) => {
              const workflowVersionPolicy = String(value) as 'LATEST_PUBLISHED' | 'PINNED_VERSION';
              const selected = agents.find((agent) => agent.agentId === form.workflowId);
              setForm({ ...form, workflowVersionPolicy, workflowVersion: workflowVersionPolicy === 'PINNED_VERSION' ? selected?.version : undefined });
            }}><Select.Option value="LATEST_PUBLISHED">每条新消息使用最新已发布版本</Select.Option><Select.Option value="PINNED_VERSION">固定到当前 Workflow 版本</Select.Option></Select><Paragraph type="tertiary">已开始的 Run 始终冻结 exact version/hash；固定模式适合生产 SOP。</Paragraph></div>
          </>}
          <div style={{ width: '100%' }}><Text strong>签名密钥环境变量</Text><Input value={form.secretEnvironmentVariable} placeholder={environmentName(form.name)} onChange={(secretEnvironmentVariable) => setForm({ ...form, secretEnvironmentVariable })} /><Paragraph type="tertiary">部署时把真实密钥注入这个环境变量。页面和数据库只保存 credentialRef。</Paragraph></div>
          <div style={{ width: '100%' }}><Text strong>回复地址（可选）</Text><Input value={form.outboundUrl} placeholder="https://gateway.example.com/ops-agent/replies" onChange={(outboundUrl) => setForm({ ...form, outboundUrl })} /><Paragraph type="tertiary">留空表示只接收消息、不主动回推；不会伪装成投递成功。本机联调可使用 localhost HTTP，其他地址必须 HTTPS。</Paragraph></div>
          <Space><Text strong>状态</Text><Select value={form.status} style={{ width: 140 }} onChange={(status) => setForm({ ...form, status: String(status) })}><Select.Option value="ACTIVE">运行中</Select.Option><Select.Option value="DISABLED">停用</Select.Option></Select></Space>
        </Space>
      </Modal>
      <Modal title={`测试发送 · ${testChannel?.name || ''}`} visible={Boolean(testChannel)} onCancel={() => setTestChannel(null)} onOk={sendTest} confirmLoading={sending} width={620}>
        <Space vertical align="start" style={{ width: '100%' }}>
          <div style={{ width: '100%' }}><Text strong>通知目标</Text><Input value={testTarget} placeholder="群聊、会话或接收人标识" onChange={setTestTarget} /></div>
          <div style={{ width: '100%' }}><Text strong>测试内容</Text><Input value={testContent} onChange={setTestContent} /></div>
          <Paragraph type="tertiary">测试会真实调用渠道的回复地址，并写入消息记录和审计；未配置回复地址时不会伪装成功。</Paragraph>
        </Space>
      </Modal>
      <Modal title={`消息记录 · ${messageChannel?.name || ''}`} visible={Boolean(messageChannel)} onCancel={() => setMessageChannel(null)} footer={null} width={960}>
        <Paragraph type="tertiary">同一发送人与会话中的消息按顺序处理。若进程中断且无法确认 Agent 是否已完成，状态会变为“结果未知”，平台不会自动重试。</Paragraph>
        <Table rowKey="messageId" loading={messagesLoading} dataSource={messages} pagination={false} scroll={{ x: 1120, y: 480 }} columns={[
          { title: '方向', dataIndex: 'direction', width: 90, render: (value: string) => <Tag color={value === 'INBOUND' ? 'blue' : 'green'}>{value === 'INBOUND' ? '收到' : '发送'}</Tag> },
          { title: '目标 / 发送人', width: 180, render: (_: unknown, item: OpsChannelMessageRecord) => item.direction === 'INBOUND' ? item.senderId : item.externalConversationId },
          { title: 'Run', dataIndex: 'runId', width: 190, render: (value: string) => value || '-' },
          { title: '状态', dataIndex: 'status', width: 150, render: (value: string) => {
            const labels: Record<string, string> = { RECEIVED: '已接收', QUEUED: '等待处理', PROCESSING: '处理中', COMPLETED: '已完成', REPLY_QUEUED: '回复待投递', REPLY_FAILED: '回复失败', FAILED: '处理失败', RECOVERY_REQUIRED: '结果未知', CANCELLED: '已停止' };
            const color = value === 'COMPLETED' ? 'green' : value === 'PROCESSING' ? 'blue' : ['FAILED', 'REPLY_FAILED', 'RECOVERY_REQUIRED'].includes(value) ? 'red' : 'grey';
            return <Tag color={color}>{labels[value] || value}</Tag>;
          } },
          { title: '时间', dataIndex: 'createTime', width: 180 },
          { title: '错误', dataIndex: 'errorMessage', width: 240, render: (value: string) => value || '-' },
          { title: '操作', width: 190, fixed: 'right' as const, render: (_: unknown, item: OpsChannelMessageRecord) => item.status === 'RECOVERY_REQUIRED' ? <Space wrap>
            <Button size="small" onClick={() => { setRecoveryConfirmed(false); setRecoveryMessage(item); }}>确认后重试</Button>
            <Button size="small" type="danger" onClick={() => void cancelInboundRecovery(item)}>停止</Button>
          </Space> : '-' },
        ]} />
      </Modal>
      <Modal title="确认重新处理入站消息" visible={Boolean(recoveryMessage)} onCancel={() => { setRecoveryMessage(null); setRecoveryConfirmed(false); }} onOk={() => void recoverInbound()} okButtonProps={{ disabled: !recoveryConfirmed, loading: recoveryLoading }} width={620}>
        <Space vertical align="start" style={{ width: '100%' }}>
          <Paragraph>平台检测到上次处理被中断，无法证明 Agent 是否已经产生外部副作用，因此没有自动重试。</Paragraph>
          <Checkbox checked={recoveryConfirmed} onChange={(event) => setRecoveryConfirmed(Boolean(event.target.checked))}>我已核对审计、外部系统和回复记录，确认上次处理未产生需要保留的副作用</Checkbox>
          <Paragraph type="tertiary">确认后将使用原消息、原项目和原会话重新进入处理队列。无法确认时请选择“停止”，再从 Chat 发起新的调查。</Paragraph>
        </Space>
      </Modal>
      <Modal title={`身份绑定 · ${identityChannel?.name || ''}`} visible={Boolean(identityChannel)} onCancel={() => setIdentityChannel(null)} footer={null} width={920}>
        <Paragraph type="tertiary">只有绑定到平台启用账号且已加入当前项目的发送人，才能以该用户身份执行固定控制动作。未绑定发送人仍可对话和调查，但不能审批或落地。</Paragraph>
        <Space vertical align="start" style={{ width: '100%' }}>
          <Space wrap style={{ width: '100%' }}>
            <div style={{ minWidth: 220, flex: 1 }}><Text strong>外部发送人</Text><Select value={identityForm.externalSenderId || undefined} disabled={Boolean(editingIdentity)} placeholder={senderOptions.length ? '从已收到的消息中选择' : '收到第一条消息后即可选择'} style={{ width: '100%' }} onChange={(value) => setIdentityForm({ ...identityForm, externalSenderId: String(value) })}>{senderOptions.map((senderId) => <Select.Option key={senderId} value={senderId}>{senderId}</Select.Option>)}</Select></div>
            <div style={{ minWidth: 220, flex: 1 }}><Text strong>平台用户</Text><Select value={identityForm.platformUserId || undefined} placeholder="选择当前项目成员" style={{ width: '100%' }} onChange={(value) => setIdentityForm({ ...identityForm, platformUserId: String(value) })}>{projectUserOptions.map((user) => <Select.Option key={user.userId} value={user.userId}>{user.username}</Select.Option>)}</Select></div>
            <div style={{ width: 130 }}><Text strong>状态</Text><Select value={identityForm.status} style={{ width: '100%' }} onChange={(value) => setIdentityForm({ ...identityForm, status: String(value) as 'ACTIVE' | 'DISABLED' })}><Select.Option value="ACTIVE">启用</Select.Option><Select.Option value="DISABLED">停用</Select.Option></Select></div>
            <Button theme="solid" loading={identitySaving} disabled={!identityForm.externalSenderId || !identityForm.platformUserId} onClick={() => void saveIdentity()}>{editingIdentity ? '保存修改' : '添加绑定'}</Button>
            {editingIdentity && <Button onClick={() => { setEditingIdentity(null); setIdentityForm({ externalSenderId: '', platformUserId: '', status: 'ACTIVE' }); }}>取消</Button>}
          </Space>
          <Table rowKey="mappingId" loading={identityLoading} dataSource={identities} pagination={false} scroll={{ y: 360 }} empty={<OpsEmptyState title="暂无身份绑定" description="先让外部用户发送一条消息，再从上方选择发送人和项目成员。" />} columns={[
            { title: '外部发送人', dataIndex: 'externalSenderId' },
            { title: '平台用户', dataIndex: 'username' },
            { title: '状态', dataIndex: 'status', width: 100, render: (value: string) => <Tag color={value === 'ACTIVE' ? 'green' : 'grey'}>{value === 'ACTIVE' ? '启用' : '停用'}</Tag> },
            { title: '版本', dataIndex: 'version', width: 80, render: (value: number) => `v${value}` },
            { title: '操作', width: 100, render: (_: unknown, identity: OpsChannelIdentity) => <Button onClick={() => { setEditingIdentity(identity); setIdentityForm({ externalSenderId: identity.externalSenderId, platformUserId: identity.platformUserId, status: identity.status }); }}>编辑</Button> },
          ]} />
        </Space>
      </Modal>
      <Modal title="Channel 投递队列" visible={outboxVisible} onCancel={() => setOutboxVisible(false)} footer={null} width={1080}>
        <Space vertical align="start" style={{ width: '100%' }}>
          <Space wrap><Button icon={<IconRefresh />} loading={outboxLoading} onClick={() => void loadOutbox()}>刷新</Button><Button onClick={() => void processOutbox()} disabled={outboxLoading}>立即处理可重试任务</Button></Space>
          <Paragraph type="tertiary">Chat 回复和分析通知都会先写入可靠队列。临时失败会自动重试；达到上限后进入“需人工处理”，可在确认渠道恢复后重新投递。</Paragraph>
          <TableScroll><Table rowKey="id" loading={outboxLoading} dataSource={outbox} pagination={{ pageSize: 20 }} scroll={{ x: 1080, y: 500 }} empty={<OpsEmptyState title="暂无投递任务" description="收到外部消息回复或发送分析通知后，这里会显示真实投递状态。" />} columns={[
            { title: '类型', dataIndex: 'messageType', width: 120, render: (value: string) => value === 'CHAT_REPLY' ? 'Chat 回复' : '分析通知' },
            { title: '渠道', dataIndex: 'channelId', width: 180, render: (value: string) => channels.find((item) => item.channelId === value)?.name || value },
            { title: '目标', dataIndex: 'target', width: 180 },
            { title: '状态', dataIndex: 'status', width: 130, render: (value: string) => {
              const label: Record<string, string> = { PENDING: '等待投递', RUNNING: '正在投递', FAILED: '等待重试', SUCCEEDED: '已送达', DEAD_LETTER: '需人工处理', CANCELLED: '已停止' };
              const color = value === 'SUCCEEDED' ? 'green' : value === 'DEAD_LETTER' || value === 'FAILED' ? 'red' : value === 'RUNNING' ? 'blue' : 'grey';
              return <Tag color={color}>{label[value] || value}</Tag>;
            } },
            { title: '重试', dataIndex: 'retryCount', width: 80 },
            { title: '错误', dataIndex: 'lastError', width: 260, render: (value: string) => value || '-' },
            { title: '操作', width: 170, fixed: 'right' as const, render: (_: unknown, item: OpsChannelOutboxItem) => <Space wrap>{['FAILED', 'DEAD_LETTER'].includes(item.status) && <Button size="small" onClick={() => void updateOutbox(item, 'requeue')}>重新投递</Button>}{['PENDING', 'FAILED', 'DEAD_LETTER'].includes(item.status) && <Button size="small" type="danger" onClick={() => void updateOutbox(item, 'cancel')}>停止</Button>}</Space> },
          ]} /></TableScroll>
        </Space>
      </Modal>
    </OpsPageShell>
  );
};

export default ChannelManagementPage;
