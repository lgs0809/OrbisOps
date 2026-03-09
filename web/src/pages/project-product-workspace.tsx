import React from 'react';
import { Button, Card, Space, Spin, Tag, Typography } from '@douyinfe/semi-ui';
import { IconAlertTriangle, IconBranch, IconFolder, IconSetting, IconShield } from '@douyinfe/semi-icons';
import { useNavigate } from 'react-router-dom';
import styled from 'styled-components';

import { OpsPageHeader, OpsPageShell } from '../components/ops-layout';
import { ProjectReadinessCard } from '../components/project-readiness-card';
import { ProjectScopeBar } from '../components/project-scope-bar';
import { useProjectScope } from '../hooks/use-project-scope';
import { theme } from '../styles/theme';

const { Paragraph, Text, Title } = Typography;

const Grid = styled.div`
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: ${theme.spacing.base};
  margin-bottom: ${theme.spacing.base};
  @media (max-width: ${theme.breakpoints.lg}) { grid-template-columns: 1fr; }
`;

const TwoColumn = styled.div`
  display: grid;
  grid-template-columns: minmax(0, 1.25fr) minmax(320px, 0.75fr);
  gap: ${theme.spacing.base};
  @media (max-width: ${theme.breakpoints.lg}) { grid-template-columns: 1fr; }
`;

const ActionGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: ${theme.spacing.sm};
  @media (max-width: ${theme.breakpoints.sm}) { grid-template-columns: 1fr; }
