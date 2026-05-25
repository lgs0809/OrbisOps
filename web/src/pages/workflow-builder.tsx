import React, { useEffect, useMemo, useState } from 'react';
import {
  Banner,
  Button,
  Card,
  Checkbox,
  Divider,
  Input,
  InputNumber,
  Select,
  Space,
  Spin,
  Tag,
  TextArea,
  Toast,
  Typography,
} from '@douyinfe/semi-ui';
import { IconArrowLeft, IconRefresh, IconSave } from '@douyinfe/semi-icons';
import { useNavigate, useSearchParams } from 'react-router-dom';
import styled from 'styled-components';
import { useQueryClient } from '@tanstack/react-query';

import { OpsAgentCanvas } from '../components/ops-agent-canvas';
import { OpsPageShell, OpsWorkspaceFrame } from '../components/ops-layout';
import { useProjectScope } from '../hooks/use-project-scope';
import {
  opsAdminService,
  type OpsAgentCapabilitySet,
  type OpsAgentDefinition,
  type OpsGraphEdge,
  type OpsWorkflowNode,
} from '../services/ops-admin-service';
import { theme } from '../styles/theme';
import { userFacingError } from '../utils/user-facing-error';
import { AgentBuilderGuide } from '../features/agents/components/AgentBuilderGuide';
import { WorkflowManualTestButton } from '../features/agents/components/WorkflowManualTestButton';
import { WorkflowDirectActions } from '../features/agents/components/WorkflowDirectActions';
import { useWorkflowEditorQuery } from '../features/agents/api/workflow-editor-query';
import { AgentBuilderModeSwitch } from '../features/agents/components/AgentBuilderModeSwitch';
import { AgentBuilderPropertyPanel } from '../features/agents/components/AgentBuilderPropertyPanel';
import { AgentBuilderVersionSection, type AgentVersionAction } from '../features/agents/components/AgentBuilderVersionSection';
import {
  agentBuilderPanelForMode,
  type AgentBuilderMode,
  type AgentBuilderPanel,
} from '../features/agents/model/agent-builder-mode';

const { Text, Title, Paragraph } = Typography;
const { Option } = Select;

const Stack = styled.div`
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-width: 0;
`;

const FieldGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: ${theme.spacing.base};

  @media (max-width: 760px) {
    grid-template-columns: 1fr;
  }
`;

const Field = styled.div`
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
`;

const WideField = styled(Field)`
  grid-column: 1 / -1;
`;

const Inspector = styled(Card)`
  position: static;
  max-height: none;
  overflow: visible;
  border-radius: 12px;
`;

const EdgeList = styled.div`
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: ${theme.spacing.base};
`;

const EdgeButton = styled.button<{ $active?: boolean }>`
  display: flex;
  justify-content: space-between;
  gap: 8px;
  width: 100%;
  border: 1px solid ${(props) => (props.$active ? theme.colors.primary : theme.colors.border.secondary)};
  border-radius: ${theme.borderRadius.base};
  background: ${(props) => (props.$active ? theme.colors.bg.secondary : theme.colors.bg.primary)};
  padding: 8px 10px;
  cursor: pointer;
  text-align: left;
  color: ${theme.colors.text.primary};
`;

const JsonBox = styled.pre`
  margin: 0;
  padding: ${theme.spacing.base};
  border-radius: ${theme.borderRadius.base};
  background: #0f172a;
  color: #e2e8f0;
  overflow: auto;
  font-size: 12px;
  line-height: 1.55;
`;

const Editor = styled(OpsWorkspaceFrame)`
  min-height: 0;
  display: flex;
  flex-direction: column;
  background: #f4f5f7;
  overflow: hidden;

  @media (max-width: 1180px) {
    overflow-y: auto;
  }

`;

const EditorToolbar = styled.header`
  min-height: 62px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 10px 16px;
  border-bottom: 1px solid ${theme.colors.border.secondary};
  background: #fff;

  @media (max-width: ${theme.breakpoints.md}) {
    align-items: flex-start;
    flex-direction: column;
  }
