import React, { useState } from 'react';
import styled from 'styled-components';
import { Button, Modal, Select, Space, Switch, Table, Tag, TextArea, Toast, Typography } from '@douyinfe/semi-ui';
import { IconRefresh } from '@douyinfe/semi-icons';

import {
  JsonBlock,
  OpsAdvancedPreview,
  OpsCapabilityFlow,
  OpsEmptyState,
  OpsPageHeader,
  OpsPageShell,
  OpsResponsiveGrid,
  OpsSectionCard,
  TableScroll,
} from '../components/ops-layout';
import {
  useRebuildToolCatalogMutation,
  useReviewToolPolicyMutation,
  useSelectToolRouteMutation,
  useToolRoutingOverviewQuery,
} from '../features/tool-routing/api/tool-routing-queries';
import { useProjectScope } from '../hooks/use-project-scope';
import { theme } from '../styles/theme';
import { userFacingError } from '../utils/user-facing-error';

const { Option } = Select;
const { Text } = Typography;

const Toolbar = styled.div`
  display: flex;
  flex-wrap: wrap;
  gap: ${theme.spacing.base};
  align-items: center;
  margin-bottom: ${theme.spacing.base};

  .semi-select,
  .semi-input-wrapper {
    min-width: 220px;
  }
`;

const asText = (value: unknown, fallback = '-') => {
  if (value === undefined || value === null || value === '') return fallback;
  return String(value);
};

const candidateToolsLabel = (value: unknown) => {
  if (!Array.isArray(value)) return asText(value);
  const labels = value.map((item) => {
    if (typeof item === 'string') return item;
    if (!item || typeof item !== 'object') return String(item);
    const tool = item as Record<string, unknown>;
    const name = tool.toolName || tool.tool_name || tool.remoteToolName || tool.remote_tool_name || tool.toolId || tool.tool_id;
    const mcp = tool.mcpName || tool.mcp_name || tool.mcpId || tool.mcp_id;
    if (name && mcp) return `${String(name)}（${String(mcp)}）`;
    if (name || mcp) return String(name || mcp);
    return JSON.stringify(tool);
  }).filter(Boolean);
  return labels.length ? labels.join('、') : '-';
};

const riskColor = (risk?: string) => {
  if (risk === 'CRITICAL') return 'red';
  if (risk === 'HIGH') return 'orange';
  if (risk === 'MEDIUM') return 'yellow';
  return 'green';
};

const statusColor = (status?: string) => {
  if (status === 'SUCCEEDED' || status === 'ENABLED' || status === 'ACTIVE') return 'green';
  if (status === 'FAILED' || status === 'DENIED' || status === 'REJECTED' || status === 'DISABLED') return 'red';
  if (status === 'RUNNING') return 'blue';
  if (status === 'PENDING_REVIEW') return 'yellow';
  if (status === 'STALE' || status === 'EXPIRED') return 'orange';
  return 'grey';
};

const statusLabel = (status?: string) => {
  switch (String(status || '').toUpperCase()) {
    case 'SUCCEEDED':
      return '调用成功';
    case 'FAILED':
      return '调用失败';
    case 'DENIED':
    case 'REJECTED':
      return '已拒绝';
    case 'BLOCKED':
      return '已阻断';
    case 'ACTIVE':
    case 'ENABLED':
      return '已启用';
    case 'DISABLED':
      return '已停用';
    case 'PENDING_REVIEW':
      return '待审核';
    case 'STALE':
      return '已过期';
    case 'EXPIRED':
      return '已失效';
    default:
      return asText(status);
  }
};

const effectLabel = (effect?: string) => {
  switch (String(effect || '').toUpperCase()) {
    case 'NO_EFFECT':
      return '无副作用';
    case 'READ_EXTERNAL_STATE':
      return '读取外部状态';
    case 'VALIDATE_ONLY':
      return '只验证';
    case 'DRY_RUN':
      return '干跑';
    case 'MUTATE_EPHEMERAL':
      return '修改临时资源';
    case 'MUTATE_TEST_RESOURCE':
      return '修改测试资源';
    case 'MUTATE_TARGET_RESOURCE':
      return '修改目标资源';
    case 'EXECUTE_EXTERNAL_ACTION':
      return '执行外部动作';
    case 'DELETE_TARGET_RESOURCE':
      return '删除目标资源';
    case 'UNKNOWN':
      return '未知效果';
    default:
      return asText(effect);
  }
};

const routingRecordKind = (record: Record<string, any> | null) => {
  if (!record) return '';
  if (record.policyId || record.policy_id) return '工具策略';
  if (record.snapshotId || record.snapshot_id) return '工具画像';
  if (record.callId || record.call_id) return '工具调用';
  if (record.decisionId || record.decision_id) return '路由决策';
  return '记录';
};

