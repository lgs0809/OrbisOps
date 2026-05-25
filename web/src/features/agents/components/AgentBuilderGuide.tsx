import React from 'react';
import styled from 'styled-components';
import { Typography } from '@douyinfe/semi-ui';

import { theme } from '../../../styles/theme';

const { Text } = Typography;

const GuideStrip = styled.div`
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: ${theme.spacing.base};
  min-width: 0;

  @media (max-width: 1280px) {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  @media (max-width: 760px) {
    grid-template-columns: 1fr;
  }
`;

const GuideItem = styled.div`
  padding: ${theme.spacing.base};
  border: 1px solid ${theme.colors.border.secondary};
  border-radius: ${theme.borderRadius.base};
  background: ${theme.colors.bg.primary};
  min-width: 0;
`;

const GuideTitle = styled.div`
  margin-bottom: 4px;
  color: ${theme.colors.text.primary};
  font-weight: ${theme.typography.fontWeight.semibold};
`;

const ITEMS = [
  ['1. 默认助手是默认入口', '除非用户显式选择工作流，否则请求由默认助手处理；自然语言表述不会静默切换执行方式。'],
  ['2. Workflow 用于可重复流程', '巡检、固定诊断、报告等稳定重复任务适合使用 Workflow；临时性工作无需先创建 Workflow。'],
  ['3. 说明 Workflow 的适用边界', '适用与排除说明用于帮助人选择 Workflow，不参与 Runtime 自动路由。'],
  ['4. 画布定义内部执行过程', 'Start / Agent / Router / End 定义执行顺序；Skill、RAG、MCP 能力绑定在真正需要它们的 Agent Node 上。'],
] as const;

export const AgentBuilderGuide: React.FC = () => (
  <GuideStrip>
    {ITEMS.map(([title, description]) => (
      <GuideItem key={title}>
        <GuideTitle>{title}</GuideTitle>
        <Text type="secondary" size="small">{description}</Text>
      </GuideItem>
    ))}
  </GuideStrip>
);
