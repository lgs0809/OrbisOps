import React, { memo, useCallback, useEffect, useMemo, useRef } from 'react';
import styled from 'styled-components';
import { Tag, Typography } from '@douyinfe/semi-ui';
import {
  IconArticle,
  IconBranch,
  IconCode,
  IconCommand,
  IconFile,
  IconFlowChartStroked,
  IconHistogram,
  IconKey,
  IconLink,
  IconPlay,
  IconPlayCircle,
  IconPulse,
  IconRoute,
  IconSearch,
  IconSend,
  IconServer,
  IconServerStroked,
  IconSetting,
  IconStop,
} from '@douyinfe/semi-icons';
import {
  Background,
  BackgroundVariant,
  BaseEdge,
  Controls,
  EdgeLabelRenderer,
  Handle,
  MarkerType,
  MiniMap,
  Position,
  ReactFlow,
  ReactFlowProvider,
  getBezierPath,
  useEdgesState,
  useNodesState,
  useReactFlow,
  type Connection,
  type Edge,
  type EdgeProps,
  type Node,
  type NodeProps,
} from '@xyflow/react';
import '@xyflow/react/dist/style.css';

import { theme } from '../styles/theme';
import {
  OpsAgentDefinition,
  OpsGraphEdge,
  OpsRuntimeEvent,
  OpsWorkflowNode,
} from '../services/ops-admin-service';

const { Text } = Typography;

type PositionValue = { x: number; y: number };

type OpsFlowNodeData = Record<string, unknown> & {
  label: string;
  agent?: string;
  description?: string;
  nodeType: string;
  mode?: string;
  visualKind: string;
  status?: string;
  isStart?: boolean;
  ragEnabled?: boolean;
  mcpEnabled?: boolean;
  roleLabel?: string;
};
type OpsFlowNodeType = Node<OpsFlowNodeData, 'opsAgent'>;
type OpsFlowEdgeData = {
  conditionType?: string;
  condition?: string;
  description?: string;
  feedback?: boolean;
};
type OpsFlowEdge = Edge<OpsFlowEdgeData>;

interface Props {
  definition: OpsAgentDefinition | null;
  selectedNodeId?: string;
  selectedEdgeKey?: string;
  runtimeEvents?: OpsRuntimeEvent[];
  height?: string;
  onSelectNode?: (nodeId: string) => void;
  onSelectEdge?: (edgeKey: string) => void;
  onCanvasClick?: () => void;
  onMoveNode?: (nodeId: string, position: PositionValue) => void;
  onConnectEdge?: (edge: OpsGraphEdge) => void;
  onCreateNode?: (node: OpsWorkflowNode, edge?: OpsGraphEdge) => void;
}

const CanvasShell = styled.div<{ $height?: string }>`
  display: flex;
  flex-direction: column;
  height: ${(props) => props.$height || '520px'};
  min-height: ${(props) => props.$height === '100%' ? '0' : '460px'};
  border: 1px solid ${theme.colors.border.secondary};
  background: ${theme.colors.bg.secondary};

  .react-flow__controls {
    border: 1px solid ${theme.colors.border.secondary};
    border-radius: ${theme.borderRadius.base};
    overflow: hidden;
    box-shadow: ${theme.shadows.sm};
  }

  .react-flow__minimap {
    border: 1px solid ${theme.colors.border.secondary};
    border-radius: ${theme.borderRadius.base};
    overflow: hidden;
    box-shadow: ${theme.shadows.sm};
  }

  @media (max-width: 760px), (max-height: 800px) {
    .react-flow__minimap {
      display: none;
    }
  }

  @media (max-width: 760px) {
    .react-flow__minimap {
      width: 110px;
      height: 70px;
    }

    .react-flow__minimap svg {
      width: 100%;
      height: 100%;
    }
  }
`;

const Palette = styled.div`
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  flex-shrink: 0;
  gap: 6px;
  padding: 8px;
  border: 1px solid ${theme.colors.border.secondary};
  border-radius: 0;
  background: rgba(255, 255, 255, 0.96);

  @media (max-width: 760px) {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 4px;
    padding: 6px;
  }
`;

