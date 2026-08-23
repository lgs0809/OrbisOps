import React from 'react';
import styled from 'styled-components';
import { Typography } from '@douyinfe/semi-ui';

import { theme } from '../../styles/theme';

const { Text, Title } = Typography;

const CardWrap = styled.div`
  min-width: 0;
  padding: 15px 16px;
  border: 1px solid ${theme.colors.border.secondary};
  border-radius: 12px;
  background: #fff;
`;

export interface OpsStatusCardProps {
  label: React.ReactNode;
  value: React.ReactNode;
  description?: React.ReactNode;
  suffix?: React.ReactNode;
}

export const OpsStatusCard: React.FC<OpsStatusCardProps> = ({ label, value, description, suffix }) => (
  <CardWrap>
    <Text type="tertiary" size="small">{label}</Text>
    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 12, minWidth: 0 }}>
      <Title heading={4} style={{ margin: '4px 0 0' }}>{value}</Title>
      {suffix}
    </div>
    {description && (
      <Text type="tertiary" size="small" style={{ display: 'block', marginTop: 6 }}>
        {description}
      </Text>
    )}
  </CardWrap>
);
