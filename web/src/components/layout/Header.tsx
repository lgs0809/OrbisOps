import React from 'react';
import { Link, useLocation } from 'react-router-dom';
import styled from 'styled-components';
import { Avatar, Button, Dropdown, Typography } from '@douyinfe/semi-ui';
import { IconExit, IconMenu } from '@douyinfe/semi-icons';

import { theme } from '../../styles/theme';
import { AdminUserService } from '../../services';
import { getStoredUserInfo, roleLabel } from '../../services/auth-session';
import { APP_ROUTES, routeByPath } from '../../app/navigation/route-metadata';

const { Text } = Typography;

interface HeaderProps {
  onToggleSidebar?: () => void;
  onLogout?: () => void;
  collapsed?: boolean;
}

const HeaderContainer = styled.header`
  height: 56px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 0 24px;
  border-bottom: 1px solid ${theme.colors.border.secondary};
  background: ${theme.colors.bg.primary};
  @media (max-width: ${theme.breakpoints.md}) { height: 52px; padding: 0 10px; }
`;

const HeaderLeft = styled.div`
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 12px;
`;

const ToggleButton = styled(Button)`
  width: 34px;
  height: 34px;
  flex-shrink: 0;
  border-radius: 8px;

  &:hover {
    background: #f1f2f4;
  }
`;

const RouteContext = styled.div`
  min-width: 0;
  display: flex;
  align-items: baseline;
  gap: 8px;

  .title {
    max-width: 300px;
    overflow: hidden;
    color: ${theme.colors.text.primary};
    font-size: 14px;
    font-weight: 600;
    line-height: 1;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .breadcrumb {
    max-width: 420px;
    overflow: hidden;
    color: ${theme.colors.text.tertiary};
    font-size: 13px;
    line-height: 1;
    text-decoration: none;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  @media (max-width: ${theme.breakpoints.sm}) {
    .breadcrumb {
      display: none;
    }
  }
`;

const HeaderRight = styled.div`
  min-width: 0;
  display: none;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
  @media (max-width: ${theme.breakpoints.md}) { display: flex; }
`;

const UserDropdown = styled.button`
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 6px 4px 9px;
  border: 0;
  border-radius: 9px;
  background: transparent;
  color: inherit;
  font: inherit;
  cursor: pointer;

  &:hover {
    background: #f1f2f4;
  }

  &:focus-visible {
    outline: 2px solid ${theme.colors.primary};
    outline-offset: 1px;
  }
`;

const UserCopy = styled.div`
  display: flex;
  min-width: 0;
  flex-direction: column;
  align-items: flex-end;

  .name {
    max-width: 140px;
    overflow: hidden;
    color: ${theme.colors.text.primary};
    font-size: 12px;
    font-weight: 600;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .role {
    margin-top: 1px;
    color: ${theme.colors.text.tertiary};
    font-size: 10px;
  }

  @media (max-width: ${theme.breakpoints.sm}) {
    display: none;
  }
`;

export const Header: React.FC<HeaderProps> = ({ onToggleSidebar, onLogout, collapsed = false }) => {
  const location = useLocation();
  const userInfo = getStoredUserInfo();
  const route = routeByPath(location.pathname);
  const parentRoute = APP_ROUTES.find((entry) => entry.navigation?.section === 'primary'
    && route?.path.startsWith(`${entry.path}/`));

  const parentPath = parentRoute?.path === '/settings' && route?.navigation?.subgroup
    ? `/settings?category=${route.navigation.subgroup}` : parentRoute?.path;

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
        <RouteContext>
          <Text className="title">{route?.title || 'OrbisOps'}</Text>
          {parentRoute && <Link className="breadcrumb" to={parentPath || parentRoute.path}>返回{parentRoute.title}</Link>}
        </RouteContext>
      </HeaderLeft>

      <HeaderRight>
        <Dropdown trigger="click" position="bottomRight" menu={userMenuItems}>
          <UserDropdown type="button" aria-label={`打开 ${userInfo.username || '用户'} 的用户菜单`}>
            <UserCopy>
              <Text className="name">{userInfo.username || '用户'}</Text>
              <Text className="role">{roleLabel(userInfo.userRole || userInfo.role)}</Text>
            </UserCopy>
            <Avatar size="small" color="grey">
              {userInfo.username?.[0]?.toUpperCase() || 'U'}
            </Avatar>
          </UserDropdown>
        </Dropdown>
      </HeaderRight>
    </HeaderContainer>
  );
};
