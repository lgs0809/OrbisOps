import React from 'react';
import styled from 'styled-components';
import { Typography } from '@douyinfe/semi-ui';
import { IconSetting } from '@douyinfe/semi-icons';

import { theme } from '../../../styles/theme';
import {
  AgentBuilderMode,
  AgentBuilderPanel,
  isAgentBuilderAdvanced,
} from '../model/agent-builder-mode';

const { Text } = Typography;

const Panel = styled.div`
  border: 1px solid ${theme.colors.border.secondary};
  border-radius: ${theme.borderRadius.base};
  background: ${theme.colors.bg.primary};
  overflow: hidden;
  min-width: 0;
  grid-column: 1 / -1;
`;

const PanelHeader = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: ${theme.spacing.sm};
  padding: ${theme.spacing.base};
  border-bottom: 1px solid ${theme.colors.border.secondary};
  min-width: 0;
`;

const PanelTitle = styled.div`
  display: flex;
  align-items: center;
  gap: ${theme.spacing.sm};
  font-weight: ${theme.typography.fontWeight.semibold};
  min-width: 0;
`;

const TabBar = styled.div<{ $count: number }>`
  display: grid;
  grid-template-columns: repeat(${(props) => props.$count}, minmax(0, 1fr));
  border-bottom: 1px solid ${theme.colors.border.secondary};
`;

const TabButton = styled.button<{ $active?: boolean }>`
  border: 0;
  border-right: 1px solid ${theme.colors.border.secondary};
  background: ${(props) => (props.$active ? theme.colors.bg.secondary : theme.colors.bg.primary)};
  color: ${(props) => (props.$active ? theme.colors.primary : theme.colors.text.secondary)};
  padding: 10px 8px;
  cursor: pointer;
  font: inherit;

  &:last-child {
    border-right: 0;
  }
`;

const PanelBody = styled.div`
  padding: ${theme.spacing.base};
  min-width: 0;
`;

type Props = {
  mode: AgentBuilderMode;
  activePanel: AgentBuilderPanel;
  onPanelChange: (panel: AgentBuilderPanel) => void;
  nodePanel: React.ReactNode;
  edgePanel: React.ReactNode;
  jsonPanel: React.ReactNode;
  testPanel: React.ReactNode;
};

export const AgentBuilderPropertyPanel: React.FC<Props> = ({
  mode,
  activePanel,
  onPanelChange,
  nodePanel,
  edgePanel,
  jsonPanel,
  testPanel,
}) => {
  const advanced = isAgentBuilderAdvanced(mode);

  return (
    <Panel>
      <PanelHeader>
        <PanelTitle>
          <IconSetting />
          <Text strong>{advanced ? '节点与连接配置' : '节点能力与测试'}</Text>
        </PanelTitle>
      </PanelHeader>
      <TabBar $count={advanced ? 4 : 2}>
        <TabButton $active={activePanel === 'node'} onClick={() => onPanelChange('node')}>节点能力</TabButton>
        {advanced && (
          <TabButton $active={activePanel === 'edge'} onClick={() => onPanelChange('edge')}>连接与路由</TabButton>
        )}
        {advanced && (
          <TabButton $active={activePanel === 'json'} onClick={() => onPanelChange('json')}>高级 JSON</TabButton>
        )}
        <TabButton $active={activePanel === 'test'} onClick={() => onPanelChange('test')}>测试</TabButton>
      </TabBar>
      <PanelBody>
        {activePanel === 'node' && nodePanel}
        {advanced && activePanel === 'edge' && edgePanel}
        {advanced && activePanel === 'json' && jsonPanel}
        {activePanel === 'test' && testPanel}
      </PanelBody>
    </Panel>
  );
};
