import { describe, expect, it } from 'vitest';
import { withEvolutionPatch, evolutionStatus, evolutionDecision, evolutionTargets, evolutionPublishedVersion } from './evolution-record';

describe('learning result provenance', () => {
  const job = { jobId: 'accepted-job', projectId: 'p', runId: 'same-run', acceptedSourceId: 'acceptance-1' };
  it('keeps the accepted task source when attaching its own result', () => {
    const old = { jobId: 'old-job', projectId: 'p', runId: 'same-run', decision: 'UNVERIFIED' };
    const current = { job_id: 'accepted-job', project_id: 'p', runId: 'same-run', decision: 'NO_PATTERN' };
    expect(withEvolutionPatch(job, [old, current])).toMatchObject({ acceptedSourceId: 'acceptance-1', decision: 'NO_PATTERN' });
  });
  it('does not infer identity from the same Run or a different project', () => {
    expect(withEvolutionPatch(job, [
      { jobId: 'old-job', projectId: 'p', runId: 'same-run' },
      { jobId: 'accepted-job', projectId: 'other', decision: 'wrong' },
    ])).toEqual(job);
  });
  it('leaves missing and ambiguous legacy links unresolved', () => {
    expect(withEvolutionPatch({ runId: 'same-run' }, [job])).toEqual({ runId: 'same-run' });
    expect(withEvolutionPatch(job, [job, { ...job, decision: 'ambiguous' }])).toEqual(job);
  });
});

describe('live publication projection', () => {
  const generated = { status: 'PENDING_INDEX', decision: 'CREATE_SKILL_CANDIDATE', targetSkillId: 'old' };
  it('shows current merge/rollback facts without rewriting the generation snapshot', () => {
    const record = { ...generated, publication: { status: 'ACTIVE', operation: 'MERGE_SKILLS', targetSkillIds: ['merged'] } };
    expect(evolutionStatus(record)).toBe('ACTIVE');
    expect(evolutionDecision(record)).toBe('MERGE_SKILLS');
    expect(evolutionTargets(record)).toBe('merged');
    expect(record.status).toBe('PENDING_INDEX');
    expect(evolutionStatus({ ...record, publication: { ...record.publication, status: 'ROLLED_BACK' } })).toBe('ROLLED_BACK');
  });
  it('retains all split targets and keeps legacy rows honest when no live projection exists', () => {
    expect(evolutionTargets({ ...generated, publication: { targetSkillIds: ['a', 'b'] } })).toBe('a、b');
    expect(evolutionStatus(generated)).toBe('PENDING_INDEX');
    expect(evolutionDecision(generated)).toBe('CREATE_SKILL_CANDIDATE');
    expect(evolutionTargets(generated)).toBe('old');
  });
  it('uses actual release versions and does not relabel a rolled-back release as the active head', () => {
    expect(evolutionPublishedVersion({ appliedVersion: 0, publication: { releasedVersion: 1, status: 'ACTIVE' } })).toBe('v1');
    expect(evolutionPublishedVersion({ appliedVersion: 999, publication: { releasedVersion: 2, status: 'ROLLED_BACK' } })).toBe('v2');
    expect(evolutionPublishedVersion({ appliedVersion: 3 })).toBe('v3');
    for (const value of [0, -1, 1.5, NaN, '4', undefined]) {
      expect(evolutionPublishedVersion({ appliedVersion: 999, publication: { releasedVersion: value } })).toBe('未发布新版本');
    }
  });
  it('does not invent one version for a replacement package containing several assets', () => {
    expect(evolutionPublishedVersion({ publication: { operation: 'SPLIT_SKILL', status: 'ACTIVE', releasedVersion: 0 } })).toBe('整组版本，见详情');
    expect(evolutionPublishedVersion({ publication: { operation: 'MERGE_SKILLS', status: 'STAGED' } })).toBe('整组尚未生效');
  });
  it('does not claim published group versions when a stale unpublished proposal closes', () => {
    expect(evolutionPublishedVersion({ publication: { operation: 'MERGE_SKILLS', status: 'ROLLED_BACK', releasedVersion: 0, targetSkillIds: [] } })).toBe('整组当前未生效');
    expect(evolutionPublishedVersion({ publication: { operation: 'SPLIT_SKILL', status: 'ROLLED_BACK', targetSkillIds: ['a', 'b'] } })).toBe('整组当前未生效');
  });
});
