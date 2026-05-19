import styled from 'styled-components';

import { theme } from '../../styles/theme';

export const OpsActionBar = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: ${theme.spacing.base};
  flex-wrap: wrap;
  min-width: 0;
  margin-bottom: ${theme.spacing.base};
`;

export const OpsActionGroup = styled.div`
  display: flex;
  align-items: center;
  gap: ${theme.spacing.sm};
  flex-wrap: wrap;
  min-width: 0;
`;
