import React from 'react';
import styled from 'styled-components';
import { Button, Card, Space, Tag, Typography } from '@douyinfe/semi-ui';

import { RagKnowledgeBaseSummary } from '../../../services/ai-client-rag-order-admin-service';
import { theme } from '../../../styles/theme';

const { Text } = Typography;

const Section = styled(Card)`
  margin-bottom: ${theme.spacing.lg};
  .semi-card-body { padding: ${theme.spacing.lg}; }
`;

const Header = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: ${theme.spacing.base};
  flex-wrap: wrap;
`;

const Grid = styled.div`
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(280px, 100%), 1fr));
  gap: ${theme.spacing.base};
  margin-top: ${theme.spacing.base};
`;

type Props = {
  projectId: string;
  knowledgeIds: string[];
  details: RagKnowledgeBaseSummary[];
  knowledgeFields: Array<[string, unknown]>;
  onCreate: () => void;
  onEnableCommon: () => void;
  onImportDocument: () => void;
  onBindAgent: () => void;
  onPreviewAll: () => void;
  onEdit: (knowledge: RagKnowledgeBaseSummary) => void;
  onToggleStatus: (knowledge: RagKnowledgeBaseSummary) => void;
  onPreviewOne: (knowledgeId: string) => void;
};

export const ProjectKnowledgeBasesSection: React.FC<Props> = ({
  projectId,
  knowledgeIds,
  details,
  knowledgeFields,
  onCreate,
  onEnableCommon,
  onImportDocument,
  onBindAgent,
  onPreviewAll,
  onEdit,
  onToggleStatus,
  onPreviewOne,
}) => (
  <Section title="项目知识库">
    <Header>
      <div>
        <Text strong>项目专属文档与授权知识范围</Text>
        <div>
          <Text type="tertiary" size="small">
            项目知识库保存当前项目专属文档，也可以显式启用通用知识库；Agent 运行时只读取当前项目授权范围。
          </Text>
        </div>
      </div>
      <Space wrap>
        <Button onClick={onCreate}>新建项目知识库</Button>
        <Button onClick={onEnableCommon}>从通用知识库启用</Button>
        <Button disabled={!knowledgeIds.length} onClick={onImportDocument}>导入项目文档</Button>
        <Button onClick={onBindAgent}>绑定 Agent</Button>
        <Button onClick={onPreviewAll}>高级预览</Button>
      </Space>
    </Header>
    <Grid>
      {knowledgeIds.map((knowledgeId) => {
        const knowledge = details.find((item) => (item.kbId || item.knowledgeTag || item.ragId) === knowledgeId);
        const projectOwned = Boolean(
          knowledge
          && (String(knowledge.scope || '').toUpperCase() === 'PROJECT' || knowledge.projectId === projectId),
        );
        const enabled = String(knowledge?.status || '').toUpperCase() === 'ENABLED' || knowledge?.status === 1;
        const sourceFields = knowledgeFields
          .filter(([, value]) => value === knowledgeId || (Array.isArray(value) && value.map(String).includes(knowledgeId)))
          .map(([key]) => key)
          .join(', ') || 'knowledge / rag / kb';
        return (
          <Card key={knowledgeId}>
            <Space vertical align="start" style={{ width: '100%' }}>
              <Header>
                <Text strong>{knowledge?.kbName || knowledgeId}</Text>
                <Tag color={enabled ? 'green' : 'grey'}>
                  {projectOwned ? (enabled ? '项目知识库 · 启用' : '项目知识库 · 停用') : '已授权'}
                </Tag>
              </Header>
              <Text type="tertiary" size="small">来源字段：{sourceFields}</Text>
              <Space wrap>
                <Button size="small" onClick={onBindAgent}>绑定 Agent</Button>
                {projectOwned && knowledge && <Button size="small" onClick={() => onEdit(knowledge)}>编辑</Button>}
                {projectOwned && knowledge && (
                  <Button size="small" type={enabled ? 'danger' : 'primary'} theme="borderless" onClick={() => onToggleStatus(knowledge)}>
                    {enabled ? '停用' : '启用'}
                  </Button>
                )}
                <Button size="small" onClick={() => onPreviewOne(knowledgeId)}>高级预览</Button>
              </Space>
            </Space>
          </Card>
        );
      })}
      {!knowledgeIds.length && (
        <Card>
          <Space vertical align="start">
            <Text strong>暂无项目知识库或通用知识库授权。</Text>
            <Text type="tertiary" size="small">可以新建项目知识库，或从项目默认能力中显式启用通用知识库。</Text>
          </Space>
        </Card>
      )}
    </Grid>
  </Section>
);
