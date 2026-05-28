import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Button, Space, Tag, Typography } from '@douyinfe/semi-ui';
import { opsAdminService } from '../../../services/ops-admin-service';
import { userFacingError } from '../../../utils/user-facing-error';
import { projectSkillCatalogOptions } from '../api/project-skill-catalog';
import { projectWorkspaceQueryKeys } from '../api/project-workspace-queries';
import { ProjectSkillVersionHistory } from './ProjectSkillVersionHistory';

const { Text } = Typography;

/** Uses the project catalog, including disabled entries, independently of the enabled capability summary. */
export const ProjectSkillGovernancePanel = ({ projectId, onChanged }: { projectId: string; onChanged: () => Promise<unknown> }) => {
  const client = useQueryClient();
  const catalog = projectSkillCatalogOptions(projectId);
  const key = catalog.queryKey;
  const [history, setHistory] = useState<{ projectId: string; skillId: string; name: string; version: number } | null>(null);
  const skills = useQuery({ ...catalog, enabled: Boolean(projectId) });
  const mutation = useMutation({
    mutationFn: async ({ skillId, status }: { skillId: string; status: 'ENABLED' | 'DISABLED' }) => {
      const response = await opsAdminService.updateProjectSkillStatus(projectId, skillId, status);
      if (response.code !== '0000') throw new Error(response.info || '更新项目 Skill 失败');
      if (response.data?.status !== status || response.data?.projectId !== projectId) throw new Error('服务端未确认项目 Skill 状态，请刷新核对。');
    },
    onSuccess: async () => {
      await client.invalidateQueries({ queryKey: key });
      await client.invalidateQueries({ queryKey: projectWorkspaceQueryKeys.capabilities(projectId) });
      await onChanged();
    },
  });
  return <section aria-label="项目 Skill 管理">
    <h3>项目 Skill 管理</h3>
    <Text type="tertiary" style={{ display: 'block', marginBottom: 10 }}>停用后，后续加载会拒绝该方法，包括等待中的工作流所绑定的旧版本。</Text>
    {skills.isError && <div role="alert">{userFacingError(skills.error, '读取项目 Skill 失败。')}</div>}
    {mutation.isError && <div role="alert">{userFacingError(mutation.error, '更新项目 Skill 失败。')}</div>}
    <Button size="small" loading={skills.isFetching} onClick={() => void skills.refetch()}>刷新项目 Skill</Button>
    <Space vertical align="start" style={{ width: '100%', marginTop: 10 }}>
      {(skills.data || []).map((skill) => {
        const id = skill.skillId || '';
        const enabled = skill.status === 'ENABLED';
        return <Space key={id} wrap aria-label={`项目方法 ${id}`}>
          <Text strong>{skill.name || id}</Text>
          <Tag color={enabled ? 'green' : 'grey'}>{enabled ? '已启用' : skill.status === 'DISABLED' ? '已停用' : skill.status}</Tag>
          <Text type="tertiary">v{skill.version}</Text>
          <Button size="small" disabled={!id} aria-label={`版本历史 ${skill.name || id}`} onClick={() => setHistory({ projectId, skillId: id, name: skill.name || id, version: skill.version || 0 })}>版本历史</Button>
          {['ENABLED', 'DISABLED'].includes(skill.status || '') && <Button size="small" disabled={mutation.isPending || !id}
            aria-label={`${enabled ? '停用' : '启用'} ${skill.name || id}`}
            onClick={() => mutation.mutate({ skillId: id, status: enabled ? 'DISABLED' : 'ENABLED' })}>
            {enabled ? '停用' : '启用'}
          </Button>}
        </Space>;
      })}
      {skills.isSuccess && !skills.data.length && <Text type="tertiary">暂无项目专属 Skill。</Text>}
    </Space>
    {history?.projectId === projectId && <ProjectSkillVersionHistory key={`${projectId}/${history.skillId}`} projectId={projectId} skillId={history.skillId}
      name={history.name} currentVersion={history.version} onClose={() => setHistory(null)} onChanged={async () => {
        await client.invalidateQueries({ queryKey: key });
        await client.invalidateQueries({ queryKey: projectWorkspaceQueryKeys.capabilities(projectId) });
        await onChanged();
      }} />}
  </section>;
};
