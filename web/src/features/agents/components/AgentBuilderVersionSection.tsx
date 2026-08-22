import React from 'react';
import styled from 'styled-components';
import { Button, Space, Tag, Typography } from '@douyinfe/semi-ui';
import { IconRefresh } from '@douyinfe/semi-icons';

import { theme } from '../../../styles/theme';
import { OpsAgentDefinition } from '../../../services/ops-admin-service';

const { Text } = Typography;

const Section = styled.div`
  border-top: 1px solid ${theme.colors.border.secondary};
  padding-top: ${theme.spacing.base};
  min-width: 0;
`;

const FormStack = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${theme.spacing.sm};
  min-width: 0;
`;

const VersionItem = styled.div`
  border: 1px solid ${theme.colors.border.secondary};
  border-radius: ${theme.borderRadius.base};
  background: ${theme.colors.bg.primary};
  padding: ${theme.spacing.sm};
`;

const EmptyState = styled.div`
  padding: ${theme.spacing.base};
  color: ${theme.colors.text.tertiary};
  text-align: center;
  border: 1px dashed ${theme.colors.border.secondary};
  border-radius: ${theme.borderRadius.base};
`;

export type AgentVersionAction = 'validate' | 'publish' | 'rollback' | 'disable';

type Props = {
  versions: OpsAgentDefinition[];
  loading: boolean;
  operating: boolean;
  onRefresh: () => void;
  onAction: (action: AgentVersionAction, version?: number) => void;
};

export const AgentBuilderVersionSection: React.FC<Props> = ({
  versions,
  loading,
  operating,
  onRefresh,
  onAction,
}) => (
  <Section>
    <Space style={{ width: '100%', justifyContent: 'space-between' }}>
      <Text strong>版本</Text>
      <Button size="small" loading={loading} icon={<IconRefresh />} onClick={onRefresh}>
        刷新
      </Button>
    </Space>
    <FormStack>
      {versions.slice(0, 8).map((version) => (
        <VersionItem key={`${version.agentId}-${version.version}`}>
          <Space vertical align="start" spacing="tight" style={{ width: '100%' }}>
            <Space style={{ width: '100%', justifyContent: 'space-between' }}>
              <Text strong>v{version.version || '-'}</Text>
              <Tag color={version.lifecycle === 'PUBLISHED' ? 'green' : version.lifecycle === 'VALIDATED' ? 'blue' : 'grey'}>
                {version.lifecycle === 'PUBLISHED' ? '已发布' : version.lifecycle === 'VALIDATED' ? '已校验' : version.lifecycle === 'DISABLED' ? '已停用' : '草稿'}
              </Tag>
            </Space>
            <Text type="tertiary" size="small">{version.name || version.agentId}</Text>
            <Space wrap>
              <Button size="small" loading={operating} onClick={() => onAction('validate', version.version)}>校验</Button>
              <Button size="small" type="primary" theme="light" loading={operating} onClick={() => onAction('publish', version.version)}>发布</Button>
              <Button size="small" loading={operating} onClick={() => onAction('rollback', version.version)}>回滚</Button>
              <Button size="small" type="danger" theme="borderless" loading={operating} onClick={() => onAction('disable', version.version)}>停用</Button>
            </Space>
          </Space>
        </VersionItem>
      ))}
      {!versions.length && <EmptyState>暂无版本。保存草稿后会创建第一个版本。</EmptyState>}
    </FormStack>
  </Section>
);
