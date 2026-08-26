import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { ChangeVerificationLauncher, verificationWorkflows } from './ChangeVerificationLauncher';
import { opsAdminService, type OpsAgentDefinition } from '../../../services/ops-admin-service';
import type { OpsChangePackage } from '../../../services/ops-change-package-types';
const navigate = vi.hoisted(() => vi.fn());
vi.mock('react-router-dom', () => ({ useNavigate: () => navigate }));
vi.mock('../../../services/ops-admin-service', () => ({ opsAdminService: {
  listChatAgents:vi.fn(), createChatSession:vi.fn(), streamUserChat:vi.fn(),
} }));
const graph:OpsAgentDefinition = {agentId:'acceptance-c',projectId:'p',version:4,lifecycle:'PUBLISHED',name:'变更后验收',nodes:[
  {nodeId:'selected',type:'ROUTER',config:{changeVerification:{operation:'SELECT_CHANGE_REQUEST'}}},
  {nodeId:'context',type:'ROUTER',config:{changeVerification:{operation:'CONTEXT_CHANGE'}}},
]};
const record:OpsChangePackage = {packageId:'cp-local',projectId:'p',title:'订单服务恢复',status:'LANDED',packageType:'STANDARD',version:3};
beforeEach(() => { vi.clearAllMocks();
  vi.mocked(opsAdminService.listChatAgents).mockResolvedValue({code:'0000',info:'OK',data:[graph]});
  vi.mocked(opsAdminService.createChatSession).mockResolvedValue({code:'0000',info:'OK',data:'session-local'});
  vi.mocked(opsAdminService.streamUserChat).mockImplementation(async (_request,onEvent)=>{
    onEvent({eventType:'RUN_STARTED',runId:'run-local'}); });
});
afterEach(cleanup);
it('uses only the selected project and a published graph supporting authoritative change selection',()=>{
  expect(verificationWorkflows([graph,{...graph,projectId:'other'},{...graph,lifecycle:'DRAFT'},
    {...graph,version:0},{...graph,nodes:graph.nodes?.slice(1)}],'p')).toEqual([graph]);
});
it('launches from ordinary UI with an immutable graph version and no claimed approval or success',async()=>{
  render(<ChangeVerificationLauncher record={record}/>);
  fireEvent.click(screen.getByRole('button',{name:'开始业务验收'}));
  await waitFor(()=>expect(opsAdminService.streamUserChat).toHaveBeenCalledTimes(1));
  const sent=vi.mocked(opsAdminService.streamUserChat).mock.calls[0][0];
  expect(sent).toMatchObject({projectId:'p',sessionId:'session-local',agentDefinitionId:'acceptance-c',agentVersion:4,
    metadata:{selectedChangePackageId:'cp-local',triggerSource:'CHANGE_VERIFICATION'}});
  expect(sent.query).toContain('订单服务恢复');expect(sent.query).not.toContain('{');
  expect(sent.metadata).not.toHaveProperty('status');expect(sent.metadata).not.toHaveProperty('approvedVersion');
  expect(navigate).toHaveBeenCalledWith('/workbench?projectId=p&sessionId=session-local&runId=run-local');
});
it('keeps the created task accessible after a lost stream and does not issue a duplicate launch',async()=>{
  vi.mocked(opsAdminService.streamUserChat).mockRejectedValue(new Error('response lost'));
  render(<ChangeVerificationLauncher record={record}/>);
  fireEvent.click(screen.getByRole('button',{name:'开始业务验收'}));
  await screen.findByRole('button',{name:'查看已创建的验收任务'});
  fireEvent.click(screen.getByRole('button',{name:'查看已创建的验收任务'}));
  expect(opsAdminService.createChatSession).toHaveBeenCalledTimes(1);
  expect(opsAdminService.streamUserChat).toHaveBeenCalledTimes(1);
});
it('never starts a task when the package has not landed or no supported graph exists',async()=>{
  const view=render(<ChangeVerificationLauncher record={{...record,status:'APPROVED'}}/>);
  expect(screen.queryByRole('button',{name:'开始业务验收'})).not.toBeInTheDocument();
  vi.mocked(opsAdminService.listChatAgents).mockResolvedValue({code:'0000',info:'OK',data:[]});
  view.rerender(<ChangeVerificationLauncher record={record}/>);
  fireEvent.click(screen.getByRole('button',{name:'开始业务验收'}));
  await screen.findByText('当前项目尚未发布可直接验收变更的工作流，请先在工作流页面完成配置与发布。');
  expect(opsAdminService.createChatSession).not.toHaveBeenCalled();
});
