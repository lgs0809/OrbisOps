import { opsAdminService } from './ops-admin-service';
import { opsChannelService } from './ops-channel-service';
import { taskScheduleAdminService } from './task-schedule-admin-service';

export type AgentReferenceKind = 'SCHEDULE' | 'ALERT' | 'CHANNEL';
export type AgentBindingMode = 'LATEST_PUBLISHED' | 'PINNED_VERSION';

export interface AgentReferenceItem {
  kind: AgentReferenceKind;
  id: string;
  name: string;
  bindingMode: AgentBindingMode;
  version?: number;
  active: boolean;
}

export interface AgentReferenceImpact {
  agentId: string;
  scheduleCount: number;
  alertCount: number;
  channelCount: number;
  totalCount: number;
  latestPublishedCount: number;
  pinnedVersionCount: number;
  references: AgentReferenceItem[];
}

const emptyImpact = (agentId: string): AgentReferenceImpact => ({
  agentId,
  scheduleCount: 0,
  alertCount: 0,
  channelCount: 0,
  totalCount: 0,
  latestPublishedCount: 0,
  pinnedVersionCount: 0,
  references: [],
});

const normalizeBindingMode = (value?: string): AgentBindingMode =>
  String(value || '').toUpperCase() === 'PINNED_VERSION' ? 'PINNED_VERSION' : 'LATEST_PUBLISHED';

export const loadAgentReferenceImpacts = async (
  projectId: string,
  agentIds: string[],
): Promise<Record<string, AgentReferenceImpact>> => {
  const ids = Array.from(new Set(agentIds.map((item) => item.trim()).filter(Boolean)));
  const impacts = Object.fromEntries(ids.map((agentId) => [agentId, emptyImpact(agentId)]));
  if (!projectId || ids.length === 0) return impacts;

  const [scheduleResponse, alertResponse, channelResponse] = await Promise.all([
    taskScheduleAdminService.list(projectId).catch(() => ({ data: [] })),
    opsAdminService.listAlertTriggerRules().catch(() => ({ data: [] })),
    opsChannelService.list(projectId).catch(() => ({ data: [] })),
  ]);

  const append = (agentId: string, reference: AgentReferenceItem) => {
    const impact = impacts[agentId];
    if (!impact) return;
    impact.references.push(reference);
    impact.totalCount += 1;
    if (reference.kind === 'SCHEDULE') impact.scheduleCount += 1;
    if (reference.kind === 'ALERT') impact.alertCount += 1;
    if (reference.kind === 'CHANNEL') impact.channelCount += 1;
    if (reference.bindingMode === 'PINNED_VERSION') impact.pinnedVersionCount += 1;
    else impact.latestPublishedCount += 1;
  };

  for (const schedule of scheduleResponse.data || []) {
    const agentId = String(schedule.agentId || '').trim();
    append(agentId, {
      kind: 'SCHEDULE',
      id: String(schedule.id),
      name: schedule.taskName || `Schedule ${schedule.id}`,
      bindingMode: normalizeBindingMode(schedule.agentBindingMode),
      version: schedule.agentVersion,
      active: Number(schedule.status) === 1,
    });
  }

  for (const alert of alertResponse.data || []) {
    if (String(alert.projectId || '').trim() !== projectId) continue;
    const agentId = String(alert.agentDefinitionId || '').trim();
    append(agentId, {
      kind: 'ALERT',
      id: String(alert.id || alert.ruleName),
      name: alert.ruleName || String(alert.id || 'Alert'),
      bindingMode: normalizeBindingMode(alert.agentBindingMode),
      version: alert.agentVersion,
      active: Number(alert.status ?? 1) === 1,
    });
  }

  for (const channel of channelResponse.data || []) {
    if (channel.executionType !== 'WORKFLOW') continue;
    const workflowId = String(channel.workflowId || '').trim();
    append(workflowId, {
      kind: 'CHANNEL',
      id: channel.channelId,
      name: channel.name || channel.channelId,
      bindingMode: normalizeBindingMode(channel.workflowVersionPolicy),
      version: channel.workflowVersion,
      active: channel.status === 'ACTIVE',
    });
  }

  return impacts;
};
