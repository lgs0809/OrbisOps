import React, { useMemo, useState } from 'react';
import { Button, Checkbox, Input, Modal, Select, Space, Table, Tag, Toast, Typography } from '@douyinfe/semi-ui';
import { IconHistory, IconPlus, IconRefresh, IconSend } from '@douyinfe/semi-icons';

import { OpsEmptyState, OpsPageHeader, OpsPageShell, OpsSectionCard, TableScroll } from '../../../components/ops-layout';
import { ProjectScopeBar } from '../../../components/project-scope-bar';
import { API_CONFIG } from '../../../config/api';
import { useProjectScope } from '../../../hooks/use-project-scope';
import type { AdminUserResponseDTO } from '../../../services/admin-user-service';
import type { OpsAgentDefinition } from '../../../services/ops-admin-service';
import type { OpsProjectMember } from '../../../services/ops-project-service';
import {
  type OpsChannel,
  type OpsChannelAccessPolicy,
  type OpsChannelConnectionMode,
  type OpsChannelIdentity,
  type OpsChannelMessageRecord,
  type OpsChannelOutboxItem,
  type OpsChannelType,
} from '../../../services/ops-channel-service';
import { agentDisplayName } from '../../../utils/agent-capabilities';
import {
  useBindChannelIdentityMutation,
  useChannelEnabledUsersQuery,
  useChannelIdentitiesQuery,
  useChannelMessagesQuery,
  useChannelOutboxQuery,
  useChannelProjectMembersQuery,
  useChannelReadinessQuery,
  useChannelsQuery,
  useChannelTypesQuery,
  useChannelWorkflowOptionsQuery,
  useProcessChannelOutboxMutation,
  useRecoverInboundMutation,
  useSaveChannelMutation,
  useSendChannelTestMutation,
  useUpdateChannelOutboxMutation,
} from '../api/channel-queries';
import { ChannelCompatibilityMatrix } from '../components/ChannelCompatibilityMatrix';
import { ChannelProviderCards } from '../components/ChannelProviderCards';
import { ChannelReadinessPanel } from '../components/ChannelReadinessPanel';
import {
  credentialEnvironmentName,
  defaultConnectionMode,
  encodingAesKeyEnvironmentName,
  providerConfigPayload,
  secondaryCredentialEnvironmentName,
  validateProviderConfig,
  verificationTokenEnvironmentName,
} from '../model/channel-provider-config';
import { buildChannelCompatibilityMatrix, buildChannelProviderCards } from '../model/channel-model';

const { Paragraph, Text } = Typography;

type ExecutionType = 'NONE' | 'REACT' | 'WORKFLOW';
type VersionPolicy = 'LATEST_PUBLISHED' | 'PINNED_VERSION';
type WizardStep = 0 | 1 | 2;

interface ChannelForm {
  name: string;
  providerType: OpsChannelType;
  credentialEnvironmentVariable: string;
  secondaryCredentialEnvironmentVariable: string;
  verificationTokenEnvironmentVariable: string;
  encodingAesKeyEnvironmentVariable: string;
  appId: string;
  botId: string;
  botUsername: string;
  messageContentIntent: boolean;
  corpId: string;
  robotCode: string;
  cardTemplateId: string;
  connectionMode: OpsChannelConnectionMode;
  requireMention: boolean;
  respondToMentionAll: boolean;
  outboundUrl: string;
  timeoutSeconds: number;
  executionType: ExecutionType;
  workflowId: string;
  workflowVersionPolicy: VersionPolicy;
  workflowVersion?: number;
  accessPolicy: OpsChannelAccessPolicy;
  status: 'ACTIVE' | 'DISABLED';
}

const formFor = (type: OpsChannelType): ChannelForm => ({
  name: '',
  providerType: type,
  credentialEnvironmentVariable: '',
  secondaryCredentialEnvironmentVariable: '',
  verificationTokenEnvironmentVariable: '',
  encodingAesKeyEnvironmentVariable: '',
  appId: '',
  botId: '',
  botUsername: '',
  messageContentIntent: false,
  corpId: '',
  robotCode: '',
  cardTemplateId: '',
  connectionMode: defaultConnectionMode(type),
  requireMention: true,
  respondToMentionAll: false,
  outboundUrl: '',
  timeoutSeconds: 15,
  executionType: 'NONE',
  workflowId: '',
  workflowVersionPolicy: 'LATEST_PUBLISHED',
  workflowVersion: undefined,
  accessPolicy: 'DENY_UNKNOWN',
  status: 'ACTIVE',
});

const envPrefix = '$' + '{env:';
const environmentReference = (name: string) => envPrefix + name + '}';
const environmentVariableFromRef = (value?: string): string => {
  const text = String(value || '').trim();
  return text.startsWith(envPrefix) && text.endsWith('}') ? text.slice(envPrefix.length, -1) : '';
};

const providerName = (type: OpsChannelType): string => ({
  GENERIC_WEBHOOK: 'Generic Webhook',
  FEISHU: 'Feishu / Lark',
  WECOM: 'WeCom',
  DINGTALK: 'DingTalk',
  SLACK: 'Slack',
  TELEGRAM: 'Telegram',
  DISCORD: 'Discord',
  QQ: 'QQ',
  WECHAT: 'WeChat',
}[type]);

