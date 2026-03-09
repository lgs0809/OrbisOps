package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.worksession.run.WorkSessionRunStartCommand;
import cn.lgs.orbisops.domain.worksession.run.model.WorkSessionRunStart;
import cn.lgs.orbisops.domain.worksession.run.model.WorkSessionRunStartDraft;
import cn.lgs.orbisops.domain.worksession.run.service.WorkSessionRunPolicy;
import cn.lgs.orbisops.domain.worksession.runtime.model.ApprovedPackageSnapshot;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class OpsApprovedLandingWorkSessionHarnessTest {

    @Test
    void approvedLandingAuthoritySurvivesIntoDomainWorkSessionPolicy() {
        OpsAgentDefinition definition = new OpsPlatformLandingRuntimeDefinitionFactory().create("demo-project");
        ApprovedPackageSnapshot approved = new ApprovedPackageSnapshot(
                "cp-test",
                1,
                "package-hash",
                "demo-project",
                "prod",
                "",
                Instant.now().plusSeconds(300));
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put(OpsAgentRunExecutionContextFactory.TRUSTED_APPROVED_PACKAGE_KEY, approved);
        OpsAgentChatRequest request = OpsAgentChatRequest.builder()
                .userId("admin")
                .sessionId("landing-run-test")
                .runId("landing-run-test")
                .query("execute approved package")
                .mode("AGENT")
                .engine("STATE_GRAPH")
                .projectId("demo-project")
                .agentDefinitionId(definition.getAgentId())
                .agentVersion(definition.getVersion())
                .agentDefinition(definition)
                .metadata(metadata)
                .build();

        OpsAgentRunExecutionContextFactory authority = new OpsAgentRunExecutionContextFactory();
        assertNotNull(authority.bindServerContext(request, definition));
        OpsExecutionHarness harness = OpsExecutionHarness.forRequest(request);
        assertEquals(OpsExecutionHarness.APPROVED_LANDING, harness);

        OpsRuntimeExecutionPlan plan = OpsRuntimeExecutionPlan.builder()
                .mode("AGENT")
                .engine("STATE_GRAPH")
                .adapterKey("UNIFIED_STATE_GRAPH")
                .metadata(Map.of())
                .build();
        WorkSessionRunStartCommand command = new OpsWorkSessionRunMapper()
                .startCommand(request, definition, plan, harness);
        assertEquals("APPROVED_LANDING", command.executionHarness());

        Instant now = Instant.now();
        WorkSessionRunStartDraft draft = new WorkSessionRunStartDraft(
                command.runId(), command.projectId(), command.sessionId(), command.actor(),
                command.agentId(), command.agentVersion(), command.agentDefinitionHash(),
                command.executionHarness(), command.harnessVersion(), command.harnessHash(),
                command.engine(), command.adapterKey(), command.modelId(), command.modelProfileId(),
                command.modelProfileVersion(), command.promptIdentity(), command.requestIdentity(),
                command.requestPayload(), command.metadata(), "attempt-test", "lease-test", "worker-test",
                now, now.plusSeconds(60));
        WorkSessionRunStart start = new WorkSessionRunPolicy().prepareStart(draft);
        assertEquals("APPROVED_LANDING", start.executionHarness());
    }
}
