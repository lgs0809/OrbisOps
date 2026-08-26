import React, { useMemo } from 'react';
import { IconChevronRight, IconSetting } from '@douyinfe/semi-icons';
import { useNavigate, useSearchParams } from 'react-router-dom';
import styled from 'styled-components';

import { groupDescriptions, routeDescriptions, platformSettingsGroups, settingsCategoryFrom, type SettingsCategory } from '../model/settings-navigation';
export { platformSettingsGroups } from '../model/settings-navigation';
import { OpsPageShell, OpsWorkspaceFrame } from '../../../components/ops-layout';
import { theme } from '../../../styles/theme';

const SettingsWorkspace = styled(OpsWorkspaceFrame)`
  min-height: 0;
  display: grid;
  grid-template-columns: 220px minmax(0, 1fr);
  border: 1px solid ${theme.colors.border.secondary};
  border-radius: 14px;
  background: #fff;
  overflow: hidden;

  @media (max-width: ${theme.breakpoints.md}) {
    grid-template-columns: 1fr;
    overflow: auto;
  }
`;

const SettingsRail = styled.aside`
  padding: 18px 10px;
  border-right: 1px solid ${theme.colors.border.secondary};
  background: #f7f8fa;

  @media (max-width: ${theme.breakpoints.md}) {
    border-right: 0;
    border-bottom: 1px solid ${theme.colors.border.secondary};
  }
`;

const RailTitle = styled.div`
  padding: 0 8px 14px;

  h1 {
    margin: 0;
    font-size: 18px;
    font-weight: 650;
    letter-spacing: -0.025em;
  }

  p {
    margin: 4px 0 0;
    color: ${theme.colors.text.tertiary};
    font-size: 13px;
  }
`;

const CategoryButton = styled.button<{ $active: boolean }>`
  width: 100%;
  min-height: 40px;
  display: flex;
  align-items: center;
  gap: 9px;
  margin: 2px 0;
  padding: 0 10px;
  border: 0;
  border-radius: 9px;
  background: ${(props) => (props.$active ? '#e5e8ec' : 'transparent')};
  color: ${(props) => (props.$active ? theme.colors.text.primary : theme.colors.text.secondary)};
  font: inherit;
  font-size: 13px;
  font-weight: ${(props) => (props.$active ? 600 : 500)};
  text-align: left;
  cursor: pointer;

  &:hover { background: ${(props) => (props.$active ? '#e0e3e7' : '#eceef1')}; }
`;

const SettingsMain = styled.section`
  min-width: 0;
  min-height: 0;
  overflow: auto;
  padding: 28px 30px 36px;

  @media (max-width: ${theme.breakpoints.sm}) {
    padding: 20px 16px 28px;
  }
`;

const MainHeader = styled.header`
  max-width: 760px;
  margin-bottom: 24px;

  h2 {
    margin: 0;
    color: ${theme.colors.text.primary};
    font-size: 28px;
    font-weight: 650;
    letter-spacing: -0.035em;
  }

  p {
    margin: 7px 0 0;
    color: ${theme.colors.text.tertiary};
    font-size: 13px;
    line-height: 1.65;
  }
`;

const ResourceFlowHint = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  max-width: 820px;
  margin-bottom: 18px;
  padding: 12px 14px;
  border: 1px solid ${theme.colors.border.tertiary};
  border-radius: 10px;
  background: #fafafa;
  color: ${theme.colors.text.secondary};
  font-size: 13px;
  line-height: 1.6;

  > div {
    display: flex;
    align-items: flex-start;
    gap: 9px;
    min-width: 0;
  }

  strong {
    display: block;
    margin-bottom: 1px;
    color: ${theme.colors.text.primary};
    font-weight: 600;
  }

  .semi-icon { margin-top: 2px; color: ${theme.colors.text.tertiary}; }

  @media (max-width: ${theme.breakpoints.sm}) {
    align-items: flex-start;
    flex-direction: column;
  }
