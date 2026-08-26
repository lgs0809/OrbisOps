import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import styled from 'styled-components';
import {
  Breadcrumb,
  Button,
  Checkbox,
  Input,
  Layout,
  Popconfirm,
  Select,
  Space,
  Spin,
  Tag,
  TextArea,
  Toast,
  Typography,
} from '@douyinfe/semi-ui';
import {
  IconAlertTriangle,
  IconArrowLeft,
  IconBranch,
  IconCalendarClock,
  IconCode,
  IconDelete,
  IconPlus,
  IconPlay,
  IconRefresh,
  IconSave,
  IconSetting,
} from '@douyinfe/semi-icons';
import { clearAuthSession } from '../services/auth-session';

import { Header, Sidebar, SIDEBAR_COLLAPSED_WIDTH, SIDEBAR_WIDTH } from '../components/layout';
import { OpsAgentCanvas } from '../components/ops-agent-canvas';
import { theme } from '../styles/theme';
import { useResponsiveSidebar } from '../hooks/use-responsive-sidebar';

import type { RagKnowledgeBaseSummary } from '../services/ai-client-rag-order-admin-service';
import type { OpsGeneratedMcp } from '../services/ops-project-service';
import {
  ApiRequestError,
  OpsAgentCapabilityBinding,
  OpsAgentDefinition,
  OpsGraphEdge,
  OpsLoopPolicy,
  OpsMcpServerConfig,
  OpsRuntimeEvent,
  OpsSkillSummary,
  OpsWorkflowNode,
} from '../services/ops-admin-service';
import { opsAgentDefinitionService } from '../services/ops-agent-definition-service';
import {
  useAgentBuilderCapabilitiesQuery,
  useAgentBuilderLibrariesQuery,
} from '../features/agents/api/agent-builder-queries';
import { useProjectScope } from '../hooks/use-project-scope';
import { AgentBuilderGuide } from '../features/agents/components/AgentBuilderGuide';
import { AgentBuilderModeSwitch } from '../features/agents/components/AgentBuilderModeSwitch';
import { AgentBuilderPropertyPanel } from '../features/agents/components/AgentBuilderPropertyPanel';
import { AgentBuilderVersionSection } from '../features/agents/components/AgentBuilderVersionSection';
import {
  AgentBuilderMode,
  AgentBuilderPanel,
  agentBuilderMode,
  agentBuilderPanelForMode,
  isAgentBuilderAdvanced,
} from '../features/agents/model/agent-builder-mode';

const { Content } = Layout;
const { Title, Text, Paragraph } = Typography;
const { Option } = Select;

type UserInfo = {
  username: string;
  loginTime: string;
  token: string;
  isTestAccount?: boolean;
};

type PanelKey = AgentBuilderPanel;

type McpOption = {
  mcpId: string;
  mcpName?: string;
  transportType?: string;
  scopeLabel: string;
  projectId?: string;
  resourceType?: string;
};

type SkillOptionMeta = OpsSkillSummary & {
  skillId?: string;
  scope?: string;
  projectId?: string;
};

type CapabilityMap = Record<string, any>;

const PageLayout = styled(Layout)`
  min-height: 100vh;
  min-width: 0;
  width: 100%;
  background: ${theme.colors.bg.secondary};
  overflow-x: hidden;
`;

const MainContent = styled.div<{ $collapsed: boolean }>`
  min-height: 100vh;
  min-width: 0;
  width: ${(props) =>
    props.$collapsed
      ? `calc(100vw - ${SIDEBAR_COLLAPSED_WIDTH}px)`
      : `calc(100vw - ${SIDEBAR_WIDTH}px)`};
  margin-left: ${(props) => (props.$collapsed ? `${SIDEBAR_COLLAPSED_WIDTH}px` : `${SIDEBAR_WIDTH}px`)};
  display: flex;
  flex: 1;
  flex-direction: column;
  background: ${theme.colors.bg.secondary};
  overflow-x: hidden;
  transition:
    width ${theme.animation.duration.normal} ${theme.animation.easing.cubic},
    margin-left ${theme.animation.duration.normal} ${theme.animation.easing.cubic};

  @media (max-width: ${theme.breakpoints.md}) {
    width: 100vw;
    margin-left: 0;
  }
`;

const ContentArea = styled(Content)`
  box-sizing: border-box;
  width: 100%;
  max-width: 100%;
  min-width: 0;
  margin: 0 auto;
  padding: 24px;
  overflow-x: hidden;

  @media (max-width: 1280px) {
    padding: 16px;
  }
`;

const PageContainer = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${theme.spacing.base};
  min-height: 100%;
  min-width: 0;
  width: 100%;
`;

const PageHeader = styled.div`
  box-sizing: border-box;
  display: flex;
  justify-content: space-between;
  gap: ${theme.spacing.lg};
  align-items: flex-start;
  padding: ${theme.spacing.lg};
  border: 1px solid ${theme.colors.border.secondary};
  border-radius: ${theme.borderRadius.base};
  background: ${theme.colors.bg.primary};
  min-width: 0;
  flex-wrap: wrap;

  > * {
    min-width: 0;
  }
`;

const WorkspaceGrid = styled.div`
  display: grid;
  grid-template-columns: minmax(240px, 320px) minmax(0, 1fr);
  gap: ${theme.spacing.base};
  min-height: calc(100vh - 190px);
  min-width: 0;

  @media (max-width: 980px) {
    grid-template-columns: 1fr;
  }
`;

const Panel = styled.div`
  border: 1px solid ${theme.colors.border.secondary};
  border-radius: ${theme.borderRadius.base};
  background: ${theme.colors.bg.primary};
  overflow: hidden;
  min-width: 0;
`;

const PanelHeader = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: ${theme.spacing.sm};
  padding: ${theme.spacing.base};
  border-bottom: 1px solid ${theme.colors.border.secondary};
  min-width: 0;
`;

const PanelTitle = styled.div`
  display: flex;
  align-items: center;
  gap: ${theme.spacing.sm};
  font-weight: ${theme.typography.fontWeight.semibold};
  min-width: 0;
`;

const PanelBody = styled.div`
  padding: ${theme.spacing.base};
  min-width: 0;
`;

const AgentList = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${theme.spacing.sm};
  min-width: 0;
`;

const AgentListItem = styled.button<{ $active?: boolean }>`
  width: 100%;
  border: 1px solid ${(props) => (props.$active ? theme.colors.primary : theme.colors.border.secondary)};
  border-radius: ${theme.borderRadius.base};
  background: ${(props) => (props.$active ? '#eff6ff' : theme.colors.bg.primary)};
  color: ${theme.colors.text.primary};
  text-align: left;
  padding: ${theme.spacing.sm} ${theme.spacing.base};
  cursor: pointer;
  transition: border-color ${theme.animation.duration.fast} ${theme.animation.easing.ease};

  &:hover {
    border-color: ${theme.colors.primary};
  }
`;

const AgentName = styled.div`
  font-weight: ${theme.typography.fontWeight.semibold};
  margin-bottom: 2px;
`;

const MetaRow = styled.div`
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: ${theme.spacing.sm};
`;

const CanvasPanel = styled(Panel)`
  min-width: 0;
`;

const CanvasToolbar = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: ${theme.spacing.base};
  padding: ${theme.spacing.base};
  border-bottom: 1px solid ${theme.colors.border.secondary};
  min-width: 0;
  flex-wrap: wrap;
`;

const ToolbarGroup = styled.div`
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: ${theme.spacing.sm};
  min-width: 0;
`;

const FormStack = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${theme.spacing.base};
  min-width: 0;
`;

const Field = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${theme.spacing.xs};
  min-width: 0;
`;

const HelpText = styled(Text)`
  line-height: 1.45;
`;

const TwoColumn = styled.div`
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: ${theme.spacing.base};
  min-width: 0;

  @media (max-width: 760px) {
    grid-template-columns: 1fr;
  }
`;

const AdvancedDetails = styled.details`
  border: 1px solid ${theme.colors.border.secondary};
  border-radius: ${theme.borderRadius.base};
  background: ${theme.colors.bg.secondary};
  padding: ${theme.spacing.sm} ${theme.spacing.base};
  min-width: 0;

  &[open] {
    padding-bottom: ${theme.spacing.base};
  }

  summary {
    cursor: pointer;
    font-weight: ${theme.typography.fontWeight.semibold};
    color: ${theme.colors.text.secondary};
  }
`;

const AdvancedContent = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${theme.spacing.base};
  margin-top: ${theme.spacing.base};
  min-width: 0;
`;

const Section = styled.div`
  border-top: 1px solid ${theme.colors.border.secondary};
  padding-top: ${theme.spacing.base};
  min-width: 0;
`;

const SectionHeader = styled.div`
  display: flex;
  flex-direction: column;
  gap: 4px;
`;

const SectionTitle = styled.div`
  color: ${theme.colors.text.primary};
  font-weight: ${theme.typography.fontWeight.semibold};
`;

const NoticeBox = styled.div<{ $tone?: 'info' | 'warning' | 'neutral' }>`
  padding: ${theme.spacing.sm} ${theme.spacing.base};
  border: 1px solid
    ${(props) =>
      props.$tone === 'warning'
        ? '#fde68a'
        : props.$tone === 'neutral'
        ? theme.colors.border.secondary
        : '#bfdbfe'};
  border-radius: ${theme.borderRadius.base};
  background: ${(props) =>
    props.$tone === 'warning'
      ? '#fffbeb'
      : props.$tone === 'neutral'
      ? theme.colors.bg.secondary
      : '#eff6ff'};
  color: ${(props) =>
    props.$tone === 'warning'
      ? '#92400e'
      : props.$tone === 'neutral'
      ? theme.colors.text.secondary
      : '#1d4ed8'};
  line-height: 1.55;
  font-size: 13px;
`;

const EdgeItem = styled.div<{ $active?: boolean }>`
  border: 1px solid ${(props) => (props.$active ? theme.colors.primary : theme.colors.border.secondary)};
  border-radius: ${theme.borderRadius.base};
  padding: ${theme.spacing.sm};
  background: ${(props) => (props.$active ? '#eff6ff' : theme.colors.bg.primary)};
  min-width: 0;
`;

const RuntimeBox = styled.pre`
  margin: 0;
  width: 100%;
  max-width: 100%;
  min-height: 160px;
  max-height: 360px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-word;
  border: 1px solid ${theme.colors.border.secondary};
  border-radius: ${theme.borderRadius.base};
  background: ${theme.colors.bg.secondary};
  padding: ${theme.spacing.base};
  color: ${theme.colors.text.primary};
  line-height: 1.65;
`;

const shortTraceText = (value: unknown, max = 1600) => {
  if (value === undefined || value === null || value === '') {
    return '';
  }
  const text = typeof value === 'string' ? value : JSON.stringify(value, null, 2);
  return text.length > max ? `${text.slice(0, max)}...` : text;
};

const runtimeEventTraceDetail = (event: OpsRuntimeEvent) => {
  const payload = event.payload || {};
  const lines: string[] = [];
  if (event.eventType?.startsWith('TOOL_CALL_')) {
    lines.push(`toolKind: ${shortTraceText(payload.toolKind || 'tool', 80)}`);
    lines.push(`toolName: ${shortTraceText(payload.toolName || event.agent || '-', 120)}`);
    if (payload.skillNames) lines.push(`skillNames: ${shortTraceText(payload.skillNames, 300)}`);
    if (payload.input !== undefined) lines.push(`input:\n${shortTraceText(payload.input)}`);
    if (payload.output !== undefined) lines.push(`output:\n${shortTraceText(payload.output)}`);
    if (payload.error !== undefined) lines.push(`error:\n${shortTraceText(payload.error, 800)}`);
    return lines.filter(Boolean).join('\n');
  }
  if (event.eventType?.startsWith('MODEL_CALL_')) {
    if (payload.modelId) lines.push(`modelId: ${shortTraceText(payload.modelId, 120)}`);
    if (payload.agentName || event.agent) lines.push(`agent: ${shortTraceText(payload.agentName || event.agent, 120)}`);
    if (payload.promptChars !== undefined) lines.push(`promptChars: ${payload.promptChars}`);
    if (payload.systemPrompt !== undefined) lines.push(`systemPrompt:\n${shortTraceText(payload.systemPrompt)}`);
    if (payload.userPrompt !== undefined) lines.push(`userPrompt:\n${shortTraceText(payload.userPrompt)}`);
    if (payload.output !== undefined) lines.push(`output:\n${shortTraceText(payload.output)}`);
    if (payload.error !== undefined) lines.push(`error:\n${shortTraceText(payload.error, 800)}`);
    return lines.filter(Boolean).join('\n');
  }
  if (event.eventType?.startsWith('QUERY_REWRITE_')) {
    if (payload.originalQuestion !== undefined) lines.push(`originalQuestion:\n${shortTraceText(payload.originalQuestion)}`);
    if (payload.rewrittenQuestion !== undefined) lines.push(`rewrittenQuestion:\n${shortTraceText(payload.rewrittenQuestion)}`);
    if (payload.reason !== undefined) lines.push(`reason:\n${shortTraceText(payload.reason, 800)}`);
    if (payload.resolvedReferences !== undefined) lines.push(`resolvedReferences:\n${shortTraceText(payload.resolvedReferences, 800)}`);
    return lines.filter(Boolean).join('\n');
  }
  return '';
};

const formatRuntimeEvent = (event: OpsRuntimeEvent) => {
  const base = `[${event.status || event.eventType || '-'}] ${event.nodeId || event.agent || '-'} ${event.summary || event.content || ''}`;
  const traceDetail = runtimeEventTraceDetail(event);
  return traceDetail ? `${base}\n${traceDetail}` : base;
};

const EmptyState = styled.div`
  min-height: 180px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: ${theme.colors.text.tertiary};
