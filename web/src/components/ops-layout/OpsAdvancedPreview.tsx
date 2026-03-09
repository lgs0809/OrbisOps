import React from 'react';
import styled from 'styled-components';
import { Typography } from '@douyinfe/semi-ui';

import { theme } from '../../styles/theme';

const { Text } = Typography;

const Details = styled.details`
  width: 100%;
  min-width: 0;
  border: 1px solid ${theme.colors.border.secondary};
  border-radius: ${theme.borderRadius.base};
  background: ${theme.colors.bg.secondary};
  padding: ${theme.spacing.sm} ${theme.spacing.base};

  &[open] {
    padding-bottom: ${theme.spacing.base};
  }

  summary {
    cursor: pointer;
    color: ${theme.colors.text.secondary};
    font-weight: ${theme.typography.fontWeight.semibold};
  }
`;

const PreviewBody = styled.div`
  min-width: 0;
  margin-top: ${theme.spacing.base};
  overflow-x: auto;
`;

export const JsonBlock = styled.pre`
  width: 100%;
  max-width: 100%;
  max-height: 520px;
  margin: 0;
  padding: 16px;
  overflow: auto;
  border-radius: ${theme.borderRadius.base};
  background: #0f172a;
  color: #dbeafe;
  font-size: 12px;
  line-height: 1.5;
`;

export interface OpsAdvancedPreviewProps {
  title?: React.ReactNode;
  description?: React.ReactNode;
  defaultOpen?: boolean;
  children: React.ReactNode;
}

export const OpsAdvancedPreview: React.FC<OpsAdvancedPreviewProps> = ({
  title = '高级预览',
  description = '高级预览，仅用于排查、审计和导入导出。日常配置请使用上方表单。',
  defaultOpen = false,
  children,
}) => (
  <Details open={defaultOpen}>
    <summary>{title}</summary>
    <PreviewBody>
      {description && (
        <Text type="tertiary" size="small" style={{ display: 'block', marginBottom: 12 }}>
          {description}
        </Text>
      )}
      {children}
    </PreviewBody>
  </Details>
);
