import React from 'react';
import { Tag } from '@douyinfe/semi-ui';

export type OpsAuthority = 'OBSERVE_ONLY' | 'PREPARE_CHANGE' | 'APPROVAL' | 'PROD_FULL';

const authorityPresentation: Record<OpsAuthority, { label: string; marker: string; color: 'blue' | 'amber' | 'green' | 'red' }> = {
  OBSERVE_ONLY: { label: 'Observe only', marker: '○', color: 'blue' },
  PREPARE_CHANGE: { label: 'Prepare change', marker: '△', color: 'amber' },
  APPROVAL: { label: 'Approval authority', marker: '✓', color: 'green' },
  PROD_FULL: { label: 'Trusted landing', marker: '◆', color: 'red' },
};

export const OpsAuthorityBadge: React.FC<{ authority: OpsAuthority }> = ({ authority }) => {
  const presentation = authorityPresentation[authority];
  return <Tag color={presentation.color}>{presentation.marker} {presentation.label}</Tag>;
};
