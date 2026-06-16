import React, { useMemo, useState } from 'react';
import {
  Button,
  Card,
  Checkbox,
  Divider,
  Input,
  Modal,
  Select,
  SideSheet,
  Space,
  Table,
  Tabs,
  TabPane,
  Tag,
  TextArea,
  Toast,
  Typography,
} from '@douyinfe/semi-ui';
import { IconEdit, IconPlus, IconRefresh, IconSend } from '@douyinfe/semi-icons';
import { useNavigate } from 'react-router-dom';
import styled from 'styled-components';

import { OpsPageHeader, OpsPageShell } from '../components/ops-layout';
import { ProjectScopeBar } from '../components/project-scope-bar';
import { ChannelCompatibilityMatrix } from '../features/channels/components/ChannelCompatibilityMatrix';
import { ChannelReadinessPanel } from '../features/channels/components/ChannelReadinessPanel';
import {
  useBindChannelIdentityMutation,
  useChannelEnabledUsersQuery,
  useChannelIdentitiesQuery,
  useChannelMessagesQuery,
  useChannelOutboxQuery,
  useChannelProjectMembersQuery,
  useChannelReadinessQuery,
  useChannelTypesQuery,
  useChannelWorkflowOptionsQuery,
  useChannelsQuery,
  useProcessChannelOutboxMutation,
  useSaveChannelMutation,
  useSendChannelTestMutation,
  useUpdateChannelOutboxMutation,
} from '../features/channels/api/channel-queries';
import { buildChannelCompatibilityMatrix } from '../features/channels/model/channel-model';
import { useProjectScope } from '../hooks/use-project-scope';
import type { OpsAgentDefinition } from '../services/ops-admin-service';
import type { AdminUserResponseDTO } from '../services/admin-user-service';
import type { OpsProjectMember } from '../services/ops-project-service';
import type {
  OpsChannel,
  OpsChannelAccessPolicy,
  OpsChannelConnectionMode,
  OpsChannelIdentity,
  OpsChannelProtocolDescriptor,
  OpsChannelType,
} from '../services/ops-channel-service';
import { theme } from '../styles/theme';
import { userFacingError } from '../utils/user-facing-error';

const { Paragraph, Text, Title } = Typography;
const { Option } = Select;

const Stack = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${theme.spacing.base};
  min-width: 0;
`;

const FormGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: ${theme.spacing.base};

  @media (max-width: 760px) {
    grid-template-columns: 1fr;
  }
`;

const Field = styled.div`
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
`;

const WideField = styled(Field)`
  grid-column: 1 / -1;
`;

const ProviderGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: ${theme.spacing.base};

  @media (max-width: 1100px) {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  @media (max-width: 720px) {
    grid-template-columns: 1fr;
  }
`;

const ProviderCard = styled(Card)`
  height: 100%;
`;

const DetailMeta = styled.div`
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: ${theme.spacing.sm};

  @media (max-width: 620px) {
    grid-template-columns: 1fr;
  }
`;

const MetaBox = styled.div`
  padding: ${theme.spacing.sm};
  border: 1px solid ${theme.colors.border.secondary};
  border-radius: ${theme.borderRadius.base};
`;

type ChannelForm = {
  name: string;
  providerType: OpsChannelType;
  credentialEnvironmentVariable: string;
  secondaryCredentialEnvironmentVariable: string;
  verificationEnvironmentVariable: string;
  encryptionEnvironmentVariable: string;
  connectionMode: OpsChannelConnectionMode;
  executionType: 'NONE' | 'REACT' | 'WORKFLOW';
  workflowId: string;
  workflowVersionPolicy: 'LATEST_PUBLISHED' | 'PINNED_VERSION';
  workflowVersion?: number;
  accessPolicy: OpsChannelAccessPolicy;
  status: 'ACTIVE' | 'DISABLED';
  outboundUrl: string;
  timeoutSeconds: number;
  appId: string;
  botId: string;
  botUsername: string;
  corpId: string;
  robotCode: string;
  cardTemplateId: string;
  requireMention: boolean;
  respondToMentionAll: boolean;
  messageContentIntent: boolean;
};

const providerDefaultMode = (descriptor?: OpsChannelProtocolDescriptor): OpsChannelConnectionMode =>
  descriptor?.connectionModes?.[0] || 'WEBHOOK';

const envNameFromRef = (value?: string) => {
  const raw = String(value || '').trim();
  const matched = raw.match(/^\$\{env:([A-Z_][A-Z0-9_]*)\}$/);
  return matched?.[1] || raw;
};

const envReference = (name: string) => '${env:' + name.trim().toUpperCase() + '}';

const suggestedEnvironmentName = (name: string, type: OpsChannelType) => {
  const slug = name.trim().toUpperCase().replace(/[^A-Z0-9]+/g, '_').replace(/^_+|_+$/g, '');
  return `ORBISOPS_${slug || type}_CREDENTIAL`;
};

const defaultForm = (descriptor?: OpsChannelProtocolDescriptor): ChannelForm => ({
  name: '',
  providerType: descriptor?.type || 'GENERIC_WEBHOOK',
  credentialEnvironmentVariable: '',
  secondaryCredentialEnvironmentVariable: '',
  verificationEnvironmentVariable: '',
  encryptionEnvironmentVariable: '',
  connectionMode: providerDefaultMode(descriptor),
  executionType: 'NONE',
  workflowId: '',
  workflowVersionPolicy: 'LATEST_PUBLISHED',
  workflowVersion: undefined,
  accessPolicy: 'DENY_UNKNOWN',
  status: 'ACTIVE',
  outboundUrl: '',
  timeoutSeconds: 15,
  appId: '',
  botId: '',
  botUsername: '',
  corpId: '',
  robotCode: '',
  cardTemplateId: '',
  requireMention: true,
  respondToMentionAll: false,
  messageContentIntent: false,
});

const formFromChannel = (channel: OpsChannel, descriptors: OpsChannelProtocolDescriptor[]): ChannelForm => ({
  ...defaultForm(descriptors.find((descriptor) => descriptor.type === channel.type)),
  name: channel.name,
  providerType: channel.type,
  credentialEnvironmentVariable: envNameFromRef(channel.credentialRef),
  secondaryCredentialEnvironmentVariable: envNameFromRef(channel.config?.appCredentialRef),
  verificationEnvironmentVariable: envNameFromRef(channel.config?.verificationTokenRef),
  encryptionEnvironmentVariable: envNameFromRef(channel.config?.encodingAesKeyRef || channel.config?.encryptKeyRef),
  connectionMode: channel.config?.connectionMode || providerDefaultMode(descriptors.find((descriptor) => descriptor.type === channel.type)),
  executionType: channel.executionType || 'NONE',
  workflowId: channel.workflowId || '',
  workflowVersionPolicy: channel.workflowVersionPolicy || 'LATEST_PUBLISHED',
  workflowVersion: channel.workflowVersion,
  accessPolicy: channel.accessPolicy || 'DENY_UNKNOWN',
  status: channel.status,
  outboundUrl: channel.config?.outboundUrl || '',
  timeoutSeconds: Number(channel.config?.timeoutSeconds || 15),
  appId: channel.config?.clientId || channel.config?.appId || '',
  botId: channel.config?.botId || '',
  botUsername: channel.config?.botUsername || '',
  corpId: channel.config?.corpId || '',
  robotCode: channel.config?.robotCode || '',
  cardTemplateId: channel.config?.cardTemplateId || '',
  requireMention: channel.config?.requireMention ?? true,
  respondToMentionAll: channel.config?.respondToMentionAll ?? false,
  messageContentIntent: channel.config?.messageContentIntent ?? false,
});

const publishedWorkflows = (workflows: OpsAgentDefinition[]) => workflows.filter((workflow) =>
  workflow.definitionKind === 'SPECIALIZED_WORKFLOW' && String(workflow.lifecycle || '').toUpperCase() === 'PUBLISHED',
);

const executionLabel = (channel: OpsChannel, workflows: OpsAgentDefinition[]) => {
  if (channel.executionType === 'NONE') return '仅发送通知';
  if (channel.executionType === 'REACT') return '默认助手';
  const workflow = workflows.find((item) => item.agentId === channel.workflowId);
  return `Workflow · ${workflow?.name || channel.workflowId || '-'}`;
};

const accessPolicyLabel = (policy?: OpsChannelAccessPolicy) => ({
  DENY_UNKNOWN: '拒绝未知身份',
  PAIRING: '首次配对后允许',
  ALLOWLIST: '仅允许白名单',
  OBSERVE_ONLY_UNKNOWN: '未知身份仅观察',
}[String(policy || '').toUpperCase()] || '受限访问');

const connectionModeLabel = (mode?: OpsChannelConnectionMode) => ({
  WEBHOOK: 'Webhook',
  CALLBACK: '回调',
  LONG_CONNECTION: '长连接',
  HYBRID: '混合模式',
  OUTBOUND_ONLY: '仅发送通知',
}[String(mode || '').toUpperCase()] || String(mode || '未声明'));

const capabilityLabel = (capability: string) => ({
  INBOUND: '接收消息',
  OUTBOUND: '发送消息',
  INTERACTIVE: '交互消息',
  THREADS: '话题 / 线程',
  CARDS: '卡片消息',
  MENTIONS: '@ 提及',
  FILES: '文件',
  APPROVAL: '审批回调',
}[capability.toUpperCase()] || capability);

const projectMemberUserId = (member: OpsProjectMember) => String(member.userId || member.user_id || '');
const projectMemberUsername = (member: OpsProjectMember) => String(member.username || '');
const activeMember = (member: OpsProjectMember) => !member.status || String(member.status).toUpperCase() === 'ACTIVE';

export const ChannelsPage: React.FC = () => {
  const navigate = useNavigate();
  const projectScope = useProjectScope();
  const projectId = projectScope.projectId;

  const channelsQuery = useChannelsQuery(projectId);
  const typesQuery = useChannelTypesQuery();
  const workflowQuery = useChannelWorkflowOptionsQuery(projectId);
  const saveMutation = useSaveChannelMutation(projectId);
  const outboxQuery = useChannelOutboxQuery(projectId, Boolean(projectId));
  const processOutboxMutation = useProcessChannelOutboxMutation();
  const updateOutboxMutation = useUpdateChannelOutboxMutation(projectId);

  const descriptors = typesQuery.data || [];
  const channels = channelsQuery.data || [];
  const workflows = useMemo(() => publishedWorkflows(workflowQuery.data || []), [workflowQuery.data]);
  const matrix = useMemo(() => buildChannelCompatibilityMatrix(descriptors), [descriptors]);

  const [modalVisible, setModalVisible] = useState(false);
  const [editing, setEditing] = useState<OpsChannel | null>(null);
  const [form, setForm] = useState<ChannelForm>(() => defaultForm());
  const [detailChannel, setDetailChannel] = useState<OpsChannel | null>(null);
  const [detailTab, setDetailTab] = useState('readiness');

  const openCreate = (type?: OpsChannelType) => {
    if (!projectId) {
      Toast.warning('请先选择 Project。');
      return;
    }
    const descriptor = descriptors.find((item) => item.type === type) || descriptors[0];
    setEditing(null);
    setForm(defaultForm(descriptor));
    setModalVisible(true);
  };

  const openEdit = (channel: OpsChannel) => {
    setEditing(channel);
    setForm(formFromChannel(channel, descriptors));
    setModalVisible(true);
  };

  const validateEnvironmentName = (value: string, required = true) => {
    const normalized = value.trim().toUpperCase();
    if (!normalized) return !required;
    return /^[A-Z_][A-Z0-9_]*$/.test(normalized);
  };

  const save = async () => {
    if (!projectId) return;
    if (!form.name.trim()) {
      Toast.error('请输入 Channel 名称。');
      return;
    }
    if (!descriptors.some((descriptor) => descriptor.type === form.providerType)) {
      Toast.error('当前版本未提供所选渠道，请选择其他可用渠道。');
      return;
    }
    const credentialName = (form.credentialEnvironmentVariable.trim() || suggestedEnvironmentName(form.name, form.providerType)).toUpperCase();
    if (!validateEnvironmentName(credentialName)) {
      Toast.error('凭据环境变量名只能使用大写字母、数字和下划线。');
      return;
    }
    if (form.executionType === 'WORKFLOW' && !form.workflowId) {
      Toast.error('请选择已发布 Workflow。');
      return;
    }
    if (form.executionType === 'WORKFLOW' && form.workflowVersionPolicy === 'PINNED_VERSION' && !form.workflowVersion) {
      Toast.error('固定版本 Workflow 必须指定一个已发布版本。');
      return;
    }
    if (form.providerType === 'SLACK' && form.secondaryCredentialEnvironmentVariable && !validateEnvironmentName(form.secondaryCredentialEnvironmentVariable)) {
      Toast.error('Slack App 级凭据引用不是合法的环境变量名。');
      return;
    }
    if (form.providerType === 'WECHAT') {
      if (!validateEnvironmentName(form.verificationEnvironmentVariable)) {
        Toast.error('微信校验凭据引用不能为空，并且必须是合法的环境变量名。');
        return;
      }
      if (!validateEnvironmentName(form.encryptionEnvironmentVariable)) {
        Toast.error('微信加密凭据引用不能为空，并且必须是合法的环境变量名。');
        return;
      }
    }

    const selectedWorkflow = workflows.find((workflow) => workflow.agentId === form.workflowId);
    const config: Record<string, unknown> = {
      connectionMode: form.connectionMode,
      timeoutSeconds: form.timeoutSeconds,
      requireMention: form.requireMention,
      respondToMentionAll: form.respondToMentionAll,
      messageContentIntent: form.messageContentIntent,
      ...(form.outboundUrl.trim() ? { outboundUrl: form.outboundUrl.trim() } : {}),
      ...(form.appId.trim() ? { appId: form.appId.trim(), clientId: form.appId.trim() } : {}),
      ...(form.botId.trim() ? { botId: form.botId.trim() } : {}),
      ...(form.botUsername.trim() ? { botUsername: form.botUsername.trim() } : {}),
      ...(form.corpId.trim() ? { corpId: form.corpId.trim() } : {}),
      ...(form.robotCode.trim() ? { robotCode: form.robotCode.trim() } : {}),
      ...(form.cardTemplateId.trim() ? { cardTemplateId: form.cardTemplateId.trim() } : {}),
      ...(form.secondaryCredentialEnvironmentVariable.trim()
        ? { appCredentialRef: envReference(form.secondaryCredentialEnvironmentVariable) }
        : {}),
      ...(form.verificationEnvironmentVariable.trim()
        ? { verificationTokenRef: envReference(form.verificationEnvironmentVariable) }
        : {}),
      ...(form.encryptionEnvironmentVariable.trim()
        ? { encodingAesKeyRef: envReference(form.encryptionEnvironmentVariable) }
        : {}),
    };

    const payload: Record<string, unknown> = {
      projectId,
      name: form.name.trim(),
      type: form.providerType,
      credentialRef: envReference(credentialName),
      config,
      executionType: form.executionType,
      workflowId: form.executionType === 'WORKFLOW' ? form.workflowId : undefined,
      workflowVersionPolicy: form.executionType === 'WORKFLOW' ? form.workflowVersionPolicy : undefined,
      workflowVersion: form.executionType === 'WORKFLOW' && form.workflowVersionPolicy === 'PINNED_VERSION'
        ? form.workflowVersion || selectedWorkflow?.version
        : undefined,
      accessPolicy: form.accessPolicy,
      status: form.status,
    };

    try {
      const response = await saveMutation.mutateAsync({ channelId: editing?.channelId, payload });
      if (response.code !== '0000') throw new Error(response.info || '保存 Channel 失败。');
      Toast.success(editing ? 'Channel 已更新。' : 'Channel 已接入。');
      setModalVisible(false);
      if (response.data) {
        setDetailChannel(response.data);
        setDetailTab('readiness');
      }
    } catch (error) {
      Toast.error(userFacingError(error, '保存 Channel 失败，请稍后重试。'));
    }
  };

  const columns = [
    {
      title: 'Channel',
      width: 230,
      render: (_: unknown, channel: OpsChannel) => (
        <div>
          <Text strong>{channel.name}</Text>
          <Text type="tertiary" size="small" style={{ display: 'block' }}>{descriptors.find((item) => item.type === channel.type)?.displayName || channel.type}</Text>
        </div>
      ),
    },
    {
      title: '执行方式',
      width: 250,
      render: (_: unknown, channel: OpsChannel) => <Tag color={channel.executionType === 'NONE' ? 'grey' : channel.executionType === 'REACT' ? 'green' : 'blue'}>{executionLabel(channel, workflows)}</Tag>,
    },
    {
      title: '访问策略',
      width: 160,
      render: (_: unknown, channel: OpsChannel) => <Tag>{accessPolicyLabel(channel.accessPolicy)}</Tag>,
    },
    {
      title: '状态',
      width: 110,
      render: (_: unknown, channel: OpsChannel) => <Tag color={channel.status === 'ACTIVE' ? 'green' : 'grey'}>{channel.status === 'ACTIVE' ? '已启用' : '已停用'}</Tag>,
    },
    { title: '更新时间', dataIndex: 'updateTime', width: 180 },
    {
      title: '操作',
      width: 250,
      render: (_: unknown, channel: OpsChannel) => (
        <Space>
          <Button size="small" onClick={() => { setDetailChannel(channel); setDetailTab('readiness'); }}>查看</Button>
          <Button size="small" icon={<IconEdit />} onClick={() => openEdit(channel)}>编辑</Button>
        </Space>
      ),
    },
  ];

  return (
    <OpsPageShell selectedKey="settings">
      <OpsPageHeader
        title="渠道"
        description="接入消息平台，用于接收用户请求、发送通知，并将需要处理的任务交给当前项目的默认执行能力或专用工作流。"
        extra={(
          <Space wrap>
            <Button onClick={() => navigate('/settings')}>返回设置</Button>
            <Button icon={<IconRefresh />} onClick={() => { void channelsQuery.refetch(); void typesQuery.refetch(); void workflowQuery.refetch(); }} loading={channelsQuery.isFetching || typesQuery.isFetching}>刷新</Button>
            <Button type="primary" icon={<IconPlus />} disabled={!projectId || descriptors.length === 0} onClick={() => openCreate()}>接入渠道</Button>
          </Space>
        )}
      />

      <Stack>
        <ProjectScopeBar
          projects={projectScope.projects}
          projectId={projectId}
          loading={projectScope.loading}
          onChange={projectScope.selectProject}
          onRefresh={projectScope.reloadProjects}
        />

        <Card>
          <Title heading={5} style={{ marginTop: 0 }}>可用渠道</Title>
          <Paragraph type="tertiary">
            这里只展示当前版本实际支持的渠道和能力。凭据使用部署环境变量引用，页面不会读取或回显真实凭据值。
          </Paragraph>
          <ProviderGrid data-testid="channel-provider-cards">
            {descriptors.map((descriptor) => (
              <ProviderCard key={descriptor.type}>
                <Space vertical align="start" spacing="tight" style={{ width: '100%' }}>
                  <Space wrap style={{ justifyContent: 'space-between', width: '100%' }}>
                    <Text strong>{descriptor.displayName}</Text>
                    <Tag color="green">已安装</Tag>
                  </Space>
                  <Text type="tertiary" size="small">{descriptor.connectionModes.map(connectionModeLabel).join(' / ') || '未声明连接模式'}</Text>
                  <Space wrap>
                    {descriptor.supportsInbound && <Tag>接收消息</Tag>}
                    {descriptor.supportsOutbound && <Tag>发送消息</Tag>}
                    {(descriptor.capabilitySet?.capabilities || []).slice(0, 4).map((capability) => <Tag key={capability} color="blue">{capabilityLabel(capability)}</Tag>)}
                  </Space>
                  <Button size="small" onClick={() => openCreate(descriptor.type)} disabled={!projectId}>接入</Button>
                </Space>
              </ProviderCard>
            ))}
            {!typesQuery.isLoading && descriptors.length === 0 && <Text type="tertiary">当前版本暂未提供可接入的渠道。</Text>}
          </ProviderGrid>
        </Card>

        <Card title="已配置渠道">
          <Table rowKey="channelId" columns={columns} dataSource={channels} loading={channelsQuery.isLoading} pagination={false} scroll={{ x: 1180 }} empty={<Text type="tertiary">当前项目还没有配置渠道。</Text>} />
        </Card>

        <Card title="服务商兼容性">
          <ChannelCompatibilityMatrix rows={matrix} />
        </Card>

        <OutboxSection
          projectId={projectId}
          items={outboxQuery.data || []}
          loading={outboxQuery.isFetching || processOutboxMutation.isPending || updateOutboxMutation.isPending}
          onRefresh={() => void outboxQuery.refetch()}
          onProcess={async () => {
            try {
              await processOutboxMutation.mutateAsync();
              Toast.success('Channel Outbox 已处理。');
            } catch (error) {
              Toast.error(userFacingError(error, '处理通知队列失败，请稍后重试。'));
            }
          }}
          onAction={async (id, action) => {
            try {
              await updateOutboxMutation.mutateAsync({ id, action });
              Toast.success(action === 'requeue' ? 'Outbox 项已重新入队。' : 'Outbox 项已取消。');
            } catch (error) {
              Toast.error(userFacingError(error, '更新通知队列失败，请稍后重试。'));
            }
          }}
        />
      </Stack>

      <Modal
        title={editing ? '编辑渠道' : '接入渠道'}
        visible={modalVisible}
        onOk={() => void save()}
        onCancel={() => setModalVisible(false)}
        okText="保存渠道"
        cancelText="取消"
        width={920}
      >
        <FormGrid>
          <Field>
            <Text strong>名称</Text>
            <Input placeholder="生产值班群" value={form.name} onChange={(name) => setForm({ ...form, name })} />
          </Field>
          <Field>
            <Text strong>服务商</Text>
            <Select
              value={form.providerType}
              disabled={Boolean(editing)}
              onChange={(value) => {
                const providerType = String(value) as OpsChannelType;
                const descriptor = descriptors.find((item) => item.type === providerType);
                setForm({ ...defaultForm(descriptor), name: form.name, providerType });
              }}
            >
              {descriptors.map((descriptor) => <Option key={descriptor.type} value={descriptor.type}>{descriptor.displayName}</Option>)}
            </Select>
          </Field>
          <Field>
            <Text strong>凭据环境变量</Text>
            <Input
              aria-label="凭据环境变量"
              value={form.credentialEnvironmentVariable}
              placeholder={suggestedEnvironmentName(form.name, form.providerType)}
              onChange={(credentialEnvironmentVariable) => setForm({ ...form, credentialEnvironmentVariable })}
            />
            <Text type="tertiary" size="small">这里只持久化环境变量引用；OrbisOps 不会把真实凭据返回到页面。</Text>
          </Field>
          <Field>
            <Text strong>连接模式</Text>
            <Select value={form.connectionMode} onChange={(value) => setForm({ ...form, connectionMode: String(value) as OpsChannelConnectionMode })}>
              {(descriptors.find((descriptor) => descriptor.type === form.providerType)?.connectionModes || [form.connectionMode]).map((mode) => <Option key={mode} value={mode}>{mode}</Option>)}
            </Select>
          </Field>

          <WideField data-testid="channel-inbound-execution" data-workflow-count={workflows.length}>
            <Text strong>入站执行方式</Text>
            <Select
              aria-label="入站执行方式"
              value={form.executionType}
              onChange={(value) => setForm({ ...form, executionType: String(value) as ChannelForm['executionType'], workflowId: String(value) === 'WORKFLOW' ? form.workflowId : '' })}
              style={{ width: '100%' }}
            >
              <Option value="NONE">仅出站</Option>
              <Option value="REACT">默认助手</Option>
              <Option value="WORKFLOW">专用工作流</Option>
            </Select>
            <Text type="tertiary" size="small">仅出站模式不会进入智能执行；入站消息始终先路由到所选项目，并创建可在工作台查看的运行记录。</Text>
          </WideField>

          {form.executionType === 'WORKFLOW' && (
            <>
              <WideField data-testid="channel-workflow-selector">
                <Text strong>专用工作流</Text>
                <Select
                  filter
                  value={form.workflowId || undefined}
                  onChange={(value) => {
                    const workflowId = String(value || '');
                    const workflow = workflows.find((item) => item.agentId === workflowId);
                    setForm({ ...form, workflowId, workflowVersion: workflow?.version });
                  }}
                  style={{ width: '100%' }}
                >
                  {workflows.map((workflow) => <Option key={workflow.agentId} value={workflow.agentId}>{workflow.name || workflow.agentId}</Option>)}
                </Select>
              </WideField>
              <Field>
                <Text strong>工作流版本策略</Text>
                <Select value={form.workflowVersionPolicy} onChange={(value) => setForm({ ...form, workflowVersionPolicy: String(value) as ChannelForm['workflowVersionPolicy'] })}>
                  <Option value="LATEST_PUBLISHED">始终使用最新已发布版本</Option>
                  <Option value="PINNED_VERSION">固定当前版本</Option>
                </Select>
              </Field>
              <Field>
                <Text strong>当前版本</Text>
                <Input disabled value={form.workflowVersion ? `v${form.workflowVersion}` : '运行时解析'} />
              </Field>
            </>
          )}

          <Field>
            <Text strong>未知发送者策略</Text>
            <Select value={form.accessPolicy} onChange={(value) => setForm({ ...form, accessPolicy: String(value) as OpsChannelAccessPolicy })}>
              <Option value="DENY_UNKNOWN">拒绝未知用户</Option>
              <Option value="PAIRING">配对后允许</Option>
              <Option value="ALLOWLIST">仅允许名单</Option>
              <Option value="OBSERVE_ONLY_UNKNOWN">未知用户仅观察</Option>
            </Select>
          </Field>
          <Field>
            <Text strong>状态</Text>
            <Select value={form.status} onChange={(value) => setForm({ ...form, status: String(value) as 'ACTIVE' | 'DISABLED' })}>
              <Option value="ACTIVE">启用</Option>
              <Option value="DISABLED">停用</Option>
            </Select>
          </Field>

          <ProviderFields form={form} setForm={setForm} />
        </FormGrid>
      </Modal>

      <SideSheet
        title={detailChannel ? `${detailChannel.name} · ${detailChannel.type}` : 'Channel 详情'}
        visible={Boolean(detailChannel)}
        onCancel={() => setDetailChannel(null)}
        width={760}
      >
        {detailChannel && (
          <ChannelDetail
            channel={detailChannel}
            projectId={projectId}
            workflows={workflows}
            tab={detailTab}
            onTabChange={setDetailTab}
            onOpenRun={(runId) => navigate(`/workbench?projectId=${encodeURIComponent(projectId)}&runId=${encodeURIComponent(runId)}`)}
          />
        )}
      </SideSheet>
    </OpsPageShell>
  );
};

const ProviderFields: React.FC<{ form: ChannelForm; setForm: React.Dispatch<React.SetStateAction<ChannelForm>> }> = ({ form, setForm }) => (
  <>
    {(form.providerType === 'GENERIC_WEBHOOK' || form.providerType === 'FEISHU' || form.providerType === 'WECOM') && (
      <WideField>
        <Text strong>出站 URL</Text>
        <Input value={form.outboundUrl} placeholder="https://gateway.example.com/orbisops/replies" onChange={(outboundUrl) => setForm({ ...form, outboundUrl })} />
      </WideField>
    )}
    {['FEISHU', 'DINGTALK', 'DISCORD', 'QQ', 'WECHAT'].includes(form.providerType) && (
      <Field>
        <Text strong>App / Client ID</Text>
        <Input aria-label="App / Client ID" value={form.appId} onChange={(appId) => setForm({ ...form, appId })} />
      </Field>
    )}
    {form.providerType === 'WECOM' && (
      <Field>
        <Text strong>Corp ID</Text>
        <Input value={form.corpId} onChange={(corpId) => setForm({ ...form, corpId })} />
      </Field>
    )}
    {form.providerType === 'DINGTALK' && (
      <>
        <Field>
          <Text strong>Robot Code</Text>
          <Input value={form.robotCode} onChange={(robotCode) => setForm({ ...form, robotCode })} />
        </Field>
        <Field>
          <Text strong>Card Template ID</Text>
          <Input value={form.cardTemplateId} onChange={(cardTemplateId) => setForm({ ...form, cardTemplateId })} />
        </Field>
      </>
    )}
    {['TELEGRAM', 'DISCORD'].includes(form.providerType) && (
      <Field>
        <Text strong>Bot 用户名</Text>
        <Input value={form.botUsername} placeholder="@orbisops_bot" onChange={(botUsername) => setForm({ ...form, botUsername })} />
      </Field>
    )}
    {form.providerType === 'SLACK' && (
      <Field>
        <Text strong>App 级凭据环境变量</Text>
        <Input aria-label="App 级凭据环境变量" value={form.secondaryCredentialEnvironmentVariable} onChange={(secondaryCredentialEnvironmentVariable) => setForm({ ...form, secondaryCredentialEnvironmentVariable })} />
      </Field>
    )}
    {form.providerType === 'WECHAT' && (
      <>
        <Field>
          <Text strong>校验凭据环境变量</Text>
          <Input aria-label="校验凭据环境变量" value={form.verificationEnvironmentVariable} onChange={(verificationEnvironmentVariable) => setForm({ ...form, verificationEnvironmentVariable })} />
        </Field>
        <Field>
          <Text strong>加密凭据环境变量</Text>
          <Input aria-label="加密凭据环境变量" value={form.encryptionEnvironmentVariable} onChange={(encryptionEnvironmentVariable) => setForm({ ...form, encryptionEnvironmentVariable })} />
        </Field>
      </>
    )}
    {['TELEGRAM', 'DISCORD', 'QQ'].includes(form.providerType) && (
      <WideField>
        <Space wrap>
          <Checkbox checked={form.requireMention} onChange={(event) => setForm({ ...form, requireMention: Boolean(event.target.checked) })}>群聊中必须 @Bot 才响应</Checkbox>
          {form.providerType === 'DISCORD' && <Checkbox checked={form.messageContentIntent} onChange={(event) => setForm({ ...form, messageContentIntent: Boolean(event.target.checked) })}>已启用 Message Content Intent</Checkbox>}
        </Space>
      </WideField>
    )}
  </>
);

const ChannelDetail: React.FC<{
  channel: OpsChannel;
  projectId: string;
  workflows: OpsAgentDefinition[];
  tab: string;
  onTabChange: (tab: string) => void;
  onOpenRun: (runId: string) => void;
}> = ({ channel, projectId, workflows, tab, onTabChange, onOpenRun }) => {
  const readinessQuery = useChannelReadinessQuery(projectId, channel.channelId, tab === 'readiness');
  const messagesQuery = useChannelMessagesQuery(projectId, channel.channelId, tab === 'messages' ? 100 : 200, tab === 'messages' || tab === 'identities');
  const identitiesQuery = useChannelIdentitiesQuery(projectId, channel.channelId, tab === 'identities');
  const usersQuery = useChannelEnabledUsersQuery(tab === 'identities');
  const membersQuery = useChannelProjectMembersQuery(projectId, tab === 'identities');
  const bindMutation = useBindChannelIdentityMutation(projectId, channel.channelId);
  const sendMutation = useSendChannelTestMutation(projectId);

  const [sendTarget, setSendTarget] = useState('');
  const [sendContent, setSendContent] = useState('OrbisOps Channel 连通性测试。');
  const [identityForm, setIdentityForm] = useState({ externalSenderId: '', platformUserId: '', status: 'ACTIVE' as 'ACTIVE' | 'DISABLED' });
  const [editingIdentity, setEditingIdentity] = useState<OpsChannelIdentity | null>(null);

  const members = (membersQuery.data || []).filter(activeMember);
  const memberIds = new Set(members.map(projectMemberUserId).filter(Boolean));
  const memberNames = new Set(members.map(projectMemberUsername).filter(Boolean));
  const eligibleUsers = (usersQuery.data || []).filter((user: AdminUserResponseDTO) =>
    (user.userId && memberIds.has(user.userId)) || memberNames.has(user.username),
  );
  const selectedUser = eligibleUsers.find((user: AdminUserResponseDTO) => user.userId === identityForm.platformUserId);

  const sendTest = async () => {
    if (!sendTarget.trim() || !sendContent.trim()) {
      Toast.warning('请输入投递目标和测试消息。');
      return;
    }
    try {
      const response = await sendMutation.mutateAsync({ channelId: channel.channelId, target: sendTarget.trim(), content: sendContent.trim() });
      if (response.code !== '0000') throw new Error(response.info || 'Provider 投递失败。');
      Toast.success('Provider 投递请求已完成。');
    } catch (error) {
      Toast.error(userFacingError(error, '发送 Channel 测试消息失败，请稍后重试。'));
    }
  };

  const bindIdentity = async () => {
    if (!identityForm.externalSenderId.trim() || !selectedUser?.userId) {
      Toast.warning('请选择已有 Project 成员，并填写外部发送者 ID。');
      return;
    }
    try {
      await bindMutation.mutateAsync({
        externalSenderId: identityForm.externalSenderId.trim(),
        platformUserId: selectedUser.userId,
        username: selectedUser.username,
        status: identityForm.status,
        expectedVersion: editingIdentity?.version,
      });
      Toast.success(editingIdentity ? '身份映射已更新。' : '身份已绑定到 Project 成员。');
      setEditingIdentity(null);
      setIdentityForm({ externalSenderId: '', platformUserId: '', status: 'ACTIVE' });
    } catch (error) {
      Toast.error(userFacingError(error, '绑定 Channel 身份失败，请稍后重试。'));
    }
  };

  return (
    <Stack>
      <DetailMeta>
        <MetaBox><Text type="tertiary">Project</Text><div><Text strong>{projectId}</Text></div></MetaBox>
        <MetaBox><Text type="tertiary">执行方式</Text><div><Text strong>{executionLabel(channel, workflows)}</Text></div></MetaBox>
        <MetaBox><Text type="tertiary">访问策略</Text><div><Text strong>{channel.accessPolicy}</Text></div></MetaBox>
        <MetaBox><Text type="tertiary">凭据</Text><div><Text strong>引用自部署环境</Text></div></MetaBox>
      </DetailMeta>
      <Tabs activeKey={tab} onChange={onTabChange} type="line">
        <TabPane tab="就绪状态" itemKey="readiness">
          <ChannelReadinessPanel readiness={readinessQuery.data} loading={readinessQuery.isFetching} onRefresh={() => void readinessQuery.refetch()} />
        </TabPane>
        <TabPane tab="消息" itemKey="messages">
          <Stack>
            <Table
              rowKey="messageId"
              dataSource={messagesQuery.data || []}
              loading={messagesQuery.isFetching}
              pagination={false}
              empty={<Text type="tertiary">当前视图暂无 Channel 消息记录。</Text>}
              columns={[
                { title: '时间', dataIndex: 'createTime', width: 170 },
                { title: '方向', dataIndex: 'direction', width: 110 },
                { title: '状态', dataIndex: 'status', width: 130 },
                { title: '外部会话', dataIndex: 'externalConversationId', width: 180 },
                { title: 'Run', dataIndex: 'runId', width: 150, render: (runId: string) => runId ? <Button theme="borderless" onClick={() => onOpenRun(runId)}>打开 Run</Button> : '-' },
              ]}
            />
            <Divider />
            <Title heading={6} style={{ margin: 0 }}>Provider 投递测试</Title>
            <Input value={sendTarget} placeholder="会话、群组或接收者标识" onChange={setSendTarget} />
            <TextArea value={sendContent} onChange={setSendContent} autosize={{ minRows: 3, maxRows: 6 }} />
            <Button icon={<IconSend />} loading={sendMutation.isPending} onClick={() => void sendTest()}>发送测试消息</Button>
          </Stack>
        </TabPane>
        <TabPane tab="身份与访问控制" itemKey="identities">
          <Stack>
            <Paragraph type="tertiary" style={{ margin: 0 }}>
              Channel 发送者只能映射到已启用、并且已经属于当前 Project 的平台用户。该映射不会创建账号，也不会绕过 Project RBAC。
            </Paragraph>
            <FormGrid>
              <Field>
                <Text strong>外部发送者 ID</Text>
                <Input disabled={Boolean(editingIdentity)} value={identityForm.externalSenderId} onChange={(externalSenderId) => setIdentityForm({ ...identityForm, externalSenderId })} />
              </Field>
              <Field>
                <Text strong>Project 成员</Text>
                <Select filter value={identityForm.platformUserId || undefined} onChange={(value) => setIdentityForm({ ...identityForm, platformUserId: String(value || '') })}>
                  {eligibleUsers.map((user: AdminUserResponseDTO) => <Option key={user.userId || user.username} value={user.userId || ''}>{user.username}</Option>)}
                </Select>
              </Field>
              <Field>
                <Text strong>状态</Text>
                <Select value={identityForm.status} onChange={(value) => setIdentityForm({ ...identityForm, status: String(value) as 'ACTIVE' | 'DISABLED' })}>
                  <Option value="ACTIVE">启用</Option>
                  <Option value="DISABLED">停用</Option>
                </Select>
              </Field>
              <Field>
                <Text strong>操作</Text>
                <Button type="primary" loading={bindMutation.isPending} onClick={() => void bindIdentity()}>{editingIdentity ? '更新映射' : '绑定身份'}</Button>
              </Field>
            </FormGrid>
            <Table
              rowKey="mappingId"
              dataSource={identitiesQuery.data || []}
              loading={identitiesQuery.isFetching}
              pagination={false}
              empty={<Text type="tertiary">当前 Channel 尚未绑定发送者身份。</Text>}
              columns={[
                { title: '外部发送者', dataIndex: 'externalSenderId', width: 220 },
                { title: '平台用户', dataIndex: 'username', width: 180 },
                { title: '状态', dataIndex: 'status', width: 110 },
                {
                  title: '操作',
                  width: 120,
                  render: (_: unknown, identity: OpsChannelIdentity) => <Button size="small" onClick={() => {
                    setEditingIdentity(identity);
                    setIdentityForm({ externalSenderId: identity.externalSenderId, platformUserId: identity.platformUserId, status: identity.status });
                  }}>编辑</Button>,
                },
              ]}
            />
          </Stack>
        </TabPane>
      </Tabs>
    </Stack>
  );
};

const OutboxSection: React.FC<{
  projectId: string;
  items: Array<{ id: number; channelId: string; target: string; messageType: string; referenceId: string; status: string; retryCount: number; lastError?: string }>;
  loading: boolean;
  onRefresh: () => void;
  onProcess: () => Promise<void>;
  onAction: (id: number, action: 'requeue' | 'cancel') => Promise<void>;
}> = ({ projectId, items, loading, onRefresh, onProcess, onAction }) => (
  <Card title="通知 Outbox">
    <Space vertical align="start" style={{ width: '100%' }}>
      <Paragraph type="tertiary" style={{ margin: 0 }}>
        失败或结果不确定的出站投递会被显式保留。OrbisOps 不会把未知 Provider 结果误判为成功，也不会自动重放可能产生副作用的消息。
      </Paragraph>
      <Space wrap>
        <Button size="small" onClick={onRefresh} disabled={!projectId} loading={loading}>刷新 Outbox</Button>
        <Button size="small" onClick={() => void onProcess()} disabled={!projectId} loading={loading}>处理待发送项</Button>
      </Space>
      <Table
        rowKey="id"
        dataSource={items}
        loading={loading}
        pagination={false}
        empty={<Text type="tertiary">通知 Outbox 当前为空。</Text>}
        scroll={{ x: 1000 }}
        columns={[
          { title: 'Channel', dataIndex: 'channelId', width: 160 },
          { title: '目标', dataIndex: 'target', width: 180 },
          { title: '类型', dataIndex: 'messageType', width: 160 },
          { title: '状态', dataIndex: 'status', width: 130 },
          { title: '重试次数', dataIndex: 'retryCount', width: 90 },
          { title: '错误', dataIndex: 'lastError', width: 220 },
          {
            title: '恢复操作',
            width: 180,
            render: (_: unknown, item: { id: number; status: string }) => ['FAILED', 'DEAD_LETTER'].includes(item.status)
              ? <Space><Button size="small" onClick={() => void onAction(item.id, 'requeue')}>重新入队</Button><Button size="small" type="danger" theme="borderless" onClick={() => void onAction(item.id, 'cancel')}>取消</Button></Space>
              : '-',
          },
        ]}
      />
    </Space>
  </Card>
);
