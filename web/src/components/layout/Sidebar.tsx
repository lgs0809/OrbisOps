import React from 'react';
import { useLocation } from 'react-router-dom';
import styled from 'styled-components';
import { Avatar, Dropdown } from '@douyinfe/semi-ui';
import {
  IconAlertTriangle,
  IconApps,
  IconBranch,
  IconClock,
  IconComment,
  IconExit,
  IconFolder,
  IconHistory,
  IconSetting,
} from '@douyinfe/semi-icons';

import { theme } from '../../styles/theme';
import { AdminUserService } from '../../services';
import { currentUserRole, getStoredUserInfo } from '../../services/auth-session';
import { GlobalCommandPalette } from '../../features/search/components/GlobalCommandPalette';
import {
  canonicalPathFor,
  navigationRoutesForRole,
  routeByPath,
  type AppRouteMetadata,
  type NavigationIcon,
} from '../../app/navigation/route-metadata';

interface SidebarProps {
  selectedKey?: string;
  onSelect?: (key: string) => void;
  collapsed?: boolean;
  onMobileClose?: () => void;
  onLogout?: () => void;
}

export const SIDEBAR_WIDTH = 84;
export const SIDEBAR_COLLAPSED_WIDTH = 84;

const SidebarContainer = styled.aside<{ $collapsed: boolean }>`
  position: fixed;
  inset: 0 auto 0 0;
  z-index: 1000;
  width: ${SIDEBAR_WIDTH}px;
  min-width: ${SIDEBAR_WIDTH}px;
  display: flex;
  flex-direction: column;
  padding: 10px 8px 9px;
  border-right: 1px solid #e4e5e7;
  background: #f7f7f5;
  color: #1d1f23;
  overflow: hidden;

  @media (max-width: ${theme.breakpoints.md}) {
    width: 248px;
    min-width: 248px;
    max-width: min(248px, 88vw);
    padding: 10px;
    transform: ${(props) => (props.$collapsed ? 'translateX(-100%)' : 'translateX(0)')};
    visibility: ${(props) => (props.$collapsed ? 'hidden' : 'visible')};
    pointer-events: ${(props) => (props.$collapsed ? 'none' : 'auto')};
    transition: transform ${theme.animation.duration.normal} ${theme.animation.easing.cubic};
    box-shadow: ${(props) => (props.$collapsed ? 'none' : '20px 0 50px rgb(15 23 42 / 16%)')};
  }

  @media (prefers-reduced-motion: reduce) {
    transition: none;
  }
`;

const SidebarBackdrop = styled.button<{ $visible: boolean }>`
  display: none;

  @media (max-width: ${theme.breakpoints.md}) {
    display: ${(props) => (props.$visible ? 'block' : 'none')};
    position: fixed;
    inset: 0;
    z-index: 999;
    padding: 0;
    border: 0;
    background: rgb(15 23 42 / 36%);
  }
`;

const Brand = styled.div`
  height: 54px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;

  @media (max-width: ${theme.breakpoints.md}) {
    justify-content: flex-start;
    gap: 10px;
    padding: 0 8px;
  }
`;

const Mark = styled.div`
  width: 38px;
  height: 38px;
  flex-shrink: 0;
  display: grid;
  place-items: center;
  border-radius: 11px;
  background: #15171a;
  color: #fff;

  .semi-icon { font-size: 19px; }
`;

const MobileBrandCopy = styled.div`
  display: none;

  @media (max-width: ${theme.breakpoints.md}) {
    display: block;

    strong {
      display: block;
      font-size: 14px;
      font-weight: 650;
      letter-spacing: -0.02em;
    }

    span {
      display: block;
      margin-top: 2px;
      color: ${theme.colors.text.tertiary};
      font-size: 9px;
      letter-spacing: 0.06em;
    }
  }
`;

const NavScroll = styled.nav`
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overflow-x: hidden;
  padding-top: 12px;
`;

const NavDivider = styled.div`
  height: 1px;
  margin: 9px 8px;
  background: #e0e1e3;

  @media (max-width: ${theme.breakpoints.md}) {
    margin: 9px 6px;
  }
`;

const NavItem = styled.button<{ $active: boolean }>`
  width: 100%;
  min-height: 54px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  margin: 2px 0;
  padding: 5px 2px;
  border: 0;
  border-radius: 11px;
  background: ${(props) => (props.$active ? '#e8e8e5' : 'transparent')};
  color: ${(props) => (props.$active ? '#17191c' : '#6f747d')};
  font: inherit;
  font-size: 10px;
  font-weight: ${(props) => (props.$active ? 650 : 500)};
  text-align: center;
  cursor: pointer;
  transition: background ${theme.animation.duration.fast} ease, color ${theme.animation.duration.fast} ease;

  .semi-icon {
    flex-shrink: 0;
    font-size: 19px;
    color: ${(props) => (props.$active ? '#17191c' : '#747983')};
  }

  span {
    display: block;
    max-width: 64px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &:hover {
    background: ${(props) => (props.$active ? '#e4e4e1' : '#ededeb')};
    color: #17191c;
  }

  &:focus-visible {
    outline: 2px solid ${theme.colors.primary};
    outline-offset: 1px;
  }

  @media (max-width: ${theme.breakpoints.md}) {
    min-height: 42px;
    flex-direction: row;
    justify-content: flex-start;
    gap: 11px;
    padding: 0 11px;
    font-size: 12px;
    text-align: left;

    span { max-width: none; }
  }
`;

