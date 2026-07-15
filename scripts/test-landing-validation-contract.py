#!/usr/bin/env python3
"""Unit checks for read-only validation; real component checks remain separate."""
import ast
import datetime
import json
from pathlib import Path
import unittest
from unittest.mock import patch
import urllib.error
import urllib.request

source = Path(__file__).resolve().parents[1] / 'deploy/acceptance/landing-mcp.py'
tree = ast.parse(source.read_text())
functions = ast.Module(body=[n for n in tree.body if isinstance(n, ast.FunctionDef) and n.name in ('tools', 'execute')], type_ignores=[])
context = dict(datetime=datetime, json=json, urllib=urllib,
               PROJECT='ops-acceptance-a', SERVICE='ops-acc-a-service-2',
               TARGETS={'test': ('http://test', 'private-test'), 'prod': ('http://prod', 'private-prod')})
exec(compile(functions, str(source), 'exec'), context)


class Response:
    def __init__(self, data, code=200, trace=''):
        self.data, self.code, self.headers = data, code, {'X-Trace-Id': trace}
    def read(self):
        return json.dumps(self.data).encode()
    def __enter__(self):
        return self
    def __exit__(self, *_):
        pass


class ValidationContract(unittest.TestCase):
    def run_case(self, *, changed=False, failed=False, stale=False, duplicate=False):
        count = 0
        calls = []
        def open_target(request, timeout):
            nonlocal count
            calls.append(request)
            if isinstance(request, urllib.request.Request):
                self.assertEqual('GET', request.get_method())
                version = 'v3' if changed and count else 'v2'
                return Response({'version': version, 'scenario': 'HEALTHY'})
            count += 1
            return Response({'version': 'v2'}, 503 if failed and count == 2 else 200,
                            'same' if duplicate else f'trace-{count}')
        with patch.object(urllib.request, 'urlopen', side_effect=open_target):
            result = context['execute']('test_validate_configuration', {
                'projectId': context['PROJECT'], 'service': context['SERVICE'],
                'expectedVersion': 'v1' if stale else 'v2', 'scenario': 'HEALTHY'})
        self.assertEqual(22, len(calls))
        self.assertEqual(20, result['requestCount'])
        self.assertIn('beforeState', result)
        self.assertIn('afterState', result)
        return result
    def test_success_is_read_only(self):
        self.assertEqual('PASSED', self.run_case()['status'])
    def test_version_change_during_requests_fails(self):
        self.assertEqual('FAILED', self.run_case(changed=True)['status'])
    def test_any_failed_request_fails(self):
        self.assertEqual('FAILED', self.run_case(failed=True)['status'])
    def test_stale_expected_version_fails(self):
        self.assertEqual('FAILED', self.run_case(stale=True)['status'])
    def test_duplicate_trace_fails(self):
        self.assertEqual('FAILED', self.run_case(duplicate=True)['status'])
    def test_production_validation_is_not_exposed(self):
        names = [tool['name'] for tool in context['tools']()]
        self.assertIn('test_validate_configuration', names)
        self.assertNotIn('prod_validate_configuration', names)
        with self.assertRaisesRegex(ValueError, 'TEST_VALIDATION_CONTRACT_REQUIRED'):
            context['execute']('prod_validate_configuration', {'projectId': context['PROJECT'],
                               'service': context['SERVICE'], 'expectedVersion': 'v2', 'scenario': 'HEALTHY'})


if __name__ == '__main__':
    unittest.main()