`;

const EditorIdentity = styled.div`
  min-width: 0;

  .eyebrow {
    display: block;
    margin-bottom: 2px;
    color: ${theme.colors.text.tertiary};
    font-size: 12px;
    font-weight: 650;
    letter-spacing: 0.08em;
  }

  strong {
    display: block;
    overflow: hidden;
    color: ${theme.colors.text.primary};
    font-size: 14px;
    font-weight: 650;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
`;

const DefinitionBar = styled.section`
  flex-shrink: 0;
  display: grid;
  grid-template-columns: minmax(180px, 0.8fr) minmax(220px, 1.2fr) auto;
  gap: 10px;
  align-items: end;
  padding: 11px 16px;
  border-bottom: 1px solid ${theme.colors.border.secondary};
  background: #fafbfc;

  @media (max-width: 980px) {
    grid-template-columns: 1fr;
  }
`;

const EditorBody = styled.div`
  min-height: 0;
  flex: 1;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 360px;
  gap: 0;

  @media (max-width: 1180px) {
    display: block;
    flex: none;
  }
`;

const CanvasColumn = styled.div`
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  padding: 12px;
`;

const CanvasSurface = styled.div`
  min-width: 0;
  min-height: 200px;
  flex: 1;
  overflow: hidden;
  border: 1px solid ${theme.colors.border.secondary};
  border-radius: 12px;
  background: #fff;

  @media (max-width: 1180px) {
    height: 560px;
    flex: none;
  }
`;

const GuideSlot = styled.div`
  margin-bottom: 10px;
`;

const InspectorRail = styled.div`
  min-width: 0;
  min-height: 0;
  padding: 12px;
  border-left: 1px solid ${theme.colors.border.secondary};
  background: #f7f8fa;
  overflow: auto;

  @media (max-width: 1180px) {
    border-left: 0;
    border-top: 1px solid ${theme.colors.border.secondary};
    overflow: visible;
  }
`;

const MetadataDetails = styled.details`
  margin-bottom: 10px;
  padding: 10px 12px;
  border: 1px solid ${theme.colors.border.secondary};
  border-radius: 10px;
  background: #fff;

  summary {
    color: ${theme.colors.text.secondary};
    font-size: 12px;
    font-weight: 600;
    cursor: pointer;
  }

  > div {
    margin-top: 10px;
  }
`;

type CapabilityOption = { value: string; label: string };

const normalizeMode = (value: unknown): 'direct' | 'llm' | 'react' => {
  const normalized = String(value || '').trim().toLowerCase();
  if (normalized === 'direct') return 'direct';
  if (normalized === 'react') return 'react';
  return 'llm';
};

const normalizeNode = (node: OpsWorkflowNode): OpsWorkflowNode => {
  const type = String(node.type || 'AGENT').trim().toUpperCase();
  if (['START', 'END', 'ROUTER', 'SUB_WORKFLOW', 'HUMAN_APPROVAL'].includes(type)) {
    return { ...node, type };
  }
  const mode = normalizeMode(node.mode || node.config?.mode || node.config?.agentMode);
  const legacyReviewer = type === 'REVIEW' || type === 'REFLECT' || String(node.mode || '').toLowerCase() === 'review';
  return {
    ...node,
    type: 'AGENT',
    mode,
    config: {
      ...(node.config || {}),
      mode,
      role: node.config?.role || (legacyReviewer ? 'reviewer' : 'general'),
      reviewMode: undefined,
      agentMode: undefined,
    },
  };
};

const normalizeDefinition = (definition: OpsAgentDefinition, projectId: string): OpsAgentDefinition => ({
  ...definition,
  projectId,
  engine: 'GRAPH',
  definitionKind: 'SPECIALIZED_WORKFLOW',
  workflowInvocationMode: 'MANUAL_ONLY',
  workflowAutoSelectEnabled: false,
  workflowPriority: undefined,
  nodes: (definition.nodes || []).map(normalizeNode),
  edges: (definition.edges || []).map((edge) => ({
    ...edge,
    conditionType: edge.conditionType || 'always',
    condition: edge.condition || 'always',
  })),
  loops: definition.loops || [],
});

const createDefaultDefinition = (projectId: string): OpsAgentDefinition => {
  const suffix = Date.now().toString(36);
  return {
    agentId: `workflow-${suffix}`,
    schemaVersion: 1,
    name: '新建 Workflow',
    projectId,
    engine: 'GRAPH',
    description: '用于稳定、可重复运维流程的可复用执行 Workflow。',
    definitionKind: 'SPECIALIZED_WORKFLOW',
    workflowInvocationMode: 'MANUAL_ONLY',
    workflowAutoSelectEnabled: false,
    whenToUse: [],
    whenNotToUse: [],
    routingKeywords: [],
    startNodeId: 'start',
    defaultMaxMainRounds: 3,
    defaultSubAgentMaxIterations: 3,
    nodes: [
      {
        nodeId: 'start',
        type: 'START',
        agent: 'start',
        description: '接收输入与 Project Runtime 上下文。',
        outputKey: 'query',
        config: { position: { x: 80, y: 220 }, inputKeys: ['query', 'sessionId', 'userId', 'metadata'] },
      },
      {
        nodeId: 'agent',
        type: 'AGENT',
        mode: 'llm',
        agent: 'agent',
        description: '使用显式绑定的能力完成当前 Workflow 节点职责。',
        instruction: '分析输入，并输出包含证据、风险、建议和未知项的有依据结果。',
        outputKey: 'answer',
        skills: [],
        mcpIds: [],
        config: {
          position: { x: 380, y: 220 },
          mode: 'llm',
          role: 'general',
          contextInputs: ['query', 'upstreamOutputs'],
        },
      },
      {
        nodeId: 'end',
        type: 'END',
        agent: 'end',
        description: '输出最终 Workflow 结果。',
        outputKey: 'final_output',
        config: { position: { x: 700, y: 220 }, outputKeys: ['answer'] },
      },
    ],
    edges: [
      { edgeId: 'start-agent', from: 'start', to: 'agent', conditionType: 'always', condition: 'always' },
      { edgeId: 'agent-end', from: 'agent', to: 'end', conditionType: 'always', condition: 'always' },
    ],
    loops: [],
  };
};

const edgeKey = (edge: OpsGraphEdge, index: number) =>
  `${edge.edgeId || `${edge.from}->${edge.to}`}:${edge.conditionType || 'always'}:${edge.condition || 'always'}:${index}`;

const unique = (values: string[]) => Array.from(new Set(values.map((value) => value.trim()).filter(Boolean)));
const parseList = (value: string) => unique(value.split(/[\n,]/));
const listText = (value?: string[]) => (value || []).join('\n');

const valueOf = (item: Record<string, any>, keys: string[]) => {
  for (const key of keys) {
    const value = item[key];
    if (value !== undefined && value !== null && String(value).trim()) return String(value);
  }
  return '';
};

const optionsOf = (items: Array<Record<string, any>> | undefined, idKeys: string[], labelKeys: string[]): CapabilityOption[] =>
  (items || []).map((item) => {
    const value = valueOf(item, idKeys);
    const label = valueOf(item, labelKeys) || value;
    return { value, label };
  }).filter((item, index, all) => Boolean(item.value) && all.findIndex((candidate) => candidate.value === item.value) === index);

const validateDefinition = (definition: OpsAgentDefinition): string[] => {
  const errors: string[] = [];
  if (!definition.projectId) errors.push('请选择 Project。');
  if (!definition.name?.trim()) errors.push('请输入 Workflow 名称。');
  const nodes = definition.nodes || [];
  const starts = nodes.filter((node) => node.type === 'START');
  const ends = nodes.filter((node) => node.type === 'END');
  if (starts.length !== 1) errors.push('一个 Workflow 必须且只能包含一个 Start 节点。');
  if (ends.length < 1) errors.push('一个 Workflow 至少需要一个 End 节点。');
  const ids = new Set(nodes.map((node) => node.nodeId));
  if (ids.size !== nodes.length) errors.push('节点 ID 必须唯一。');
  (definition.edges || []).forEach((edge) => {
    if (!ids.has(edge.from) || !ids.has(edge.to)) errors.push(`边 ${edge.from} → ${edge.to} 引用了不存在的节点。`);
  });
  nodes.filter((node) => node.type === 'AGENT').forEach((node) => {
    const mode = normalizeMode(node.mode || node.config?.mode);
    if (!['direct', 'llm', 'react'].includes(mode)) errors.push(`Agent Node ${node.nodeId} 使用了不支持的执行模式。`);
    if (mode === 'direct' && !Array.isArray(node.config?.actions)) errors.push(`DIRECT 节点 ${node.nodeId} 必须配置 actions 数组。`);
  });
  if (starts.length === 1) {
    const reachable = new Set<string>();
    const queue = [starts[0].nodeId];
    while (queue.length) {
      const current = queue.shift()!;
      if (reachable.has(current)) continue;
      reachable.add(current);
      (definition.edges || []).filter((edge) => edge.from === current).forEach((edge) => queue.push(edge.to));
    }
    const unreachableEnd = ends.find((node) => !reachable.has(node.nodeId));
    if (unreachableEnd) errors.push(`End 节点 ${unreachableEnd.nodeId} 无法从 Start 到达。`);
  }
  return unique(errors);
};

const responseData = <T,>(response: { code: string; info?: string; data?: T }, fallback?: T): T => {
  if (response.code !== '0000') throw new Error(response.info || '请求失败');
  if (response.data === undefined || response.data === null) {
    if (fallback !== undefined) return fallback;
    throw new Error(response.info || '响应数据缺失');
  }
  return response.data;
};

export const WorkflowBuilderPage: React.FC<{ publishImpact?: React.ReactNode }> = ({ publishImpact }) => {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const queryClient = useQueryClient();
  const projectScope = useProjectScope();
  const projectId = searchParams.get('projectId') || projectScope.projectId || '';
  const agentId = searchParams.get('agentId') || '';

  const [definition, setDefinition] = useState<OpsAgentDefinition>(() => createDefaultDefinition(projectId));
  const [versions, setVersions] = useState<OpsAgentDefinition[]>([]);
  const [capabilities, setCapabilities] = useState<OpsAgentCapabilitySet | null>(null);
  const [publishedWorkflows, setPublishedWorkflows] = useState<OpsAgentDefinition[]>([]);
  const [selectedNodeId, setSelectedNodeId] = useState('agent');
  const [selectedEdgeKey, setSelectedEdgeKey] = useState('');
  const [mode, setMode] = useState<AgentBuilderMode>('basic');
  const [activePanel, setActivePanel] = useState<AgentBuilderPanel>('node');
  const [rawJson, setRawJson] = useState('');
  const editorQuery = useWorkflowEditorQuery(projectId, agentId);
  const [appliedData, setAppliedData] = useState<{ data: typeof editorQuery.data; revision: number }>();
  const loading = editorQuery.isFetching;
  const definitionReady = Boolean(editorQuery.data && appliedData?.data === editorQuery.data && appliedData?.revision === editorQuery.dataUpdatedAt);
  const [operating, setOperating] = useState(false);
  const refreshEditorAfterMutation = async () => {
    const queryKey = ['workflow-editor', projectId];
    // A read started before save/publish cannot replace the final server version.
    await queryClient.cancelQueries({ queryKey });
    await queryClient.invalidateQueries({ queryKey });
  };

  const loadVersions = async (id: string) => {
    if (!id) {
      setVersions([]);
      return;
    }
    try {
      const response = await opsAdminService.listAgentVersions(id);
      setVersions(response.code === '0000' ? (response.data || []) : []);
    } catch {
      setVersions([]);
    }
  };

  const loadDefinition = () => editorQuery.refetch();

  useEffect(() => {
    const data = editorQuery.data;
    if (!data || data.projectId !== projectId || data.agentId !== agentId) return;
    const loaded = data.definition ? normalizeDefinition(data.definition, projectId) : createDefaultDefinition(projectId);
    setDefinition(loaded);
    setSelectedNodeId(loaded.nodes?.find(node => node.type === 'AGENT')?.nodeId || loaded.nodes?.[0]?.nodeId || '');
    setSelectedEdgeKey('');
    setCapabilities(data.capabilities);
    setPublishedWorkflows(data.workflows);
    setVersions(data.versions);
    setAppliedData({ data, revision: editorQuery.dataUpdatedAt });
  }, [editorQuery.data, editorQuery.dataUpdatedAt, projectId, agentId]);

  useEffect(() => {
    setRawJson(JSON.stringify(normalizeDefinition(definition, projectId), null, 2));
  }, [definition, projectId]);

  const selectedNode = useMemo(
    () => (definition.nodes || []).find((node) => node.nodeId === selectedNodeId) || null,
    [definition.nodes, selectedNodeId],
  );
  const selectedEdgeIndex = useMemo(
    () => (definition.edges || []).findIndex((edge, index) => edgeKey(edge, index) === selectedEdgeKey),
    [definition.edges, selectedEdgeKey],
  );
  const selectedEdge = selectedEdgeIndex >= 0 ? (definition.edges || [])[selectedEdgeIndex] : null;

  const skillOptions = useMemo(() => optionsOf(
    [...(capabilities?.projectSkills || []), ...(capabilities?.enabledGlobalSkills || [])],
    ['skillId', 'id', 'name', 'skillName'],
    ['skillName', 'name', 'skillId', 'id'],
  ), [capabilities]);
  const toolOptions = useMemo(() => optionsOf(
    [...(capabilities?.projectTools || []), ...(capabilities?.enabledSharedTools || [])],
    ['mcpId', 'toolId', 'id', 'name'],
    ['name', 'toolName', 'mcpId', 'toolId'],
  ), [capabilities]);
  const knowledgeOptions = useMemo(() => optionsOf(
    [...(capabilities?.projectKnowledgeBases || []), ...(capabilities?.enabledGlobalKnowledgeBases || [])],
    ['kbId', 'knowledgeBaseId', 'id', 'name'],
    ['name', 'knowledgeName', 'kbId', 'id'],
  ), [capabilities]);
  const executionTargetOptions = useMemo(() => optionsOf(
    capabilities?.executionTargets || [],
    ['targetId', 'executionTargetId', 'id', 'name'],
    ['name', 'targetName', 'targetId', 'id'],
  ), [capabilities]);

  const updateDefinition = (patch: Partial<OpsAgentDefinition>) => setDefinition((current) => ({ ...current, ...patch }));
  const updateNode = (patch: Partial<OpsWorkflowNode>) => {
    if (!selectedNodeId) return;
    setDefinition((current) => ({
      ...current,
      nodes: (current.nodes || []).map((node) => node.nodeId === selectedNodeId ? normalizeNode({ ...node, ...patch }) : node),
    }));
  };
  const updateNodeConfig = (patch: Record<string, any>) => {
    if (!selectedNode) return;
    updateNode({ config: { ...(selectedNode.config || {}), ...patch } });
  };

  const updateEdge = (patch: Partial<OpsGraphEdge>) => {
    if (selectedEdgeIndex < 0) return;
    setDefinition((current) => ({
      ...current,
      edges: (current.edges || []).map((edge, index) => index === selectedEdgeIndex ? { ...edge, ...patch } : edge),
    }));
    setSelectedEdgeKey('');
  };

  const addNode = (node: OpsWorkflowNode, edge?: OpsGraphEdge) => {
    const normalized = normalizeNode(node);
    setDefinition((current) => ({
      ...current,
      nodes: [...(current.nodes || []), normalized],
      edges: edge ? [...(current.edges || []), edge] : (current.edges || []),
    }));
    setSelectedNodeId(normalized.nodeId);
    setActivePanel('node');
  };

  const removeSelectedNode = () => {
    if (!selectedNode || ['START', 'END'].includes(selectedNode.type)) return;
    setDefinition((current) => ({
      ...current,
      nodes: (current.nodes || []).filter((node) => node.nodeId !== selectedNode.nodeId),
      edges: (current.edges || []).filter((edge) => edge.from !== selectedNode.nodeId && edge.to !== selectedNode.nodeId),
    }));
    setSelectedNodeId('');
  };

  const connectEdge = (edge: OpsGraphEdge) => {
    setDefinition((current) => {
      const exists = (current.edges || []).some((item) => item.from === edge.from && item.to === edge.to);
      if (exists) return current;
      return { ...current, edges: [...(current.edges || []), edge] };
    });
  };

  const moveNode = (nodeId: string, position: { x: number; y: number }) => {
    setDefinition((current) => ({
      ...current,
      nodes: (current.nodes || []).map((node) => node.nodeId === nodeId
        ? { ...node, config: { ...(node.config || {}), position } }
        : node),
    }));
  };

  const saveDraft = async (): Promise<OpsAgentDefinition | null> => {
    if (loading || operating || !definitionReady) return null;
    if (!projectId) {
      Toast.error('保存前请先选择 Project。');
      return null;
    }
    const payload = normalizeDefinition(definition, projectId);
    const errors = validateDefinition(payload);
    if (errors.length) {
      Toast.error(errors[0]);
      return null;
    }
    setOperating(true);
    try {
      const response = await opsAdminService.saveAgentDraft(payload);
      const saved = normalizeDefinition(responseData(response, payload), projectId);
      setDefinition(saved);
      const nextParams = new URLSearchParams(searchParams);
      nextParams.set('projectId', projectId);
      nextParams.set('agentId', saved.agentId);
      setSearchParams(nextParams, { replace: true });
      await loadVersions(saved.agentId);
      Toast.success(`草稿已保存${saved.version ? `，版本 v${saved.version}` : ''}。`);
      return saved;
    } catch (error) {
      Toast.error(userFacingError(error, '保存 Workflow 草稿失败，请稍后重试。'));
      return null;
    } finally {
      await refreshEditorAfterMutation();
      setOperating(false);
    }
  };

  const validateSaved = async (saved?: OpsAgentDefinition | null) => {
    const target = saved || definition;
    if (!target.agentId || !target.version) {
      Toast.warning('请先保存草稿，再执行校验。');
      return false;
    }
    setOperating(true);
    try {
      const bindingResponse = await opsAdminService.validateAgentBindings(target.agentId, normalizeDefinition(target, projectId));
      const binding = responseData(bindingResponse);
      if (!binding.valid) {
        Toast.error(binding.errors?.[0] || 'Workflow 能力绑定校验失败。');
        return false;
      }
      // Lifecycle validation is a state transition (DRAFT -> VALIDATED), not a
      // generic health check. Published/validated versions are immutable and
      // must never be sent to that endpoint, which would otherwise surface a
      // misleading server error to a user who only asked to inspect a version.
      const lifecycle = String(target.lifecycle || '').trim().toUpperCase();
      if (lifecycle !== 'DRAFT') {
        if (lifecycle === 'DISABLED') {
          Toast.warning(`Workflow v${target.version} 已停用；请保存新的草稿后再校验。`);
          return false;
        }
        Toast.success(lifecycle === 'PUBLISHED'
          ? `Workflow v${target.version} 已发布，能力绑定校验通过。`
          : `Workflow v${target.version} 已校验，能力绑定校验通过。`);
        return true;
      }
      const response = await opsAdminService.validateAgentVersion(target.agentId, target.version);
      const validated = normalizeDefinition(responseData(response, target), projectId);
      setDefinition(validated);
      await loadVersions(target.agentId);
      Toast.success(`Workflow v${target.version} 校验通过。`);
      return true;
    } catch (error) {
      Toast.error(userFacingError(error, 'Workflow 校验失败，请检查配置后重试。'));
      return false;
    } finally {
      await refreshEditorAfterMutation();
      setOperating(false);
    }
  };

  const publish = async () => {
    const saved = await saveDraft();
    if (!saved?.version) return;
    const valid = await validateSaved(saved);
    if (!valid) return;
    setOperating(true);
    try {
      const response = await opsAdminService.publishAgentVersion(saved.agentId, saved.version);
      const published = normalizeDefinition(responseData(response, saved), projectId);
      setDefinition(published);
      await loadVersions(saved.agentId);
      Toast.success(`Workflow v${saved.version} 已发布。`);
    } catch (error) {
      Toast.error(userFacingError(error, '发布 Workflow 失败，请稍后重试。'));
    } finally {
      await refreshEditorAfterMutation();
      setOperating(false);
    }
  };

  const handleVersionAction = async (action: AgentVersionAction, version?: number) => {
    if (!definition.agentId || !version) return;
    setOperating(true);
    try {
      if (action === 'validate') await opsAdminService.validateAgentVersion(definition.agentId, version);
      if (action === 'publish') await opsAdminService.publishAgentVersion(definition.agentId, version);
      if (action === 'rollback') await opsAdminService.rollbackAgentVersion(definition.agentId, version);
      if (action === 'disable') await opsAdminService.disableAgentVersion(definition.agentId, version);
      await loadVersions(definition.agentId);
      const refreshed = await opsAdminService.getAgent(definition.agentId);
      if (refreshed.code === '0000' && refreshed.data) setDefinition(normalizeDefinition(refreshed.data, projectId));
      const actionLabel = action === 'validate' ? '校验' : action === 'publish' ? '发布' : action === 'rollback' ? '回滚' : '停用';
      Toast.success(`Workflow v${version} ${actionLabel}完成。`);
    } catch (error) {
      const actionLabel = action === 'validate' ? '校验' : action === 'publish' ? '发布' : action === 'rollback' ? '回滚' : '停用';
      Toast.error(userFacingError(error, `${actionLabel} Workflow v${version} 失败，请稍后重试。`));
    } finally {
      await refreshEditorAfterMutation();
      setOperating(false);
    }
  };

  const applyJson = () => {
    try {
      const parsed = JSON.parse(rawJson) as OpsAgentDefinition;
      const normalized = normalizeDefinition(parsed, projectId);
      const errors = validateDefinition(normalized);
      if (errors.length) {
        Toast.error(errors[0]);
        return;
      }
      setDefinition(normalized);
      setSelectedNodeId(normalized.nodes?.[0]?.nodeId || '');
      setSelectedEdgeKey('');
      Toast.success('高级 JSON 已应用到草稿。');
    } catch {
      Toast.error('Workflow JSON 无效，请检查格式。');
    }
  };

  const nodePanel = !selectedNode ? (
    <Banner type="info" description="请在画布上选择节点，然后配置它的执行模式和能力。" />
  ) : (
    <Stack>
      <Space wrap>
        <Tag>{selectedNode.type}</Tag>
        <Text strong>{selectedNode.nodeId}</Text>
        {selectedNode.type === 'AGENT' && <Tag color="blue">{normalizeMode(selectedNode.mode || selectedNode.config?.mode).toUpperCase()}</Tag>}
      </Space>
      <Field>
        <Text strong>说明</Text>
        <Input value={selectedNode.description || ''} onChange={(description) => updateNode({ description })} />
      </Field>

      {selectedNode.type === 'AGENT' && (
        <>
          <Field>
            <Text strong>执行模式</Text>
            <Select
              value={normalizeMode(selectedNode.mode || selectedNode.config?.mode)}
              onChange={(value) => {
                const nextMode = normalizeMode(value);
                updateNode({ mode: nextMode, config: { ...(selectedNode.config || {}), mode: nextMode } });
              }}
            >
              <Option value="direct">DIRECT · 确定性的受治理动作</Option>
              <Option value="llm">LLM · 单次模型调用</Option>
              <Option value="react">REACT · 有界工具循环</Option>
            </Select>
          </Field>
          {normalizeMode(selectedNode.mode || selectedNode.config?.mode) !== 'direct' && <Field>
            <Text strong>节点 Prompt</Text>
            <TextArea autosize={{ minRows: 5, maxRows: 12 }} value={selectedNode.instruction || ''} onChange={(instruction) => updateNode({ instruction })} />
          </Field>}

          {normalizeMode(selectedNode.mode || selectedNode.config?.mode) === 'llm' && (
            <Field>
              <Text strong>LLM 职责</Text>
              <Select value={String(selectedNode.config?.role || 'general')} onChange={(role) => updateNodeConfig({ role: String(role) })}>
                <Option value="general">通用处理</Option>
                <Option value="reviewer">Reviewer · 结构化判断</Option>
                <Option value="reporter">报告 · 综合整理</Option>
                <Option value="extractor">抽取 · 结构化提取</Option>
                <Option value="classifier">分类 · 结构化分类</Option>
              </Select>
              <Text type="tertiary" size="small">Review 是 LLM 节点的一种职责，不是第四种执行模式。</Text>
            </Field>
          )}

          {normalizeMode(selectedNode.mode || selectedNode.config?.mode) === 'direct' && (
            <Field>
              <WorkflowDirectActions
                nodeId={selectedNode.nodeId}
                actions={selectedNode.config?.actions}
                mode={mode}
                toolNames={new Map(toolOptions.map((tool) => [tool.value, tool.label]))}
                onAdvanced={() => { setMode('advanced'); setActivePanel('node'); }}
                onChange={(actions) => updateNodeConfig({ actions })}
              />
            </Field>
          )}

          <Divider margin="12px" />
          <Title heading={6} style={{ margin: 0 }}>Agent Node 能力</Title>
          <Field>
            <Text strong>Skill</Text>
            <Select multiple filter value={selectedNode.skills || []} onChange={(value) => updateNode({ skills: (value || []) as string[] })} style={{ width: '100%' }}>
              {skillOptions.map((option) => <Option key={option.value} value={option.value}>{option.label}</Option>)}
            </Select>
          </Field>
          <Field>
            <Text strong>工具 / MCP</Text>
            <Select multiple filter value={selectedNode.mcpIds || []} onChange={(value) => updateNode({ mcpIds: (value || []) as string[] })} style={{ width: '100%' }}>
              {toolOptions.map((option) => <Option key={option.value} value={option.value}>{option.label}</Option>)}
            </Select>
          </Field>
          <Field>
            <Checkbox checked={Boolean(selectedNode.ragEnabled)} onChange={(event) => updateNode({ ragEnabled: Boolean(event.target.checked) })}>
              为当前 Agent Node 启用知识库检索
            </Checkbox>
          </Field>
          {selectedNode.ragEnabled && (
            <Field>
              <Text strong>知识库</Text>
              <Select filter value={selectedNode.knowledgeBaseId || undefined} onChange={(value) => updateNode({ knowledgeBaseId: String(value || '') })} style={{ width: '100%' }}>
                {knowledgeOptions.map((option) => <Option key={option.value} value={option.value}>{option.label}</Option>)}
              </Select>
            </Field>
          )}
          <Field>
            <Text strong>执行目标</Text>
            <Select multiple filter value={selectedNode.executionTargetIds || []} onChange={(value) => updateNode({ executionTargetIds: (value || []) as string[] })} style={{ width: '100%' }}>
              {executionTargetOptions.map((option) => <Option key={option.value} value={option.value}>{option.label}</Option>)}
            </Select>
            <Text type="tertiary" size="small">绑定执行目标只代表节点具备生成受治理变更提案的能力；生产变更仍必须经过 ChangePackage → Approval → Landing → Verification。</Text>
          </Field>
        </>
      )}

      {selectedNode.type === 'ROUTER' && (
        <FieldGrid>
          <Field>
            <Text strong>路由模式</Text>
            <Select value={String(selectedNode.config?.routeMode || 'multi')} onChange={(routeMode) => updateNodeConfig({ routeMode: String(routeMode) })}>
              <Option value="single">单一下游分支</Option>
              <Option value="multi">多个下游分支</Option>
            </Select>
          </Field>
          <Field>
            <Text strong>路由输出 Key</Text>
            <Input value={String(selectedNode.outputKey || 'selectedRoutes')} onChange={(outputKey) => updateNode({ outputKey })} />
          </Field>
          <WideField>
            <Text strong>Router 指令</Text>
            <TextArea autosize={{ minRows: 4, maxRows: 8 }} value={selectedNode.instruction || ''} onChange={(instruction) => updateNode({ instruction })} />
          </WideField>
        </FieldGrid>
      )}

      {selectedNode.type === 'SUB_WORKFLOW' && (
        <FieldGrid>
          <WideField>
            <Text strong>已发布 Workflow</Text>
            <Select filter value={selectedNode.agent || undefined} onChange={(agent) => updateNode({ agent: String(agent || '') })} style={{ width: '100%' }}>
              {publishedWorkflows.filter((workflow) => workflow.agentId !== definition.agentId).map((workflow) => (
                <Option key={workflow.agentId} value={workflow.agentId}>{workflow.name || workflow.agentId}</Option>
              ))}
            </Select>
          </WideField>
          <Field>
            <Text strong>版本策略</Text>
            <Select value={String(selectedNode.config?.versionPolicy || 'LATEST_PUBLISHED')} onChange={(versionPolicy) => updateNodeConfig({ versionPolicy: String(versionPolicy) })}>
              <Option value="LATEST_PUBLISHED">始终使用最新已发布版本</Option>
              <Option value="PINNED_VERSION">固定版本</Option>
            </Select>
          </Field>
          <Field>
            <Text strong>输入 Graph State Key</Text>
            <Input value={String(selectedNode.config?.inputKey || '')} onChange={(inputKey) => updateNodeConfig({ inputKey })} />
          </Field>
        </FieldGrid>
      )}

      {selectedNode.type === 'HUMAN_APPROVAL' && (
        <>
          <Field>
            <Text strong>审批说明</Text>
            <TextArea value={selectedNode.instruction || ''} onChange={(instruction) => updateNode({ instruction })} autosize={{ minRows: 3, maxRows: 6 }} />
          </Field>
          <Field>
            <Text strong>审批有效期（秒）</Text>
            <InputNumber min={60} max={86400} value={Number(selectedNode.config?.timeoutSeconds || 1800)}
              onChange={(timeoutSeconds) => updateNodeConfig({ timeoutSeconds: Number(timeoutSeconds) })} />
            <Text type="tertiary" size="small">审批等待会持久保存；超时后需重新发起审核。</Text>
          </Field>
        </>
      )}

      {selectedNode.type === 'START' && (
        <Field>
          <Text strong>输入契约 Key</Text>
          <TextArea value={listText(selectedNode.config?.inputKeys)} onChange={(value) => updateNodeConfig({ inputKeys: parseList(value) })} autosize={{ minRows: 3, maxRows: 6 }} />
        </Field>
      )}

      {selectedNode.type === 'END' && (
        <Field>
          <Text strong>输出契约 Key</Text>
          <TextArea value={listText(selectedNode.config?.outputKeys)} onChange={(value) => updateNodeConfig({ outputKeys: parseList(value) })} autosize={{ minRows: 3, maxRows: 6 }} />
        </Field>
      )}

      {!['START', 'END'].includes(selectedNode.type) && (
        <Button type="danger" theme="borderless" onClick={removeSelectedNode}>删除节点</Button>
      )}
    </Stack>
  );

  const edgePanel = (
    <Stack>
      <EdgeList>
        {(definition.edges || []).map((edge, index) => {
          const key = edgeKey(edge, index);
          return (
            <EdgeButton key={key} type="button" $active={key === selectedEdgeKey} onClick={() => setSelectedEdgeKey(key)}>
              <span>{edge.from} → {edge.to}</span>
              <Tag>{edge.conditionType || 'always'}</Tag>
            </EdgeButton>
          );
        })}
      </EdgeList>
      {!selectedEdge && <Banner type="info" description="请在画布或列表中选择一条边，然后配置路由条件。" />}
      {selectedEdge && (
        <FieldGrid>
          <Field>
            <Text strong>条件类型</Text>
            <Select value={selectedEdge.conditionType || 'always'} onChange={(conditionType) => updateEdge({ conditionType: String(conditionType) })}>
              <Option value="always">always</Option>
              <Option value="route_match">route_match</Option>
              <Option value="review_decision">review_decision</Option>
              <Option value="expression">expression</Option>
              <Option value="contains">contains</Option>
              <Option value="default">default</Option>
              <Option value="error">error</Option>
            </Select>
          </Field>
          <Field>
            <Text strong>条件值</Text>
            <Input value={selectedEdge.condition || 'always'} onChange={(condition) => updateEdge({ condition })} />
          </Field>
          <WideField>
            <Checkbox checked={Boolean(selectedEdge.feedback)} onChange={(event) => updateEdge({ feedback: Boolean(event.target.checked) })}>
              Feedback 边 / 有界循环
            </Checkbox>
          </WideField>
          <WideField>
            <Button
              type="danger"
              theme="borderless"
              onClick={() => {
                setDefinition((current) => ({ ...current, edges: (current.edges || []).filter((_, index) => index !== selectedEdgeIndex) }));
                setSelectedEdgeKey('');
              }}
            >
              删除边
            </Button>
          </WideField>
        </FieldGrid>
      )}
    </Stack>
  );

  const jsonPanel = (
    <Stack>
      <Text type="tertiary">这里展示 Runtime 实际消费的同一份 Workflow 定义。普通配置优先使用可视化编辑器。</Text>
      <TextArea autosize={{ minRows: 18, maxRows: 32 }} value={rawJson} onChange={setRawJson} />
      <Button onClick={applyJson}>应用 JSON 到草稿</Button>
    </Stack>
  );

  const testPanel = (
    <Stack>
      <Text strong>发布前检查</Text>
      {validateDefinition(normalizeDefinition(definition, projectId)).length === 0
        ? <Banner type="success" description="Graph 结构和编辑器级执行模式检查通过。" />
        : <Banner type="warning" description={validateDefinition(normalizeDefinition(definition, projectId))[0]} />}
      <Button onClick={() => void validateSaved()} disabled={!definition.version} loading={operating}>
        {String(definition.lifecycle || '').toUpperCase() === 'DRAFT' ? '校验已保存版本' : '检查当前版本'}
      </Button>
      <WorkflowManualTestButton projectId={projectId} definition={definition} disabled={operating} onNavigate={navigate} />
      <Space wrap><Tag>{definition.nodes?.length || 0} 个节点</Tag><Tag>{definition.edges?.length || 0} 条连接</Tag>
        {unique((definition.nodes || []).filter((node) => node.type === 'AGENT').map((node) => normalizeMode(node.mode || node.config?.mode).toUpperCase()))
          .map((executionMode) => <Tag key={executionMode}>{executionMode}</Tag>)}
      </Space>
      {mode === 'advanced' && <JsonBox>{JSON.stringify({
        definitionKind: 'SPECIALIZED_WORKFLOW',
        invocation: 'MANUAL_ONLY',
        nodeExecutionModes: unique((definition.nodes || []).filter((node) => node.type === 'AGENT').map((node) => normalizeMode(node.mode || node.config?.mode).toUpperCase())),
        nodes: definition.nodes?.length || 0,
        edges: definition.edges?.length || 0,
      }, null, 2)}</JsonBox>}
    </Stack>
  );

  if (!projectId) {
    return (
      <OpsPageShell selectedKey="workflows" maxWidth="none" padding="0">
        <Editor>
          <EditorToolbar>
            <EditorIdentity><span className="eyebrow">WORKFLOW EDITOR</span><strong>工作流编辑器</strong></EditorIdentity>
            <Button icon={<IconArrowLeft />} onClick={() => navigate('/workflows')}>返回工作流</Button>
          </EditorToolbar>
          <div style={{ padding: 20 }}><Banner type="warning" description="请先在工作流页面选择项目，再打开编辑器。" /></div>
        </Editor>
      </OpsPageShell>
    );
  }

  return (
    <OpsPageShell selectedKey="workflows" maxWidth="none" padding="0">
      <Editor>
        <EditorToolbar>
          <EditorIdentity>
            <span className="eyebrow">WORKFLOW EDITOR</span>
            <strong>{definitionReady ? definition.name || '新建工作流' : editorQuery.isError ? '工作流加载失败' : '正在加载工作流…'}</strong>
          </EditorIdentity>
          <Space wrap>
            <Button icon={<IconArrowLeft />} onClick={() => navigate(`/workflows?projectId=${encodeURIComponent(projectId)}`)}>返回</Button>
            <Button icon={<IconRefresh />} onClick={() => void loadDefinition()} loading={loading}>重新加载</Button>
            <Button icon={<IconSave />} onClick={() => void saveDraft()} loading={operating} disabled={loading || !definitionReady}>保存草稿</Button>
            <Button type="primary" onClick={() => void publish()} loading={operating} disabled={loading || !definitionReady}>校验并发布</Button>
          </Space>
        </EditorToolbar>

        {editorQuery.isError ? <div style={{ padding: 24 }}><Banner type="danger" description={userFacingError(editorQuery.error, '加载工作流失败，请点击重新加载。')} /></div>
          : !definitionReady || loading ? <div role="status" style={{ padding: 48, textAlign: 'center' }}><Spin /><Paragraph>正在读取工作流及其项目能力…</Paragraph></div>
          : <>
        <DefinitionBar>
          <Field>
            <Text strong>工作流名称</Text>
            <Input value={definition.name || ''} onChange={(name) => updateDefinition({ name })} />
          </Field>
          <Field>
            <Text strong>说明</Text>
            <Input value={definition.description || ''} onChange={(description) => updateDefinition({ description })} />
          </Field>
          <Space wrap style={{ justifyContent: 'flex-end' }}>
            <AgentBuilderModeSwitch
              value={mode}
              onChange={(nextMode) => {
                setMode(nextMode);
                setActivePanel((panel) => agentBuilderPanelForMode(nextMode, panel));
              }}
            />
            <Tag color={definition.lifecycle === 'PUBLISHED' ? 'green' : 'blue'}>{definition.lifecycle === 'PUBLISHED' ? '已发布' : definition.lifecycle === 'DISABLED' ? '已停用' : '草稿'}</Tag>
            {definition.version && <Tag>v{definition.version}</Tag>}
          </Space>
        </DefinitionBar>

        <EditorBody>
          <CanvasColumn>
            <GuideSlot><AgentBuilderGuide /></GuideSlot>
            <CanvasSurface>
              <OpsAgentCanvas
                definition={definition}
                selectedNodeId={selectedNodeId}
                selectedEdgeKey={selectedEdgeKey}
                height="100%"
                onSelectNode={(nodeId) => {
                  setSelectedNodeId(nodeId);
                  setSelectedEdgeKey('');
                  setActivePanel('node');
                }}
                onSelectEdge={(key) => {
                  setSelectedEdgeKey(key);
                  if (mode === 'advanced') setActivePanel('edge');
                }}
                onCanvasClick={() => {
                  setSelectedNodeId('');
                  setSelectedEdgeKey('');
                }}
                onMoveNode={moveNode}
                onConnectEdge={connectEdge}
                onCreateNode={addNode}
              />
            </CanvasSurface>
          </CanvasColumn>

          <InspectorRail>
            {publishImpact && <div style={{ marginBottom: 12 }}>{publishImpact}</div>}
            <MetadataDetails>
              <summary>目录信息与适用范围</summary>
              <div>
                <FieldGrid>
                  <Field>
                    <Text strong>工作流 ID</Text>
                    <Input value={definition.agentId} disabled />
                  </Field>
                  <Field>
                    <Text strong>调用方式</Text>
                    <Input value="仅显式选择" disabled />
                  </Field>
                  <WideField>
                    <Text strong>适用场景</Text>
                    <TextArea value={listText(definition.whenToUse)} onChange={(value) => updateDefinition({ whenToUse: parseList(value) })} autosize={{ minRows: 2, maxRows: 5 }} />
                  </WideField>
                  <WideField>
                    <Text strong>不适用场景</Text>
                    <TextArea value={listText(definition.whenNotToUse)} onChange={(value) => updateDefinition({ whenNotToUse: parseList(value) })} autosize={{ minRows: 2, maxRows: 5 }} />
                  </WideField>
                </FieldGrid>
              </div>
            </MetadataDetails>
            <Inspector>
              <AgentBuilderPropertyPanel
                mode={mode}
                activePanel={activePanel}
                onPanelChange={setActivePanel}
                nodePanel={nodePanel}
                edgePanel={edgePanel}
                jsonPanel={jsonPanel}
                testPanel={testPanel}
              />
              <Divider margin="16px" />
              <AgentBuilderVersionSection
                versions={versions}
                loading={loading}
                operating={operating}
                onRefresh={() => void loadVersions(definition.agentId)}
                onAction={(action, version) => void handleVersionAction(action, version)}
              />
            </Inspector>
          </InspectorRail>
        </EditorBody>
        </>}
      </Editor>
    </OpsPageShell>
  );
};
