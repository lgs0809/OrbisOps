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

  html {
    font-size: 15px;
    text-rendering: optimizeLegibility;
    -webkit-font-smoothing: antialiased;
  }

  body {
    color: ${theme.colors.text.primary};
    font-family: ${theme.typography.fontFamily};
  }

  ::selection {
    background: #dbeafe;
    color: #172554;
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

  .semi-card {
    border-color: ${theme.colors.border.secondary};
    border-radius: 12px;
    box-shadow: ${theme.shadows.sm};
  }

  .semi-modal-content {
    display: flex;
    max-height: min(92vh, 860px);
    flex-direction: column;
    border-radius: 14px;
    box-shadow: ${theme.shadows.modal};
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

  .semi-table-thead > .semi-table-row > .semi-table-row-head {
    background: #fafbfc;
    color: ${theme.colors.text.secondary};
    font-size: 12px;
    font-weight: 600;
    border-bottom-color: ${theme.colors.border.secondary};
  }

  .semi-table-tbody > .semi-table-row > .semi-table-row-cell {
    border-bottom-color: ${theme.colors.border.tertiary};
  }

  .semi-table-tbody > .semi-table-row:hover > .semi-table-row-cell {
    background: #fafbfc;
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

  .semi-input-wrapper,
  .semi-input-textarea-wrapper,
  .semi-select {
    border-radius: 9px;
  }

  .semi-button {
    min-height: 36px;
    border-radius: 9px;
    white-space: nowrap;
    font-weight: 500;
  }

  .semi-button-primary {
    box-shadow: none;
  }

  .semi-tabs-bar {
    border-bottom-color: ${theme.colors.border.secondary};
  }

  .semi-tabs-tab {
    font-size: 14px;
  }

  .semi-tag {
    border-radius: 999px;
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

  button:focus-visible,
  a:focus-visible {
    outline: 2px solid ${theme.colors.primary};
    outline-offset: 2px;
  }

  .semi-input-wrapper,
  .semi-input-textarea-wrapper,
  .semi-select {
    border: 1px solid ${theme.colors.border.primary};
    background: ${theme.colors.bg.primary};
  }

  .semi-table-tbody > .semi-table-row > .semi-table-row-cell {
    font-size: 13px;
    line-height: 1.6;
  }
`;
