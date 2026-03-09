import { createGlobalStyle } from 'styled-components';

import { theme } from './theme';

export const GlobalStyle = createGlobalStyle`
  *,
  *::before,
  *::after {
    box-sizing: border-box;
  }

  html,
  body,
  #root {
    width: 100%;
    min-width: 0;
    min-height: 100%;
    margin: 0;
    background: ${theme.colors.bg.secondary};
    overflow-x: hidden;
  }

  body {
    color: ${theme.colors.text.primary};
    font-family: ${theme.typography.fontFamily};
  }

  .semi-layout,
  .semi-layout-content,
  .semi-layout-header,
  .semi-layout-footer,
  .semi-tabs,
  .semi-tabs-content,
  .semi-tabs-pane,
  .semi-card,
  .semi-card-body,
  .semi-form,
  .semi-form-field,
  .semi-modal,
  .semi-modal-body,
  .semi-spin,
  .semi-spin-wrapper {
    min-width: 0 !important;
    max-width: 100%;
  }

  .semi-layout-content {
    overflow-x: hidden;
  }

  .semi-modal-content {
    display: flex;
    max-height: min(92vh, 860px);
    flex-direction: column;
  }

  .semi-modal-body {
    flex: 1 1 auto;
    min-height: 0;
    overflow-y: auto;
  }

  .semi-table-wrapper,
  .semi-table-container,
  .semi-table,
  pre,
  code,
  textarea,
  .semi-input-textarea-wrapper,
  .semi-code-highlight {
    max-width: 100%;
    overflow-x: auto;
  }

  .semi-table-wrapper,
  .semi-table-container {
    width: 100%;
  }

  .semi-typography,
  .semi-typography-paragraph,
  .semi-input,
  .semi-input-wrapper {
    min-width: 0;
    overflow-wrap: break-word;
    word-break: normal;
  }

  .semi-table-cell,
  .semi-tag,
  .semi-tag-content,
  .semi-select-selection-text {
    min-width: 0;
    overflow-wrap: normal;
    word-break: normal;
  }

  .semi-button {
    white-space: nowrap;
  }

  .semi-space {
    min-width: 0;
    max-width: 100%;
  }

  [data-ops-scroll-region='true'] {
    min-width: 0;
    max-width: 100%;
    overflow-x: hidden;
  }
`;
