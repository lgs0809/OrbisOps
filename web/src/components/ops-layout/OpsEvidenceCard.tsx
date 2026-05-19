import React from 'react';
import { Typography } from '@douyinfe/semi-ui';
import styled from 'styled-components';

import { theme } from '../../styles/theme';
import { OpsStatusBadge } from './OpsStatusBadge';

const { Text } = Typography;

const Wrap = styled.div`
  padding: ${theme.spacing.base};
  border: 1px solid ${theme.colors.border.secondary};
  border-radius: ${theme.borderRadius.base};
  background: ${theme.colors.bg.primary};
`;

export interface OpsEvidenceCardProps {
  title: React.ReactNode;
  summary: React.ReactNode;
  source?: React.ReactNode;
  observedAt?: React.ReactNode;
  footer?: React.ReactNode;
}

export const OpsEvidenceCard: React.FC<OpsEvidenceCardProps> = ({ title, summary, source, observedAt, footer }) => (
  <Wrap>
    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 12 }}>
      <Text strong>{title}</Text>
      <OpsStatusBadge status="EVIDENCE" />
    </div>
    <div style={{ marginTop: 8 }}>{summary}</div>
    {(source || observedAt) && (
      <Text type="tertiary" size="small" style={{ display: 'block', marginTop: 8 }}>
        {[source, observedAt].filter(Boolean).map((item, index) => <React.Fragment key={index}>{index > 0 ? ' · ' : ''}{item}</React.Fragment>)}
      </Text>
    )}
    {footer && <div style={{ marginTop: 10 }}>{footer}</div>}
  </Wrap>
);
