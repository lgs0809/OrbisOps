import React, { useEffect, useMemo, useRef, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useNavigate, useSearchParams } from 'react-router-dom';
import styled from 'styled-components';
import {
  Button,
  Empty,
  Modal,
  Select,
  Space,
  Spin,
  Table,
  Tag,
  TextArea,
  Toast,
  Typography,
} from '@douyinfe/semi-ui';

import {
  JsonBlock,
  OpsAdvancedPreview,
  OpsDangerZone,
  OpsEmptyState,
  OpsPageHeader,
  OpsPageShell,
  OpsResponsiveGrid,
  OpsSectionCard,
  TableScroll,
} from '../components/ops-layout';
import { opsChangePackageService } from '../services/ops-change-package-service';
import type {
  OpsChangePackage,
  OpsChangePackageEvent,
  OpsLandingOperationRun,
} from '../services/ops-change-package-types';
import { useProjectScope } from '../hooks/use-project-scope';
import { getStoredUserInfo, isAdminUser } from '../services/auth-session';
import { ExecutionQueue, PackageCapability, packageCapability, packagesForQueue } from '../features/execution/execution-center-model';
import { ApprovalChannelDispatch } from '../features/changes/components/ApprovalChannelDispatch';
import { ApprovalCockpit } from '../features/changes/components/ApprovalCockpit';
import { toChangePackageSummary } from '../features/changes/model/change-package-summary';
import { theme } from '../styles/theme';
import {
  changePackageReasonLabel,
  changePackageRiskColor,
  changePackageRiskLabel,
} from './change-package-ui';

const { Option } = Select;
const { Text, Title } = Typography;

const Toolbar = styled.div`
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: ${theme.spacing.base};
  width: 100%;
  min-width: 0;
  margin-bottom: ${theme.spacing.base};
`;

const DetailGrid = styled.div`
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(320px, 0.85fr);
  gap: ${theme.spacing.base};
  width: 100%;
  min-width: 0;

  @media (max-width: ${theme.breakpoints.lg}) {
    grid-template-columns: minmax(0, 1fr);
  }
`;

const EventList = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${theme.spacing.sm};
  width: 100%;
  min-width: 0;
  max-height: 360px;
  overflow: auto;
`;

const EventItem = styled.div`
  min-width: 0;
  padding: ${theme.spacing.sm} ${theme.spacing.base};
  border: 1px solid ${theme.colors.border.secondary};
  border-radius: ${theme.borderRadius.base};
  background: ${theme.colors.bg.secondary};
