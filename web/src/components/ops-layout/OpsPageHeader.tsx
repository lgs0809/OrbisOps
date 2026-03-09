import React from 'react';
import styled from 'styled-components';
import { Space, Typography } from '@douyinfe/semi-ui';

import { theme } from '../../styles/theme';

const { Title, Paragraph } = Typography;

const HeaderWrap = styled.div`
  box-sizing: border-box;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: ${theme.spacing.base};
  width: 100%;
  min-width: 0;
  margin-bottom: ${theme.spacing.lg};
  padding: ${theme.spacing.lg};
  border: 1px solid ${theme.colors.border.secondary};
  border-radius: ${theme.borderRadius.base};
  background: ${theme.colors.bg.primary};
  min-width: 0;
  flex-wrap: wrap;

  @media (max-width: ${theme.breakpoints.md}) {
    flex-direction: column;
  }
`;

const TitleBlock = styled.div`
  min-width: 0;
`;

export interface OpsPageHeaderProps {
  title: React.ReactNode;
  description?: React.ReactNode;
  context?: React.ReactNode;
  primaryAction?: React.ReactNode;
  extra?: React.ReactNode;
}

export const OpsPageHeader: React.FC<OpsPageHeaderProps> = ({
  title,
  description,
  context,
  primaryAction,
  extra,
}) => (
  <HeaderWrap>
    <TitleBlock>
      {context}
      <Title heading={3} style={{ margin: 0 }}>{title}</Title>
      {description && (
        <Paragraph type="tertiary" style={{ margin: '6px 0 0' }}>
          {description}
        </Paragraph>
      )}
    </TitleBlock>
    {(primaryAction || extra) && (
      <Space wrap align="start">
        {extra}
        {primaryAction}
      </Space>
    )}
  </HeaderWrap>
);
