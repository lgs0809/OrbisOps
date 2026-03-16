import React from 'react';
import { Button, Typography } from '@douyinfe/semi-ui';
import styled from 'styled-components';

import { OpsStatusBadge } from '../../../components/ops-layout';
import { theme } from '../../../styles/theme';
import type { OpsChannelType } from '../../../services/ops-channel-service';
import { channelReadinessLabel, type ChannelProviderCardModel } from '../model/channel-model';

const { Text } = Typography;

const Grid = styled.div`
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: ${theme.spacing.base};

  @media (max-width: 1000px) { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  @media (max-width: 640px) { grid-template-columns: 1fr; }
`;

const ProviderCard = styled.div`
  min-width: 0;
  padding: ${theme.spacing.base};
  border: 1px solid ${theme.colors.border.secondary};
  border-radius: ${theme.borderRadius.base};
  background: ${theme.colors.bg.primary};
`;

const semanticStatus = (state: ChannelProviderCardModel['readiness']) => {
  if (state === 'READY') return 'VERIFIED' as const;
  if (state === 'ACTION_REQUIRED') return 'AWAITING_APPROVAL' as const;
  if (state === 'DISABLED') return 'BLOCKED' as const;
  return 'BLOCKED' as const;
};

export interface ChannelProviderCardsProps {
  providers: ChannelProviderCardModel[];
  onConnect: (type: OpsChannelType) => void;
}

export const ChannelProviderCards: React.FC<ChannelProviderCardsProps> = ({ providers, onConnect }) => (
  <Grid data-testid="channel-provider-cards">
    {providers.map((provider) => (
      <ProviderCard key={provider.type} data-provider={provider.type}>
        <div style={{ display: 'flex', justifyContent: 'space-between', gap: 10, alignItems: 'flex-start' }}>
          <div>
            <Text strong>{provider.name}</Text>
            <Text type="tertiary" size="small" style={{ display: 'block', marginTop: 4 }}>{provider.type}</Text>
          </div>
          <OpsStatusBadge status={semanticStatus(provider.readiness)} label={channelReadinessLabel(provider.readiness)} />
        </div>
        <Text type="tertiary" size="small" style={{ display: 'block', marginTop: 12 }}>
          Connection: {provider.connectionMode}
        </Text>
        {provider.actionRequired && (
          <Text type="tertiary" size="small" style={{ display: 'block', marginTop: 6 }}>{provider.actionRequired}</Text>
        )}
        <Button
          block
          style={{ marginTop: 14 }}
          disabled={!provider.adapterAvailable}
          onClick={() => onConnect(provider.type)}
        >
          {provider.configured ? 'Manage' : 'Connect'}
        </Button>
      </ProviderCard>
    ))}
  </Grid>
);
