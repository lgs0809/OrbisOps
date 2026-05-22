import React, { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Banner, Button, Popconfirm, Space, Tag, Toast, Typography } from '@douyinfe/semi-ui';

import { OpsAuthorityBadge, OpsSectionCard } from '../../../components/ops-layout';
import { opsWorkflowApprovalService } from '../../../services/ops-workflow-approval-service';
import type {
  OpsWorkflowApprovalScope,
  OpsWorkflowApprovalView,
} from '../../../services/ops-workflow-approval-service';
import { userFacingError } from '../../../utils/user-facing-error';
import { ApiRequestError } from '../../../services/ops-http-client';

const { Text } = Typography;

interface Props {
  runId: string;
  projectId: string;
  scope: OpsWorkflowApprovalScope;
  onChanged?: (view: OpsWorkflowApprovalView) => void;
}

const queryKey = (scope: OpsWorkflowApprovalScope, projectId: string, runId: string) =>
  ['workflow-approval', scope, projectId, runId] as const;

const approvalStatusLabel = (status?: string) => ({
  WAITING: '等待审批',
  APPROVED: '已批准',
  REJECTED: '已驳回',
  EXPIRED: '已过期',
}[String(status || '').toUpperCase()] || '状态未知');

export const WorkflowApprovalPanel: React.FC<Props> = ({ runId, projectId, scope, onChanged }) => {
  const queryClient = useQueryClient();
  const [reviewedApprovalId, setReviewedApprovalId] = useState('');
  const approvalQuery = useQuery({
    queryKey: queryKey(scope, projectId, runId),
    enabled: Boolean(runId && projectId),
    queryFn: async () => (await opsWorkflowApprovalService.get(runId, projectId, scope)).data,
    staleTime: 3_000,
    refetchOnWindowFocus: false,
    refetchInterval: (query) => query.state.data?.runStatus === 'WAITING_APPROVAL' ? 5_000 : false,
  });

  const updateView = (view?: OpsWorkflowApprovalView) => {
    if (!view) return;
    queryClient.setQueryData(queryKey(scope, projectId, runId), view);
    onChanged?.(view);
  };

  const decisionMutation = useMutation({
    mutationFn: async (decision: 'APPROVE' | 'REJECT') =>
      (await opsWorkflowApprovalService.decide(runId, projectId, decision, reviewedApprovalId, scope)).data,
    onSuccess: (view, decision) => {
      updateView(view);
      Toast.success(decision === 'APPROVE'
        ? '审批已记录，Workflow 正在继续执行。'
        : '驳回已记录，Workflow 将按已配置的分支继续处理。');
    },
    onError: (error) => Toast.error(userFacingError(error, 'Workflow 审批失败，请稍后重试。')),
  });

  const resumeMutation = useMutation({
    mutationFn: async () => (await opsWorkflowApprovalService.retryResume(runId, projectId, approvalQuery.data?.approvalId || '', scope)).data,
    onSuccess: (view) => {
      updateView(view);
      Toast.success('Workflow 已重新调度继续执行。');
    },
    onError: (error) => Toast.error(userFacingError(error, 'Workflow 恢复重试失败，请稍后重试。')),
  });

  if (approvalQuery.isLoading) return null;
  if (approvalQuery.isError) {
    if (approvalQuery.error instanceof ApiRequestError && approvalQuery.error.status === 403) {
      return <Banner type="info" description="当前账号无权查看此运行的 Workflow 审批记录。运行结果可按项目权限查看。" />;
    }
    return <Banner type="warning" description="Workflow 审批状态暂时不可用，请稍后重试。" />;
  }
  const approval = approvalQuery.data;
  if (!approval?.available) return null;

  const waiting = approval.status === 'WAITING';
  const terminal = approval.status === 'APPROVED' || approval.status === 'REJECTED';
  const busy = decisionMutation.isPending || resumeMutation.isPending;

  return (
    <OpsSectionCard title="Workflow 人工审批">
      <Space vertical align="start" spacing="medium" style={{ width: '100%' }}>
        <Space wrap align="center">
          <OpsAuthorityBadge authority={approval.canDecide || approval.resumeRequired ? 'APPROVAL' : 'OBSERVE_ONLY'} />
          <Tag color={waiting ? 'amber' : approval.status === 'APPROVED' ? 'green' : approval.status === 'REJECTED' ? 'red' : 'grey'}>
            {approvalStatusLabel(approval.status)}
          </Tag>
          {approval.channelBound && <Tag color="blue">已关联 Channel</Tag>}
          <Text type="tertiary">节点 {approval.nodeId || '-'}</Text>
        </Space>

        <div style={{ width: '100%' }}>
          <Text strong>审批请求</Text>
          <div style={{ marginTop: 6, whiteSpace: 'pre-wrap', lineHeight: 1.6 }}>
            {approval.requestSummary || 'Workflow 需要人工确认后才能继续执行。'}
          </div>
        </div>

        <Space wrap>
          <Text type="tertiary">申请时间 {approval.requestedAt || '-'}</Text>
          <Text type="tertiary">有效期至 {approval.expiresAt || '-'}</Text>
          {approval.decidedBy && <Text type="tertiary">处理人 {approval.decidedBy}</Text>}
        </Space>

        {waiting && approval.canDecide && (
          <Space wrap>
            <Popconfirm
              title="批准这个 Workflow 步骤？"
              content="确认后会记录人工决策并继续执行，但不会绕过后续权限和安全控制。"
              onVisibleChange={(visible) => { if (visible) setReviewedApprovalId(approval.approvalId || ''); }}
              onConfirm={() => decisionMutation.mutate('APPROVE')}
            >
              <Button type="primary" loading={decisionMutation.isPending} disabled={busy}>批准并继续</Button>
            </Popconfirm>
            <Popconfirm
              title="驳回这个 Workflow 步骤？"
              content="确认后会记录驳回结果，Workflow 将按已配置的条件分支继续处理。"
              onVisibleChange={(visible) => { if (visible) setReviewedApprovalId(approval.approvalId || ''); }}
              onConfirm={() => decisionMutation.mutate('REJECT')}
            >
              <Button type="danger" disabled={busy}>驳回</Button>
            </Popconfirm>
          </Space>
        )}

        {waiting && !approval.canDecide && (
          <Banner
            type="info"
            description="你可以查看这条审批，但只有当前 Work Session 的负责人或编辑者可以处理。"
          />
        )}

        {terminal && approval.resumeRequired && (
          <Banner
            type="warning"
            description={
              <Space wrap>
                <span>审批结果已经记录，但外层 Work Session 仍在等待。重新调度只会继续执行，不会改变已有审批结果。</span>
                <Button loading={resumeMutation.isPending} disabled={busy} onClick={() => resumeMutation.mutate()}>
                  重新调度继续执行
                </Button>
              </Space>
            }
          />
        )}
      </Space>
    </OpsSectionCard>
  );
};
