import React, { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import styled from 'styled-components';
import { Layout, Toast } from '@douyinfe/semi-ui';

import { Header, Sidebar, SIDEBAR_COLLAPSED_WIDTH, SIDEBAR_WIDTH } from '../layout';
import { clearAuthSession } from '../../services/auth-session';
import { theme } from '../../styles/theme';
import { useResponsiveSidebar } from '../../hooks/use-responsive-sidebar';

const PageLayout = styled(Layout)`
  min-height: 100vh;
  min-width: 0;
  width: 100%;
  background: ${theme.colors.bg.secondary};
  overflow-x: hidden;
`;

const SkipLink = styled.a`
  position: fixed;
  top: 8px;
  left: 8px;
  z-index: 2000;
  transform: translateY(-160%);
  padding: 8px 12px;
  border-radius: ${theme.borderRadius.base};
  background: ${theme.colors.bg.primary};
  color: ${theme.colors.primary};
  box-shadow: ${theme.shadows.md};

  &:focus {
    transform: translateY(0);
    outline: 2px solid ${theme.colors.primary};
    outline-offset: 2px;
  }
`;

const MainContent = styled.div<{ $collapsed: boolean }>`
  height: 100vh;
  min-width: 0;
  width: ${(props) =>
    props.$collapsed
      ? `calc(100vw - ${SIDEBAR_COLLAPSED_WIDTH}px)`
      : `calc(100vw - ${SIDEBAR_WIDTH}px)`};
  margin-left: ${(props) => (props.$collapsed ? `${SIDEBAR_COLLAPSED_WIDTH}px` : `${SIDEBAR_WIDTH}px`)};
  display: flex;
  flex-direction: column;
  background: ${theme.colors.bg.secondary};
  overflow-x: hidden;
  overflow-y: auto;
  transition:
    width ${theme.animation.duration.normal} ${theme.animation.easing.cubic},
    margin-left ${theme.animation.duration.normal} ${theme.animation.easing.cubic};

  @media (prefers-reduced-motion: reduce) {
    transition: none;
  }

  @media (max-width: ${theme.breakpoints.md}) {
    width: 100vw;
    margin-left: 0;
  }
`;

const ContentArea = styled.main<{ $maxWidth?: string; $padding?: string }>`
  box-sizing: border-box;
  width: 100%;
  max-width: ${(props) => props.$maxWidth || '1440px'};
  min-width: 0;
  margin: 0 auto;
  padding: ${(props) => props.$padding || '24px'};
  overflow-x: hidden;

  @media (max-width: 1280px) {
    padding: 16px;
  }
`;

export interface OpsPageShellProps {
  selectedKey: string;
  children: React.ReactNode;
  maxWidth?: string;
  padding?: string;
  defaultCollapsed?: boolean;
  onSelect?: (key: string) => void;
  onLogout?: () => void;
}

export const OpsPageShell: React.FC<OpsPageShellProps> = ({
  selectedKey,
  children,
  maxWidth,
  padding,
  defaultCollapsed,
  onSelect,
  onLogout,
}) => {
  const navigate = useNavigate();
  const { mobile, mobileOpen, collapsed, sidebarCollapsed, toggleSidebar, closeMobileSidebar } =
    useResponsiveSidebar(defaultCollapsed);

  useEffect(() => {
    if (!mobile || !mobileOpen) return undefined;
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        event.preventDefault();
        closeMobileSidebar();
      }
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [closeMobileSidebar, mobile, mobileOpen]);

  const handleNavigation = (key: string) => {
    closeMobileSidebar();
    if (onSelect) {
      onSelect(key);
      return;
    }
    navigate(key.startsWith('/') ? key : `/${key}`);
  };

  const handleLogout = () => {
    clearAuthSession();
    Toast.success('已退出登录');
    if (onLogout) {
      onLogout();
      return;
    }
    navigate('/login');
  };

  return (
    <PageLayout>
      <SkipLink href="#main-content">Skip to main content</SkipLink>
      <Sidebar
        selectedKey={selectedKey}
        onSelect={handleNavigation}
        collapsed={sidebarCollapsed}
        onMobileClose={closeMobileSidebar}
      />
      <MainContent $collapsed={collapsed} data-ops-scroll-region="true">
        <Header collapsed={sidebarCollapsed} onToggleSidebar={toggleSidebar} onLogout={handleLogout} />
        <ContentArea id="main-content" tabIndex={-1} $maxWidth={maxWidth} $padding={padding}>{children}</ContentArea>
      </MainContent>
    </PageLayout>
  );
};

export const OpsShellMainContent = MainContent;
export const OpsShellContentArea = ContentArea;