`;

const shortHash = (value?: string) => {
  if (!value) {
    return '-';
  }
  return value.length > 18 ? `${value.slice(0, 10)}...${value.slice(-6)}` : value;
};

const statusOptions = [
  { value: '', label: '全部状态' },
  { value: 'DRAFT', label: '草稿' },
  { value: 'VALIDATING', label: '验证中' },
  { value: 'VALIDATION_FAILED', label: '验证失败' },
  { value: 'READY_FOR_REVIEW', label: '可提交审核' },
  { value: 'REVIEWING', label: '待审批' },
  { value: 'REVISING', label: '待修订' },
  { value: 'REJECTED', label: '已驳回' },
  { value: 'APPROVED', label: '已审批' },
  { value: 'LANDING', label: '落地执行中' },
  { value: 'LANDED', label: '已落地' },
  { value: 'NEEDS_REPLAN', label: '需重规划' },
  { value: 'LANDING_FAILED', label: '执行失败' },
  { value: 'CLOSED', label: '已关闭' },
];

const riskColor = changePackageRiskColor;

const formatJson = (value: unknown) => {
  if (value == null || value === '') {
    return '{}';
  }
  if (typeof value === 'string') {
    try {
      return JSON.stringify(JSON.parse(value), null, 2);
    } catch {
      return value;
    }
  }
  return JSON.stringify(value, null, 2);
};

const parseJsonValue = (value: unknown): any => {
  if (value == null || value === '') return null;
  if (typeof value !== 'string') return value;
  try {
    return JSON.parse(value);
  } catch {
    return value;
  }
};

const toArray = (value: unknown): any[] => {
  const parsed = parseJsonValue(value);
  if (Array.isArray(parsed)) return parsed;
  if (Array.isArray(parsed?.steps)) return parsed.steps;
  if (Array.isArray(parsed?.operations)) return parsed.operations;
  if (Array.isArray(parsed?.mcpSteps)) return parsed.mcpSteps;
  if (Array.isArray(parsed?.items)) return parsed.items;
  return parsed ? [parsed] : [];
};

const valueText = (value: unknown, fallback = '-') => {
  if (value == null || value === '') return fallback;
  if (typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean') return String(value);
  return JSON.stringify(value);
};

const sensitiveArgument = /password|passwd|pwd|secret|token|api[_-]?key|access[_-]?key|private[_-]?key|credential|authorization|bearer|cookie/i;

const safeArgumentText = (key: string, value: unknown) => (
  sensitiveArgument.test(key) ? '***' : valueText(value)
);

const argumentLabels: Record<string, string> = {
  query: '查询条件',
  sql: 'SQL',
  namespace: '命名空间',
  database: '数据库',
  table: '数据表',
  service: '服务',
  environment: '环境',
  key: '配置项',
  value: '目标值',
  timeoutMs: '超时',
  limit: '数量上限',
};

const humanArguments = (value: unknown) => {
  const parsed = parseJsonValue(value);
  if (parsed == null || parsed === '') return '无参数';
  if (Array.isArray(parsed)) return parsed.map((item) => valueText(item)).join('；');
  if (typeof parsed !== 'object') return String(parsed);
  const entries = Object.entries(parsed as Record<string, unknown>);
  if (entries.length === 0) return '无参数';
  return entries
    .slice(0, 6)
    .map(([key, item]) => `${argumentLabels[key] || key}：${safeArgumentText(key, item)}`)
    .join('；');
};

const operationStatus = (run: OpsLandingOperationRun) => {
  const status = String(run.status || '').toUpperCase();
  if (status === 'SUCCEEDED') return { label: '执行成功', color: 'green' as const };
  if (status === 'FAILED') return { label: '执行失败', color: 'red' as const };
  if (status === 'BLOCKED' || status === 'NEEDS_REPLAN') return { label: '已阻断', color: 'orange' as const };
  if (status === 'EXECUTING' || status === 'POST_CHECKING' || status === 'PRECONDITION_CHECKING') {
    return { label: '执行中', color: 'blue' as const };
  }
  return { label: status || '等待执行', color: 'grey' as const };
};

const packageOperations = (record?: OpsChangePackage | null) => {
  if (!record) return [];
  const approvedSnapshot = parseJsonValue(record.approvedSnapshot);
  const steps = [
    ...toArray(record.mcpSteps),
    ...toArray(record.mcpStepsJson),
    ...toArray(record.landingPlan),
    ...toArray(record.landingPlanJson),
    ...toArray(record.preferredPlanJson),
    ...toArray(approvedSnapshot?.mcpSteps),
    ...toArray(approvedSnapshot?.mcpStepsJson),
    ...toArray(approvedSnapshot?.landingPlan),
    ...toArray(approvedSnapshot?.landingPlanJson),
  ];
  const seen = new Set<string>();
  return steps
    .map((step, index) => {
      const op = step || {};
      const id = valueText(op.operationId || op.operation_id || op.id || `${op.mcpId || op.toolName || 'operation'}-${index}`);
      return {
        id,
        tool: valueText(op.toolName || op.tool_name || op.remoteToolName || op.remote_tool_name || op.action || op.type, '待确认工具'),
        resource: valueText(op.resourceKey || op.resource_key || op.resourceScope || op.resource_scope || op.targetObject || op.target_object || op.serviceId || op.service_id, '未声明资源'),
        effect: valueText(op.effectType || op.effect_type || op.mutability || op.riskLevel || op.risk_level, '未声明效果'),
        args: op.arguments || op.args || op.input || op.parameters,
      };
    })
    .filter((op) => {
      if (seen.has(op.id)) return false;
      seen.add(op.id);
      return true;
    })
    .slice(0, 8);
};

const proofState = (value: unknown) => {
  const parsed = parseJsonValue(value);
  const status = String(parsed?.status || parsed?.resultStatus || parsed?.assessment || '').toUpperCase();
  if (!parsed) return { label: '未提供', color: 'grey' as const };
  if (['PASSED', 'SUCCEEDED', 'SUCCESS', 'READY'].includes(status)) return { label: '已通过', color: 'green' as const };
  if (['FAILED', 'BLOCKED', 'SANDBOX_UNAVAILABLE', 'WAITING_SANDBOX'].includes(status)) return { label: status, color: 'red' as const };
  return { label: status || '已记录', color: 'blue' as const };
};

const hasPackageHash = (record?: OpsChangePackage | null) => Boolean(record?.packageHash || record?.approvedPackageHash);

const field = (record: OpsChangePackage | null | undefined, ...names: string[]) => {
  if (!record) return '';
  for (const name of names) {
    const value = (record as any)[name];
    if (value !== undefined && value !== null && value !== '') {
      return value;
    }
  }
  return '';
};

const hasContextBundle = (record?: OpsChangePackage | null) => Boolean(
  field(record, 'contextBundleId', 'context_bundle_id')
    && field(record, 'contextBundleHash', 'context_bundle_hash'),
);

const missingReviewItems = (record?: OpsChangePackage | null) => {
  if (!record) return ['未选择执行包'];
  const items: string[] = [];
  if (!hasContextBundle(record)) items.push('缺少后端 Runtime Context Bundle，请从对话重新生成执行包');
  if (!record.packageHash) items.push('缺少当前版本 packageHash');
  if (!record.riskLevel) items.push('缺少风险等级');
  if (!record.targetEnvironment) items.push('缺少目标环境');
  if (packageOperations(record).length === 0 && record.packageType !== 'NO_ACTION_REQUIRED') items.push('没有声明可落地操作');
  if (!record.rollbackStepsJson && ['HIGH', 'CRITICAL'].includes(String(record.riskLevel || '').toUpperCase())) items.push('高风险执行包缺少回滚计划');
  if (!record.verificationCriteriaJson && record.packageType !== 'NO_ACTION_REQUIRED') items.push('缺少落地后验证标准');
  return items;
};

export const ChangePackageCenterPage: React.FC = () => {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const projectScope = useProjectScope();
  const admin = isAdminUser();
  const apiScope: 'admin' | 'user' = admin ? 'admin' : 'user';
  const storedPrincipal = getStoredUserInfo();
  const principalKey = storedPrincipal.userId || storedPrincipal.username || 'anonymous';
  const requestedPackageAppliedRef = useRef('');
  const [selected, setSelected] = useState<OpsChangePackage | null>(null);
  const [detailVisible, setDetailVisible] = useState(false);
  const [events, setEvents] = useState<OpsChangePackageEvent[]>([]);
  const [landingOperations, setLandingOperations] = useState<OpsLandingOperationRun[]>([]);
  const [status, setStatus] = useState('');
  const [queue, setQueue] = useState<ExecutionQueue>('pending');
  const [workView, setWorkView] = useState<'mine' | 'all'>(() => admin ? 'all' : 'mine');
  const projectId = projectScope.projectId;
  const setProjectId = projectScope.selectProject;
  const [actionMode, setActionMode] = useState<'validate' | 'submitReview' | 'approve' | 'reject' | 'land' | 'cleanup' | null>(null);
  const [comment, setComment] = useState('');
  const [actionLoading, setActionLoading] = useState(false);

  const query = useMemo(() => ({
    status: status || undefined,
    projectId: projectId.trim() || undefined,
    incidentId: searchParams.get('incidentId') || undefined,
    scope: apiScope,
    limit: 100,
  }), [apiScope, projectId, searchParams, status]);

  const packageQuery = useQuery({
    queryKey: ['execution-center', principalKey, apiScope, query.projectId || '', query.status || '', query.incidentId || ''],
    queryFn: async () => {
      const response = await opsChangePackageService.list(query);
      const requestedIncidentId = query.incidentId;
      return (response.data || []).filter((item) => (
        !requestedIncidentId || apiScope === 'admin' || item.incidentId === requestedIncidentId
      ));
    },
    enabled: admin || Boolean(query.projectId),
  });
  const packages = packageQuery.data || [];
  const loading = packageQuery.isFetching;

  const loadPackages = async () => {
    if (!admin && !query.projectId) return;
    await packageQuery.refetch();
  };

  const openDetail = async (record: OpsChangePackage) => {
    setSelected(record);
    setDetailVisible(true);
    setEvents([]);
    setLandingOperations([]);
    try {
      const [detailResponse, eventsResponse, operationResponse] = await Promise.all([
        opsChangePackageService.get(record.packageId, apiScope),
        opsChangePackageService.listEvents(record.packageId, apiScope, 100),
        opsChangePackageService.listLandingOperationRuns(record.packageId, apiScope, 200),
      ]);
      setSelected(detailResponse.data || record);
      setEvents(eventsResponse.data || []);
      setLandingOperations(operationResponse.data || []);
    } catch (error) {
      Toast.error(error instanceof Error ? error.message : '详情加载失败');
    }
  };

  useEffect(() => {
    const packageId = searchParams.get('packageId') || '';
    if (!packageId) {
      requestedPackageAppliedRef.current = '';
      return;
    }
    if (requestedPackageAppliedRef.current === packageId || packages.length === 0) return;
    const target = packages.find((item) => item.packageId === packageId);
    if (!target) return;
    requestedPackageAppliedRef.current = packageId;
    void openDetail(target);
    setSearchParams((current) => {
      const next = new URLSearchParams(current);
      next.delete('packageId');
      return next;
    }, { replace: true });
  }, [packages, searchParams, setSearchParams]);

  useEffect(() => {
    if (!packageQuery.error) return;
    Toast.error(packageQuery.error instanceof Error ? packageQuery.error.message : 'ChangePackage 加载失败');
  }, [packageQuery.error]);

  useEffect(() => {
    if (loading || !selected) return;
    if (packages.some((item) => item.packageId === selected.packageId)) return;
    setSelected(null);
    setEvents([]);
    setLandingOperations([]);
  }, [loading, packages, selected]);

  const submitAction = async () => {
    if (!selected || !actionMode) {
      return;
    }
    const requiredCapability: Record<NonNullable<typeof actionMode>, PackageCapability> = {
      validate: 'canRevise',
      submitReview: 'canSubmitReview',
      approve: 'canApprove',
      reject: 'canReject',
      land: 'canLand',
      cleanup: 'canCleanup',
    };
    if (!packageCapability(selected, requiredCapability[actionMode])) {
      Toast.error('当前账号没有执行该动作的项目权限');
      return;
    }
    setActionLoading(true);
    try {
      if (actionMode === 'validate') {
        await opsChangePackageService.validate(selected.packageId, { reason: comment.trim() }, apiScope);
        Toast.success('已触发审核前验证');
      } else if (actionMode === 'submitReview') {
        await opsChangePackageService.submitReview(selected.packageId, { summary: comment.trim() }, apiScope);
        Toast.success('已提交人工审核');
      } else if (actionMode === 'approve') {
        if (!selected.packageHash) {
          Toast.error('当前版本缺少 packageHash，不能审批');
          return;
        }
        await opsChangePackageService.approve(selected.packageId, selected.version, selected.packageHash, apiScope);
        Toast.success('ChangePackage 已审批');
      } else if (actionMode === 'reject') {
        await opsChangePackageService.reject(selected.packageId, { reason: comment.trim(), comment: comment.trim() }, apiScope);
        Toast.success('ChangePackage 已驳回');
      } else if (actionMode === 'land') {
        const approvedVersion = selected.approvedVersion || selected.version;
        const approvedPackageHash = selected.approvedPackageHash || selected.packageHash || '';
        if (!approvedVersion || !approvedPackageHash) {
          Toast.error('缺少已审批版本或 approvedPackageHash，不能落地');
          return;
        }
        await opsChangePackageService.land(selected.packageId, {
          version: approvedVersion,
          packageHash: approvedPackageHash,
          approvedPackageHash,
        }, apiScope);
        Toast.success('生产处置已提交到独立安全执行环境');
      } else if (actionMode === 'cleanup') {
        await opsChangePackageService.cleanup(selected.packageId, { reason: comment.trim() }, apiScope);
        Toast.success('清理请求已提交');
      }
      setActionMode(null);
      setComment('');
      await loadPackages();
      await openDetail(selected);
    } catch (error) {
      Toast.error(error instanceof Error ? error.message : '操作失败');
    } finally {
      setActionLoading(false);
    }
  };

  const selectedHash = selected?.approvedPackageHash || selected?.packageHash || '-';
  const returnToPreApproval = (record: OpsChangePackage) => {
    const reason = changePackageReasonLabel(record.reasonCode)
      || String(parseJsonValue(record.failureSummaryJson)?.reason || record.reasonCode || '落地结果要求重新规划');
    const params = new URLSearchParams({
      projectId: record.projectId,
      replanPackageId: record.packageId,
      replanReason: reason,
    });
    navigate(`/chat?${params.toString()}`);
  };
  const selectedMissingItems = missingReviewItems(selected);
  const selectedReadyForApproval = selectedMissingItems.length === 0;
  const queueItems = (target: ExecutionQueue) => packagesForQueue(packages, target, {
    platformAdmin: admin,
    mineOnly: workView === 'mine',
  });
  const visiblePackages = queueItems(queue);
  const interventionItems = queueItems('intervention');
  const needsReplanCount = interventionItems.filter((item) => item.status === 'NEEDS_REPLAN').length;
  const landingFailedCount = interventionItems.filter((item) => item.status === 'LANDING_FAILED').length;

  return (
    <OpsPageShell selectedKey="executions">
      <OpsPageHeader
        title="执行中心"
        description={admin
          ? '统一查看项目处置方案、审批、生产执行与恢复证明。所有动作都以后端项目权限和当前执行包 capability facts 为准。'
          : '这里统一展示与你所在项目有关的处置任务。默认只看当前需要你处理的事项，审批和生产执行权限完全以后端 capability facts 为准。'}
        primaryAction={<Button onClick={loadPackages}>刷新</Button>}
      />

      <OpsResponsiveGrid $min="220px">
        <OpsSectionCard title="待审批">
          <Title heading={4} style={{ margin: 0 }}>{queueItems('pending').length}</Title>
          <Text type="tertiary">等待验证完成、提交审核或人工审批</Text>
          <div style={{ marginTop: 8 }}><Button theme="borderless" onClick={() => setQueue('pending')}>查看待审批</Button></div>
        </OpsSectionCard>
        <OpsSectionCard title="执行中">
          <Title heading={4} style={{ margin: 0 }}>{queueItems('running').length}</Title>
          <Text type="tertiary">已审批待 Landing 或正在生产执行</Text>
          <div style={{ marginTop: 8 }}><Button theme="borderless" onClick={() => setQueue('running')}>查看执行中</Button></div>
        </OpsSectionCard>
        <OpsSectionCard title="需介入">
          <Title heading={4} style={{ margin: 0 }}>{queueItems('intervention').length}</Title>
          <Text type="tertiary">{needsReplanCount} 个需重规划，{landingFailedCount} 个执行失败；不会自动重试。</Text>
          <div style={{ marginTop: 8 }}><Button theme="borderless" onClick={() => setQueue('intervention')}>查看需介入</Button></div>
        </OpsSectionCard>
        <OpsSectionCard title="历史">
          <Title heading={4} style={{ margin: 0 }}>{queueItems('history').length}</Title>
          <Text type="tertiary">已落地或关闭的执行记录与审计证明</Text>
          <div style={{ marginTop: 8 }}><Button theme="borderless" onClick={() => setQueue('history')}>查看历史</Button></div>
        </OpsSectionCard>
      </OpsResponsiveGrid>

      <OpsSectionCard title={`执行队列 · ${{ pending: '待审批', running: '执行中', intervention: '需介入', history: '历史' }[queue]}`}>
        <Toolbar>
          <Select
            value={projectId}
            onChange={(value) => setProjectId(String(value || ''))}
            loading={projectScope.loading}
            style={{ width: 260 }}
            placeholder="选择项目"
          >
            {admin && <Option value="">全部项目</Option>}
            {projectScope.projects.map((project) => (
              <Option key={project.projectId} value={project.projectId}>{project.name || project.projectId}</Option>
            ))}
          </Select>
          {!admin && (
            <Select value={workView} onChange={(value) => setWorkView(value as 'mine' | 'all')} style={{ width: 160 }}>
              <Option value="mine">需要我处理</Option>
              <Option value="all">项目全部</Option>
            </Select>
          )}
          <Select value={status} onChange={(value) => setStatus(String(value || ''))} style={{ width: 180 }}>
            {statusOptions.map((item) => (
              <Option key={item.value || 'all'} value={item.value}>{item.label}</Option>
            ))}
          </Select>
          <Text type="tertiary">列表只展示业务状态；版本、哈希和执行证明请在详情的高级安全证明中查看。</Text>
        </Toolbar>
        <Spin spinning={loading}>
          {visiblePackages.length === 0 ? (
            <Empty title="当前队列暂无 ChangePackage" description="切换上方待审批、执行中、需介入或历史队列查看其他执行包。" />
          ) : (
            <TableScroll>
              <Table
                dataSource={visiblePackages}
                rowKey="packageId"
                pagination={{ pageSize: 10 }}
                scroll={{ x: 1080 }}
                columns={[
                  {
                    title: '执行包',
                    dataIndex: 'packageId',
                    width: 260,
                    render: (_: unknown, record: OpsChangePackage) => {
                      const summary = toChangePackageSummary(record);
                      return (
                        <div>
                          <Text strong ellipsis={{ showTooltip: true }}>{summary.title}</Text>
                          <div style={{ color: theme.colors.text.tertiary, fontSize: 12 }}>
                            {summary.packageId} · v{summary.version}
                          </div>
                        </div>
                      );
                    },
                  },
                  {
                    title: '项目',
                    dataIndex: 'projectId',
                    width: 160,
                    render: (value: string) => value || '-',
                  },
                  {
                    title: '状态',
                    dataIndex: 'status',
                    width: 130,
                    render: (_: string, record: OpsChangePackage) => {
                      const summary = toChangePackageSummary(record);
                      return <Tag color={summary.statusColor as any}>{summary.statusLabel}</Tag>;
                    },
                  },
                  {
                    title: '风险',
                    dataIndex: 'riskLevel',
                    width: 120,
                    render: (_: string, record: OpsChangePackage) => {
                      const summary = toChangePackageSummary(record);
                      return <Tag color={summary.riskColor as any}>{summary.riskLabel}</Tag>;
                    },
                  },
                  {
                    title: '环境',
                    dataIndex: 'targetEnvironment',
                    width: 140,
                    render: (value: string) => value || '-',
                  },
                  {
                    title: '当前结论',
                    dataIndex: 'reasonCode',
                    width: 190,
                    render: (_: string, record: OpsChangePackage) => {
                      const summary = toChangePackageSummary(record);
                      return (
                        <Text ellipsis={{ showTooltip: true }}>
                          {summary.reasonCode ? summary.reasonLabel : summary.statusHint}
                        </Text>
                      );
                    },
                  },
                  {
                    title: '更新时间',
                    dataIndex: 'updateTime',
                    width: 160,
                    render: (value: string) => value || '-',
                  },
                  {
                    title: '操作',
                    key: 'actions',
                    width: 96,
                    fixed: 'right' as const,
                    render: (_: unknown, record: OpsChangePackage) => (
                      <Button size="small" onClick={() => openDetail(record)}>查看</Button>
                    ),
                  },
                ]}
              />
            </TableScroll>
          )}
        </Spin>
      </OpsSectionCard>

      <Modal
        title={selected ? `执行包详情 - ${selected.summary || selected.objective || selected.packageId}` : '执行包详情'}
        visible={detailVisible}
        footer={null}
        width={1120}
        style={{ maxWidth: '96vw' }}
        onCancel={() => setDetailVisible(false)}
      >
        {selected && (
        <div style={{ maxHeight: '72vh', overflow: 'auto', paddingRight: 8 }}>
        <DetailGrid>
          <OpsSectionCard title="Approval Cockpit">
            <ApprovalCockpit record={selected} />
            <ApprovalChannelDispatch
              record={selected}
              scope={apiScope}
              canApprove={packageCapability(selected, 'canApprove')}
            />
            {selectedMissingItems.length > 0 && (
              <OpsDangerZone
                title="审批材料仍有缺口"
                description="缺口未闭合前不能把“可查看”误当成“可审批”。"
              >
                <Space vertical align="start" spacing={4}>
                  {selectedMissingItems.map((item) => <Text key={item}>- {item}</Text>)}
                </Space>
              </OpsDangerZone>
            )}
            <OpsAdvancedPreview title="审批版本与 Runtime 标识" description="仅用于审计与调试，不作为普通用户理解处置方案的第一屏。">
              <JsonBlock>{formatJson({
                packageId: selected.packageId,
                version: selected.version,
                approvedVersion: selected.approvedVersion,
                packageHash: selected.packageHash,
                approvedPackageHash: selected.approvedPackageHash,
                contextBundleId: field(selected, 'contextBundleId', 'context_bundle_id'),
                contextBundleHash: field(selected, 'contextBundleHash', 'context_bundle_hash'),
                repositoryId: selected.repositoryId,
                repairCommit: selected.repairCommit,
                diffHash: selected.diffHash,
              })}</JsonBlock>
            </OpsAdvancedPreview>

            <OpsSectionCard title="审批前请确认：Landing 会做什么" style={{ marginTop: 16 }}>
              <Space vertical align="start" spacing="medium" style={{ width: '100%' }}>
                <Text>
                  这个执行包面向 <Text strong>{selected.targetEnvironment || '未声明环境'}</Text>，
                  风险等级为 <Tag color={riskColor(selected.riskLevel)}>{changePackageRiskLabel(selected.riskLevel)}</Tag>。
                  审批通过后，AI 只能在你当前确认的处置范围内执行；任何需要实质改变方案的情况都必须重新规划并重新审批。
                </Text>
                <Space wrap>
                  <Tag color={proofState(selected.preflightResultJson).color}>预检：{proofState(selected.preflightResultJson).label}</Tag>
                  {selected.dryRunResultJson && (
                    <Tag color={proofState(selected.dryRunResultJson).color}>验证：{proofState(selected.dryRunResultJson).label}</Tag>
                  )}
                  {selected.ciResultJson && (
                    <Tag color={proofState(selected.ciResultJson).color}>CI：{proofState(selected.ciResultJson).label}</Tag>
                  )}
                  {selected.testProofHash && <Tag color="green">测试证明：{shortHash(selected.testProofHash)}</Tag>}
                </Space>
                {packageOperations(selected).length > 0 ? (
                  <Table
                    size="small"
                    pagination={false}
                    rowKey="id"
                    dataSource={packageOperations(selected)}
                    columns={[
                      { title: '操作', dataIndex: 'tool', width: 180 },
                      { title: '目标资源', dataIndex: 'resource' },
                      { title: '效果/风险', dataIndex: 'effect', width: 160 },
                      {
                        title: '关键参数',
                        dataIndex: 'args',
                        render: (args: unknown) => (
                          <Text ellipsis={{ showTooltip: true }}>{humanArguments(args)}</Text>
                        ),
                      },
                    ]}
                  />
                ) : (
                  <OpsEmptyState title="没有可执行的处置步骤" description="当前方案没有声明可执行的生产处置步骤。审批前应要求 AI 修订；如果方案本身不成立，必须重新规划并重新审批。" />
                )}
                <Space vertical align="start" spacing={2}>
                  <Text strong>回滚/验证</Text>
                  <Text type="tertiary">回滚计划：{selected.rollbackStepsJson ? '已记录' : '未记录'}；落地后验证：{selected.verificationCriteriaJson ? '已记录' : '未记录'}；允许的小修：{selected.allowedLandingAdjustmentsJson ? '已声明' : '未声明'}。</Text>
                </Space>
                {selectedMissingItems.length > 0 ? (
                  <OpsDangerZone
                    title="当前不能审批或落地"
                    description="下面这些缺口会阻止审批或安全执行；如果原方案因此不再成立，需要回到诊断重新规划并重新审批。"
                  >
                    <Space vertical align="start" spacing={4}>
                      {selectedMissingItems.map((item) => <Text key={item}>- {item}</Text>)}
                    </Space>
                  </OpsDangerZone>
                ) : (
                  <Tag color="green">审批材料完整：可以按当前状态执行允许的下一步</Tag>
                )}
              </Space>
            </OpsSectionCard>

            <Space vertical align="start" spacing="medium" style={{ width: '100%', marginTop: 16 }}>
              <OpsAdvancedPreview title="MCP 步骤 / 预检 / 显式验证" description="这些字段会纳入审批快照和 packageHash，落地时只能读取已审批版本。">
                <JsonBlock>{formatJson({
                  toolBindingsJson: selected.toolBindingsJson,
                  mcpStepsJson: selected.mcpStepsJson,
                  preflightResultJson: selected.preflightResultJson,
                  validationResultJson: selected.dryRunResultJson,
                })}</JsonBlock>
              </OpsAdvancedPreview>
              <OpsAdvancedPreview title="代码修复 / Bash / LSP 证据" description="代码修复字段会进入审批快照；Landing 只能执行 approvedVersion 中记录的 repairCommit、diffHash 和测试证明。">
                <JsonBlock>{formatJson({
                  repairWorkspaceId: selected.repairWorkspaceId,
                  repositoryId: selected.repositoryId,
                  serviceId: selected.serviceId,
                  baseCommit: selected.baseCommit,
                  repairCommit: selected.repairCommit,
                  diffSummary: selected.diffSummary,
                  diffHash: selected.diffHash,
                  testProofHash: selected.testProofHash,
                  changedFilesJson: selected.changedFilesJson,
                  codeEvidenceJson: selected.codeEvidenceJson,
                  bashEvidenceJson: selected.bashEvidenceJson,
                  lspEvidenceJson: selected.lspEvidenceJson,
                })}</JsonBlock>
              </OpsAdvancedPreview>
              <OpsAdvancedPreview title="审批边界 / Landing Plan / 回滚和验证" description="platform-landing-react 会基于这些边界做执行前检查。">
                <JsonBlock>{formatJson({
                  approvalBoundaryJson: selected.approvalBoundaryJson,
                  preferredPlanJson: selected.preferredPlanJson,
                  adjustmentPolicyJson: selected.adjustmentPolicyJson,
                  landingPlanJson: selected.landingPlanJson,
                  allowedLandingAdjustmentsJson: selected.allowedLandingAdjustmentsJson,
                  rollbackStepsJson: selected.rollbackStepsJson,
                  verificationCriteriaJson: selected.verificationCriteriaJson,
                  cleanupPlanJson: selected.cleanupPlanJson,
                })}</JsonBlock>
              </OpsAdvancedPreview>
            </Space>
          </OpsSectionCard>

          <Space vertical align="start" spacing="medium" style={{ width: '100%', minWidth: 0 }}>
            <OpsSectionCard title="落地步骤">
              {landingOperations.length === 0 ? (
                <Empty title="尚未执行生产处置" description="审批通过并开始安全执行后，这里会逐项显示前置检查、执行和后置检查结果。" />
              ) : (
                <TableScroll>
                  <Table
                    size="small"
                    pagination={false}
                    rowKey="operationRunId"
                    dataSource={landingOperations}
                    scroll={{ x: 900 }}
                    columns={[
                      {
                        title: '步骤',
                        dataIndex: 'operationId',
                        width: 170,
                        render: (value: string, run: OpsLandingOperationRun) => (
                          <div>
                            <Text strong>{run.toolName || value || '未命名操作'}</Text>
                            <div style={{ color: theme.colors.text.tertiary, fontSize: 12 }}>{value || '-'}</div>
                          </div>
                        ),
                      },
                      { title: '目标资源', dataIndex: 'resourceKey', width: 180, render: (value: string) => value || '-' },
                      {
                        title: '状态',
                        key: 'status',
                        width: 110,
                        render: (_: unknown, run: OpsLandingOperationRun) => {
                          const view = operationStatus(run);
                          return <Tag color={view.color}>{view.label}</Tag>;
                        },
                      },
                      { title: '阶段', dataIndex: 'stage', width: 120, render: (value: string) => value || '-' },
                      {
                        title: '回滚',
                        dataIndex: 'rollbackStatus',
                        width: 120,
                        render: (value: string) => value ? <Tag color={value === 'SUCCEEDED' ? 'green' : value === 'FAILED' ? 'red' : 'blue'}>{value}</Tag> : '-',
                      },
                      {
                        title: '结果说明',
                        dataIndex: 'reasonCode',
                        render: (value: string, run: OpsLandingOperationRun) => (
                          <Space vertical align="start" spacing={2}>
                            <Text>{changePackageReasonLabel(value) || value || (run.status === 'SUCCEEDED' ? '前置检查、执行和后置检查均通过' : '-')}</Text>
                            {(run.resultId || run.outputHash) && (
                              <Text type="tertiary">结果 {run.resultId || '-'} · {shortHash(run.outputHash)}</Text>
                            )}
                          </Space>
                        ),
                      },
                    ]}
                  />
                </TableScroll>
              )}
              {landingOperations.length > 0 && (
                <OpsAdvancedPreview title="操作级技术记录" description="用于故障定位；审批判断请以上方人类可读状态为准。">
                  <JsonBlock>{formatJson(landingOperations)}</JsonBlock>
                </OpsAdvancedPreview>
              )}
            </OpsSectionCard>

            <OpsSectionCard title="事件轨迹">
              {events.length === 0 ? (
                <Empty title="暂无事件" description="审批、落地、拦截、清理等动作会记录到这里。" />
              ) : (
                <EventList>
                  {events.map((event) => (
                    <EventItem key={event.eventId || `${event.eventType}-${event.createTime}`}>
                      <Space vertical align="start" spacing={2} style={{ width: '100%' }}>
                        <Space wrap>
                          <Tag>{event.eventType}</Tag>
                          <Text type="tertiary">{event.createTime || '-'}</Text>
                        </Space>
                        <Text>{event.summary || changePackageReasonLabel(event.reasonCode) || '-'}</Text>
                        <OpsAdvancedPreview title="事件 Payload" description="默认折叠，避免原始 payload 撑开页面。">
                          <JsonBlock>{formatJson(event.payloadJson)}</JsonBlock>
                        </OpsAdvancedPreview>
                      </Space>
                    </EventItem>
                  ))}
                </EventList>
              )}
            </OpsSectionCard>

            <OpsDangerZone
              title="ChangePackage 危险操作"
              description="验证、提交审核、审批、驳回、生产执行和清理都会写入审计。生产处置始终只能执行已审批的当前方案，任何实质变化都必须重新审批。"
            >
              <Space wrap>
                {packageCapability(selected, 'canRevise') && (
                  <Button
                    disabled={!['DRAFT', 'VALIDATION_FAILED', 'REVISING'].includes(selected.status)}
                    onClick={() => setActionMode('validate')}
                  >
                    运行审核前验证
                  </Button>
                )}
                {packageCapability(selected, 'canSubmitReview') && (
                  <Button
                    disabled={selected.status !== 'READY_FOR_REVIEW'}
                    onClick={() => setActionMode('submitReview')}
                  >
                    提交人工审核
                  </Button>
                )}
                {packageCapability(selected, 'canApprove') && (
                  <Button
                    type="primary"
                    disabled={selected.status !== 'REVIEWING' || !hasPackageHash(selected) || !selectedReadyForApproval}
                    onClick={() => setActionMode('approve')}
                  >
                    审批当前版本
                  </Button>
                )}
                {packageCapability(selected, 'canReject') && (
                  <Button
                    type="danger"
                    theme="borderless"
                    disabled={selected.status !== 'REVIEWING'}
                    onClick={() => setActionMode('reject')}
                  >
                    驳回修订
                  </Button>
                )}
                {packageCapability(selected, 'canLand') && (
                  <Button
                    disabled={
                      selected.status !== 'APPROVED'
                      || !selected.approvedPackageHash
                      || !hasContextBundle(selected)
                      || !selectedReadyForApproval
                    }
                    onClick={() => setActionMode('land')}
                  >
                    执行生产处置
                  </Button>
                )}
                {packageCapability(selected, 'canCleanup') && (
                  <Button
                    disabled={!['LANDED', 'LANDING_FAILED', 'NEEDS_REPLAN', 'CLOSED'].includes(selected.status)}
                    onClick={() => setActionMode('cleanup')}
                  >
                    清理临时资源
                  </Button>
                )}
                {packageCapability(selected, 'canRevise') && (
                  <Button
                    type="primary"
                    disabled={!['NEEDS_REPLAN', 'LANDING_FAILED', 'REJECTED', 'VALIDATION_FAILED'].includes(selected.status)}
                    onClick={() => returnToPreApproval(selected)}
                  >
                    回到对话重新规划
                  </Button>
                )}
                {!['canRevise', 'canSubmitReview', 'canApprove', 'canReject', 'canLand', 'canCleanup']
                  .some((capability) => packageCapability(selected, capability as PackageCapability)) && (
                  <Tag color="grey">当前项目权限仅允许查看</Tag>
                )}
              </Space>
            </OpsDangerZone>
          </Space>
        </DetailGrid>
        </div>
        )}
      </Modal>

      <Modal
        title={
          actionMode === 'validate' ? '运行审核前验证'
            : actionMode === 'submitReview' ? '提交人工审核'
              : actionMode === 'approve' ? '审批 ChangePackage'
                : actionMode === 'reject' ? '驳回 ChangePackage'
                  : actionMode === 'land' ? '执行生产处置'
                    : '清理临时资源'
        }
        visible={Boolean(actionMode)}
        confirmLoading={actionLoading}
        okText="确认"
        cancelText="取消"
        onOk={submitAction}
        onCancel={() => {
          if (!actionLoading) {
            setActionMode(null);
            setComment('');
          }
        }}
      >
        <Space vertical align="start" spacing="medium" style={{ width: '100%' }}>
          <Text type="tertiary">
            {selected?.packageId} · v{actionMode === 'land' ? selected?.approvedVersion : selected?.version} · {selectedHash}
          </Text>
          <Text>
            {actionMode === 'validate'
              ? '验证会重新检查 repair workspace、controlled bash / CI，以及包中显式声明的 validation proof；不能使用 request 自报 proof。'
              : actionMode === 'submitReview'
                ? '只有验证通过后的 READY_FOR_REVIEW 执行包才能提交人工审核。'
                : actionMode === 'land'
                  ? '生产处置只执行当前已审批方案。AI 会在独立安全执行环境中工作；权限、停止、幂等、审计和结果确认仍由平台统一治理。'
                  : '请确认当前版本、hash 和风险边界。该操作会写入审计。'}
          </Text>
          {(actionMode === 'validate' || actionMode === 'submitReview' || actionMode === 'reject' || actionMode === 'cleanup') && (
            <TextArea
              value={comment}
              placeholder="填写原因或备注"
              autosize={{ minRows: 4, maxRows: 8 }}
              onChange={setComment}
            />
          )}
        </Space>
      </Modal>
    </OpsPageShell>
  );
};
