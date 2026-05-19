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
  gap: 20px;
  width: 100%;
  min-width: 0;
  margin: 2px 0 22px;
  flex-wrap: wrap;

  @media (max-width: ${theme.breakpoints.md}) {
    flex-direction: column;
    gap: 12px;
  }
`;

const TitleBlock = styled.div`
  min-width: 0;
  max-width: 900px;

  .semi-typography-h3 {
    letter-spacing: -0.025em;
  }
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
      <Title heading={3} style={{ margin: 0, fontSize: 26, lineHeight: 1.25 }}>{title}</Title>
      {description && (
        <Paragraph type="tertiary" style={{ margin: '7px 0 0', maxWidth: 760, lineHeight: 1.6 }}>
          {description}
        </Paragraph>
      )}
    </TitleBlock>
    {(primaryAction || extra) && (
      <Space wrap align="start" spacing="tight">
        {extra}
        {primaryAction}
      </Space>
    )}
  </HeaderWrap>
);
