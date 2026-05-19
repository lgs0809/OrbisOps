import React from 'react';
import { useLocation } from 'react-router-dom';
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

import {
  SidebarContainer, SidebarBackdrop, Brand, Mark, BrandCopy, NavScroll,
  NavDivider, NavItem, Footer, SearchSlot, UserTrigger,
} from './Sidebar.styles';
export { SIDEBAR_WIDTH, SIDEBAR_COLLAPSED_WIDTH } from './Sidebar.styles';

interface SidebarProps {
  selectedKey?: string;
  onSelect?: (key: string) => void;
  collapsed?: boolean;
  onMobileClose?: () => void;
  onLogout?: () => void;
}

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
  const activeRoute = routes.find((route) => activeSelection === route.path || activeSelection.startsWith(`${route.path}/`));
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
        <Brand $collapsed={collapsed}>
          <Mark><IconBranch /></Mark>
          <BrandCopy $collapsed={collapsed}><strong>OrbisOps</strong><span>智能运维工作空间</span></BrandCopy>
        </Brand>

        <SearchSlot $collapsed={collapsed}><GlobalCommandPalette compact={collapsed} /></SearchSlot>

        <NavScroll aria-label="一级导航" role="menu">
          {routes.map((route, index) => (
            <React.Fragment key={route.path}>
              {index > 0 && SECTION_BREAK_BEFORE.has(route.id) && <NavDivider />}
              <NavItem
                type="button"
                role="menuitem"
                aria-current={activeRoute?.path === route.path ? 'page' : undefined}
                aria-label={route.navigation?.label || route.title}
                title={route.navigation?.label || route.title}
                $active={activeRoute?.path === route.path}
                $collapsed={collapsed}
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
          <Dropdown trigger="click" position="rightBottom" menu={userMenuItems}>
            <UserTrigger $collapsed={collapsed} type="button" aria-label={`打开 ${userInfo.username || '用户'} 的用户菜单`}>
              <Avatar size="small" color="grey">{userInfo.username?.[0]?.toUpperCase() || 'U'}</Avatar>
              <span className="user-copy"><strong>{userInfo.username || '用户'}</strong><span>账号与退出</span></span>
            </UserTrigger>
          </Dropdown>
        </Footer>
      </SidebarContainer>
    </>
  );
};
