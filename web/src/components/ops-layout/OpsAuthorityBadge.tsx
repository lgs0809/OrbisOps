import React from 'react';
import { Tag } from '@douyinfe/semi-ui';

export type OpsAuthority = 'OBSERVE_ONLY' | 'PREPARE_CHANGE' | 'APPROVAL' | 'PROD_FULL';

const authorityPresentation: Record<OpsAuthority, { label: string; marker: string; color: 'blue' | 'amber' | 'green' | 'red' }> = {
  OBSERVE_ONLY: { label: '仅观察', marker: '○', color: 'blue' },
  PREPARE_CHANGE: { label: '可准备变更', marker: '△', color: 'amber' },
  APPROVAL: { label: '可审批', marker: '✓', color: 'green' },
  PROD_FULL: { label: '可执行已批准变更', marker: '◆', color: 'red' },
};

export const OpsAuthorityBadge: React.FC<{ authority: OpsAuthority }> = ({ authority }) => {
  const presentation = authorityPresentation[authority];
  return <Tag color={presentation.color}>{presentation.marker} {presentation.label}</Tag>;
};
