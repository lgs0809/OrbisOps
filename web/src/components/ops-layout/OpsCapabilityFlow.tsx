import React from 'react';
import styled from 'styled-components';
import { Tag, Typography } from '@douyinfe/semi-ui';

import { theme } from '../../styles/theme';

const { Text } = Typography;

const FlowWrap = styled.div`
  display: flex;
  align-items: stretch;
  gap: ${theme.spacing.sm};
  flex-wrap: wrap;
  min-width: 0;
`;

const Step = styled.div`
  min-width: 160px;
  flex: 1 1 160px;
  padding: ${theme.spacing.base};
  border: 1px solid ${theme.colors.border.secondary};
  border-radius: ${theme.borderRadius.base};
  background: ${theme.colors.bg.primary};
`;

export interface OpsCapabilityFlowItem {
  title: string;
  description?: string;
  status?: 'done' | 'current' | 'pending' | 'blocked';
}

const colorOf = (status?: OpsCapabilityFlowItem['status']) => {
  if (status === 'done') return 'green';
  if (status === 'current') return 'blue';
  if (status === 'blocked') return 'red';
  return 'grey';
};

export const OpsCapabilityFlow: React.FC<{ items: OpsCapabilityFlowItem[] }> = ({ items }) => (
  <FlowWrap>
    {items.map((item, index) => (
      <Step key={`${item.title}-${index}`}>
        <Tag color={colorOf(item.status)}>{index + 1}</Tag>
        <Text strong style={{ display: 'block', marginTop: 8 }}>{item.title}</Text>
        {item.description && (
          <Text type="tertiary" size="small" style={{ display: 'block', marginTop: 4 }}>
            {item.description}
          </Text>
        )}
      </Step>
    ))}
  </FlowWrap>
);
