import React from 'react';

import styled from 'styled-components';
import { Button, Dropdown, Avatar, Typography } from '@douyinfe/semi-ui';
import { IconExit, IconMenu } from '@douyinfe/semi-icons';

import { theme } from '../../styles/theme';
import { AdminUserService } from '../../services';
import { getStoredUserInfo, roleLabel } from '../../services/auth-session';
import { GlobalCommandPalette } from '../../features/search/components/GlobalCommandPalette';

const { Text } = Typography;

interface HeaderProps {
  onToggleSidebar?: () => void;
  onLogout?: () => void;
  collapsed?: boolean;
}

const HeaderContainer = styled.header`
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

  @media (max-width: ${theme.breakpoints.md}) {
    padding: 0 12px;
  }
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
  flex-shrink: 0;

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
  flex-shrink: 0;

  @media (max-width: ${theme.breakpoints.sm}) {
    gap: ${theme.spacing.xs};
  }
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
    overflow: hidden;
    text-overflow: ellipsis;
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

const UserDropdown = styled.button`
  display: flex;
  align-items: center;
  gap: ${theme.spacing.sm};
  padding: ${theme.spacing.sm} ${theme.spacing.base};
  border: 0;
  border-radius: ${theme.borderRadius.base};
  background: transparent;
  color: inherit;
  font: inherit;
  cursor: pointer;
  transition: all ${theme.animation.duration.normal} ${theme.animation.easing.cubic};

  &:hover {
    background: ${theme.colors.bg.tertiary};
  }

  &:focus-visible {
    outline: 2px solid ${theme.colors.primary};
    outline-offset: 2px;
  }

  @media (max-width: ${theme.breakpoints.sm}) {
    padding: ${theme.spacing.xs};
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

export const Header: React.FC<HeaderProps> = ({ onToggleSidebar, onLogout, collapsed = false }) => {
  const userInfo = getStoredUserInfo();

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
        <ToggleButton
          theme="borderless"
          icon={<IconMenu />}
          aria-label={collapsed ? '打开导航菜单' : '收起导航菜单'}
          aria-controls="orbisops-sidebar"
          aria-expanded={!collapsed}
          onClick={onToggleSidebar}
        />

        <ProductScope>
          <Text className="title">OrbisOps</Text>
          <Text className="subtitle">受治理的智能运维工作空间</Text>
        </ProductScope>
      </HeaderLeft>

      <HeaderRight>
        <GlobalCommandPalette />

        <Dropdown trigger="click" position="bottomRight" menu={userMenuItems}>
          <UserDropdown type="button" aria-label={`打开 ${userInfo.username || '用户'} 的用户菜单`}>
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
