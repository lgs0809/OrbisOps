import { APP_ROUTES, PLATFORM_NAVIGATION_GROUPS, type NavigationSubgroup } from '../../../app/navigation/route-metadata';

export const groupDescriptions: Record<NavigationSubgroup, string> = {
  integrations: '连接外部消息和协作平台，把渠道消息带入 OrbisOps。',
  intelligence: '接入和维护模型、知识库与技能，供项目按需使用。',
  'execution-governance': '维护工具、执行目标与安全策略，并控制它们可在哪些项目中使用。',
  advanced: '低频运行能力和内部观测配置，通常无需日常调整。',
};

export const routeDescriptions: Record<string, string> = {
  channels: '接入飞书、企业微信、钉钉、Slack、Telegram、Discord、QQ 等渠道。',
  models: '配置对话、向量化、重排服务商和默认模型。',
  tools: '维护可供项目和运行使用的 MCP / 工具目录。',
  knowledge: '管理知识库、文档入库、检索策略与质量验证。',
  skills: '管理可被项目、默认助手和工作流启用的技能。',
  'execution-targets': '配置受控落地、验证和回滚所使用的执行目标。',
  governance: '查看安全策略、审批边界和审计记录。',
  'model-catalog': '维护底层模型目录和服务商能力元数据。',
  memory: '配置并观察智能运行中的记忆行为。',
  'skill-evolver': '查看技能进化候选和受治理版本。',
  'tool-routing': '观察工具候选、路由与运行时选择证据。',
};

export const platformSettingsGroups = PLATFORM_NAVIGATION_GROUPS.map((group) => ({
  ...group,
  routes: APP_ROUTES
    .filter((route) => route.navigation?.section === 'platform' && route.navigation.subgroup === group.key)
    .sort((left, right) => (left.navigation?.order || 0) - (right.navigation?.order || 0)),
}));

export type SettingsCategory = 'account' | NavigationSubgroup;

export const settingsCategoryFrom = (value: string | null): SettingsCategory =>
  value === 'account' || platformSettingsGroups.some((group) => group.key === value)
    ? value as SettingsCategory : 'account';
