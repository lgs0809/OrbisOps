import React from 'react';
import styled from 'styled-components';
import { Button, Card, Space, Tag, Typography } from '@douyinfe/semi-ui';

import { OpsSkillSummary } from '../../../services/ops-admin-service';
import { theme } from '../../../styles/theme';

const { Text } = Typography;

const Section = styled(Card)`
  margin-bottom: ${theme.spacing.lg};
  .semi-card-body { padding: ${theme.spacing.lg}; }
`;

const Header = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: ${theme.spacing.base};
  flex-wrap: wrap;
`;

const Grid = styled.div`
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(280px, 100%), 1fr));
  gap: ${theme.spacing.base};
  margin-top: ${theme.spacing.base};
`;

type Props = {
  projectId: string;
  skillIds: string[];
  skillsById: ReadonlyMap<string, OpsSkillSummary>;
  saving: boolean;
  isProjectOwned: (skill: OpsSkillSummary | undefined, projectId: string) => boolean;
  sourceLabel: (skill: OpsSkillSummary | undefined, projectId: string) => string;
  onCreate: () => void;
  onCopy: () => void;
  onBindAgent: () => void;
  onEdit: (skill: OpsSkillSummary) => void;
  onToggleStatus: (skill: OpsSkillSummary, nextStatus: 'ENABLED' | 'DISABLED') => void;
  onUsage: (skill: OpsSkillSummary) => void;
  onPreview: (skillId: string, skill?: OpsSkillSummary) => void;
};

export const ProjectSkillsSection: React.FC<Props> = ({
  projectId, skillIds, skillsById, saving, isProjectOwned, sourceLabel,
  onCreate, onCopy, onBindAgent, onEdit, onToggleStatus, onUsage, onPreview,
}) => (
  <Section title="项目 Skill">
    <Header>
      <div>
        <Text strong>项目专属经验与 SOP</Text>
        <div><Text type="tertiary" size="small">维护项目专属经验、业务说明、发布规范与回滚规则。</Text></div>
      </div>
      <Space wrap>
        <Button onClick={onCreate}>新建项目 Skill</Button>
        <Button onClick={onCopy}>从通用 Skill 复制</Button>
        <Button disabled>从 SOP 生成</Button>
        <Button disabled>从事故复盘沉淀</Button>
        <Button onClick={onBindAgent}>绑定 Agent</Button>
      </Space>
    </Header>
    <Grid>
      {skillIds.map((skillId) => {
        const skill = skillsById.get(skillId);
        const projectOwned = isProjectOwned(skill, projectId);
        const currentStatus = String(skill?.status || 'ENABLED').toUpperCase();
        const nextStatus: 'ENABLED' | 'DISABLED' = currentStatus === 'DISABLED' ? 'ENABLED' : 'DISABLED';
        const usageSkill = skill || ({ name: skillId, skillId } as OpsSkillSummary);
        return (
          <Card key={skillId}>
            <Space vertical align="start" style={{ width: '100%' }}>
              <Header><Text strong>{skill?.name || skillId}</Text><Tag color="green">已绑定</Tag></Header>
              <Text type="secondary" size="small">ID：{skillId}</Text>
              <Text type="tertiary" size="small">来源：{sourceLabel(skill, projectId)}</Text>
              {skill?.status && <Text type="tertiary" size="small">状态：{skill.status}</Text>}
              <Space wrap>
                {projectOwned && skill && <Button size="small" onClick={() => onEdit(skill)}>编辑</Button>}
                {projectOwned && skill && (
                  <Button size="small" loading={saving} onClick={() => onToggleStatus(skill, nextStatus)}>
                    {nextStatus === 'ENABLED' ? '启用' : '停用'}
                  </Button>
                )}
                <Button size="small" onClick={onBindAgent}>绑定 Agent</Button>
                <Button size="small" onClick={() => onUsage(usageSkill)}>查看使用记录</Button>
                <Button size="small" onClick={() => onPreview(skillId, skill)}>高级预览</Button>
              </Space>
            </Space>
          </Card>
        );
      })}
      {!skillIds.length && (
        <Card><Space vertical align="start"><Text strong>暂无项目 Skill</Text><Text type="tertiary" size="small">可先从通用 Skill 复制，或后续在项目内沉淀经验。</Text></Space></Card>
      )}
    </Grid>
  </Section>
);
