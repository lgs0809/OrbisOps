import { ChangePackageList } from '../features/changes/components/ChangePackageList';
import React, { useEffect, useMemo, useRef, useState } from 'react';
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
  OpsPageShell,
  OpsSectionCard,
  TableScroll,
} from '../components/ops-layout';
import type { OpsChangePackage, OpsLandingOperationRun } from '../services/ops-change-package-types';
import {
  useChangePackageActionMutation,
  useChangePackageDetailQuery,
  useChangePackagesQuery,
} from '../features/changes/api/change-package-queries';
import { useProjectScope } from '../hooks/use-project-scope';
import { getStoredUserInfo, isAdminUser } from '../services/auth-session';
import { ExecutionQueue, PackageCapability, packageCapability, packagesForQueue } from '../features/execution/execution-center-model';
import { ApprovalChannelDispatch } from '../features/changes/components/ApprovalChannelDispatch';
import { ChangeVerificationLauncher } from '../features/changes/components/ChangeVerificationLauncher';
import { LandingPostcheckVerifier } from '../features/changes/components/LandingPostcheckVerifier';
import { ApprovalCockpit } from '../features/changes/components/ApprovalCockpit';
import { operationSafetyGaps } from '../features/changes/model/approval-cockpit-model';
import { theme } from '../styles/theme';
import { userFacingError } from '../utils/user-facing-error';
import {
  changePackageReasonLabel,
  changePackageRiskColor,
  changePackageRiskLabel,
} from './change-package-ui';

const { Option } = Select;
const { Text } = Typography;

const ChangeWorkspace = styled.div`
  max-width: 1480px;
  margin: 0 auto;
`;

const ChangeHeader = styled.header`
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 20px;
  align-items: end;
  padding: 22px 0 24px;

  h1 {
    margin: 0;
    color: ${theme.colors.text.primary};
    font-size: 36px;
    font-weight: 650;
    line-height: 1.1;
    letter-spacing: -0.04em;
  }

  p {
    max-width: 760px;
    margin: 8px 0 0;
    color: ${theme.colors.text.tertiary};
    font-size: 12px;
    line-height: 1.65;
  }

  @media (max-width: ${theme.breakpoints.md}) {
    grid-template-columns: 1fr;
    align-items: start;
  }
`;

const QueuePanel = styled.section`
  overflow: hidden;
  border: 1px solid ${theme.colors.border.secondary};
  border-radius: 13px;
  background: #fff;
`;

const QueueToolbar = styled.div`
  padding: 12px 14px 0;
`;

const QueueBody = styled.div`
  padding: 0 14px 14px;
`;

const Toolbar = styled.div`
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: ${theme.spacing.base};
  width: 100%;
  min-width: 0;
  margin-bottom: ${theme.spacing.base};
`;

const QueueTabs = styled.div`
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
  margin-bottom: 14px;

  @media (max-width: ${theme.breakpoints.md}) {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  @media (max-width: ${theme.breakpoints.sm}) {
    grid-template-columns: 1fr;
  }
`;

