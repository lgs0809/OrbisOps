import React from 'react';
import { Tag } from '@douyinfe/semi-ui';

export type OpsRiskLevel = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';

const riskPresentation: Record<OpsRiskLevel, { label: string; marker: string; color: 'green' | 'blue' | 'amber' | 'red' }> = {
  LOW: { label: '低风险', marker: 'L', color: 'green' },
  MEDIUM: { label: '中风险', marker: 'M', color: 'blue' },
  HIGH: { label: '高风险', marker: 'H', color: 'amber' },
  CRITICAL: { label: '严重风险', marker: 'C', color: 'red' },
};

export const OpsRiskBadge: React.FC<{ level: OpsRiskLevel }> = ({ level }) => {
  const presentation = riskPresentation[level];
  return <Tag color={presentation.color}>{presentation.marker} · {presentation.label}</Tag>;
};
