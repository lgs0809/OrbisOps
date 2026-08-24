package cn.lgs.orbisops.trigger.ops.change;

import cn.lgs.orbisops.application.changepackage.ChangePackageCommands;
import cn.lgs.orbisops.application.changepackage.ChangePackagePreparationPlan;
import cn.lgs.orbisops.application.changepackage.PrepareChangePackageUseCase;
import cn.lgs.orbisops.application.mcpexecution.McpExecutionApplicationService;
import cn.lgs.orbisops.application.toolexecution.ToolExecutionApplicationService;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionRecordedResult;
import cn.lgs.orbisops.domain.worksession.run.model.WorkSessionRunClaim;
import cn.lgs.orbisops.trigger.application.mcpexecution.OpsMcpExecutionMapper;
import cn.lgs.orbisops.trigger.application.toolexecution.OpsToolExecutionCatalogAdapter;
import cn.lgs.orbisops.trigger.application.toolexecution.OpsToolExecutionMapper;
import cn.lgs.orbisops.trigger.application.toolexecution.dispatch.OpsChangePackageToolExecutionDispatchHandler;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentChatRequest;
import cn.lgs.orbisops.trigger.ops.runtime.OpsChangePackageRuntimeToolContributor;
import cn.lgs.orbisops.trigger.ops.runtime.OpsRuntimeEvent;
import cn.lgs.orbisops.trigger.ops.runtime.OpsWorkSessionClaimMetadata;
import cn.lgs.orbisops.trigger.ops.runtime.OpsWorkSessionRunMapper;
import cn.lgs.orbisops.trigger.ops.toolset.OpsToolExecutionService;
import cn.lgs.orbisops.trigger.ops.toolset.OpsToolsetCatalogService;
import cn.lgs.orbisops.trigger.ops.toolset.OpsToolsetRegistry;
import cn.lgs.orbisops.trigger.ops.toolset.OpsToolsetRouter;
import com.alibaba.fastjson.JSON;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.ObjectProvider;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsChangePackageToolProviderTest {

    @Test
    void shouldDiscloseRecoveryObjectTypesAndNestedPostChecksToTheActualModelTool() {
        var provider = new OpsChangePackageToolProvider((OpsToolExecutionService) null);
        var callback = provider.build("project", "alice", "run", "bundle", "hash", List.of(), List.of());
        var schema = JSON.parseObject(callback.getToolDefinition().inputSchema());
        var action = schema.getJSONObject("properties").getJSONObject("actions").getJSONObject("items");
        var fields = action.getJSONObject("properties");
        for (var key : List.of("preconditions", "postCheck", "rollbackPlan", "rollbackPrecondition", "manualFallback")) {
            assertEquals("object", fields.getJSONObject(key).getString("type"), key);
        }
        assertFalse(action.getJSONArray("required").contains("manualFallback"), "validation actions remain valid");
        assertFalse(fields.containsKey("additionalChecks"));
        var checks = fields.getJSONObject("postCheck").getJSONObject("properties").getJSONObject("additionalChecks");
        assertEquals(15, checks.getIntValue("maxItems"));
        assertFalse(checks.getJSONObject("items").getJSONObject("properties").containsKey("additionalChecks"));
        assertFalse(checks.getJSONObject("items").getBooleanValue("additionalProperties"));
        assertTrue(fields.getJSONObject("arguments").getBooleanValue("additionalProperties"), "dynamic MCP arguments retain their own contract");
    }

    @Test
    void shouldCreateEvidenceBoundChangePackage() {
        OpsChangePackagePreparationService preparationService = mock(OpsChangePackagePreparationService.class);
        when(preparationService.prepare(any(), eq("alice"))).thenReturn(
                ChangePackagePreparationPlan.from(Map.of(
                        "projectId", "demo-project",
                        "packageId", "cp-1001",
                        "status", "REVIEWING",
                        "version", 1,
                        "packageHash", "hash-1",
                        "riskLevel", "HIGH")));
        OpsChangePackageToolProvider provider =
                new OpsChangePackageToolProvider(executionService(preparationService));
        List<OpsRuntimeEvent> events = List.of(OpsRuntimeEvent.builder()
                .eventType("TOOL_CALL_FINISHED")
                .nodeId("prometheus-investigation")
                .status("SUCCEEDED")
                .summary("Prometheus 查询完成")
                .payload(Map.of(
                        "toolName", "prometheus.query",
                        "toolKind", "mcp",
                        "owner", "node:prometheus-investigation",
                        "output", "{\"up\":0,\"instance\":\"order-service-01\"}",
                        "durationMs", 42))
                .build());

        ToolCallback tool = provider.build(
                "demo-project", "alice", "run-1001", "bundle-1", "bundle-hash-1",
                events, List.of("demo-project-local-runtime"));
        assertFalse(tool.getToolDefinition().inputSchema().contains("preparationGraphId"));
        String output = tool.call("""
                {
                  "title": "处理异常示例实例",
                  "summary": "Prometheus 证据显示目标实例不可用",
                  "diagnosis": "order-service-01 的 up 指标为 0",
                  "riskLevel": "HIGH",
                  "actions": [{
                    "operationId": "op-1",
                    "mcpId": "order-service-mcp",
                    "toolName": "restart_preview",
                    "effectType": "DRY_RUN",
                    "effectScope": "TARGET_ENVIRONMENT",
                    "riskLevel": "MEDIUM",
                    "arguments": {"scope": "order-service-01"}
                  }]
                }
                """);

        assertEquals("cp-1001", JSON.parseObject(output).getString("packageId"));
        assertEquals("REVIEWING", JSON.parseObject(output).getString("status"));
        assertEquals("PROPOSE_ONLY", JSON.parseObject(output).getString("changePackageBehavior"));
        assertEquals("tool-result-test", JSON.parseObject(output).getString("resultId"));
        assertEquals("evidence-test", JSON.parseObject(output).getString("evidenceId"));
        assertEquals("a".repeat(64), JSON.parseObject(output).getString("outputHash"));
        verify(preparationService).prepare(any(), eq("alice"));
    }

    @Test
    void shouldRejectPackageWithoutSuccessfulEvidence() {
        OpsChangePackagePreparationService preparationService = mock(OpsChangePackagePreparationService.class);
        OpsChangePackageToolProvider provider = new OpsChangePackageToolProvider((OpsToolExecutionService) null);
        ToolCallback tool = provider.build(
                "demo-project", "alice", "run-1002", "bundle-2", "bundle-hash-2",
                List.of(), List.of("demo-project-local-runtime"));

        RuntimeException error = assertThrows(RuntimeException.class, () -> tool.call("""
                {
                  "title": "无证据变更包",
                  "summary": "不应创建",
                  "actions": []
                }
                """));
        assertTrue(String.valueOf(error.getMessage()).contains("证据"));
    }

    @Test
    void shouldPolicyBindActionsBeforePrepareValidationExecution() {
        OpsToolExecutionService toolExecutionService = mock(OpsToolExecutionService.class);
        when(toolExecutionService.execute(any(), eq("alice"))).thenReturn(Map.of(
                "packageId", "cp-bound",
                "status", "READY_FOR_REVIEW",
                "version", 1,
                "packageHash", "hash-bound",
                "riskLevel", "MEDIUM"));
        OpsChangePackageActionPolicyBinder binder = mock(OpsChangePackageActionPolicyBinder.class);
        when(binder.proposalCatalog("demo-project")).thenReturn(List.of());
        when(binder.bind(eq("demo-project"), any())).thenReturn(List.of(Map.of(
                "operationId", "restart_service_dry_run",
                "mcpId", "order-service-control-mcp",
                "toolName", "restart_service_dry_run",
                "effectType", "DRY_RUN",
                "effectScope", "VALIDATION_SANDBOX",
                "mutability", "EPHEMERAL",
                "riskLevel", "MEDIUM",
                "arguments", Map.of("service", "order-service"))));
        OpsChangePackageToolProvider provider =
                new OpsChangePackageToolProvider(toolExecutionService, null, binder);
        List<OpsRuntimeEvent> events = List.of(OpsRuntimeEvent.builder()
                .eventType("TOOL_CALL_FINISHED")
                .status("SUCCEEDED")
                .payload(Map.of(
                        "toolName", "project_mcp_demo_openapi_prod_readonly_mcp",
                        "toolKind", "mcp",
                        "output", "resolved"))
                .build());
        ToolCallback tool = provider.build(
                "demo-project", "alice", "run-bound", "bundle-1", "bundle-hash-1",
                events, List.of("demo-project-local-runtime"));

        tool.call("""
                {
                  "title":"bound before validation",
                  "summary":"test",
                  "actions":[{
                    "operationId":"restart_service_dry_run",
                    "mcpId":"order-service-control-mcp",
                    "toolName":"restart_service_dry_run",
                    "arguments":{"service":"model-guess","expectedVersion":{"minimum":0}}
                  }]
                }
                """);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> execution = ArgumentCaptor.forClass(Map.class);
        verify(toolExecutionService).execute(execution.capture(), eq("alice"));
        @SuppressWarnings("unchecked")
        Map<String, Object> arguments = (Map<String, Object>) execution.getValue().get("arguments");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> steps = (List<Map<String, Object>>) arguments.get("mcpSteps");
        @SuppressWarnings("unchecked")
        Map<String, Object> stepArguments = (Map<String, Object>) steps.get(0).get("arguments");
        assertEquals(Map.of("service", "order-service"), stepArguments);
    }

    @Test
    void shouldRejectHighRiskPackageWithoutRequiredAuthoritativeSources() {
        OpsChangePackageActionPolicyBinder binder = mock(OpsChangePackageActionPolicyBinder.class);
        when(binder.proposalCatalog("demo-project")).thenReturn(List.of());
        when(binder.bind(eq("demo-project"), any())).thenReturn(List.of(Map.of(
                "operationId", "restart_service",
                "mcpId", "order-service-control-mcp",
                "toolName", "restart_service",
                "effectType", "EXECUTE_EXTERNAL_ACTION",
                "effectScope", "PRODUCTION",
                "mutability", "PROD_MUTATING",
                "riskLevel", "HIGH",
                "requiresDryRun", true,
                "arguments", Map.of("service", "order-service"))));
        OpsChangePackageToolProvider provider =
                new OpsChangePackageToolProvider((OpsToolExecutionService) null, null, binder);
        OpsAgentChatRequest runtimeRequest = OpsAgentChatRequest.builder()
                .metadata(new java.util.LinkedHashMap<>(Map.of(
                        OpsChangePackageRuntimeToolContributor.AVAILABLE_AUTHORITATIVE_SOURCE_TYPES_KEY,
                        List.of("PROMETHEUS", "ELASTICSEARCH"))))
                .build();
        List<OpsRuntimeEvent> events = List.of(OpsRuntimeEvent.builder()
                .eventType("TOOL_CALL_FINISHED")
                .status("SUCCEEDED")
                .payload(Map.of(
                        "toolName", "project_mcp_demo_openapi_prod_readonly_mcp",
                        "toolKind", "mcp",
                        "output", "resolved"))
                .build());
        ToolCallback tool = provider.build(
                "demo-project", "alice", "run-evidence-gate", "bundle-1", "bundle-hash-1",
                events, List.of("demo-project-local-runtime"), runtimeRequest);

        RuntimeException error = assertThrows(RuntimeException.class, () -> tool.call("""
                {
                  "title":"restart",
                  "summary":"test",
                  "actions":[{
                    "operationId":"restart_service",
                    "mcpId":"order-service-control-mcp",
                    "toolName":"restart_service",
                    "arguments":{"service":"order-service"}
                  }]
                }
                """));
        assertTrue(String.valueOf(error.getMessage()).contains("CHANGE_PACKAGE_AUTHORITATIVE_EVIDENCE_REQUIRED"));
    }

    @Test
    void shouldAcceptVerifiedRabbitMqEvidenceForHighRiskPackageWhenItIsTheAvailableAuthority() {
        OpsChangePackageActionPolicyBinder binder = mock(OpsChangePackageActionPolicyBinder.class);
        when(binder.proposalCatalog("demo-project")).thenReturn(List.of());
        when(binder.bind(eq("demo-project"), any())).thenReturn(List.of(Map.of(
                "operationId", "restart_service",
                "mcpId", "order-service-control-mcp",
                "toolName", "restart_service",
                "effectType", "EXECUTE_EXTERNAL_ACTION",
                "effectScope", "PRODUCTION",
                "mutability", "PROD_MUTATING",
                "riskLevel", "HIGH",
                "requiresDryRun", false,
                "arguments", Map.of("service", "order-service"))));
        OpsToolExecutionService toolExecutionService = mock(OpsToolExecutionService.class);
        when(toolExecutionService.execute(any(), eq("alice"))).thenReturn(Map.of(
                "projectId", "demo-project",
                "packageId", "cp-rabbit",
                "status", "DRAFT",
                "version", 1,
                "packageHash", "hash-rabbit",
                "riskLevel", "HIGH"));
        OpsChangePackageToolProvider provider =
                new OpsChangePackageToolProvider(toolExecutionService, null, binder);
        OpsAgentChatRequest runtimeRequest = OpsAgentChatRequest.builder()
                .metadata(new java.util.LinkedHashMap<>(Map.of(
                        OpsChangePackageRuntimeToolContributor.AVAILABLE_AUTHORITATIVE_SOURCE_TYPES_KEY,
                        List.of("RABBITMQ"))))
                .build();
        List<OpsRuntimeEvent> events = List.of(OpsRuntimeEvent.builder()
                .eventType("TOOL_CALL_FINISHED")
                .status("SUCCEEDED")
                .summary("RabbitMQ health completed")
                .payload(Map.of(
                        "toolName", "project_mcp_demo_rabbitmq_readonly_mcp",
                        "toolKind", "mcp",
                        "output", "{\"status\":\"ok\"}",
                        "remoteCallExecuted", true,
                        "allowed", true,
                        "resultId", "tool-result-rabbit",
                        "mcpId", "demo-rabbitmq-readonly-mcp",
                        "remoteToolName", "rabbitmq_health"))
                .build());
        ToolCallback tool = provider.build(
                "demo-project", "alice", "run-rabbit", "bundle-1", "bundle-hash-1",
                events, List.of("demo-project-local-runtime"), runtimeRequest);

        String output = tool.call("""
                {
                  "title":"restart after RabbitMQ recovery",
                  "summary":"test",
                  "actions":[{
                    "operationId":"restart_service",
                    "mcpId":"order-service-control-mcp",
                    "toolName":"restart_service",
                    "arguments":{"service":"order-service"}
                  }]
                }
                """);

        assertTrue(output.contains("cp-rabbit"));
        verify(toolExecutionService).execute(any(), eq("alice"));
    }

    @Test
    void shouldUsePriorSessionEvidenceForHighRiskPackage() {
        OpsChangePackageActionPolicyBinder binder = mock(OpsChangePackageActionPolicyBinder.class);
        when(binder.proposalCatalog("demo-project")).thenReturn(List.of());
        when(binder.bind(eq("demo-project"), any())).thenReturn(List.of(Map.of(
                "operationId", "restart_service",
                "mcpId", "order-service-control-mcp",
                "toolName", "restart_service",
                "effectType", "EXECUTE_EXTERNAL_ACTION",
                "effectScope", "PRODUCTION",
                "mutability", "PROD_MUTATING",
                "riskLevel", "HIGH",
                "requiresDryRun", false,
                "arguments", Map.of("service", "order-service"))));
        OpsToolExecutionService toolExecutionService = mock(OpsToolExecutionService.class);
        when(toolExecutionService.execute(any(), eq("alice"))).thenReturn(Map.of(
                "projectId", "demo-project",
                "packageId", "cp-prior-session",
                "status", "DRAFT",
                "version", 1,
                "packageHash", "hash-prior-session",
                "riskLevel", "HIGH"));
        OpsChangePackageToolProvider provider =
                new OpsChangePackageToolProvider(toolExecutionService, null, binder);
        OpsRuntimeEvent priorEvidence = OpsRuntimeEvent.builder()
                .eventType("TOOL_CALL_FINISHED")
                .status("SUCCEEDED")
                .summary("RabbitMQ health completed in a previous turn")
                .payload(Map.of(
                        "toolName", "project_mcp_demo_rabbitmq_readonly_mcp",
                        "toolKind", "mcp",
                        "output", "{\"status\":\"ok\"}",
                        "remoteCallExecuted", true,
                        "allowed", true,
                        "resultId", "tool-result-prior-rabbit",
                        "mcpId", "demo-rabbitmq-readonly-mcp",
                        "remoteToolName", "rabbitmq_health"))
                .build();
        OpsAgentChatRequest runtimeRequest = OpsAgentChatRequest.builder()
                .metadata(new java.util.LinkedHashMap<>(Map.of(
                        OpsChangePackageRuntimeToolContributor.AVAILABLE_AUTHORITATIVE_SOURCE_TYPES_KEY,
                        List.of("RABBITMQ"),
                        OpsChangePackageRuntimeToolContributor.SESSION_EVIDENCE_KEY,
                        List.of(priorEvidence))))
                .build();
        ToolCallback tool = provider.build(
                "demo-project", "alice", "run-prior-session", "bundle-1", "bundle-hash-1",
                List.of(), List.of("demo-project-local-runtime"), runtimeRequest);

        String output = tool.call("""
                {
                  "title":"restart after prior RabbitMQ evidence",
                  "summary":"test",
                  "actions":[{
                    "operationId":"restart_service",
                    "mcpId":"order-service-control-mcp",
                    "toolName":"restart_service",
                    "arguments":{"service":"order-service"}
                  }]
                }
                """);

        assertTrue(output.contains("cp-prior-session"));
        verify(toolExecutionService).execute(any(), eq("alice"));
    }

    @Test
    void shouldForwardCompleteWorkSessionClaimToUnifiedToolExecution() {
        OpsToolExecutionService toolExecutionService = mock(OpsToolExecutionService.class);
        when(toolExecutionService.execute(any(), eq("alice"))).thenReturn(Map.of(
                "projectId", "demo-project",
                "packageId", "cp-work-session",
                "status", "DRAFT",
                "version", 1,
                "packageHash", "hash-work-session",
                "riskLevel", "MEDIUM"));
        OpsChangePackageToolProvider provider = new OpsChangePackageToolProvider(toolExecutionService);
        OpsRuntimeEvent evidence = OpsRuntimeEvent.builder()
                .eventType("TOOL_CALL_FINISHED")
                .nodeId("prometheus-investigation")
                .status("SUCCEEDED")
                .summary("Prometheus 查询完成")
                .payload(Map.of(
                        "toolName", "prometheus.query",
                        "toolKind", "mcp",
                        "output", "{\"up\":0}"))
                .build();
        OpsAgentChatRequest runtimeRequest = OpsAgentChatRequest.builder()
                .userId("alice")
                .projectId("demo-project")
                .sessionId("session-1")
                .runId("run-work-session")
                .metadata(Map.of(
                        OpsWorkSessionClaimMetadata.ATTEMPT_ID, "attempt-1",
                        OpsWorkSessionClaimMetadata.LEASE_TOKEN, "lease-1",
                        OpsWorkSessionClaimMetadata.FENCING_TOKEN, 6L,
                        OpsWorkSessionClaimMetadata.STATE_VERSION, 7L,
                        OpsWorkSessionClaimMetadata.RUN_MANIFEST_HASH, "manifest-1"))
                .build();

        ToolCallback tool = provider.build(
                "demo-project", "alice", "run-work-session", "bundle-1", "bundle-hash-1",
                List.of(evidence), List.of("demo-project-local-runtime"), runtimeRequest);
        tool.call("{\"title\":\"work-session claim\",\"summary\":\"evidence bound\",\"actions\":[]}");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> execution = ArgumentCaptor.forClass(Map.class);
        verify(toolExecutionService).execute(execution.capture(), eq("alice"));
        WorkSessionRunClaim claim = new OpsWorkSessionRunMapper().toolClaim(execution.getValue());
        assertEquals("attempt-1", claim.attemptId());
        assertEquals(6L, claim.fencingToken());
        assertEquals(7L, claim.stateVersion());
        assertEquals("manifest-1", claim.runManifestHash());
    }

    private OpsToolExecutionService executionService(OpsChangePackagePreparationService preparationService) {
        OpsToolsetCatalogService catalogService = mock(OpsToolsetCatalogService.class);
        when(catalogService.listEffectiveToolsets(eq("demo-project"), eq("alice")))
                .thenReturn(new OpsToolsetRegistry().listBuiltInToolsets());
        PrepareChangePackageUseCase preparation = mock(PrepareChangePackageUseCase.class);
        when(preparation.prepare(any(ChangePackageCommands.Prepare.class))).thenAnswer(invocation -> {
            ChangePackageCommands.Prepare command = invocation.getArgument(0);
            return preparationService.prepare(
                    command.request(),
                    command.actor()).snapshotInput();
        });
        OpsToolsetRouter router = new OpsToolsetRouter();
        AtomicLong nanos = new AtomicLong();
        ToolExecutionApplicationService application = new ToolExecutionApplicationService(
                new OpsToolExecutionCatalogAdapter(catalogService, router),
                new OpsChangePackageToolExecutionDispatchHandler(
                        providerOf(preparation), providerOf(null), providerOf(null))::dispatch,
                command -> new ToolExecutionRecordedResult(
                        "tool-result-test", "evidence-test", "preview", "a".repeat(64), false,
                        "tool-result://tool-result-test", "b".repeat(64), command.durationMs()),
                (request, checkpointType, payload) -> { },
                event -> { },
                () -> "tool-call-test",
                () -> nanos.addAndGet(1_000_000L));
        return new OpsToolExecutionService(
                application,
                new OpsToolExecutionMapper(),
                mock(McpExecutionApplicationService.class),
                new OpsMcpExecutionMapper());
    }

    @SuppressWarnings("unchecked")
    private <T> ObjectProvider<T> providerOf(T value) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(value);
        if (value != null) {
            when(provider.getObject()).thenReturn(value);
        }
        return provider;
    }
}
