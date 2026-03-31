import React from 'react';
import styled from 'styled-components';

import { theme } from '../../../styles/theme';
import type { KnowledgeOverview as KnowledgeOverviewModel } from '../model/knowledge-model';

const Grid = styled.div`
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: ${theme.spacing.base};
  min-width: 0;
`;

const Tile = styled.div`
  border: 1px solid ${theme.colors.border.secondary};
  border-radius: ${theme.borderRadius.base};
  background: ${theme.colors.bg.primary};
  padding: ${theme.spacing.base};
`;

const Label = styled.div`
  color: ${theme.colors.text.tertiary};
  font-size: ${theme.typography.fontSize.sm};
  margin-bottom: ${theme.spacing.xs};
`;

const Value = styled.div`
  color: ${theme.colors.text.primary};
  font-size: ${theme.typography.fontSize.xl};
  font-weight: ${theme.typography.fontWeight.semibold};
`;

const metrics: Array<[keyof KnowledgeOverviewModel, string]> = [
  ['chunkCount', '结构化片段'],
  ['documentCount', '文档来源'],
  ['knowledgeCount', '通用知识库'],
  ['enabledCount', '已启用'],
  ['tagCount', '场景标签'],
  ['activeJobs', '入库任务中'],
];

export const KnowledgeOverview: React.FC<{ overview: KnowledgeOverviewModel }> = ({ overview }) => (
  <Grid>
    {metrics.map(([key, label]) => (
      <Tile key={key}>
        <Label>{label}</Label>
        <Value>{overview[key]}</Value>
      </Tile>
    ))}
  </Grid>
);
