import { Button } from '@douyinfe/semi-ui';
import { canRetrySkillJob, useRetrySkillEvolverMutation, type RetryableSkillJob } from './api/skill-evolver-queries';
import { userFacingError } from '../../utils/user-facing-error';

/** Recover the same accepted source through the existing queue API. Publication has its own lifecycle. */
export function BackgroundTaskRetryPanel({ job }: { job?: RetryableSkillJob }) {
  const mutation = useRetrySkillEvolverMutation();
  if (!canRetrySkillJob(job)) return null;
  return <section aria-label="恢复后台分析">
    <p>可以使用原任务证据重新分析，历史失败记录会保留。</p>
    <Button loading={mutation.isPending} disabled={mutation.isPending}
      onClick={() => mutation.mutate(job)}>重新分析这项任务</Button>
    {mutation.isError && <p role="alert">{userFacingError(mutation.error, '暂时无法重新分析，请刷新任务状态后重试。')}</p>}
  </section>;
}
