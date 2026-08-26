import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Button, Input, Modal, Space, Tag, Typography } from '@douyinfe/semi-ui';
import { OpsSectionCard } from '../../components/ops-layout';
import { opsAdminService } from '../../services/ops-admin-service';
import { userFacingError } from '../../utils/user-facing-error';

const { Text } = Typography;
const checked = <T,>(response: { code: string; info?: string; data: T }): T => {
  if (response.code !== '0000') throw new Error(response.info || '读取失败');
  return response.data;
};

export const AtomicPublicationPanel = ({ projectId }: { projectId: string }) => {
  const client = useQueryClient();
  const [selected, setSelected] = useState<Record<string, any> | null>(null);
  const [reason, setReason] = useState('');
  const query = useQuery({ queryKey: ['skill-atomic-publications', projectId], queryFn: async () => checked(await opsAdminService.listAtomicSkillPublications(projectId)) });
  const rollback = useMutation({
    mutationFn: async () => {
      if (!selected || !reason.trim()) throw new Error('请说明回滚原因');
      return checked(await opsAdminService.rollbackAtomicSkillPublication(selected.candidateId, selected.projectId, reason.trim()));
    },
    onSuccess: async () => {
      setSelected(null); setReason('');
      await client.invalidateQueries({ queryKey: ['skill-atomic-publications'] });
      await client.invalidateQueries({ queryKey: ['skill-evolver'] });
    },
  });
  return <OpsSectionCard title="方法拆分与合并">
    <Space vertical align="start" style={{ width: '100%' }}>
      <Text type="tertiary">全部方法正文和索引就绪后一起生效。原方法及已有工作流的明确绑定保留，可查看替换关系并整组回滚。</Text>
      <Button onClick={() => void query.refetch()} loading={query.isFetching}>刷新发布记录</Button>
      {query.isError && <Text type="danger">{userFacingError(query.error, '发布记录暂时无法读取')}</Text>}
      {!query.isPending && !query.isError && query.data?.length === 0 && <Text>尚无拆分或合并发布记录。</Text>}
      {query.data?.map(record => <Space key={record.candidateId} vertical align="start" spacing="tight" style={{ width: '100%', paddingBlock: 12 }}>
        <Space><Text strong>{record.operation === 'SPLIT_SKILL' ? '拆分方法' : '合并方法'}</Text><Tag>{({ ACTIVE: '已生效', STAGED: '等待全部正文与索引', ROLLED_BACK: '已整组回滚' } as Record<string, string>)[record.status] || record.status}</Tag></Space>
        <Text>项目：{record.projectId}</Text>
        <Text>原方法：{record.plan?.sources?.map((s: Record<string, any>) => `${s.skillId}（第 ${s.version} 版）`).join('、')}</Text>
        <Text>新方法：{record.plan?.targets?.map((s: Record<string, any>) => s.name).join('、')}</Text>
        {record.rollbackReason && <Text>回滚原因：{record.rollbackReason}</Text>}
        {['ACTIVE', 'PENDING_INDEX'].includes(record.releaseStatus) && record.status !== 'ROLLED_BACK' && <Button type="danger" onClick={() => { rollback.reset(); setReason(''); setSelected(record); }}>整组回滚</Button>}
      </Space>)}
    </Space>
    <Modal title="整组回滚方法发布" visible={Boolean(selected)} onCancel={() => setSelected(null)} onOk={() => rollback.mutate()}
      confirmLoading={rollback.isPending} okButtonProps={{ disabled: !reason.trim(), 'aria-label': '确认整组回滚' }} okText="确认整组回滚">
      <Text>新方法将退出后续使用，原方法恢复自动检索。若方法已有后续修改或被其他合并引用，系统会拦截本次回滚。</Text>
      <Input aria-label="方法发布回滚原因" placeholder="说明回滚原因" value={reason} maxLength={1024} onChange={setReason} style={{ marginTop: 16 }} />
      {rollback.isError && <Text type="danger">{userFacingError(rollback.error, '回滚未完成，请核对是否存在后续修改')}</Text>}
    </Modal>
  </OpsSectionCard>;
};
