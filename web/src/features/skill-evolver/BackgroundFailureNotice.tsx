import { backgroundFailureMessage } from './background-failure';

export const BackgroundFailureNotice = ({ job }: { job?: Record<string, any> }) => {
  const diagnostic = backgroundFailureMessage(job?.lastFailureCode);
  const latestReason = backgroundFailureMessage(job?.lastError);
  const message = job?.lastError && (job.lastFailureCode === 'BACKGROUND_FAILURE_RECORDED'
    ? latestReason || diagnostic : diagnostic || latestReason);
  if (!message) return null;
  const at = job?.nextRunAt ? new Date(job.nextRunAt) : null;
  return <div role="status">
    <p>最近处理说明：{message}</p>
    {job?.status === 'FAILED' && <p>本次任务已失败，未在等待自动重试。</p>}
    {job?.status === 'PENDING' && at && !Number.isNaN(at.getTime()) && <p>下一次重试不早于：<time dateTime={at.toISOString()}>{at.toLocaleString()}</time>（本地时间）</p>}
  </div>;
};
