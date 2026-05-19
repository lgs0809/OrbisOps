import styled from 'styled-components';

import { theme } from '../styles/theme';

/**
 * Sticky, low-noise navigation for the advanced project setup journey.
 * It intentionally owns presentation only; the page keeps section discovery/scroll semantics.
 */
export const SetupNav = styled.nav`
  position: sticky;
  top: 8px;
  z-index: 20;
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  padding: 8px 10px;
  margin-bottom: ${theme.spacing.base};
  border: 1px solid ${theme.colors.border.secondary};
  border-radius: ${theme.borderRadius.base};
  background: rgba(255, 255, 255, 0.96);
  box-shadow: ${theme.shadows.sm};

  @media (max-width: ${theme.breakpoints.md}) {
    position: static;
  }
`;
