import React from 'react';
import styled from 'styled-components';
import { Button, Typography } from '@douyinfe/semi-ui';

import { theme } from '../../styles/theme';

const { Text } = Typography;

const EmptyWrap = styled.div`
  min-width: 0;
  padding: 40px 24px;
  border: 1px solid ${theme.colors.border.tertiary};
  border-radius: 12px;
  background: #fafbfc;
  text-align: center;
`;

export interface OpsEmptyStateProps {
  title: React.ReactNode;
  description?: React.ReactNode;
  actionText?: string;
  onAction?: () => void;
}

export const OpsEmptyState: React.FC<OpsEmptyStateProps> = ({ title, description, actionText, onAction }) => (
  <EmptyWrap>
    <Text strong>{title}</Text>
    {description && (
      <Text type="tertiary" size="small" style={{ display: 'block', marginTop: 6 }}>
        {description}
      </Text>
    )}
    {actionText && onAction && (
      <Button theme="solid" style={{ marginTop: 14 }} onClick={onAction}>
        {actionText}
      </Button>
    )}
  </EmptyWrap>
);
