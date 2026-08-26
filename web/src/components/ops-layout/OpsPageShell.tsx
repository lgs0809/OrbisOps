import React, { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import styled from 'styled-components';
import { Layout, Toast } from '@douyinfe/semi-ui';

import { Header, Sidebar, SIDEBAR_COLLAPSED_WIDTH, SIDEBAR_WIDTH } from '../layout';
import { clearAuthSession } from '../../services/auth-session';
import { theme } from '../../styles/theme';
import { useResponsiveSidebar } from '../../hooks/use-responsive-sidebar';

const PageLayout = styled(Layout)`
  height: 100dvh;
  min-height: 0;
  min-width: 0;
  width: 100%;
  background: ${theme.colors.bg.secondary};
  overflow: hidden;
`;

const SkipLink = styled.a`
  position: fixed;
  top: 8px;
  left: 8px;
  z-index: 2000;
  transform: translateY(-160%);
  padding: 8px 12px;
  border-radius: 8px;
  background: #fff;
  color: ${theme.colors.primary};
  box-shadow: ${theme.shadows.md};

  &:focus {
    transform: translateY(0);
    outline: 2px solid ${theme.colors.primary};
    outline-offset: 2px;
  }
`;

const MainContent = styled.div<{ $collapsed: boolean }>`
  height: 100dvh;
  min-height: 0;
  min-width: 0;
  width: ${(props) =>
    props.$collapsed
      ? `calc(100vw - ${SIDEBAR_COLLAPSED_WIDTH}px)`
      : `calc(100vw - ${SIDEBAR_WIDTH}px)`};
  margin-left: ${(props) => (props.$collapsed ? `${SIDEBAR_COLLAPSED_WIDTH}px` : `${SIDEBAR_WIDTH}px`)};
  display: flex;
  flex: 1;
  flex-direction: column;
  background: ${theme.colors.bg.secondary};
  overflow-x: hidden;
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
  max-width: ${(props) => props.$maxWidth || 'none'};
  min-width: 0;
  min-height: 0;
  flex: 1;
  margin: 0 auto;
  padding: ${(props) => props.$padding || '24px 28px 40px'};
  overflow-x: hidden;
  overflow-y: auto;

  @media (max-width: 1280px) {
    padding: ${(props) => props.$padding || '20px 20px 32px'};
  }

  @media (max-width: ${theme.breakpoints.sm}) {
    padding: ${(props) => props.$padding || '16px 12px 28px'};
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
    Toast.success('已退出登录。');
    if (onLogout) {
      onLogout();
      return;
    }
    navigate('/login');
  };

  return (
    <PageLayout>
      <SkipLink href="#main-content">跳到主要内容</SkipLink>
      <Sidebar
        selectedKey={selectedKey}
        onSelect={handleNavigation}
        collapsed={sidebarCollapsed}
        onMobileClose={closeMobileSidebar}
        onLogout={handleLogout}
      />
      <MainContent $collapsed={collapsed} data-ops-scroll-region="true">
        <Header collapsed={sidebarCollapsed} onToggleSidebar={toggleSidebar} onLogout={handleLogout} />
        <ContentArea id="main-content" tabIndex={-1} $maxWidth={maxWidth} $padding={padding}>
          {children}
        </ContentArea>
      </MainContent>
    </PageLayout>
  );
};

export const OpsShellMainContent = MainContent;
export const OpsShellContentArea = ContentArea;
