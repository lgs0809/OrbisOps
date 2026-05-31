import React from 'react';
import { Typography } from '@douyinfe/semi-ui';
import styled from 'styled-components';

import { OpsAuthorityBadge, OpsRiskBadge, OpsStatusBadge } from '../../../components/ops-layout';
import type { OpsChangePackage } from '../../../services/ops-change-package-types';
import { theme } from '../../../styles/theme';
import { approvalCockpitView } from '../model/approval-cockpit-model';

const { Text } = Typography;

const Grid = styled.div`
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: ${theme.spacing.base};

  @media (max-width: 760px) { grid-template-columns: 1fr; }
`;

const Cell = styled.div`
  min-width: 0;
  padding: ${theme.spacing.base};
  border: 1px solid ${theme.colors.border.secondary};
  border-radius: ${theme.borderRadius.base};
  background: ${theme.colors.bg.secondary};
  overflow-wrap: anywhere;
`;

const label: Record<keyof ReturnType<typeof approvalCockpitView>, string> = {
  why: 'WHY · 为什么要改',
  what: 'WHAT · 改什么',
  where: 'WHERE · 在哪里改',
  evidence: 'EVIDENCE · 依据什么',
  risk: 'RISK · 风险',
  blastRadius: 'BLAST RADIUS · 影响范围',
  rollback: 'ROLLBACK · 如何回退',
  verify: 'VERIFY · 如何验证',
};

export const ApprovalCockpit: React.FC<{ record: OpsChangePackage }> = ({ record }) => {
  const view = approvalCockpitView(record);
  const cells: Array<keyof typeof view> = ['why', 'what', 'where', 'evidence', 'risk', 'blastRadius', 'rollback', 'verify'];
  return (
    <div>
      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8, marginBottom: 12 }}>
        <OpsStatusBadge
          status={record.status === 'REVIEWING' ? 'AWAITING_APPROVAL' : record.status === 'APPROVED' ? 'APPROVED' : record.status === 'LANDING' ? 'LANDING' : record.status === 'LANDED' ? 'VERIFIED' : 'PROPOSED_CHANGE'}
          label={record.status}
        />
        <OpsAuthorityBadge authority={record.status === 'APPROVED' || record.status === 'LANDING' || record.status === 'LANDED' ? 'APPROVAL' : 'PREPARE_CHANGE'} />
        <OpsRiskBadge level={view.risk} />
      </div>
      <Grid data-testid="approval-cockpit">
        {cells.map((key) => (
          <Cell key={key}>
            <Text type="tertiary" size="small">{label[key]}</Text>
            <div style={{ marginTop: 6 }}><Text strong>{view[key]}</Text></div>
          </Cell>
        ))}
      </Grid>
    </div>
  );
};