export const ChannelManagementPage: React.FC = () => {
  const scope = useProjectScope();
  const channelsQuery = useChannelsQuery(scope.projectId);
  const typesQuery = useChannelTypesQuery();
  const agentsQuery = useChannelWorkflowOptionsQuery(scope.projectId);
  const saveChannelMutation = useSaveChannelMutation(scope.projectId);
  const sendTestMutation = useSendChannelTestMutation(scope.projectId);
  const channels = channelsQuery.data || [];
  const agents = agentsQuery.data || [];
  const providerCards = useMemo(() => buildChannelProviderCards(typesQuery.data || [], channels), [typesQuery.data, channels]);
  const compatibilityRows = useMemo(() => buildChannelCompatibilityMatrix(typesQuery.data || []), [typesQuery.data]);
  const availableTypes = useMemo(() => new Set((typesQuery.data || []).map((item) => item.type)), [typesQuery.data]);
  const activeCount = useMemo(() => channels.filter((item) => item.status === 'ACTIVE').length, [channels]);

  const [wizardVisible, setWizardVisible] = useState(false);
  const [wizardStep, setWizardStep] = useState<WizardStep>(0);
  const [editing, setEditing] = useState<OpsChannel | null>(null);
  const [wizardChannel, setWizardChannel] = useState<OpsChannel | null>(null);
  const [form, setForm] = useState<ChannelForm>(() => formFor('GENERIC_WEBHOOK'));
  const readinessQuery = useChannelReadinessQuery(
    scope.projectId,
    wizardChannel?.channelId || '',
    wizardVisible && wizardStep === 2 && Boolean(wizardChannel?.channelId),
  );

  const [testChannel, setTestChannel] = useState<OpsChannel | null>(null);
  const [testTarget, setTestTarget] = useState('');
  const [testContent, setTestContent] = useState('这是一条 Channel 连通性测试消息。');

  const [messageChannel, setMessageChannel] = useState<OpsChannel | null>(null);
  const messagesQuery = useChannelMessagesQuery(scope.projectId, messageChannel?.channelId || '', 100, Boolean(messageChannel));
  const recoverInboundMutation = useRecoverInboundMutation(scope.projectId, messageChannel?.channelId || '');
  const [recoveryMessage, setRecoveryMessage] = useState<OpsChannelMessageRecord | null>(null);
  const [recoveryConfirmed, setRecoveryConfirmed] = useState(false);

  const [identityChannel, setIdentityChannel] = useState<OpsChannel | null>(null);
  const identitiesQuery = useChannelIdentitiesQuery(scope.projectId, identityChannel?.channelId || '', Boolean(identityChannel));
  const identityMessagesQuery = useChannelMessagesQuery(scope.projectId, identityChannel?.channelId || '', 200, Boolean(identityChannel));
  const identityUsersQuery = useChannelEnabledUsersQuery(Boolean(identityChannel));
  const identityMembersQuery = useChannelProjectMembersQuery(scope.projectId, Boolean(identityChannel));
  const bindIdentityMutation = useBindChannelIdentityMutation(scope.projectId, identityChannel?.channelId || '');
  const [editingIdentity, setEditingIdentity] = useState<OpsChannelIdentity | null>(null);
  const [identityForm, setIdentityForm] = useState({ externalSenderId: '', platformUserId: '', status: 'ACTIVE' as 'ACTIVE' | 'DISABLED' });

  const [outboxVisible, setOutboxVisible] = useState(false);
  const outboxQuery = useChannelOutboxQuery(scope.projectId, outboxVisible);
  const processOutboxMutation = useProcessChannelOutboxMutation();
  const updateOutboxMutation = useUpdateChannelOutboxMutation(scope.projectId);

  const openCreate = (type: OpsChannelType = 'GENERIC_WEBHOOK') => {
    setEditing(null);
    setWizardChannel(null);
    setWizardStep(0);
    setForm(formFor(type));
    setWizardVisible(true);
  };

  const openEdit = (channel: OpsChannel, step: WizardStep = 0) => {
    setEditing(channel);
    setWizardChannel(channel);
    setWizardStep(step);
    setForm({
      name: channel.name,
      providerType: channel.type,
      credentialEnvironmentVariable: environmentVariableFromRef(channel.credentialRef),
      secondaryCredentialEnvironmentVariable: environmentVariableFromRef(channel.config?.appCredentialRef),
      verificationTokenEnvironmentVariable: environmentVariableFromRef(channel.config?.verificationTokenRef),
      encodingAesKeyEnvironmentVariable: environmentVariableFromRef(channel.config?.encodingAesKeyRef),
      appId: channel.config?.clientId || channel.config?.appId || '',
      botId: channel.config?.botId || '',
      botUsername: channel.config?.botUsername || '',
      messageContentIntent: channel.config?.messageContentIntent ?? false,
      corpId: channel.config?.corpId || '',
      robotCode: channel.config?.robotCode || '',
      cardTemplateId: channel.config?.cardTemplateId || '',
      connectionMode: channel.config?.connectionMode || defaultConnectionMode(channel.type),
      requireMention: channel.config?.requireMention ?? true,
      respondToMentionAll: channel.config?.respondToMentionAll ?? false,
      outboundUrl: channel.config?.outboundUrl || '',
      timeoutSeconds: Number(channel.config?.timeoutSeconds || 15),
      executionType: channel.executionType || 'NONE',
      workflowId: channel.workflowId || '',
      workflowVersionPolicy: channel.workflowVersionPolicy || 'LATEST_PUBLISHED',
      workflowVersion: channel.workflowVersion,
      accessPolicy: channel.accessPolicy || 'DENY_UNKNOWN',
      status: channel.status,
    });
    setWizardVisible(true);
  };

  const validateConnect = (): boolean => {
    if (!form.name.trim()) { Toast.warning('请填写 Channel 名称'); return false; }
    if (!availableTypes.has(form.providerType)) { Toast.error('当前构建未安装该 Provider Adapter'); return false; }
    const providerError = validateProviderConfig(form);
    if (providerError) { Toast.warning(providerError); return false; }
    const envName = (form.credentialEnvironmentVariable.trim() || credentialEnvironmentName(form.name, form.providerType)).toUpperCase();
    if (!/^[A-Z_][A-Z0-9_]*$/.test(envName)) { Toast.warning('凭据环境变量只能包含大写字母、数字和下划线'); return false; }
    if (form.providerType === 'SLACK') {
      const secondaryEnvName = (form.secondaryCredentialEnvironmentVariable.trim() || secondaryCredentialEnvironmentName(form.name, form.providerType)).toUpperCase();
      if (!/^[A-Z_][A-Z0-9_]*$/.test(secondaryEnvName)) { Toast.warning('Slack App-level credential 环境变量格式不合法'); return false; }
    }
    if (form.providerType === 'WECHAT') {
      const verifyEnv = (form.verificationTokenEnvironmentVariable.trim() || verificationTokenEnvironmentName(form.name)).toUpperCase();
      const aesEnv = (form.encodingAesKeyEnvironmentVariable.trim() || encodingAesKeyEnvironmentName(form.name)).toUpperCase();
      if (!/^[A-Z_][A-Z0-9_]*$/.test(verifyEnv)) { Toast.warning('WeChat Verification Token 环境变量格式不合法'); return false; }
      if (!/^[A-Z_][A-Z0-9_]*$/.test(aesEnv)) { Toast.warning('WeChat EncodingAESKey 环境变量格式不合法'); return false; }
    }
    return true;
  };

  const validateRoute = (): boolean => {
    if (form.executionType === 'WORKFLOW' && !form.workflowId) { Toast.warning('请选择入站 Workflow'); return false; }
    if (form.executionType === 'WORKFLOW' && form.workflowVersionPolicy === 'PINNED_VERSION' && !form.workflowVersion) {
      Toast.warning('固定版本模式需要选择已发布 Workflow 版本'); return false;
    }
    return true;
  };

  const saveAndTest = async () => {
    if (!scope.projectId || !validateConnect() || !validateRoute()) return;
    try {
      const envName = (form.credentialEnvironmentVariable.trim() || credentialEnvironmentName(form.name, form.providerType)).toUpperCase();
      const secondaryEnvName = (form.secondaryCredentialEnvironmentVariable.trim() || secondaryCredentialEnvironmentName(form.name, form.providerType)).toUpperCase();
      const verificationEnvName = (form.verificationTokenEnvironmentVariable.trim() || verificationTokenEnvironmentName(form.name)).toUpperCase();
      const aesEnvName = (form.encodingAesKeyEnvironmentVariable.trim() || encodingAesKeyEnvironmentName(form.name)).toUpperCase();
      const providerConfig = {
        ...providerConfigPayload({
          ...form,
          verificationTokenRef: form.providerType === 'WECHAT' ? environmentReference(verificationEnvName) : undefined,
          encodingAesKeyRef: form.providerType === 'WECHAT' ? environmentReference(aesEnvName) : undefined,
        }),
        ...(form.providerType === 'SLACK' ? { appCredentialRef: environmentReference(secondaryEnvName) } : {}),
      };
      const payload = {
        projectId: scope.projectId,
        name: form.name.trim(),
        type: form.providerType,
        credentialRef: environmentReference(envName),
        config: providerConfig,
        executionType: form.executionType,
        workflowId: form.executionType === 'WORKFLOW' ? form.workflowId : undefined,
        workflowVersionPolicy: form.executionType === 'WORKFLOW' ? form.workflowVersionPolicy : undefined,
        workflowVersion: form.executionType === 'WORKFLOW' && form.workflowVersionPolicy === 'PINNED_VERSION' ? form.workflowVersion : undefined,
        accessPolicy: form.accessPolicy,
        status: form.status,
      };
      const response = await saveChannelMutation.mutateAsync({ channelId: editing?.channelId, payload });
      const saved = response.data;
      setEditing(saved);
      setWizardChannel(saved);
      setWizardStep(2);
      Toast.success('Channel 已保存，继续完成真实 readiness 检查');
    } catch (error) {
      Toast.error(error instanceof Error ? error.message : '保存 Channel 失败');
    }
  };

  const nextWizard = async () => {
    if (wizardStep === 0) {
      if (validateConnect()) setWizardStep(1);
      return;
    }
    if (wizardStep === 1) {
      await saveAndTest();
      return;
    }
    setWizardVisible(false);
  };

  const sendTest = async () => {
    if (!scope.projectId || !testChannel || !testTarget.trim() || !testContent.trim()) {
      Toast.warning('请填写通知目标和测试内容'); return;
    }
    try {
      const result = await sendTestMutation.mutateAsync({
        channelId: testChannel.channelId,
        target: testTarget.trim(),
        content: testContent.trim(),
      });
      if (result.data?.delivered !== true) throw new Error('Provider 未确认真实投递');
      Toast.success('测试消息已真实投递');
      setTestChannel(null);
    } catch (error) {
      Toast.error(error instanceof Error ? error.message : '测试投递失败');
    }
  };

  const recoverInbound = async () => {
    if (!scope.projectId || !messageChannel || !recoveryMessage?.externalMessageId || !recoveryConfirmed) return;
    try {
      await recoverInboundMutation.mutateAsync({ externalMessageId: recoveryMessage.externalMessageId, action: 'requeue' });
      Toast.success('消息已重新进入处理队列');
      setRecoveryMessage(null);
      setRecoveryConfirmed(false);
    } catch (error) {
      Toast.error(error instanceof Error ? error.message : '重新处理失败');
    }
  };

  const cancelInboundRecovery = async (item: OpsChannelMessageRecord) => {
    if (!scope.projectId || !messageChannel || !item.externalMessageId) return;
    try {
      await recoverInboundMutation.mutateAsync({ externalMessageId: item.externalMessageId, action: 'cancel' });
      Toast.success('已停止该消息，不会再次处理');
    } catch (error) { Toast.error(error instanceof Error ? error.message : '停止处理失败'); }
  };

  const projectUserOptions = useMemo(() => {
    const members = identityMembersQuery.data || [];
    const users = identityUsersQuery.data || [];
    const enabledKeys = new Set<string>();
    members.filter((member: OpsProjectMember) => (member.status || 'ENABLED') === 'ENABLED').forEach((member: OpsProjectMember) => {
      if (member.userId || member.user_id) enabledKeys.add(String(member.userId || member.user_id));
      if (member.username) enabledKeys.add(member.username);
    });
    const owner = scope.selectedProject?.owner || '';
    return users.filter((user: AdminUserResponseDTO) => Boolean(user.userId)
      && (enabledKeys.has(String(user.userId)) || enabledKeys.has(user.username) || owner === user.userId || owner === user.username));
  }, [identityMembersQuery.data, identityUsersQuery.data, scope.selectedProject?.owner]);

  const senderOptions = useMemo(() => Array.from(new Set([
    ...(identityMessagesQuery.data || []).filter((message) => message.direction === 'INBOUND').map((message) => message.senderId || ''),
    ...(identitiesQuery.data || []).map((identity) => identity.externalSenderId),
  ].filter(Boolean))), [identityMessagesQuery.data, identitiesQuery.data]);

  const openIdentities = (channel: OpsChannel) => {
    setIdentityChannel(channel);
    setEditingIdentity(null);
    setIdentityForm({ externalSenderId: '', platformUserId: '', status: 'ACTIVE' });
  };

  const saveIdentity = async () => {
    if (!scope.projectId || !identityChannel || !identityForm.externalSenderId || !identityForm.platformUserId) {
      Toast.warning('请选择外部发送人和平台用户'); return;
    }
    const user = projectUserOptions.find((item) => item.userId === identityForm.platformUserId);
    if (!user?.userId || !user.username) { Toast.error('所选平台用户已不可用，请刷新后重试'); return; }
    try {
      await bindIdentityMutation.mutateAsync({
        externalSenderId: identityForm.externalSenderId,
        platformUserId: user.userId,
        username: user.username,
        status: identityForm.status,
        expectedVersion: editingIdentity?.version,
      });
      Toast.success(editingIdentity ? '身份绑定已更新' : '身份绑定已创建');
      setEditingIdentity(null);
      setIdentityForm({ externalSenderId: '', platformUserId: '', status: 'ACTIVE' });
    } catch (error) { Toast.error(error instanceof Error ? error.message : '保存身份绑定失败'); }
  };

  const processOutbox = async () => {
    try {
      const result = await processOutboxMutation.mutateAsync();
      Toast.info(`本次处理 ${Number(result.data?.processed || 0)} 条，成功 ${Number(result.data?.success || 0)} 条`);
    } catch (error) { Toast.error(error instanceof Error ? error.message : '处理投递队列失败'); }
  };

  const updateOutbox = async (item: OpsChannelOutboxItem, action: 'requeue' | 'cancel') => {
    if (!scope.projectId) return;
    try {
      await updateOutboxMutation.mutateAsync({ id: item.id, action });
      Toast.success(action === 'requeue' ? '已重新进入投递队列' : '已停止继续投递');
    } catch (error) { Toast.error(error instanceof Error ? error.message : '更新投递任务失败'); }
  };

  const columns = [
    { title: 'Provider', width: 190, render: (_: unknown, item: OpsChannel) => <Space vertical align="start" spacing={2}><Text strong>{item.name}</Text><Text type="tertiary">{providerName(item.type)}</Text></Space> },
    { title: '入站处理', width: 190, render: (_: unknown, item: OpsChannel) => item.executionType === 'WORKFLOW'
      ? <Tag color="purple">{agentDisplayName(agents.find((agent) => agent.agentId === item.workflowId) || { agentId: item.workflowId } as OpsAgentDefinition)}</Tag>
      : item.executionType === 'REACT' ? <Tag color="blue">ReAct</Tag> : <Tag color="grey">仅通知</Tag> },
    { title: 'Access', width: 170, render: (_: unknown, item: OpsChannel) => <Tag color={item.accessPolicy === 'OBSERVE_ONLY_UNKNOWN' ? 'orange' : 'blue'}>{item.accessPolicy}</Tag> },
    { title: '状态', width: 100, render: (_: unknown, item: OpsChannel) => <Tag color={item.status === 'ACTIVE' ? 'green' : 'grey'}>{item.status}</Tag> },
    { title: '连接方式', width: 230, render: (_: unknown, item: OpsChannel) => item.type === 'GENERIC_WEBHOOK'
      ? <Text>Webhook{item.config?.outboundUrl ? ' · outbound configured' : ' · inbound only'}</Text>
      : <Text>{item.config?.connectionMode || 'LONG_CONNECTION'}</Text> },
    { title: '操作', width: 410, fixed: 'right' as const, render: (_: unknown, item: OpsChannel) => <Space wrap>
      <Button size="small" onClick={() => openEdit(item, 2)}>Readiness</Button>
      <Button size="small" icon={<IconSend />} disabled={item.status !== 'ACTIVE' || (item.type === 'GENERIC_WEBHOOK' && !item.config?.outboundUrl)} onClick={() => { setTestChannel(item); setTestTarget(''); }}>测试</Button>
      <Button size="small" icon={<IconHistory />} onClick={() => setMessageChannel(item)}>消息</Button>
      <Button size="small" onClick={() => openIdentities(item)}>身份</Button>
      <Button size="small" onClick={() => openEdit(item)}>编辑</Button>
    </Space> },
  ];

  const wizardLabels = ['1. Connect', '2. Route & Access', '3. Test'];
  const testableChannel = wizardChannel || editing;

  return (
    <OpsPageShell selectedKey="/platform/integrations">
      <OpsPageHeader title="Integrations / Channels" description="Channel 是 OrbisOps 的受控命令入口与通知出口。Provider 只负责协议与连接，不改变 Project、Identity、Approval 或 Landing authority。" />
      <ProjectScopeBar
        projects={scope.projects}
        projectId={scope.projectId}
        onChange={scope.selectProject}
        loading={scope.loading}
        onRefresh={scope.reloadProjects}
        actions={<Space wrap>
          <Button icon={<IconHistory />} onClick={() => setOutboxVisible(true)} disabled={!scope.projectId}>投递队列</Button>
          <Button theme="solid" icon={<IconPlus />} onClick={() => openCreate()} disabled={!scope.projectId}>新建 Channel</Button>
        </Space>}
      />

      <OpsSectionCard title="Channel Providers">
        <Paragraph type="tertiary">Provider 卡片来自后端真实 Adapter Catalog。尚未实现的 Provider 明确显示 Blocked；已配置也必须通过五项 readiness 才算 Ready。</Paragraph>
        <ChannelProviderCards providers={providerCards} onConnect={(type) => {
          const existing = channels.find((item) => item.type === type);
          if (existing) openEdit(existing);
          else openCreate(type);
        }} />
      </OpsSectionCard>

      <OpsSectionCard title="Provider Compatibility Matrix">
        <ChannelCompatibilityMatrix rows={compatibilityRows} />
      </OpsSectionCard>

      <OpsSectionCard title={`Channels · ${activeCount} active`} actions={[<Button key="refresh" icon={<IconRefresh />} loading={channelsQuery.isFetching} onClick={() => channelsQuery.refetch()}>刷新</Button>]}>
        <Paragraph type="tertiary">Generic Webhook 使用签名回调；原生 Provider 使用各自受认证的长连接、Stream、Socket Mode、Bot API long polling 或官方 Secure Webhook。所有入站最终进入同一 durable queue、identity/access policy 和 ReAct / Workflow runtime。</Paragraph>
        <TableScroll>
          <Table rowKey="channelId" columns={columns} dataSource={channels} loading={channelsQuery.isLoading || typesQuery.isLoading || agentsQuery.isLoading} pagination={false} scroll={{ x: 1300 }} empty={<OpsEmptyState title="暂无 Channel" description="从上方 Provider 卡片开始连接。" />} />
        </TableScroll>
      </OpsSectionCard>

      <Modal
        title={`${editing ? 'Manage' : 'Connect'} · ${providerName(form.providerType)}`}
        visible={wizardVisible}
        onCancel={() => setWizardVisible(false)}
        width={760}
        footer={<Space>
          {wizardStep > 0 && <Button onClick={() => setWizardStep((wizardStep - 1) as WizardStep)}>Back</Button>}
          <Button theme="solid" loading={saveChannelMutation.isPending} onClick={() => void nextWizard()}>{wizardStep === 0 ? 'Next' : wizardStep === 1 ? 'Save & Test' : 'Done'}</Button>
        </Space>}
      >
        <Space vertical align="start" style={{ width: '100%' }}>
          <Space wrap>{wizardLabels.map((label, index) => <Tag key={label} color={wizardStep === index ? 'blue' : index < wizardStep ? 'green' : 'grey'}>{index < wizardStep ? '✓ ' : ''}{label}</Tag>)}</Space>

          {wizardStep === 0 && <>
            <div style={{ width: '100%' }}><Text strong>Provider</Text><Select value={form.providerType} disabled={Boolean(editing)} style={{ width: '100%' }} onChange={(value) => {
              const providerType = String(value) as OpsChannelType;
              setForm({ ...formFor(providerType), name: form.name, providerType });
            }}>{(typesQuery.data || []).map((item) => <Select.Option key={item.type} value={item.type}>{item.displayName}</Select.Option>)}</Select></div>
            <div style={{ width: '100%' }}><Text strong>Channel Name</Text><Input value={form.name} placeholder="Production On-call" onChange={(name) => setForm({ ...form, name })} /></div>
            <div style={{ width: '100%' }}><Text strong>凭据环境变量</Text><Input value={form.credentialEnvironmentVariable} placeholder={credentialEnvironmentName(form.name, form.providerType)} onChange={(credentialEnvironmentVariable) => setForm({ ...form, credentialEnvironmentVariable })} /><Paragraph type="tertiary">数据库只保存环境变量引用，页面不读取或回显真实凭据。</Paragraph></div>

            {form.providerType === 'GENERIC_WEBHOOK' && <>
              <div style={{ width: '100%' }}><Text strong>Outbound URL（可选）</Text><Input value={form.outboundUrl} placeholder="https://gateway.example.com/orbisops/replies" onChange={(outboundUrl) => setForm({ ...form, outboundUrl })} /><Paragraph type="tertiary">留空表示只接收入站；不会把未投递伪装为成功。</Paragraph></div>
              <div style={{ width: '100%' }}><Text strong>Timeout Seconds</Text><Input value={String(form.timeoutSeconds)} onChange={(value) => setForm({ ...form, timeoutSeconds: Number(value) || 15 })} /></div>
            </>}

            {form.providerType === 'FEISHU' && <>
              <div style={{ width: '100%' }}><Text strong>Feishu App ID</Text><Input value={form.appId} placeholder="cli_xxx" onChange={(appId) => setForm({ ...form, appId })} /></div>
              <div style={{ width: '100%' }}><Text strong>Connection</Text><Select value={form.connectionMode} disabled style={{ width: '100%' }}><Select.Option value="LONG_CONNECTION">WebSocket long connection</Select.Option></Select></div>
              <Checkbox checked={form.requireMention} onChange={(event) => setForm({ ...form, requireMention: Boolean(event.target.checked) })}>群聊中要求 @Bot 才触发</Checkbox>
              <Checkbox checked={form.respondToMentionAll} onChange={(event) => setForm({ ...form, respondToMentionAll: Boolean(event.target.checked) })}>响应 @所有人</Checkbox>
            </>}

            {form.providerType === 'WECOM' && <>
              <div style={{ width: '100%' }}><Text strong>WeCom Bot ID</Text><Input value={form.botId} placeholder="bot id" onChange={(botId) => setForm({ ...form, botId })} /></div>
              <div style={{ width: '100%' }}><Text strong>Connection</Text><Select value={form.connectionMode} disabled style={{ width: '100%' }}><Select.Option value="LONG_CONNECTION">Intelligent Bot long connection</Select.Option></Select></div>
            </>}

            {form.providerType === 'DINGTALK' && <>
              <div style={{ width: '100%' }}><Text strong>DingTalk Client ID</Text><Input value={form.appId} placeholder="ding..." onChange={(appId) => setForm({ ...form, appId })} /></div>
              <div style={{ width: '100%' }}><Text strong>Corp ID</Text><Input value={form.corpId} placeholder="ding..." onChange={(corpId) => setForm({ ...form, corpId })} /></div>
              <div style={{ width: '100%' }}><Text strong>Robot Code</Text><Input value={form.robotCode} placeholder="robot code" onChange={(robotCode) => setForm({ ...form, robotCode })} /><Paragraph type="tertiary">用于主动群聊/单聊消息；不要假设它永远等于 Client ID。</Paragraph></div>
              <div style={{ width: '100%' }}><Text strong>Card Template ID</Text><Input value={form.cardTemplateId} placeholder="your-template-id.schema" onChange={(cardTemplateId) => setForm({ ...form, cardTemplateId })} /><Paragraph type="tertiary">请在钉钉卡片平台导入 OrbisOps 兼容模板；用于审批按钮和长任务进度原地更新。</Paragraph></div>
              <div style={{ width: '100%' }}><Text strong>Connection</Text><Select value={form.connectionMode} disabled style={{ width: '100%' }}><Select.Option value="LONG_CONNECTION">Stream long connection</Select.Option></Select></div>
            </>}

            {form.providerType === 'SLACK' && <>
              <div style={{ width: '100%' }}><Text strong>Slack App-level credential 环境变量</Text><Input value={form.secondaryCredentialEnvironmentVariable} placeholder={secondaryCredentialEnvironmentName(form.name, form.providerType)} onChange={(secondaryCredentialEnvironmentVariable) => setForm({ ...form, secondaryCredentialEnvironmentVariable })} /><Paragraph type="tertiary">主凭据环境变量用于 Bot credential；这里用于 Socket Mode 的 App-level credential。数据库只保存两个环境变量引用。</Paragraph></div>
              <div style={{ width: '100%' }}><Text strong>Connection</Text><Select value={form.connectionMode} disabled style={{ width: '100%' }}><Select.Option value="LONG_CONNECTION">Socket Mode</Select.Option></Select></div>
              <Checkbox checked={form.requireMention} onChange={(event) => setForm({ ...form, requireMention: Boolean(event.target.checked) })}>频道中要求 @App 才触发；DM 不需要</Checkbox>
              <Paragraph type="tertiary">若要接收附件，请为 Slack App 授予 files:read；OrbisOps 下载后会立即转换为受控对象引用，不持久化 Slack 私有下载地址。</Paragraph>
            </>}

            {form.providerType === 'TELEGRAM' && <>
              <div style={{ width: '100%' }}><Text strong>Telegram Bot Username</Text><Input value={form.botUsername} placeholder="@orbisops_bot" onChange={(botUsername) => setForm({ ...form, botUsername })} /><Paragraph type="tertiary">用于群组 @Bot 触发校验；OrbisOps 建立连接时会通过 getMe 校验 Username 与当前凭据对应的 Bot 一致。</Paragraph></div>
              <div style={{ width: '100%' }}><Text strong>Connection</Text><Select value={form.connectionMode} disabled style={{ width: '100%' }}><Select.Option value="LONG_CONNECTION">Bot API long polling</Select.Option></Select></div>
              <Checkbox checked={form.requireMention} onChange={(event) => setForm({ ...form, requireMention: Boolean(event.target.checked) })}>群组中要求 @Bot 才触发；私聊不需要</Checkbox>
              <Paragraph type="tertiary">无需公网 Webhook。Bot credential 只以环境变量引用保存；Inline Keyboard 审批继续使用平台统一的 opaque action token 与 RBAC。</Paragraph>
            </>}

            {form.providerType === 'DISCORD' && <>
              <div style={{ width: '100%' }}><Text strong>Connection</Text><Select value={form.connectionMode} disabled style={{ width: '100%' }}><Select.Option value="LONG_CONNECTION">Gateway v10 WebSocket</Select.Option></Select></div>
              <Checkbox checked={form.requireMention} onChange={(event) => {
                const requireMention = Boolean(event.target.checked);
                setForm({ ...form, requireMention, messageContentIntent: requireMention ? form.messageContentIntent : true });
              }}>Guild 中要求 @Bot 才触发；DM 不需要</Checkbox>
              <Checkbox checked={form.messageContentIntent} onChange={(event) => setForm({ ...form, messageContentIntent: Boolean(event.target.checked) })}>启用 MESSAGE_CONTENT privileged intent</Checkbox>
              <Paragraph type="tertiary">默认建议保留 @Bot gate 且不开 privileged intent；只有需要读取 Guild 中未 @Bot 的普通消息时才启用，并在 Discord Developer Portal 同步开启 Message Content Intent。</Paragraph>
              <Paragraph type="tertiary">Button component 审批通过 Gateway 收到 Interaction，并继续走 OrbisOps 的 opaque action token、RBAC、重放保护与统一审批 authority。</Paragraph>
            </>}

            {form.providerType === 'QQ' && <>
              <div style={{ width: '100%' }}><Text strong>QQ Bot AppID</Text><Input value={form.appId} placeholder="1020..." onChange={(appId) => setForm({ ...form, appId })} /><Paragraph type="tertiary">AppID 作为普通 Provider 配置保存；上面的凭据环境变量用于 App credential，真实值不会进入数据库。</Paragraph></div>
              <div style={{ width: '100%' }}><Text strong>Connection</Text><Select value={form.connectionMode} disabled style={{ width: '100%' }}><Select.Option value="LONG_CONNECTION">QQ Gateway WebSocket</Select.Option></Select></div>
              <Paragraph type="tertiary">当前原生能力覆盖 QQ C2C 私聊、群 @Bot 消息、主动/回复发送和 Inline Keyboard 审批；暂不宣称 QQ Guild、附件或原消息更新。</Paragraph>
              <Paragraph type="tertiary">按钮点击先向 QQ Open Platform ACK，再进入 OrbisOps 统一 opaque action token、RBAC、过期与重放保护；QQ Provider 本身不决定审批结果。</Paragraph>
            </>}

            {form.providerType === 'WECHAT' && <>
              <div style={{ width: '100%' }}><Text strong>WeChat Official Account AppID</Text><Input value={form.appId} placeholder="wx..." onChange={(appId) => setForm({ ...form, appId })} /></div>
              <div style={{ width: '100%' }}><Text strong>Callback Verification Token 环境变量</Text><Input value={form.verificationTokenEnvironmentVariable} placeholder={verificationTokenEnvironmentName(form.name)} onChange={(verificationTokenEnvironmentVariable) => setForm({ ...form, verificationTokenEnvironmentVariable })} /><Paragraph type="tertiary">只保存环境变量引用；回调必须使用微信安全模式签名与 AES 加密。</Paragraph></div>
              <div style={{ width: '100%' }}><Text strong>EncodingAESKey 环境变量</Text><Input value={form.encodingAesKeyEnvironmentVariable} placeholder={encodingAesKeyEnvironmentName(form.name)} onChange={(encodingAesKeyEnvironmentVariable) => setForm({ ...form, encodingAesKeyEnvironmentVariable })} /></div>
              <div style={{ width: '100%' }}><Text strong>Connection</Text><Select value={form.connectionMode} disabled style={{ width: '100%' }}><Select.Option value="WEBHOOK">Official Account Secure Webhook</Select.Option></Select></div>
              <Paragraph type="tertiary">当前原生能力仅覆盖公众号文本私聊入站，以及微信允许窗口内的客服文本回复。暂不宣称群聊、附件、卡片审批、原消息更新或无限制主动推送。</Paragraph>
              <Paragraph type="tertiary">不支持个人微信号 QR 注入或非官方 Session Bridge。</Paragraph>
            </>}
          </>}

          {wizardStep === 1 && <>
            <div data-testid="channel-inbound-execution" data-workflow-count={agents.length} style={{ width: '100%' }}><Text strong>Inbound Runtime</Text><Select value={form.executionType} style={{ width: '100%' }} onChange={(value) => {
              const executionType = String(value) as ExecutionType;
              setForm({ ...form, executionType, workflowId: executionType === 'WORKFLOW' ? form.workflowId : '', workflowVersion: undefined });
            }}><Select.Option value="NONE">Outbound only</Select.Option><Select.Option value="REACT">Project Default ReAct</Select.Option><Select.Option value="WORKFLOW">Specialized Workflow</Select.Option></Select></div>
            {form.executionType === 'WORKFLOW' && <>
              <div data-testid="channel-workflow-selector" style={{ width: '100%' }}><Text strong>Workflow</Text><Select value={form.workflowId || undefined} placeholder="选择已发布 Workflow" style={{ width: '100%' }} onChange={(value) => {
                const workflowId = String(value);
                const selected = agents.find((agent) => agent.agentId === workflowId);
                setForm({ ...form, workflowId, workflowVersion: form.workflowVersionPolicy === 'PINNED_VERSION' ? selected?.version : undefined });
              }}>{agents.map((agent) => <Select.Option key={agent.agentId} value={agent.agentId}>{agentDisplayName(agent)}</Select.Option>)}</Select></div>
              <div style={{ width: '100%' }}><Text strong>Version Policy</Text><Select value={form.workflowVersionPolicy} style={{ width: '100%' }} onChange={(value) => {
                const workflowVersionPolicy = String(value) as VersionPolicy;
                const selected = agents.find((agent) => agent.agentId === form.workflowId);
                setForm({ ...form, workflowVersionPolicy, workflowVersion: workflowVersionPolicy === 'PINNED_VERSION' ? selected?.version : undefined });
              }}><Select.Option value="LATEST_PUBLISHED">Latest published</Select.Option><Select.Option value="PINNED_VERSION">Pinned exact version</Select.Option></Select></div>
            </>}
            <div style={{ width: '100%' }}><Text strong>Unknown Sender Access</Text><Select value={form.accessPolicy} style={{ width: '100%' }} onChange={(value) => setForm({ ...form, accessPolicy: String(value) as OpsChannelAccessPolicy })}><Select.Option value="DENY_UNKNOWN">Deny unknown（默认）</Select.Option><Select.Option value="PAIRING">Pairing</Select.Option><Select.Option value="ALLOWLIST">Allowlist</Select.Option><Select.Option value="OBSERVE_ONLY_UNKNOWN">Observe only unknown（硬降权只读）</Select.Option></Select><Paragraph type="tertiary">未知身份默认不能进入 Project Runtime。Observe only unknown 由服务端 Tool Authority 强制为只读，不能 ChangePackage、Approval 或 Landing。</Paragraph></div>
            <div style={{ width: '100%' }}><Text strong>Status</Text><Select value={form.status} style={{ width: '100%' }} onChange={(value) => setForm({ ...form, status: String(value) as 'ACTIVE' | 'DISABLED' })}><Select.Option value="ACTIVE">Active</Select.Option><Select.Option value="DISABLED">Disabled</Select.Option></Select></div>
          </>}

          {wizardStep === 2 && <>
            <ChannelReadinessPanel readiness={readinessQuery.data} loading={readinessQuery.isFetching} onRefresh={() => readinessQuery.refetch()} />
            {form.providerType === 'WECHAT' && testableChannel && <div style={{ width: '100%' }}><Text strong>Callback URL</Text><Input readOnly value={`${API_CONFIG.BASE_DOMAIN}/api/v1/channels/${testableChannel.channelId}/wechat/callback`} /><Paragraph type="tertiary">在公众号后台将消息加解密方式设为安全模式，并使用 Connect 步骤配置的 Verification Token 与 EncodingAESKey。</Paragraph></div>}
            {testableChannel && <Space wrap>
              <Button onClick={() => { setTestChannel(testableChannel); setTestTarget(''); }}>Test outbound</Button>
              <Button onClick={() => openIdentities(testableChannel)}>Identity / Access</Button>
              <Button onClick={() => setMessageChannel(testableChannel)}>Message history</Button>
            </Space>}
            <Paragraph type="tertiary">没有真实 Provider 凭据或真实消息时，Credentials / Connection / Inbound / Outbound 会保持 Blocked / Action Required，不会伪造 PASS。</Paragraph>
          </>}
        </Space>
      </Modal>

      <Modal title={`测试发送 · ${testChannel?.name || ''}`} visible={Boolean(testChannel)} onCancel={() => setTestChannel(null)} onOk={() => void sendTest()} confirmLoading={sendTestMutation.isPending} width={620}>
        <Space vertical align="start" style={{ width: '100%' }}>
          <div style={{ width: '100%' }}><Text strong>目标会话 / 接收人</Text><Input value={testTarget} placeholder="Provider conversation / chat / user id" onChange={setTestTarget} /></div>
          <div style={{ width: '100%' }}><Text strong>测试内容</Text><Input value={testContent} onChange={setTestContent} /></div>
          <Paragraph type="tertiary">这是实际 Provider 投递测试；失败会明确报错并写入 Channel message/audit，不会转换成成功状态。</Paragraph>
        </Space>
      </Modal>

      <Modal title={`消息记录 · ${messageChannel?.name || ''}`} visible={Boolean(messageChannel)} onCancel={() => setMessageChannel(null)} footer={null} width={980}>
        <Paragraph type="tertiary">同一发送人与会话中的消息按 durable queue 顺序处理。结果未知时平台不会自动重放可能已有副作用的任务。</Paragraph>
        <Table rowKey="messageId" loading={messagesQuery.isLoading} dataSource={messagesQuery.data || []} pagination={false} scroll={{ x: 1120, y: 480 }} columns={[
          { title: '方向', dataIndex: 'direction', width: 90, render: (value: string) => <Tag color={value === 'INBOUND' ? 'blue' : 'green'}>{value}</Tag> },
          { title: '目标 / 发送人', width: 180, render: (_: unknown, item: OpsChannelMessageRecord) => item.direction === 'INBOUND' ? item.senderId : item.externalConversationId },
          { title: 'Run', dataIndex: 'runId', width: 190, render: (value: string) => value || '-' },
          { title: '状态', dataIndex: 'status', width: 150, render: (value: string) => <Tag color={value === 'COMPLETED' || value === 'DELIVERED' ? 'green' : ['FAILED', 'REPLY_FAILED', 'RECOVERY_REQUIRED'].includes(value) ? 'red' : 'grey'}>{value}</Tag> },
          { title: '时间', dataIndex: 'createTime', width: 180 },
          { title: '错误', dataIndex: 'errorMessage', width: 240, render: (value: string) => value || '-' },
          { title: '操作', width: 190, fixed: 'right' as const, render: (_: unknown, item: OpsChannelMessageRecord) => item.status === 'RECOVERY_REQUIRED' ? <Space wrap><Button size="small" onClick={() => { setRecoveryConfirmed(false); setRecoveryMessage(item); }}>确认后重试</Button><Button size="small" type="danger" onClick={() => void cancelInboundRecovery(item)}>停止</Button></Space> : '-' },
        ]} />
      </Modal>

      <Modal title="确认重新处理入站消息" visible={Boolean(recoveryMessage)} onCancel={() => { setRecoveryMessage(null); setRecoveryConfirmed(false); }} onOk={() => void recoverInbound()} okButtonProps={{ disabled: !recoveryConfirmed, loading: recoverInboundMutation.isPending }} width={620}>
        <Space vertical align="start" style={{ width: '100%' }}>
          <Paragraph>平台无法证明上次处理是否已产生外部副作用，因此没有自动重试。</Paragraph>
          <Checkbox checked={recoveryConfirmed} onChange={(event) => setRecoveryConfirmed(Boolean(event.target.checked))}>我已核对审计和外部系统，确认上次处理未产生需要保留的副作用</Checkbox>
        </Space>
      </Modal>

      <Modal title={`身份绑定 · ${identityChannel?.name || ''}`} visible={Boolean(identityChannel)} onCancel={() => setIdentityChannel(null)} footer={null} width={920}>
        <Paragraph type="tertiary">绑定身份必须同时是有效平台账号与当前 Project 成员。未绑定发送人默认 fail closed。</Paragraph>
        <Space vertical align="start" style={{ width: '100%' }}>
          <Space wrap style={{ width: '100%' }}>
            <div style={{ minWidth: 220, flex: 1 }}><Text strong>外部发送人</Text><Select value={identityForm.externalSenderId || undefined} disabled={Boolean(editingIdentity)} placeholder={senderOptions.length ? '从真实入站消息中选择' : '收到第一条消息后可选择'} style={{ width: '100%' }} onChange={(value) => setIdentityForm({ ...identityForm, externalSenderId: String(value) })}>{senderOptions.map((senderId) => <Select.Option key={senderId} value={senderId}>{senderId}</Select.Option>)}</Select></div>
            <div style={{ minWidth: 220, flex: 1 }}><Text strong>平台用户</Text><Select value={identityForm.platformUserId || undefined} placeholder="选择当前项目成员" style={{ width: '100%' }} onChange={(value) => setIdentityForm({ ...identityForm, platformUserId: String(value) })}>{projectUserOptions.map((user) => <Select.Option key={user.userId} value={user.userId}>{user.username}</Select.Option>)}</Select></div>
            <div style={{ width: 130 }}><Text strong>状态</Text><Select value={identityForm.status} style={{ width: '100%' }} onChange={(value) => setIdentityForm({ ...identityForm, status: String(value) as 'ACTIVE' | 'DISABLED' })}><Select.Option value="ACTIVE">启用</Select.Option><Select.Option value="DISABLED">停用</Select.Option></Select></div>
            <Button theme="solid" loading={bindIdentityMutation.isPending} disabled={!identityForm.externalSenderId || !identityForm.platformUserId} onClick={() => void saveIdentity()}>{editingIdentity ? '保存修改' : '添加绑定'}</Button>
            {editingIdentity && <Button onClick={() => { setEditingIdentity(null); setIdentityForm({ externalSenderId: '', platformUserId: '', status: 'ACTIVE' }); }}>取消</Button>}
          </Space>
          <Table rowKey="mappingId" loading={identitiesQuery.isLoading || identityUsersQuery.isLoading || identityMembersQuery.isLoading} dataSource={identitiesQuery.data || []} pagination={false} scroll={{ y: 360 }} empty={<OpsEmptyState title="暂无身份绑定" description="先让外部用户发送一条真实消息，再从上方选择发送人与项目成员。" />} columns={[
            { title: '外部发送人', dataIndex: 'externalSenderId' },
            { title: '平台用户', dataIndex: 'username' },
            { title: '状态', dataIndex: 'status', width: 100, render: (value: string) => <Tag color={value === 'ACTIVE' ? 'green' : 'grey'}>{value}</Tag> },
            { title: '版本', dataIndex: 'version', width: 80 },
            { title: '操作', width: 100, render: (_: unknown, item: OpsChannelIdentity) => <Button size="small" onClick={() => { setEditingIdentity(item); setIdentityForm({ externalSenderId: item.externalSenderId, platformUserId: item.platformUserId, status: item.status }); }}>编辑</Button> },
          ]} />
        </Space>
      </Modal>

      <Modal title="Channel Outbox" visible={outboxVisible} onCancel={() => setOutboxVisible(false)} footer={null} width={980}>
        <Space wrap style={{ marginBottom: 12 }}><Button icon={<IconRefresh />} loading={outboxQuery.isFetching} onClick={() => outboxQuery.refetch()}>刷新</Button><Button theme="solid" loading={processOutboxMutation.isPending || updateOutboxMutation.isPending} onClick={() => void processOutbox()}>处理待投递</Button></Space>
        <Table rowKey="id" dataSource={outboxQuery.data || []} loading={outboxQuery.isLoading || processOutboxMutation.isPending || updateOutboxMutation.isPending} pagination={false} scroll={{ x: 1050, y: 480 }} columns={[
          { title: 'Channel', dataIndex: 'channelId', width: 160 },
          { title: '目标', dataIndex: 'target', width: 180 },
          { title: '类型', dataIndex: 'messageType', width: 150 },
          { title: '状态', dataIndex: 'status', width: 120, render: (value: string) => <Tag color={value === 'SUCCEEDED' ? 'green' : value === 'DEAD_LETTER' ? 'red' : value === 'RUNNING' ? 'blue' : 'grey'}>{value}</Tag> },
          { title: '重试', dataIndex: 'retryCount', width: 80 },
          { title: '错误', dataIndex: 'lastError', width: 220, render: (value: string) => value || '-' },
          { title: '操作', width: 190, fixed: 'right' as const, render: (_: unknown, item: OpsChannelOutboxItem) => ['DEAD_LETTER', 'FAILED'].includes(item.status) ? <Space><Button size="small" onClick={() => void updateOutbox(item, 'requeue')}>重试</Button><Button size="small" type="danger" onClick={() => void updateOutbox(item, 'cancel')}>取消</Button></Space> : '-' },
        ]} />
      </Modal>
    </OpsPageShell>
  );
};

export default ChannelManagementPage;
