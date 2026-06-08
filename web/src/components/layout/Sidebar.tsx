import React from 'react';
import { useLocation } from 'react-router-dom';

import styled from 'styled-components';
import { Nav, Avatar, Typography } from '@douyinfe/semi-ui';
import {
  IconBranch,
  IconApps,
  IconAlertTriangle,
  IconClock,
  IconFolder,
  IconHistory,
  IconShield,
} from '@douyinfe/semi-icons';

import { theme } from '../../styles/theme';
import { currentUserRole, getStoredUserInfo, roleLabel } from '../../services/auth-session';
import {
  canonicalPathFor,
  navigationRoutesForRole,
  PLATFORM_NAVIGATION_GROUPS,
  routeByPath,
  type AppRouteMetadata,
  type NavigationIcon,
} from '../../app/navigation/route-metadata';

const { Text } = Typography;

interface SidebarProps {
  selectedKey?: string;
  onSelect?: (key: string) => void;
  collapsed?: boolean;
  onMobileClose?: () => void;
}

export const SIDEBAR_WIDTH = 280;
export const SIDEBAR_COLLAPSED_WIDTH = 80;

const SidebarContainer = styled.div<{ $collapsed: boolean }>`
  width: ${(props) => (props.$collapsed ? `${SIDEBAR_COLLAPSED_WIDTH}px` : `${SIDEBAR_WIDTH}px`)};
  min-width: ${(props) =>
    props.$collapsed ? `${SIDEBAR_COLLAPSED_WIDTH}px` : `${SIDEBAR_WIDTH}px`};
  max-width: ${(props) =>
    props.$collapsed ? `${SIDEBAR_COLLAPSED_WIDTH}px` : `${SIDEBAR_WIDTH}px`};
  height: 100vh;
  background: ${theme.colors.bg.primary};
  border-right: 1px solid ${theme.colors.border.secondary};
  display: flex;
  flex-direction: column;
  transition: width ${theme.animation.duration.normal} ${theme.animation.easing.cubic};
  position: fixed;
  left: 0;
  top: 0;
  z-index: 1000;
  overflow-x: hidden;
  overflow-y: auto;

  @media (max-width: ${theme.breakpoints.md}) {
    width: ${SIDEBAR_WIDTH}px;
    min-width: ${SIDEBAR_WIDTH}px;
    max-width: min(${SIDEBAR_WIDTH}px, 88vw);
    transform: ${(props) => (props.$collapsed ? 'translateX(-100%)' : 'translateX(0)')};
    visibility: ${(props) => (props.$collapsed ? 'hidden' : 'visible')};
    pointer-events: ${(props) => (props.$collapsed ? 'none' : 'auto')};
    transition: transform ${theme.animation.duration.normal} ${theme.animation.easing.cubic};
    box-shadow: ${(props) => (props.$collapsed ? 'none' : theme.shadows.lg)};
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
    background: rgba(15, 23, 42, 0.42);
    cursor: default;
  }
`;

const SidebarHeader = styled.div<{ $collapsed: boolean }>`
  padding: ${(props) => (props.$collapsed ? theme.spacing.base : theme.spacing.lg)};
  border-bottom: 1px solid ${theme.colors.border.secondary};
  display: flex;
  align-items: center;
  justify-content: ${(props) => (props.$collapsed ? 'center' : 'flex-start')};
  gap: ${theme.spacing.base};
  min-height: 72px;
`;

const Logo = styled.div`
  width: 40px;
  height: 40px;
  background: ${theme.colors.gradient.primary};
  border-radius: ${theme.borderRadius.base};
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;

  .semi-icon {
    color: white;
    font-size: 20px;
  }
`;

const BrandInfo = styled.div<{ $collapsed: boolean }>`
  display: ${(props) => (props.$collapsed ? 'none' : 'block')};

  h4 {
    margin: 0;
    color: ${theme.colors.text.primary};
    font-weight: ${theme.typography.fontWeight.semibold};
    font-size: ${theme.typography.fontSize.base};
  }

  p {
    margin: 0;
    color: ${theme.colors.text.tertiary};
    font-size: ${theme.typography.fontSize.sm};
  }
`;

const SidebarContent = styled.div`
  flex: 1;
  padding: ${theme.spacing.base} 0;
  overflow-x: hidden;
  overflow-y: auto;
`;

