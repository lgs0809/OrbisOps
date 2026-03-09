import styled from 'styled-components';
import { Card } from '@douyinfe/semi-ui';

import { theme } from '../../styles/theme';

export const OpsSectionCard = styled(Card)`
  width: 100%;
  min-width: 0;
  margin-bottom: ${theme.spacing.lg};
  border: 1px solid ${theme.colors.border.secondary};
  box-shadow: ${theme.shadows.sm};

  .semi-card-body {
    min-width: 0;
    padding: ${theme.spacing.lg};
  }

  .semi-card-header {
    min-width: 0;
  }
`;
