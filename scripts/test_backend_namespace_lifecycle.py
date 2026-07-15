"""Failure-path checks for retaining and restoring only previously running acceptance peers."""
import runpy
from pathlib import Path
from types import SimpleNamespace
import unittest
from unittest.mock import Mock, patch

module = runpy.run_path(str(Path(__file__).with_name('backend-namespace-lifecycle.py')))
runtime = module['running_peers'].__globals__


class BackendNamespaceLifecycleTest(unittest.TestCase):
    def peer(self):
        return {'name': 'orbisops-acceptance-observability-mcp-1', 'service': 'observability-mcp',
                'image': 'same-image', 'mounts': [{'name': 'existing-volume'}], 'namespace': 'old-network'}

    def test_restore_runs_even_when_backend_change_raises(self):
        compose = Mock()
        old = self.peer()
        current = {**old, 'running': True, 'health': 'healthy', 'id': 'new-peer', 'startedAt': 'after'}
        with patch.dict(runtime, {'running_peers': lambda: ({'id': 'backend', 'startedAt': 'before'}, [old]),
                                 'namespace': lambda name: 'current-network', 'inspect': lambda name: current,
                                 'runpy': SimpleNamespace(run_path=lambda path: {'compose': compose})}):
            with self.assertRaisesRegex(RuntimeError, 'injected backend failure'):
                with module['suspended_peers']() as report:
                    raise RuntimeError('injected backend failure')
        self.assertEqual(2, compose.call_count)
        self.assertIn('stop', compose.call_args_list[0].args)
        self.assertIn('--force-recreate', compose.call_args_list[1].args)
        self.assertEqual('observability-mcp', compose.call_args_list[1].args[-1])
        self.assertTrue(report['peersAfter'][0]['imageAndMountsPreserved'])

    def test_discovers_only_running_peers_of_the_exact_backend(self):
        backend = {'id': 'backend-id', 'running': True, 'labels': {'com.docker.compose.project': 'orbisops-acceptance'}}
        peer = {**self.peer(), 'networkMode': 'container:backend-id', 'labels': {'com.docker.compose.service': 'observability-mcp'}}
        unrelated = {'networkMode': 'container:other-backend', 'labels': {}}
        check_output = Mock(return_value='peer\nunrelated\n')
        with patch.dict(runtime, {'inspect': lambda name: backend if name == runtime['BACKEND'] else peer if name == 'peer' else unrelated,
                                 'namespace': lambda name: 'network', 'subprocess': SimpleNamespace(check_output=check_output)}):
            _, peers = module['running_peers']()
        self.assertEqual(['observability-mcp'], [item['service'] for item in peers])
        self.assertIn('ps', check_output.call_args.args[0])
        self.assertIn('label=com.docker.compose.project=orbisops-acceptance', check_output.call_args.args[0])

    def test_image_or_mount_change_is_not_a_successful_restore(self):
        compose = Mock(); old = self.peer()
        current = {**old, 'running': True, 'health': 'healthy', 'image': 'different-image', 'startedAt': 'after'}
        with patch.dict(runtime, {'running_peers': lambda: ({'id': 'backend', 'startedAt': 'before'}, [old]),
                                 'namespace': lambda name: 'current-network', 'inspect': lambda name: current,
                                 'runpy': SimpleNamespace(run_path=lambda path: {'compose': compose})}):
            with self.assertRaisesRegex(RuntimeError, 'image or persisted mount changed'):
                with module['suspended_peers']():
                    pass
        self.assertEqual(2, compose.call_count)

    def test_active_work_is_rejected_before_stopping_or_restarting(self):
        admission = Mock(side_effect=RuntimeError('active work'))
        suspend = Mock()
        with patch.dict(runtime, {'suspended_peers': suspend,
                                 'runpy': SimpleNamespace(run_path=lambda path: {'require_idle_runs': admission})}):
            with self.assertRaisesRegex(RuntimeError, 'active work'):
                module['restart_backend']()
        suspend.assert_not_called()


if __name__ == '__main__':
    unittest.main()
