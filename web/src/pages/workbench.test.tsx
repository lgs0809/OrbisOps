import { act, cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { WorkbenchPage } from './workbench';
import { opsAdminService, type OpsAnalysisTaskDetail } from '../services/ops-admin-service';
vi.mock('../services/ops-admin-service', () => ({opsAdminService:{listAnalysisTasks:vi.fn(),getAnalysisTask:vi.fn()}}));
vi.mock('../services/auth-session', () => ({isAdminUser:()=>false}));
vi.mock('../hooks/use-project-scope', () => ({useProjectScope:()=>({projectId:'project-a',projects:[],selectProject:()=>{}})}));
vi.mock('../components/ops-layout', async (importOriginal) => ({
  ...await importOriginal<typeof import('../components/ops-layout')>(),
  OpsPageShell:({children}:any)=><>{children}</>,OpsAdvancedPreview:()=>null,JsonBlock:()=>null,
}));
vi.mock('../features/execution/components/WorkflowApprovalPanel', () => ({WorkflowApprovalPanel:()=>null}));
const detail=(status:string,summary:string):OpsAnalysisTaskDetail=>({runId:'linked-run',projectId:'project-a',status,
  source:'CHAT',taskType:'CHAT',goal:'独立巡检任务',summary});
const open=()=>render(<MemoryRouter initialEntries={['/workbench?projectId=project-a&runId=linked-run']}><WorkbenchPage/></MemoryRouter>);
beforeEach(()=>{vi.mocked(opsAdminService.listAnalysisTasks).mockReset().mockResolvedValue({data:[]} as any);
  vi.mocked(opsAdminService.getAnalysisTask).mockReset();});
afterEach(()=>{cleanup();vi.useRealTimers();});
it('opens an authorized linked result even when it is absent from the inbox page',async()=>{
  vi.mocked(opsAdminService.getAnalysisTask).mockResolvedValue({data:detail('SUCCEEDED','原始采样证据已保留')} as any);
  open();await screen.findByText('原始采样证据已保留');
  expect(opsAdminService.getAnalysisTask).toHaveBeenCalledWith('project-a','linked-run','user');
});
it('searches loaded tasks while retaining the separately authorized linked detail',async()=>{
  vi.mocked(opsAdminService.listAnalysisTasks).mockResolvedValue({data:[
    {...detail('SUCCEEDED',''),runId:'run-a',goal:'服务 A 版本查询'},
    {...detail('FAILED',''),runId:'run-b',goal:'订单接口恢复'},
  ]} as any);
  vi.mocked(opsAdminService.getAnalysisTask).mockResolvedValue({data:detail('SUCCEEDED','已保存的独立任务证据')} as any);
  open();await screen.findByText('已保存的独立任务证据');
  fireEvent.change(screen.getByRole('textbox',{name:'搜索已加载的运行'}),{target:{value:'run-b'}});
  expect(screen.queryByRole('button',{name:'服务 A 版本查询'})).not.toBeInTheDocument();
  expect(screen.getByRole('button',{name:'订单接口恢复'})).toBeInTheDocument();
  expect(screen.getByText('已保存的独立任务证据')).toBeInTheDocument();
  expect(screen.getByText('匹配 1 / 2 条已加载记录')).toBeInTheDocument();
});
it('refreshes a queued linked task through completion without a manual page reload',async()=>{
  vi.mocked(opsAdminService.getAnalysisTask).mockResolvedValueOnce({data:detail('QUEUED','等待执行')} as any)
    .mockResolvedValueOnce({data:detail('RUNNING','采样进行中')} as any)
    .mockResolvedValue({data:detail('SUCCEEDED','核验完成')} as any);
  vi.useFakeTimers();open();await act(async()=>{});
  expect(screen.getByText('等待执行')).toBeInTheDocument();
  await act(async()=>{await vi.advanceTimersByTimeAsync(5000);});
  expect(screen.getByText('采样进行中')).toBeInTheDocument();
  await act(async()=>{await vi.advanceTimersByTimeAsync(5000);});
  expect(screen.getByText('核验完成')).toBeInTheDocument();
  const count=vi.mocked(opsAdminService.getAnalysisTask).mock.calls.length;
  await act(async()=>{await vi.advanceTimersByTimeAsync(15000);});
  expect(opsAdminService.getAnalysisTask).toHaveBeenCalledTimes(count);
});
it('shows internal structured tasks and unknown results without raw JSON task titles or a success claim',async()=>{
  const goal=JSON.stringify({serviceId:'checkout',alertContent:'订单延迟升高',priorEvidence:{state:'HEALTHY',queryScope:{}}});
  const record={...detail('SUCCEEDED',JSON.stringify({verdict:'UNKNOWN',count:0,allowed:false})),goal,
    evidence:[{summary:'{"outputMode":"MCP_EVIDENCE_REFERENCE"}'},{summary:'{"outputMode":"MCP_EVIDENCE_REFERENCE"}'}]};
  vi.mocked(opsAdminService.listAnalysisTasks).mockResolvedValue({data:[record]} as any);
  vi.mocked(opsAdminService.getAnalysisTask).mockResolvedValue({data:record} as any);
  open();await screen.findByRole('region',{name:'结构化返回结果'});
  expect(screen.getByRole('button',{name:'订单延迟升高'})).toBeInTheDocument();
  expect(screen.queryByRole('button',{name:goal})).not.toBeInTheDocument();
  expect(screen.getByText('UNKNOWN')).toBeInTheDocument();
  expect(screen.getByText('false')).toBeInTheDocument();
  expect(screen.getByText('证据记录（2）')).toBeInTheDocument();
  expect(screen.getAllByText('已记录一项结构化工具证据，可在技术详情中查看。')).toHaveLength(1);
  fireEvent.change(screen.getByRole('textbox',{name:'搜索已加载的运行'}),{target:{value:'checkout'}});
  expect(screen.getByRole('button',{name:'订单延迟升高'})).toBeInTheDocument();
});
it('selects an inbox result without reloading the list or reading its detail twice',async()=>{
  vi.mocked(opsAdminService.listAnalysisTasks).mockResolvedValue({data:[detail('SUCCEEDED','')]} as any);
  vi.mocked(opsAdminService.getAnalysisTask).mockResolvedValue({data:detail('SUCCEEDED','已完成的核验')} as any);
  render(<MemoryRouter initialEntries={['/workbench?projectId=project-a']}><WorkbenchPage/></MemoryRouter>);
  fireEvent.click(await screen.findByRole('button',{name:'独立巡检任务'}));
  await screen.findByText('已完成的核验');
  expect(opsAdminService.listAnalysisTasks).toHaveBeenCalledTimes(1);
  expect(opsAdminService.getAnalysisTask).toHaveBeenCalledExactlyOnceWith('project-a','linked-run','user');
});
