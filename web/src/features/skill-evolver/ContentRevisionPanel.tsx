import { useQuery } from '@tanstack/react-query';
import { Space, Typography } from '@douyinfe/semi-ui';
import { opsAdminService } from '../../services/ops-admin-service';
import { userFacingError } from '../../utils/user-facing-error';

const { Text } = Typography;
const labels: Record<string, string> = {
  LEASED: '正在按审查意见修订', PENDING: '已保存进度，等待自动重试',
  APPLIED: '修订已保存，仍需通过发布检查', REJECTED: '修订未满足边界检查，已保留原因',
};
export const ContentRevisionPanel = ({ candidateId, projectId }: { candidateId: string; projectId: string }) => {
  const query = useQuery({ queryKey: ['skill-content-revisions', projectId, candidateId], refetchInterval: 10000,
    queryFn: async () => {
      const response = await opsAdminService.skillContentRevisions(candidateId, projectId);
      if (response.code !== '0000') throw new Error(response.info || '修订记录读取失败');
      return response.data;
    } });
  const rows = query.data?.revisions as Array<{ round: number; status: string; reviewReasons: string[] }> | undefined;
  if (query.isError) return <Text type="danger">{userFacingError(query.error, '修订记录暂时无法读取')}</Text>;
  if (!rows?.length) return null;
  const completed = rows.filter(row => row.status === 'APPLIED' || row.status === 'REJECTED').length;
  return <Space vertical align="start" style={{ minWidth: 0, maxWidth: '100%', overflowWrap: 'anywhere' }}>
    <Text strong>按审查意见自动修订</Text>
    <Text type="tertiary">最多修订两轮，每轮保留原稿并重新审查。连接故障保留进度，后台稍后重试。</Text>
    {rows.map(row => <div key={row.round}>
      <Text strong>第 {row.round} 轮：{labels[row.status] || '状态待核对'}</Text>
      {row.reviewReasons.map((reason, index) => <p key={index}>{reason.replace(/^POLICY_CONTENT_REVIEW:/, '')}</p>)}
    </div>)}
    {completed === 2 && query.data?.candidateStatus === 'POLICY_REJECTED' && <Text type="warning">两轮修订后仍未通过，已保留失败；等待新的验收来源。</Text>}
  </Space>;
};
