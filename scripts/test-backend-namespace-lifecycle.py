#!/usr/bin/env python3
"""Deployment safety checks with no Docker side effects."""
import importlib.util
from pathlib import Path
import unittest
from unittest.mock import patch, Mock

spec = importlib.util.spec_from_file_location('lifecycle', Path(__file__).with_name('backend-namespace-lifecycle.py'))
lifecycle = importlib.util.module_from_spec(spec)
spec.loader.exec_module(lifecycle)


def backend(running=False, project=lifecycle.PROJECT):
    return dict(id='backend-id', running=running, startedAt='start', labels={'com.docker.compose.project': project},
                networkMode='default', mounts=[], image='backend-image')


class NamespaceLifecycleSafetyTest(unittest.TestCase):
    def test_cold_deployment_does_not_start_previously_stopped_peers(self):
        compose = Mock()
        with patch.object(lifecycle, 'inspect', side_effect=[backend(), backend(True)]), \
                patch.object(lifecycle.subprocess, 'check_output', return_value=''), \
                patch.object(lifecycle.runpy, 'run_path', return_value={'compose': compose}), \
                patch.object(lifecycle, 'namespace', return_value='new-namespace') as network:
            with lifecycle.suspended_peers() as report:
                self.assertFalse(report['backendBefore']['running'])
                self.assertIsNone(report['backendBefore']['namespace'])
            self.assertTrue(report['backendAfter']['running'])
            self.assertEqual('new-namespace', report['backendAfter']['namespace'])
            self.assertFalse(report['previouslyStoppedPeersStarted'])
            compose.assert_not_called()
            network.assert_called_once_with(lifecycle.BACKEND)

    def test_live_peer_using_stopped_backend_blocks_before_any_mutation(self):
        peer = dict(backend(True), networkMode='container:backend-id')
        compose = Mock()
        with patch.object(lifecycle, 'inspect', side_effect=[backend(), peer]), \
                patch.object(lifecycle.subprocess, 'check_output', return_value='old-peer\n'), \
                patch.object(lifecycle.runpy, 'run_path', return_value={'compose': compose}):
            with self.assertRaisesRegex(RuntimeError, 'peer is still using'):
                with lifecycle.suspended_peers():
                    self.fail('Deployment must not begin')
            compose.assert_not_called()

    def test_foreign_project_is_rejected(self):
        with patch.object(lifecycle, 'inspect', return_value=backend(False, 'production')), \
                patch.object(lifecycle.subprocess, 'check_output') as docker:
            with self.assertRaisesRegex(RuntimeError, 'local acceptance'):
                lifecycle.deployment_peers()
            docker.assert_not_called()

    def test_failed_cold_deployment_preserves_original_error_and_never_starts_peers(self):
        compose = Mock()
        with patch.object(lifecycle, 'inspect', side_effect=[backend(), backend()]), \
                patch.object(lifecycle.subprocess, 'check_output', return_value=''), \
                patch.object(lifecycle.runpy, 'run_path', return_value={'compose': compose}), \
                patch.object(lifecycle, 'namespace') as network:
            with self.assertRaisesRegex(ValueError, 'deployment rejected'):
                with lifecycle.suspended_peers():
                    raise ValueError('deployment rejected')
            compose.assert_not_called()
            network.assert_not_called()


if __name__ == '__main__':
    unittest.main()
