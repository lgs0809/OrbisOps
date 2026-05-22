import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { TaskAcceptancePanel } from './TaskAcceptancePanel';
import { draftTaskAcceptance, getTaskAcceptance, verifyTaskAcceptance, type TaskAcceptanceDraft } from '../../services/task-episode-service';
vi.mock('../../services/task-episode-service', () => ({getTaskAcceptance:vi.fn(),verifyTaskAcceptance:vi.fn(),draftTaskAcceptance:vi.fn()}));
const fixture = () => ({episodeId:'synthetic-task',goal:'核验合成目标版本',revision:2,outcome:'UNKNOWN',pendingTurns:0,
  receipts:[{result_id:'result-a',output_hash:'a'.repeat(64),status:'SUCCEEDED',tool_name:'fixture_version',run_id:'synthetic-run'}],history:[]});
const proposed = ():TaskAcceptanceDraft => ({status:'READY',explanation:'核对这次查询所报告的版本。',checks:[{label:'目标版本',operator:'EQ',expected:'v1'}],
  request:{requestId:'synthetic-draft-request',revision:2,goalReview:'核对任务所要求的目标版本与实际工具回执一致。',criteria:[{resultId:'result-a',outputHash:'a'.repeat(64),pointer:'/version',operator:'EQ',expected:'v1'}]}});
beforeEach(() => {vi.mocked(getTaskAcceptance).mockReset().mockResolvedValue(fixture());vi.mocked(verifyTaskAcceptance).mockReset();vi.mocked(draftTaskAcceptance).mockReset().mockResolvedValue(proposed());});
afterEach(cleanup);
const open = () => render(<QueryClientProvider client={new QueryClient({defaultOptions:{queries:{retry:false}}})}>
  <TaskAcceptancePanel projectId="project-a" episodeId="synthetic-task" onClose={()=>{}} />
</QueryClientProvider>);
async function prepare() {
  open();await screen.findByText('核验合成目标版本');
  fireEvent.change(screen.getByLabelText('验收要求'),{target:{value:'确认查询得到的版本是 v1。'}});
  fireEvent.click(screen.getByRole('button',{name:'整理核验条件'}));
  await screen.findByRole('button',{name:'确认并核验'});
}
it('accepts natural language without receipt IDs or JSON and verifies only after review',async()=>{
  vi.mocked(verifyTaskAcceptance).mockResolvedValue({outcome:'SUCCEEDED',acceptanceId:'synthetic-acceptance'});
  await prepare();
  expect(draftTaskAcceptance).toHaveBeenCalledWith('project-a','synthetic-task',{revision:2,instruction:'确认查询得到的版本是 v1。'});
  expect(screen.queryByLabelText('核验 1 的结果字段')).not.toBeInTheDocument();
  expect(screen.queryByText('result-a')).not.toBeInTheDocument();
  expect(verifyTaskAcceptance).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole('button',{name:'确认并核验'}));
  await waitFor(()=>expect(verifyTaskAcceptance).toHaveBeenCalledWith('project-a','synthetic-task',proposed().request));
});
it('reuses the immutable request after a lost response', async () => {
  vi.mocked(verifyTaskAcceptance).mockRejectedValueOnce(new Error('response lost')).mockResolvedValue({outcome:'SUCCEEDED',acceptanceId:'synthetic-acceptance'});
  await prepare();fireEvent.click(screen.getByRole('button',{name:'确认并核验'}));await screen.findByText('response lost');
  fireEvent.click(screen.getByRole('button',{name:'确认并核验'}));
  await waitFor(()=>expect(verifyTaskAcceptance).toHaveBeenCalledTimes(2));
  const calls=vi.mocked(verifyTaskAcceptance).mock.calls;
  expect(calls[1][2]).toEqual(calls[0][2]);expect(calls[0][2]).not.toHaveProperty('outcome');
});
it('does not reuse conditions after the user changes the requirement',async()=>{
  await prepare();fireEvent.change(screen.getByLabelText('验收要求'),{target:{value:'还要核对订单成功率。'}});
  expect(screen.queryByRole('button',{name:'确认并核验'})).not.toBeInTheDocument();expect(verifyTaskAcceptance).not.toHaveBeenCalled();
});
it('shows missing evidence without a confirmation action',async()=>{
  vi.mocked(draftTaskAcceptance).mockResolvedValue({status:'INSUFFICIENT_EVIDENCE',explanation:'缺少十五分钟观测记录。'});
  open();await screen.findByText('核验合成目标版本');fireEvent.click(screen.getByRole('button',{name:'整理核验条件'}));
  await screen.findByText('缺少十五分钟观测记录。');expect(screen.queryByRole('button',{name:'确认并核验'})).not.toBeInTheDocument();expect(verifyTaskAcceptance).not.toHaveBeenCalled();
});
it('distinguishes an unexpected provider model from a temporary network error and never offers acceptance',async()=>{
  vi.mocked(draftTaskAcceptance).mockRejectedValue(new Error('MODEL_RESPONSE_IDENTITY_MISMATCH'));
  open();await screen.findByText('核验合成目标版本');fireEvent.click(screen.getByRole('button',{name:'整理核验条件'}));
  await screen.findByText('模型服务返回的模型与配置不一致，已拒绝该结果，未提交验收。请检查模型连接配置。');
  expect(screen.queryByRole('button',{name:'确认并核验'})).not.toBeInTheDocument();expect(verifyTaskAcceptance).not.toHaveBeenCalled();
});
it('keeps pending classification visible without sending a model request',async()=>{
  vi.mocked(getTaskAcceptance).mockResolvedValue({...fixture(),pendingTurns:1});open();
  await screen.findByText('后台还在整理最新对话，整理完成后可核验；你可以继续正常对话。');
  expect(screen.getByRole('button',{name:'整理核验条件'})).toBeDisabled();expect(draftTaskAcceptance).not.toHaveBeenCalled();
});
it('explains a rejected metadata-only draft without showing an internal error code',async()=>{
  vi.mocked(draftTaskAcceptance).mockRejectedValue(new Error('HTTP 400 : TASK_ACCEPTANCE_NEEDS_TASK_RESULT_NOT_RECEIPT_METADATA'));
  open();await screen.findByText('核验合成目标版本');fireEvent.click(screen.getByRole('button',{name:'整理核验条件'}));
  await screen.findByText('生成的条件没有核对实际任务结果，已拒绝提交。请重新整理核验条件。');
  expect(screen.queryByText(/HTTP 400/)).not.toBeInTheDocument();expect(verifyTaskAcceptance).not.toHaveBeenCalled();
});
it('rejects a stale draft and explains network failure without inventing success',async()=>{
  vi.mocked(draftTaskAcceptance).mockResolvedValue({...proposed(),request:{...proposed().request!,revision:1}});
  await prepare();expect(screen.getByRole('button',{name:'确认并核验'})).toBeDisabled();
  vi.mocked(draftTaskAcceptance).mockRejectedValue(new Error('MODEL_PROVIDER_UNAVAILABLE'));
  fireEvent.click(screen.getByRole('button',{name:'整理核验条件'}));
  await screen.findByText('模型或网络暂时不可用，未提交验收结果。请稍后重试。');expect(verifyTaskAcceptance).not.toHaveBeenCalled();
});