const StyledNav = styled(Nav)<{ $collapsed: boolean }>`
  background: transparent;
  border: none;
  width: ${(props) => (props.$collapsed ? '60px' : '100%')};
  min-width: 0;
  margin: ${(props) => (props.$collapsed ? '0 auto' : '0')};
  padding-left: ${(props) => (props.$collapsed ? theme.spacing.sm : theme.spacing.base)};
  padding-right: ${(props) => (props.$collapsed ? theme.spacing.sm : theme.spacing.base)};

  .semi-navigation-inner,
  .semi-navigation-header-list-outer,
  .semi-navigation-list-wrapper,
  .semi-navigation-list {
    width: 100%;
    min-width: 0;
  }

  .semi-navigation-item,
  .semi-navigation-sub-title {
    margin: 4px 0;
    border-radius: ${theme.borderRadius.base};
    transition: all ${theme.animation.duration.normal} ${theme.animation.easing.cubic};
    width: 100%;
    min-width: 0;
    box-sizing: border-box;
    align-items: center;
    justify-content: ${(props) => (props.$collapsed ? 'center' : 'flex-start')};
    padding-left: ${(props) => (props.$collapsed ? theme.spacing.sm : '12px')};
    padding-right: ${(props) => (props.$collapsed ? theme.spacing.sm : '12px')};

    &:hover {
      background: ${theme.colors.bg.tertiary};
    }

    &.semi-navigation-item-selected {
      background: ${theme.colors.primary};
      color: white;

      .semi-icon,
      .semi-navigation-item-icon {
        color: white;
      }

      .semi-navigation-item-text {
        color: white;
      }
    }

    .semi-navigation-item-inner {
      display: flex;
      align-items: center;
      width: 100%;
      min-width: 0;
      justify-content: ${(props) => (props.$collapsed ? 'center' : 'flex-start')};
    }

    .semi-navigation-item-icon-info {
      margin-right: ${(props) => (props.$collapsed ? '0' : '12px')};
      min-width: 20px;
    }

    .semi-navigation-item-icon-toggle-right {
      display: ${(props) => (props.$collapsed ? 'none' : 'inline-flex')};
    }

    .semi-navigation-item-text {
      display: ${(props) => (props.$collapsed ? 'none' : 'block')};
      min-width: 0;
    }
  }

  .semi-navigation-sub-title-selected {
    background: ${(props) => (props.$collapsed ? theme.colors.primary : 'transparent')};
    color: ${(props) => (props.$collapsed ? 'white' : theme.colors.text.primary)};
    font-weight: ${theme.typography.fontWeight.semibold};

    .semi-icon,
    .semi-navigation-item-icon,
    .semi-navigation-item-text {
      color: ${(props) => (props.$collapsed ? 'white' : theme.colors.text.primary)};
    }
  }

  &.semi-navigation-vertical
    .semi-navigation-list
    > .semi-navigation-sub-wrap
    > .semi-navigation-sub-title-selected {
    background-color: ${(props) => (props.$collapsed ? theme.colors.primary : 'transparent')};
    color: ${(props) => (props.$collapsed ? 'white' : theme.colors.text.primary)};
  }

  &.semi-navigation-vertical
    .semi-navigation-list
    > .semi-navigation-sub-wrap
    > .semi-navigation-sub-title-selected
    .semi-navigation-item-icon,
  &.semi-navigation-vertical
    .semi-navigation-list
    > .semi-navigation-sub-wrap
    > .semi-navigation-sub-title-selected
    .semi-navigation-item-text {
    color: ${(props) => (props.$collapsed ? 'white' : theme.colors.text.primary)};
  }

  &.semi-navigation-collapsed
    .semi-navigation-list
    > .semi-navigation-sub-wrap
    > .semi-navigation-sub-title
    .semi-navigation-item-icon-info {
    opacity: 1 !important;
  }

  .semi-navigation-list > .semi-navigation-item,
  .semi-navigation-list > .semi-navigation-sub-wrap > .semi-navigation-sub-title {
    padding-left: ${(props) => (props.$collapsed ? theme.spacing.sm : '12px')};
    padding-right: ${(props) => (props.$collapsed ? theme.spacing.sm : '12px')};
  }

  .semi-navigation-list > .semi-navigation-sub-wrap > .semi-navigation-sub-title {
    padding-left: ${(props) => (props.$collapsed ? theme.spacing.sm : '0')};
  }

  .semi-navigation-sub {
    .semi-navigation-item {
      padding-left: ${(props) => (props.$collapsed ? theme.spacing.sm : theme.spacing.xl)};
    }
  }
`;

const SidebarFooter = styled.div<{ $collapsed: boolean }>`
  padding: ${(props) => (props.$collapsed ? theme.spacing.base : theme.spacing.lg)};
  border-top: 1px solid ${theme.colors.border.secondary};
  display: flex;
  align-items: center;
  justify-content: ${(props) => (props.$collapsed ? 'center' : 'flex-start')};
  gap: ${theme.spacing.base};
`;

