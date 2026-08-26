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
const statuses: Record<string, string> = { PENDING: '等待后台检查', RUNNING: '正在检查', NO_CHANGE: '检查完成，无需精简',
  REVIEW_REQUIRED: '需要检查', REVIEWED_KEEP: '已检查，继续保留', PENDING_INDEX: '正文已保存，等待索引', ACTIVE: '新版已生效', SUPERSEDED: '原版本已更新' };

export const MaintenancePanel = ({ projectId }: { projectId: string }) => {
  const client = useQueryClient();
  const [selected, setSelected] = useState<Record<string, any> | null>(null);
  const [reason, setReason] = useState('');
  const query = useQuery({ queryKey: ['skill-maintenance', projectId], queryFn: async () => checked(await opsAdminService.listSkillMaintenance(projectId)) });
  const keep = useMutation({
    mutationFn: async () => {
      if (!selected || !reason.trim()) throw new Error('请填写检查结论');
      return checked(await opsAdminService.keepInactiveSkill(selected.check_id, selected.project_id, reason.trim()));
    },
    onSuccess: async () => { setSelected(null); setReason(''); await client.invalidateQueries({ queryKey: ['skill-maintenance'] }); },
  });
  return <OpsSectionCard title="方法维护检查">
    <Space vertical align="start" style={{ width: '100%' }}>
      <Text type="tertiary">正文及其引用内容过长，或累计五次修改时，后台检查重复规则和步骤，保留适用条件与停止要求。九十天没有使用记录时提醒检查，方法仍保留，不会自动停用或删除。</Text>
      <Button onClick={() => void query.refetch()} loading={query.isFetching}>刷新维护检查</Button>
      {query.isError && <Text type="danger">{userFacingError(query.error, '维护记录暂时无法读取')}</Text>}
      {!query.isPending && !query.isError && query.data?.length === 0 && <Text>目前没有达到检查条件的方法。</Text>}
      {query.data?.map(record => <Space key={record.check_id} vertical align="start" spacing="tight" style={{ paddingBlock: 10 }}>
        <Space><Text strong>{record.skill_id} · 第 {record.base_version} 版</Text><Tag>{statuses[record.status] || record.status}</Tag></Space>
        <Text>{record.kind === 'INACTIVITY_REVIEW' ? '长期未使用检查' : '正文精简检查'} · 项目：{record.project_id}</Text>
        {record.reason === 'BEHAVIOR_CHANGE_REQUIRES_SOURCE_QUALIFIED_PATCH' && <Text>精简可能改变方法含义，已保留原文。后续修改仍需满足成功来源要求。</Text>}
        {record.reason === 'MAINTENANCE_RECHECK_REQUIRED' && <Text>上次检查暂未完成，后台会稍后重试。</Text>}
        {record.status === 'ACTIVE' && <Text>已发布第 {record.published_version} 版；内容检查通过，实际使用效果仍需观察。</Text>}
        {record.review_reason && <Text>检查结论：{record.review_reason}</Text>}
        {record.kind === 'INACTIVITY_REVIEW' && record.status === 'REVIEW_REQUIRED' && <Button onClick={() => { keep.reset(); setReason(''); setSelected(record); }}>检查后保留</Button>}
      </Space>)}
    </Space>
    <Modal title="记录方法检查结论" visible={Boolean(selected)} onCancel={() => setSelected(null)} onOk={() => keep.mutate()}
      confirmLoading={keep.isPending} okButtonProps={{ disabled: !reason.trim(), 'aria-label': '确认继续保留方法' }} okText="继续保留">
      <Text>请核对方法是否仍适用，记录继续保留的原因。此操作只保存检查结论。</Text>
      <Input aria-label="方法维护检查结论" value={reason} maxLength={1024} onChange={setReason} style={{ marginTop: 16 }} />
      {keep.isError && <Text type="danger">{userFacingError(keep.error, '检查结论未保存')}</Text>}
    </Modal>
  </OpsSectionCard>;
};
