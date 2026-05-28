import { useState } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import { Button, Modal, Space, Typography } from '@douyinfe/semi-ui';
import { opsAdminService } from '../../../services/ops-admin-service';
import { userFacingError } from '../../../utils/user-facing-error';

const { Text } = Typography;
export const ProjectSkillVersionHistory = ({ projectId, skillId, name, currentVersion, onChanged, onClose }: {
  projectId: string; skillId: string; name: string; currentVersion: number; onChanged: () => Promise<unknown>; onClose: () => void;
}) => {
  const [target, setTarget] = useState<number | null>(null);
  const [restored, setRestored] = useState(false);
  const versions = useQuery({ queryKey: ['project-skill-versions', projectId, skillId], queryFn: async () => {
    const response = await opsAdminService.listProjectSkillVersions(projectId, skillId);
    if (response.code !== '0000') throw new Error(response.info || '版本历史读取失败');
    return response.data || [];
  } });
  const rollback = useMutation({ mutationFn: async (version: number) => {
    const response = await opsAdminService.rollbackProjectSkillVersion(projectId, skillId, version);
    if (response.code !== '0000') throw new Error(response.info || '回滚失败');
    if (response.data?.projectId !== projectId || response.data?.skillId !== skillId) throw new Error('回滚结果与当前方法不一致，请刷新核对');
  }, onSuccess: async () => { setTarget(null); setRestored(true); await onChanged(); await versions.refetch(); } });
  return <Modal title={`${name} · 版本历史`} visible onCancel={onClose} footer={<Button onClick={onClose}>关闭版本历史</Button>} width={760}>
    <Text>回滚会以历史正文和资源创建新版本，原有版本均保留。索引就绪后供后续任务使用。</Text>
    {restored && <p role="status">回滚版本已保存，后续任务将在索引就绪后使用。</p>}
    {versions.isError && <p role="alert">{userFacingError(versions.error, '版本历史读取失败')}</p>}
    {rollback.isError && <p role="alert">{userFacingError(rollback.error, '回滚失败')}</p>}
    <Space vertical align="start" style={{ width: '100%', marginTop: 12 }}>
      {versions.data?.map(version => <div key={version.version} style={{ width: '100%', borderBottom: '1px solid var(--semi-color-border)', paddingBlock: 8 }}>
        <Space><Text strong>第 {version.version} 版</Text><Text>{version.changeSummary || version.status || ''}</Text>
          {version.version < currentVersion && <Button disabled={rollback.isPending || restored} onClick={() => setTarget(version.version)} aria-label={`选择回滚到第 ${version.version} 版`}>回滚到此版本</Button>}
        </Space>
        <details><summary>查看方法正文</summary><pre style={{ whiteSpace: 'pre-wrap', maxHeight: 240, overflow: 'auto' }}>{version.content}</pre></details>
      </div>)}
    </Space>
    {target !== null && <div style={{ marginTop: 16 }}>
      <p>确认使用第 {target} 版的正文和资源，替代当前方法？</p>
      <Space><Button type="primary" disabled={rollback.isPending} loading={rollback.isPending} onClick={() => rollback.mutate(target)}>确认回滚方法</Button>
        <Button disabled={rollback.isPending} onClick={() => setTarget(null)}>取消回滚</Button></Space>
    </div>}
  </Modal>;
};
