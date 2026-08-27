import datetime
import re
import unittest
from deployment_control import DeploymentControl, version_schema
from landing_tool_contract import tools


class LandingToolContractTest(unittest.TestCase):
    def test_both_writers_and_test_validation_share_the_target_version_boundary(self):
        catalog = {tool["name"]: tool for tool in tools()}
        self.assertEqual(7, len(catalog))
        for name in ["test_apply_configuration", "prod_apply_configuration", "test_validate_configuration"]:
            for key in ["expectedVersion", "version"] if name.endswith("apply_configuration") else ["expectedVersion"]:
                self.assertEqual(version_schema(), catalog[name]["inputSchema"]["properties"][key])
        self.assertTrue(catalog["prod_apply_configuration"]["annotations"]["destructiveHint"])
        self.assertIn("executionKey", catalog["prod_apply_configuration"]["inputSchema"]["required"])

    def test_long_version_cannot_reach_the_database(self):
        calls = []
        control = DeploymentControl(lambda query: calls.append(query), lambda value: repr(value), "p", "token", ["s"])
        for value in ["a" * 21, "invalid/version", "_prefix"]:
            body = dict(projectId="p", service="s", expectedVersion="baseline", version=value, scenario="HEALTHY",
                        executionKey="own-request", actor="local-reviewer", deadline=(datetime.datetime.now(datetime.timezone.utc)+datetime.timedelta(seconds=30)).isoformat())
            self.assertIsNone(re.fullmatch(version_schema()["pattern"], value))
            with self.assertRaisesRegex(ValueError, "INVALID_VERSION"):
                control.apply(body)
        self.assertEqual([], calls)
        self.assertIsNotNone(re.fullmatch(version_schema()["pattern"], "a" * 20))


if __name__ == "__main__":
    unittest.main()