`;

const defaultQuestion =
  '分析最近 15 分钟当前业务系统是否存在异常，重点关注错误日志、慢 SQL、接口错误率和实例在线状态。';

const errorMessageOf = (error: unknown): string => {
  if (error instanceof ApiRequestError) {
    if (error.status === 401) {
      return '登录已过期，请重新登录';
    }
    return error.responseBody || `HTTP ${error.status} ${error.statusText}`;
  }
  return error instanceof Error ? error.message : '未知错误';
};

const isUnauthorizedError = (error: unknown): boolean =>
  error instanceof ApiRequestError && error.status === 401;

const edgeKeyOf = (edge: OpsGraphEdge, index: number) =>
  `${edge.edgeId || `${edge.from}->${edge.to}`}:${edge.conditionType || 'always'}:${edge.condition || 'always'}:${index}`;

const textToList = (value?: string) =>
  (value || '')
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean);

const listToText = (value?: string[]) => (value || []).join(', ');

const skillValueOf = (skill: OpsSkillSummary) => {
  const meta = skill as SkillOptionMeta;
  return meta.name || meta.skillId || '';
};

const skillScopeOf = (skill: OpsSkillSummary) => {
  const meta = skill as SkillOptionMeta;
  return String(meta.scope || meta.frontMatter?.scope || '').toUpperCase();
};

const skillProjectIdOf = (skill: OpsSkillSummary) => {
  const meta = skill as SkillOptionMeta;
  return String(meta.projectId || meta.frontMatter?.projectId || '');
};

const skillIdCandidatesOf = (skill: OpsSkillSummary) => {
  const meta = skill as SkillOptionMeta;
  return [meta.name, meta.skillId].filter(Boolean).map(String);
};

const isProjectSkill = (skill: OpsSkillSummary, currentProjectId?: string, currentProjectSkillIds?: string[]) => {
  const projectSkillIds = new Set((currentProjectSkillIds || []).map(String));
  const belongsById = skillIdCandidatesOf(skill).some((id) => projectSkillIds.has(id));
  const belongsByProject = Boolean(currentProjectId) && skillProjectIdOf(skill) === currentProjectId;
  const scope = skillScopeOf(skill);
  return scope === 'PROJECT' || belongsByProject || (!scope && belongsById);
};

const renderSkillOptionLabel = (skill: OpsSkillSummary) => {
  const name = skillValueOf(skill);
  return `${name}${skill.description ? ` · ${skill.description}` : ''}`;
};

const skillOptionFromCapability = (
  capability: Record<string, any>,
  options: OpsSkillSummary[],
  fallbackScope: string,
  fallbackDescription: string,
): OpsSkillSummary | null => {
  const skillId = String(capability.skillId || capability.name || capability.id || '').trim();
  if (!skillId) return null;
  const matched = options.find((skill) => skillIdCandidatesOf(skill).includes(skillId));
  const matchedMeta = matched as SkillOptionMeta | undefined;
  return {
    ...(matched || {}),
    name: skillId,
    skillId,
    description: String(capability.description || matched?.description || fallbackDescription),
    scope: String(capability.scope || skillScopeOf(matched || ({ name: skillId } as OpsSkillSummary)) || fallbackScope),
    projectId: capability.projectId || matchedMeta?.projectId,
  } as OpsSkillSummary;
};

const mcpOptionFromCapability = (
  item: CapabilityMap,
  scopeLabel: string,
  currentProjectId?: string,
): McpOption | null => {
  const mcpId = String(item.mcpId || item.id || '').trim();
  if (!mcpId) return null;
  const projectId = item.projectId ? String(item.projectId) : currentProjectId;
  return {
    mcpId,
    mcpName: String(item.mcpName || item.name || mcpId),
    transportType: item.transportType ? String(item.transportType) : undefined,
    projectId,
    resourceType: item.resourceType ? String(item.resourceType) : undefined,
    scopeLabel,
  };
};

const knowledgeBaseSummaryOf = (
  id: string,
  knowledgeBases: RagKnowledgeBaseSummary[],
): RagKnowledgeBaseSummary => {
  const existed = knowledgeBases.find((item) => item.knowledgeTag === id || item.kbId === id || item.ragId === id);
  return existed || { knowledgeTag: id, kbId: id, chunkCount: 0, documentCount: 0 };
};

const edgeIdOf = (edge: Pick<OpsGraphEdge, 'from' | 'to'>) => `${edge.from}->${edge.to}`;
const edgeRefOf = (edge: Pick<OpsGraphEdge, 'from' | 'to' | 'edgeId'>) => edge.edgeId || edgeIdOf(edge);
const uniqueList = (values: string[]) => Array.from(new Set(values.filter(Boolean)));

const duplicateNodeIdOf = (nodes: OpsWorkflowNode[]) => {
  const seen = new Set<string>();
  for (const node of nodes) {
    const nodeId = node.nodeId?.trim();
    if (!nodeId) continue;
    if (seen.has(nodeId)) return nodeId;
    seen.add(nodeId);
  }
  return '';
};

const nextSequentialNodeId = (nodes: OpsWorkflowNode[], prefix = 'agent') => {
  const existing = new Set(nodes.map((node) => node.nodeId));
  let index = nodes.length + 1;
  let candidate = `${prefix}_${index}`;
  while (existing.has(candidate)) {
    index += 1;
    candidate = `${prefix}_${index}`;
  }
  return candidate;
};

const ensureUniqueNodeId = (nodes: OpsWorkflowNode[], preferredNodeId?: string) => {
  const existing = new Set(nodes.map((node) => node.nodeId));
  const base = preferredNodeId?.trim() || nextSequentialNodeId(nodes);
  if (!existing.has(base)) return base;
  let index = 2;
  let candidate = `${base}_${index}`;
  while (existing.has(candidate)) {
    index += 1;
    candidate = `${base}_${index}`;
  }
  return candidate;
};

const boundedLoopRounds = (value: number | undefined, fallback = 3) => {
  const numeric = Number(value || fallback);
  if (!Number.isFinite(numeric)) return fallback;
  return Math.min(20, Math.max(1, Math.trunc(numeric)));
};

const loopIdForEdge = (edge: Pick<OpsGraphEdge, 'from' | 'to'>, existingLoops: OpsLoopPolicy[]) => {
  const base = `loop_${edge.from}_${edge.to}`.replace(/[^A-Za-z0-9_-]/g, '_');
  let candidate = base;
  let index = 1;
  while (existingLoops.some((loop) => loop.loopId === candidate)) {
    candidate = `${base}_${index++}`;
  }
  return candidate;
};

const ensureFeedbackLoopPolicy = (
  definition: OpsAgentDefinition,
  edge: OpsGraphEdge,
  patch: Partial<OpsLoopPolicy> = {},
): OpsAgentDefinition => {
  const edgeRef = edgeRefOf(edge);
  const loops = definition.loops || [];
  const existingIndex = loops.findIndex((loop) => (loop.feedbackEdges || []).includes(edgeRef));
  const endpointIndex = loops.findIndex((loop) => (loop.nodes || []).includes(edge.from) && (loop.nodes || []).includes(edge.to));
  const targetIndex = existingIndex >= 0 ? existingIndex : endpointIndex;
  const fallbackRounds = boundedLoopRounds(definition.defaultMaxMainRounds || 3);
  if (targetIndex >= 0) {
    return {
      ...definition,
      loops: loops.map((loop, index) => {
        if (index !== targetIndex) return loop;
        return {
          ...loop,
          ...patch,
          nodes: uniqueList([...(loop.nodes || []), edge.from, edge.to]),
          feedbackEdges: uniqueList([...(loop.feedbackEdges || []), edgeRef]),
          maxRounds: boundedLoopRounds(patch.maxRounds ?? loop.maxRounds, fallbackRounds),
          stopCondition: patch.stopCondition || loop.stopCondition || 'evidence_sufficient || round_limit',
          timeoutSeconds: patch.timeoutSeconds || loop.timeoutSeconds || 240,
          countMode: patch.countMode || loop.countMode || 'router_choice',
        };
      }),
    };
  }
  return {
    ...definition,
    loops: [
      ...loops,
      {
        loopId: loopIdForEdge(edge, loops),
        name: `循环 ${edge.from} -> ${edge.to}`,
        nodes: (definition.nodes || []).map((node) => node.nodeId),
        feedbackEdges: [edgeRef],
        maxRounds: boundedLoopRounds(patch.maxRounds, fallbackRounds),
        stopCondition: patch.stopCondition || 'evidence_sufficient || round_limit',
        timeoutSeconds: patch.timeoutSeconds || 240,
        countMode: patch.countMode || 'router_choice',
      },
    ],
  };
};

const removeFeedbackEdgeFromLoops = (definition: OpsAgentDefinition, edge: OpsGraphEdge): OpsAgentDefinition => {
  const edgeRef = edgeRefOf(edge);
  return {
    ...definition,
    loops: (definition.loops || []).map((loop) => ({
      ...loop,
      feedbackEdges: (loop.feedbackEdges || []).filter((item) => item !== edgeRef),
    })),
  };
};

const materializeFeedbackLoopPolicies = (definition: OpsAgentDefinition): OpsAgentDefinition => {
  return (definition.edges || [])
    .filter((edge) => edge.feedback)
    .reduce((current, edge) => {
      const edgeRef = edgeRefOf(edge);
      const exists = (current.loops || []).some((loop) => (loop.feedbackEdges || []).includes(edgeRef));
      return exists ? current : ensureFeedbackLoopPolicy(current, edge);
    }, definition);
};

const normalizeEdgeDraft = (edge: OpsGraphEdge, _existingEdges: OpsGraphEdge[]): OpsGraphEdge => {
  const feedback = Boolean(edge.feedback);
  const currentConditionType = edge.conditionType || '';
  const conditionType = feedback && (!currentConditionType || currentConditionType === 'always')
    ? 'route_match'
    : currentConditionType || 'always';
  return {
    ...edge,
    edgeId: edge.edgeId || edgeIdOf(edge),
    conditionType,
    condition: edge.condition || 'always',
    feedback,
  };
};

const DEFAULT_NODE_TYPES = ['START', 'AGENT', 'ROUTER', 'SUB_WORKFLOW', 'HUMAN_APPROVAL', 'END'];
const AGENT_MODES = [
  { value: 'direct', label: 'DIRECT · 固定动作，不调用模型', role: 'general' },
  { value: 'llm', label: 'LLM · 单次模型调用', role: 'general' },
  { value: 'react', label: 'REACT · 节点内自主工具循环', role: 'data_agent' },
];
const LLM_ROLE_TEMPLATES = [
  { value: 'general', label: '通用' },
  { value: 'reviewer', label: 'Review · 结构化判断' },
  { value: 'reporter', label: 'Report · 汇总输出' },
  { value: 'extractor', label: 'Extract · 信息抽取' },
  { value: 'classifier', label: 'Classify · 分类判断' },
];
const CONDITION_TYPES = [
  { value: 'always', label: 'always · 总是进入' },
  { value: 'route_match', label: 'route_match · Router 选中' },
  { value: 'review_decision', label: 'review_decision · 复盘分支' },
  { value: 'expression', label: 'expression · 表达式判断' },
  { value: 'contains', label: 'contains · 文本包含' },
  { value: 'default', label: 'default · 默认分支' },
  { value: 'error', label: 'error · 异常分支' },
];

const stripRuntimeAgentScopeFields = (definition: OpsAgentDefinition): OpsAgentDefinition => {
  const copy = { ...definition } as OpsAgentDefinition & Record<string, unknown>;
  delete copy.agentscopeAgents;
  delete copy.agentScopeMode;
  delete copy.agentScopeMaxConcurrency;
  return copy as OpsAgentDefinition;
};

const cleanDefinition = (definition: OpsAgentDefinition): OpsAgentDefinition => {
  const rest = stripRuntimeAgentScopeFields(definition);
  return {
    ...rest,
    definitionKind: 'SPECIALIZED_WORKFLOW',
    engine: inferEngine(definition),
    nodes: rest.nodes || [],
  };
};

const routingListText = (values?: string[]) => (values || []).join('\n');

const parseRoutingList = (value: string) => value
  .split(/[\n,，]/)
  .map((item) => item.trim())
  .filter((item, index, values) => Boolean(item) && values.indexOf(item) === index);

const normalizeNodeType = (type?: string) => String(type || '').trim().toUpperCase();

const normalizeAgentMode = (mode?: unknown) => {
  const value = String(mode || '').trim().toLowerCase();
  if (['direct', 'llm', 'react'].includes(value)) return value;
  if (value === 'review') return 'llm';
  if (value === 'auto' || value === 'plan') return 'llm';
  return 'llm';
};

const modeExplanation = (mode?: string) => {
  const normalized = normalizeAgentMode(mode);
  if (normalized === 'direct') return '按配置的固定 Action 顺序调用受治理工具，不调用模型；适合稳定巡检和确定性读取。';
  if (normalized === 'react') return '负责在节点内部进行有限轮工具调用；只看该节点配置的 RAG、Skill、MCP 和上下文输入。';
  return '单次模型调用；Review、Report、Extract、Classify 等职责由节点 Prompt、输出契约和职责模板表达，不进入工具循环。';
};

const roleForMode = (mode: string) => {
  return AGENT_MODES.find((item) => item.value === mode)?.role || 'general';
};

const isRouteConditionEdge = (edge: OpsGraphEdge) => {
  const conditionType = String(edge.conditionType || '').toLowerCase();
  return conditionType === 'route_match' || conditionType === 'review_decision';
};

const isRouterNodeId = (nodes: OpsWorkflowNode[] | undefined, nodeId?: string) =>
  normalizeNodeType((nodes || []).find((node) => node.nodeId === nodeId)?.type) === 'ROUTER';

const inferAgentMode = (node: OpsWorkflowNode) => {
  const configured = node.mode || node.config?.mode || node.config?.agentMode;
  if (String(configured || '').trim()) return normalizeAgentMode(configured);
  const type = normalizeNodeType(node.type);
  if (type === 'REVIEW' || type === 'REFLECT') return 'llm';
  if (['SUB_AGENT', 'EXECUTE', 'AGENTSCOPE', 'RAG', 'MCP', 'TOOL_CALL', 'KNOWLEDGE_RETRIEVAL'].includes(type)) return 'react';
  return 'llm';
};

const nodeNeedsExplicitResource = (node: OpsWorkflowNode) => {
  const type = normalizeNodeType(node.type);
  if (['START', 'END', 'ROUTER', 'SUB_WORKFLOW', 'HUMAN_APPROVAL', 'REPORT', 'NOTIFY'].includes(type)) {
    return false;
  }
  const mode = inferAgentMode(node);
  if (mode === 'direct') {
    return !Array.isArray(node.config?.actions) || node.config.actions.length === 0;
  }
  if (mode !== 'react') {
    return false;
  }
  const ragEnabled = Boolean(node.ragEnabled);
  return !ragEnabled && !(node.mcpIds || []).length && !(node.mcpServers || []).length;
};

const normalizeNodeForEditor = (node: OpsWorkflowNode): OpsWorkflowNode => {
  const type = normalizeNodeType(node.type);
  if (type === 'START' || type === 'END' || type === 'HUMAN_APPROVAL') {
    return { ...node, type };
  }
  if (type === 'ROUTER') {
    return {
      ...node,
      type: 'ROUTER',
      mode: undefined,
      outputKey: node.outputKey || 'selectedRoutes',
      config: {
        ...(node.config || {}),
        routeMode: node.config?.routeMode || 'multi',
        inputKey: node.config?.inputKey || 'selectedRoutes',
      },
    };
  }
  if (type === 'SUB_WORKFLOW') {
    return {
      ...node,
      type: 'SUB_WORKFLOW',
      mode: undefined,
      config: { ...(node.config || {}) },
    };
  }
  const mode = inferAgentMode(node);
  const legacyReview = String(node.mode || node.config?.mode || node.config?.agentMode || '').trim().toLowerCase() === 'review'
    || type === 'REVIEW'
    || type === 'REFLECT';
  const config = {
    ...(node.config || {}),
    mode,
    role: node.config?.role || (legacyReview ? 'reviewer' : roleForMode(mode)),
    reviewMode: undefined,
  };
  return {
    ...node,
    type: 'AGENT',
    mode,
    config,
  };
};

const normalizeEngineName = (engine?: string) => {
  const value = (engine || 'GRAPH').toUpperCase();
  if (value.includes('HYBRID')) return 'HYBRID';
  return 'GRAPH';
};

const ragModeOf = (value?: boolean) => {
  if (value === true) return 'enabled';
  if (value === false) return 'disabled';
  return 'inherit';
};

const ragEnabledOf = (mode: string): boolean | undefined => {
  if (mode === 'enabled') return true;
  if (mode === 'disabled') return false;
  return undefined;
};

const normalizeDefinitionForEditor = (definition: OpsAgentDefinition): OpsAgentDefinition => {
  const rest = stripRuntimeAgentScopeFields(definition);
  return materializeFeedbackLoopPolicies({
    ...rest,
    definitionKind: definition.definitionKind || 'SPECIALIZED_WORKFLOW',
    // Phase 652: user-facing specialized workflows are explicit-selection only.
    // Editing an older AUTO_ELIGIBLE definition migrates it to current product semantics.
    workflowInvocationMode: 'MANUAL_ONLY',
    workflowAutoSelectEnabled: false,
    workflowPriority: undefined,
    whenToUse: definition.whenToUse || [],
    whenNotToUse: definition.whenNotToUse || [],
    routingKeywords: definition.routingKeywords || [],
    name: definition.name,
    description: definition.description,
    engine: normalizeEngineName(definition.engine),
    queryRewriteEnabled: definition.queryRewriteEnabled !== false,
    nodes: (definition.nodes || []).map(normalizeNodeForEditor),
    edges: (definition.edges || []).map((edge) => ({
      ...edge,
      conditionType: edge.conditionType || (edge.feedback ? 'route_match' : 'always'),
      condition: edge.condition || 'always',
    })),
    loops: definition.loops || [],
  });
};

const createDefaultDefinition = (): OpsAgentDefinition => ({
  agentId: `ops-agent-${Date.now().toString(36)}`,
  schemaVersion: 1,
  name: '专项运维 Workflow',
  projectId: '',
  engine: 'GRAPH',
  description: '针对重复、稳定场景的可选执行流程；其他任务由项目主助手处理。',
  definitionKind: 'SPECIALIZED_WORKFLOW',
  workflowInvocationMode: 'MANUAL_ONLY',
  workflowAutoSelectEnabled: false,
  workflowPriority: 50,
  whenToUse: [],
  whenNotToUse: [],
  routingKeywords: [],
  instruction:
    '你是当前业务系统的运维 Agent。必须基于用户输入、知识库和工具 observation 回答；证据不足时明确说明缺口。',
  startNodeId: 'start',
  defaultMaxMainRounds: 3,
  defaultSubAgentMaxIterations: 3,
  queryRewriteEnabled: true,
  skills: [],
  nodes: [
    {
      nodeId: 'start',
      type: 'START',
      agent: 'start',
      description: '接收用户问题和项目上下文。',
      outputKey: 'query',
      config: { position: { x: 80, y: 220 }, inputKeys: ['query', 'sessionId', 'userId', 'metadata'] },
    },
    {
      nodeId: 'agent',
      type: 'AGENT',
      mode: 'llm',
      agent: 'ops-agent',
      description: '根据节点 Prompt 和显式绑定的 Skill、RAG、MCP 执行任务。',
      instruction: '分析当前问题并输出结论、证据、风险、建议和信息缺口。',
      outputKey: 'answer',
      skills: [],
      mcpIds: [],
      config: {
        position: { x: 380, y: 220 },
        mode: 'llm',
        role: 'general',
        contextInputs: ['query', 'rewrittenQuery', 'memoryContext', 'upstreamOutputs'],
      },
    },
    {
      nodeId: 'end',
      type: 'END',
      agent: 'end',
      description: '返回最终结果。',
      outputKey: 'final_output',
      config: { position: { x: 700, y: 220 }, outputKeys: ['answer'] },
    },
  ],
  edges: [
    { from: 'start', to: 'agent', conditionType: 'always', condition: 'always', description: '进入 Agent。' },
    { from: 'agent', to: 'end', conditionType: 'always', condition: 'always', description: '输出结果。' },
  ],
  loops: [],
});

const inferEngine = (definition: OpsAgentDefinition) => {
  const nodes = definition.nodes || [];
  const hasReactNode = nodes.some((node) =>
    node.subEngine === 'AGENTSCOPE'
    || normalizeNodeType(node.type) === 'AGENTSCOPE'
    || inferAgentMode(node) === 'react',
  );
  return hasReactNode ? 'HYBRID' : 'GRAPH';
};

export const AgentConfigPage: React.FC = () => {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const routeAgentId = searchParams.get('agentId') || undefined;
  const routeProjectId = searchParams.get('projectId') || '';
  const { collapsed, sidebarCollapsed, toggleSidebar, closeMobileSidebar } = useResponsiveSidebar();
  const [userInfo, setUserInfo] = useState<UserInfo | null>(null);
  const projectScope = useProjectScope();
  const librariesQuery = useAgentBuilderLibrariesQuery(Boolean(userInfo));
  const projects = projectScope.projects;
  const mcpOptions = librariesQuery.data?.mcps || [];
  const knowledgeBases = librariesQuery.data?.knowledgeBases || [];
  const modelOptions = librariesQuery.data?.models || [];
  const skillOptions = librariesQuery.data?.skills || [];
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [testing, setTesting] = useState(false);
  const [agents, setAgents] = useState<OpsAgentDefinition[]>([]);
  const [agentVersions, setAgentVersions] = useState<OpsAgentDefinition[]>([]);
  const [agentBindings, setAgentBindings] = useState<OpsAgentCapabilityBinding[]>([]);
  const [selectedAgentId, setSelectedAgentId] = useState<string>('');
  const [definition, setDefinition] = useState<OpsAgentDefinition>(createDefaultDefinition);
  const capabilitiesQuery = useAgentBuilderCapabilitiesQuery(definition.projectId || '', Boolean(userInfo));
  const agentCapabilities = capabilitiesQuery.data || null;
  const [selectedNodeId, setSelectedNodeId] = useState<string>('start');
  const [selectedEdgeIndex, setSelectedEdgeIndex] = useState<number | null>(null);
  const [activePanel, setActivePanel] = useState<PanelKey>('node');
  const [builderMode, setBuilderMode] = useState<AgentBuilderMode>(() => agentBuilderMode(searchParams.get('mode')));
  const [edgeDraft, setEdgeDraft] = useState<OpsGraphEdge>({ from: 'start', to: 'main-plan', conditionType: 'always', condition: 'always' });
  const [jsonDraft, setJsonDraft] = useState('');
  const [jsonDirty, setJsonDirty] = useState(false);
  const [testQuery, setTestQuery] = useState(defaultQuestion);
  const [runtimeEvents, setRuntimeEvents] = useState<OpsRuntimeEvent[]>([]);
  const [testResult, setTestResult] = useState('');
  const [versionLoading, setVersionLoading] = useState(false);
  const [versionOperating, setVersionOperating] = useState(false);
  const [showAdvancedNodeMcp, setShowAdvancedNodeMcp] = useState(false);
  const [nodeIdDraft, setNodeIdDraft] = useState('start');
  const [directActionsDraft, setDirectActionsDraft] = useState('[]');
  const advancedBuilder = isAgentBuilderAdvanced(builderMode);

  const handleBuilderModeChange = (mode: AgentBuilderMode) => {
    setBuilderMode(mode);
    setActivePanel((current) => agentBuilderPanelForMode(mode, current));
    if (mode === 'basic') {
      setShowAdvancedNodeMcp(false);
    }
  };

  const selectedNode = useMemo(
    () => (definition.nodes || []).find((node) => node.nodeId === selectedNodeId),
    [definition.nodes, selectedNodeId],
  );
  const selectedEdge = selectedEdgeIndex === null ? null : (definition.edges || [])[selectedEdgeIndex];
  const selectedEdgeKey = selectedEdge && selectedEdgeIndex !== null ? edgeKeyOf(selectedEdge, selectedEdgeIndex) : undefined;
  const selectedProject = useMemo(
    () => projects.find((project) => project.projectId === definition.projectId),
    [definition.projectId, projects],
  );
  const agentBindingStats = useMemo(() => {
    const initial = {
      skill: 0,
      projectTool: 0,
      knowledgeBase: 0,
      executionTarget: 0,
      inlineMcp: 0,
      other: 0,
    };
    return agentBindings.reduce((stats, binding) => {
      const type = String(binding.capabilityType || '');
      if (type === 'skill') stats.skill += 1;
      else if (type === 'project_tool') stats.projectTool += 1;
      else if (type === 'knowledge_base') stats.knowledgeBase += 1;
      else if (type === 'execution_target') stats.executionTarget += 1;
      else if (type === 'inline_mcp_server') stats.inlineMcp += 1;
      else stats.other += 1;
      return stats;
    }, initial);
  }, [agentBindings]);
  const authorizedSkillIds = useMemo(
    () => new Set((agentCapabilities?.skillIds || selectedProject?.skillIds || []).map(String)),
    [agentCapabilities?.skillIds, selectedProject?.skillIds],
  );
  const authorizedKnowledgeBaseIds = useMemo(() => {
    const ids = agentCapabilities?.knowledgeBaseIds?.length
      ? agentCapabilities.knowledgeBaseIds
      : selectedProject?.knowledgeBaseId
      ? [selectedProject.knowledgeBaseId]
      : [];
    return new Set(ids.map(String).filter(Boolean));
  }, [agentCapabilities?.knowledgeBaseIds, selectedProject?.knowledgeBaseId]);
  const authorizedKnowledgeBases = useMemo(
    () => Array.from(authorizedKnowledgeBaseIds).map((id) => knowledgeBaseSummaryOf(id, knowledgeBases)),
    [authorizedKnowledgeBaseIds, knowledgeBases],
  );
  const executionTargetOptions = useMemo(
    () => (agentCapabilities?.executionTargets || [])
      .map((item) => ({
        executionTargetId: String(item.executionTargetId || item.resourceId || item.id || ''),
        name: String(item.targetName || item.name || item.executionTargetId || item.resourceId || ''),
        adapterType: String(item.adapterType || ''),
        status: String(item.status || ''),
      }))
      .filter((item) => item.executionTargetId),
    [agentCapabilities?.executionTargets],
  );
  const projectMcpOptions = useMemo<OpsGeneratedMcp[]>(
    () => selectedProject?.generatedMcps || [],
    [selectedProject],
  );
  const projectSkillOptions = useMemo(
    () => {
      if (agentCapabilities?.projectSkills) {
        return agentCapabilities.projectSkills
          .map((skill) => skillOptionFromCapability(skill, skillOptions, 'PROJECT_AUTHORIZED', '项目已授权 Skill'))
          .filter((skill): skill is OpsSkillSummary => Boolean(skill));
      }
      const options = skillOptions.filter((skill) => authorizedSkillIds.has(skillValueOf(skill))
        && isProjectSkill(skill, definition.projectId, Array.from(authorizedSkillIds)));
      const known = new Set(options.map(skillValueOf));
      const idOnlyOptions = Array.from(authorizedSkillIds)
        .filter((skillId) => !known.has(skillId))
        .map((skillId) => ({ name: skillId, description: '项目已授权 Skill' }));
      return [...options, ...idOnlyOptions];
    },
    [agentCapabilities?.projectSkills, authorizedSkillIds, definition.projectId, skillOptions],
  );
  const globalSkillOptions = useMemo(
    () => {
      if (agentCapabilities?.enabledGlobalSkills) {
        return agentCapabilities.enabledGlobalSkills
          .map((skill) => skillOptionFromCapability(skill, skillOptions, 'GLOBAL_ENABLED', '项目已启用通用 Skill'))
          .filter((skill): skill is OpsSkillSummary => Boolean(skill));
      }
      return skillOptions.filter((skill) => authorizedSkillIds.has(skillValueOf(skill))
        && !isProjectSkill(skill, definition.projectId, Array.from(authorizedSkillIds)));
    },
    [agentCapabilities?.enabledGlobalSkills, authorizedSkillIds, definition.projectId, skillOptions],
  );
  const unifiedMcpOptions = useMemo<McpOption[]>(() => {
    if (agentCapabilities) {
      const options = [
        ...(agentCapabilities.projectTools || [])
          .map((item) => mcpOptionFromCapability(item, `项目生成工具 / ${agentCapabilities.projectName || definition.projectId}`, definition.projectId))
          .filter((item): item is McpOption => Boolean(item)),
        ...(agentCapabilities.enabledSharedTools || [])
          .map((item) => mcpOptionFromCapability(item, '项目启用共享工具'))
          .filter((item): item is McpOption => Boolean(item)),
      ];
      const seen = new Set<string>();
      return options.filter((item) => {
        if (!item.mcpId || seen.has(item.mcpId)) return false;
        seen.add(item.mcpId);
        return true;
      });
    }
    const sharedMcpIds = new Set((selectedProject?.sharedMcpIds || []).map(String));
    const options: McpOption[] = [
      ...projectMcpOptions.map((item) => ({
        mcpId: item.mcpId,
        mcpName: item.mcpName,
        transportType: item.transportType,
        projectId: item.projectId,
        resourceType: item.resourceType,
        scopeLabel: `项目生成工具 / ${selectedProject?.name || item.projectId} / ${item.resourceType}`,
      })),
      ...mcpOptions
        .filter((item) => sharedMcpIds.has(String(item.mcpId)))
        .map((item) => ({
          mcpId: item.mcpId,
          mcpName: item.mcpName,
          transportType: item.transportType,
          scopeLabel: '项目启用共享工具',
        })),
    ];
    const seen = new Set<string>();
    return options.filter((item) => {
      if (!item.mcpId || seen.has(item.mcpId)) return false;
      seen.add(item.mcpId);
      return true;
    });
  }, [agentCapabilities, definition.projectId, mcpOptions, projectMcpOptions, selectedProject?.name, selectedProject?.sharedMcpIds]);
  const projectGeneratedMcpOptions = useMemo(
    () => unifiedMcpOptions.filter((item) => Boolean(item.projectId)),
    [unifiedMcpOptions],
  );
  const sharedProjectMcpOptions = useMemo(
    () => unifiedMcpOptions.filter((item) => !item.projectId),
    [unifiedMcpOptions],
  );
  const renderSkillOptionGroups = () => (
    <>
      <Option disabled value="__project_skill_group">
        项目 Skill
      </Option>
      {projectSkillOptions.length ? (
        projectSkillOptions.map((skill) => (
          <Option key={`project-skill-${skillValueOf(skill)}`} value={skillValueOf(skill)}>
            {renderSkillOptionLabel(skill)}
          </Option>
        ))
      ) : (
        <Option disabled value="__project_skill_empty">
          暂无项目 Skill
        </Option>
      )}
      <Option disabled value="__global_skill_group">
        已启用通用 Skill
      </Option>
      {globalSkillOptions.length ? (
        globalSkillOptions.map((skill) => (
          <Option key={`global-skill-${skillValueOf(skill)}`} value={skillValueOf(skill)}>
            {renderSkillOptionLabel(skill)}
          </Option>
        ))
      ) : (
        <Option disabled value="__global_skill_empty">
          暂无已启用通用 Skill
        </Option>
      )}
    </>
  );
  const renderMcpOptionGroups = () => (
    <>
      <Option disabled value="__project_generated_mcp_group">
        项目生成工具
      </Option>
      {projectGeneratedMcpOptions.length ? (
        projectGeneratedMcpOptions.map((option) => (
          <Option key={`project-mcp-${option.mcpId}`} value={option.mcpId}>
            {option.mcpName || option.mcpId} · {option.transportType || 'stdio'} · {option.scopeLabel}
          </Option>
        ))
      ) : (
        <Option disabled value="__project_generated_mcp_empty">
          暂无项目生成工具
        </Option>
      )}
      <Option disabled value="__project_shared_mcp_group">
        项目启用共享工具
      </Option>
      {sharedProjectMcpOptions.length ? (
        sharedProjectMcpOptions.map((option) => (
          <Option key={`shared-mcp-${option.mcpId}`} value={option.mcpId}>
            {option.mcpName || option.mcpId} · {option.transportType || 'stdio'} · {option.scopeLabel}
          </Option>
        ))
      ) : (
        <Option disabled value="__project_shared_mcp_empty">
          暂无项目启用共享工具
        </Option>
      )}
    </>
  );
  const definitionJson = useMemo(() => JSON.stringify(cleanDefinition(definition), null, 2), [definition]);
  const nodeIdDraftError = useMemo(() => {
    if (!selectedNode) return '';
    const value = nodeIdDraft.trim();
    if (!value) return '节点 ID 不能为空';
    if ((definition.nodes || []).some((node) => node.nodeId === value && node.nodeId !== selectedNode.nodeId)) {
      return `节点 ID "${value}" 已存在`;
    }
    return '';
  }, [definition.nodes, nodeIdDraft, selectedNode]);

  useEffect(() => {
    const token = localStorage.getItem('token');
    const storedUserInfo = localStorage.getItem('userInfo');
    if (!token || !storedUserInfo) {
      Toast.error('请先登录');
      navigate('/login');
      return;
    }
    try {
      setUserInfo(JSON.parse(storedUserInfo));
    } catch (error) {
      Toast.error('用户信息解析失败');
      navigate('/login');
    }
  }, [navigate]);

  useEffect(() => {
    if (!jsonDirty) {
      setJsonDraft(definitionJson);
    }
  }, [definitionJson, jsonDirty]);

  useEffect(() => {
    setNodeIdDraft(selectedNode?.nodeId || '');
  }, [selectedNode?.nodeId]);

  useEffect(() => {
    setDirectActionsDraft(JSON.stringify(selectedNode?.config?.actions || [], null, 2));
  }, [selectedNode?.nodeId, selectedNode?.config?.actions]);

  useEffect(() => {
    const nodes = definition.nodes || [];
    if (!nodes.length) return;
    setEdgeDraft((current) => {
      const hasFrom = nodes.some((node) => node.nodeId === current.from);
      const hasTo = nodes.some((node) => node.nodeId === current.to);
      return {
        ...current,
        from: hasFrom ? current.from : nodes[0].nodeId,
        to: hasTo ? current.to : nodes[1]?.nodeId || nodes[0].nodeId,
        condition: current.condition || 'always',
      };
    });
  }, [definition.nodes]);

  const loadAgentVersions = useCallback(async (agentId?: string) => {
    if (!agentId) {
      setAgentVersions([]);
      return;
    }
    setVersionLoading(true);
    try {
      const response = await opsAgentDefinitionService.listAgentVersions(agentId);
      setAgentVersions((response.data || []).map(normalizeDefinitionForEditor));
    } catch (error) {
      setAgentVersions([]);
    } finally {
      setVersionLoading(false);
    }
  }, []);

  const handleAuthExpired = useCallback(() => {
    clearAuthSession();
    Toast.error('登录已过期，请重新登录');
    navigate('/login');
  }, [navigate]);

  const refreshAgentBindings = useCallback(async (agentId?: string) => {
    if (!agentId) {
      setAgentBindings([]);
      return;
    }
    try {
      const response = await opsAgentDefinitionService.getAgentBindings(agentId);
      setAgentBindings(response.data || []);
    } catch (error) {
      setAgentBindings([]);
      if (isUnauthorizedError(error)) {
        handleAuthExpired();
        return;
      }
      Toast.warning(`Agent 能力绑定加载失败：${errorMessageOf(error)}`);
    }
  }, [handleAuthExpired]);

  const refreshAgents = useCallback(async (preferredId?: string) => {
    if (!routeProjectId) {
      Toast.warning('请先从 Agent 页面选择项目');
      navigate('/agent-list');
      return;
    }
    setLoading(true);
    try {
      const response = await opsAgentDefinitionService.listAgents(routeProjectId);
      let list = (response.data || [])
        .filter((item) => item.definitionKind === 'SPECIALIZED_WORKFLOW')
        .map(normalizeDefinitionForEditor);
      if (!list.some((item) => item.agentId === preferredId) && preferredId) {
        let preferred: OpsAgentDefinition | undefined;
        const detail = await opsAgentDefinitionService.getAgent(preferredId);
        if (detail.data) {
          preferred = normalizeDefinitionForEditor(detail.data);
        }
        if (!preferred) {
          const versions = await opsAgentDefinitionService.listAgentVersions(preferredId);
          const latestVersion = (versions.data || [])[0];
          if (latestVersion) {
            preferred = normalizeDefinitionForEditor(latestVersion);
          }
        }
        if (preferred) {
          if (preferred.projectId !== routeProjectId) {
            Toast.error('该 Agent 不属于当前项目');
            navigate(`/agent-list?projectId=${encodeURIComponent(routeProjectId)}`, { replace: true });
            return;
          }
          if (preferred.definitionKind !== 'SPECIALIZED_WORKFLOW') {
            Toast.warning('项目主助手由平台统一管理，不能在专项 Workflow 页面编辑');
            navigate(`/agent-list?projectId=${encodeURIComponent(routeProjectId)}`, { replace: true });
            return;
          }
          list = [preferred, ...list.filter((item) => item.agentId !== preferred.agentId)];
        }
      }
      setAgents(list);
      const target = list.find((item) => item.agentId === preferredId) || list[0];
      if (target) {
        setSelectedAgentId(target.agentId);
        setDefinition(target);
        setSelectedNodeId(target.startNodeId || target.nodes?.[0]?.nodeId || '');
        loadAgentVersions(target.agentId);
        refreshAgentBindings(target.agentId);
      } else {
        const next = createDefaultDefinition();
        next.projectId = routeProjectId;
        setDefinition(next);
        setSelectedNodeId(next.startNodeId || 'start');
        setAgentVersions([]);
        setAgentBindings([]);
      }
    } catch (error) {
      console.error('加载专项 Workflow 失败:', error);
      if (isUnauthorizedError(error)) {
        handleAuthExpired();
        return;
      }
      Toast.error(`加载专项 Workflow 失败：${errorMessageOf(error)}`);
      setDefinition(createDefaultDefinition());
      setAgentVersions([]);
      setAgentBindings([]);
    } finally {
      setLoading(false);
    }
  }, [handleAuthExpired, loadAgentVersions, navigate, refreshAgentBindings, routeProjectId]);

  useEffect(() => {
    if (userInfo) {
      refreshAgents(routeAgentId);
    }
  }, [refreshAgents, routeAgentId, userInfo]);

  useEffect(() => {
    if (!capabilitiesQuery.error) return;
    if (isUnauthorizedError(capabilitiesQuery.error)) {
      handleAuthExpired();
      return;
    }
    Toast.warning(`项目可绑定能力加载失败，将仅使用项目快照兜底：${errorMessageOf(capabilitiesQuery.error)}`);
  }, [capabilitiesQuery.error, handleAuthExpired]);

  const updateDefinition = useCallback((updater: (current: OpsAgentDefinition) => OpsAgentDefinition) => {
    setDefinition((current) => updater(current));
    setJsonDirty(false);
  }, []);

  const updateNode = useCallback(
    (nodeId: string, patch: Partial<OpsWorkflowNode>) => {
      updateDefinition((current) => ({
        ...current,
        nodes: (current.nodes || []).map((node) => (node.nodeId === nodeId ? { ...node, ...patch } : node)),
      }));
    },
    [updateDefinition],
  );

  useEffect(() => {
    if (!definition.projectId && routeProjectId && projects.length > 0) {
      const routeProject = projects.find((project) => project.projectId === routeProjectId);
      updateDefinition((current) => ({
        ...current,
        projectId: routeProjectId,
        knowledgeBaseId: current.knowledgeBaseId || routeProject?.knowledgeBaseId,
      }));
    }
  }, [definition.projectId, projects, routeProjectId, updateDefinition]);

  const handleNavigation = (path: string) => {
    const route = path.startsWith('/') ? path : `/${path}`;
    navigate(route);
  };

  const handleLogout = () => {
    clearAuthSession();
    Toast.success('已退出登录');
    navigate('/login');
  };

  const handleSelectAgent = async (agentId: string) => {
    const localAgent = agents.find((item) => item.agentId === agentId);
    if (localAgent) {
      setSelectedAgentId(localAgent.agentId);
      setDefinition(localAgent);
      setSelectedNodeId(localAgent.startNodeId || localAgent.nodes?.[0]?.nodeId || '');
      setSelectedEdgeIndex(null);
      setActivePanel('node');
      setRuntimeEvents([]);
      setTestResult('');
      loadAgentVersions(localAgent.agentId);
    } else {
      setLoading(true);
    }
    try {
      const response = await opsAgentDefinitionService.getAgent(agentId);
      const next = response.data ? normalizeDefinitionForEditor(response.data) : agents.find((item) => item.agentId === agentId);
      if (!next) {
        Toast.warning('未找到该 Agent 定义');
        return;
      }
      setSelectedAgentId(next.agentId);
      setDefinition(next);
      setSelectedNodeId(next.startNodeId || next.nodes?.[0]?.nodeId || '');
      setSelectedEdgeIndex(null);
      setActivePanel('node');
      setRuntimeEvents([]);
      setTestResult('');
      loadAgentVersions(next.agentId);
    } catch (error) {
      console.error('加载 Agent 详情失败:', error);
      if (isUnauthorizedError(error)) {
        handleAuthExpired();
        return;
      }
      if (!localAgent) {
        Toast.error(`加载 Agent 详情失败：${errorMessageOf(error)}`);
      }
    } finally {
      setLoading(false);
    }
  };

  const validateDefinition = () => {
    if (!definition.projectId || definition.projectId !== routeProjectId) {
      Toast.error('Agent 必须归属当前项目，请从 Agent 列表重新进入');
      return false;
    }
    if (!definition.agentId?.trim()) {
      Toast.error('Agent ID 不能为空');
      return false;
    }
    if (!definition.name?.trim()) {
      Toast.error('请填写专项 Workflow 名称');
      return false;
    }
    const nodes = definition.nodes || [];
    const nodeIds = new Set(nodes.map((node) => node.nodeId));
    if (nodeIds.size !== nodes.length) {
      Toast.error(`节点 ID 不能重复：${duplicateNodeIdOf(nodes) || '-'}`);
      return false;
    }
    const engine = inferEngine(definition);
    if (['GRAPH', 'HYBRID'].includes(engine) && !nodeIds.has(definition.startNodeId || '')) {
      Toast.error('画布编排必须设置有效的起始节点');
      return false;
    }
    const startNodes = nodes.filter((node) => normalizeNodeType(node.type) === 'START');
    if (['GRAPH', 'HYBRID'].includes(engine) && startNodes.length !== 1) {
      Toast.error('画布编排必须有且只有一个 Start 节点');
      return false;
    }
    if (startNodes.length === 1 && definition.startNodeId !== startNodes[0].nodeId) {
      Toast.error(`起始节点必须指向 Start 节点：${startNodes[0].nodeId}`);
      return false;
    }
    const endNodes = nodes.filter((node) => normalizeNodeType(node.type) === 'END');
    if (['GRAPH', 'HYBRID'].includes(engine) && !endNodes.length) {
      Toast.error('画布编排至少需要一个 End 节点作为结束出口');
      return false;
    }
    const invalidNode = nodes.find((node) => {
      const type = normalizeNodeType(node.type);
      return !type || !node.nodeId || (['AGENT', 'ROUTER'].includes(type) && !node.agent);
    });
    if (invalidNode) {
      Toast.error(`节点 ${invalidNode.nodeId || '-'} 缺少类型或执行 Agent`);
      return false;
    }
    const invalidEdge = (definition.edges || []).find((edge) => !nodeIds.has(edge.from) || !nodeIds.has(edge.to));
    if (invalidEdge) {
      Toast.error(`边 ${invalidEdge.from} -> ${invalidEdge.to} 引用了不存在的节点`);
      return false;
    }
    if (['GRAPH', 'HYBRID'].includes(engine)) {
      const reachable = new Set<string>();
      const pendingNodes = definition.startNodeId ? [definition.startNodeId] : [];
      while (pendingNodes.length) {
        const currentNodeId = pendingNodes.shift();
        if (!currentNodeId || reachable.has(currentNodeId)) continue;
        reachable.add(currentNodeId);
        (definition.edges || [])
          .filter((edge) => edge.from === currentNodeId)
          .forEach((edge) => {
            if (!reachable.has(edge.to)) pendingNodes.push(edge.to);
          });
      }
      const unreachable = nodes.find((node) => !reachable.has(node.nodeId));
      if (unreachable) {
        Toast.error(`存在不可达节点：${unreachable.nodeId}。请从 Start 建立有效路径或删除该节点。`);
        return false;
      }
    }
    const nodeById = new Map(nodes.map((node) => [node.nodeId, node]));
    const invalidRouteEdge = (definition.edges || []).find((edge) => {
      if (!isRouteConditionEdge(edge)) {
        return false;
      }
      return normalizeNodeType(nodeById.get(edge.from)?.type) !== 'ROUTER';
    });
    if (invalidRouteEdge) {
      Toast.error(`路由条件边必须从 Router 节点发出：${invalidRouteEdge.from} -> ${invalidRouteEdge.to}`);
      return false;
    }
    const invalidFeedbackEdge = (definition.edges || []).find((edge) =>
      edge.feedback && !isRouterNodeId(nodes, edge.from),
    );
    if (invalidFeedbackEdge) {
      Toast.error(`回边必须从 Router 节点发出：${invalidFeedbackEdge.from} -> ${invalidFeedbackEdge.to}`);
      return false;
    }
    const routerWithoutSelectiveEdge = nodes.find((node) => {
      if (normalizeNodeType(node.type) !== 'ROUTER') return false;
      const outgoing = (definition.edges || []).filter((edge) => edge.from === node.nodeId);
      if (outgoing.length <= 1) return false;
      return outgoing.every((edge) => ['always', ''].includes(String(edge.conditionType || 'always').toLowerCase()));
    });
    if (routerWithoutSelectiveEdge) {
      Toast.error(`Router 节点 ${routerWithoutSelectiveEdge.nodeId} 有多条出边时，至少需要 route_match、review_decision 或 default 条件来表达选择关系`);
      return false;
    }
    const routerWithoutDefault = nodes.find((node) => {
      if (normalizeNodeType(node.type) !== 'ROUTER') return false;
      const outgoing = (definition.edges || []).filter((edge) => edge.from === node.nodeId);
      if (outgoing.length <= 1) return false;
      return !outgoing.some((edge) => String(edge.conditionType || '').toLowerCase() === 'default' || edge.defaultEdge === true);
    });
    if (routerWithoutDefault) {
      Toast.error(`Router 节点 ${routerWithoutDefault.nodeId} 缺少 default 出口，无法对未命中条件 fail-safe 收口。`);
      return false;
    }
    const invalidLoop = (definition.loops || []).find((loop) => {
      if (!loop.loopId?.trim()) return true;
      if (!loop.maxRounds || loop.maxRounds < 1 || loop.maxRounds > 20) return true;
      if ((loop.nodes || []).some((nodeId) => !nodeIds.has(nodeId))) return true;
      const edgeRefs = new Set((definition.edges || []).map((edge) => edgeRefOf(edge)));
      if ((loop.feedbackEdges || []).some((edgeRef) => !edgeRefs.has(edgeRef))) return true;
      return false;
    });
    if (invalidLoop) {
      Toast.error(`Loop ${invalidLoop.loopId || '-'} 配置无效：maxRounds 必须为 1~20，节点和回边必须真实存在。`);
      return false;
    }
    const nonRouterConditionalFanout = nodes.find((node) => {
      if (normalizeNodeType(node.type) === 'ROUTER') return false;
      const outgoing = (definition.edges || []).filter((edge) => edge.from === node.nodeId);
      return outgoing.length > 1 && outgoing.some((edge) => !['always', 'default'].includes(String(edge.conditionType || 'always').toLowerCase()));
    });
    if (nonRouterConditionalFanout) {
      Toast.error(`节点 ${nonRouterConditionalFanout.nodeId} 如果要按条件选择多个下游，请先连接到 Router，再由 Router 分发`);
      return false;
    }
    const missingResourceNode = nodes.find((node) => nodeNeedsExplicitResource(node));
    if (missingResourceNode) {
      Toast.error(`ReAct 节点 ${missingResourceNode.nodeId} 必须显式选择 MCP，或启用 RAG。不能通过节点名称或路由条件推断工具能力。`);
      return false;
    }
    const knownModelIds = new Set(modelOptions.map((item) => item.modelId).filter(Boolean));
    const knownMcpIds = new Set(unifiedMcpOptions.map((item) => item.mcpId).filter(Boolean));
    const knownKnowledgeIds = new Set(authorizedKnowledgeBases.flatMap((item) => [item.kbId, item.knowledgeTag, item.ragId].filter(Boolean) as string[]));
    const knownExecutionTargetIds = new Set(executionTargetOptions.map((item) => item.executionTargetId));
    const validateSingleRef = (value: string | undefined, knownValues: Set<string>, label: string) => {
      if (!value || knownValues.size === 0 || knownValues.has(value)) return true;
      Toast.error(`${label} 不存在或未启用: ${value}`);
      return false;
    };
    const validateManyRefs = (values: string[] | undefined, knownValues: Set<string>, label: string) => {
      if (!values?.length || knownValues.size === 0) return true;
      const missing = values.find((value) => !knownValues.has(value));
      if (!missing) return true;
      Toast.error(`${label} 不存在或未启用: ${missing}`);
      return false;
    };
    if (!validateSingleRef(definition.modelId, knownModelIds, 'Agent Model')) return false;
    if (!validateSingleRef(definition.knowledgeBaseId, knownKnowledgeIds, 'Agent 知识库')) return false;
    if (!validateManyRefs(definition.skills, authorizedSkillIds, 'Agent Skill')) return false;
    if (!validateManyRefs(definition.mcpIds, knownMcpIds, 'Agent MCP')) return false;
    if (!validateManyRefs(definition.executionTargetIds, knownExecutionTargetIds, 'Agent 执行目标')) return false;
    for (const node of nodes) {
      if (!validateSingleRef(node.modelId, knownModelIds, `节点 ${node.nodeId} Model`)) return false;
      if (!validateSingleRef(node.knowledgeBaseId, knownKnowledgeIds, `节点 ${node.nodeId} 知识库`)) return false;
      if (!validateManyRefs(node.skills, authorizedSkillIds, `节点 ${node.nodeId} Skill`)) return false;
      if (!validateManyRefs(node.mcpIds, knownMcpIds, `节点 ${node.nodeId} MCP`)) return false;
      if (!validateManyRefs(node.executionTargetIds, knownExecutionTargetIds, `节点 ${node.nodeId} 执行目标`)) return false;
    }
    const inlineMcpServers = [
      ...(definition.mcpServers || []),
      ...nodes.flatMap((node) => node.mcpServers || []),
    ];
    const invalidMcp = inlineMcpServers
      .find((server) => !server.name || (!server.command && !server.url));
    if (invalidMcp) {
      Toast.error(`MCP 配置 ${invalidMcp.name || '-'} 缺少 command 或 url`);
      return false;
    }
    const validCapabilities = new Set(['read_only', 'notification', 'mutating', 'blocked']);
    const invalidCapability = inlineMcpServers.find((server) =>
      Object.values(server.toolCapabilities || {}).some((value) => !validCapabilities.has(String(value))),
    );
    if (invalidCapability) {
      Toast.error(`MCP 配置 ${invalidCapability.name || '-'} 存在非法工具能力`);
      return false;
    }
    return true;
  };

  const validateDefinitionBindings = async (payload: OpsAgentDefinition) => {
    const response = await opsAgentDefinitionService.validateAgentBindings(payload.agentId || 'draft', payload);
    const result = response.data;
    if (!result?.valid) {
      const message = result?.errors?.length
        ? result.errors.join('；')
        : 'Agent 绑定能力未通过项目授权校验';
      Toast.error(message);
      return false;
    }
    if (result.warnings?.length) {
      Toast.warning(result.warnings.join('；'));
    }
    return true;
  };

  const handleSave = async () => {
    if (!validateDefinition()) return;
    setSaving(true);
    try {
      const payload = cleanDefinition(definition);
      if (!(await validateDefinitionBindings(payload))) return;
      const draftResponse = await opsAgentDefinitionService.saveAgent(payload);
      const draft = normalizeDefinitionForEditor(draftResponse.data || payload);
      const version = Number(draft.version || 0);
      if (!version) {
        throw new Error('Workflow 保存后未返回可发布版本');
      }
      await opsAgentDefinitionService.validateAgentVersion(draft.agentId, version);
      const publishResponse = await opsAgentDefinitionService.publishAgentVersion(draft.agentId, version);
      const published = normalizeDefinitionForEditor(publishResponse.data || draft);
      setDefinition(published);
      setSelectedAgentId(published.agentId);
      setAgents((current) => {
        const without = current.filter((item) => item.agentId !== published.agentId);
        return [published, ...without];
      });
      await loadAgentVersions(published.agentId);
      await refreshAgentBindings(published.agentId);
      Toast.success('专项 Workflow 已保存并发布');
    } catch (error) {
      console.error('保存专项 Workflow 失败:', error);
      if (isUnauthorizedError(error)) {
        handleAuthExpired();
        return;
      }
      Toast.error(`保存专项 Workflow 失败：${errorMessageOf(error)}`);
    } finally {
      setSaving(false);
    }
  };

  const handleSaveDraft = async () => {
    if (!validateDefinition()) return;
    setSaving(true);
    try {
      const payload = cleanDefinition(definition);
      if (!(await validateDefinitionBindings(payload))) return;
      const response = await opsAgentDefinitionService.saveAgentDraft(payload);
      const saved = normalizeDefinitionForEditor(response.data || payload);
      setDefinition(saved);
      setSelectedAgentId(saved.agentId);
      await loadAgentVersions(saved.agentId);
      await refreshAgentBindings(saved.agentId);
      Toast.success('专项 Workflow 草稿已保存');
    } catch (error) {
      console.error('保存 Agent 草稿失败:', error);
      if (isUnauthorizedError(error)) {
        handleAuthExpired();
        return;
      }
      Toast.error(`保存 Agent 草稿失败：${errorMessageOf(error)}`);
    } finally {
      setSaving(false);
    }
  };

  const handleVersionAction = async (action: 'validate' | 'publish' | 'rollback' | 'disable', version?: number) => {
    if (!selectedAgentId || !version) {
      return;
    }
    setVersionOperating(true);
    try {
      if (action === 'validate') {
        const response = await opsAgentDefinitionService.validateAgentVersion(selectedAgentId, version);
        if (response.data) setDefinition(normalizeDefinitionForEditor(response.data));
        Toast.success(`版本 v${version} 校验通过`);
      } else if (action === 'publish') {
        const response = await opsAgentDefinitionService.publishAgentVersion(selectedAgentId, version);
        if (response.data) setDefinition(normalizeDefinitionForEditor(response.data));
        Toast.success(`版本 v${version} 已发布`);
        await refreshAgents(selectedAgentId);
      } else if (action === 'rollback') {
        const response = await opsAgentDefinitionService.rollbackAgentVersion(selectedAgentId, version);
        if (response.data) setDefinition(normalizeDefinitionForEditor(response.data));
        Toast.success(`已回滚到 v${version}`);
        await refreshAgents(selectedAgentId);
      } else {
        await opsAgentDefinitionService.disableAgentVersion(selectedAgentId, version);
        Toast.success(`版本 v${version} 已停用`);
      }
      await loadAgentVersions(selectedAgentId);
      await refreshAgentBindings(selectedAgentId);
    } catch (error) {
      console.error('版本操作失败:', error);
      if (isUnauthorizedError(error)) {
        handleAuthExpired();
        return;
      }
      Toast.error(`版本操作失败：${errorMessageOf(error)}`);
    } finally {
      setVersionOperating(false);
    }
  };

  const handleDeleteAgent = async () => {
    if (!selectedAgentId) return;
    try {
      await opsAgentDefinitionService.deleteAgent(selectedAgentId);
      Toast.success('专项 Workflow 已停用');
      setSelectedAgentId('');
      const next = createDefaultDefinition();
      setDefinition(next);
      setSelectedNodeId(next.startNodeId || next.nodes?.[0]?.nodeId || '');
      setAgentVersions([]);
      refreshAgents();
    } catch (error) {
      console.error('停用专项 Workflow 失败:', error);
      if (isUnauthorizedError(error)) {
        handleAuthExpired();
        return;
      }
      Toast.error(`停用专项 Workflow 失败：${errorMessageOf(error)}`);
    }
  };

  const handleCreateAgent = () => {
    const routeProject = projects.find((project) => project.projectId === routeProjectId);
    const next = {
      ...createDefaultDefinition(),
      projectId: routeProjectId,
      knowledgeBaseId: routeProject?.knowledgeBaseId,
    };
    setSelectedAgentId('');
    setDefinition(next);
    setSelectedNodeId(next.startNodeId || next.nodes?.[0]?.nodeId || '');
    setSelectedEdgeIndex(null);
    setRuntimeEvents([]);
    setAgentVersions([]);
    setAgentBindings([]);
    setTestResult('');
    setActivePanel('node');
  };

  const handleAddNode = () => {
    const existingNodes = definition.nodes || [];
    const count = existingNodes.length + 1;
    const nodeId = nextSequentialNodeId(existingNodes, 'agent');
    const nextNode: OpsWorkflowNode = {
      nodeId,
      type: 'AGENT',
      mode: 'llm',
      agent: `ops-${nodeId}`,
      description: '新的 Agent 节点',
      instruction: '说明这个 Agent 的职责、可用上下文、工具使用方式和输出格式。',
      outputKey: `${nodeId}_result`,
      config: { position: { x: 80 + count * 40, y: 80 + count * 36 }, mode: 'llm', role: 'general', contextInputs: ['query', 'upstreamOutputs'] },
    };
    updateDefinition((current) => ({
      ...current,
      startNodeId: current.startNodeId || nodeId,
      nodes: [...(current.nodes || []), nextNode],
    }));
    setSelectedNodeId(nodeId);
    setActivePanel('node');
  };

  const handleDeleteNode = () => {
    if (!selectedNodeId) return;
    updateDefinition((current) => {
      const nodes = (current.nodes || []).filter((node) => node.nodeId !== selectedNodeId);
      return {
        ...current,
        startNodeId: current.startNodeId === selectedNodeId ? nodes[0]?.nodeId : current.startNodeId,
        nodes,
        edges: (current.edges || []).filter((edge) => edge.from !== selectedNodeId && edge.to !== selectedNodeId),
        loops: (current.loops || []).map((loop) => ({
          ...loop,
          nodes: (loop.nodes || []).filter((nodeId) => nodeId !== selectedNodeId),
          feedbackEdges: (loop.feedbackEdges || []).filter((edgeId) => !edgeId.includes(`${selectedNodeId}->`) && !edgeId.includes(`->${selectedNodeId}`)),
        })),
      };
    });
    const nextNode = (definition.nodes || []).find((node) => node.nodeId !== selectedNodeId);
    setSelectedNodeId(nextNode?.nodeId || '');
  };

  const handleRenameNode = (nextNodeId: string) => {
    const value = nextNodeId.trim();
    if (!selectedNode || value === selectedNode.nodeId) return;
    if (!value) {
      Toast.error('节点 ID 不能为空');
      return;
    }
    if ((definition.nodes || []).some((node) => node.nodeId === value && node.nodeId !== selectedNode.nodeId)) {
      Toast.error(`节点 ID "${value}" 已存在`);
      return;
    }
    const oldNodeId = selectedNode.nodeId;
    updateDefinition((current) => ({
      ...current,
      startNodeId: current.startNodeId === oldNodeId ? value : current.startNodeId,
      nodes: (current.nodes || []).map((node) => (node.nodeId === oldNodeId ? { ...node, nodeId: value } : node)),
      edges: (current.edges || []).map((edge) => ({
        ...edge,
        from: edge.from === oldNodeId ? value : edge.from,
        to: edge.to === oldNodeId ? value : edge.to,
        edgeId: edgeIdOf({
          from: edge.from === oldNodeId ? value : edge.from,
          to: edge.to === oldNodeId ? value : edge.to,
        }),
      })),
      loops: (current.loops || []).map((loop) => ({
        ...loop,
        nodes: (loop.nodes || []).map((nodeId) => (nodeId === oldNodeId ? value : nodeId)),
        feedbackEdges: (loop.feedbackEdges || []).map((edgeId) => edgeId.replace(`${oldNodeId}->`, `${value}->`).replace(`->${oldNodeId}`, `->${value}`)),
      })),
    }));
    setSelectedNodeId(value);
  };

  const commitNodeIdDraft = () => {
    if (!selectedNode) return;
    const value = nodeIdDraft.trim();
    if (value === selectedNode.nodeId) {
      setNodeIdDraft(selectedNode.nodeId);
      return;
    }
    if (!value) {
      Toast.error('节点 ID 不能为空');
      setNodeIdDraft(selectedNode.nodeId);
      return;
    }
    if ((definition.nodes || []).some((node) => node.nodeId === value && node.nodeId !== selectedNode.nodeId)) {
      Toast.error(`节点 ID "${value}" 已存在`);
      setNodeIdDraft(selectedNode.nodeId);
      return;
    }
    handleRenameNode(value);
  };

  const handleMoveNode = (nodeId: string, position: { x: number; y: number }) => {
    updateNode(nodeId, {
      config: {
        ...((definition.nodes || []).find((node) => node.nodeId === nodeId)?.config || {}),
        position,
      },
    });
  };

  const handleAddEdge = () => {
    if (!edgeDraft.from || !edgeDraft.to) {
      Toast.error('请选择边的起点和终点');
      return;
    }
    if (edgeDraft.from === edgeDraft.to) {
      Toast.error('边的起点和终点不能相同');
      return;
    }
    if (edgeDraft.feedback && !isRouterNodeId(definition.nodes, edgeDraft.from)) {
      Toast.error('回边必须从 Router 节点发出');
      return;
    }
    const normalizedEdge = normalizeEdgeDraft(edgeDraft, definition.edges || []);
    updateDefinition((current) => {
      const next = {
        ...current,
        edges: [...(current.edges || []), normalizedEdge],
      };
      return normalizedEdge.feedback ? ensureFeedbackLoopPolicy(next, normalizedEdge) : next;
    });
    setSelectedEdgeIndex((definition.edges || []).length);
    setActivePanel(agentBuilderPanelForMode(builderMode, 'edge'));
  };

  const handleCanvasConnectEdge = (edge: OpsGraphEdge) => {
    if (edge.feedback && !isRouterNodeId(definition.nodes, edge.from)) {
      Toast.error('回边必须从 Router 节点发出');
      return;
    }
    updateDefinition((current) => {
      const normalizedEdge = normalizeEdgeDraft(edge, current.edges || []);
      const exists = (current.edges || []).some(
        (item) => item.from === normalizedEdge.from && item.to === normalizedEdge.to && (item.condition || 'always') === (normalizedEdge.condition || 'always'),
      );
      if (exists) return current;
      const next = {
        ...current,
        edges: [...(current.edges || []), normalizedEdge],
      };
      return normalizedEdge.feedback ? ensureFeedbackLoopPolicy(next, normalizedEdge) : next;
    });
    setSelectedEdgeIndex((definition.edges || []).length);
    setActivePanel(agentBuilderPanelForMode(builderMode, 'edge'));
  };

  const handleCanvasCreateNode = (node: OpsWorkflowNode, edge?: OpsGraphEdge) => {
    const nodeId = ensureUniqueNodeId(definition.nodes || [], node.nodeId);
    const nextNode: OpsWorkflowNode = {
      ...node,
      nodeId,
      agent: node.agent === node.nodeId ? nodeId : node.agent,
      outputKey: node.outputKey === `${node.nodeId}_result` ? `${nodeId}_result` : node.outputKey,
    };
    const nextEdge = edge
      ? {
          ...edge,
          from: edge.from === node.nodeId ? nodeId : edge.from,
          to: edge.to === node.nodeId ? nodeId : edge.to,
          edgeId: edgeIdOf({
            from: edge.from === node.nodeId ? nodeId : edge.from,
            to: edge.to === node.nodeId ? nodeId : edge.to,
          }),
        }
      : undefined;
    updateDefinition((current) => ({
      ...current,
      nodes: [...(current.nodes || []), nextNode],
      edges: nextEdge ? [...(current.edges || []), nextEdge] : current.edges,
    }));
    setSelectedNodeId(nodeId);
    setSelectedEdgeIndex(null);
    setActivePanel('node');
  };

  const handleUpdateEdge = (index: number, patch: Partial<OpsGraphEdge>) => {
    const currentEdge = (definition.edges || [])[index];
    const nextEdgeDraft = currentEdge ? { ...currentEdge, ...patch } : undefined;
    if (nextEdgeDraft?.feedback && !isRouterNodeId(definition.nodes, nextEdgeDraft.from)) {
      Toast.error('回边必须从 Router 节点发出');
      return;
    }
    updateDefinition((current) => {
      const previous = (current.edges || [])[index];
      const nextEdges = (current.edges || []).map((edge, i) => (i === index ? { ...edge, ...patch } : edge));
      const nextEdge = nextEdges[index];
      let next: OpsAgentDefinition = { ...current, edges: nextEdges };
      if (previous && patch.feedback === false) {
        next = removeFeedbackEdgeFromLoops(next, previous);
      }
      if (nextEdge?.feedback) {
        next = ensureFeedbackLoopPolicy(next, nextEdge);
      }
      return next;
    });
  };

  const handleUpdateLoop = (index: number, patch: Partial<OpsLoopPolicy>) => {
    updateDefinition((current) => ({
      ...current,
      loops: (current.loops || []).map((loop, i) => (i === index ? { ...loop, ...patch } : loop)),
    }));
  };

  const handleAddLoop = () => {
    const feedbackEdges = (definition.edges || [])
      .filter((edge) => edge.feedback)
      .map((edge) => edge.edgeId || `${edge.from}->${edge.to}`);
    updateDefinition((current) => ({
      ...current,
      loops: [
        ...(current.loops || []),
        {
          loopId: `loop_${Date.now().toString(36)}`,
          name: '循环策略',
          nodes: current.nodes?.map((node) => node.nodeId) || [],
          feedbackEdges,
          maxRounds: current.defaultMaxMainRounds || 3,
          stopCondition: 'evidence_sufficient || round_limit',
          timeoutSeconds: 240,
          countMode: 'router_choice',
        },
      ],
    }));
  };

  const handleDeleteLoop = (index: number) => {
    updateDefinition((current) => ({
      ...current,
      loops: (current.loops || []).filter((_, i) => i !== index),
    }));
  };

  const findLoopForEdge = (edge: OpsGraphEdge) => {
    const edgeRef = edgeRefOf(edge);
    return (definition.loops || [])
      .map((loop, index) => ({ loop, index }))
      .find(({ loop }) => (loop.feedbackEdges || []).includes(edgeRef));
  };

  const handleUpdateLoopForEdge = (edge: OpsGraphEdge, patch: Partial<OpsLoopPolicy>) => {
    updateDefinition((current) => ensureFeedbackLoopPolicy(current, edge, patch));
  };

  const handleDeleteEdge = (index: number) => {
    updateDefinition((current) => ({
      ...removeFeedbackEdgeFromLoops(current, (current.edges || [])[index] || { from: '', to: '' }),
      edges: (current.edges || []).filter((_, i) => i !== index),
    }));
    setSelectedEdgeIndex(null);
  };

  const handleAutoLayout = () => {
    const nodes = definition.nodes || [];
    const edges = definition.edges || [];
    const incoming = new Map(nodes.map((node) => [node.nodeId, 0]));
    edges.forEach((edge) => incoming.set(edge.to, (incoming.get(edge.to) || 0) + 1));
    const startNodeId = definition.startNodeId || nodes.find((node) => incoming.get(node.nodeId) === 0)?.nodeId;
    const depth = new Map<string, number>();
    const visit = (nodeId: string): number => {
      if (depth.has(nodeId)) return depth.get(nodeId)!;
      const parents = edges.filter((edge) => edge.to === nodeId).map((edge) => edge.from);
      const value = nodeId === startNodeId || !parents.length ? 0 : Math.max(...parents.map(visit)) + 1;
      depth.set(nodeId, value);
      return value;
    };
    nodes.forEach((node) => visit(node.nodeId));
    const grouped = new Map<number, OpsWorkflowNode[]>();
    nodes.forEach((node) => {
      const level = depth.get(node.nodeId) || 0;
      grouped.set(level, [...(grouped.get(level) || []), node]);
    });
    updateDefinition((current) => ({
      ...current,
      nodes: (current.nodes || []).map((node) => {
        const level = depth.get(node.nodeId) || 0;
        const index = (grouped.get(level) || []).findIndex((item) => item.nodeId === node.nodeId);
        return {
          ...node,
          config: {
            ...(node.config || {}),
            position: { x: 60 + level * 270, y: 80 + Math.max(index, 0) * 160 },
          },
        };
      }),
    }));
  };

  const handleApplyJson = () => {
    try {
      const parsed = JSON.parse(jsonDraft) as OpsAgentDefinition;
      if (!parsed.agentId) {
        Toast.error('JSON 缺少 agentId');
        return;
      }
      const normalized = cleanDefinition(normalizeDefinitionForEditor(parsed));
      setDefinition(normalized);
      setSelectedNodeId(normalized.startNodeId || normalized.nodes?.[0]?.nodeId || '');
      setSelectedEdgeIndex(null);
      setJsonDirty(false);
      Toast.success('JSON 已应用到画布');
    } catch (error) {
      Toast.error('JSON 格式不正确');
    }
  };

  const handleTestRun = async () => {
    if (!validateDefinition()) return;
    if (!testQuery.trim()) {
      Toast.error('请输入测试问题');
      return;
    }
    const runtimeProjectId = definition.projectId;
    if (!runtimeProjectId) {
      Toast.error('测试运行前请先创建并选择业务系统');
      return;
    }
    setTesting(true);
    setRuntimeEvents([]);
    setTestResult('');
    try {
      const response = await opsAgentDefinitionService.testRunAgent({
        userId: userInfo?.username || 'admin',
        sessionId: `agent-config-${Date.now()}`,
        query: testQuery,
        mode: 'AGENT',
        engine: inferEngine(definition),
        projectId: runtimeProjectId,
        agentDefinition: cleanDefinition(definition),
      });
      const result = response.data;
      setRuntimeEvents(result?.events || []);
      setTestResult(result?.content || '');
      Toast.success('测试运行完成');
    } catch (error) {
      console.error('测试运行失败:', error);
      if (isUnauthorizedError(error)) {
        handleAuthExpired();
        return;
      }
      Toast.error(`测试运行失败：${errorMessageOf(error)}`);
    } finally {
      setTesting(false);
    }
  };

  const handleEdgeSelect = (edgeKey: string) => {
    const index = Number(edgeKey.substring(edgeKey.lastIndexOf(':') + 1));
    setSelectedEdgeIndex(Number.isFinite(index) ? index : null);
    setActivePanel(agentBuilderPanelForMode(builderMode, 'edge'));
  };

  const renderNodePanel = () => {
    if (!selectedNode) {
      return <EmptyState>请选择一个节点，或先新增节点。</EmptyState>;
    }
    const selectedMcpIds = selectedNode.mcpIds || [];
    const selectedExecutionTargetIds = selectedNode.executionTargetIds || [];
    const selectedType = normalizeNodeType(selectedNode.type);
    const selectedMode = selectedType === 'AGENT' ? normalizeAgentMode(selectedNode.mode || selectedNode.config?.mode) : '';
    const isStructureNode = selectedType === 'START' || selectedType === 'END';
    const isAgentLike = selectedType === 'AGENT';
    const isRouterNode = selectedType === 'ROUTER';
    const isSubWorkflowNode = selectedType === 'SUB_WORKFLOW';
    const selectedRole = String(selectedNode.config?.role || 'general').trim().toLowerCase();
    const outgoingEdges = (definition.edges || []).filter((edge) => edge.from === selectedNode.nodeId);
    const nodeById = new Map((definition.nodes || []).map((node) => [node.nodeId, node]));
    const outgoingRouters = outgoingEdges
      .map((edge) => nodeById.get(edge.to))
      .filter((node): node is OpsWorkflowNode => normalizeNodeType(node?.type) === 'ROUTER');
    const effectiveModel = selectedNode.modelId || definition.modelId || '';
    const effectiveKnowledge = selectedNode.knowledgeBaseId || definition.knowledgeBaseId || '';
    const effectiveRag = selectedNode.ragEnabled === undefined ? Boolean(definition.ragEnabled) : Boolean(selectedNode.ragEnabled);
    const effectiveSkills = uniqueList([...(definition.skills || []), ...(selectedNode.skills || [])]);
    const effectiveMcpIds = selectedNode.mcpIds || [];
    const nameOfModel = (modelId?: string) => modelOptions.find((item) => item.modelId === modelId)?.modelName || modelId || '运行时默认';
    const nameOfMcp = (mcpId: string) => unifiedMcpOptions.find((item) => item.mcpId === mcpId)?.mcpName || mcpId;
    const updateSelectedNodeConfig = (patch: Record<string, unknown>) => {
      updateNode(selectedNode.nodeId, { config: { ...(selectedNode.config || {}), ...patch } });
    };
    const applyDirectActionsDraft = () => {
      try {
        const parsed = JSON.parse(directActionsDraft || '[]');
        if (!Array.isArray(parsed)) {
          Toast.error('DIRECT Actions 必须是 JSON 数组');
          return;
        }
        updateSelectedNodeConfig({ actions: parsed });
        Toast.success('DIRECT Actions 已应用');
      } catch (error) {
        Toast.error('DIRECT Actions JSON 格式不正确');
      }
    };
    const updateSelectedAgentMode = (modeValue: string) => {
      const mode = normalizeAgentMode(modeValue);
      const subEngine = mode === 'react' ? 'AGENTSCOPE' : undefined;
      const role = mode === 'llm' && LLM_ROLE_TEMPLATES.some((item) => item.value === selectedRole)
        ? selectedRole
        : roleForMode(mode);
      updateNode(selectedNode.nodeId, {
        type: 'AGENT',
        mode,
        subEngine,
        config: {
          ...(selectedNode.config || {}),
          mode,
          role,
          reviewMode: undefined,
        },
      });
    };
    const handleMcpLibraryChange = (values: any) => {
      const ids = Array.isArray(values) ? values.map(String) : [];
      updateNode(selectedNode.nodeId, { mcpIds: ids });
    };
    const handleMcpJsonChange = (value: string) => {
      try {
        const parsed = JSON.parse(value || '[]');
        if (!Array.isArray(parsed)) {
          Toast.error('MCP JSON 必须是数组');
          return;
        }
        updateNode(selectedNode.nodeId, { mcpServers: parsed });
      } catch (error) {
        Toast.error('MCP JSON 格式不正确');
      }
    };
    const mcpServers = selectedNode.mcpServers || [];
    const showInlineMcpEditor = showAdvancedNodeMcp || mcpServers.length > 0;
    const updateNodeMcpServer = (index: number, patch: Partial<OpsMcpServerConfig>) => {
      updateNode(selectedNode.nodeId, {
        mcpServers: mcpServers.map((server, i) => (i === index ? { ...server, ...patch } : server)),
      });
    };
    const deleteNodeMcpServer = (index: number) => {
      updateNode(selectedNode.nodeId, {
        mcpServers: mcpServers.filter((_, i) => i !== index),
      });
    };
    const addNodeMcpServer = () => {
      const nextIndex = mcpServers.length + 1;
      updateNode(selectedNode.nodeId, {
        mcpServers: [
          ...mcpServers,
          {
            name: `node-mcp-${nextIndex}`,
            transport: 'stdio',
            timeoutSeconds: 30,
            toolCapabilities: { '*': 'read_only' },
            allowedTools: ['*'],
          },
        ],
      });
    };
    const updateToolCapabilities = (index: number, value: string) => {
      try {
        const parsed = JSON.parse(value || '{}');
        if (!parsed || Array.isArray(parsed) || typeof parsed !== 'object') {
          Toast.error('工具能力必须是 JSON 对象');
          return;
        }
        updateNodeMcpServer(index, { toolCapabilities: parsed as Record<string, string> });
      } catch (error) {
        Toast.error('工具能力 JSON 格式不正确');
      }
    };
    return (
      <FormStack>
        <SectionHeader>
          <SectionTitle>节点身份</SectionTitle>
          <Text type="tertiary" size="small">
            节点 ID 只用于图内部引用；节点类型决定它是结构节点、执行节点还是路由节点。
          </Text>
        </SectionHeader>
        <TwoColumn>
          <Field>
            <Text strong>节点 ID</Text>
            <Input
              value={nodeIdDraft}
              validateStatus={nodeIdDraftError ? 'error' : 'default'}
              onChange={(value) => setNodeIdDraft(value)}
              onBlur={commitNodeIdDraft}
              onEnterPress={commitNodeIdDraft}
            />
            <HelpText type={nodeIdDraftError ? 'danger' : 'tertiary'} size="small">
              {nodeIdDraftError || '节点 ID 是连线、循环策略和运行状态引用的唯一键；修改后会自动同步相关引用。'}
            </HelpText>
          </Field>
          <Field>
            <Text strong>节点类型</Text>
            <Select
              value={selectedType || 'AGENT'}
              onChange={(value) => {
                const type = normalizeNodeType(String(value));
                if (type === 'AGENT') {
                  const mode = normalizeAgentMode(selectedNode.mode || selectedNode.config?.mode);
                  updateNode(selectedNode.nodeId, {
                    type: 'AGENT',
                    mode,
                    agent: selectedNode.agent || selectedNode.nodeId,
                    config: {
                      ...(selectedNode.config || {}),
                      mode,
                      role: selectedNode.config?.role || roleForMode(mode),
                    },
                  });
                  return;
                }
                if (type === 'ROUTER') {
                  updateNode(selectedNode.nodeId, {
                    type: 'ROUTER',
                    mode: undefined,
                    agent: selectedNode.agent || selectedNode.nodeId,
                    outputKey: selectedNode.outputKey || 'selectedRoutes',
                    config: {
                      ...(selectedNode.config || {}),
                      mode: undefined,
                      routeMode: selectedNode.config?.routeMode || 'multi',
                      inputKey: selectedNode.config?.inputKey || 'plan',
                    },
                  });
                  return;
                }
                updateNode(selectedNode.nodeId, { type, mode: undefined, config: { ...(selectedNode.config || {}), mode: undefined } });
              }}
            >
              {DEFAULT_NODE_TYPES.map((type) => (
                <Option key={type} value={type}>{type}</Option>
              ))}
            </Select>
          </Field>
        </TwoColumn>
        {isStructureNode && (
          <>
            <HelpText type="tertiary" size="small">
              Start/End 是结构节点，只负责运行入口和结束输出；不绑定模型、Skill、MCP 或数据源。输入/输出契约都可以留空。
            </HelpText>
            <Field>
              <Text strong>{selectedType === 'START' ? '入口字段' : '结束输出字段'}</Text>
              <Input
                value={listToText((selectedNode.config?.[selectedType === 'START' ? 'inputKeys' : 'outputKeys'] || []) as string[])}
                placeholder={selectedType === 'START' ? '可留空；例如 query, alertId, service' : '可留空；例如 conclusion, changePackageId'}
                onChange={(value) =>
                  updateSelectedNodeConfig({
                    [selectedType === 'START' ? 'inputKeys' : 'outputKeys']: textToList(value),
                  })
                }
              />
              <HelpText type="tertiary" size="small">
                {selectedType === 'START' ? '留空表示没有显式输入契约；定时巡检可直接由后续 Agent Prompt 与运行时上下文开始。' : '留空表示直接采用最终 Graph 输出；只有需要稳定对外字段时才配置。'}
              </HelpText>
            </Field>
          </>
        )}
        {isAgentLike && (
          <>
            <Section>
              <SectionHeader>
                <SectionTitle>执行语义</SectionTitle>
                <Text type="tertiary" size="small">{modeExplanation(selectedMode)}</Text>
              </SectionHeader>
            </Section>
            <TwoColumn>
              <Field>
                <Text strong>运行标识</Text>
                <Input value={selectedNode.agent || ''} onChange={(value) => updateNode(selectedNode.nodeId, { agent: value })} />
                <HelpText type="tertiary" size="small">
                  用于运行事件、日志和消息展示；通常与节点职责一致即可。
                </HelpText>
              </Field>
              <Field>
                <Text strong>Agent 模式</Text>
                <Select value={selectedMode || 'llm'} onChange={(value) => updateSelectedAgentMode(String(value))}>
                  {AGENT_MODES.map((mode) => (
                    <Option key={mode.value} value={mode.value}>{mode.label}</Option>
                  ))}
                </Select>
                <HelpText type="tertiary" size="small">
                  DIRECT 固定动作；LLM 单次调用；REACT 节点内工具循环。Review 不再是执行模式，而是 LLM 节点职责。
                </HelpText>
              </Field>
            </TwoColumn>
            {selectedMode === 'llm' && (
              <Field>
                <Text strong>LLM 职责模板</Text>
                <Select value={selectedRole || 'general'} onChange={(value) => updateSelectedNodeConfig({ role: String(value) })}>
                  {LLM_ROLE_TEMPLATES.map((role) => (
                    <Option key={role.value} value={role.value}>{role.label}</Option>
                  ))}
                </Select>
                <HelpText type="tertiary" size="small">
                  这里只表达职责与画布语义；真正的 Review / Report / Extract / Classify 规则仍写在当前节点 Prompt 和输出契约中。
                </HelpText>
              </Field>
            )}
            <Field>
              <Text strong>Agent Authority</Text>
              <Select
                value={String(selectedNode.config?.authority || ((selectedNode.repairEnabled || selectedNode.changePackageEnabled) ? 'PREPARE_CHANGE' : 'OBSERVE_ONLY'))}
                onChange={(value) => updateSelectedNodeConfig({ authority: String(value) })}
              >
                <Option value="OBSERVE_ONLY">OBSERVE_ONLY · 只读观察与分析</Option>
                <Option value="PREPARE_CHANGE">PREPARE_CHANGE · 可准备修复与 ChangePackage</Option>
              </Select>
              <HelpText type="tertiary" size="small">
                PREPARE_CHANGE 可修改隔离 Worktree、Test/Build，并把受治理 PROD mutation 记录为 ProposedAction；真实生产执行只能由审批后的独立 Landing Runtime 完成。
              </HelpText>
            </Field>
            {outgoingRouters.length > 0 && (
              <NoticeBox>
                下游连接到 Router：运行时会把 Router 的可选分支、条件值和要求输出格式注入当前节点 Prompt。当前节点需要在输出中明确下一步选择，Router 再按连线条件进入一个或多个下游节点。
              </NoticeBox>
            )}
          </>
        )}
        {isAgentLike && selectedMode === 'direct' && (
          <Field>
            <Text strong>固定 Actions</Text>
            <TextArea
              autosize={{ minRows: 6, maxRows: 14 }}
              value={directActionsDraft}
              onChange={setDirectActionsDraft}
              placeholder={'[{"toolName":"prometheus_query","arguments":{"query":"up"},"outputKey":"prometheus"}]'}
            />
            <Button size="small" onClick={applyDirectActionsDraft}>应用 Actions</Button>
            <HelpText type="tertiary" size="small">
              按数组顺序执行；每个 Action 通过当前节点已授权并受 Runtime Authority 与审计保护的 ToolCallback 调用。DIRECT 不调用模型，也不会绕过生产变更边界。
            </HelpText>
          </Field>
        )}
        {isRouterNode && (
          <>
            <TwoColumn>
              <Field>
                <Text strong>路由模式</Text>
                <Select value={String(selectedNode.config?.routeMode || 'multi')} onChange={(value) => updateSelectedNodeConfig({ routeMode: String(value) })}>
                  <Option value="single">single</Option>
                  <Option value="multi">multi</Option>
                </Select>
                <HelpText type="tertiary" size="small">
                  single 只选择一个下游 Agent；multi 可同时选择多个下游 Agent 并行取证。
                </HelpText>
              </Field>
              <Field>
                <Text strong>路由输入字段</Text>
                <Input value={String(selectedNode.config?.inputKey || '')} placeholder="plan / selectedRoutes / review_decision / 自定义 outputKey" onChange={(value) => updateSelectedNodeConfig({ inputKey: value })} />
                <HelpText type="tertiary" size="small">
                  Router 读取这个字段，并用 route_match、review_decision、expression 等条件边筛选下游 Agent。
                </HelpText>
              </Field>
            </TwoColumn>
            <Field>
              <Text strong>路由输出字段</Text>
              <Input value={selectedNode.outputKey || ''} placeholder="selectedRoutes" onChange={(value) => updateNode(selectedNode.nodeId, { outputKey: value })} />
              <HelpText type="tertiary" size="small">
                保存 Router 选中的分支名称；下游边的条件值应对应这里输出的 route key。
              </HelpText>
            </Field>
          </>
        )}
        {isSubWorkflowNode && (
          <>
            <Field>
              <Text strong>目标专项 Workflow</Text>
              <Select
                value={selectedNode.agent || ''}
                filter
                style={{ width: '100%' }}
                onChange={(value) => updateNode(selectedNode.nodeId, { agent: String(value || '') })}
              >
                <Option value="">请选择已发布 Workflow</Option>
                {agents.filter((item) => item.agentId !== definition.agentId).map((item) => (
                  <Option key={item.agentId} value={item.agentId}>{item.name || item.agentId}</Option>
                ))}
              </Select>
              <HelpText type="tertiary" size="small">只复用当前项目内的专项 Workflow；运行时会解析已发布版本并创建独立 durable child Run。</HelpText>
            </Field>
            <TwoColumn>
              <Field>
                <Text strong>版本策略</Text>
                <Select value={String(selectedNode.config?.versionPolicy || 'LATEST_PUBLISHED')} onChange={(value) => updateSelectedNodeConfig({ versionPolicy: String(value) })}>
                  <Option value="LATEST_PUBLISHED">最新已发布版本</Option>
                  <Option value="PINNED_VERSION">固定版本</Option>
                </Select>
              </Field>
              <Field>
                <Text strong>固定版本</Text>
                <Input
                  disabled={String(selectedNode.config?.versionPolicy || 'LATEST_PUBLISHED') !== 'PINNED_VERSION'}
                  value={String(selectedNode.config?.version || '')}
                  onChange={(value) => updateSelectedNodeConfig({ version: Number(value) > 0 ? Number(value) : undefined })}
                />
              </Field>
            </TwoColumn>
            <Field>
              <Text strong>输入字段</Text>
              <Input value={String(selectedNode.config?.inputKey || '')} placeholder="留空使用父请求 query" onChange={(value) => updateSelectedNodeConfig({ inputKey: value })} />
              <HelpText type="tertiary" size="small">填写 Graph State key 时，child Workflow 使用该字段作为输入；留空则继承父请求。</HelpText>
            </Field>
          </>
        )}
        {isAgentLike && advancedBuilder && (
          <AdvancedDetails>
            <summary>高级运行配置</summary>
            <AdvancedContent>
              <TwoColumn>
                <Field>
                  <Text strong>输出键</Text>
                  <Input value={selectedNode.outputKey || ''} onChange={(value) => updateNode(selectedNode.nodeId, { outputKey: value })} />
                  <HelpText type="tertiary" size="small">
                    节点输出写入 Graph State 的字段名；下游需要显式读取时才配置。
                  </HelpText>
                </Field>
                <Field>
                  <Text strong>上下文输入</Text>
                  <Input
                    value={listToText((selectedNode.config?.contextInputs || []) as string[])}
                    placeholder="query, state.plan, upstreamOutputs"
                    onChange={(value) => updateSelectedNodeConfig({ contextInputs: textToList(value) })}
                  />
                  <HelpText type="tertiary" size="small">
                    限定该节点能看到哪些 Graph State 字段；留空时使用运行时默认上下文。
                  </HelpText>
                </Field>
              </TwoColumn>
              <Field>
                <Text strong>模型</Text>
                <Select
                  value={selectedNode.modelId || ''}
                  filter
                  style={{ width: '100%' }}
                  onChange={(value) => updateNode(selectedNode.nodeId, { modelId: String(value || '') || undefined })}
                >
                  <Option value="">继承 Agent 默认/运行时默认</Option>
                  {modelOptions.map((item) => (
                    <Option key={item.modelId} value={item.modelId}>
                      {item.modelName || item.modelId}
                    </Option>
                  ))}
                </Select>
              </Field>
            </AdvancedContent>
          </AdvancedDetails>
        )}
        <Section>
          <SectionHeader>
            <SectionTitle>节点说明与 Prompt</SectionTitle>
            <Text type="tertiary" size="small">说明用于画布识别，System Prompt 才会约束当前模型节点。</Text>
          </SectionHeader>
        </Section>
        <Field>
          <Text strong>说明</Text>
          <Input value={selectedNode.description || ''} onChange={(value) => updateNode(selectedNode.nodeId, { description: value })} />
          <HelpText type="tertiary" size="small">
            当上游连接到 Router 时，这段说明会作为 Router choice 的后续节点描述注入给上游 Agent。
          </HelpText>
        </Field>
        {!isStructureNode && advancedBuilder && (
          <Field>
            <Text strong>全局 System Prompt 注入</Text>
            <Select
              value={String(selectedNode.config?.globalPromptMode || 'inherit')}
              onChange={(value) => updateSelectedNodeConfig({ globalPromptMode: String(value) })}
            >
              <Option value="inherit">继承全局并追加节点约束</Option>
              <Option value="node_only">仅使用节点 System Prompt</Option>
            </Select>
            <HelpText type="tertiary" size="small">
              分支取证节点可选择仅使用节点 Prompt；需要统一安全边界、输出规范或角色约束时保留继承全局。
            </HelpText>
          </Field>
        )}
        {!isStructureNode && (
          <Field>
            <Text strong>节点 System Prompt</Text>
            <TextArea
              autosize={{ minRows: 5, maxRows: 10 }}
              value={selectedNode.instruction || ''}
              onChange={(value) => updateNode(selectedNode.nodeId, { instruction: value })}
            />
            <HelpText type="tertiary" size="small">
              只约束当前节点；是否与全局 System Prompt 组合由上面的注入方式决定。
            </HelpText>
          </Field>
        )}
        {isAgentLike && (
          <Section>
            <SectionHeader>
              <SectionTitle>节点能力资源</SectionTitle>
              <Text type="tertiary" size="small">这里决定当前节点运行时能看到什么。RAG 是知识检索，Skill 是流程经验，MCP 是可调用工具。</Text>
            </SectionHeader>
          </Section>
        )}
        {isAgentLike && (
          <Field>
            <Checkbox
              checked={Boolean(selectedNode.repairEnabled)}
              onChange={(event) => updateNode(selectedNode.nodeId, { repairEnabled: Boolean(event.target.checked) })}
            >
              启用代码修复候选验证
            </Checkbox>
            <HelpText type="tertiary" size="small">
              仅向当前节点暴露隔离 worktree 与受控构建/测试工具；默认关闭，开启后也只能生成并验证候选补丁，不能直接部署。
            </HelpText>
          </Field>
        )}
        {isAgentLike && (
          <Field>
            <Checkbox
              checked={Boolean(selectedNode.changePackageEnabled)}
              onChange={(event) => updateNode(selectedNode.nodeId, {
                changePackageEnabled: Boolean(event.target.checked),
              })}
            >
              允许生成 ChangePackage
            </Checkbox>
            <HelpText type="tertiary" size="small">
              仅在本次运行已有成功的 MCP、RAG 或工具证据时，允许当前节点创建受控执行包；不会自动执行、审批或绕过 Tool Policy / ChangePackage 安全边界。
            </HelpText>
          </Field>
        )}
        {isAgentLike && (
          <TwoColumn>
            <Field>
              <Text strong>RAG 知识库范围</Text>
              <Select
                value={selectedNode.knowledgeBaseId || ''}
                filter
                style={{ width: '100%' }}
                onChange={(value) => updateNode(selectedNode.nodeId, { knowledgeBaseId: String(value || '') })}
              >
                <Option value="">继承当前项目授权知识库</Option>
                {authorizedKnowledgeBases.map((item) => (
                                      <Option key={item.kbId || item.knowledgeTag} value={item.kbId || item.knowledgeTag}>
                                        {item.kbName || item.name || item.knowledgeTag}
                  </Option>
                ))}
              </Select>
              <HelpText type="tertiary" size="small">
                作为该 Agent 的知识库范围；启用 RAG 后由模型在当前节点内按需使用。
              </HelpText>
            </Field>
            <Field>
              <Text strong>RAG 开关</Text>
              <Select
                value={ragModeOf(selectedNode.ragEnabled)}
                onChange={(value) => updateNode(selectedNode.nodeId, { ragEnabled: ragEnabledOf(String(value)) })}
              >
                <Option value="inherit">继承全局</Option>
                <Option value="enabled">启用</Option>
                <Option value="disabled">禁用</Option>
              </Select>
              <HelpText type="tertiary" size="small">
                节点级 RAG 优先于全局开关；选择继承时按 Agent 级默认值执行。
              </HelpText>
            </Field>
          </TwoColumn>
        )}
        {isAgentLike && (
          <Field>
            <Text strong>Skill</Text>
            <Select
              multiple
              filter
              value={selectedNode.skills || []}
              placeholder="从 Skill 管理中选择当前节点需要的能力说明"
              style={{ width: '100%' }}
              onChange={(values) => updateNode(selectedNode.nodeId, { skills: Array.isArray(values) ? values.map(String) : [] })}
            >
              {renderSkillOptionGroups()}
            </Select>
            <HelpText type="tertiary" size="small">
              Skill 是流程经验和边界说明；运行时按节点选择加载摘要或正文，不等同于知识库事实检索。
            </HelpText>
          </Field>
        )}
        {isAgentLike && (
        <Field>
          <Text strong>MCP 工具集</Text>
          <Select
            multiple
            filter
            value={selectedMcpIds}
            placeholder="必须显式选择当前项目可用工具"
            style={{ width: '100%' }}
            onChange={handleMcpLibraryChange}
          >
            {renderMcpOptionGroups()}
          </Select>
          <HelpText type="tertiary" size="small">
            MCP 模板不是项目工具；Agent 只能绑定当前项目可用工具。需要访问 MySQL、Redis、ELK、Prometheus 或其他系统时，请先在项目空间生成或启用工具。
          </HelpText>
        </Field>
        )}
        {isAgentLike && (
          <Field>
            <Text strong>变更执行目标</Text>
            <Select
              multiple
              filter
              value={selectedExecutionTargetIds}
              placeholder="选择当前节点允许生成提案的执行目标"
              style={{ width: '100%' }}
              onChange={(values) => updateNode(selectedNode.nodeId, {
                executionTargetIds: Array.isArray(values) ? values.map(String) : [],
              })}
            >
              <Option disabled value="__execution_target_group">当前项目执行目标</Option>
              {executionTargetOptions.map((item) => (
                <Option key={item.executionTargetId} value={item.executionTargetId}>
                  {item.name}{item.adapterType ? ` / ${item.adapterType}` : ''}
                </Option>
              ))}
              {!executionTargetOptions.length && (
                <Option disabled value="__execution_target_empty">项目尚未配置执行目标</Option>
              )}
            </Select>
            <HelpText type="tertiary" size="small">
              只有显式绑定的项目执行目标会出现在变更提案工具目录中；未绑定时当前节点不能创建可执行变更提案。
            </HelpText>
          </Field>
        )}
        {isAgentLike && advancedBuilder && (
          <>
          <Space>
            <Button size="small" theme="light" onClick={() => setShowAdvancedNodeMcp((visible) => !visible)}>
              {showInlineMcpEditor ? '隐藏节点内 MCP 高级配置' : '显示节点内 MCP 高级配置'}
            </Button>
            <HelpText type="tertiary" size="small">
              默认只从当前项目可用工具中选择；节点内 MCP 是临时覆盖，不建议常规使用。
            </HelpText>
          </Space>
          {showInlineMcpEditor && (
          <>
          <Field>
            <Space style={{ width: '100%', justifyContent: 'space-between' }}>
              <Text strong>节点内 MCP（高级）</Text>
              <Button size="small" icon={<IconPlus />} onClick={addNodeMcpServer}>
                新增 MCP
              </Button>
            </Space>
            <FormStack>
              {mcpServers.map((server, index) => (
                <EdgeItem key={`${server.name || 'mcp'}-${index}`}>
                <FormStack>
                  <TwoColumn>
                    <Field>
                      <Text strong>名称</Text>
                      <Input value={server.name || ''} onChange={(value) => updateNodeMcpServer(index, { name: value })} />
                    </Field>
                    <Field>
                      <Text strong>传输</Text>
                      <Select value={server.transport || 'stdio'} onChange={(value) => updateNodeMcpServer(index, { transport: String(value) })}>
                        <Option value="stdio">stdio</Option>
                        <Option value="sse">sse</Option>
                        <Option value="streamable-http">streamable-http</Option>
                      </Select>
                    </Field>
                  </TwoColumn>
                  <Field>
                    <Text strong>说明</Text>
                    <Input value={server.description || ''} onChange={(value) => updateNodeMcpServer(index, { description: value })} />
                  </Field>
                  <TwoColumn>
                    <Field>
                      <Text strong>Command</Text>
                      <Input value={server.command || ''} onChange={(value) => updateNodeMcpServer(index, { command: value })} />
                    </Field>
                    <Field>
                      <Text strong>URL</Text>
                      <Input value={server.url || ''} onChange={(value) => updateNodeMcpServer(index, { url: value })} />
                    </Field>
                  </TwoColumn>
                  <TwoColumn>
                    <Field>
                      <Text strong>超时秒</Text>
                      <Input value={String(server.timeoutSeconds || 30)} onChange={(value) => updateNodeMcpServer(index, { timeoutSeconds: Number(value) || 30 })} />
                    </Field>
                    <Field>
                      <Text strong>Args</Text>
                      <Input value={listToText(server.args)} onChange={(value) => updateNodeMcpServer(index, { args: textToList(value) })} />
                    </Field>
                  </TwoColumn>
                  <TwoColumn>
                    <Field>
                      <Text strong>只读工具</Text>
                      <Input value={listToText(server.allowedTools)} onChange={(value) => updateNodeMcpServer(index, { allowedTools: textToList(value) })} />
                    </Field>
                    <Field>
                      <Text strong>通知工具</Text>
                      <Input value={listToText(server.notificationTools)} onChange={(value) => updateNodeMcpServer(index, { notificationTools: textToList(value) })} />
                    </Field>
                  </TwoColumn>
                  <Field>
                    <Text strong>禁用工具</Text>
                    <Input value={listToText(server.blockedTools)} onChange={(value) => updateNodeMcpServer(index, { blockedTools: textToList(value) })} />
                  </Field>
                  <Field>
                    <Text strong>工具能力</Text>
                    <TextArea
                      key={`${selectedNode.nodeId}-${server.name || index}-tool-capabilities`}
                      autosize={{ minRows: 3, maxRows: 6 }}
                      defaultValue={JSON.stringify(server.toolCapabilities || {}, null, 2)}
                      onBlur={(event) => updateToolCapabilities(index, event.target.value)}
                    />
                  </Field>
                  <Button size="small" type="danger" icon={<IconDelete />} onClick={() => deleteNodeMcpServer(index)}>
                    移除节点 MCP 定义
                  </Button>
                </FormStack>
                </EdgeItem>
              ))}
              {!mcpServers.length && <Text type="tertiary">暂无节点内 MCP。</Text>}
            </FormStack>
            <HelpText type="tertiary" size="small">
              只属于当前节点的临时 MCP Server 定义；默认优先使用 MCP 工具集，节点内 MCP 只用于临时或高级场景。
            </HelpText>
          </Field>
          <Field>
            <Text strong>MCP JSON（高级）</Text>
            <TextArea
              key={`${selectedNode.nodeId}-mcp-json`}
              autosize={{ minRows: 4, maxRows: 10 }}
              defaultValue={JSON.stringify(mcpServers, null, 2)}
              onBlur={(event) => handleMcpJsonChange(event.target.value)}
            />
            <HelpText type="tertiary" size="small">
              节点内 MCP 的高级 JSON 编辑视图，和上面的表单编辑的是同一份 mcpServers 数据。
            </HelpText>
          </Field>
          </>
          )}
          </>
        )}
        {isAgentLike && (
          <Section>
            <FormStack>
              <Text strong>生效资源预览</Text>
              <MetaRow>
                <Tag color={selectedNode.subEngine === 'AGENTSCOPE' ? 'purple' : 'blue'}>
                  {selectedNode.subEngine === 'AGENTSCOPE' ? 'ReAct 工具节点' : '模型节点'}
                </Tag>
                <Tag color="grey">Model: {nameOfModel(effectiveModel)}</Tag>
                <Tag color={effectiveRag ? 'green' : 'grey'}>RAG: {effectiveRag ? '启用' : '禁用'}</Tag>
                <Tag color={effectiveKnowledge ? 'green' : 'grey'}>知识库: {effectiveKnowledge || '全部'}</Tag>
              </MetaRow>
              <MetaRow>
                <Tag color={effectiveSkills.length ? 'blue' : 'grey'}>
                  Skill: {effectiveSkills.length ? effectiveSkills.join(', ') : '未选择'}
                </Tag>
                <Tag color={effectiveMcpIds.length ? 'purple' : 'grey'}>
                  MCP: {effectiveMcpIds.length ? effectiveMcpIds.map(nameOfMcp).join(', ') : '未选择'}
                </Tag>
                {mcpServers.length > 0 && <Tag color="orange">节点内 MCP: {mcpServers.length}</Tag>}
              </MetaRow>
              <HelpText type="tertiary" size="small">
                这里展示当前节点运行时可见的主要资源。Graph 节点不会自动继承全局 MCP；需要用工具时请在节点 MCP 工具集中显式选择。
              </HelpText>
            </FormStack>
          </Section>
        )}
        {isRouterNode && (
          <Section>
            <FormStack>
              <Text strong>路由候选分支</Text>
              <HelpText type="tertiary" size="small">
                Router 根据上游节点输出选择这些出边；multi 模式可同时命中多个候选分支，default 只在无命中时进入。
              </HelpText>
              <FormStack>
                {outgoingEdges.map((edge, index) => (
                  <EdgeItem key={`${edge.from}-${edge.to}-${index}`}>
                    <MetaRow>
                      <Tag color="blue">{edge.to}</Tag>
                      <Tag color={edge.defaultEdge || edge.conditionType === 'default' ? 'grey' : edge.feedback ? 'purple' : 'green'}>
                        {edge.conditionType || 'always'}: {edge.condition || 'always'}
                      </Tag>
                      {edge.feedback && <Tag color="purple">回边</Tag>}
                    </MetaRow>
                    {edge.description && <Text type="tertiary" size="small">{edge.description}</Text>}
                  </EdgeItem>
                ))}
                {!outgoingEdges.length && <Text type="tertiary">暂无出边，请在“连线”面板添加路由候选。</Text>}
              </FormStack>
            </FormStack>
          </Section>
        )}
        <Space>
          <Button type="primary" icon={<IconBranch />} onClick={() => updateDefinition((current) => ({ ...current, startNodeId: selectedNode.nodeId }))}>
            设为起始节点
          </Button>
          <Popconfirm title="删除节点会同时删除相关连线，确认删除？" onConfirm={handleDeleteNode}>
            <Button type="danger" icon={<IconDelete />}>
              删除节点
            </Button>
          </Popconfirm>
        </Space>
      </FormStack>
    );
  };

  const renderEdgePanel = () => (
    <FormStack>
      <SectionHeader>
        <SectionTitle>连接规则</SectionTitle>
        <Text type="tertiary" size="small">
          普通连线表达执行顺序；条件分支统一由 Router 发出；回边只能是 Router 出边，用于绑定循环次数。
        </Text>
      </SectionHeader>
      <NoticeBox $tone="neutral">
        常用做法：任意 Agent 后面都可以接 Router；Router 出边用 route_match 或 review_decision 表达候选分支，用 default 表达兜底出口。需要回到前序节点时，在对应 Router 出边上勾选回边并设置循环次数；回边说明会随 Router choice 一起进入上游 Prompt。
      </NoticeBox>
      <TwoColumn>
        <Field>
          <Text strong>起点</Text>
          <Select value={edgeDraft.from} onChange={(value) => setEdgeDraft((current) => ({ ...current, from: String(value) }))}>
            {(definition.nodes || []).map((node) => (
              <Option key={node.nodeId} value={node.nodeId}>
                {node.nodeId}
              </Option>
            ))}
          </Select>
        </Field>
        <Field>
          <Text strong>终点</Text>
          <Select value={edgeDraft.to} onChange={(value) => setEdgeDraft((current) => ({ ...current, to: String(value) }))}>
            {(definition.nodes || []).map((node) => (
              <Option key={node.nodeId} value={node.nodeId}>
                {node.nodeId}
              </Option>
            ))}
          </Select>
        </Field>
      </TwoColumn>
      <TwoColumn>
        <Field>
          <Text strong>条件类型</Text>
          <Select value={edgeDraft.conditionType || 'always'} onChange={(value) => setEdgeDraft((current) => ({ ...current, conditionType: String(value) }))}>
            {CONDITION_TYPES.map((item) => (
              <Option key={item.value} value={item.value}>{item.label}</Option>
            ))}
          </Select>
        </Field>
        <Field>
          <Text strong>条件值</Text>
          <Input
            value={edgeDraft.condition || ''}
            placeholder="always / elasticsearch / evidence_sufficient"
            onChange={(value) => setEdgeDraft((current) => ({ ...current, condition: value }))}
          />
        </Field>
      </TwoColumn>
      <Checkbox
        checked={Boolean(edgeDraft.feedback)}
        onChange={(event) => setEdgeDraft((current) => {
          const checked = Boolean(event.target.checked);
          return {
            ...current,
            feedback: checked,
            conditionType: checked && (!current.conditionType || current.conditionType === 'always') ? 'route_match' : current.conditionType,
          };
        })}
      >
        回边 / Loop Policy 控制
      </Checkbox>
      {edgeDraft.feedback && (
        <HelpText type="tertiary" size="small">
          仅 Router 出边可设为回边。命中后会自动创建或更新 Loop Policy，默认循环次数为 {boundedLoopRounds(definition.defaultMaxMainRounds || 3)}，可在连线或 Loop Policy 中调整。
        </HelpText>
      )}
      <Field>
        <Text strong>说明</Text>
        <Input value={edgeDraft.description || ''} onChange={(value) => setEdgeDraft((current) => ({ ...current, description: value }))} />
      </Field>
      <Button type="primary" icon={<IconPlus />} onClick={handleAddEdge}>
        新增连线
      </Button>
      <Section>
        <SectionHeader>
          <SectionTitle>已配置连接</SectionTitle>
          <Text type="tertiary" size="small">点击画布连线也会定位到这里；条件值应与 Router 上游输出的 route key 对齐。</Text>
        </SectionHeader>
        <Space vertical align="start" style={{ width: '100%' }}>
          {(definition.edges || []).map((edge, index) => (
            <EdgeItem key={edgeKeyOf(edge, index)} $active={selectedEdgeIndex === index}>
              <Space vertical align="start" style={{ width: '100%' }}>
                <MetaRow>
                  <Tag color="blue">{edge.from}</Tag>
                  <Text type="tertiary">-&gt;</Text>
                  <Tag color="green">{edge.to}</Tag>
                  {edge.feedback && <Tag color="purple">回边</Tag>}
                </MetaRow>
                <TwoColumn>
                  <Select value={edge.conditionType || 'always'} onChange={(value) => handleUpdateEdge(index, { conditionType: String(value) })}>
                    {CONDITION_TYPES.map((item) => (
                      <Option key={item.value} value={item.value}>{item.label}</Option>
                    ))}
                  </Select>
                  <Input value={edge.condition || ''} onChange={(value) => handleUpdateEdge(index, { condition: value })} />
                </TwoColumn>
                <Checkbox
                  checked={Boolean(edge.feedback)}
                  onChange={(event) => {
                    const checked = Boolean(event.target.checked);
                    handleUpdateEdge(index, {
                      feedback: checked,
                      conditionType: checked && (!edge.conditionType || edge.conditionType === 'always') ? 'route_match' : edge.conditionType,
                    });
                  }}
                >
                  回边
                </Checkbox>
                {edge.feedback && (() => {
                  const loopEntry = findLoopForEdge(edge);
                  const loop = loopEntry?.loop;
                  return (
                    <TwoColumn>
                      <Field>
                        <Text strong>循环次数</Text>
                        <Input
                          value={String(loop?.maxRounds || definition.defaultMaxMainRounds || 3)}
                          onChange={(value) => handleUpdateLoopForEdge(edge, { maxRounds: boundedLoopRounds(Number(value), loop?.maxRounds || definition.defaultMaxMainRounds || 3) })}
                        />
                        <HelpText type="tertiary" size="small">
                          Router 命中这条回边后，主循环最多执行的轮数。
                        </HelpText>
                      </Field>
                      <Field>
                        <Text strong>所属 Loop</Text>
                        <Input value={loop?.loopId || '自动创建中'} disabled />
                      </Field>
                    </TwoColumn>
                  );
                })()}
                <Input value={edge.description || ''} placeholder="连线说明" onChange={(value) => handleUpdateEdge(index, { description: value })} />
                <Space>
                  <Button size="small" onClick={() => setSelectedEdgeIndex(index)}>
                    选中
                  </Button>
                  <Button size="small" type="danger" icon={<IconDelete />} onClick={() => handleDeleteEdge(index)}>
                    删除
                  </Button>
                </Space>
              </Space>
            </EdgeItem>
          ))}
          {!(definition.edges || []).length && <Text type="tertiary">暂无连线。</Text>}
        </Space>
      </Section>
      {selectedEdge && (
        <Text type="tertiary">
          当前选中：{selectedEdge.from} -&gt; {selectedEdge.to}
        </Text>
      )}
      <Section>
        <SectionHeader>
          <SectionTitle>循环策略</SectionTitle>
          <Text type="tertiary" size="small">正常只需要在回边上调整循环次数；这里是自动生成的 Loop Policy 明细。</Text>
        </SectionHeader>
        <FormStack>
          {(definition.loops || []).map((loop, index) => (
            <EdgeItem key={`${loop.loopId}-${index}`}>
              <FormStack>
                <TwoColumn>
                  <Field>
                    <Text strong>Loop ID</Text>
                    <Input value={loop.loopId || ''} onChange={(value) => handleUpdateLoop(index, { loopId: value })} />
                  </Field>
                  <Field>
                    <Text strong>名称</Text>
                    <Input value={loop.name || ''} onChange={(value) => handleUpdateLoop(index, { name: value })} />
                  </Field>
                </TwoColumn>
                <TwoColumn>
                  <Field>
                    <Text strong>最大轮数</Text>
                    <Input value={String(loop.maxRounds || 3)} onChange={(value) => handleUpdateLoop(index, { maxRounds: Number(value) || 3 })} />
                  </Field>
                  <Field>
                    <Text strong>超时秒</Text>
                    <Input value={String(loop.timeoutSeconds || 240)} onChange={(value) => handleUpdateLoop(index, { timeoutSeconds: Number(value) || 240 })} />
                  </Field>
                </TwoColumn>
                <Field>
                  <Text strong>停止条件</Text>
                  <Input value={loop.stopCondition || ''} onChange={(value) => handleUpdateLoop(index, { stopCondition: value })} />
                </Field>
                <Field>
                  <Text strong>回边</Text>
                  <Input value={listToText(loop.feedbackEdges)} onChange={(value) => handleUpdateLoop(index, { feedbackEdges: textToList(value) })} />
                </Field>
                <Button size="small" type="danger" icon={<IconDelete />} onClick={() => handleDeleteLoop(index)}>
                  删除循环策略
                </Button>
              </FormStack>
            </EdgeItem>
          ))}
          {!(definition.loops || []).length && <Text type="tertiary">暂无循环策略。添加回边后建议配置 Loop Policy。</Text>}
        </FormStack>
        <AdvancedDetails>
          <summary>高级：手动新增 Loop Policy</summary>
          <AdvancedContent>
            <Button size="small" icon={<IconPlus />} onClick={handleAddLoop}>
              新增循环策略
            </Button>
            <HelpText type="tertiary" size="small">
              仅用于复杂图的高级配置。普通场景在某条连线上勾选“回边”即可自动创建。
            </HelpText>
          </AdvancedContent>
        </AdvancedDetails>
      </Section>
    </FormStack>
  );

  const renderJsonPanel = () => (
    <FormStack>
      <Text type="tertiary">高级调试入口：这里编辑的是后端运行时直接消费的 OpsAgentDefinition，包含 engine/subEngine 等内部字段，普通配置建议优先使用画布和节点面板。</Text>
      <TextArea
        autosize={{ minRows: 18, maxRows: 30 }}
        value={jsonDraft}
        onChange={(value) => {
          setJsonDraft(value);
          setJsonDirty(true);
        }}
      />
      <Space>
        <Button type="primary" icon={<IconCode />} onClick={handleApplyJson}>
          应用 JSON
        </Button>
        <Button
          onClick={() => {
            setJsonDraft(definitionJson);
            setJsonDirty(false);
          }}
        >
          同步当前定义
        </Button>
      </Space>
    </FormStack>
  );

  const renderTestPanel = () => (
    <FormStack>
      <Field>
        <Text strong>测试问题</Text>
        <TextArea autosize={{ minRows: 4, maxRows: 8 }} value={testQuery} onChange={setTestQuery} />
      </Field>
      <Button type="primary" icon={<IconPlay />} loading={testing} onClick={handleTestRun}>
        运行当前 Workflow
      </Button>
      <Field>
        <Text strong>输出</Text>
        <RuntimeBox>{testResult || '运行后展示 Agent 最终输出。'}</RuntimeBox>
      </Field>
      <Field>
        <Text strong>事件</Text>
        <RuntimeBox>
          {runtimeEvents.length
            ? runtimeEvents.map(formatRuntimeEvent).join('\n\n')
            : '运行后展示节点事件。'}
        </RuntimeBox>
      </Field>
    </FormStack>
  );

  if (!userInfo) {
    return null;
  }

  return (
    <PageLayout>
      <Sidebar selectedKey="agent-list" onSelect={handleNavigation} collapsed={sidebarCollapsed} onMobileClose={closeMobileSidebar} />
      <MainContent $collapsed={collapsed}>
        <div style={{ display: 'flex', flexDirection: 'column', width: '100%' }}>
          <Header onToggleSidebar={toggleSidebar} onLogout={handleLogout} collapsed={sidebarCollapsed} />
          <ContentArea>
            <PageContainer>
              <PageHeader>
                <div>
                  <Breadcrumb>
                    <Breadcrumb.Item onClick={() => navigate(`/agent-list?projectId=${encodeURIComponent(routeProjectId)}`)} style={{ cursor: 'pointer' }}>
                      Agent
                    </Breadcrumb.Item>
                    <Breadcrumb.Item>专项 Workflow</Breadcrumb.Item>
                  </Breadcrumb>
                  <Space vertical spacing="tight" style={{ marginTop: theme.spacing.sm }}>
                    <Title heading={3} style={{ margin: 0 }}>
                      专项 Workflow 工作台
                    </Title>
                    <Text type="secondary">项目主助手负责默认对话与任务；这里只配置重复、稳定场景下可选的专项执行流程。</Text>
                    <AgentBuilderModeSwitch value={builderMode} onChange={handleBuilderModeChange} />
                  </Space>
                </div>
                <Space>
                  <Button icon={<IconCalendarClock />} onClick={() => navigate(`/inspections?projectId=${encodeURIComponent(routeProjectId)}`)}>
                    定时任务
                  </Button>
                  <Button icon={<IconAlertTriangle />} onClick={() => navigate(`/alerts?projectId=${encodeURIComponent(routeProjectId)}`)}>
                    告警触发
                  </Button>
                  <Button icon={<IconRefresh />} onClick={() => refreshAgents(selectedAgentId)}>
                    刷新
                  </Button>
                  <Button icon={<IconArrowLeft />} onClick={() => navigate(`/agent-list?projectId=${encodeURIComponent(routeProjectId)}`)}>
                    返回
                  </Button>
                </Space>
              </PageHeader>

              <AgentBuilderGuide />

              <Spin spinning={loading}>
                <WorkspaceGrid>
                  <Panel>
                    <PanelHeader>
                      <PanelTitle>
                        <IconSetting />
                        Workflow 与默认值
                      </PanelTitle>
                      <Button size="small" icon={<IconPlus />} onClick={handleCreateAgent}>
                        新建
                      </Button>
                    </PanelHeader>
                    <PanelBody>
                      <FormStack>
                        <AgentList>
                          {agents.map((agent) => (
                            <AgentListItem
                              type="button"
                              key={agent.agentId}
                              $active={agent.agentId === selectedAgentId}
                              onMouseDown={(event) => {
                                event.preventDefault();
                                event.stopPropagation();
                                handleSelectAgent(agent.agentId);
                              }}
                              onKeyDown={(event) => {
                                if (event.key === 'Enter' || event.key === ' ') {
                                  event.preventDefault();
                                  handleSelectAgent(agent.agentId);
                                }
                              }}
                            >
                              <AgentName>{agent.name || agent.agentId}</AgentName>
                              <MetaRow>
                                <Tag color="grey">用户显式选择</Tag>
                              </MetaRow>
                            </AgentListItem>
                          ))}
                          {!agents.length && <EmptyState>暂无专项 Workflow，普通任务仍可直接使用项目主助手。</EmptyState>}
                        </AgentList>

                        {advancedBuilder && (
                          <AgentBuilderVersionSection
                            versions={agentVersions}
                            loading={versionLoading}
                            operating={versionOperating}
                            onRefresh={() => loadAgentVersions(selectedAgentId)}
                            onAction={handleVersionAction}
                          />
                        )}

                        <Section>
                          <FormStack>
                            <SectionHeader>
                              <SectionTitle>当前专项 Workflow</SectionTitle>
                              <Text type="tertiary" size="small">先说明适用边界，再用画布配置流程；不确定的请求会回退给主助手。</Text>
                            </SectionHeader>
                            <Field>
                              <Text strong>名称</Text>
                              <Input value={definition.name || ''} onChange={(value) => updateDefinition((current) => ({ ...current, name: value }))} />
                            </Field>
                            <Field>
                              <Text strong>所属项目</Text>
                              <Select
                                value={definition.projectId || routeProjectId}
                                disabled
                                style={{ width: '100%' }}
                              >
                                {projects.filter((project) => project.projectId === routeProjectId).map((project) => (
                                  <Option key={project.projectId} value={project.projectId}>
                                    {project.name} · {project.projectId}
                                  </Option>
                                ))}
                              </Select>
                              <HelpText type="tertiary" size="small">
                                Workflow 的所属项目不可直接修改；需要跨项目复用时，请从列表创建副本。
                              </HelpText>
                            </Field>
                            <Field>
                              <Text strong>说明</Text>
                              <Input value={definition.description || ''} onChange={(value) => updateDefinition((current) => ({ ...current, description: value }))} />
                            </Field>
                            <NoticeBox $tone="info">
                              项目主助手会处理所有普通请求，并统一解释工具、Skill 或 Workflow 失败。专项 Workflow 只是对重复任务的可选优化，不是新的聊天入口。
                            </NoticeBox>
                            <NoticeBox $tone="info">
                              专项 Workflow 只会在用户显式选择后运行；未选择时始终使用项目默认智能诊断。下面的场景与关键词仅用于目录说明和人工查找，不参与执行路由。
                            </NoticeBox>
                            <Field>
                              <Text strong>适合使用的场景</Text>
                              <TextArea
                                autosize={{ minRows: 3, maxRows: 6 }}
                                value={routingListText(definition.whenToUse)}
                                placeholder={'每行一个场景，例如：\n最近十分钟订单失败排查\n生成固定格式的周巡检报告'}
                                onChange={(value) => updateDefinition((current) => ({
                                  ...current,
                                  whenToUse: parseRoutingList(value),
                                }))}
                              />
                            </Field>
                            <Field>
                              <Text strong>不应使用的场景</Text>
                              <TextArea
                                autosize={{ minRows: 3, maxRows: 6 }}
                                value={routingListText(definition.whenNotToUse)}
                                placeholder={'每行一个边界，例如：\n只解释概念的知识问答\n需要修改其他项目资源'}
                                onChange={(value) => updateDefinition((current) => ({
                                  ...current,
                                  whenNotToUse: parseRoutingList(value),
                                }))}
                              />
                            </Field>
                            <Field>
                              <Text strong>检索关键词</Text>
                              <TextArea
                                autosize={{ minRows: 2, maxRows: 4 }}
                                value={routingListText(definition.routingKeywords)}
                                placeholder="每行一个关键词，例如：订单失败、下单异常、错误率"
                                onChange={(value) => updateDefinition((current) => ({
                                  ...current,
                                  routingKeywords: parseRoutingList(value),
                                }))}
                              />
                              <HelpText type="tertiary" size="small">
                                关键词仅用于帮助用户在专项流程目录中查找合适流程，不触发自动执行。
                              </HelpText>
                            </Field>
                            <NoticeBox $tone="neutral">
                              推荐使用画布表达所有流程：Agent 节点负责模型推理或工具取证，Router 节点负责选择下游，MCP/RAG/Skill 在需要使用它们的 Agent 节点上显式选择。
                            </NoticeBox>
                            {advancedBuilder && (
                            <>
                            <NoticeBox $tone={agentBindings.length ? 'info' : 'neutral'}>
                              <Space vertical align="start" spacing="tight" style={{ width: '100%' }}>
                                <Text strong>已持久化能力绑定</Text>
                                <Text type="tertiary" size="small">
                                  保存后端会把 Workflow / 节点级 Skill、MCP、知识库绑定落到独立绑定表；运行时与审计读取同一份授权边界。
                                </Text>
                                {agentBindings.length ? (
                                  <Space wrap>
                                    <Tag color="blue">Skill {agentBindingStats.skill}</Tag>
                                    <Tag color="green">MCP {agentBindingStats.projectTool}</Tag>
                                    <Tag color="purple">知识库 {agentBindingStats.knowledgeBase}</Tag>
                                    <Tag color="red">执行目标 {agentBindingStats.executionTarget}</Tag>
                                    {agentBindingStats.inlineMcp > 0 && <Tag color="orange">内联 MCP {agentBindingStats.inlineMcp}</Tag>}
                                    {agentBindingStats.other > 0 && <Tag color="grey">其他 {agentBindingStats.other}</Tag>}
                                  </Space>
                                ) : (
                                  <Text type="tertiary" size="small">
                                    当前还没有已保存的能力绑定。保存草稿或发布后会生成绑定记录。
                                  </Text>
                                )}
                              </Space>
                            </NoticeBox>

                            <AdvancedDetails open>
                              <summary>运行默认值</summary>
                              <AdvancedContent>
                                <Field>
                                  <Text strong>默认模型</Text>
                                  <Select
                                    value={definition.modelId || ''}
                                    filter
                                    style={{ width: '100%' }}
                                    onChange={(value) => updateDefinition((current) => ({ ...current, modelId: String(value || '') || undefined }))}
                                  >
                                    <Option value="">运行时默认</Option>
                                    {modelOptions.map((item) => (
                                      <Option key={item.modelId} value={item.modelId}>
                                        {item.modelName || item.modelId}
                                      </Option>
                                    ))}
                                  </Select>
                                </Field>
                                <Field>
                                  <Text strong>起始节点</Text>
                                  <Select value={definition.startNodeId} onChange={(value) => updateDefinition((current) => ({ ...current, startNodeId: String(value) }))}>
                                    {(definition.nodes || []).map((node) => (
                                      <Option key={node.nodeId} value={node.nodeId}>
                                        {node.nodeId}
                                      </Option>
                                    ))}
                                  </Select>
                                </Field>
                                <TwoColumn>
                                  <Field>
                                    <Text strong>主循环默认上限</Text>
                                    <Input
                                      value={String(definition.defaultMaxMainRounds || 3)}
                                      onChange={(value) => updateDefinition((current) => ({ ...current, defaultMaxMainRounds: Number(value) || 3 }))}
                                    />
                                  </Field>
                                  <Field>
                                    <Text strong>ReAct 节点默认轮数</Text>
                                    <Input
                                      value={String(definition.defaultSubAgentMaxIterations || 3)}
                                      onChange={(value) => updateDefinition((current) => ({ ...current, defaultSubAgentMaxIterations: Number(value) || 3 }))}
                                    />
                                  </Field>
                                </TwoColumn>
                                <Checkbox
                                  checked={definition.queryRewriteEnabled !== false}
                                  onChange={(event) => updateDefinition((current) => ({ ...current, queryRewriteEnabled: Boolean(event.target.checked) }))}
                                >
                                  主 Agent 问题重写
                                </Checkbox>
                                <HelpText type="tertiary" size="small">
                                  运行前结合当前会话记忆，把“这个接口、刚才那个错误、上面的问题”等指代改写成可独立执行的问题。
                                </HelpText>
                              </AdvancedContent>
                            </AdvancedDetails>

                            <AdvancedDetails open>
                              <summary>全局 Prompt / Skill / RAG 默认</summary>
                              <AdvancedContent>
                                <Field>
                                  <Text strong>全局 System Prompt</Text>
                                  <TextArea autosize={{ minRows: 4, maxRows: 8 }} value={definition.instruction || ''} onChange={(value) => updateDefinition((current) => ({ ...current, instruction: value }))} />
                                  <HelpText type="tertiary" size="small">
                                    只放全局安全边界、输出规范和通用角色约束；具体节点职责写在节点 System Prompt。
                                  </HelpText>
                                </Field>
                                <Field>
                                  <Text strong>默认 Skill</Text>
                                  <Select
                                    multiple
                                    filter
                                    value={definition.skills || []}
                                    placeholder="选择全局默认经验包"
                                    style={{ width: '100%' }}
                                    onChange={(values) =>
                                      updateDefinition((current) => ({
                                        ...current,
                                        skills: Array.isArray(values) ? values.map(String) : [],
                                      }))
                                    }
                                  >
                                    {renderSkillOptionGroups()}
                                  </Select>
                                  <HelpText type="tertiary" size="small">
                                    全局 Skill 会作为默认经验包；节点级 Skill 用来收窄到具体角色。
                                  </HelpText>
                                </Field>
                                <Checkbox
                                  checked={Boolean(definition.ragEnabled)}
                                  onChange={(event) => updateDefinition((current) => ({ ...current, ragEnabled: Boolean(event.target.checked) }))}
                                >
                                  默认启用 RAG
                                </Checkbox>
                                <Field>
                                  <Text strong>默认知识库范围</Text>
                                  <Select
                                    value={definition.knowledgeBaseId || ''}
                                    filter
                                    style={{ width: '100%' }}
                                    onChange={(value) => updateDefinition((current) => ({ ...current, knowledgeBaseId: String(value || '') }))}
                                  >
                                    <Option value="">继承当前项目授权知识库</Option>
                                    {authorizedKnowledgeBases.map((item) => (
                                      <Option key={item.kbId || item.knowledgeTag} value={item.kbId || item.knowledgeTag}>
                                        {item.kbName || item.name || item.knowledgeTag}
                                      </Option>
                                    ))}
                                  </Select>
                                </Field>
                                <Field>
                                  <Text strong>默认变更执行目标</Text>
                                  <Select
                                    multiple
                                    filter
                                    value={definition.executionTargetIds || []}
                                    placeholder="选择 Agent 默认允许生成提案的执行目标"
                                    style={{ width: '100%' }}
                                    onChange={(values) => updateDefinition((current) => ({
                                      ...current,
                                      executionTargetIds: Array.isArray(values) ? values.map(String) : [],
                                    }))}
                                  >
                                    <Option disabled value="__agent_execution_target_group">当前项目执行目标</Option>
                                    {executionTargetOptions.map((item) => (
                                      <Option key={item.executionTargetId} value={item.executionTargetId}>
                                        {item.name}{item.adapterType ? ` / ${item.adapterType}` : ''}
                                      </Option>
                                    ))}
                                    {!executionTargetOptions.length && (
                                      <Option disabled value="__agent_execution_target_empty">项目尚未配置执行目标</Option>
                                    )}
                                  </Select>
                                  <HelpText type="tertiary" size="small">
                                    节点可继承这里的执行目标并进一步收窄；Agent 不会看到项目内未绑定的执行资源。
                                  </HelpText>
                                </Field>
                              </AdvancedContent>
                            </AdvancedDetails>
                            </>
                            )}

                          </FormStack>
                        </Section>
                      </FormStack>
                    </PanelBody>
                  </Panel>

                  <CanvasPanel>
                    <CanvasToolbar>
                      <ToolbarGroup>
                        <Text strong>{definition.name || definition.agentId}</Text>
                        <Text type="tertiary">{(definition.nodes || []).length} 个节点 / {(definition.edges || []).length} 条边</Text>
                      </ToolbarGroup>
                      <ToolbarGroup>
                        <Button icon={<IconPlus />} onClick={handleAddNode}>
                          节点
                        </Button>
                        <Button icon={<IconBranch />} onClick={handleAutoLayout}>
                          自动布局
                        </Button>
                        <Button icon={<IconPlay />} loading={testing} onClick={handleTestRun}>
                          测试
                        </Button>
                        <Button icon={<IconSave />} loading={saving} onClick={handleSaveDraft}>
                          保存草稿
                        </Button>
                        <Button type="primary" icon={<IconSave />} loading={saving} onClick={handleSave}>
                          发布
                        </Button>
                        {selectedAgentId && (
                          <Popconfirm title="确认停用当前专项 Workflow？" onConfirm={handleDeleteAgent}>
                            <Button type="danger" icon={<IconDelete />}>
                              删除
                            </Button>
                          </Popconfirm>
                        )}
                      </ToolbarGroup>
                    </CanvasToolbar>
                    <OpsAgentCanvas
                      definition={definition}
                      height="calc(100vh - 265px)"
                      selectedNodeId={selectedNodeId}
                      selectedEdgeKey={selectedEdgeKey}
                      runtimeEvents={runtimeEvents}
                      onSelectNode={(nodeId) => {
                        setSelectedNodeId(nodeId);
                        setSelectedEdgeIndex(null);
                        setActivePanel('node');
                      }}
                      onSelectEdge={handleEdgeSelect}
                      onCanvasClick={() => setSelectedEdgeIndex(null)}
                      onMoveNode={handleMoveNode}
                      onConnectEdge={handleCanvasConnectEdge}
                      onCreateNode={handleCanvasCreateNode}
                    />
                  </CanvasPanel>

                  <AgentBuilderPropertyPanel
                    mode={builderMode}
                    activePanel={activePanel}
                    onPanelChange={setActivePanel}
                    nodePanel={renderNodePanel()}
                    edgePanel={renderEdgePanel()}
                    jsonPanel={renderJsonPanel()}
                    testPanel={renderTestPanel()}
                  />
                </WorkspaceGrid>
              </Spin>

              <Panel>
                <PanelBody>
                  <Paragraph spacing="extended" style={{ margin: 0 }}>
                    保存后写入后端 Workflow Registry。只有用户显式选择该专项流程时才运行固定 Workflow；未选择时使用项目默认智能诊断。ReAct 节点仍在当前 Runtime Authority 与环境权限边界内自行多轮调用工具。
                  </Paragraph>
                </PanelBody>
              </Panel>
            </PageContainer>
          </ContentArea>
        </div>
      </MainContent>
    </PageLayout>
  );
};
