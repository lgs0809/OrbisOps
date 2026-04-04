import React from 'react';
import { Typography } from '@douyinfe/semi-ui';
import { IconChevronRight } from '@douyinfe/semi-icons';
import { useNavigate } from 'react-router-dom';
import styled from 'styled-components';

import {
  APP_ROUTES,
  PLATFORM_NAVIGATION_GROUPS,
  type NavigationSubgroup,
} from '../../../app/navigation/route-metadata';
import { OpsPageHeader, OpsPageShell, OpsSectionCard } from '../../../components/ops-layout';
import { theme } from '../../../styles/theme';

const GroupDescription = styled(Typography.Text)`
  display: block;
  padding: 0 ${theme.spacing.lg} ${theme.spacing.base};
  color: ${theme.colors.text.tertiary};
  line-height: 1.6;
`;

const SettingsGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: ${theme.spacing.base};
  padding: 0 ${theme.spacing.lg} ${theme.spacing.lg};
`;

const SettingCard = styled.button`
  display: flex;
  min-width: 0;
  align-items: flex-start;
  justify-content: space-between;
  gap: ${theme.spacing.base};
  padding: ${theme.spacing.base};
  border: 1px solid ${theme.colors.border.secondary};
  border-radius: ${theme.borderRadius.base};
  background: ${theme.colors.bg.primary};
  color: inherit;
  text-align: left;
  cursor: pointer;
  transition: border-color 0.15s ease, box-shadow 0.15s ease, transform 0.15s ease;

  &:hover {
    border-color: ${theme.colors.border.primary};
    box-shadow: ${theme.shadows.sm};
    transform: translateY(-1px);
  }

  &:focus-visible {
    outline: 2px solid ${theme.colors.primary};
    outline-offset: 2px;
  }
`;

const SettingBody = styled.div`
  min-width: 0;
`;

const SettingTitle = styled(Typography.Text)`
  display: block;
  color: ${theme.colors.text.primary};
  font-weight: ${theme.typography.fontWeight.semibold};
`;

const SettingDescription = styled(Typography.Text)`
  display: block;
  margin-top: ${theme.spacing.xs};
  color: ${theme.colors.text.tertiary};
  line-height: 1.55;
`;

const ScopeNotice = styled.div`
  padding: ${theme.spacing.base} ${theme.spacing.lg};
  border: 1px solid ${theme.colors.border.secondary};
  border-radius: ${theme.borderRadius.base};
  background: ${theme.colors.bg.secondary};
  color: ${theme.colors.text.secondary};
  line-height: 1.65;
`;

const groupDescriptions: Record<NavigationSubgroup, string> = {
  integrations: '连接外部协作入口。这里只配置平台级 Channel provider；具体项目是否允许使用，仍由项目授权决定。',
  intelligence: '管理模型、公共知识和可复用 Skills。平台提供能力目录，项目再显式选择和授权。',
  'execution-governance': '管理工具、生产执行目标和治理策略。高风险执行仍必须经过 ChangePackage、审批和 Landing 门禁。',
  advanced: '面向平台维护者的低频内部能力，包括模型目录、Memory、Skill Evolver、Tool Routing 与分析任务。',
};

const routeDescriptions: Record<string, string> = {
  channels: '配置飞书、企业微信、钉钉、Slack、Telegram、Discord、QQ 等 Channel provider。',
  models: '配置 Chat / Embedding / Rerank API 与模型连接。',
  tools: '维护 MCP / Tool 能力目录及平台级连接。',
  knowledge: '维护跨项目复用的公共知识库、入库任务、检索策略与质量评测。',
  skills: '维护平台级可复用 Skill，并由项目或 Agent 显式绑定。',
  'execution-targets': '维护 Landing / Verification / Rollback 所需的生产执行目标与适配器。',
  governance: '查看平台治理状态、审计与安全门禁。',
  'model-catalog': '维护低层模型目录与 Provider capability metadata。',
  memory: '维护 Agent Memory 的平台级策略与观测能力。',
  'skill-evolver': '管理 Skill 自进化流程、候选版本与治理。',
  'tool-routing': '观察工具路由、候选集与运行时选择证据。',
  'analysis-tasks': '查看平台内部分析与异步治理任务。',
};

export const platformSettingsGroups = PLATFORM_NAVIGATION_GROUPS.map((group) => ({
  ...group,
  routes: APP_ROUTES
    .filter((route) => route.navigation?.section === 'platform' && route.navigation.subgroup === group.key)
    .sort((left, right) => (left.navigation?.order || 0) - (right.navigation?.order || 0)),
}));

export const PlatformSettingsPage: React.FC = () => {
  const navigate = useNavigate();

  return (
    <OpsPageShell selectedKey="platform">
      <OpsPageHeader
        title="Platform Settings"
        description="集中管理平台级集成、智能能力、执行目标和治理配置。这里定义可用能力，不代表任何项目或 Agent 自动获得权限。"
      />

      <ScopeNotice>
        <strong>平台目录 ≠ 项目授权。</strong> Models、Knowledge、Skills、Tools 和 Execution Targets 在这里注册后，仍需在 Project Workspace 中显式启用；生产变更继续受 ChangePackage、Approval、Landing 和 Verification 约束。
      </ScopeNotice>

      {platformSettingsGroups.map((group) => (
        <OpsSectionCard key={group.key} title={group.label}>
          <GroupDescription>{groupDescriptions[group.key]}</GroupDescription>
          <SettingsGrid>
            {group.routes.map((route) => (
              <SettingCard key={route.id} type="button" onClick={() => navigate(route.path)}>
                <SettingBody>
                  <SettingTitle>{route.title}</SettingTitle>
                  <SettingDescription>{routeDescriptions[route.id] || route.title}</SettingDescription>
                </SettingBody>
                <IconChevronRight aria-hidden="true" />
              </SettingCard>
            ))}
          </SettingsGrid>
        </OpsSectionCard>
      ))}
    </OpsPageShell>
  );
};