const QueueTab = styled.button<{ $active: boolean; $attention?: boolean }>`
  min-width: 0;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
  padding: 11px 13px;
  border: 1px solid ${(props) => (props.$active ? '#cdd5e1' : theme.colors.border.secondary)};
  border-radius: 10px;
  background: ${(props) => (props.$active ? '#f1f3f5' : '#fff')};
  color: ${theme.colors.text.primary};
  text-align: left;
  cursor: pointer;

  &:hover {
    background: #f7f8fa;
  }

  .copy {
    min-width: 0;
  }

  strong {
    display: block;
    font-size: 13px;
    font-weight: 600;
  }

  span {
    display: block;
    margin-top: 2px;
    overflow: hidden;
    color: ${theme.colors.text.tertiary};
    font-size: 11px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .count {
    min-width: 28px;
    color: ${(props) => (props.$attention ? theme.colors.error : theme.colors.text.primary)};
    font-size: 18px;
    font-weight: 650;
    text-align: right;
  }
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
  { value: 'VALIDATING', label: '校验中' },
  { value: 'VALIDATION_FAILED', label: '校验失败' },
  { value: 'READY_FOR_REVIEW', label: '待提交审批' },
  { value: 'REVIEWING', label: '等待审批' },
  { value: 'REVISING', label: '修订中' },
  { value: 'REJECTED', label: '已驳回' },
  { value: 'APPROVED', label: '已批准' },
  { value: 'LANDING', label: '落地中' },
  { value: 'LANDED', label: '已落地' },
  { value: 'NEEDS_REPLAN', label: '需要重规划' },
  { value: 'LANDING_FAILED', label: '落地失败' },
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
  if (parsed?.preferredPlan) return toArray(parsed.preferredPlan);
  // A landing plan/approval boundary is a container, not an executable
  // operation. Do not render the container itself as a fake "未指定工具" row.
  return [];
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
  query: '查询',
  sql: 'SQL',
  namespace: 'Namespace',
  database: '数据库',
  table: '表',
  service: '服务',
  environment: '环境',
  key: '配置项',
  value: '目标值',
  timeoutMs: '超时',
  limit: '数量限制',
};

// Runtime identity is injected by the governed executor after approval. It is
// evidence for the technical drawer, not business input that an approver needs
// to review in the primary operation table.
const runtimeArgumentKeys = new Set([
  'projectid',
  'actor',
  'executionkey',
  'idempotencykey',
  'deadline',
  'executionnotrequested',
  'requireshumanapproval',
  'permissiongranted',
]);

const humanArguments = (value: unknown) => {
  const parsed = parseJsonValue(value);
  if (parsed == null || parsed === '') return '无参数';
  if (Array.isArray(parsed)) return parsed.map((item) => valueText(item)).join('; ');
  if (typeof parsed !== 'object') return String(parsed);
  const entries = Object.entries(parsed as Record<string, unknown>)
    .filter(([key]) => !runtimeArgumentKeys.has(key.replace(/[_-]/g, '').toLowerCase()));
  if (entries.length === 0) return '平台运行参数已绑定';
  return entries
    .slice(0, 6)
    .map(([key, item]) => `${argumentLabels[key] || key}: ${safeArgumentText(key, item)}`)
    .join('; ');
};

const operationStatus = (run: OpsLandingOperationRun) => {
  const status = String(run.status || '').toUpperCase();
  if (status === 'SUCCEEDED') return { label: '成功', color: 'green' as const };
  if (status === 'FAILED') return { label: '失败', color: 'red' as const };
  if (status === 'BLOCKED' || status === 'NEEDS_REPLAN') return { label: '已阻塞', color: 'orange' as const };
  if (status === 'EXECUTING' || status === 'POST_CHECKING' || status === 'PRECONDITION_CHECKING') {
    return { label: '运行中', color: 'blue' as const };
  }
  return { label: status || '待执行', color: 'grey' as const };
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
        tool: valueText(op.toolName || op.tool_name || op.remoteToolName || op.remote_tool_name || op.action || op.type, '未指定工具'),
        resource: valueText(op.resourceKey || op.resource_key || op.resourceScope || op.resource_scope || op.targetObject || op.target_object || op.serviceId || op.service_id, '未指定资源'),
        effect: valueText(op.effectType || op.effect_type || op.mutability || op.riskLevel || op.risk_level, '未指定影响'),
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
  if (['PASSED', 'SUCCEEDED', 'SUCCESS', 'READY'].includes(status)) return { label: '通过', color: 'green' as const };
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

/**
 * The API intentionally keeps the self-approval rejection as the final
 * authority, but the page should not advertise an action that the current
 * principal can never complete.  Compare both stable identifiers because
 * older ChangePackages may have been created before the actor projection was
 * normalized to userId.
 */
const packageCreatedByPrincipal = (
  record: OpsChangePackage | null | undefined,
  principal: ReturnType<typeof getStoredUserInfo>,
) => {
  const creator = String(field(record, 'createBy', 'create_by') || '').trim();
  if (!creator) return false;
  return [principal.userId, principal.username]
    .filter(Boolean)
    .some((identity) => String(identity).trim() === creator);
};

const missingReviewItems = (record?: OpsChangePackage | null) => {
  if (!record) return ['尚未选择 ChangePackage'];
  const items: string[] = [];
  if (record.status === 'VALIDATION_FAILED') items.push('当前方案整体校验失败；部分准备验证通过不代表可以提交审批');
  if (['MANUAL_REQUIRED', 'NEEDS_HUMAN_DESIGN', 'NO_ACTION_REQUIRED'].includes(record.packageType)
      && packageOperations(record).length > 0) items.push('方案类型与执行操作冲突，请重新规划');
  if (!hasContextBundle(record)) items.push('缺少 Runtime Context Bundle；请返回对话重新生成 ChangePackage');
  if (!record.packageHash) items.push('当前版本缺少 packageHash');
  if (!record.riskLevel) items.push('缺少风险级别');
  if (!record.targetEnvironment) items.push('缺少目标环境');
  if (packageOperations(record).length === 0 && record.packageType !== 'NO_ACTION_REQUIRED') items.push('未声明 Landing 操作');
  if (!record.rollbackStepsJson && ['HIGH', 'CRITICAL'].includes(String(record.riskLevel || '').toUpperCase())) items.push('高风险 ChangePackage 缺少回滚方案');
  if (!record.verificationCriteriaJson && record.packageType !== 'NO_ACTION_REQUIRED') items.push('缺少 Landing 后的 Verification 标准');
  return [...items, ...operationSafetyGaps(record)];
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
  const [selectedPackageId, setSelectedPackageId] = useState('');
  const [detailVisible, setDetailVisible] = useState(false);
  const [status, setStatus] = useState('');
  const [queue, setQueue] = useState<ExecutionQueue>('pending');
  const [workView, setWorkView] = useState<'mine' | 'all'>(() => admin ? 'all' : 'mine');
  const projectId = projectScope.projectId;
  const setProjectId = projectScope.selectProject;
  const [actionMode, setActionMode] = useState<'validate' | 'submitReview' | 'approve' | 'reject' | 'land' | 'cleanup' | null>(null);
  const [comment, setComment] = useState('');
  const actionMutation = useChangePackageActionMutation(apiScope);

  const query = useMemo(() => ({
    status: status || undefined,
    projectId: projectId.trim() || undefined,
    incidentId: searchParams.get('incidentId') || undefined,
    scope: apiScope,
    limit: 100,
  }), [apiScope, projectId, searchParams, status]);

  const requestedProjectId = searchParams.get('projectId') || '';
  const waitingForRequestedProject = Boolean(requestedProjectId) && !query.projectId;
  const packageQuery = useChangePackagesQuery({
    principalKey,
    scope: apiScope,
    params: query,
    enabled: Boolean(query.projectId) || (admin && !waitingForRequestedProject),
  });
  const packages = packageQuery.data || [];
  const loading = packageQuery.isFetching;
  const selectedSummary = packages.find((item) => item.packageId === selectedPackageId) || null;
  const detailQuery = useChangePackageDetailQuery(selectedPackageId, apiScope, detailVisible);
  const selected = detailQuery.data?.package || selectedSummary;
  const events = detailQuery.data?.events || [];
  const landingOperations = detailQuery.data?.landingOperations || [];
  const actionLoading = actionMutation.isPending;
  const selectedCreatedByPrincipal = packageCreatedByPrincipal(selected, storedPrincipal);
  const canApproveSelected = packageCapability(selected, 'canApprove') && !selectedCreatedByPrincipal;

  const loadPackages = async () => {
    if (!admin && !query.projectId) return;
    await packageQuery.refetch();
  };

  const openDetail = (record: OpsChangePackage) => {
    setSelectedPackageId(record.packageId);
    setDetailVisible(true);
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

  const submitAction = async () => {
    if (!selected || !actionMode) return;
    const requiredCapability: Record<NonNullable<typeof actionMode>, PackageCapability> = {
      validate: 'canRevise',
      submitReview: 'canSubmitReview',
      approve: 'canApprove',
      reject: 'canReject',
      land: 'canLand',
      cleanup: 'canCleanup',
    };
    if (actionMode === 'approve' && packageCreatedByPrincipal(selected, storedPrincipal)) {
      Toast.warning('当前变更包由你创建，需要另一位审批人完成审批。');
      return;
    }
    if (!packageCapability(selected, requiredCapability[actionMode])) {
      Toast.error('当前 Project 权限不允许执行此操作。');
      return;
    }
    try {
      if (actionMode === 'validate') {
        await actionMutation.mutateAsync({
          kind: 'validate',
          packageId: selected.packageId,
          reason: comment.trim(),
        });
        Toast.success('审批前校验已启动。');
      } else if (actionMode === 'submitReview') {
        await actionMutation.mutateAsync({
          kind: 'submit-review',
          packageId: selected.packageId,
          summary: comment.trim(),
        });
        Toast.success('已提交审批。');
      } else if (actionMode === 'approve') {
        if (!selected.packageHash) {
          Toast.error('当前版本缺少 packageHash，无法审批。');
          return;
        }
        await actionMutation.mutateAsync({
          kind: 'approve',
          packageId: selected.packageId,
          version: selected.version,
          packageHash: selected.packageHash,
        });
        Toast.success('ChangePackage 已批准。');
      } else if (actionMode === 'reject') {
        await actionMutation.mutateAsync({
          kind: 'reject',
          packageId: selected.packageId,
          reason: comment.trim(),
        });
        Toast.success('ChangePackage 已驳回。');
      } else if (actionMode === 'land') {
        const approvedVersion = selected.approvedVersion || selected.version;
        const approvedPackageHash = selected.approvedPackageHash || selected.packageHash || '';
        if (!approvedVersion || !approvedPackageHash) {
          Toast.error('缺少 approvedVersion 或 approvedPackageHash，无法启动 Landing。');
          return;
        }
        // The server may continue after the HTTP request times out. Close the
        // confirmation and follow authoritative detail polling instead of inviting a second click.
        setActionMode(null);
        await actionMutation.mutateAsync({
          kind: 'land',
          packageId: selected.packageId,
          version: approvedVersion,
          packageHash: approvedPackageHash,
          approvedPackageHash,
          ...(selected.status === 'LANDING_FAILED'
            ? { idempotencyKey: `landing-retry-${selected.packageId}-${Date.now()}` }
            : {}),
        });
        Toast.success('Landing 已提交到受治理的生产 Runtime。');
      } else if (actionMode === 'cleanup') {
        await actionMutation.mutateAsync({
          kind: 'cleanup',
          packageId: selected.packageId,
          reason: comment.trim(),
        });
        Toast.success('临时资源清理请求已提交。');
      }
      setActionMode(null);
      setComment('');
    } catch (error) {
      if (actionMode === 'land') {
        Toast.warning('Landing 请求未能确认最终结果，详情会自动刷新。请根据服务端状态判断，勿重复提交。');
      } else {
        Toast.error(userFacingError(error, '操作失败，请稍后重试。'));
      }
    }
  };

  const selectedHash = selected?.approvedPackageHash || selected?.packageHash || '-';
  const returnToPreApproval = (record: OpsChangePackage) => {
    const reason = missingReviewItems(record).join('；')
      || (record.status === 'REJECTED' ? '当前方案已驳回；请核对审核记录并重新准备，旧版本不得执行。' : '')
      || changePackageReasonLabel(record.reasonCode)
      || String(parseJsonValue(record.failureSummaryJson)?.reason || record.reasonCode || 'Landing result requires a new plan');
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
    <OpsPageShell selectedKey="changes">
      <ChangeWorkspace>
        <ChangeHeader>
          <div>
            <h1>变更</h1>
            <p>{admin
              ? '受控变更包从这里进入审批、落地、验证和恢复。列表只呈现需要做决策的业务状态，技术证据在详情中按需展开。'
              : '查看你所在项目的受控变更；可执行动作始终由当前账号权限、审批状态与运行边界共同决定。'}</p>
          </div>
          <Button onClick={loadPackages}>刷新</Button>
        </ChangeHeader>

      <QueueTabs aria-label="变更队列">
        <QueueTab type="button" $active={queue === 'pending'} onClick={() => setQueue('pending')}>
          <div className="copy"><strong>待审批</strong><span>校验、提交审批或等待决策</span></div>
          <div className="count">{queueItems('pending').length}</div>
        </QueueTab>
        <QueueTab type="button" $active={queue === 'running'} onClick={() => setQueue('running')}>
          <div className="copy"><strong>落地</strong><span>已批准待执行或正在执行</span></div>
          <div className="count">{queueItems('running').length}</div>
        </QueueTab>
        <QueueTab type="button" $active={queue === 'intervention'} $attention={queueItems('intervention').length > 0} onClick={() => setQueue('intervention')}>
          <div className="copy"><strong>需要处理</strong><span>{needsReplanCount} 个重规划 · {landingFailedCount} 个落地失败</span></div>
          <div className="count">{queueItems('intervention').length}</div>
        </QueueTab>
        <QueueTab type="button" $active={queue === 'history'} onClick={() => setQueue('history')}>
          <div className="copy"><strong>历史</strong><span>已落地或已关闭的变更</span></div>
          <div className="count">{queueItems('history').length}</div>
        </QueueTab>
      </QueueTabs>

      <QueuePanel>
        <QueueToolbar>
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
              <Option value="all">全部项目变更</Option>
            </Select>
          )}
          <Select value={status} onChange={(value) => setStatus(String(value || ''))} style={{ width: 180 }}>
            {statusOptions.map((item) => (
              <Option key={item.value || 'all'} value={item.value}>{item.label}</Option>
            ))}
          </Select>
          <Text type="tertiary">列表只展示业务状态；打开受控变更包后可查看版本、内容哈希、落地、验证与审计证据。</Text>
        </Toolbar>
        </QueueToolbar>
        <QueueBody>
        <Spin spinning={loading}>
          {packageQuery.isError ? (
            <OpsEmptyState
              title="加载受控变更包失败"
              description={userFacingError(packageQuery.error, '加载变更列表失败，请刷新后重试。')}
            />
          ) : visiblePackages.length === 0 ? (
            <Empty title="当前队列暂无受控变更包" description="可切换待审批、落地、需要人工处理和历史队列查看其他变更。" />
          ) : (
            <ChangePackageList packages={visiblePackages} projects={projectScope.projects} onOpen={openDetail} />
          )}
        </Spin>
        </QueueBody>
      </QueuePanel>
      </ChangeWorkspace>

      <Modal
        title={selected ? `受控变更包 - ${selected.objective || selected.summary || selected.packageId}` : '受控变更包'}
        visible={detailVisible}
        footer={null}
        width={1120}
        style={{ maxWidth: '96vw' }}
        onCancel={() => setDetailVisible(false)}
      >
        <Spin spinning={detailQuery.isFetching}>
          {detailQuery.isError ? (
            <OpsEmptyState
              title="加载受控变更包失败"
              description={userFacingError(detailQuery.error, '加载变更详情失败，请刷新后重试。')}
            />
          ) : selected && detailQuery.data?.package?.packageId === selectedPackageId ? (
        <div style={{ maxHeight: '72vh', overflow: 'auto', paddingRight: 8 }}>
        <DetailGrid>
          <OpsSectionCard title="审批工作台">
            <ApprovalCockpit record={selected} />
            <ChangeVerificationLauncher key={selected.packageId} record={selected} />
            <ApprovalChannelDispatch
              record={selected}
              scope={apiScope}
              canApprove={canApproveSelected}
            />
            {selectedCreatedByPrincipal && packageCapability(selected, 'canApprove') && (
              <Text type="tertiary">当前变更包由你创建，需要另一位具备审批权限的成员完成审批。</Text>
            )}
            {selectedMissingItems.length > 0 && (
              <OpsDangerZone
                title="审批证据不完整"
                description="ChangePackage 可见不代表可以直接批准；请先补齐以下缺口。"
              >
                <Space vertical align="start" spacing={4}>
                  {selectedMissingItems.map((item) => <Text key={item}>- {item}</Text>)}
                </Space>
              </OpsDangerZone>
            )}
            <OpsAdvancedPreview title="审批版本与 Runtime 身份" description="用于审计和调试的技术细节，不是变更的主要产品视图。">
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

            <OpsSectionCard title="批准前确认：Landing 将执行什么" style={{ marginTop: 16 }}>
              <Space vertical align="start" spacing="medium" style={{ width: '100%' }}>
                <Text>
                  当前 ChangePackage 的目标环境是 <Text strong>{selected.targetEnvironment || '未指定环境'}</Text>，风险级别为{' '}
                  <Tag color={riskColor(selected.riskLevel)}>{changePackageRiskLabel(selected.riskLevel)}</Tag>。
                  批准后，Landing 只能在已批准边界内执行；任何实质性方案变化都必须重新规划并重新审批。
                </Text>
                <Space wrap>
                  <Tag color={proofState(selected.preflightResultJson).color}>Preflight：{proofState(selected.preflightResultJson).label}</Tag>
                  {selected.dryRunResultJson && (
                    <Tag color={proofState(selected.dryRunResultJson).color}>准备验证：{proofState(selected.dryRunResultJson).label}</Tag>
                  )}
                  {selected.ciResultJson && (
                    <Tag color={proofState(selected.ciResultJson).color}>CI：{proofState(selected.ciResultJson).label}</Tag>
                  )}
                  {selected.testProofHash && <Tag color="green">测试证据：{shortHash(selected.testProofHash)}</Tag>}
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
                      { title: '影响 / 风险', dataIndex: 'effect', width: 160 },
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
                  <OpsEmptyState title="没有可执行的 Landing 步骤" description="当前 ChangePackage 没有声明生产操作。请在审批前修订；如果方案已失效，则返回对话重新规划。" />
                )}
                <Space vertical align="start" spacing={2}>
                  <Text strong>回滚 / Verification</Text>
                  <Text type="tertiary">回滚方案：{selected.rollbackStepsJson ? '已记录' : '缺失'}；Verification：{selected.verificationCriteriaJson ? '已记录' : '缺失'}；允许的 Landing 调整：{selected.allowedLandingAdjustmentsJson ? '已声明' : '未声明'}。</Text>
                </Space>
                {selectedMissingItems.length > 0 ? (
                  <OpsDangerZone
                    title="审批或 Landing 已阻塞"
                    description="以下缺口会阻止安全审批或执行。如果方案已经失效，请返回对话重新规划，并审批新版本。"
                  >
                    <Space vertical align="start" spacing={4}>
                      {selectedMissingItems.map((item) => <Text key={item}>- {item}</Text>)}
                    </Space>
                  </OpsDangerZone>
                ) : (
                  <Tag color="green">审批证据完整：下一步状态允许的操作已可执行</Tag>
                )}
              </Space>
            </OpsSectionCard>

            <Space vertical align="start" spacing="medium" style={{ width: '100%', marginTop: 16 }}>
              <OpsAdvancedPreview title="MCP 步骤 / Preflight / 显式校验" description="这些字段属于审批快照和 packageHash；Landing 只能读取已批准版本。">
                <JsonBlock>{formatJson({
                  toolBindingsJson: selected.toolBindingsJson,
                  mcpStepsJson: selected.mcpStepsJson,
                  preflightResultJson: selected.preflightResultJson,
                  validationResultJson: selected.dryRunResultJson,
                })}</JsonBlock>
              </OpsAdvancedPreview>
              <OpsAdvancedPreview title="代码修复 / Bash / LSP 证据" description="代码修复事实属于审批快照；Landing 只能执行 approvedVersion 中记录的 repairCommit、diffHash 和测试证据。">
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
              <OpsAdvancedPreview title="审批边界 / Landing 方案 / 回滚 / Verification" description="受治理的 Landing Runtime 会在执行前检查这些已批准边界。">
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
            <OpsSectionCard title="Landing 步骤">
              {landingOperations.length === 0 ? (
                <Empty title="Landing 尚未开始" description="审批完成并启动受治理执行后，这里会展示每个操作的前置条件、执行过程和后置检查结果。" />
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
                        title: '结果',
                        dataIndex: 'reasonCode',
                        render: (value: string, run: OpsLandingOperationRun) => (
                          <Space vertical align="start" spacing={2}>
                            <Text>{changePackageReasonLabel(value) || value || (run.status === 'SUCCEEDED' ? '前置条件、执行和后置检查均已通过' : '-')}</Text>
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
                <OpsAdvancedPreview title="操作级技术记录" description="用于故障排查；审批决策应以上方人类可读状态为准。">
                  <JsonBlock>{formatJson(landingOperations)}</JsonBlock>
                </OpsAdvancedPreview>
              )}
            </OpsSectionCard>

            <OpsSectionCard title="事件轨迹">
              {events.length === 0 ? (
                <Empty title="暂无事件" description="审批、Landing、阻塞、清理及相关操作都会记录在这里。" />
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
                        <OpsAdvancedPreview title="事件 Payload" description="默认折叠，避免原始 Payload 干扰主要业务视图。">
                          <JsonBlock>{formatJson(event.payloadJson)}</JsonBlock>
                        </OpsAdvancedPreview>
                      </Space>
                    </EventItem>
                  ))}
                </EventList>
              )}
            </OpsSectionCard>

            <OpsDangerZone
              title="受治理的 ChangePackage 操作"
              description="校验、提交审批、批准、驳回、Landing 和清理都会进入审计。生产执行只能使用当前已批准方案；实质性变更必须重新审批。"
            >
              <Space wrap>
                {packageCapability(selected, 'canRevise') && (
                  <Button
                    disabled={!['DRAFT', 'VALIDATION_FAILED', 'REVISING'].includes(selected.status)}
                    onClick={() => setActionMode('validate')}
                  >
                    执行审批前校验
                  </Button>
                )}
                {packageCapability(selected, 'canSubmitReview') && (
                  <Button
                    disabled={selected.status !== 'READY_FOR_REVIEW'}
                    onClick={() => setActionMode('submitReview')}
                  >
                    提交审批
                  </Button>
                )}
                {canApproveSelected && (
                  <Button
                    type="primary"
                    disabled={selected.status !== 'REVIEWING' || !hasPackageHash(selected) || !selectedReadyForApproval}
                    onClick={() => setActionMode('approve')}
                  >
                    批准当前版本
                  </Button>
                )}
                {packageCapability(selected, 'canReject') && (
                  <Button
                    type="danger"
                    theme="borderless"
                    disabled={selected.status !== 'REVIEWING'}
                    onClick={() => setActionMode('reject')}
                  >
                    驳回并要求修订
                  </Button>
                )}
                {packageCapability(selected, 'canLand') && (
                  <Button
                    disabled={
                      !['APPROVED', 'LANDING_FAILED'].includes(selected.status)
                      || !selected.approvedPackageHash
                      || !hasContextBundle(selected)
                      || !selectedReadyForApproval
                    }
                    onClick={() => setActionMode('land')}
                  >
                    {selected.status === 'LANDING_FAILED' ? '重试 Landing' : '启动 Landing'}
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
                {packageCapability(selected, 'canLand') && ['LANDED', 'LANDING_FAILED'].includes(selected.status) && (
                  <LandingPostcheckVerifier key={selected.packageId} packageId={selected.packageId} scope={apiScope} />
                )}
                {packageCapability(selected, 'canRevise') && (
                  <Button
                    type="primary"
                    disabled={!['NEEDS_REPLAN', 'LANDING_FAILED', 'REJECTED', 'VALIDATION_FAILED'].includes(selected.status)}
                    onClick={() => returnToPreApproval(selected)}
                  >
                    返回对话并重新规划
                  </Button>
                )}
                {!['canRevise', 'canSubmitReview', 'canReject', 'canLand', 'canCleanup']
                  .some((capability) => packageCapability(selected, capability as PackageCapability))
                  && !canApproveSelected && (
                  <Tag color="grey">当前 Project 权限仅允许查看</Tag>
                )}
              </Space>
            </OpsDangerZone>
          </Space>
        </DetailGrid>
        </div>
          ) : (
            <Empty title="请选择一个 ChangePackage 查看完整详情" />
          )}
        </Spin>
      </Modal>

      <Modal
        title={
          actionMode === 'validate' ? '执行审批前校验'
            : actionMode === 'submitReview' ? '提交审批'
              : actionMode === 'approve' ? '批准 ChangePackage'
                : actionMode === 'reject' ? '驳回 ChangePackage'
                  : actionMode === 'land'
                    ? selected?.status === 'LANDING_FAILED' ? '重试 Landing' : '启动 Landing'
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
              ? '校验会重新检查修复工作区、受控 Bash / CI 以及显式声明的验证证据；不会信任请求方自行提供的证明。'
              : actionMode === 'submitReview'
                ? '只有已经校验并处于 READY_FOR_REVIEW 的 ChangePackage 才能提交审批。'
                : actionMode === 'land'
                  ? selected?.status === 'LANDING_FAILED'
                    ? '上次 Landing 未派发任何操作；将使用新的幂等键重试同一已批准方案。平台仍会重新校验权限、停止控制、审计和结果 Verification。'
                    : 'Landing 只执行当前已批准方案。平台仍持续治理权限、停止控制、幂等、审计和结果 Verification。'
                  : '请确认当前版本、hash 与风险边界；本次操作会进入审计。'}
          </Text>
          {(actionMode === 'validate' || actionMode === 'submitReview' || actionMode === 'reject' || actionMode === 'cleanup') && (
            <TextArea
              value={comment}
              placeholder="原因或备注"
              autosize={{ minRows: 4, maxRows: 8 }}
              onChange={setComment}
            />
          )}
        </Space>
      </Modal>
    </OpsPageShell>
  );
};
