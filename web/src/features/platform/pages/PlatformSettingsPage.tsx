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
  integrations: '配置外部交互入口。每个渠道最终能执行什么，仍由项目路由、成员关系和权限共同决定。',
  intelligence: '注册模型、知识库和技能等平台能力，由项目按需显式启用。',
  'execution-governance': '配置 MCP 工具、生产执行目标和治理能力，但不会绕过受控变更包安全门禁。',
  advanced: '模型目录、记忆、技能进化和工具路由观测等低频平台能力。',
};

const routeDescriptions: Record<string, string> = {
  channels: '配置飞书、企业微信、钉钉、Slack、Telegram、Discord、QQ 等渠道接入。',
  models: '配置对话、向量化、重排服务商与模型连接。',
  tools: '管理平台级 MCP / 工具能力目录。',
  knowledge: '管理可复用知识库、文档入库、检索策略和质量验证。',
  skills: '管理可被项目或工作流智能节点启用的可复用技能。',
  'execution-targets': '配置落地、验证和回滚执行所使用的目标模板。',
  governance: '查看平台治理、审计和安全控制。',
  'model-catalog': '维护底层模型元数据与服务商能力。',
  memory: '配置并观察平台级智能执行记忆行为。',
  'skill-evolver': '查看技能进化候选和受治理版本。',
  'tool-routing': '观察工具候选生成、路由和运行时选择证据。',
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
    <OpsPageShell selectedKey="settings">
      <OpsPageHeader
        title="设置"
        description="管理平台用户、渠道、共享能力、执行目标和治理配置。"
      />

      <ScopeNotice>
        <strong>共享能力需要在项目中显式启用。</strong> 在这里注册模型、知识库、技能、工具或执行目标，不会自动授予任何项目；请在对应项目中按需配置和授权。
      </ScopeNotice>

      <OpsSectionCard title="用户与访问控制">
        <GroupDescription>平台账号与项目成员关系独立管理。</GroupDescription>
        <SettingsGrid>
          <SettingCard type="button" onClick={() => navigate('/settings/users-access')}>
            <SettingBody>
              <SettingTitle>用户与访问控制</SettingTitle>
              <SettingDescription>创建和管理平台用户。项目只能添加已经存在的平台账号。</SettingDescription>
            </SettingBody>
            <IconChevronRight aria-hidden="true" />
          </SettingCard>
        </SettingsGrid>
      </OpsSectionCard>

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