const UserInfo = styled.div<{ $collapsed: boolean }>`
  display: ${(props) => (props.$collapsed ? 'none' : 'flex')};
  flex-direction: column;
  flex: 1;

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

interface SidebarNavItem {
  itemKey: string;
  text: string;
  icon?: React.ReactNode;
  items?: SidebarNavItem[];
}

const iconForNavigation = (icon?: NavigationIcon): React.ReactNode => {
  switch (icon) {
    case 'home':
      return <IconApps />;
    case 'incident':
      return <IconAlertTriangle />;
    case 'diagnosis':
      return <IconBranch />;
    case 'automation':
      return <IconClock />;
    case 'project':
      return <IconFolder />;
    case 'governance':
      return <IconHistory />;
    case 'change':
    case 'integration':
    case 'model':
    case 'tool':
    case 'knowledge':
    case 'skill':
    case 'execution':
    case 'advanced':
    default:
      return <IconShield />;
  }
};

const routeItem = (route: AppRouteMetadata): SidebarNavItem => ({
  itemKey: route.path,
  text: route.navigation?.label || route.title,
  icon: iconForNavigation(route.navigation?.icon),
});

const routesInSection = (routes: AppRouteMetadata[], section: string, subgroup?: string) =>
  routes
    .filter((route) => route.navigation?.section === section && route.navigation?.subgroup === subgroup)
    .sort((left, right) => (left.navigation?.order || 0) - (right.navigation?.order || 0));

const buildMenuItems = (routes: AppRouteMetadata[]): SidebarNavItem[] => {
  const menu: SidebarNavItem[] = routesInSection(routes, 'home').map(routeItem);
  const work = routesInSection(routes, 'work');
  const automations = routesInSection(routes, 'automations');
  const projects = routesInSection(routes, 'projects');
  const platform = routesInSection(routes, 'platform');
  const platformGroups = PLATFORM_NAVIGATION_GROUPS
    .map((group) => ({
      ...group,
      itemKey: `nav-platform-${group.key}`,
      routes: routesInSection(routes, 'platform', group.key),
    }))
    .filter((group) => group.routes.length > 0);

  if (work.length) {
    menu.push({ itemKey: 'nav-work', text: 'Work', icon: <IconBranch />, items: work.map(routeItem) });
  }
  if (automations.length) {
    menu.push({ itemKey: 'nav-automations', text: 'Automations', icon: <IconClock />, items: automations.map(routeItem) });
  }
  menu.push(...projects.map(routeItem));
  if (platform.length || platformGroups.length) {
    const platformItems = platform.map(routeItem);
    platformGroups.forEach((group) => {
      platformItems.push({
        itemKey: group.itemKey,
        text: group.label,
        icon: <IconShield />,
        items: group.routes.map(routeItem),
      });
    });
    menu.push({ itemKey: 'nav-platform', text: 'Platform', icon: <IconShield />, items: platformItems });
  }
  return menu;
};

const nonNavigableKeys = new Set<string>([
  'nav-work',
  'nav-automations',
  'nav-platform',
  ...PLATFORM_NAVIGATION_GROUPS.map((group) => `nav-platform-${group.key}`),
]);

export const Sidebar: React.FC<SidebarProps> = ({
  selectedKey = 'dashboard',
  onSelect,
  collapsed = false,
  onMobileClose,
}) => {
  const location = useLocation();
  const userInfo = getStoredUserInfo();
  const menuItems = buildMenuItems(navigationRoutesForRole(currentUserRole()));
  const selectedPath = selectedKey.startsWith('/') ? selectedKey : `/${selectedKey}`;
  const activeSelection = routeByPath(location.pathname)?.path || canonicalPathFor(selectedPath);

  return (
    <>
      <SidebarBackdrop
        type="button"
        aria-label="关闭导航菜单"
        $visible={!collapsed}
        onClick={onMobileClose}
      />
      <SidebarContainer id="orbisops-sidebar" role="complementary" aria-label="Application navigation" $collapsed={collapsed}>
      <SidebarHeader $collapsed={collapsed}>
        <Logo>
          <IconBranch />
        </Logo>
        <BrandInfo $collapsed={collapsed}>
          <h4>OrbisOps</h4>
          <p>运维智能体平台</p>
        </BrandInfo>
      </SidebarHeader>

      <SidebarContent>
        <StyledNav
          aria-label="Primary navigation"
          $collapsed={collapsed}
          selectedKeys={[activeSelection]}
          isCollapsed={collapsed}
          onSelect={({ selectedKeys }: { selectedKeys: string[] }) => {
            const key = selectedKeys[0] as string;
            if (nonNavigableKeys.has(key)) {
              return;
            }
            onMobileClose?.();
            onSelect?.(key);
          }}
          items={menuItems}
        />
      </SidebarContent>

      <SidebarFooter $collapsed={collapsed}>
        <Avatar size="small" color="blue">
          {userInfo.username?.[0]?.toUpperCase() || 'U'}
        </Avatar>
        <UserInfo $collapsed={collapsed}>
          <Text className="username">{userInfo.username || '用户'}</Text>
          <Text className="role">{roleLabel(userInfo.userRole || userInfo.role)}</Text>
        </UserInfo>
      </SidebarFooter>
      </SidebarContainer>
    </>
  );
};
