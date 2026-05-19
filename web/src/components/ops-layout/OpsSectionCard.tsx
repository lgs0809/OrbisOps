import styled from 'styled-components';
import { Card } from '@douyinfe/semi-ui';

import { theme } from '../../styles/theme';

export const OpsSectionCard = styled(Card)`
  width: 100%;
  min-width: 0;
  margin-bottom: 16px;
  border: 1px solid ${theme.colors.border.secondary};
  border-radius: 12px;
  background: ${theme.colors.bg.primary};
  box-shadow: none;
  overflow: hidden;

  .semi-card-body {
    min-width: 0;
    padding: 18px 20px 20px;
  }

  .semi-card-header {
    min-width: 0;
    padding: 16px 20px 0;
    border-bottom: 0;
  }

  .semi-card-header-title {
    color: ${theme.colors.text.primary};
    font-size: 15px;
    font-weight: 650;
  }
`;
