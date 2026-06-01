import React from 'react';

import styled from 'styled-components';
import { Button, Dropdown, Avatar, Typography } from '@douyinfe/semi-ui';
import { IconActivity, IconExit, IconMenu, IconShield } from '@douyinfe/semi-icons';

import { theme } from '../../styles/theme';
import { AdminUserService } from '../../services';
import { getStoredUserInfo, isAdminUser, roleLabel } from '../../services/auth-session';
import { GlobalCommandPalette } from '../../features/search/components/GlobalCommandPalette';

const { Text } = Typography;

interface HeaderProps {
  onToggleSidebar?: () => void;
  onLogout?: () => void;
  collapsed?: boolean;
}

const HeaderContainer = styled.div`
  height: 72px;
  background: ${theme.colors.bg.primary};
  border-bottom: 1px solid ${theme.colors.border.secondary};
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 ${theme.spacing.lg};
  position: sticky;
  top: 0;
  z-index: 100;
  box-shadow: ${theme.shadows.sm};
  flex-shrink: 0;
`;

const HeaderLeft = styled.div`
  display: flex;
  align-items: center;
  gap: ${theme.spacing.base};
  min-width: 0;
`;

const ToggleButton = styled(Button)`
  width: 40px;
  height: 40px;
  border-radius: ${theme.borderRadius.base};
  border: 1px solid ${theme.colors.border.primary};

  &:hover {
    border-color: ${theme.colors.primary};
    color: ${theme.colors.primary};
  }
`;

const HeaderRight = styled.div`
  display: flex;
  align-items: center;
  gap: ${theme.spacing.base};
  min-width: 0;
`;

const ProductScope = styled.div`
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;

  .title {
    color: ${theme.colors.text.primary};
    font-size: ${theme.typography.fontSize.base};
    font-weight: ${theme.typography.fontWeight.semibold};
    line-height: 1.2;
    margin: 0;
    white-space: nowrap;
  }

  .subtitle {
    color: ${theme.colors.text.tertiary};
    font-size: ${theme.typography.fontSize.sm};
    line-height: 1.2;
    margin: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  @media (max-width: ${theme.breakpoints.sm}) {
    .subtitle {
      display: none;
    }
  }
`;

const StatusRail = styled.div`
  display: flex;
  align-items: center;
  gap: ${theme.spacing.sm};

  @media (max-width: ${theme.breakpoints.lg}) {
    display: none;
  }
`;

const StatusPill = styled.div`
  display: inline-flex;
  align-items: center;
  gap: ${theme.spacing.xs};
  height: 32px;
  padding: 0 ${theme.spacing.sm};
  border: 1px solid ${theme.colors.border.secondary};
  border-radius: ${theme.borderRadius.base};
  color: ${theme.colors.text.secondary};
  background: ${theme.colors.bg.secondary};
  font-size: ${theme.typography.fontSize.sm};
  white-space: nowrap;

  .semi-icon {
    color: ${theme.colors.success};
  }
`;

const UserDropdown = styled.div`
  display: flex;
  align-items: center;
  gap: ${theme.spacing.sm};
  padding: ${theme.spacing.sm} ${theme.spacing.base};
  border-radius: ${theme.borderRadius.base};
  cursor: pointer;
  transition: all ${theme.animation.duration.normal} ${theme.animation.easing.cubic};

  &:hover {
    background: ${theme.colors.bg.tertiary};
  }
`;

const UserInfo = styled.div`
  display: flex;
  flex-direction: column;
  align-items: flex-end;

  @media (max-width: ${theme.breakpoints.sm}) {
    display: none;
  }

  .username {
    color: ${theme.colors.text.primary};
    font-weight: ${theme.typography.fontWeight.medium};
    font-size: ${theme.typography.fontSize.sm};
    margin: 0;
  }

  .role {
    color: ${theme.colors.text.tertiary};
    font-size: ${theme.typography.fontSize.xs};
    margin: 0;
  }
`;

export const Header: React.FC<HeaderProps> = ({ onToggleSidebar, onLogout }) => {
  const userInfo = getStoredUserInfo();
  const admin = isAdminUser();

  const userMenuItems = [
    {
      node: 'item' as const,
      name: '退出登录',
      icon: <IconExit />,
      onClick: () => {
        AdminUserService.logout().finally(() => onLogout?.());
      },
    },
  ];

  return (
    <HeaderContainer>
      <HeaderLeft>
        <ToggleButton theme="borderless" icon={<IconMenu />} onClick={onToggleSidebar} />

        <ProductScope>
          <Text className="title">运维 AI 控制台</Text>
          <Text className="subtitle">
            {admin ? '多 Agent 排障、知识库和监控数据统一入口' : '通用 Agent 对话和会话工作台'}
          </Text>
        </ProductScope>
      </HeaderLeft>

      <HeaderRight>
        <GlobalCommandPalette />
        <StatusRail>
          <StatusPill>
            <IconShield />
            JWT 已保护
          </StatusPill>
          <StatusPill>
            <IconActivity />
            {admin ? '生产控制台' : 'Agent 工作台'}
          </StatusPill>
        </StatusRail>

        <Dropdown trigger="click" position="bottomRight" menu={userMenuItems}>
          <UserDropdown>
            <UserInfo>
              <Text className="username">{userInfo.username || '用户'}</Text>
              <Text className="role">{roleLabel(userInfo.userRole || userInfo.role)}</Text>
            </UserInfo>
            <Avatar size="small" color="blue">
              {userInfo.username?.[0]?.toUpperCase() || 'U'}
            </Avatar>
          </UserDropdown>
        </Dropdown>
      </HeaderRight>
    </HeaderContainer>
  );
};