const Footer = styled.div`
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 7px;
  padding-top: 9px;
  border-top: 1px solid #e0e1e3;

  @media (max-width: ${theme.breakpoints.md}) {
    align-items: stretch;
  }
`;

const SearchSlot = styled.div`
  display: flex;
  align-items: center;
  justify-content: center;

  @media (max-width: ${theme.breakpoints.md}) {
    justify-content: flex-start;
    padding: 0 3px;
  }
`;

const UserTrigger = styled.button`
  width: 46px;
  height: 46px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  border: 0;
  border-radius: 11px;
  background: transparent;
  cursor: pointer;

  &:hover { background: #ededeb; }
  &:focus-visible { outline: 2px solid ${theme.colors.primary}; outline-offset: 1px; }

  .user-copy { display: none; }

  @media (max-width: ${theme.breakpoints.md}) {
    width: 100%;
    justify-content: flex-start;
    gap: 10px;
    padding: 0 9px;

    .user-copy {
      display: block;
      min-width: 0;
      text-align: left;
    }

    .user-copy strong {
      display: block;
      overflow: hidden;
      font-size: 11px;
      font-weight: 600;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .user-copy span {
      display: block;
      margin-top: 2px;
      color: ${theme.colors.text.tertiary};
      font-size: 9px;
    }
  }
`;

const iconForNavigation = (icon?: NavigationIcon): React.ReactNode => {
  switch (icon) {
    case 'home': return <IconApps />;
    case 'chat': return <IconComment />;
    case 'workbench': return <IconHistory />;
    case 'workflow': return <IconBranch />;
    case 'automation': return <IconClock />;
    case 'project': return <IconFolder />;
    case 'change': return <IconAlertTriangle />;
    case 'settings': return <IconSetting />;
    default: return <IconApps />;
  }
};

const SECTION_BREAK_BEFORE = new Set(['workflows', 'projects', 'settings']);

const orderedRoutes = (routes: AppRouteMetadata[]) => routes
  .filter((route) => route.navigation?.section === 'primary')
  .sort((left, right) => (left.navigation?.order || 0) - (right.navigation?.order || 0));

export const Sidebar: React.FC<SidebarProps> = ({
  selectedKey = 'dashboard',
  onSelect,
  collapsed = false,
  onMobileClose,
  onLogout,
}) => {
  const location = useLocation();
  const userInfo = getStoredUserInfo();
  const routes = orderedRoutes(navigationRoutesForRole(currentUserRole()));
  const selectedPath = selectedKey.startsWith('/') ? selectedKey : `/${selectedKey}`;
  const activeSelection = routeByPath(location.pathname)?.path || canonicalPathFor(selectedPath);
  const userMenuItems = [{
    node: 'item' as const,
    name: '退出登录',
    icon: <IconExit />,
    onClick: () => AdminUserService.logout().finally(() => onLogout?.()),
  }];

  return (
    <>
      <SidebarBackdrop type="button" aria-label="关闭导航菜单" $visible={!collapsed} onClick={onMobileClose} />
      <SidebarContainer id="orbisops-sidebar" aria-label="应用导航" $collapsed={collapsed}>
        <Brand>
          <Mark><IconBranch /></Mark>
          <MobileBrandCopy><strong>OrbisOps</strong><span>AGENTIC OPERATIONS</span></MobileBrandCopy>
        </Brand>

        <NavScroll aria-label="一级导航" role="menu">
          {routes.map((route, index) => (
            <React.Fragment key={route.path}>
              {index > 0 && SECTION_BREAK_BEFORE.has(route.id) && <NavDivider />}
              <NavItem
                type="button"
                role="menuitem"
                aria-current={activeSelection === route.path ? 'page' : undefined}
                aria-label={route.navigation?.label || route.title}
                title={route.navigation?.label || route.title}
                $active={activeSelection === route.path}
                onClick={() => {
                  onMobileClose?.();
                  onSelect?.(route.path);
                }}
              >
                {iconForNavigation(route.navigation?.icon)}
                <span>{route.navigation?.label || route.title}</span>
              </NavItem>
            </React.Fragment>
          ))}
        </NavScroll>

        <Footer>
          <SearchSlot><GlobalCommandPalette compact /></SearchSlot>
          <Dropdown trigger="click" position="rightBottom" menu={userMenuItems}>
            <UserTrigger type="button" aria-label={`打开 ${userInfo.username || '用户'} 的用户菜单`}>
              <Avatar size="small" color="grey">{userInfo.username?.[0]?.toUpperCase() || 'U'}</Avatar>
              <span className="user-copy"><strong>{userInfo.username || '用户'}</strong><span>账号与退出</span></span>
            </UserTrigger>
          </Dropdown>
        </Footer>
      </SidebarContainer>
    </>
  );
};