const PaletteItem = styled.button`
  display: inline-flex;
  align-items: center;
  gap: 6px;
  width: 116px;
  height: 32px;
  border: 1px solid ${theme.colors.border.secondary};
  border-radius: ${theme.borderRadius.base};
  background: ${theme.colors.bg.primary};
  color: ${theme.colors.text.primary};
  cursor: grab;
  font-size: 12px;
  text-align: left;

  &:hover {
    border-color: ${theme.colors.primary};
    color: ${theme.colors.primary};
  }

  &:active {
    cursor: grabbing;
  }

  @media (max-width: 760px) {
    width: 100%;
  }
`;

const PaletteGroupTitle = styled.div`
  grid-column: 1 / -1;
  margin: 4px 2px 0;
  color: ${theme.colors.text.tertiary};
  font-size: 11px;
  font-weight: ${theme.typography.fontWeight.semibold};
`;

const NodeCard = styled.div<{ $engine: string; $selected?: boolean; $status?: string }>`
  min-width: 176px;
  max-width: 230px;
  border: 1px solid
    ${(props) =>
      props.$selected
        ? theme.colors.primary
        : props.$status === 'RUNNING'
        ? theme.colors.primary
        : ['SUCCEEDED', 'FOUND'].includes(props.$status || '')
        ? theme.colors.success
        : ['FAILED', 'ERROR', 'BLOCKED'].includes(props.$status || '')
        ? theme.colors.error
        : props.$engine === 'AGENTSCOPE'
        ? '#7c3aed'
        : props.$engine === 'RAG'
        ? theme.colors.success
        : theme.colors.border.primary};
  border-radius: ${theme.borderRadius.base};
  background: ${(props) =>
    props.$engine === 'AGENTSCOPE'
      ? '#faf5ff'
      : props.$engine === 'RAG'
      ? '#ecfdf5'
      : props.$engine === 'PROMETHEUS'
      ? '#eff6ff'
      : props.$engine === 'MYSQL_SLOW_SQL'
      ? '#fffbeb'
      : theme.colors.bg.primary};
  box-shadow: ${(props) =>
    props.$selected ? '0 10px 24px rgba(37, 99, 235, 0.18)' : theme.shadows.sm};
  overflow: hidden;
`;

const NodeHeader = styled.div<{ $engine: string }>`
  display: flex;
  align-items: center;
  gap: ${theme.spacing.sm};
  padding: 8px 10px;
  color: #fff;
  background: ${(props) =>
    props.$engine === 'AGENTSCOPE'
      ? '#7c3aed'
      : props.$engine === 'RAG'
      ? theme.colors.success
      : props.$engine === 'PROMETHEUS'
      ? theme.colors.primary
      : props.$engine === 'MYSQL_SLOW_SQL'
      ? theme.colors.warning
      : '#334155'};
`;

const NodeBody = styled.div`
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 10px;
`;

const NodeDescription = styled.div`
  color: ${theme.colors.text.secondary};
  font-size: 12px;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
`;

const StartBadge = styled.span`
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 6px;
  border-radius: 999px;
  color: #047857;
  background: #d1fae5;
  font-size: 11px;
  font-weight: ${theme.typography.fontWeight.semibold};
`;

const edgeKeyOf = (edge: OpsGraphEdge, index: number) =>
  `${edge.edgeId || `${edge.from}->${edge.to}`}:${edge.conditionType || 'always'}:${edge.condition || 'always'}:${index}`;

const eventStatusByNode = (events?: OpsRuntimeEvent[]) => {
  const map = new Map<string, string>();
  (events || []).forEach((event) => {
    const key = event.nodeId || event.agent;
    if (key && event.status) {
      map.set(key, event.status);
    }
  });
  return map;
};

const normalized = (value?: string) => (value || '').trim().toUpperCase();

const agentMode = (node: OpsWorkflowNode) => {
  const configured = String(node.mode || node.config?.mode || node.config?.agentMode || '').trim().toLowerCase();
  if (['direct', 'llm', 'react'].includes(configured)) {
    return configured;
  }
  if (configured === 'review' || configured === 'auto' || configured === 'plan') return 'llm';
  const type = normalized(node.type);
  if (type === 'REVIEW' || type === 'REFLECT') return 'llm';
  if (['SUB_AGENT', 'EXECUTE', 'AGENTSCOPE', 'RAG', 'MCP', 'TOOL_CALL', 'KNOWLEDGE_RETRIEVAL'].includes(type)) return 'react';
  return 'llm';
};

