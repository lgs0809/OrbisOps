import React, { useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Button, Modal, Space, Tag, TextArea, Typography } from '@douyinfe/semi-ui';
import { draftTaskAcceptance, getTaskAcceptance, verifyTaskAcceptance, type TaskAcceptanceDraft } from '../../services/task-episode-service';

const outcomes: Record<string, string> = { SUCCEEDED:'验收通过', FAILED:'验收未通过', UNKNOWN:'结果待验证' };
const operators: Record<string,string> = {EQ:'等于',LE:'不超过',GE:'至少'};
const errorText = (error: unknown) => {
  const raw = error instanceof Error ? error.message : '';
  if (/REVISION_CHANGED|PENDING_TURNS/.test(raw)) return '任务有新进展，请刷新后按最新内容核验。';
  if (/MODEL_RESPONSE_IDENTITY_(MISMATCH|MISSING)/.test(raw)) return '模型服务返回的模型与配置不一致，已拒绝该结果，未提交验收。请检查模型连接配置。';
  if (/MODEL_|DRAFT_UNAVAILABLE|DEADLINE|Failed to fetch|请求超时/.test(raw)) return '模型或网络暂时不可用，未提交验收结果。请稍后重试。';
  if (/EVIDENCE_TOO_LARGE/.test(raw)) return '任务证据较多，暂时无法完整整理验收条件，尚未提交结果。';
  if (/NEEDS_TASK_RESULT_NOT_RECEIPT_METADATA/.test(raw)) return '生成的条件没有核对实际任务结果，已拒绝提交。请重新整理核验条件。';
  if (/DRAFT_|ASSERTION|EVIDENCE_/.test(raw)) return '目前无法从完整证据中整理出可靠的核验条件，请补充期望结果后重试。';
  return raw || '核验未完成，请稍后重试。';
};
export const TaskAcceptancePanel: React.FC<{projectId:string;episodeId:string;onClose:()=>void}> = ({projectId,episodeId,onClose}) => {
  const cache = useQueryClient();
  const query = useQuery({queryKey:['task-acceptance',projectId,episodeId],queryFn:()=>getTaskAcceptance(projectId,episodeId)});
  const [instruction,setInstruction] = useState('请根据本次任务原定目标和已有证据，整理需要核验的结果。证据不足时说明缺什么。');
  const [draft,setDraft] = useState<TaskAcceptanceDraft>();
  const [error,setError] = useState('');
  const [busy,setBusy] = useState<'draft'|'verify'|''>('');
  const data = query.data;
  const prepare = async () => {
    if (!data) return;
    setError('');setDraft(undefined);setBusy('draft');
    try { setDraft(await draftTaskAcceptance(projectId,episodeId,{revision:data.revision,instruction})); }
    catch (e) { setError(errorText(e)); }
    finally { setBusy(''); }
  };
  const submit = async () => {
    if (!draft?.request || !data || draft.request.revision!==data.revision || data.pendingTurns>0) return;
    setError('');setBusy('verify');
    try {
      // Keep this exact request after a lost response; the server's immutable ID prevents duplicate acceptance.
      await verifyTaskAcceptance(projectId,episodeId,draft.request);
      setDraft(undefined);
      await cache.invalidateQueries({queryKey:['task-acceptance',projectId,episodeId]});
      await cache.invalidateQueries({queryKey:['task-episodes',projectId]});
    } catch (e) { setError(errorText(e)); }
    finally { setBusy(''); }
  };
  return <Modal title="任务验收" visible onCancel={onClose} footer={null} width="min(700px, 100vw)" bodyStyle={{maxHeight:'75vh',overflowY:'auto'}}>
    {query.isLoading && <Typography.Paragraph>正在读取任务与证据…</Typography.Paragraph>}
    {query.isError && <Typography.Paragraph type="danger">无法读取验收记录，请检查项目权限。</Typography.Paragraph>}
    {data && <>
      <Typography.Paragraph strong>{data.goal}</Typography.Paragraph>
      <Space><Tag>版本 {data.revision}</Tag><Tag>{outcomes[data.outcome] || data.outcome}</Tag></Space>
      <Typography.Paragraph type="tertiary">直接说明你要核验什么。系统会结合任务目标和已保存的证据整理条件，确认后计算实际结果。</Typography.Paragraph>
      {data.pendingTurns > 0 && <Typography.Paragraph type="warning">后台还在整理最新对话，整理完成后可核验；你可以继续正常对话。</Typography.Paragraph>}
      <TextArea aria-label="验收要求" placeholder="例如：确认目标服务已经恢复，订单请求都成功；如果证据不足，请告诉我还缺什么。" value={instruction} disabled={Boolean(busy)}
        onChange={value=>{setInstruction(value);setDraft(undefined);setError('');}} />
      <Button theme="solid" loading={busy==='draft'} disabled={Boolean(busy) || data.pendingTurns>0 || !instruction.trim()} onClick={prepare} style={{marginTop:12}}>整理核验条件</Button>
      {busy==='draft' && <Typography.Paragraph type="tertiary">正在整理，网络波动时会自动重试，尚未提交验收。</Typography.Paragraph>}
      {draft && <section style={{marginTop:16}}>
        <Typography.Paragraph>{draft.explanation}</Typography.Paragraph>
        {draft.status==='READY' && <>
          <ul>{draft.checks?.map((check,index)=><li key={index}>{check.label}：{operators[check.operator] || check.operator} {String(check.expected)}</li>)}</ul>
          <Typography.Paragraph>{draft.request?.goalReview}</Typography.Paragraph>
          {draft.request?.revision!==data.revision && <Typography.Paragraph type="warning">任务已更新，请重新整理核验条件。</Typography.Paragraph>}
          <Button theme="solid" type="primary" loading={busy==='verify'} disabled={Boolean(busy) || data.pendingTurns>0 || draft.request?.revision!==data.revision} onClick={submit}>确认并核验</Button>
        </>}
      </section>}
      {error && <Typography.Paragraph type="danger">{error}</Typography.Paragraph>}
      <Typography.Title heading={5} style={{marginTop:20}}>历史验收记录</Typography.Title>
      {data.history.length===0 && <Typography.Paragraph>尚未进行任务验收。</Typography.Paragraph>}
      {data.history.map(item=><details key={item.acceptance_id}><summary>版本 {item.episode_revision} · {outcomes[item.outcome] || item.outcome}</summary><AcceptanceHistory record={item.record_json} /></details>)}
    </>}
  </Modal>;
};
const AcceptanceHistory: React.FC<{record:string}> = ({record}) => {
  try {
    const item=JSON.parse(record) as {goalReview?:string;checks?:Array<{verdict:string}>};
    const passed=item.checks?.filter(c=>c.verdict==='PASSED').length || 0;
    return <><Typography.Paragraph>{item.goalReview}</Typography.Paragraph><Typography.Paragraph>已核对 {item.checks?.length || 0} 项，其中 {passed} 项通过。</Typography.Paragraph></>;
  } catch { return <Typography.Paragraph>历史记录暂时无法展示，请查看运行记录。</Typography.Paragraph>; }
};
