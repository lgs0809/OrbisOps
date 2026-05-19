import React from 'react';
import { Tag } from '@douyinfe/semi-ui';

export type OpsSemanticStatus =
  | 'OBSERVE'
  | 'EVIDENCE'
  | 'REASONING'
  | 'PROPOSED_CHANGE'
  | 'AWAITING_APPROVAL'
  | 'APPROVED'
  | 'LANDING'
  | 'VERIFIED'
  | 'BLOCKED'
  | 'FAILED';

const statusPresentation: Record<OpsSemanticStatus, { label: string; marker: string; color: 'blue' | 'cyan' | 'violet' | 'amber' | 'green' | 'red' }> = {
  OBSERVE: { label: '观察中', marker: '○', color: 'blue' },
  EVIDENCE: { label: '证据', marker: '◆', color: 'cyan' },
  REASONING: { label: '分析中', marker: '◇', color: 'violet' },
  PROPOSED_CHANGE: { label: '变更待确认', marker: '△', color: 'amber' },
  AWAITING_APPROVAL: { label: '等待审批', marker: '◷', color: 'amber' },
  APPROVED: { label: '已批准', marker: '✓', color: 'green' },
  LANDING: { label: '执行中', marker: '▶', color: 'blue' },
  VERIFIED: { label: '已验证', marker: '✓', color: 'green' },
  BLOCKED: { label: '已阻塞', marker: '■', color: 'red' },
  FAILED: { label: '失败', marker: '×', color: 'red' },
};

export interface OpsStatusBadgeProps {
  status: OpsSemanticStatus;
  label?: string;
}

export const OpsStatusBadge: React.FC<OpsStatusBadgeProps> = ({ status, label }) => {
  const presentation = statusPresentation[status];
  return <Tag color={presentation.color}>{presentation.marker} {label || presentation.label}</Tag>;
};
