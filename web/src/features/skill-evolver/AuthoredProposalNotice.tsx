import type { AuthoredDecision } from './AuthoredDecisionNotice';

type Job = { authoredDecision?: AuthoredDecision; authoredPublication?: { status?: string } };
const labels: Record<string, string> = {
  CREATE_SKILL: '新增方法', SPLIT_SKILL: '拆分方法', MERGE_SKILLS: '合并方法',
  UPDATE_ROUTING_RULE: '调整适用范围', UPDATE_DIAGNOSTIC_RECIPE: '更新排查步骤',
  UPDATE_EVIDENCE_CRITERIA: '更新证据要求', UPDATE_NEGATIVE_RULE: '更新使用边界',
};

/** A verified author response is progress, independently of candidate/publication admission. */
export const AuthoredProposalNotice = ({ job }: { job?: Job }) => {
  const decision = job?.authoredDecision;
  if (!decision?.currentSource || decision.source !== 'LLM' || !decision.planId?.trim()
    || !decision.reason?.trim() || !labels[decision.operation]
    || ['ACTIVE', 'ROLLED_BACK'].includes(job?.authoredPublication?.status || '')) return null;
  return <section aria-label="后台维护提案">
    <p>模型已提出：{labels[decision.operation]}。这是一份已保存的提案，尚不代表发布通过。</p>
    <p>{decision.reason}</p>
    <p>提案仍需通过证据、内容和发布检查；实际生效情况以发布记录为准。</p>
  </section>;
};
