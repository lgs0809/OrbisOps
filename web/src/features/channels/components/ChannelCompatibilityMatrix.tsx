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
  { key: 'INBOUND', title: '入站', unsupported: '当前 Adapter 不支持接收入站消息。' },
  { key: 'OUTBOUND', title: '出站', unsupported: '当前 Adapter 不支持发送出站消息。' },
  { key: 'DIRECT_MESSAGES', title: '私聊', unsupported: '当前不支持私聊会话。' },
  { key: 'GROUP_MESSAGES', title: '群聊', unsupported: '当前不支持群聊或 Guild 会话。' },
  { key: 'REPLY_TO_INBOUND', title: '回复', unsupported: '当前不支持基于入站消息上下文的回复投递。' },
  { key: 'INTERACTIVE_ACTIONS', title: '审批', unsupported: '不支持 Channel 内交互审批时，会回退到 OrbisOps Web 审批页面。' },
  { key: 'MESSAGE_UPDATE', title: '更新', unsupported: '当前不支持编辑原消息，进度会以新消息形式投递。' },
  { key: 'ATTACHMENTS', title: '附件', unsupported: '当前 Provider 不支持附件接入或投递。' },
  { key: 'PROACTIVE_PUSH', title: '主动推送', unsupported: '当前不支持无限制主动推送；请使用回复上下文或 Provider 允许的投递方式。' },
];

const CapabilityCell: React.FC<{ supported: boolean; fallback: string }> = ({ supported, fallback }) => (
  <Tooltip content={supported ? '当前已安装 Provider Adapter 支持该能力。' : fallback}>
    <Tag color={supported ? 'green' : 'grey'} aria-label={supported ? '支持' : '不支持'}>
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
      这里只展示当前版本实际可用的渠道能力。短横线表示该能力目前不可用，不会仅凭配置项推断为已支持。
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
            render: (_: string, row: ChannelCompatibilityRow) => <Text strong>{row.name}</Text>,
          },
          {
            title: '连接方式',
            dataIndex: 'connectionMode',
            width: 190,
            render: (value: string, row: ChannelCompatibilityRow) => (
              row.installed ? <Text size="small">{value}</Text> : <Tag color="grey">未安装</Tag>
            ),
          },
          ...dimensions.map((dimension) => ({
            title: dimension.title,
            width: 92,
            align: 'center' as const,
            render: (_: unknown, row: ChannelCompatibilityRow) => (
              <CapabilityCell
                supported={row.capabilities[dimension.key]}
                fallback={row.installed ? dimension.unsupported : '当前构建未安装该 Provider Adapter。'}
              />
            ),
          })),
        ]}
      />
    </TableScroll>
  </div>
);
