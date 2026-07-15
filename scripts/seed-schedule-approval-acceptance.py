#!/usr/bin/env python3
"""Create a disabled synthetic approval schedule via APIs; --start explicitly runs it once.
No model, notification or production resource writes. Existing fixtures are preserved.
"""
import argparse
import json
from pathlib import Path
import runpy

ROOT = Path(__file__).resolve().parents[1]
PROJECT = 'ops-acceptance-a'
NAME = 'OPS-02 周期任务审批状态验收（合成）'


def prepare(start=False):
    h = runpy.run_path(str(ROOT / 'scripts/test-mcp-runtime.py'))
    api, token = h['api'], h['token']
    definition = runpy.run_path(str(ROOT / 'scripts/seed-workflow-acceptance.py'))['prepare'](
        1, 'ops-acceptance-schedule-approval')
    body = dict(projectId=PROJECT, executionType='WORKFLOW', agentId=definition['agentId'],
                agentBindingMode='PINNED_VERSION', agentVersion=definition['version'], taskName=NAME,
                description='合成状态机验收：两个审批节点，无模型或业务写入；保持禁用，仅手动执行。',
                cronExpression='0 0 0 * * *', status=0, taskParam='验证两个审批等待及恢复后的周期执行状态。',
                notifyChannel=False, lightweightScreeningEnabled=False, rangeMinutes=5, promWindow='5m',
                nodeTimeoutSeconds=120, maxEvidenceItems=20, subAgentMaxIterations=3)
    base = '/api/v1/admin/task-schedule'
    def existing():
        return next((x for x in api(base+'/list?projectId='+PROJECT, token=token) if x['taskName']==NAME), None)
    schedule = existing()
    if schedule is None:
        api(base+'/create','POST',body,token)
        schedule = existing()
    for key in ('agentId','agentVersion','status','taskParam'):
        if schedule.get(key) != body[key]:
            raise RuntimeError('Existing fixture differs; preserved: '+key)
    result = dict(scope='SYNTHETIC_APPROVAL_STATE_MACHINE', scheduleId=schedule['id'],
                  agentId=definition['agentId'], version=definition['version'], productionWrites=False)
    if start:
        execution = api(base+'/run-now/'+str(schedule['id'])+'?projectId='+PROJECT,'POST',{},token)
        result.update(executionId=execution,runId=f"task_{schedule['id']}_{execution}")
    return result


if __name__ == '__main__':
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--start',action='store_true');p.add_argument('--output',required=True,type=Path)
    args=p.parse_args()
    if args.output.exists(): p.error('Output exists; preserving it')
    result=prepare(args.start)
    args.output.parent.mkdir(parents=True,exist_ok=True)
    args.output.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
    print(json.dumps(result,ensure_ascii=False))
