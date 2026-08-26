import styled from 'styled-components';
import { theme } from '../../styles/theme';

export const SIDEBAR_WIDTH = 216;
export const SIDEBAR_COLLAPSED_WIDTH = 76;

export const SidebarContainer = styled.aside<{ $collapsed: boolean }>`
  position: fixed;
  inset: 0 auto 0 0;
  z-index: 1000;
  width: ${(p) => p.$collapsed ? SIDEBAR_COLLAPSED_WIDTH : SIDEBAR_WIDTH}px;
  display: flex;
  flex-direction: column;
  padding: 18px 10px 12px;
  border-right: 1px solid ${theme.colors.border.secondary};
  background: #f5f6f8;
  overflow: hidden;
  transition: width 0.2s ease;

  @media (max-width: ${theme.breakpoints.md}) {
    width: 248px;
    max-width: 88vw;
    transform: ${(p) => p.$collapsed ? 'translateX(-100%)' : 'translateX(0)'};
    visibility: ${(p) => p.$collapsed ? 'hidden' : 'visible'};
    pointer-events: ${(p) => p.$collapsed ? 'none' : 'auto'};
    box-shadow: ${(p) => p.$collapsed ? 'none' : theme.shadows.lg};
  }

  @media (prefers-reduced-motion: reduce) { transition: none; }
`;

export const SidebarBackdrop = styled.button<{ $visible: boolean }>`
  display: none;
  @media (max-width: ${theme.breakpoints.md}) {
    display: ${(p) => p.$visible ? 'block' : 'none'};
    position: fixed;
    inset: 0;
    z-index: 999;
    padding: 0;
    border: 0;
    background: rgb(15 23 42 / 36%);
  }
`;

export const Brand = styled.div<{ $collapsed: boolean }>`
  height: 44px;
  display: flex;
  align-items: center;
  justify-content: ${(p) => p.$collapsed ? 'center' : 'flex-start'};
  gap: 11px;
  padding: 0 8px;
  flex-shrink: 0;
  @media (max-width: ${theme.breakpoints.md}) { justify-content: flex-start; }
`;

export const Mark = styled.div`
  width: 34px;
  height: 34px;
  flex-shrink: 0;
  display: grid;
  place-items: center;
  border-radius: 10px;
  background: #172338;
  color: white;
  .semi-icon { font-size: 19px; }
`;

export const BrandCopy = styled.div<{ $collapsed: boolean }>`
  display: ${(p) => p.$collapsed ? 'none' : 'block'};
  strong { display: block; font-size: 18px; letter-spacing: -0.035em; }
  span { display: block; margin-top: 2px; color: ${theme.colors.text.secondary}; font-size: 11px; }
  @media (max-width: ${theme.breakpoints.md}) { display: block; }
`;

export const SearchSlot = styled.div<{ $collapsed: boolean }>`
  display: flex;
  justify-content: center;
  margin: 20px 0 12px;
  .semi-button { width: 100%; min-width: 0; gap: 8px; justify-content: flex-start; }
  .command-search-shortcut { display: none; }
  @media (max-width: ${theme.breakpoints.md}) {
    .semi-button { height: 40px; flex-direction: row; justify-content: flex-start; padding-left: 11px; }
  }
`;

export const NavScroll = styled.nav`
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overflow-x: hidden;
`;

export const NavDivider = styled.div`
  height: 1px;
  margin: 16px 8px;
  background: ${theme.colors.border.secondary};
`;

export const NavItem = styled.button<{ $active: boolean; $collapsed: boolean }>`
  width: 100%;
  min-height: ${(p) => p.$collapsed ? '55px' : '44px'};
  display: flex;
  flex-direction: ${(p) => p.$collapsed ? 'column' : 'row'};
  align-items: center;
  justify-content: ${(p) => p.$collapsed ? 'center' : 'flex-start'};
  gap: ${(p) => p.$collapsed ? '4px' : '12px'};
  margin: 3px 0;
  padding: 7px 12px;
  border: 0;
  border-radius: 9px;
  background: ${(p) => p.$active ? '#e5ecf7' : 'transparent'};
  color: ${(p) => p.$active ? '#1b4f9f' : theme.colors.text.secondary};
  font: inherit;
  font-size: ${(p) => p.$collapsed ? '10px' : '14px'};
  font-weight: ${(p) => p.$active ? 600 : 500};
  text-align: left;
  cursor: pointer;
  .semi-icon { flex-shrink: 0; font-size: 19px; }
  span { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  &:hover { background: ${(p) => p.$active ? '#dce7f7' : '#eaeef3'}; }
  &:focus-visible { outline: 2px solid ${theme.colors.primary}; outline-offset: -2px; }
  @media (max-width: ${theme.breakpoints.md}) {
    min-height: 44px;
    flex-direction: row;
    justify-content: flex-start;
    gap: 12px;
    font-size: 14px;
  }
`;

export const Footer = styled.div`
  flex-shrink: 0;
  padding-top: 12px;
  margin-top: 12px;
  border-top: 1px solid ${theme.colors.border.secondary};
`;

export const UserTrigger = styled.button<{ $collapsed: boolean }>`
  width: 100%;
  min-height: 46px;
  display: flex;
  align-items: center;
  justify-content: ${(p) => p.$collapsed ? 'center' : 'flex-start'};
  gap: 10px;
  padding: 7px;
  border: 0;
  border-radius: 9px;
  background: transparent;
  color: inherit;
  font: inherit;
  cursor: pointer;
  &:hover { background: #eaeef3; }
  &:focus-visible { outline: 2px solid ${theme.colors.primary}; }
  .user-copy { display: ${(p) => p.$collapsed ? 'none' : 'block'}; min-width: 0; text-align: left; }
  strong { display: block; overflow: hidden; font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }
  .user-copy span { display: block; margin-top: 2px; color: ${theme.colors.text.secondary}; font-size: 11px; }
  @media (max-width: ${theme.breakpoints.md}) {
    justify-content: flex-start;
    .user-copy { display: block; }
  }
`;
