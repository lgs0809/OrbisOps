import React from 'react';
import styled from 'styled-components';
import { Button, Card, Space, Tag, Typography } from '@douyinfe/semi-ui';
import { IconSetting } from '@douyinfe/semi-icons';

import { ProjectReadinessCard } from '../../../components/project-readiness-card';
import { OpsDiagnosticScenario, OpsProjectWorkspace } from '../../../services/ops-project-service';
import { theme } from '../../../styles/theme';

const { Title, Text, Paragraph } = Typography;

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

const SummaryGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(220px, 100%), 1fr));
  gap: ${theme.spacing.base};
  margin-top: ${theme.spacing.base};
`;

const SummaryItem = styled.div`
  border: 1px solid ${theme.colors.border.secondary};
  border-radius: ${theme.borderRadius.base};
  background: #f8fafc;
  padding: ${theme.spacing.base};
`;

const ReadinessGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: ${theme.spacing.base};
  margin-top: ${theme.spacing.base};

  @media (max-width: ${theme.breakpoints.md}) {
    grid-template-columns: minmax(0, 1fr);
  }
`;

const ReadinessCard = styled.div`
  border: 1px solid ${theme.colors.border.secondary};
  border-radius: ${theme.borderRadius.base};
  background: ${theme.colors.bg.secondary};
  padding: ${theme.spacing.base};
  min-width: 0;
`;

const ReadinessCheckRow = styled.div`
  display: grid;
  grid-template-columns: 22px minmax(0, 1fr);
  gap: 8px;
  align-items: start;
  padding: 8px 0;
  border-bottom: 1px solid ${theme.colors.border.secondary};

  &:last-child {
    border-bottom: 0;
  }
`;

const ScenarioGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(260px, 100%), 1fr));
  gap: ${theme.spacing.base};
  margin-top: ${theme.spacing.base};
`;

const ScenarioItem = styled.div<{ $ready: boolean }>`
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 184px;
  padding: ${theme.spacing.base};
  border: 1px solid ${(props) => (props.$ready ? theme.colors.border.primary : theme.colors.border.secondary)};
  border-radius: ${theme.borderRadius.base};
  background: ${(props) => (props.$ready ? theme.colors.bg.primary : theme.colors.bg.secondary)};

  .scenario-description,
  .scenario-reason {
    overflow-wrap: anywhere;
    line-height: 1.6;
  }

  .scenario-action {
    margin-top: auto;
    padding-top: ${theme.spacing.base};
  }
