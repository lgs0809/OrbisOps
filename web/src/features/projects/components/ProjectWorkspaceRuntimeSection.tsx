import React from 'react';
import styled from 'styled-components';
import { Button, Card, Space, Tag, Typography } from '@douyinfe/semi-ui';
import { IconEdit, IconPlus } from '@douyinfe/semi-icons';

import {
  OpsExecutionResource,
  OpsProjectService,
  OpsSourceRepository,
} from '../../../services/ops-repair-service';
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
  margin-top: ${theme.spacing.base};
`;

type Props = {
  repositories: OpsSourceRepository[];
  services: OpsProjectService[];
  executionResources: OpsExecutionResource[];
  onAddRepository: () => void;
  onAddService: () => void;
  onEditService: (service: OpsProjectService) => void;
  onAddExecutionResource: () => void;
  onEditExecutionResource: (resource: OpsExecutionResource) => void;
};

export const ProjectWorkspaceRuntimeSection: React.FC<Props> = ({
  repositories,
  services,
  executionResources,
  onAddRepository,
  onAddService,
  onEditService,
  onAddExecutionResource,
  onEditExecutionResource,
}) => (
  <>
    <Section title="代码与服务">
      <ResourceTitle>
        <div>
          <Text strong>修复范围与构建入口</Text>
          <div><Text type="tertiary" size="small">仓库限定代码边界；服务目录限定模块、构建命令、制品和健康检查。</Text></div>
        </div>
        <Space>
          <Button icon={<IconPlus />} onClick={onAddRepository}>登记仓库</Button>
          <Button theme="solid" icon={<IconPlus />} disabled={!repositories.length} onClick={onAddService}>添加服务</Button>
        </Space>
      </ResourceTitle>
      <ResourceGrid>
        {repositories.map((repository) => (
          <Card key={repository.repositoryId}>
            <Text strong>{repository.name}</Text>
            <Paragraph type="tertiary">{repository.localPath}</Paragraph>
            <Space wrap>
              <Tag color="blue">{repository.repositoryId}</Tag>
              <Tag>{repository.defaultCommitSha?.slice(0, 12)}</Tag>
              <Tag color="green">{repository.status}</Tag>
            </Space>
          </Card>
        ))}
        {services.map((service) => (
          <Card key={service.serviceId}>
            <ResourceTitle>
              <div>
                <Text strong>{service.name}</Text>
                <Paragraph type="tertiary">{service.modulePath} · {service.buildProfile}</Paragraph>
              </div>
              <Button icon={<IconEdit />} onClick={() => onEditService(service)}>编辑</Button>
            </ResourceTitle>
            <Space wrap>
              <Tag color="blue">{service.serviceId}</Tag>
              <Tag>{service.repositoryId}</Tag>
              <Tag color={service.deploymentResourceId ? 'green' : 'grey'}>{service.deploymentResourceId ? '可部署' : '仅验证'}</Tag>
            </Space>
          </Card>
        ))}
        {!repositories.length && !services.length && (
          <Text type="tertiary">尚未登记代码仓库和服务，Agent 只能分析运行态数据，不能验证代码修复。</Text>
        )}
      </ResourceGrid>
    </Section>

    <Section title="变更执行目标">
      <ResourceTitle>
        <div>
          <Text strong>审批通过后允许受控执行服务操作的目标</Text>
          <div><Text type="tertiary" size="small">这里只承载写操作和部署动作。它不会作为 Agent 查询数据的工具，也不会绕过审批链路。</Text></div>
        </div>
        <Button theme="solid" icon={<IconPlus />} onClick={onAddExecutionResource}>添加执行目标</Button>
      </ResourceTitle>
      <ResourceGrid>
        {executionResources.map((resource) => (
          <Card key={resource.resourceId}>
            <ResourceTitle>
              <div>
                <Text strong>{resource.name}</Text>
                <div><Text type="tertiary" size="small">{resource.resourceId}</Text></div>
              </div>
              <Tag color={resource.status === 'ENABLED' ? 'green' : 'grey'}>{resource.status}</Tag>
            </ResourceTitle>
            <Paragraph type="tertiary">{resource.adapter} · 执行连接 {resource.workerId}</Paragraph>
            <Space wrap>
              {(resource.environments || []).map((environment) => <Tag key={environment}>{environment}</Tag>)}
            </Space>
            <Button icon={<IconEdit />} style={{ marginTop: 14 }} onClick={() => onEditExecutionResource(resource)}>编辑配置</Button>
          </Card>
        ))}
        {!executionResources.length && (
          <Text type="tertiary">尚未配置执行目标。项目仍可诊断和生成方案，但审批后不能自动执行变更。</Text>
        )}
      </ResourceGrid>
    </Section>
  </>
);