const roleMeta = (node: OpsWorkflowNode) => {
  const role = String(node.config?.role || '').trim().toLowerCase();
  if (role === 'reviewer') return { label: 'Reviewer' };
  if (role === 'reporter') return { label: 'Report' };
  if (role === 'extractor') return { label: 'Extract' };
  if (role === 'classifier') return { label: 'Classify' };
  return undefined;
};

const nodeVisualKind = (node: OpsWorkflowNode) => {
  if (node.type === 'START') return 'START';
  if (node.type === 'END') return 'END';
  if (normalized(node.type) === 'ROUTER') return 'ROUTER';
  if (normalized(node.type) === 'SUB_WORKFLOW') return 'SUB_WORKFLOW';
  if (normalized(node.type) === 'HUMAN_APPROVAL') return 'HUMAN_APPROVAL';
  const mode = agentMode(node);
  const abilityText = normalized([
    node.nodeId,
    node.agent,
    node.description,
    node.instruction,
    ...(node.mcpIds || []),
    ...(node.skills || []),
  ].filter(Boolean).join(' '));
  if (mode === 'react') {
    if (node.ragEnabled) return 'RAG';
    if (abilityText.includes('PROMETHEUS')) return 'PROMETHEUS';
    if (abilityText.includes('ELASTICSEARCH') || abilityText.includes('ES_LOG') || abilityText.includes('ELK')) return 'ELASTICSEARCH';
    if (abilityText.includes('MYSQL') || abilityText.includes('SLOW_SQL')) return 'MYSQL_SLOW_SQL';
    if (abilityText.includes('REDIS') || abilityText.includes('CACHE')) return 'REDIS';
    if (node.mcpIds?.length || node.mcpServers?.length) return 'MCP';
    return 'REACT';
  }
  if (abilityText.includes('NOTIFICATION') || abilityText.includes('CHANNEL')) return 'NOTIFY';
  if (abilityText.includes('REPORT')) return 'REPORT';
  if (node.ragEnabled || node.type === 'RAG') return 'RAG';
  return 'AGENT';
};

const iconByEngine = (engine: string) => {
  if (engine === 'START') return IconPlayCircle;
  if (engine === 'END') return IconStop;
  if (engine === 'ROUTER') return IconRoute;
  if (engine === 'SUB_WORKFLOW') return IconBranch;
  if (engine === 'HUMAN_APPROVAL') return IconKey;
  if (engine === 'PLAN') return IconFlowChartStroked;
  if (engine === 'REVIEW') return IconPulse;
  if (engine === 'REACT') return IconCommand;
  if (engine === 'REPORT') return IconArticle;
  if (engine === 'NOTIFY') return IconSend;
  if (engine === 'TOOL_CALL') return IconLink;
  if (engine === 'KNOWLEDGE_RETRIEVAL') return IconFile;
  if (engine === 'HTTP' || engine === 'CODE' || engine === 'TEMPLATE' || engine === 'VARIABLE_MERGE' || engine === 'SUB_WORKFLOW') return IconSetting;
  if (engine === 'RAG') return IconFile;
  if (engine === 'ES' || engine === 'ELASTICSEARCH') return IconSearch;
  if (engine === 'PROMETHEUS') return IconHistogram;
  if (engine === 'MYSQL_SLOW_SQL') return IconServerStroked;
  if (engine === 'REDIS') return IconKey;
  if (engine === 'AGENTSCOPE') return IconBranch;
  if (engine === 'MCP') return IconServer;
  if (engine === 'REFLECT') return IconPulse;
  if (engine === 'AGENT') return IconCommand;
  return IconCode;
};

const nodeTagColor = (engine: string) => {
  if (engine === 'START' || engine === 'END') return 'grey';
  if (engine === 'PLAN') return 'blue';
  if (engine === 'REACT') return 'purple';
  if (engine === 'AGENTSCOPE') return 'purple';
  if (engine === 'ROUTER') return 'blue';
  if (engine === 'REVIEW') return 'pink';
  if (engine === 'NOTIFY') return 'cyan';
  if (engine === 'RAG') return 'green';
  if (engine === 'MYSQL_SLOW_SQL') return 'orange';
  if (engine === 'PROMETHEUS') return 'blue';
  if (engine === 'REDIS') return 'red';
  if (engine === 'ELASTICSEARCH' || engine === 'ES') return 'cyan';
  return 'blue';
};

