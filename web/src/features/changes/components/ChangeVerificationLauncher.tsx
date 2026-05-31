import React, { useRef, useState } from 'react';
import { Button, Modal, Select, Typography } from '@douyinfe/semi-ui';
import { useNavigate } from 'react-router-dom';
import { opsAdminService, type OpsAgentDefinition } from '../../../services/ops-admin-service';
import type { OpsChangePackage } from '../../../services/ops-change-package-types';
import { userFacingError } from '../../../utils/user-facing-error';

export const verificationWorkflows = (items: OpsAgentDefinition[], projectId: string) => items.filter(item =>
  item.projectId === projectId && item.lifecycle === 'PUBLISHED' && Number.isInteger(item.version) && Number(item.version) > 0
  && item.nodes?.some(node => node.config?.changeVerification?.operation === 'SELECT_CHANGE_REQUEST')
  && item.nodes.some(node => node.config?.changeVerification?.operation === 'CONTEXT_CHANGE'));

/** The normal chat runtime owns authorization, frozen graph execution, cancellation and persistence. */
export const ChangeVerificationLauncher: React.FC<{ record: OpsChangePackage }> = ({ record }) => {
  const navigate = useNavigate();
  const inFlight = useRef(false);
  const [busy, setBusy] = useState(false);
  const [options, setOptions] = useState<OpsAgentDefinition[]>([]);
  const [selected, setSelected] = useState('');
  const [error, setError] = useState('');
  const [launchedSession, setLaunchedSession] = useState('');
  if (record.status !== 'LANDED') return null;

  const launch = async (workflow: OpsAgentDefinition) => {
    const response = await opsAdminService.createChatSession({ projectId: record.projectId, agentId: workflow.agentId,
      agentVersion: workflow.version, title: `变更验收 · ${record.title || '已执行方案'}`, mode: 'AGENT', engine: 'GRAPH',
      metadata: { executionType: 'WORKFLOW', executionName: workflow.name || '变更后验收' } });
    if (response.code !== '0000' || !response.data) throw new Error('无法创建验收任务，请检查项目权限。');
    const sessionId = response.data;
    // Once a session exists, a lost response offers the saved task rather than issuing another submit.
    setLaunchedSession(sessionId);
    let opened = false;
    await opsAdminService.streamUserChat({ projectId: record.projectId, sessionId, mode: 'AGENT', engine: 'GRAPH',
      agentDefinitionId: workflow.agentId, agentVersion: workflow.version,
      query: `请只读验收已选中的“${record.title || '已执行方案'}”，按原批准标准检查实际发布效果，缺少证据时如实说明。`,
      metadata: { executionType: 'WORKFLOW', triggerSource: 'CHANGE_VERIFICATION', selectedChangePackageId: record.packageId } }, event => {
        const runId = event.runId || (typeof event.payload?.runId === 'string' ? event.payload.runId : '');
        if (runId && !opened) { opened = true; navigate(`/workbench?${new URLSearchParams({projectId: record.projectId, sessionId, runId})}`); }
      });
    if (!opened) navigate(`/chat?${new URLSearchParams({projectId: record.projectId, sessionId})}`);
  };
  const run = async (workflow?: OpsAgentDefinition) => {
    if (inFlight.current || launchedSession) return;
    inFlight.current = true; setBusy(true); setError('');
    try {
      if (workflow) { setOptions([]); await launch(workflow); return; }
      const response = await opsAdminService.listChatAgents(record.projectId);
      if (response.code !== '0000') throw new Error('无法读取当前项目的验收工作流。');
      const choices = verificationWorkflows(response.data || [], record.projectId);
      if (choices.length === 0) throw new Error('当前项目尚未发布可直接验收变更的工作流，请先在工作流页面完成配置与发布。');
      if (choices.length === 1) await launch(choices[0]);
      else { setOptions(choices); setSelected(''); }
    } catch (e) { setError(userFacingError(e, '验收请求未能确认，请查看已创建的任务后再决定下一步。')); }
    finally { inFlight.current = false; setBusy(false); }
  };
  return <section style={{marginTop:16}}>
    <Typography.Paragraph>检查实际版本、观察窗口和业务指标，保存独立的验收结论。</Typography.Paragraph>
    {launchedSession ? <Button onClick={() => navigate(`/chat?${new URLSearchParams({projectId:record.projectId,sessionId:launchedSession})}`)}>查看已创建的验收任务</Button>
      : <Button theme="solid" loading={busy} disabled={busy} onClick={() => void run()}>开始业务验收</Button>}
    {error && <Typography.Paragraph type="danger">{error}</Typography.Paragraph>}
    <Modal title="选择验收工作流" visible={options.length > 1} onCancel={() => setOptions([])}
      okText="开始验收" okButtonProps={{disabled:!selected || busy}} onOk={() => {
        const workflow = options.find(item => item.agentId === selected); if (workflow) void run(workflow);
      }}>
      <Select aria-label="验收工作流" value={selected || undefined} placeholder="选择已发布的验收工作流" style={{width:'100%'}}
        onChange={value => setSelected(String(value || ''))} optionList={options.map(item=>({value:item.agentId,label:item.name || item.agentId}))} />
    </Modal>
  </section>;
};
