type EvolutionRecord = Record<string, unknown>;
const identity = (record: EvolutionRecord, camel: string, snake: string) =>
  String(record[camel] || record[snake] || '');

/** Repeated analysis of one Run can create distinct jobs with different accepted sources. */
export const withEvolutionPatch = (job: EvolutionRecord, patches: EvolutionRecord[]): EvolutionRecord => {
  const jobId = identity(job, 'jobId', 'job_id');
  const projectId = identity(job, 'projectId', 'project_id');
  if (!jobId || !projectId) return job;
  const matches = patches.filter(patch => identity(patch, 'jobId', 'job_id') === jobId
    && identity(patch, 'projectId', 'project_id') === projectId);
  return matches.length === 1 ? { ...job, ...matches[0] } : job;
};

/** Display the live publication projection, preserving the generated decision/status for audit. */
const publication = (record: EvolutionRecord) =>
  record.publication && typeof record.publication === 'object'
    ? record.publication as EvolutionRecord : {};
export const evolutionStatus = (record: EvolutionRecord): string =>
  String(publication(record).status || record.status || '');
export const evolutionDecision = (record: EvolutionRecord): string =>
  String(publication(record).operation || record.decision || '');
export const evolutionTargets = (record: EvolutionRecord): string => {
  const targets = publication(record).targetSkillIds;
  return Array.isArray(targets) && targets.length ? targets.join('、')
    : String(record.targetSkillTitle || record.target_skill_title || record.skillTitle || record.skill_title
      || record.targetSkillId || record.target_skill_id || '待创建或待匹配 Skill');
};

/** This is the version produced by this publication, not the current head after a rollback. */
export const evolutionPublishedVersion = (record: EvolutionRecord): string => {
  const live = publication(record);
  if (['SPLIT_SKILL', 'MERGE_SKILLS'].includes(evolutionDecision(record))) {
    const status = evolutionStatus(record);
    if (status === 'ROLLED_BACK') return '整组当前未生效';
    return status === 'ACTIVE' ? '整组版本，见详情' : '整组尚未生效';
  }
  const value = Object.keys(live).length ? live.releasedVersion : record.appliedVersion ?? record.applied_version;
  return typeof value === 'number' && Number.isSafeInteger(value) && value > 0 ? `v${value}` : '未发布新版本';
};
