import React from 'react';
import styled from 'styled-components';
import { Button, Card, Space, Tag, Typography } from '@douyinfe/semi-ui';
import { IconBranch, IconEdit, IconPlus, IconSetting } from '@douyinfe/semi-icons';

import {
  OpsProjectResource,
  OpsProjectResourceSchemaObject,
} from '../../../services/ops-project-service';
import { theme } from '../../../styles/theme';

const { Text, Paragraph } = Typography;
type TagColor = React.ComponentProps<typeof Tag>['color'];

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

const SchemaList = styled.div`
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: ${theme.spacing.base};
`;

const SchemaRow = styled.div`
  border: 1px solid ${theme.colors.border.secondary};
  border-radius: ${theme.borderRadius.base};
  padding: 10px 12px;
  background: #f8fafc;
`;

type Props = {
  resources: OpsProjectResource[];
  onAdd: () => void;
  onEdit: (resource: OpsProjectResource) => void;
  onPermission: (resource: OpsProjectResource) => void;
  onGenerate: (resource: OpsProjectResource) => void;
  typeColor: (type: string) => TagColor;
  statusColor: (status: string) => TagColor;
  credentialLabel: (resource: OpsProjectResource) => string;
  schemaSourceLabel: (source?: string) => string;
  schemaObjectMeta: (item: OpsProjectResourceSchemaObject) => string;
};

export const ProjectDataConnectionsSection: React.FC<Props> = ({
  resources,
  onAdd,
  onEdit,
  onPermission,
  onGenerate,
  typeColor,
  statusColor,
  credentialLabel,
  schemaSourceLabel,
  schemaObjectMeta,
}) => (
  <Section title="数据连接与工具">
    <ResourceTitle style={{ marginBottom: 16 }}>
      <div>
        <Text strong>Agent 可查询的数据源</Text>
        <div><Text type="tertiary" size="small">连接保存地址与凭据；可见对象和允许动作决定生成的工具能访问哪些数据。</Text></div>
      </div>
      <Button theme="solid" icon={<IconPlus />} onClick={onAdd}>添加连接</Button>
    </ResourceTitle>
    <ResourceGrid>
      {resources.map((resource) => (
        <Card key={resource.resourceId} shadows="hover">
          <ResourceTitle>
            <Space>
              <IconBranch />
              <div>
                <Text strong>{resource.name}</Text>
                <div>
                  <Tag color={typeColor(resource.type)}>{resource.typeName}</Tag>
                  <Tag>{resource.environment}</Tag>
                </div>
              </div>
            </Space>
            <Tag color={statusColor(resource.status)}>{resource.status}</Tag>
          </ResourceTitle>
          <Paragraph type="tertiary" style={{ marginTop: 12 }}>{resource.endpoint}</Paragraph>
          <Space wrap style={{ marginBottom: 10 }}>
            <Tag color={resource.credential?.configured ? 'green' : 'grey'}>{credentialLabel(resource)}</Tag>
            <Tag color={resource.schema?.source === 'live' ? 'blue' : 'grey'}>{schemaSourceLabel(resource.schema?.source)}</Tag>
          </Space>
          {resource.schema?.message && <Paragraph type="tertiary" style={{ marginTop: 0, marginBottom: 10 }}>{resource.schema.message}</Paragraph>}
          <Space wrap>
            {(resource.permission?.actions || []).map((action) => <Tag key={action}>{action}</Tag>)}
          </Space>
          <SchemaList>
            {(resource.schema?.objects || []).slice(0, 3).map((item) => (
              <SchemaRow key={item.name}>
                <Text strong>{item.name}</Text>
                <div><Text type="tertiary" size="small">{schemaObjectMeta(item)}</Text></div>
              </SchemaRow>
            ))}
          </SchemaList>
          <Space style={{ marginTop: 14 }}>
            <Button icon={<IconEdit />} onClick={() => onEdit(resource)}>编辑</Button>
            <Button icon={<IconSetting />} onClick={() => onPermission(resource)}>权限</Button>
            <Button theme="solid" onClick={() => onGenerate(resource)}>生成项目工具</Button>
          </Space>
        </Card>
      ))}
    </ResourceGrid>
  </Section>
);
