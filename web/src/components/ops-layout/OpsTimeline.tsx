import React from 'react';
import { Typography } from '@douyinfe/semi-ui';
import styled from 'styled-components';

import { theme } from '../../styles/theme';
import { OpsStatusBadge, type OpsSemanticStatus } from './OpsStatusBadge';

const { Text } = Typography;

const Row = styled.div`
  display: grid;
  grid-template-columns: 150px minmax(0, 1fr);
  gap: ${theme.spacing.base};
  padding: 10px 0;
  border-bottom: 1px solid ${theme.colors.border.secondary};

  &:last-child { border-bottom: none; }

  @media (max-width: 640px) {
    grid-template-columns: 1fr;
    gap: 6px;
  }
`;

export interface OpsTimelineItem {
  id: string;
  status: OpsSemanticStatus;
  title: React.ReactNode;
  description?: React.ReactNode;
  timestamp?: React.ReactNode;
}

export const OpsTimeline: React.FC<{ items: OpsTimelineItem[] }> = ({ items }) => (
  <div>
    {items.map((item) => (
      <Row key={item.id}>
        <div>
          <OpsStatusBadge status={item.status} />
          {item.timestamp && <Text type="tertiary" size="small" style={{ display: 'block', marginTop: 6 }}>{item.timestamp}</Text>}
        </div>
        <div>
          <Text strong>{item.title}</Text>
          {item.description && <div style={{ marginTop: 4 }}><Text type="tertiary">{item.description}</Text></div>}
        </div>
      </Row>
    ))}
  </div>
);
