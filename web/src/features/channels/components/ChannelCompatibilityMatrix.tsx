import React from 'react';
import { Table, Tag, Tooltip, Typography } from '@douyinfe/semi-ui';

import { TableScroll } from '../../../components/ops-layout';
import type {
  ChannelCompatibilityCapability,
  ChannelCompatibilityRow,
} from '../model/channel-model';

const { Text } = Typography;

const dimensions: Array<{
  key: ChannelCompatibilityCapability;
  title: string;
  unsupported: string;
}> = [
  { key: 'INBOUND', title: 'Inbound', unsupported: 'This provider does not accept inbound messages in the current adapter.' },
  { key: 'OUTBOUND', title: 'Outbound', unsupported: 'This provider does not deliver outbound messages in the current adapter.' },
  { key: 'DIRECT_MESSAGES', title: 'DM', unsupported: 'Direct-message conversations are not supported.' },
  { key: 'GROUP_MESSAGES', title: 'Group', unsupported: 'Group or guild conversations are not supported.' },
  { key: 'REPLY_TO_INBOUND', title: 'Reply', unsupported: 'Reply-scoped delivery is not supported.' },
  { key: 'INTERACTIVE_ACTIONS', title: 'Approval', unsupported: 'Interactive approval falls back to the OrbisOps web approval surface.' },
  { key: 'MESSAGE_UPDATE', title: 'Update', unsupported: 'Progress is delivered as new messages instead of editing the original message.' },
  { key: 'ATTACHMENTS', title: 'Files', unsupported: 'Attachment ingestion/delivery is unavailable for this provider.' },
  { key: 'PROACTIVE_PUSH', title: 'Proactive', unsupported: 'Unrestricted proactive delivery is not supported; use reply-scoped or provider-allowed delivery.' },
];

const CapabilityCell: React.FC<{ supported: boolean; fallback: string }> = ({ supported, fallback }) => (
  <Tooltip content={supported ? 'Supported by the installed provider adapter.' : fallback}>
    <Tag color={supported ? 'green' : 'grey'} aria-label={supported ? 'Supported' : 'Not supported'}>
      {supported ? '✓' : '—'}
    </Tag>
  </Tooltip>
);

export interface ChannelCompatibilityMatrixProps {
  rows: ChannelCompatibilityRow[];
}

export const ChannelCompatibilityMatrix: React.FC<ChannelCompatibilityMatrixProps> = ({ rows }) => (
  <div data-testid="channel-compatibility-matrix">
    <Text type="tertiary" style={{ display: 'block', marginBottom: 12 }}>
      Capability facts come directly from the backend Provider Adapter Catalog. A dash means the current adapter does not expose that capability; OrbisOps does not infer support from provider sample-projecting or configuration alone.
    </Text>
    <TableScroll>
      <Table
        rowKey="type"
        dataSource={rows}
        pagination={false}
        scroll={{ x: 1280 }}
        columns={[
          {
            title: 'Provider',
            dataIndex: 'name',
            width: 170,
            fixed: 'left' as const,
            render: (_: string, row: ChannelCompatibilityRow) => (
              <div>
                <Text strong>{row.name}</Text>
                <Text type="tertiary" size="small" style={{ display: 'block' }}>{row.type}</Text>
              </div>
            ),
          },
          {
            title: 'Connection',
            dataIndex: 'connectionMode',
            width: 190,
            render: (value: string, row: ChannelCompatibilityRow) => (
              row.installed ? <Text size="small">{value}</Text> : <Tag color="grey">Not installed</Tag>
            ),
          },
          ...dimensions.map((dimension) => ({
            title: dimension.title,
            width: 92,
            align: 'center' as const,
            render: (_: unknown, row: ChannelCompatibilityRow) => (
              <CapabilityCell
                supported={row.capabilities[dimension.key]}
                fallback={row.installed ? dimension.unsupported : 'Provider adapter is not installed in the current build.'}
              />
            ),
          })),
        ]}
      />
    </TableScroll>
  </div>
);
