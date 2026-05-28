import React from 'react';
import styled from 'styled-components';
import { Button, Card, Space, Tag, Typography } from '@douyinfe/semi-ui';
import { IconEyeOpened } from '@douyinfe/semi-icons';

import { OpsGeneratedMcp } from '../../../services/ops-project-service';
import { theme } from '../../../styles/theme';

const { Text, Paragraph } = Typography;

const Section = styled(Card)`
  margin-bottom: ${theme.spacing.lg};

  .semi-card-body {
    padding: ${theme.spacing.lg};
  }
`;

const ResourceTitle = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: ${theme.spacing.base};
  flex-wrap: wrap;
`;

const ResourceGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(280px, 100%), 1fr));
  gap: ${theme.spacing.base};
`;

type Props = {
  tools: OpsGeneratedMcp[];
  onPreview: (tool: OpsGeneratedMcp) => void;
};

export const ProjectGeneratedToolsSection: React.FC<Props> = ({ tools, onPreview }) => (
  <Section title="已生成工具">
    <ResourceGrid>
      {tools.map((tool) => (
        <Card key={tool.mcpId}>
          <ResourceTitle>
            <div>
              <Text strong>{tool.toolName || tool.mcpName}</Text>
              <div><Text type="tertiary" size="small">{tool.toolId || tool.mcpId}</Text></div>
            </div>
            <Space>
              {tool.templateId && <Tag color="blue">{tool.templateId}</Tag>}
              <Tag color={tool.status === 'ENABLED' ? 'green' : 'orange'}>{tool.status}</Tag>
            </Space>
          </ResourceTitle>
          <Paragraph type="tertiary">
            {tool.resourceType} · {tool.transportType} · 权限以平台审核策略为准 · 超时 {tool.requestTimeout}s
          </Paragraph>
          <Button icon={<IconEyeOpened />} onClick={() => onPreview(tool)}>查看配置</Button>
        </Card>
      ))}
      {!tools.length && <Text type="tertiary">还没有项目工具。先添加数据连接、限制可见对象和动作，再生成工具。</Text>}
    </ResourceGrid>
  </Section>
);
