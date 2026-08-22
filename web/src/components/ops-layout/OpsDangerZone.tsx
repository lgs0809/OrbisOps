import React from 'react';
import styled from 'styled-components';
import { Typography } from '@douyinfe/semi-ui';

const { Text } = Typography;

const DangerWrap = styled.div`
  min-width: 0;
  padding: 16px;
  border: 1px solid #fecaca;
  border-radius: 8px;
  background: #fff1f2;
`;

const DangerHeader = styled.div`
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-bottom: 12px;
`;

export interface OpsDangerZoneProps {
  title?: React.ReactNode;
  description?: React.ReactNode;
  children: React.ReactNode;
}

export const OpsDangerZone: React.FC<OpsDangerZoneProps> = ({
  title = 'Dangerous actions',
  description = 'These actions affect production execution, approval, or data availability. Review the details before continuing.',
  children,
}) => (
  <DangerWrap>
    <DangerHeader>
      <Text strong type="danger">{title}</Text>
      {description && <Text type="tertiary" size="small">{description}</Text>}
    </DangerHeader>
    {children}
  </DangerWrap>
);
