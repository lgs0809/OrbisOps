import React, { useEffect, useMemo, useState } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import { Button, Input, Select, Space, Tag, Toast, Typography } from '@douyinfe/semi-ui';

import { opsChangePackageService } from '../../../services/ops-change-package-service';
import type { OpsApprovalChannelOption, OpsExecutionScope } from '../../../services/ops-change-package-service';
import type { OpsChangePackage } from '../../../services/ops-change-package-types';
import { userFacingError } from '../../../utils/user-facing-error';

const { Text } = Typography;
const { Option } = Select;

interface Props {
  record: OpsChangePackage;
  scope: OpsExecutionScope;
  canApprove: boolean;
}

export const ApprovalChannelDispatch: React.FC<Props> = ({ record, scope, canApprove }) => {
  const [channelId, setChannelId] = useState('');
  const [target, setTarget] = useState('');
  const enabled = canApprove && record.status === 'REVIEWING';

  const channelsQuery = useQuery({
    queryKey: ['change-package-approval-channels', scope, record.packageId],
    enabled,
    queryFn: async () => (await opsChangePackageService.listApprovalChannels(record.packageId, scope)).data || [],
    staleTime: 30_000,
    refetchOnWindowFocus: false,
  });

  const channels = channelsQuery.data || [];
  const selectedChannel = useMemo(
    () => channels.find((channel) => channel.channelId === channelId),
    [channelId, channels],
  );

  useEffect(() => {
    if (!channelId) return;
    if (channels.some((channel) => channel.channelId === channelId)) return;
    setChannelId('');
  }, [channelId, channels]);

  const sendMutation = useMutation({
    mutationFn: async () => {
      const normalizedTarget = target.trim();
      if (!channelId) throw new Error('请选择用于审批的 Channel。');
      if (!normalizedTarget) throw new Error('请输入 Channel 会话目标。');
      return await opsChangePackageService.sendApprovalCard(record.packageId, channelId, normalizedTarget, scope);
    },
    onSuccess: () => {
      Toast.success('审批卡已发送；用户操作时仍会重新校验身份、权限和当前 ChangePackage 版本。');
    },
    onError: (error) => {
      Toast.error(userFacingError(error, '发送审批卡失败，请稍后重试。'));
    },
  });

  if (!enabled) return null;

  return (
    <Space vertical align="start" spacing="tight" style={{ width: '100%', marginTop: 12 }}>
      <Space wrap align="center">
        <Text strong>发送审批卡到 Channel</Text>
        <Tag color="blue">仅发起审批交互</Tag>
      </Space>
      <Text type="tertiary">
        发送审批卡不会直接批准或执行生产变更。审批人在 Feishu 或 WeCom 操作时，OrbisOps 会重新校验 Channel 身份映射、Project RBAC、ChangePackage 状态、版本和内容哈希。
      </Text>
      <Space wrap style={{ width: '100%' }}>
        <Select
          value={channelId || undefined}
          placeholder={channelsQuery.isFetching ? '正在加载可用 Channel...' : '选择 Feishu / WeCom Channel'}
          loading={channelsQuery.isFetching}
          style={{ minWidth: 240 }}
          onChange={(value) => setChannelId(String(value || ''))}
        >
          {channels.map((channel: OpsApprovalChannelOption) => (
            <Option key={channel.channelId} value={channel.channelId}>
              {channel.name} · {channel.type}
            </Option>
          ))}
        </Select>
        <Input
          value={target}
          placeholder={selectedChannel?.type === 'FEISHU' ? 'Feishu 会话 ID（chat_id）' : selectedChannel?.type === 'WECOM' ? 'WeCom 会话 ID（chatid）' : 'Channel 会话目标'}
          style={{ minWidth: 260 }}
          onChange={(value) => setTarget(String(value || ''))}
        />
        <Button
          type="primary"
          loading={sendMutation.isPending}
          disabled={!channelId || !target.trim() || channelsQuery.isFetching}
          onClick={() => sendMutation.mutate()}
        >
          发送审批卡
        </Button>
      </Space>
      {!channelsQuery.isFetching && channels.length === 0 && (
        <Text type="tertiary">当前 Project 没有可用的 Feishu / WeCom Channel。请先完成 Channel 配置和身份绑定。</Text>
      )}
      {selectedChannel && <Tag>{selectedChannel.name} · {selectedChannel.type}</Tag>}
    </Space>
  );
};