const paletteItems = [
  { type: 'START', label: 'Start', icon: IconPlayCircle },
  { type: 'AGENT', label: 'Agent Node', icon: IconCommand },
  { type: 'ROUTER', label: 'Router', icon: IconRoute },
  { type: 'SUB_WORKFLOW', label: '子 Workflow', icon: IconBranch },
  { type: 'HUMAN_APPROVAL', label: '人工审批', icon: IconKey },
  { type: 'END', label: 'End', icon: IconStop },
];

const createNodeByType = (type: string, position: PositionValue): OpsWorkflowNode => {
  const nodeId = `${type.toLowerCase()}_${Date.now().toString(36)}`;
  if (type === 'START') {
    return {
      nodeId,
      type: 'START',
      agent: 'start',
      description: 'Workflow 起始节点；输入契约可选。',
      instruction: '存在显式输入时向下游传递；定时或无输入 Run 可以直接从后续 Agent Prompt 与 Runtime 上下文开始。',
      outputKey: 'input',
      config: { position, inputKeys: [] },
    };
  }
  if (type === 'END') {
    return {
      nodeId,
      type: 'END',
      agent: 'end',
      description: 'Workflow 结束节点；输出契约可选。',
      instruction: '存在显式契约时输出配置字段；未定义契约时使用最终 Graph 输出。',
      outputKey: 'output',
      config: { position, outputKeys: [] },
    };
  }
  if (type === 'AGENT') {
    return {
      nodeId,
      type: 'AGENT',
      mode: 'llm',
      agent: nodeId,
      description: '可配置 Agent Node',
      instruction: '使用绑定到当前 Agent Node 的模型、RAG、Skill 和 MCP 能力完成节点职责。',
      outputKey: `${nodeId}_result`,
      config: { position, mode: 'llm', role: 'general', contextInputs: ['query', 'upstreamOutputs'] },
    };
  }
  if (type === 'ROUTER') {
    return {
      nodeId,
      type: 'ROUTER',
      agent: nodeId,
      description: 'Router 节点',
      instruction: '读取上游结构化输出或 Graph State，并根据路由条件选择一个或多个下游节点。',
      outputKey: 'selectedRoutes',
      config: { position, routeMode: 'multi', inputKey: 'selectedRoutes' },
    };
  }
  if (type === 'SUB_WORKFLOW') {
    return {
      nodeId,
      type: 'SUB_WORKFLOW',
      agent: '',
      description: '复用当前 Project 中已发布的 Workflow。',
      outputKey: `${nodeId}_result`,
      config: { position, versionPolicy: 'LATEST_PUBLISHED', inputKey: '' },
    };
  }
  if (type === 'HUMAN_APPROVAL') {
    return { nodeId, type: 'HUMAN_APPROVAL', description: '等待人工确认后继续。',
      instruction: '请审核当前步骤的证据和操作范围。', outputKey: `${nodeId}_result`,
      config: { position, timeoutSeconds: 1800 } };
  }
  return {
    nodeId,
    type: 'AGENT',
    mode: 'llm',
    agent: nodeId,
    description: 'General LLM Agent Node',
    instruction: 'Complete this Agent Node responsibility using the upstream input.',
    outputKey: `${nodeId}_result`,
    config: { position, mode: 'llm' },
  };
};

const autoPosition = (nodes: OpsWorkflowNode[], edges: OpsGraphEdge[]) => {
  const depth = new Map<string, number>();
  const visiting = new Set<string>();
  const layoutEdges = edges.filter((edge) => !edge.feedback);
  const visit = (nodeId: string): number => {
    if (depth.has(nodeId)) return depth.get(nodeId)!;
    if (visiting.has(nodeId)) return 0;
    visiting.add(nodeId);
    const parents = layoutEdges.filter((edge) => edge.to === nodeId).map((edge) => edge.from);
    const value = parents.length ? Math.max(...parents.map(visit)) + 1 : 0;
    visiting.delete(nodeId);
    depth.set(nodeId, value);
    return value;
  };
  nodes.forEach((node) => visit(node.nodeId));
  const groups = new Map<number, OpsWorkflowNode[]>();
  nodes.forEach((node) => {
    const level = depth.get(node.nodeId) || 0;
    groups.set(level, [...(groups.get(level) || []), node]);
  });
  const positions = new Map<string, PositionValue>();
  Array.from(groups.entries()).forEach(([level, group]) => {
    group.forEach((node, index) => {
      const configured = node.config?.position;
      positions.set(node.nodeId, {
        x: typeof configured?.x === 'number' ? configured.x : 80 + level * 280,
        y: typeof configured?.y === 'number' ? configured.y : 90 + index * 170,
      });
    });
  });
  return positions;
};

