import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Button, Space, Typography } from '@douyinfe/semi-ui';
import { opsAdminService } from '../../services/ops-admin-service';
import { userFacingError } from '../../utils/user-facing-error';

const { Text } = Typography;
const checked = (r: { code: string; info?: string; data: Record<string, any> }) => {
  if (r.code !== '0000') throw new Error(r.info || '发布状态读取失败');
  return r.data;
};
const labels: Record<string, string> = { NOT_QUEUED: '尚未提交后台复核', PENDING: '等待后台复核', RUNNING: '后台正在复核',
  COMPLETED: '复核结束，等待发布状态确认', REJECTED: '未通过发布检查', PENDING_INDEX: '检查通过，等待正文与索引就绪',
  ACTIVE: '方法已发布并生效', ROLLED_BACK: '方法已回滚', READY: '检查通过，等待发布' };

export const PublicationRetryPanel = ({ candidateId, projectId }: { candidateId: string; projectId: string }) => {
  const client = useQueryClient();
  const key = ['skill-publication', projectId, candidateId];
  const query = useQuery({ queryKey: key, queryFn: async () => checked(await opsAdminService.skillPublicationStatus(candidateId, projectId)), refetchInterval: 10000 });
  const retry = useMutation({ mutationFn: async () => checked(await opsAdminService.retrySkillPublication(candidateId, projectId)),
    onSuccess: async () => { await client.invalidateQueries({ queryKey: key }); } });
  const state = query.data?.releaseStatus || query.data?.status;
  return <Space vertical align="start">
    <Text strong>发布进度：{query.isPending ? '读取中' : labels[state] || '待核对'}</Text>
    {query.data?.last_reason === 'SKILL_CONTENT_REVIEW_UNAVAILABLE' && <Text>模型复核暂时不可用，已保留候选，后台将自动重试。</Text>}
    {query.data?.next_run_at && query.data.status === 'PENDING' && <Text>下次复核：{String(query.data.next_run_at)}</Text>}
    {query.data?.skillId && <Text>已生成方法：{query.data.skillId} · 第 {query.data.version} 版</Text>}
    {query.data?.status === 'NOT_QUEUED' && !query.data?.releaseStatus && <Button loading={retry.isPending} onClick={() => retry.mutate()}>重新检查并发布</Button>}
    <Button loading={query.isFetching} onClick={() => void query.refetch()}>刷新发布进度</Button>
    {(query.isError || retry.isError) && <Text type="danger">{userFacingError(query.error || retry.error, '发布状态暂时不可用')}</Text>}
  </Space>;
};