const summaryTools = (summary: Record<string, any> | null) => {
  const value = summary?.tools || summary?.summaryJson?.tools || summary?.summary_json?.tools;
  return Array.isArray(value) ? value : [];
};

const runtimeCatalogTools = (summary: Record<string, any> | null) => summaryTools(summary).flatMap((tool: Record<string, any>) => {
  const runtimeTools = tool.runtimeExecutableTools || tool.runtime_executable_tools;
  if (!Array.isArray(runtimeTools)) return [];
  return runtimeTools.map((runtimeTool) => ({
    ...runtimeTool,
    mcpId: runtimeTool.mcpId || runtimeTool.mcp_id || tool.toolId || tool.tool_id || tool.mcpId || tool.mcp_id,
  }));
});

type PolicyReviewDraft = {
  disclosureTier: 'CORE' | 'EXTENSION';
  effectType: string;
  effectScope: string;
  mutability: string;
  riskLevel: string;
  readOnly: boolean;
  investigateAllowed: boolean;
  prepareAllowed: boolean;
  landAllowed: boolean;
  requiresApprovedPackage: boolean;
  requiresHumanApproval: boolean;
  requiresDryRun: boolean;
  requiresRollbackPlan: boolean;
  reason: string;
};

const readLikeTool = (toolName: string) => /(^|_)(get|list|read|search|query|find|scan|show|describe|explain|health|status|inspect|logs?)(_|$)/i.test(toolName);

const reviewDefaults = (record: Record<string, any>): PolicyReviewDraft => {
  const toolName = asText(record.toolName || record.tool_name, '');
  const inferredReadOnly = Boolean(record.readOnly || record.read_only) || readLikeTool(toolName);
  return {
    disclosureTier: asText(record.disclosureTier || record.disclosure_tier || record.metadata?.disclosureTier, 'EXTENSION') === 'CORE' ? 'CORE' : 'EXTENSION',
    effectType: inferredReadOnly ? 'READ_EXTERNAL_STATE' : asText(record.effectType || record.effect_type, 'UNKNOWN'),
    effectScope: inferredReadOnly ? 'TARGET_RESOURCE_READ' : asText(record.effectScope || record.effect_scope, 'UNKNOWN'),
    mutability: inferredReadOnly ? 'READ_ONLY' : asText(record.mutability, 'UNKNOWN'),
    riskLevel: inferredReadOnly ? 'LOW' : asText(record.riskLevel || record.risk_level, 'HIGH'),
    readOnly: inferredReadOnly,
    investigateAllowed: inferredReadOnly,
    prepareAllowed: inferredReadOnly,
    landAllowed: false,
    requiresApprovedPackage: !inferredReadOnly,
    requiresHumanApproval: !inferredReadOnly,
    requiresDryRun: !inferredReadOnly,
    requiresRollbackPlan: !inferredReadOnly,
    reason: inferredReadOnly ? '确认该工具仅查询外部状态，不修改目标资源。' : '',
  };
};