function OpsFlowNode({ id, data, selected }: NodeProps<OpsFlowNodeType>) {
  const nodeData = data;
  const visualKind = nodeData.visualKind || nodeData.nodeType || 'CHAT';
  const Icon = iconByEngine(visualKind);
  const status = nodeData.status;

  return (
    <NodeCard $engine={visualKind} $selected={selected} $status={status}>
      <Handle
        type="target"
        position={Position.Top}
        style={{ width: 10, height: 10, border: '2px solid #fff', background: '#64748b' }}
      />
      <NodeHeader $engine={visualKind}>
        <Icon />
        <Text strong ellipsis={{ showTooltip: true }} style={{ maxWidth: 152, color: '#fff' }}>
          {nodeData.label || id}
        </Text>
      </NodeHeader>
      <NodeBody>
        <div style={{ display: 'flex', alignItems: 'center', gap: 6, flexWrap: 'wrap' }}>
          <Tag color={nodeTagColor(nodeData.nodeType)}>{nodeData.nodeType}</Tag>
          {nodeData.mode && nodeData.nodeType === 'AGENT' && <Tag color={nodeTagColor(visualKind)}>{nodeData.mode}</Tag>}
          {nodeData.isStart && (
            <StartBadge>
              <IconPlay />
              START
            </StartBadge>
          )}
          {status && <Tag color={status === 'FAILED' ? 'red' : status === 'RUNNING' ? 'blue' : 'green'}>{status}</Tag>}
          {nodeData.ragEnabled && <Tag color="green">RAG</Tag>}
          {nodeData.mcpEnabled && <Tag color="cyan">MCP</Tag>}
          {nodeData.roleLabel && (
            <Tag color={nodeTagColor(visualKind)}>{nodeData.roleLabel}</Tag>
          )}
        </div>
        <Text type="tertiary" size="small" ellipsis={{ showTooltip: true }}>
          {nodeData.agent || '-'}
        </Text>
        <NodeDescription>{nodeData.description || '-'}</NodeDescription>
      </NodeBody>
      <Handle
        type="source"
        position={Position.Bottom}
        style={{ width: 10, height: 10, border: '2px solid #fff', background: '#64748b' }}
      />
    </NodeCard>
  );
}

function OpsDefaultEdge(props: EdgeProps<OpsFlowEdge>) {
  const [edgePath] = getBezierPath(props);
  return (
    <BaseEdge
      id={props.id}
      path={edgePath}
      markerEnd={props.markerEnd}
      style={{ stroke: props.selected ? theme.colors.primary : '#94a3b8', strokeWidth: props.selected ? 2.4 : 1.6 }}
    />
  );
}

function OpsConditionEdge(props: EdgeProps<OpsFlowEdge>) {
  const condition = props.data?.condition;
  const conditionType = props.data?.conditionType;
  const feedback = props.data?.feedback;
  const [edgePath, labelX, labelY] = getBezierPath(props);
  const label = [conditionType && conditionType !== 'always' ? conditionType : '', condition].filter(Boolean).join(': ');
  return (
    <>
      <BaseEdge
        id={props.id}
        path={edgePath}
        markerEnd={props.markerEnd}
        style={{
          stroke: feedback ? '#9333ea' : props.selected ? theme.colors.primary : '#94a3b8',
          strokeWidth: props.selected || feedback ? 2.4 : 1.6,
          strokeDasharray: feedback ? '6 4' : undefined,
        }}
      />
      {label && (
        <EdgeLabelRenderer>
          <div
            style={{
              position: 'absolute',
              transform: `translate(-50%, -50%) translate(${labelX}px, ${labelY}px)`,
              padding: '2px 8px',
              border: `1px solid ${theme.colors.border.secondary}`,
              borderRadius: 6,
              background: 'rgba(255,255,255,0.96)',
              color: theme.colors.text.secondary,
              fontSize: 12,
              boxShadow: theme.shadows.sm,
              pointerEvents: 'all',
            }}
          >
            {feedback ? '↺ ' : ''}{label}
          </div>
        </EdgeLabelRenderer>
      )}
    </>
  );
}

