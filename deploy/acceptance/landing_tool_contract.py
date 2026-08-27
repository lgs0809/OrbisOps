"""Pure MCP catalog sharing the actual target's bounded version contract."""
from deployment_control import version_schema

PROJECT, SERVICE = "ops-acceptance-a", "ops-acc-a-service-2"
TARGETS = ("test", "prod")


def tools():
    result = []
    for environment in TARGETS:
        for action in ("read_state", "check_orders", "apply_configuration", *(["validate_configuration"] if environment == "test" else [])):
            write = action == "apply_configuration"
            properties = {"projectId": {"type": "string", "enum": [PROJECT]},
                          "service": {"type": "string", "enum": [SERVICE], "description": "验收服务 A2，订单查询服务"},
                          "actor": {"type": "string", "minLength": 1, "maxLength": 128}}
            required = ["projectId", "service"]
            if write:
                properties.update({key: {"type": "string", "minLength": 1, "maxLength": 256}
                                   for key in ("expectedVersion", "version", "executionKey", "deadline")})
                properties.update({key: version_schema() for key in ("expectedVersion", "version")})
                properties["scenario"] = {"type": "string", "enum": ["HEALTHY", "FAULT", "SLOW_SQL"]}
                required += ["expectedVersion", "version", "scenario"]
                if environment == "prod":
                    required += ["actor", "executionKey", "deadline"]
            if action == "validate_configuration":
                properties.update({"expectedVersion": version_schema(),
                                   "scenario": {"type": "string", "enum": ["HEALTHY"]}})
                required += ["expectedVersion", "scenario"]
            result.append({"name": environment + "_" + action,
                "description": f"Local isolated {environment} connection: {action}. Resource identity is service://{SERVICE}/{environment}. "
                    + ("Changes actual order-service configuration with expected-version CAS and an atomic receipt."
                       if write else "Reads the actual target. validate_configuration verifies the expected HEALTHY test version before and after twenty real order GETs; it never rewrites a repaired target. check_orders only observes requests."),
                "inputSchema": {"type": "object", "additionalProperties": False, "properties": properties, "required": required},
                "outputSchema": {"type": "object", "required": ["status", "resourceKey", "environment", "projectId"],
                                 "properties": {k: {"type": "string"} for k in ("status", "resourceKey", "environment", "projectId")}},
                "annotations": {"readOnlyHint": not write, "destructiveHint": write, "idempotentHint": action not in ("check_orders", "validate_configuration")}})
    return result