`;

type Props = {
  project: OpsProjectWorkspace;
  projectServiceCount: number;
  capabilitySaving: boolean;
  onNavigate: (path: string) => void;
  onEnsureDefaultAgent: () => void;
  onConfigureDiagnosisResources: () => void;
  onOpenExecutionResource: () => void;
  onStartScenario: (scenario: OpsDiagnosticScenario) => void;
  onConfigureScenario: (scenario: OpsDiagnosticScenario) => void;
  onOpenProjectSettings: () => void;
};

const stringList = (value: unknown): string[] => {
  if (Array.isArray(value)) return value.map(String).filter(Boolean);
  if (value === undefined || value === null || value === '') return [];
  return [String(value)];
};

const effectiveSkillIds = (project: OpsProjectWorkspace): string[] => {
  const projected = [...(project.projectSkills || []), ...(project.enabledGlobalSkills || [])]
    .map((item: any) => String(item?.skillId || item?.id || item?.name || '').trim())
    .filter(Boolean);
  return [...new Set(projected.length ? projected : (project.skillIds || []).map(String).filter(Boolean))];
};

export const ProjectWorkspaceOverviewSection: React.FC<Props> = ({
  project,
  projectServiceCount,
  capabilitySaving,
  onNavigate,
  onEnsureDefaultAgent,
  onConfigureDiagnosisResources,
  onOpenExecutionResource,
  onStartScenario,
  onConfigureScenario,
  onOpenProjectSettings,
}) => {
  const skillIds = effectiveSkillIds(project);
  return (
    <>
    <Section>
      <ResourceTitle>
        <div>
          <Title heading={4} style={{ margin: 0 }}>{project.name}</Title>
          <Text type="tertiary">负责人：{project.owner} · 环境：{stringList(project.environments).join(' / ') || '-'}</Text>
          <div style={{ marginTop: 8 }}>
            <Space wrap>
              <Tag color={project.readyForInvestigation ? 'green' : 'orange'}>
                {project.readyForInvestigation
                  ? '可开始排障'
                  : project.readinessReason === 'DEFAULT_AGENT_NOT_PUBLISHED'
                    ? '默认 Agent 未发布'
                    : '待完善接入'}
              </Tag>
              <Tag color="blue">默认 Agent：{project.defaultAgentId || '未设置'}</Tag>
              <Tag color="green">知识库：{project.knowledgeBaseId || '-'}</Tag>
            </Space>
          </div>
          <div style={{ marginTop: 8 }}>
            <Space wrap>
              {(project.onboarding || []).map((step) => (
                <Tag key={step.key} color={step.completed ? 'green' : 'grey'}>{step.label}</Tag>
              ))}
            </Space>
          </div>
        </div>
        <Space wrap>
          <Button onClick={() => onNavigate(`/agent-list?projectId=${encodeURIComponent(project.projectId)}`)}>Agent</Button>
          <Button onClick={() => onNavigate(`/inspections?projectId=${encodeURIComponent(project.projectId)}`)}>巡检</Button>
          <Button onClick={() => onNavigate(`/alerts?projectId=${encodeURIComponent(project.projectId)}`)}>告警</Button>
          <Button onClick={() => onNavigate(`/executions?projectId=${encodeURIComponent(project.projectId)}`)}>执行中心</Button>
          <Button onClick={() => onNavigate(`/audit?projectId=${encodeURIComponent(project.projectId)}`)}>审计</Button>
        </Space>
      </ResourceTitle>
      <ReadinessGrid>
        <ReadinessCard>
          <Text strong>首次价值路径</Text>
          {(project.onboarding || []).map((step) => (
            <ReadinessCheckRow key={step.key}>
              <Text>{step.completed ? '✓' : '×'}</Text>
              <Text>{step.label}</Text>
            </ReadinessCheckRow>
          ))}
          <Paragraph type="tertiary" style={{ marginBottom: 0 }}>首次诊断前不要求配置 Skill Evolver、Landing 或复杂 Workflow。</Paragraph>
        </ReadinessCard>
        <ProjectReadinessCard
          title="诊断能力"
          readiness={project.diagnosisReadiness}
          fallbackAction="完善诊断能力"
          onAction={() => project.defaultAgentPublished
            ? (project.diagnosisReadiness?.ready ? onNavigate('/chat') : onConfigureDiagnosisResources())
            : onEnsureDefaultAgent()}
        />
        <ProjectReadinessCard
          title="生产处置能力"
          readiness={project.remediationReadiness}
          fallbackAction="配置生产处置能力"
          onAction={() => project.executionResourceCount ? onNavigate('/executions') : onOpenExecutionResource()}
        />
      </ReadinessGrid>
      <SummaryGrid>
        <SummaryItem><Text type="tertiary" size="small">数据连接</Text><Title heading={4} style={{ margin: '4px 0 0' }}>{project.resourceCount}</Title></SummaryItem>
        <SummaryItem><Text type="tertiary" size="small">已生成工具</Text><Title heading={4} style={{ margin: '4px 0 0' }}>{project.generatedMcpCount}</Title></SummaryItem>
        <SummaryItem><Text type="tertiary" size="small">项目 Skill</Text><Title heading={4} style={{ margin: '4px 0 0' }}>{skillIds.length}</Title></SummaryItem>
        <SummaryItem><Text type="tertiary" size="small">代码服务</Text><Title heading={4} style={{ margin: '4px 0 0' }}>{projectServiceCount}</Title></SummaryItem>
      </SummaryGrid>
    </Section>

    <Section title="开始第一次真实诊断">
      <ResourceTitle>
        <div style={{ minWidth: 0 }}>
          <Text strong>选择当前项目已经具备证据来源的场景</Text>
          <div><Text type="tertiary" size="small">平台只开放已接入的数据能力。未接入的场景会说明缺少什么，不会给出一个无法运行的入口。</Text></div>
        </div>
        {project.readyForInvestigation && <Tag color="green">已可开始诊断</Tag>}
      </ResourceTitle>
      <ScenarioGrid>
        {(project.diagnosticScenarios || []).map((scenario) => (
          <ScenarioItem key={scenario.scenarioId} $ready={scenario.ready} data-testid={`diagnostic-scenario-${scenario.scenarioId}`}>
            <Space wrap style={{ justifyContent: 'space-between', width: '100%' }}>
              <Text strong>{scenario.name}</Text>
              {scenario.scenarioId === project.recommendedScenarioId && scenario.ready
                ? <Tag color="blue">推荐</Tag>
                : <Tag color={scenario.ready ? 'green' : 'grey'}>{scenario.ready ? '可用' : '未接入'}</Tag>}
            </Space>
            <Text className="scenario-description" type="secondary" style={{ marginTop: 10 }}>{scenario.description}</Text>
            {!scenario.ready && <Text className="scenario-reason" type="tertiary" size="small" style={{ marginTop: 8 }}>还需：{scenario.unavailableReason || '完善项目能力'}</Text>}
            <div className="scenario-action">
              {scenario.ready
                ? <Button theme="solid" onClick={() => onStartScenario(scenario)}>开始诊断</Button>
                : <Button onClick={() => onConfigureScenario(scenario)}>{project.defaultAgentPublished ? '完善接入' : '发布默认 Agent'}</Button>}
            </div>
          </ScenarioItem>
        ))}
        {!project.diagnosticScenarios?.length && (
          <ScenarioItem $ready={false}>
            <Text strong>暂未生成诊断入口</Text>
            <Text type="tertiary" style={{ marginTop: 8 }}>刷新项目后仍未出现时，请先发布默认 Agent 并接入至少一种证据来源。</Text>
          </ScenarioItem>
        )}
      </ScenarioGrid>
    </Section>

    <Section title="项目默认能力">
      <ResourceTitle>
        <div>
          <Text strong>默认 Agent、知识库和可用能力</Text>
          <div><Text type="tertiary" size="small">平台默认运维 Agent 会继承这里的能力；画布 Agent 仍可在节点上单独选择 MCP、RAG 和 Skill。</Text></div>
        </div>
        <Space>
          {!project.defaultAgentPublished && <Button loading={capabilitySaving} onClick={onEnsureDefaultAgent}>修复默认 Agent</Button>}
          <Button icon={<IconSetting />} onClick={onOpenProjectSettings}>编辑默认能力</Button>
        </Space>
      </ResourceTitle>
      <SummaryGrid>
        <SummaryItem><Text type="tertiary" size="small">默认 Agent</Text><Paragraph style={{ margin: '6px 0 0' }}>{project.defaultAgentId || '未设置'}</Paragraph></SummaryItem>
        <SummaryItem><Text type="tertiary" size="small">默认知识库</Text><Paragraph style={{ margin: '6px 0 0' }}>{project.knowledgeBaseId || '未绑定'}</Paragraph></SummaryItem>
        <SummaryItem><Text type="tertiary" size="small">项目 Skill</Text><Paragraph style={{ margin: '6px 0 0' }}>{skillIds.join(', ') || '未选择'}</Paragraph></SummaryItem>
        <SummaryItem><Text type="tertiary" size="small">通用 MCP</Text><Paragraph style={{ margin: '6px 0 0' }}>{project.sharedMcpIds?.join(', ') || '未绑定'}</Paragraph></SummaryItem>
      </SummaryGrid>
    </Section>
  </>
  );
};
