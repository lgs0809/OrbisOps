import styled from 'styled-components';

import { theme } from '../../styles/theme';

export const OpsResponsiveGrid = styled.div<{ $min?: string; $gap?: string }>`
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(${(props) => props.$min || '240px'}, 100%), 1fr));
  gap: ${(props) => props.$gap || theme.spacing.base};
  width: 100%;
  min-width: 0;
`;

export const OpsTwoColumnGrid = styled.div`
  display: grid;
  grid-template-columns: minmax(240px, 320px) minmax(0, 1fr);
  gap: ${theme.spacing.base};
  width: 100%;
  min-width: 0;

  @media (max-width: ${theme.breakpoints.lg}) {
    grid-template-columns: minmax(0, 1fr);
  }
`;

export const TableScroll = styled.div<{ $minWidth?: string }>`
  width: 100%;
  max-width: 100%;
  min-width: 0;
  overflow-x: auto;
  overscroll-behavior-x: contain;

  > .semi-table-wrapper {
    width: 100%;
    min-width: ${(props) => props.$minWidth || '680px'};
    max-width: none;
  }

  .semi-table-container,
  .semi-table {
    max-width: none;
  }

  .semi-table-cell {
    overflow-wrap: normal;
    word-break: normal;
  }
`;
