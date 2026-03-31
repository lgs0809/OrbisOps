import React from 'react';
import { Typography } from '@douyinfe/semi-ui';
import styled from 'styled-components';

import { OpsSectionCard } from '../../../components/ops-layout';
import { theme } from '../../../styles/theme';

const Flow = styled.div`
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: ${theme.spacing.base};
  padding: ${theme.spacing.lg};
  min-width: 0;

  @media (max-width: 960px) {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  @media (max-width: 640px) {
    grid-template-columns: minmax(0, 1fr);
  }
`;

const Step = styled.div`
  min-width: 0;
  padding: ${theme.spacing.base};
  border: 1px solid ${theme.colors.border.secondary};
  border-radius: ${theme.borderRadius.base};
  background: ${theme.colors.bg.secondary};
`;

const HelperText = styled(Typography.Text)`
  display: block;
  margin-top: ${theme.spacing.sm};
  line-height: 1.6;
`;

const steps = [
  ['1. 新建知识库', '创建稳定的知识库名称和标签，它是文档、策略和项目授权的容器。'],
  ['2. 导入文档', '导入 Markdown/PDF，并选择同一个知识库名称/标签，系统会解析、切片和入库。'],
  ['3. 配置检索策略', '按知识库设置 embedding、rerank、召回条数和过滤规则。'],
  ['4. 授权给项目', '项目启用后，Agent 才能在当前项目上下文中读取对应知识。'],
] as const;

export const KnowledgeLifecycleFlow: React.FC = () => (
  <OpsSectionCard title="知识库使用流程">
    <Flow>
      {steps.map(([title, description]) => (
        <Step key={title}>
          <Typography.Text strong>{title}</Typography.Text>
          <HelperText type="tertiary">{description}</HelperText>
        </Step>
      ))}
    </Flow>
  </OpsSectionCard>
);
