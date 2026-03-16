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
  ['1. 主助手是默认入口', '未显式选择专项流程时，始终使用项目默认智能诊断，不会根据自然语言偷偷切换 Workflow。'],
  ['2. Workflow 只服务固定场景', '为巡检、固定诊断、周报等重复任务编排节点；临时问题无需创建 Workflow。'],
  ['3. 说明适用与禁用场景', '“何时使用”和“何时不用”只帮助用户选择专项流程，不参与运行时自动路由。'],
  ['4. 画布定义内部执行', 'Start/Agent/Router/End 表达执行顺序，Skill、RAG 和 MCP 绑定在需要它们的节点。'],
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