`;

const metric = (label: string, value: React.ReactNode, hint: string) => (
  <Card>
    <Text type="tertiary" size="small">{label}</Text>
    <Title heading={4} style={{ margin: '6px 0' }}>{value}</Title>
    <Text type="tertiary" size="small">{hint}</Text>
  </Card>
);

export const ProjectProductWorkspacePage: React.FC = () => {
  const navigate = useNavigate();
  const scope = useProjectScope();
  const project = scope.selectedProject;
  const projectQuery = project?.projectId ? `?projectId=${encodeURIComponent(project.projectId)}` : '';
  const recommended = project?.diagnosticScenarios?.find((item) => item.scenarioId === project.recommendedScenarioId)
    || project?.diagnosticScenarios?.find((item) => item.ready);

  return (
    <OpsPageShell selectedKey="project-workspace">
      <OpsPageHeader
        title="项目空间"
        description="先确认项目是否具备真实诊断与受控处置能力；具体资源、成员、Skill、知识库和连接配置统一下沉到高级配置。"
        primaryAction={project?.readyForInvestigation
          ? <Button type="primary" icon={<IconBranch />} onClick={() => navigate(`/chat${projectQuery}`)}>开始诊断</Button>
          : undefined}
        extra={<Button icon={<IconSetting />} onClick={() => navigate(`/project-workspace/advanced${projectQuery}`)}>高级项目配置</Button>}
      />

      <ProjectScopeBar
        projects={scope.projects}
        projectId={scope.projectId}
        loading={scope.loading}
        onChange={scope.selectProject}
        onRefresh={() => void scope.reloadProjects()}
      />

      <Spin spinning={scope.loading}>
        {!project ? (
          <Card><Text type="tertiary">当前没有可用项目。进入高级项目配置创建或接入项目。</Text></Card>
        ) : (
          <>
            <Card style={{ marginBottom: theme.spacing.base }}>
              <Space vertical align="start" style={{ width: '100%' }}>
                <Space wrap>
                  <IconFolder />
                  <Title heading={4} style={{ margin: 0 }}>{project.name}</Title>
                  <Tag color={project.readyForInvestigation ? 'green' : 'orange'}>
                    {project.readyForInvestigation ? '可开始真实诊断' : '接入尚未完成'}
                  </Tag>
                </Space>
                <Paragraph type="tertiary" style={{ margin: 0 }}>{project.description || '暂无项目说明'}</Paragraph>
                <Space wrap>
                  <Tag>负责人 {project.owner || '-'}</Tag>
                  {(project.environments || []).map((env) => <Tag key={env}>{env}</Tag>)}
                  <Tag color="blue">默认 Agent {project.defaultAgentId || '未设置'}</Tag>
                </Space>
              </Space>
            </Card>

            <Grid>
              <Card>
                <Text strong>首次价值路径</Text>
                <Paragraph type="tertiary">创建项目 → 接入实时数据 → 完成一次真实查询 → 默认助手可用。</Paragraph>
                <Space vertical align="start">
                  {(project.onboarding || []).map((step) => (
                    <Text key={step.key}>{step.completed ? '✓' : '○'} {step.label}</Text>
                  ))}
                </Space>
              </Card>
              <ProjectReadinessCard
                title="诊断能力"
                readiness={project.diagnosisReadiness}
                fallbackAction="完善诊断能力"
                onAction={() => navigate(`/project-workspace/advanced${projectQuery}`)}
              />
              <ProjectReadinessCard
                title="生产处置能力"
                readiness={project.remediationReadiness}
                fallbackAction="配置生产处置能力"
                onAction={() => navigate(`/project-workspace/advanced${projectQuery}`)}
              />
            </Grid>

            <Grid>
              {metric('数据连接', project.resourceCount || 0, 'Agent 可查询的项目数据源')}
              {metric('已生成工具', project.generatedMcpCount || 0, '由项目连接生成的受控工具')}
              {metric('项目 Skill', project.skillIds?.length || 0, '项目专属经验与 SOP')}
              {metric('代码仓库', project.sourceRepositoryCount || 0, '代码分析与修复边界')}
              {metric('执行目标', project.executionResourceCount || 0, '审批后允许受控执行的目标')}
              {metric('知识库', project.knowledgeBaseIds?.length || (project.knowledgeBaseId ? 1 : 0), '当前项目授权知识范围')}
            </Grid>

            <TwoColumn>
              <Card title="下一步">
                {recommended ? (
                  <Space vertical align="start" style={{ width: '100%' }}>
                    <Space wrap>
                      <Text strong>{recommended.name}</Text>
                      <Tag color={recommended.ready ? 'green' : 'orange'}>{recommended.ready ? '可运行' : '还需接入'}</Tag>
                    </Space>
                    <Paragraph type="tertiary">{recommended.description}</Paragraph>
                    {recommended.ready ? (
                      <Button type="primary" onClick={() => navigate(`/chat${projectQuery}`)}>使用当前项目开始诊断</Button>
                    ) : (
                      <Button icon={<IconSetting />} onClick={() => navigate(`/project-workspace/advanced${projectQuery}`)}>
                        {recommended.unavailableReason || '完善项目能力'}
                      </Button>
                    )}
                  </Space>
                ) : (
                  <Space vertical align="start">
                    <IconAlertTriangle />
                    <Text strong>还没有可用诊断场景</Text>
                    <Text type="tertiary">先发布默认 Agent，并接入至少一种真实证据来源。</Text>
                    <Button onClick={() => navigate(`/project-workspace/advanced${projectQuery}`)}>完善接入</Button>
                  </Space>
                )}
              </Card>

              <Card title="日常入口">
                <ActionGrid>
                  <Button icon={<IconBranch />} onClick={() => navigate(`/chat${projectQuery}`)}>AI 诊断</Button>
                  <Button icon={<IconAlertTriangle />} onClick={() => navigate(`/incidents${projectQuery}`)}>事件中心</Button>
                  <Button onClick={() => navigate(`/agent-list${projectQuery}`)}>专项 Workflow</Button>
                  <Button onClick={() => navigate(`/inspections${projectQuery}`)}>自动巡检</Button>
                  <Button onClick={() => navigate(`/alerts${projectQuery}`)}>告警接入</Button>
                  <Button icon={<IconShield />} onClick={() => navigate(`/executions${projectQuery}`)}>执行中心</Button>
                </ActionGrid>
              </Card>
            </TwoColumn>

            <Card style={{ marginTop: theme.spacing.base }}>
              <Space wrap style={{ justifyContent: 'space-between', width: '100%' }}>
                <div>
                  <Text strong>需要修改资源、成员、Skill、知识库或连接？</Text>
                  <div><Text type="tertiary">这些低频配置不再占据项目首页，统一进入高级项目配置。</Text></div>
                </div>
                <Button icon={<IconSetting />} onClick={() => navigate(`/project-workspace/advanced${projectQuery}`)}>进入高级配置</Button>
              </Space>
            </Card>
          </>
        )}
      </Spin>
    </OpsPageShell>
  );
};