export const ToolRoutingObservabilityPage: React.FC = () => {
  const scope = useProjectScope();
  const projectId = scope.projectId;
  const projects = scope.projects;
  const selectedProject = scope.selectedProject;
  const overviewQuery = useToolRoutingOverviewQuery(projectId);
  const rebuildMutation = useRebuildToolCatalogMutation(projectId);
  const selectRouteMutation = useSelectToolRouteMutation(projectId);
  const reviewPolicyMutation = useReviewToolPolicyMutation(projectId);
  const summary = overviewQuery.data?.summary || null;
  const remoteCatalogs = overviewQuery.data?.remoteCatalogs || [];
  const decisions = overviewQuery.data?.decisions || [];
  const calls = overviewQuery.data?.calls || [];
  const snapshots = overviewQuery.data?.snapshots || [];
  const policies = overviewQuery.data?.policies || [];
  const activations = overviewQuery.data?.activations || [];
  const [selected, setSelected] = useState<Record<string, any> | null>(null);
  const [policyReviewTarget, setPolicyReviewTarget] = useState<Record<string, any> | null>(null);
  const [policyReviewDraft, setPolicyReviewDraft] = useState<PolicyReviewDraft | null>(null);
  const [routeForm, setRouteForm] = useState({
    agentId: '',
    nodeId: '',
    capabilityType: 'metric_query',
    userRequest: '',
  });

  const rebuild = async () => {
    if (!projectId) return;
    try {
      await rebuildMutation.mutateAsync();
      Toast.success('工具目录摘要已重建');
    } catch (error) {
      Toast.error(userFacingError(error, '重建工具目录摘要失败，请稍后重试。'));
    }
  };

  const selectRoute = async () => {
    if (!projectId) return;
    try {
      const result = await selectRouteMutation.mutateAsync({
        ...routeForm,
        capability: routeForm.capabilityType,
      });
      setSelected(result || null);
      Toast.success('已生成工具候选');
    } catch (error) {
      Toast.error(userFacingError(error, '工具路由失败，请稍后重试。'));
    }
  };

  const reviewPolicy = async (policyId: string, action: 'reject' | 'disable') => {
    if (!projectId || !policyId) return;
    try {
      await reviewPolicyMutation.mutateAsync({ policyId, action, payload: { reason: `${action} by admin` } });
      Toast.success(action === 'reject' ? '策略已驳回' : '策略已停用');
    } catch (error) {
      Toast.error(userFacingError(error, '工具策略审核失败，请稍后重试。'));
    }
  };

  const openPolicyReview = (record: Record<string, any>) => {
    setPolicyReviewTarget(record);
    setPolicyReviewDraft(reviewDefaults(record));
  };

  const submitPolicyReview = async () => {
    if (!projectId || !policyReviewTarget || !policyReviewDraft) return;
    const policyId = asText(policyReviewTarget.policyId || policyReviewTarget.policy_id, '');
    const toolName = asText(policyReviewTarget.toolName || policyReviewTarget.tool_name, '');
    if (!policyId || !toolName || ['UNKNOWN'].includes(policyReviewDraft.effectType)
      || ['UNKNOWN'].includes(policyReviewDraft.effectScope)
      || ['UNKNOWN'].includes(policyReviewDraft.mutability)) {
      Toast.error('请先明确工具效果、作用范围和可变性');
      return;
    }
    try {
      const allowedAction = toolName.replace(/[^a-zA-Z0-9]+/g, '_').toUpperCase();
      await reviewPolicyMutation.mutateAsync({
        policyId,
        action: 'approve',
        payload: {
          ...policyReviewDraft,
          metadata: {
            ...(policyReviewTarget.metadata || {}),
            disclosureTier: policyReviewDraft.disclosureTier,
          },
          capability: policyReviewDraft.readOnly ? 'READ_ONLY' : 'MUTATING',
          allowedActions: [allowedAction],
          argumentPolicy: policyReviewDraft.readOnly ? { maxLimit: 500 } : {},
        },
      });
      Toast.success('策略已人工审核并发布');
      setPolicyReviewTarget(null);
      setPolicyReviewDraft(null);
    } catch (error) {
      Toast.error(userFacingError(error, '工具策略审核失败，请稍后重试。'));
    }
  };

  const decisionColumns = [
    {
      title: 'Decision',
      render: (_: unknown, record: Record<string, any>) => (
        <Space vertical align="start" spacing={2}>
          <Text strong>{asText(record.decisionId || record.decision_id)}</Text>
          <Text type="tertiary">{asText(record.capabilityType || record.capability_type || record.capability)}</Text>
        </Space>
      ),
    },
    {
      title: 'Agent / Node',
      width: 200,
      render: (_: unknown, record: Record<string, any>) => (
        <Space vertical align="start" spacing={2}>
          <Text>{asText(record.agentId || record.agent_id)}</Text>
          <Text type="tertiary">{asText(record.nodeId || record.node_id)}</Text>
        </Space>
      ),
    },
    {
      title: '候选工具',
      render: (_: unknown, record: Record<string, any>) => candidateToolsLabel(record.selectedTools || record.selected_tools || record.toolIds || record.tool_ids),
    },
    {
      title: '原因',
      render: (_: unknown, record: Record<string, any>) => asText(record.reason || record.routingReason || record.routing_reason),
    },
    {
      title: '操作',
      width: 90,
      fixed: 'right' as const,
      render: (_: unknown, record: Record<string, any>) => (
        <Button size="small" theme="borderless" onClick={() => setSelected(record)}>查看</Button>
      ),
    },
  ];

  const callColumns = [
    {
      title: 'MCP 调用',
      render: (_: unknown, record: Record<string, any>) => (
        <Space vertical align="start" spacing={2}>
          <Text strong>{asText(record.callId || record.call_id)}</Text>
          <Text type="tertiary">{asText(record.toolName || record.tool_name || record.toolId || record.tool_id)}</Text>
        </Space>
      ),
    },
    {
      title: 'MCP',
      width: 210,
      render: (_: unknown, record: Record<string, any>) => asText(record.mcpId || record.mcp_id || record.mcpName || record.mcp_name),
    },
    {
      title: '风险',
      width: 110,
      render: (_: unknown, record: Record<string, any>) => <Tag color={riskColor(asText(record.riskLevel || record.risk_level))}>{asText(record.riskLevel || record.risk_level)}</Tag>,
    },
    {
      title: '状态',
      width: 110,
      render: (_: unknown, record: Record<string, any>) => <Tag color={statusColor(asText(record.status))}>{statusLabel(asText(record.status))}</Tag>,
    },
    {
      title: '耗时',
      width: 110,
      render: (_: unknown, record: Record<string, any>) => `${asText(record.durationMs || record.duration_ms, '0')} ms`,
    },
    {
      title: '操作',
      width: 90,
      fixed: 'right' as const,
      render: (_: unknown, record: Record<string, any>) => (
        <Button size="small" theme="borderless" onClick={() => setSelected(record)}>查看</Button>
      ),
    },
  ];

  const snapshotColumns = [
    {
      title: '工具快照',
      render: (_: unknown, record: Record<string, any>) => (
        <Space vertical align="start" spacing={2}>
          <Text strong>{asText(record.toolName || record.tool_name)}</Text>
          <Text type="tertiary">{asText(record.snapshotId || record.snapshot_id)}</Text>
        </Space>
      ),
    },
    {
      title: 'MCP',
      width: 180,
      render: (_: unknown, record: Record<string, any>) => asText(record.mcpId || record.mcp_id),
    },
    {
      title: 'Schema Hash',
      render: (_: unknown, record: Record<string, any>) => asText(record.schemaHash || record.schema_hash),
    },
    {
      title: '元数据',
      width: 170,
      render: (_: unknown, record: Record<string, any>) => (
        <Tag color={record.metadataComplete || record.metadata_complete ? 'green' : 'orange'}>
          {record.metadataComplete || record.metadata_complete ? '远端描述完整' : '远端描述不完整'}
        </Tag>
      ),
    },
    {
      title: '操作',
      width: 90,
      fixed: 'right' as const,
      render: (_: unknown, record: Record<string, any>) => (
        <Button size="small" theme="borderless" onClick={() => setSelected(record)}>查看</Button>
      ),
    },
  ];

  const policyColumns = [
    {
      title: '策略',
      render: (_: unknown, record: Record<string, any>) => (
        <Space vertical align="start" spacing={2}>
          <Text strong>{asText(record.toolName || record.tool_name)}</Text>
          <Text type="tertiary">{asText(record.policyId || record.policy_id)}</Text>
        </Space>
      ),
    },
    {
      title: '状态',
      width: 150,
      render: (_: unknown, record: Record<string, any>) => (
        <Space vertical align="start" spacing={2}>
          <Tag color={statusColor(asText(record.status))}>{statusLabel(asText(record.status))}</Tag>
          <Text type="tertiary">{asText(record.reviewStatus || record.review_status)}</Text>
        </Space>
      ),
    },
    {
      title: '效果 / 风险',
      width: 220,
      render: (_: unknown, record: Record<string, any>) => (
        <Space vertical align="start" spacing={2}>
          <Text>{effectLabel(record.effectType || record.effect_type)}</Text>
          <Tag color={riskColor(asText(record.riskLevel || record.risk_level))}>{asText(record.riskLevel || record.risk_level)}</Tag>
        </Space>
      ),
    },
    {
      title: '披露方式',
      width: 130,
      render: (_: unknown, record: Record<string, any>) => {
        const tier = asText(record.disclosureTier || record.disclosure_tier || record.metadata?.disclosureTier, 'EXTENSION');
        return <Tag color={tier === 'CORE' ? 'green' : 'blue'}>{tier === 'CORE' ? '核心直达' : '按需启用'}</Tag>;
      },
    },
    {
      title: '阶段',
      width: 190,
      render: (_: unknown, record: Record<string, any>) => (
        <Space spacing={4} wrap>
          {record.investigateAllowed || record.investigate_allowed ? <Tag color="green">审核前可读</Tag> : null}
          {record.prepareAllowed || record.prepare_allowed ? <Tag color="blue">验证/干跑可用</Tag> : null}
          {record.landAllowed || record.land_allowed ? <Tag color="red">审批后可落地</Tag> : null}
        </Space>
      ),
    },
    {
      title: '操作',
      width: 220,
      fixed: 'right' as const,
      render: (_: unknown, record: Record<string, any>) => {
        const policyId = asText(record.policyId || record.policy_id, '');
        return (
          <Space spacing={4} wrap>
            <Button size="small" theme="borderless" onClick={() => setSelected(record)}>查看</Button>
            <Button size="small" disabled={!policyId || asText(record.status, '').toUpperCase() === 'STALE'} onClick={() => openPolicyReview(record)}>审核并发布</Button>
            <Button size="small" type="danger" theme="borderless" disabled={!policyId} onClick={() => reviewPolicy(policyId, 'reject')}>驳回</Button>
            <Button size="small" type="danger" theme="borderless" disabled={!policyId} onClick={() => reviewPolicy(policyId, 'disable')}>停用</Button>
          </Space>
        );
      },
    },
  ];

  const activationColumns = [
    {
      title: 'Work Session',
      render: (_: unknown, record: Record<string, any>) => (
        <Space vertical align="start" spacing={2}>
          <Text strong>{asText(record.runId || record.run_id)}</Text>
          <Text type="tertiary">{asText(record.sessionId || record.session_id)}</Text>
        </Space>
      ),
    },
    {
      title: '按需启用的工具',
      render: (_: unknown, record: Record<string, any>) => (
        <Space vertical align="start" spacing={2}>
          <Text>{asText(record.toolName || record.tool_name)}</Text>
          <Text type="tertiary">{asText(record.mcpId || record.mcp_id)}</Text>
        </Space>
      ),
    },
    {
      title: '披露方式', width: 120,
      render: (_: unknown, record: Record<string, any>) => <Tag color="blue">{asText(record.disclosureTier || record.disclosure_tier) === 'CORE' ? '核心直达' : '按需启用'}</Tag>,
    },
    {
      title: '状态', width: 110,
      render: (_: unknown, record: Record<string, any>) => <Tag color={statusColor(asText(record.status))}>{statusLabel(asText(record.status))}</Tag>,
    },
    { title: '失效时间', width: 190, render: (_: unknown, record: Record<string, any>) => asText(record.expiresAt || record.expires_at) },
  ];

  return (
    <OpsPageShell selectedKey="tool-routing-observability">
      <OpsPageHeader
        title="工具权限与调用审计"
        description="这里管理 Agent 能不能用某个外部工具：先看项目授权，再看人工审核过的 Tool Policy，最后记录每次候选选择和实际调用。"
        primaryAction={<Button theme="solid" onClick={rebuild} loading={rebuildMutation.isPending}>重建目录摘要</Button>}
        extra={<Button icon={<IconRefresh />} onClick={() => overviewQuery.refetch()} loading={overviewQuery.isFetching}>刷新</Button>}
      />

      <OpsSectionCard title="项目工具目录">
        <Toolbar>
          <Select placeholder="选择项目" value={projectId} onChange={(value) => scope.selectProject(String(value || ''))}>
            {projects.map((project) => (
              <Option value={project.projectId} key={project.projectId}>{project.name || project.projectId}</Option>
            ))}
          </Select>
          <Text type="tertiary">{selectedProject?.description || 'Agent 只能使用当前项目生成或启用的工具。'}</Text>
        </Toolbar>
        <OpsCapabilityFlow
          items={[
            { title: '项目授权工具', description: '来自项目 MCP，而不是全局模板。' },
            { title: '人工审核策略', description: '未审核或过期工具默认不可用。' },
            { title: '候选选择', description: '根据用户意图只挑少量候选工具。' },
            { title: '按需加载参数', description: '只对候选工具读取 schema。' },
            { title: '调用审计', description: '每次允许、阻断、失败都能追踪。' },
          ]}
        />
      </OpsSectionCard>

      <OpsSectionCard title="远端工具目录同步">
        <Text type="tertiary">首次接入保存完整目录，之后每日检查。检查失败时保留上次可用目录；项目撤权仍立即生效。更换接入配置后，旧配置的检查记录仍会保留。</Text>
        {remoteCatalogs.length === 0 ? (
          <OpsEmptyState title="尚未读取远端目录" description="接入工具或实际使用后，这里会显示检查时间和结果。" />
        ) : (
          <TableScroll><Table dataSource={remoteCatalogs} rowKey="connectionId" pagination={{ pageSize: 6 }} columns={[
            { title: 'MCP 接入', dataIndex: 'serverId' },
            { title: '配置记录', dataIndex: 'connectionId', render: (v) => String(v || '').slice(0, 12) },
            { title: '目录版本', dataIndex: 'generation' },
            { title: '远端工具数', dataIndex: 'toolCount' },
            { title: '最后检查', dataIndex: 'checkedAt', render: (v) => v ? new Date(String(v)).toLocaleString() : '-' },
            { title: '最后成功', dataIndex: 'succeededAt', render: (v) => v ? new Date(String(v)).toLocaleString() : '-' },
            { title: '结果', render: (_, row) => row?.errorCode
              ? <Tag color="orange">{row.generation > 0 ? '检查失败，保留旧目录' : '检查失败，尚无可用目录'}</Tag>
              : <Tag color="green">目录可用</Tag> },
          ]} /></TableScroll>
        )}
      </OpsSectionCard>

      <OpsResponsiveGrid $min="320px">
        <OpsSectionCard title="目录摘要">
          {summary ? (
            <Space vertical align="start">
              <Text>目录版本：{asText(summary.catalogVersion || summary.catalog_version)}</Text>
              <Text>工具数量：{asText(summary.toolCount || summary.tool_count, '0')}</Text>
              <Text>审核后可用的远端工具：{runtimeCatalogTools(summary).length}</Text>
              <Text>更新时间：{asText(summary.updateTime || summary.update_time || summary.createTime || summary.create_time)}</Text>
              <Text type="tertiary">目录摘要只是帮助 Agent 先挑候选工具；真正能不能调用，还要看项目授权和人工审核后的策略。</Text>
              <Space wrap>
                {runtimeCatalogTools(summary).slice(0, 12).map((tool: Record<string, any>) => (
                  <Tag
                    key={`${asText(tool.mcpId || tool.mcp_id)}:${asText(tool.toolName || tool.tool_name)}`}
                    color={tool.readOnly || tool.read_only ? 'green' : riskColor(asText(tool.riskLevel || tool.risk_level))}
                  >
                    {asText(tool.toolName || tool.tool_name || tool.toolId || tool.tool_id)}
                    {tool.readOnly || tool.read_only ? ' · 只读' : ` · ${asText(tool.riskLevel || tool.risk_level, '需审核')}`}
                    {asText(tool.disclosureTier || tool.disclosure_tier, 'EXTENSION') === 'CORE' ? ' · 核心直达' : ' · 按需启用'}
                  </Tag>
                ))}
              </Space>
              <OpsAdvancedPreview title="调试用目录 JSON" description="目录摘要用于模型选择工具，不包含完整 schema。普通审核不需要阅读这段。">
                <JsonBlock>{JSON.stringify(summary, null, 2)}</JsonBlock>
              </OpsAdvancedPreview>
            </Space>
          ) : (
            <OpsEmptyState title="暂无目录摘要" description="选择项目后可以点击重建目录摘要。" />
          )}
        </OpsSectionCard>

        <OpsSectionCard title="手动验证：一句话会匹配哪些工具">
          <Space vertical align="start" style={{ width: '100%' }}>
            <Text type="tertiary">用于验证“如果用户这样问，Agent 会看到哪些候选工具”。这里只做候选选择，不会真正调用外部工具。</Text>
            <Text strong>用户想做什么</Text>
            <Select value={routeForm.capabilityType} onChange={(value) => setRouteForm((current) => ({ ...current, capabilityType: String(value || '') }))}>
              <Option value="metric_query">指标查询</Option>
              <Option value="log_query">日志查询</Option>
              <Option value="sql_query">只读 SQL 查询</Option>
              <Option value="cache_query">缓存只读查询</Option>
              <Option value="notification">通知</Option>
            </Select>
            <Text strong>用户原话</Text>
            <TextArea placeholder="例如：帮我查当前服务最近 15 分钟的 5xx 指标和错误日志" autosize rows={3} value={routeForm.userRequest} onChange={(value) => setRouteForm((current) => ({ ...current, userRequest: value }))} />
            <Button theme="solid" onClick={selectRoute}>选择候选工具</Button>
          </Space>
        </OpsSectionCard>

        <OpsSectionCard title="参数说明如何产生">
          <Space vertical align="start" style={{ width: '100%' }}>
            <Text>Agent 首次需要某个候选工具时，平台会从真实 MCP Server 获取名称、说明和 input schema，并自动生成待审核画像。</Text>
            <Text type="tertiary">管理员不需要填写 toolId，也不能用手工 JSON 伪造 schema。发现后的真实参数结构会出现在下方“工具快照”，审核通过后才可用于业务调用。</Text>
          </Space>
        </OpsSectionCard>
      </OpsResponsiveGrid>

      <OpsSectionCard title="工具快照">
        {snapshots.length === 0 && !overviewQuery.isLoading ? (
          <OpsEmptyState title="暂无工具快照" description="Hydrate schema 后会保存 toolName、inputSchema、schemaHash 和元数据完整性。" />
        ) : (
          <TableScroll>
            <Table columns={snapshotColumns} dataSource={snapshots} loading={overviewQuery.isLoading} rowKey={(record) => asText(record?.snapshotId || record?.snapshot_id || record?.id)} pagination={{ pageSize: 6 }} />
          </TableScroll>
        )}
      </OpsSectionCard>

      <OpsSectionCard title="工具策略审核">
        {policies.length === 0 && !overviewQuery.isLoading ? (
          <OpsEmptyState title="暂无工具策略" description="系统发现工具后只会生成待审核建议，业务 Agent 只能调用 ACTIVE 且 HUMAN_REVIEWED 的策略。" />
        ) : (
          <TableScroll>
            <Table columns={policyColumns} dataSource={policies} loading={overviewQuery.isLoading} rowKey={(record) => asText(record?.policyId || record?.policy_id || record?.id)} pagination={{ pageSize: 6 }} />
          </TableScroll>
        )}
      </OpsSectionCard>

      <OpsSectionCard title="路由决策">
        {decisions.length === 0 && !overviewQuery.isLoading ? (
          <OpsEmptyState title="暂无路由决策" description="Agent 或手动验证触发工具选择后会记录。" />
        ) : (
          <TableScroll>
            <Table columns={decisionColumns} dataSource={decisions} loading={overviewQuery.isLoading} rowKey={(record) => asText(record?.decisionId || record?.decision_id || record?.id)} pagination={{ pageSize: 8 }} />
          </TableScroll>
        )}
      </OpsSectionCard>

      <OpsSectionCard title="MCP 调用记录">
        {calls.length === 0 && !overviewQuery.isLoading ? (
          <OpsEmptyState title="暂无 MCP 调用" description="工具执行后会记录脱敏输入、输出摘要、风险等级和耗时。" />
        ) : (
          <TableScroll>
            <Table columns={callColumns} dataSource={calls} loading={overviewQuery.isLoading} rowKey={(record) => asText(record?.callId || record?.call_id || record?.id)} pagination={{ pageSize: 8 }} />
          </TableScroll>
        )}
      </OpsSectionCard>

      <OpsSectionCard title="按 Work Session 启用记录">
        {activations.length === 0 && !overviewQuery.isLoading ? (
          <OpsEmptyState title="暂无按需启用记录" description="扩展工具只有在某次 Work Session 明确需要时才会加载完整参数；启用只对该次 run 生效并自动过期。" />
        ) : (
          <TableScroll>
            <Table columns={activationColumns} dataSource={activations} loading={overviewQuery.isLoading}
              rowKey={(record) => asText(record?.activationId || record?.activation_id || record?.id)} pagination={{ pageSize: 8 }} />
          </TableScroll>
        )}
      </OpsSectionCard>

      <Modal
        title={`记录详情 · ${routingRecordKind(selected) || '工具治理'}`}
        visible={Boolean(selected)}
        onCancel={() => setSelected(null)}
        footer={null}
        width={760}
      >
        {selected ? (
          <div style={{ maxHeight: '72vh', overflowY: 'auto', paddingRight: 4 }}>
          <Space vertical align="start" spacing="medium" style={{ width: '100%' }}>
            <Space wrap>
              <Tag color="blue">{routingRecordKind(selected)}</Tag>
              {(selected.status || selected.reviewStatus || selected.review_status) && (
                <Tag color={statusColor(asText(selected.status || selected.reviewStatus || selected.review_status))}>
                  {statusLabel(asText(selected.status || selected.reviewStatus || selected.review_status))}
                </Tag>
              )}
              {(selected.riskLevel || selected.risk_level) && (
                <Tag color={riskColor(asText(selected.riskLevel || selected.risk_level))}>
                  {asText(selected.riskLevel || selected.risk_level)}
                </Tag>
              )}
            </Space>
            <Text type="tertiary">
              这条记录说明某个工具为什么被选中、为什么被阻断，或当前策略是否已人工审核。远端 MCP 描述只用于识别能力，不会自动授予权限；业务调用只认平台中已人工审核且仍匹配当前 schema 的策略。
            </Text>
            <OpsResponsiveGrid $min="260px">
              <OpsSectionCard title="工具">
                <Text>{asText(selected.toolName || selected.tool_name || selected.remoteToolName || selected.remote_tool_name || selected.toolId || selected.tool_id)}</Text>
              </OpsSectionCard>
              <OpsSectionCard title="为什么">
                <Text>{asText(selected.reason || selected.routingReason || selected.routing_reason || selected.reasonCode || selected.reason_code)}</Text>
              </OpsSectionCard>
              <OpsSectionCard title="项目 / Agent">
                <Text>{asText(selected.projectId || selected.project_id)} / {asText(selected.agentId || selected.agent_id)}</Text>
              </OpsSectionCard>
            </OpsResponsiveGrid>
            <OpsAdvancedPreview title="审计原始字段" description="排查时使用，普通审核不需要先看 JSON。">
              <JsonBlock>{JSON.stringify(selected, null, 2)}</JsonBlock>
            </OpsAdvancedPreview>
          </Space>
          </div>
        ) : null}
      </Modal>

      <Modal
        title={`审核工具策略 · ${asText(policyReviewTarget?.toolName || policyReviewTarget?.tool_name, '')}`}
        visible={Boolean(policyReviewTarget && policyReviewDraft)}
        onCancel={() => { setPolicyReviewTarget(null); setPolicyReviewDraft(null); }}
        onOk={submitPolicyReview}
        confirmLoading={reviewPolicyMutation.isPending}
        okText="确认发布"
        cancelText="取消"
        width={720}
      >
        {policyReviewDraft ? (
          <Space vertical align="start" spacing="medium" style={{ width: '100%' }}>
            <Text type="tertiary">系统已绑定项目、工具和当前 schemaHash。你只需要确认这个工具会产生什么效果，以及它在哪些场景可用。</Text>
            <OpsResponsiveGrid $min="210px">
              <Space vertical align="start">
                <Text strong>模型何时看到完整参数</Text>
                <Select style={{ width: '100%' }} value={policyReviewDraft.disclosureTier} onChange={(value) => setPolicyReviewDraft((current) => current ? ({ ...current, disclosureTier: value === 'CORE' ? 'CORE' : 'EXTENSION' }) : current)}>
                  <Option value="EXTENSION">按需启用（推荐）</Option>
                  <Option value="CORE">核心直达（仅低风险只读）</Option>
                </Select>
                <Text type="tertiary">扩展工具先显示一行目录，当前 Work Session 真正需要时才加载完整 schema。</Text>
              </Space>
              <Space vertical align="start">
                <Text strong>工具效果</Text>
                <Select style={{ width: '100%' }} value={policyReviewDraft.effectType} onChange={(value) => setPolicyReviewDraft((current) => current ? ({ ...current, effectType: String(value || '') }) : current)}>
                  <Option value="NO_EFFECT">无副作用</Option>
                  <Option value="READ_EXTERNAL_STATE">读取外部状态</Option>
                  <Option value="VALIDATE_ONLY">只验证</Option>
                  <Option value="DRY_RUN">干跑</Option>
                  <Option value="MUTATE_EPHEMERAL">修改临时资源</Option>
                  <Option value="MUTATE_TEST_RESOURCE">修改测试资源</Option>
                  <Option value="MUTATE_TARGET_RESOURCE">修改目标资源</Option>
                  <Option value="EXECUTE_EXTERNAL_ACTION">执行外部动作</Option>
                  <Option value="DELETE_TARGET_RESOURCE">删除目标资源</Option>
                </Select>
              </Space>
              <Space vertical align="start">
                <Text strong>作用范围</Text>
                <Select style={{ width: '100%' }} value={policyReviewDraft.effectScope} onChange={(value) => setPolicyReviewDraft((current) => current ? ({ ...current, effectScope: String(value || '') }) : current)}>
                  <Option value="PLATFORM_INTERNAL">平台内部</Option>
                  <Option value="TARGET_RESOURCE_READ">目标资源只读</Option>
                  <Option value="VALIDATION_ENVIRONMENT">验证环境</Option>
                  <Option value="TEMPORARY_RESOURCE">临时资源</Option>
                  <Option value="TEST_ENVIRONMENT">测试环境</Option>
                  <Option value="STAGING">预发环境</Option>
                  <Option value="PRODUCTION">生产环境</Option>
                  <Option value="TARGET_RESOURCE_WRITE">目标资源写入</Option>
                </Select>
              </Space>
              <Space vertical align="start">
                <Text strong>可变性</Text>
                <Select style={{ width: '100%' }} value={policyReviewDraft.mutability} onChange={(value) => setPolicyReviewDraft((current) => current ? ({ ...current, mutability: String(value || '') }) : current)}>
                  <Option value="READ_ONLY">只读</Option>
                  <Option value="VALIDATION_ONLY">仅验证</Option>
                  <Option value="DRY_RUN_ONLY">仅干跑</Option>
                  <Option value="TEST_MUTATING">修改测试资源</Option>
                  <Option value="PROD_MUTATING">修改生产资源</Option>
                  <Option value="DESTRUCTIVE">破坏性操作</Option>
                </Select>
              </Space>
              <Space vertical align="start">
                <Text strong>风险等级</Text>
                <Select style={{ width: '100%' }} value={policyReviewDraft.riskLevel} onChange={(value) => setPolicyReviewDraft((current) => current ? ({ ...current, riskLevel: String(value || '') }) : current)}>
                  <Option value="LOW">低</Option><Option value="MEDIUM">中</Option><Option value="HIGH">高</Option><Option value="CRITICAL">严重</Option>
                </Select>
              </Space>
            </OpsResponsiveGrid>
            <OpsResponsiveGrid $min="210px">
              {([
                ['readOnly', '只读工具'], ['investigateAllowed', '审核前可查询'], ['prepareAllowed', '验证阶段可用'],
                ['landAllowed', '审批后可执行'], ['requiresApprovedPackage', '必须绑定执行包'], ['requiresHumanApproval', '必须人工审批'],
                ['requiresDryRun', '必须干跑'], ['requiresRollbackPlan', '必须回滚方案'],
              ] as Array<[keyof PolicyReviewDraft, string]>).map(([key, label]) => (
                <Space key={key} align="center"><Switch checked={Boolean(policyReviewDraft[key])} onChange={(checked) => setPolicyReviewDraft((current) => current ? ({ ...current, [key]: checked }) : current)} /><Text>{label}</Text></Space>
              ))}
            </OpsResponsiveGrid>
            <Space vertical align="start" style={{ width: '100%' }}>
              <Text strong>审核说明</Text>
              <TextArea value={policyReviewDraft.reason} autosize rows={3} placeholder="说明为什么该权限范围是安全且必要的" onChange={(value) => setPolicyReviewDraft((current) => current ? ({ ...current, reason: value }) : current)} />
            </Space>
          </Space>
        ) : null}
      </Modal>
    </OpsPageShell>
  );
};

export default ToolRoutingObservabilityPage;
