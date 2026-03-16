import React from 'react';
import { Tag } from '@douyinfe/semi-ui';

export type OpsRiskLevel = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';

const riskPresentation: Record<OpsRiskLevel, { marker: string; color: 'green' | 'blue' | 'amber' | 'red' }> = {
  LOW: { marker: 'L', color: 'green' },
  MEDIUM: { marker: 'M', color: 'blue' },
  HIGH: { marker: 'H', color: 'amber' },
  CRITICAL: { marker: 'C', color: 'red' },
};

export const OpsRiskBadge: React.FC<{ level: OpsRiskLevel }> = ({ level }) => {
  const presentation = riskPresentation[level];
  return <Tag color={presentation.color}>{presentation.marker} · {level}</Tag>;
};
