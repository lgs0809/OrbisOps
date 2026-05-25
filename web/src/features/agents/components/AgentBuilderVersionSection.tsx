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

/**
 * Keep the editor's action surface aligned with the immutable lifecycle.
 * The API intentionally rejects invalid transitions; the UI should not offer
 * them and then turn a predictable state mismatch into a user-facing error.
 */
export const versionActionsForLifecycle = (lifecycle?: string): AgentVersionAction[] => {
  const normalized = String(lifecycle || 'DRAFT').trim().toUpperCase();
  if (normalized === 'DRAFT') return ['validate', 'disable'];
  if (normalized === 'VALIDATED') return ['publish', 'disable'];
  if (normalized === 'PUBLISHED') return ['rollback', 'disable'];
  return [];
};

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
              {versionActionsForLifecycle(version.lifecycle).map((action) => (
                <Button
                  key={action}
                  size="small"
                  type={action === 'publish' ? 'primary' : action === 'disable' ? 'danger' : 'tertiary'}
                  theme={action === 'disable' ? 'borderless' : action === 'publish' ? 'light' : 'solid'}
                  loading={operating}
                  onClick={() => onAction(action, version.version)}
                >
                  {action === 'validate' ? '校验' : action === 'publish' ? '发布' : action === 'rollback' ? '回滚' : '停用'}
                </Button>
              ))}
              {!versionActionsForLifecycle(version.lifecycle).length && <Text type="tertiary" size="small">无可用操作</Text>}
            </Space>
          </Space>
        </VersionItem>
      ))}
      {!versions.length && <EmptyState>暂无版本。保存草稿后会创建第一个版本。</EmptyState>}
    </FormStack>
  </Section>
);
