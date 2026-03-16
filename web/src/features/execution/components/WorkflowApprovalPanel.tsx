import React from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Banner, Button, Popconfirm, Space, Tag, Toast, Typography } from '@douyinfe/semi-ui';

import { OpsAuthorityBadge, OpsSectionCard } from '../../../components/ops-layout';
import { opsWorkflowApprovalService } from '../../../services/ops-workflow-approval-service';
import type {
  OpsWorkflowApprovalScope,
  OpsWorkflowApprovalView,
} from '../../../services/ops-workflow-approval-service';

const { Text } = Typography;

interface Props {
  runId: string;
  projectId: string;
  scope: OpsWorkflowApprovalScope;
  onChanged?: (view: OpsWorkflowApprovalView) => void;
}

const queryKey = (scope: OpsWorkflowApprovalScope, projectId: string, runId: string) =>
  ['workflow-approval', scope, projectId, runId] as const;

export const WorkflowApprovalPanel: React.FC<Props> = ({ runId, projectId, scope, onChanged }) => {
  const queryClient = useQueryClient();
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
      (await opsWorkflowApprovalService.decide(runId, projectId, decision, scope)).data,
    onSuccess: (view, decision) => {
      updateView(view);
      Toast.success(decision === 'APPROVE'
        ? '审批已记录，Workflow 正在从 durable checkpoint 恢复'
        : '驳回已记录，Workflow 正在从 durable checkpoint 恢复');
    },
    onError: (error) => Toast.error(error instanceof Error ? error.message : 'Workflow 审批失败'),
  });

  const resumeMutation = useMutation({
    mutationFn: async () => (await opsWorkflowApprovalService.retryResume(runId, projectId, scope)).data,
    onSuccess: (view) => {
      updateView(view);
      Toast.success('已重新调度 Workflow approval resume');
    },
    onError: (error) => Toast.error(error instanceof Error ? error.message : 'Workflow 恢复重试失败'),
  });

  if (approvalQuery.isLoading) return null;
  if (approvalQuery.isError) {
    return <Banner type="warning" description="Workflow approval 状态暂时不可用，请稍后重试。" />;
  }
  const approval = approvalQuery.data;
  if (!approval?.available) return null;

  const waiting = approval.status === 'WAITING';
  const terminal = approval.status === 'APPROVED' || approval.status === 'REJECTED';
  const busy = decisionMutation.isPending || resumeMutation.isPending;

  return (
    <OpsSectionCard title="Workflow Approval">
      <Space vertical align="start" spacing="medium" style={{ width: '100%' }}>
        <Space wrap align="center">
          <OpsAuthorityBadge authority="APPROVAL" />
          <Tag color={waiting ? 'amber' : approval.status === 'APPROVED' ? 'green' : approval.status === 'REJECTED' ? 'red' : 'grey'}>
            {approval.status || 'UNKNOWN'}
          </Tag>
          {approval.channelBound && <Tag color="blue">Channel linked</Tag>}
          <Text type="tertiary">Node {approval.nodeId || '-'}</Text>
        </Space>

        <div style={{ width: '100%' }}>
          <Text strong>Approval request</Text>
          <div style={{ marginTop: 6, whiteSpace: 'pre-wrap', lineHeight: 1.6 }}>
            {approval.requestSummary || 'Workflow requests human approval before continuing.'}
          </div>
        </div>

        <Space wrap>
          <Text type="tertiary">Requested {approval.requestedAt || '-'}</Text>
          <Text type="tertiary">Expires {approval.expiresAt || '-'}</Text>
          {approval.decidedBy && <Text type="tertiary">Decided by {approval.decidedBy}</Text>}
        </Space>

        {waiting && approval.canDecide && (
          <Space wrap>
            <Popconfirm
              title="Approve this workflow step?"
              content="This records the human decision and resumes the durable workflow. It does not bypass downstream runtime permissions."
              onConfirm={() => decisionMutation.mutate('APPROVE')}
            >
              <Button type="primary" loading={decisionMutation.isPending} disabled={busy}>Approve & Resume</Button>
            </Popconfirm>
            <Popconfirm
              title="Reject this workflow step?"
              content="The workflow will resume with decision=REJECTED so its configured conditional route can handle the rejection."
              onConfirm={() => decisionMutation.mutate('REJECT')}
            >
              <Button type="danger" disabled={busy}>Reject</Button>
            </Popconfirm>
          </Space>
        )}

        {waiting && !approval.canDecide && (
          <Banner
            type="info"
            description="You can inspect this approval, but only the current Work Session owner/editor can decide it."
          />
        )}

        {terminal && approval.resumeRequired && (
          <Banner
            type="warning"
            description={
              <Space wrap>
                <span>The decision is committed, but the outer Work Session is still waiting. Retry only schedules resume; it cannot change the decision.</span>
                <Button loading={resumeMutation.isPending} disabled={busy} onClick={() => resumeMutation.mutate()}>
                  Retry Resume
                </Button>
              </Space>
            }
          />
        )}
      </Space>
    </OpsSectionCard>
  );
};