const nodeTypes = {
  opsAgent: memo(OpsFlowNode),
};

const edgeTypes = {
  default: memo(OpsDefaultEdge),
  condition: memo(OpsConditionEdge),
};

const toFlowNodes = (
  definition: OpsAgentDefinition | null,
  statuses: Map<string, string>,
): OpsFlowNodeType[] => {
  const nodes = definition?.nodes || [];
  const edges = definition?.edges || [];
  const positions = autoPosition(nodes, edges);
  return nodes.map((node) => {
    const visualKind = nodeVisualKind(node);
    const rawType = normalized(node.type || 'AGENT');
    const nodeType = ['START', 'END', 'ROUTER', 'SUB_WORKFLOW', 'HUMAN_APPROVAL'].includes(rawType) ? rawType : 'AGENT';
    const role = roleMeta(node);
    return {
      id: node.nodeId,
      type: 'opsAgent',
      position: positions.get(node.nodeId) || { x: 80, y: 80 },
      data: {
        label: node.nodeId,
        agent: node.agent || node.nodeId,
        description: node.description || node.instruction,
        nodeType,
        mode: nodeType === 'AGENT' ? agentMode(node) : undefined,
        visualKind,
        status: statuses.get(node.nodeId) || statuses.get(node.agent || ''),
        isStart: definition?.startNodeId === node.nodeId,
        ragEnabled: Boolean(node.ragEnabled),
        mcpEnabled: Boolean(node.mcpIds?.length || node.mcpServers?.length),
        roleLabel: role?.label,
      },
    };
  });
};

const toFlowEdges = (definition: OpsAgentDefinition | null): OpsFlowEdge[] =>
  (definition?.edges || []).map((edge, index) => {
    const condition = edge.condition || 'always';
    return {
      id: edgeKeyOf(edge, index),
      source: edge.from,
      target: edge.to,
      type: condition && condition !== 'always' ? 'condition' : 'default',
      data: { condition, conditionType: edge.conditionType || 'always', description: edge.description, feedback: Boolean(edge.feedback) },
      markerEnd: { type: MarkerType.ArrowClosed, color: '#94a3b8' },
    };
  });

