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
  OBSERVE: { label: 'Observe', marker: '○', color: 'blue' },
  EVIDENCE: { label: 'Evidence', marker: '◆', color: 'cyan' },
  REASONING: { label: 'Reasoning', marker: '◇', color: 'violet' },
  PROPOSED_CHANGE: { label: 'Proposed change', marker: '△', color: 'amber' },
  AWAITING_APPROVAL: { label: 'Awaiting approval', marker: '◷', color: 'amber' },
  APPROVED: { label: 'Approved', marker: '✓', color: 'green' },
  LANDING: { label: 'Landing', marker: '▶', color: 'blue' },
  VERIFIED: { label: 'Verified', marker: '✓', color: 'green' },
  BLOCKED: { label: 'Blocked', marker: '■', color: 'red' },
  FAILED: { label: 'Failed', marker: '×', color: 'red' },
};

export interface OpsStatusBadgeProps {
  status: OpsSemanticStatus;
  label?: string;
}

export const OpsStatusBadge: React.FC<OpsStatusBadgeProps> = ({ status, label }) => {
  const presentation = statusPresentation[status];
  return <Tag color={presentation.color}>{presentation.marker} {label || presentation.label}</Tag>;
};