`;

const ResourceFlowAction = styled.button`
  flex: 0 0 auto;
  padding: 5px 8px;
  border: 0;
  border-radius: 7px;
  background: transparent;
  color: ${theme.colors.primary};
  font: inherit;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;

  &:hover { background: #f0f2f5; }
  &:focus-visible { outline: 2px solid ${theme.colors.primary}; outline-offset: 2px; }
`;

const SettingsList = styled.section`
  max-width: 820px;
  border: 1px solid ${theme.colors.border.secondary};
  border-radius: 12px;
  overflow: hidden;
`;

const SettingRow = styled.button`
  width: 100%;
  min-width: 0;
  display: grid;
  grid-template-columns: 36px minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
  padding: 14px 15px;
  border: 0;
  border-bottom: 1px solid ${theme.colors.border.tertiary};
  background: #fff;
  color: inherit;
  text-align: left;
  cursor: pointer;

  &:last-child { border-bottom: 0; }
  &:hover { background: #fafbfc; }
  &:focus-visible { outline: 2px solid ${theme.colors.primary}; outline-offset: -2px; }

  > .semi-icon:last-child { color: #a0a6af; }
`;

const SettingIcon = styled.span`
  width: 36px;
  height: 36px;
  display: grid;
  place-items: center;
  border-radius: 9px;
  background: #f0f2f5;
  color: #5e6672;
`;

const RowCopy = styled.div`
  min-width: 0;

  strong {
    display: block;
    color: ${theme.colors.text.primary};
    font-size: 13px;
    font-weight: 600;
  }

  span {
    display: block;
    margin-top: 3px;
    color: ${theme.colors.text.tertiary};
    font-size: 13px;
    line-height: 1.5;
  }
`;

export const PlatformSettingsPage: React.FC = () => {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const category = settingsCategoryFrom(searchParams.get('category'));
  const setCategory = (value: SettingsCategory) => {
    const next = new URLSearchParams(searchParams);
    next.set('category', value);
    setSearchParams(next);
  };
  const selectedGroup = useMemo(() => platformSettingsGroups.find((group) => group.key === category), [category]);
  const title = category === 'account' ? '用户与访问控制' : selectedGroup?.label || '设置';
  const description = category === 'account' ? '平台账号与项目成员关系分别管理。' : groupDescriptions[category];
  const routes = category === 'account'
    ? [{ id: 'users-access', title: '用户与访问控制', path: '/settings/users-access' }]
    : selectedGroup?.routes || [];
  const showResourceFlowHint = category === 'intelligence' || category === 'execution-governance';

  return (
    <OpsPageShell selectedKey="settings" maxWidth="none" padding="0">
      <SettingsWorkspace>
        <SettingsRail>
          <RailTitle><h1>设置</h1><p>平台级能力与治理</p></RailTitle>
          <CategoryButton type="button" $active={category === 'account'} aria-pressed={category === 'account'} onClick={() => setCategory('account')}>用户与访问控制</CategoryButton>
          {platformSettingsGroups.map((group) => (
            <CategoryButton key={group.key} type="button" $active={category === group.key} aria-pressed={category === group.key} onClick={() => setCategory(group.key)}>{group.label}</CategoryButton>
          ))}
        </SettingsRail>

        <SettingsMain>
          <MainHeader><h2>{title}</h2><p>{description}</p></MainHeader>
          {showResourceFlowHint && (
            <ResourceFlowHint data-testid="platform-resource-flow-hint">
              <div>
                <IconSetting />
                <span>
                  <strong>先接入平台，再按项目使用</strong>
                  这里负责统一维护平台资源。接入后的资源不会自动影响现有项目；项目会按自己的资源绑定与权限配置决定实际可用能力。
                </span>
              </div>
              <ResourceFlowAction type="button" onClick={() => navigate('/projects?view=capabilities')}>查看项目能力</ResourceFlowAction>
            </ResourceFlowHint>
          )}
          <SettingsList>
            {routes.map((route) => (
              <SettingRow key={route.id} type="button" aria-label={route.title} onClick={() => navigate(route.path)}>
                <SettingIcon><IconSetting /></SettingIcon>
                <RowCopy>
                  <strong>{route.title}</strong>
                  <span>{route.id === 'users-access' ? '创建和管理平台用户；项目只能添加已经存在的平台账号。' : routeDescriptions[route.id] || route.title}</span>
                </RowCopy>
                <IconChevronRight aria-hidden="true" />
              </SettingRow>
            ))}
          </SettingsList>
        </SettingsMain>
      </SettingsWorkspace>
    </OpsPageShell>
  );
};