function OpsAgentFlowCanvas({
  definition,
  selectedNodeId,
  selectedEdgeKey,
  runtimeEvents,
  height,
  onSelectNode,
  onSelectEdge,
  onCanvasClick,
  onMoveNode,
  onConnectEdge,
  onCreateNode,
}: Props) {
  const statuses = useMemo(() => eventStatusByNode(runtimeEvents), [runtimeEvents]);
  const flowNodes = useMemo(() => toFlowNodes(definition, statuses), [definition, statuses]);
  const flowEdges = useMemo(() => toFlowEdges(definition), [definition]);
  const [nodes, setNodes, onNodesChange] = useNodesState<OpsFlowNodeType>(flowNodes);
  const [edges, setEdges, onEdgesChange] = useEdgesState<OpsFlowEdge>(flowEdges);
  const { screenToFlowPosition } = useReactFlow();
  const connectingFrom = useRef<string | null>(null);
  const connectedRef = useRef(false);

  useEffect(() => {
    setNodes(flowNodes.map((node) => ({ ...node, selected: node.id === selectedNodeId })));
  }, [flowNodes, selectedNodeId, setNodes]);

  useEffect(() => {
    setEdges(flowEdges.map((edge) => ({ ...edge, selected: edge.id === selectedEdgeKey })));
  }, [flowEdges, selectedEdgeKey, setEdges]);

  const handleConnect = useCallback(
    (connection: Connection) => {
      connectedRef.current = true;
      if (!connection.source || !connection.target) return;
      onConnectEdge?.({
        edgeId: `${connection.source}->${connection.target}`,
        from: connection.source,
        to: connection.target,
        conditionType: 'always',
        condition: 'always',
      });
    },
    [onConnectEdge],
  );

  const handleConnectStart = useCallback((_event: MouseEvent | TouchEvent, params: { nodeId: string | null; handleType: string | null }) => {
    connectedRef.current = false;
    connectingFrom.current = params.handleType === 'source' ? params.nodeId : null;
  }, []);

  const handleConnectEnd = useCallback(
    (event: MouseEvent | TouchEvent) => {
      const sourceNodeId = connectingFrom.current;
      connectingFrom.current = null;
      if (!sourceNodeId || connectedRef.current) {
        connectedRef.current = false;
        return;
      }
      const point =
        'changedTouches' in event && event.changedTouches.length > 0
          ? { x: event.changedTouches[0].clientX, y: event.changedTouches[0].clientY }
          : { x: (event as MouseEvent).clientX, y: (event as MouseEvent).clientY };
      const position = screenToFlowPosition(point);
      const nodeId = `agent_${Date.now().toString(36)}`;
      onCreateNode?.(
        {
          nodeId,
          type: 'AGENT',
          mode: 'react',
          agent: nodeId,
          description: '从画布连接创建的新 ReAct Agent Node',
          instruction: '描述这个 Agent Node 的职责、检索参数和输出格式。',
          outputKey: `${nodeId}_result`,
          config: { position, mode: 'react', role: 'data_agent', contextInputs: ['query', 'upstreamOutputs'] },
        },
        {
          edgeId: `${sourceNodeId}->${nodeId}`,
          from: sourceNodeId,
          to: nodeId,
          conditionType: 'always',
          condition: 'always',
        },
      );
    },
    [onCreateNode, screenToFlowPosition],
  );

  const handleDragOver = useCallback((event: React.DragEvent) => {
    event.preventDefault();
    event.dataTransfer.dropEffect = 'move';
  }, []);

  const handleDrop = useCallback(
    (event: React.DragEvent) => {
      event.preventDefault();
      const type = event.dataTransfer.getData('application/ops-agent-node');
      if (!type) {
        return;
      }
      const position = screenToFlowPosition({ x: event.clientX, y: event.clientY });
      onCreateNode?.(createNodeByType(type, position));
    },
    [onCreateNode, screenToFlowPosition],
  );

  return (
    <CanvasShell $height={height}>
      <Palette aria-label="节点工具">
        <PaletteGroupTitle>核心节点</PaletteGroupTitle>
        {paletteItems.map((item) => {
          const Icon = item.icon;
          return (
            <PaletteItem
              key={item.type}
              type="button"
              draggable
              onDragStart={(event) => {
                event.dataTransfer.setData('application/ops-agent-node', item.type);
                event.dataTransfer.effectAllowed = 'move';
              }}
            >
              <Icon />
              {item.label}
            </PaletteItem>
          );
        })}
      </Palette>
      <ReactFlow<OpsFlowNodeType, OpsFlowEdge>
        style={{ height: 'auto', flex: 1, minHeight: 0 }}
        nodes={nodes}
        edges={edges}
        nodeTypes={nodeTypes}
        edgeTypes={edgeTypes}
        onNodesChange={onNodesChange}
        onEdgesChange={onEdgesChange}
        onConnect={handleConnect}
        onConnectStart={handleConnectStart}
        onConnectEnd={handleConnectEnd}
        onNodeClick={(_event, node) => onSelectNode?.(node.id)}
        onEdgeClick={(event, edge) => {
          event.stopPropagation();
          onSelectEdge?.(String(edge.id));
        }}
        onPaneClick={() => onCanvasClick?.()}
        onNodeDragStop={(_event, node) => onMoveNode?.(node.id, node.position)}
        onDragOver={handleDragOver}
        onDrop={handleDrop}
        zoomOnScroll={false}
        panOnScroll={false}
        preventScrolling={false}
        minZoom={0.1}
        fitView
        fitViewOptions={{ padding: 0.25 }}
        defaultEdgeOptions={{
          markerEnd: { type: MarkerType.ArrowClosed, color: '#94a3b8' },
        }}
      >
        <Background variant={BackgroundVariant.Dots} gap={18} size={1.2} color="#cbd5e1" />
        <Controls showInteractive={false} />
        <MiniMap zoomable pannable nodeStrokeWidth={3} />
      </ReactFlow>
    </CanvasShell>
  );
}

export const OpsAgentCanvas: React.FC<Props> = (props) => (
  <ReactFlowProvider>
    <OpsAgentFlowCanvas {...props} />
  </ReactFlowProvider>
);
