#!/usr/bin/env python3
"""Read-only run, model retry and MCP disclosure evidence; never treats a completed Run as business acceptance."""
import argparse
import json
from pathlib import Path
import runpy

ROOT = Path(__file__).resolve().parents[1]


def inspect(run_id, output):
    if output.exists() or output.with_suffix('.sql').exists():
        raise ValueError('Use a new output name; previous evidence is retained')
    support = runpy.run_path(str(ROOT / 'scripts/test-mcp-runtime.py'))
    run = support['quoted'](run_id)
    queries = {
        'run': "SELECT JSON_OBJECT('runId',run_id,'status',status,'error',error_message) "
               'FROM ai_ops_agent_run WHERE run_id=' + run,
        'events': """SELECT JSON_OBJECT('eventId',id,'type',event_type,'status',status,'summary',summary,
          'requestedModel',JSON_EXTRACT(payload_json,'$.requestedModel'),
          'responseModel',JSON_EXTRACT(payload_json,'$.responseModel'),
          'attempt',JSON_EXTRACT(payload_json,'$.attempt'),'maxAttempts',JSON_EXTRACT(payload_json,'$.maxAttempts'),
          'remainingMs',JSON_EXTRACT(payload_json,'$.remainingMs'),'delayMs',JSON_EXTRACT(payload_json,'$.delayMs'),
          'reasonCode',JSON_EXTRACT(payload_json,'$.reasonCode'),'timeoutSeconds',JSON_EXTRACT(payload_json,'$.timeoutSeconds'),
          'toolName',JSON_EXTRACT(payload_json,'$.toolName'),'schemas',JSON_EXTRACT(payload_json,'$.tools'),
          'discoveryMode',JSON_EXTRACT(payload_json,'$.mode'),'toolCount',JSON_EXTRACT(payload_json,'$.toolCount'),
          'summaryTokens',JSON_EXTRACT(payload_json,'$.summaryTokens'),'tokenizer',JSON_EXTRACT(payload_json,'$.tokenizer'),
          'searchHits',JSON_EXTRACT(payload_json,'$.hits'))
          FROM ai_ops_agent_node_trace WHERE run_id=""" + run
          + " AND (event_type LIKE 'MODEL_%' OR event_type LIKE 'MCP_SCHEMA_%' OR event_type IN ('MCP_DISCOVERY_MODE','MCP_TOOL_SEARCH') OR event_type='TOOL_CALL_FINISHED') ORDER BY id",
    }
    result = {name: support['rows'](query) for name, query in queries.items()}
    result['scope'] = 'READ_ONLY_RUN_AND_TELEMETRY_NOT_BUSINESS_ACCEPTANCE'
    result['status'] = 'OBSERVED' if result['run'] else 'NOT_FOUND'
    output.parent.mkdir(parents=True, exist_ok=True)
    output.with_suffix('.sql').write_text(';\n'.join(queries.values()) + ';\n')
    output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps({'status': result['status'], 'run': result['run'], 'eventCount': len(result['events'])}, ensure_ascii=False))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--run-id', required=True)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    inspect(args.run_id, args.output)
